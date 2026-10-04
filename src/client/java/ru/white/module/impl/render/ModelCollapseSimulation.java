package ru.white.module.impl.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.utils.render.ModelBoxCapture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Портированная Kimiko ModelCollapseSimulation.
 *
 * Разбирает захваченную модель на поверхностные кубики (внутренние отбрасываются),
 * запускает по ним волну разлома снизу вверх или сверху вниз, гоняет физику
 * с коллизией по блокам и рисует осколки + остаточную оболочку модели.
 */
public final class ModelCollapseSimulation {

    private static final int MAX_PILES = 8;
    private static final int MAX_CUBES_PER_PILE = 2800;
    private static final int MAX_CUBES_TOTAL = 6500;
    private static final float MAX_STEP = 0.016666668f;
    private static final float GRAVITY = 12.5f;
    private static final float AIR_DRAG = 0.85f;
    private static final float GROUND_FRICTION = 0.62f;
    private static final float MIN_BOUNCE = 0.55f;
    private static final float REST_SPEED_SQ = 0.09f;
    private static final long SHRINK_MS = 600L;
    private static final long HURT_OVERLAY_MS = 450L;

    private static final float[] UV_SCRATCH = new float[2];
    private static final float[] CLIP = new float[30];
    private static final Vector3f POSITION_SCRATCH = new Vector3f();
    private static final Vector3f NORMAL_SCRATCH = new Vector3f();
    private static final float[] CORNERS_X = new float[8];
    private static final float[] CORNERS_Y = new float[8];
    private static final float[] CORNERS_Z = new float[8];

    private static final int[] FACES = {
            1, 3, 7, 5, 0, 2, 6, 4,
            2, 3, 7, 6, 0, 1, 5, 4,
            4, 5, 7, 6, 0, 1, 3, 2,
    };

    private final List<Pile> piles = new ArrayList<>();
    private final Random random = new Random();
    private final BlockPos.Mutable blockPos = new BlockPos.Mutable();

    private long lastFrameNs;

    public boolean isIdle() {
        return piles.isEmpty();
    }

    public void clear() {
        piles.clear();
        lastFrameNs = 0L;
    }

    public int totalCubes() {
        int total = 0;
        for (Pile pile : piles) total += pile.cubes.size();
        return total;
    }

    /** Порождение по подтверждённой смерти: модель ещё доступна для захвата. */
    public boolean spawn(LivingEntity entity, float partialTick, Settings settings) {
        ModelCollapseSnapshots.Snapshot snapshot = ModelCollapseSnapshots.take(entity.getId());
        boolean gone = entity.isRemoved() || !entity.isAlive();

        double originX = snapshot != null && gone ? snapshot.x()
                : MathHelper.lerp(partialTick, entity.lastRenderX, entity.getX());
        double originY = snapshot != null && gone ? snapshot.y()
                : MathHelper.lerp(partialTick, entity.lastRenderY, entity.getY());
        double originZ = snapshot != null && gone ? snapshot.z()
                : MathHelper.lerp(partialTick, entity.lastRenderZ, entity.getZ());

        List<ModelBoxCapture.Box> boxes = snapshot != null && snapshot.boxes() != null
                ? snapshot.boxes()
                : ModelBoxCapture.capture(entity, partialTick, false);

        float height = snapshot != null ? snapshot.height() : entity.getHeight();
        Vec3d velocity = entity.getVelocity();
        float motionX = snapshot != null ? snapshot.motionX() : (float) velocity.x;
        float motionY = snapshot != null ? snapshot.motionY() : (float) velocity.y;
        float motionZ = snapshot != null ? snapshot.motionZ() : (float) velocity.z;

        return buildPile(entity.getId(), boxes, originX, originY, originZ,
                height * 0.5f, motionX, motionY, motionZ, settings, false);
    }

    /** Порождение из замороженного снапшота: сущности уже нет, берём её позу. */
    public boolean spawnFrozen(int entityId, ModelCollapseSnapshots.Snapshot snapshot, Settings settings) {
        return buildPile(entityId, snapshot.boxes(), snapshot.x(), snapshot.y(), snapshot.z(),
                snapshot.height() * 0.5f, snapshot.motionX(), snapshot.motionY(), snapshot.motionZ(),
                settings, true);
    }

    /** Снимает заморозку, если сущность всё-таки появилась (подтвердила смерть). */
    public boolean release(int entityId) {
        for (Pile pile : piles) {
            if (pile.entityId != entityId || !pile.frozen) continue;
            pile.frozen = false;
            pile.spawnMs = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    /** Сущность вернулась, а у нас уже не замороженная куча — выкидываем. */
    public void dropFrozen(int entityId) {
        piles.removeIf(p -> p.entityId == entityId && p.frozen);
    }

    private boolean buildPile(int entityId, List<ModelBoxCapture.Box> captured,
                              double originX, double originY, double originZ, float centerY,
                              float motionX, float motionY, float motionZ,
                              Settings settings, boolean frozen) {
        if (piles.size() >= MAX_PILES) return false;

        List<ModelBoxCapture.Box> boxes = dropSameTextureOverlays(captured);
        if (boxes.isEmpty()) return false;

        float cubeSize = MathHelper.clamp(settings.cubeSize(), 0.02f, 0.25f);

        int prospective = 0;
        for (ModelBoxCapture.Box box : boxes) prospective += surfaceCellCount(box, cubeSize);
        if (prospective <= 0) return false;

        int budget = Math.min(MAX_CUBES_PER_PILE, MAX_CUBES_TOTAL - totalCubes());
        if (budget < 40) return false;

        float keepChance = Math.min(1.0f, (float) budget / (float) prospective);
        float impulse = Math.max(0.0f, settings.impulse());

        List<Identifier> textures = new ArrayList<>();
        List<float[]> texSizes = new ArrayList<>();
        List<ShellQuad> shell = new ArrayList<>();

        for (ModelBoxCapture.Box box : boxes) {
            int texIndex = textures.indexOf(box.texture());
            if (texIndex < 0) {
                texIndex = textures.size();
                textures.add(box.texture());
                texSizes.add(textureSize(box.texture()));
            }
            appendShell(box, texIndex, shell);
        }

        List<CollapseCube> cubes = new ArrayList<>(Math.min(budget, prospective));
        for (ModelBoxCapture.Box box : boxes) {
            int texIndex = textures.indexOf(box.texture());
            if (texIndex < 0) continue;

            float[] texSize = texSizes.get(texIndex);
            voxelizeBox(box, texIndex, texSize[0], texSize[1], cubeSize, keepChance,
                    originX, originY, originZ, centerY, motionX, motionY, motionZ, impulse, cubes);

            if (cubes.size() >= budget) break;
        }
        if (cubes.isEmpty()) return false;

        while (cubes.size() > budget) cubes.remove(cubes.size() - 1);
        cubes.sort(Comparator.comparingInt(c -> c.texIndex));

        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (ShellQuad quad : shell) {
            minY = Math.min(minY, quad.minY);
            maxY = Math.max(maxY, quad.maxY);
        }
        if (shell.isEmpty()) {
            for (CollapseCube cube : cubes) {
                float local = (float) (cube.y - originY);
                minY = Math.min(minY, local);
                maxY = Math.max(maxY, local);
            }
        }

        float span = Math.max(1.0E-4f, maxY - minY);
        long waveMs = MathHelper.clamp(settings.waveMs(), 0L, 3000L);

        // каждому кубику свой момент отпускания — так получается фронт волны
        for (CollapseCube cube : cubes) {
            float frac = MathHelper.clamp(((float) (cube.y - originY) - minY) / span, 0.0f, 1.0f);
            if (settings.topDown()) frac = 1.0f - frac;
            float jitter = (random.nextFloat() - 0.5f) * 0.06f;
            cube.releaseMs = Math.round(MathHelper.clamp(frac + jitter, 0.0f, 1.0f) * waveMs);
        }

        Pile pile = new Pile(entityId, System.currentTimeMillis(),
                Math.max(500L, settings.lifeMs()),
                originX, originY, originZ, textures, cubes, shell,
                minY, maxY, waveMs, settings.topDown());
        pile.frozen = frozen;
        piles.add(pile);
        return true;
    }

    /** Кладёт 4-вершинные квады бокса в мировые координаты для остаточной оболочки. */
    private void appendShell(ModelBoxCapture.Box box, int texIndex, List<ShellQuad> out) {
        int n = box.quadCount();
        float[] verts = box.verts();
        float[] uvs = box.uvs();

        for (int q = 0; q < n; q++) {
            int o = q * 12;
            float[] pos = new float[12];
            for (int i = 0; i < 4; i++) {
                pos[i * 3] = box.cx() + verts[o + i * 3];
                pos[i * 3 + 1] = box.cy() + verts[o + i * 3 + 1];
                pos[i * 3 + 2] = box.cz() + verts[o + i * 3 + 2];
            }

            float[] uv = new float[8];
            for (int i = 0; i < 8; i++) uv[i] = uvs[q * 8 + i];

            float e1y = pos[4] - pos[1];
            float e2z = pos[11] - pos[2];
            float e1z = pos[5] - pos[2];
            float e2y = pos[10] - pos[1];
            float nx = e1y * e2z - e1z * e2y;
            float e2x = pos[9] - pos[0];
            float e1x = pos[3] - pos[0];
            float ny = e1z * e2x - e1x * e2z;
            float nz = e1x * e2y - e1y * e2x;

            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0E-6f) {
                nx = 0.0f;
                ny = 1.0f;
                nz = 0.0f;
            } else {
                nx /= len;
                ny /= len;
                nz /= len;
            }

            out.add(new ShellQuad(texIndex, pos, uv,
                    box.colors()[q], box.lights()[q], box.overlays()[q], nx, ny, nz));
        }
    }

    public void renderAndStep(EventRender3D event, Settings settings) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld level = client.world;
        if (level == null) {
            clear();
            return;
        }
        if (piles.isEmpty()) {
            lastFrameNs = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        long nowNs = System.nanoTime();
        float dt = lastFrameNs == 0L ? 0.0f : (float) ((nowNs - lastFrameNs) / 1.0E9);
        lastFrameNs = nowNs;
        dt = MathHelper.clamp(dt, 0.0f, 0.1f);

        for (int i = piles.size() - 1; i >= 0; i--) {
            Pile pile = piles.get(i);
            if (pile.frozen) continue;
            if (now - pile.spawnMs <= pile.lifeMs && !pile.cubes.isEmpty()) continue;
            piles.remove(i);
        }
        if (piles.isEmpty()) return;

        if (dt > 0.0f) {
            // дробим кадр на фиксированные шаги, иначе физика зависит от FPS
            int steps = MathHelper.clamp((int) Math.ceil(dt / MAX_STEP), 1, 4);
            float h = dt / steps;
            float restitution = MathHelper.clamp(settings.restitution(), 0.0f, 0.9f);
            double voidY = level.getBottomY() - 24.0;

            for (Pile pile : piles) {
                if (pile.frozen) continue;
                long age = now - pile.spawnMs;
                for (int step = 0; step < steps; step++) stepPile(level, pile, h, restitution, voidY, age);
                pile.cubes.removeIf(cube -> cube.dead);
            }
        }

        render(event, now);
    }

    private void stepPile(ClientWorld level, Pile pile, float h, float restitution, double voidY, long ageMs) {
        for (CollapseCube cube : pile.cubes) {
            if (cube.sleeping || cube.dead) continue;

            if (!cube.released) {
                if (ageMs < cube.releaseMs) continue;
                cube.released = true;
                // выталкиваем из блока, в котором кубик появился
                for (int escape = 0; escape < 8 && collides(level, cube.x, cube.y, cube.z, cube.half); escape++) {
                    cube.y += 0.06;
                }
            }

            cube.vy -= GRAVITY * h;
            float drag = 1.0f - AIR_DRAG * h;
            cube.vx *= drag;
            cube.vy *= drag;
            cube.vz *= drag;

            double half = cube.half;
            boolean grounded = false;

            double nx = cube.x + cube.vx * h;
            if (cube.vx != 0.0f) {
                if (collides(level, nx, cube.y, cube.z, half) && !collides(level, cube.x, cube.y, cube.z, half)) {
                    cube.vx = -cube.vx * restitution * 0.7f;
                } else {
                    cube.x = nx;
                }
            }

            double nz = cube.z + cube.vz * h;
            if (cube.vz != 0.0f) {
                if (collides(level, cube.x, cube.y, nz, half) && !collides(level, cube.x, cube.y, cube.z, half)) {
                    cube.vz = -cube.vz * restitution * 0.7f;
                } else {
                    cube.z = nz;
                }
            }

            double ny = cube.y + cube.vy * h;
            if (collides(level, cube.x, ny, cube.z, half) && !collides(level, cube.x, cube.y, cube.z, half)) {
                if (cube.vy < 0.0f) {
                    grounded = true;
                    float bounced = -cube.vy * restitution;
                    cube.vy = bounced > MIN_BOUNCE ? bounced : 0.0f;
                    cube.vx *= GROUND_FRICTION;
                    cube.vz *= GROUND_FRICTION;
                    cube.angVel *= 0.55f;
                } else {
                    cube.vy = -cube.vy * 0.25f;
                }
            } else {
                cube.y = ny;
            }

            cube.angle += cube.angVel * h;

            float speedSq = cube.vx * cube.vx + cube.vy * cube.vy + cube.vz * cube.vz;
            if (grounded && speedSq < REST_SPEED_SQ) {
                cube.restSteps++;
                if (cube.restSteps > 6) {
                    cube.sleeping = true;
                    cube.angVel = 0.0f;
                    cube.vx = 0.0f;
                    cube.vy = 0.0f;
                    cube.vz = 0.0f;
                }
            } else {
                cube.restSteps = 0;
            }

            if (cube.y < voidY) cube.dead = true;
        }
    }

    /** AABB кубика против коллизионныхshape блоков. */
    private boolean collides(ClientWorld level, double x, double y, double z, double half) {
        double minX = x - half, minY = y - half, minZ = z - half;
        double maxX = x + half, maxY = y + half, maxZ = z + half;

        int x0 = MathHelper.floor(minX), x1 = MathHelper.floor(maxX);
        int y0 = MathHelper.floor(minY), y1 = MathHelper.floor(maxY);
        int z0 = MathHelper.floor(minZ), z1 = MathHelper.floor(maxZ);

        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    blockPos.set(bx, by, bz);
                    BlockState state = level.getBlockState(blockPos);
                    if (state.isAir()) continue;

                    VoxelShape shape = state.getCollisionShape((BlockView) level, blockPos);
                    if (shape.isEmpty()) continue;

                    Box bounds = shape.getBoundingBox();
                    if (minX < bx + bounds.maxX && maxX > bx + bounds.minX
                            && minY < by + bounds.maxY && maxY > by + bounds.minY
                            && minZ < bz + bounds.maxZ && maxZ > bz + bounds.minZ) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void render(EventRender3D event, long now) {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client.gameRenderer == null ? null : client.gameRenderer.getCamera();
        if (camera == null || event.getMatrixStack() == null) return;

        Vec3d cam = camera.getCameraPos();
        MatrixStack.Entry pose = event.getMatrixStack().peek();
        VertexConsumerProvider.Immediate provider = client.getBufferBuilders().getEntityVertexConsumers();

        for (Pile pile : piles) {
            long age = pile.frozen ? 0L : now - pile.spawnMs;
            long shrinkStart = pile.lifeMs - SHRINK_MS;

            float scale;
            if (age <= shrinkStart) {
                scale = 1.0f;
            } else {
                float t = MathHelper.clamp((age - shrinkStart) / 600.0f, 0.0f, 1.0f);
                scale = 1.0f - t * t * (3.0f - 2.0f * t);
            }
            if (scale <= 0.02f) continue;

            boolean useOverlay = now - pile.createdMs < HURT_OVERLAY_MS;

            float span = Math.max(1.0E-4f, pile.shellMaxY - pile.shellMinY);
            float progress = pile.frozen ? 0.0f
                    : (pile.waveMs <= 0L ? 1.0f : MathHelper.clamp(age / (float) pile.waveMs, 0.0f, 1.0f));

            float frontLocal = pile.topDown
                    ? pile.shellMaxY - progress * span
                    : pile.shellMinY + progress * span;
            double frontWorld = pile.originY + frontLocal;

            boolean keepAbove = !pile.topDown;
            boolean shellAlive = progress < 1.0f && !pile.shell.isEmpty();

            int texCount = pile.textures.size();
            for (int texIndex = 0; texIndex < texCount; texIndex++) {
                RenderLayer layer = RenderLayers.entityCutoutNoCull(pile.textures.get(texIndex));
                VertexConsumer consumer = provider.getBuffer(layer);
                boolean drew = false;

                if (shellAlive) {
                    for (ShellQuad quad : pile.shell) {
                        if (quad.texIndex != texIndex) continue;
                        if (keepAbove ? quad.maxY < frontLocal : quad.minY > frontLocal) continue;
                        if (!emitShell(consumer, pose, quad, pile, cam.x, cam.y, cam.z, frontLocal, keepAbove, useOverlay)) {
                            continue;
                        }
                        drew = true;
                    }
                }

                for (CollapseCube cube : pile.cubes) {
                    if (cube.texIndex != texIndex || cube.dead) continue;
                    if (!cube.released && shellAlive) {
                        boolean shattered = keepAbove ? cube.y <= frontWorld : cube.y >= frontWorld;
                        if (!shattered) continue;
                    }
                    emitCube(consumer, pose, cube, cam.x, cam.y, cam.z, scale, useOverlay);
                    drew = true;
                }

                if (!drew) continue;
                provider.draw(layer);
            }
        }
    }

    /** Клиппит квад оболочки по фронту волны и разворачивает получившийся полигон. */
    private boolean emitShell(VertexConsumer consumer, MatrixStack.Entry pose, ShellQuad quad, Pile pile,
                              double camX, double camY, double camZ, float frontY,
                              boolean keepAbove, boolean useOverlay) {
        int count = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            float xi = quad.pos[i * 3];
            float yi = quad.pos[i * 3 + 1];
            float zi = quad.pos[i * 3 + 2];
            float xj = quad.pos[j * 3];
            float yj = quad.pos[j * 3 + 1];
            float zj = quad.pos[j * 3 + 2];
            float ui = quad.uv[i * 2];
            float vi = quad.uv[i * 2 + 1];
            float uj = quad.uv[j * 2];
            float vj = quad.uv[j * 2 + 1];

            boolean insideI = keepAbove ? yi >= frontY : yi <= frontY;
            boolean insideJ = keepAbove ? yj >= frontY : yj <= frontY;

            if (insideI) {
                int b = count * 5;
                CLIP[b] = xi;
                CLIP[b + 1] = yi;
                CLIP[b + 2] = zi;
                CLIP[b + 3] = ui;
                CLIP[b + 4] = vi;
                count++;
            }

            if (insideI == insideJ) continue;

            // ребро пересекло фронт — добавляем точку пересечения
            float denom = yj - yi;
            float t = Math.abs(denom) < 1.0E-6f ? 0.0f
                    : MathHelper.clamp((frontY - yi) / denom, 0.0f, 1.0f);

            int b = count * 5;
            CLIP[b] = xi + (xj - xi) * t;
            CLIP[b + 1] = yi + (yj - yi) * t;
            CLIP[b + 2] = zi + (zj - zi) * t;
            CLIP[b + 3] = ui + (uj - ui) * t;
            CLIP[b + 4] = vi + (vj - vi) * t;
            count++;
        }

        if (count < 3) return false;

        int overlay = useOverlay ? quad.overlay : OverlayTexture.DEFAULT_UV;
        Matrix4f matrix = pose.getPositionMatrix();
        pose.transformNormal(quad.nx, quad.ny, quad.nz, NORMAL_SCRATCH);
        float tnx = NORMAL_SCRATCH.x;
        float tny = NORMAL_SCRATCH.y;
        float tnz = NORMAL_SCRATCH.z;

        int n = count - 1;
        for (int k = 1; k < n; k++) {
            emitShellVertex(consumer, matrix, quad, pile, 0, camX, camY, camZ, overlay, tnx, tny, tnz);
            emitShellVertex(consumer, matrix, quad, pile, k, camX, camY, camZ, overlay, tnx, tny, tnz);
            emitShellVertex(consumer, matrix, quad, pile, k + 1, camX, camY, camZ, overlay, tnx, tny, tnz);
            emitShellVertex(consumer, matrix, quad, pile, k + 1, camX, camY, camZ, overlay, tnx, tny, tnz);
        }
        return true;
    }

    private void emitShellVertex(VertexConsumer consumer, Matrix4f matrix, ShellQuad quad, Pile pile, int index,
                                 double camX, double camY, double camZ, int overlay,
                                 float normalX, float normalY, float normalZ) {
        int b = index * 5;
        matrix.transformPosition(
                (float) (pile.originX + CLIP[b] - camX),
                (float) (pile.originY + CLIP[b + 1] - camY),
                (float) (pile.originZ + CLIP[b + 2] - camZ),
                POSITION_SCRATCH);
        consumer.vertex(POSITION_SCRATCH.x, POSITION_SCRATCH.y, POSITION_SCRATCH.z)
                .color(quad.color)
                .texture(CLIP[b + 3], CLIP[b + 4])
                .overlay(overlay)
                .light(quad.light)
                .normal(normalX, normalY, normalZ);
    }

    private void emitCube(VertexConsumer consumer, MatrixStack.Entry pose, CollapseCube cube,
                          double camX, double camY, double camZ, float scale, boolean useOverlay) {
        float half = cube.half * scale;
        if (half < 0.003f) return;

        float c = (float) Math.cos(cube.angle);
        float s = (float) Math.sin(cube.angle);
        float t = 1.0f - c;

        float ax = cube.axX, ay = cube.axY, az = cube.axZ;

        float m00 = c + ax * ax * t;
        float m01 = ax * ay * t - az * s;
        float m02 = ax * az * t + ay * s;
        float m10 = ay * ax * t + az * s;
        float m11 = c + ay * ay * t;
        float m12 = ay * az * t - ax * s;
        float m20 = az * ax * t - ay * s;
        float m21 = az * ay * t + ax * s;
        float m22 = c + az * az * t;

        float bx = (float) (cube.x - camX);
        float by = (float) (cube.y - camY);
        float bz = (float) (cube.z - camZ);

        Matrix4f matrix = pose.getPositionMatrix();
        for (int i = 0; i < 8; i++) {
            float lx = (i & 1) != 0 ? half : -half;
            float ly = (i & 2) != 0 ? half : -half;
            float lz = (i & 4) != 0 ? half : -half;
            matrix.transformPosition(
                    bx + m00 * lx + m01 * ly + m02 * lz,
                    by + m10 * lx + m11 * ly + m12 * lz,
                    bz + m20 * lx + m21 * ly + m22 * lz,
                    POSITION_SCRATCH);
            CORNERS_X[i] = POSITION_SCRATCH.x;
            CORNERS_Y[i] = POSITION_SCRATCH.y;
            CORNERS_Z[i] = POSITION_SCRATCH.z;
        }

        int overlay = useOverlay ? cube.overlay : OverlayTexture.DEFAULT_UV;

        for (int face = 0; face < 6; face++) {
            int axis = face >> 1;
            float sign = (face & 1) == 0 ? 1.0f : -1.0f;

            float nx, ny, nz;
            switch (axis) {
                case 0 -> { nx = m00; ny = m10; nz = m20; }
                case 1 -> { nx = m01; ny = m11; nz = m21; }
                default -> { nx = m02; ny = m12; nz = m22; }
            }
            nx *= sign;
            ny *= sign;
            nz *= sign;

            pose.transformNormal(nx, ny, nz, NORMAL_SCRATCH);
            float tnx = NORMAL_SCRATCH.x;
            float tny = NORMAL_SCRATCH.y;
            float tnz = NORMAL_SCRATCH.z;

            int base = face * 4;
            for (int v = 0; v < 4; v++) {
                int corner = FACES[base + v];
                consumer.vertex(CORNERS_X[corner], CORNERS_Y[corner], CORNERS_Z[corner])
                        .color(cube.color)
                        .texture(cube.u, cube.v)
                        .overlay(overlay)
                        .light(cube.light)
                        .normal(tnx, tny, tnz);
            }
        }
    }

    /** Сколько кубиков даст бокс: только поверхностный слой, внутренние не считаем. */
    private int surfaceCellCount(ModelBoxCapture.Box box, float cubeSize) {
        int na = gridCount(box.half()[0], cubeSize);
        int nb = gridCount(box.half()[1], cubeSize);
        int nn = gridCount(box.half()[2], cubeSize);

        int total = na * nb * nn;
        if (na <= 2 || nb <= 2 || nn <= 2) return total;
        return total - (na - 2) * (nb - 2) * (nn - 2);
    }

    private int gridCount(float half, float cubeSize) {
        return MathHelper.clamp((int) Math.round(half * 2.0f / cubeSize), 1, 14);
    }

    private float[] textureSize(Identifier texture) {
        try {
            var gpu = MinecraftClient.getInstance().getTextureManager().getTexture(texture).getGlTexture();
            return new float[]{gpu.getWidth(0), gpu.getHeight(0)};
        } catch (Throwable ignored) {
            return new float[]{0.0f, 0.0f};
        }
    }

    /** Притягивает UV к центру текселя, иначе кубики «смазывают» текстуру. */
    private float snapToTexel(float value, float size) {
        if (size < 1.0f) return value;
        int texel = MathHelper.clamp((int) MathHelper.floor(value * size), 0, (int) MathHelper.floor(size) - 1);
        return (texel + 0.5f) / size;
    }

    private void voxelizeBox(ModelBoxCapture.Box box, int texIndex, float texWidth, float texHeight,
                             float cubeSize, float keepChance,
                             double originX, double originY, double originZ, float centerY,
                             float motionX, float motionY, float motionZ, float impulse,
                             List<CollapseCube> out) {
        float[] axes = box.axes();
        float[] half = box.half();

        int na = gridCount(half[0], cubeSize);
        int nb = gridCount(half[1], cubeSize);
        int nn = gridCount(half[2], cubeSize);

        float stepA = half[0] * 2.0f / na;
        float stepB = half[1] * 2.0f / nb;
        float stepN = half[2] * 2.0f / nn;
        float cellSize = Math.max(stepA, Math.max(stepB, stepN));

        for (int ia = 0; ia < na; ia++) {
            for (int ib = 0; ib < nb; ib++) {
                for (int ic = 0; ic < nn; ic++) {
                    boolean surface = ia == 0 || ia == na - 1
                            || ib == 0 || ib == nb - 1
                            || ic == 0 || ic == nn - 1;
                    if (!surface) continue;
                    if (keepChance < 1.0f && random.nextFloat() > keepChance) continue;

                    float la = -half[0] + (ia + 0.5f) * stepA;
                    float lb = -half[1] + (ib + 0.5f) * stepB;
                    float ln = -half[2] + (ic + 0.5f) * stepN;

                    float localX = axes[0] * la + axes[3] * lb + axes[6] * ln;
                    float localY = axes[1] * la + axes[4] * lb + axes[7] * ln;
                    float localZ = axes[2] * la + axes[5] * lb + axes[8] * ln;

                    // UV берём с той грани, которая смотрит наружу
                    float sa = la, sb = lb, sn = ln;
                    float da = half[0] - Math.abs(la);
                    float db = half[1] - Math.abs(lb);
                    float dn = half[2] - Math.abs(ln);
                    if (da <= db && da <= dn) {
                        sa = la >= 0.0f ? half[0] : -half[0];
                    } else if (db <= dn) {
                        sb = lb >= 0.0f ? half[1] : -half[1];
                    } else {
                        sn = ln >= 0.0f ? half[2] : -half[2];
                    }

                    float surfX = axes[0] * sa + axes[3] * sb + axes[6] * sn;
                    float surfY = axes[1] * sa + axes[4] * sb + axes[7] * sn;
                    float surfZ = axes[2] * sa + axes[5] * sb + axes[8] * sn;

                    int quad = sampleUv(box, surfX, surfY, surfZ, UV_SCRATCH);

                    double wx = originX + box.cx() + localX;
                    double wy = originY + box.cy() + localY;
                    double wz = originZ + box.cz() + localZ;

                    // разлёт от центра модели + унаследованное движение сущности
                    float dirX = box.cx() + localX;
                    float dirY = box.cy() + localY - centerY;
                    float dirZ = box.cz() + localZ;
                    float len = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
                    if (len < 1.0E-4f) {
                        dirX = random.nextFloat() - 0.5f;
                        dirY = random.nextFloat() * 0.6f;
                        dirZ = random.nextFloat() - 0.5f;
                        len = Math.max(1.0E-4f, (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ));
                    }

                    float burst = impulse * (1.1f + random.nextFloat() * 2.1f);
                    float vx = (dirX /= len) * burst * 0.8f + motionX * 2.0f
                            + (random.nextFloat() - 0.5f) * 0.5f * impulse;
                    float vy = (dirY /= len) * burst * 0.45f + impulse * (0.5f + random.nextFloat() * 1.3f)
                            + (random.nextFloat() - 0.5f) * 0.3f;
                    float vz = (dirZ /= len) * burst * 0.8f + motionZ * 2.0f
                            + (random.nextFloat() - 0.5f) * 0.5f * impulse;

                    float rx = random.nextFloat() * 2.0f - 1.0f;
                    float ry = random.nextFloat() * 2.0f - 1.0f;
                    float rz = random.nextFloat() * 2.0f - 1.0f;
                    float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
                    if (rl < 1.0E-4f) {
                        rx = 0.0f;
                        ry = 1.0f;
                        rz = 0.0f;
                        rl = 1.0f;
                    }

                    out.add(new CollapseCube(
                            wx, wy, wz, vx, vy + motionY, vz,
                            cellSize * 0.5f * (0.98f + random.nextFloat() * 0.12f),
                            snapToTexel(UV_SCRATCH[0], texWidth),
                            snapToTexel(UV_SCRATCH[1], texHeight),
                            box.colors()[quad], box.lights()[quad], box.overlays()[quad], texIndex,
                            rx /= rl, ry /= rl, rz /= rl,
                            random.nextFloat() * (float) Math.PI * 2.0f,
                            (random.nextFloat() * 2.0f - 1.0f) * (3.0f + impulse * 5.0f)));
                }
            }
        }
    }

    /** Ближайший квад бокса к точке + билинейная интерполяция UV внутри него. */
    private int sampleUv(ModelBoxCapture.Box box, float px, float py, float pz, float[] outUv) {
        float[] verts = box.verts();
        float[] uvs = box.uvs();

        float bestDist = Float.MAX_VALUE;
        int bestQuad = 0;
        float bestS = 0.5f;
        float bestT = 0.5f;

        int n = box.quadCount();
        for (int q = 0; q < n; q++) {
            int o = q * 12;
            float v0x = verts[o], v0y = verts[o + 1], v0z = verts[o + 2];

            float e1x = verts[o + 3] - v0x;
            float e1y = verts[o + 4] - v0y;
            float e1z = verts[o + 5] - v0z;
            float e2x = verts[o + 9] - v0x;
            float e2y = verts[o + 10] - v0y;
            float e2z = verts[o + 11] - v0z;

            float dx = px - v0x;
            float dy = py - v0y;
            float dz = pz - v0z;

            float len1 = e1x * e1x + e1y * e1y + e1z * e1z;
            float len2 = e2x * e2x + e2y * e2y + e2z * e2z;
            if (len1 < 1.0E-8f || len2 < 1.0E-8f) continue;

            float s = MathHelper.clamp((dx * e1x + dy * e1y + dz * e1z) / len1, 0.0f, 1.0f);
            float t = MathHelper.clamp((dx * e2x + dy * e2y + dz * e2z) / len2, 0.0f, 1.0f);

            float cx = v0x + e1x * s + e2x * t;
            float cy = v0y + e1y * s + e2y * t;
            float cz = v0z + e1z * s + e2z * t;

            float ddx = px - cx;
            float ddy = py - cy;
            float ddz = pz - cz;
            float dist = ddx * ddx + ddy * ddy + ddz * ddz;
            if (dist >= bestDist) continue;

            bestDist = dist;
            bestQuad = q;
            bestS = s;
            bestT = t;
        }

        float u0 = uvs[bestQuad * 8];
        float v0 = uvs[bestQuad * 8 + 1];
        float u1 = uvs[bestQuad * 8 + 2];
        float v1 = uvs[bestQuad * 8 + 3];
        float u3 = uvs[bestQuad * 8 + 6];
        float v3 = uvs[bestQuad * 8 + 7];

        outUv[0] = u0 + (u1 - u0) * bestS + (u3 - u0) * bestT;
        outUv[1] = v0 + (v1 - v0) * bestS + (v3 - v0) * bestT;
        return bestQuad;
    }

    /** Убирает оверлеи (огонь/эффекты) с той же текстурой, что и основная геометрия. */
    private List<ModelBoxCapture.Box> dropSameTextureOverlays(List<ModelBoxCapture.Box> boxes) {
        int size = boxes.size();
        if (size < 2) return boxes;

        List<ModelBoxCapture.Box> kept = new ArrayList<>(size);
        outer:
        for (int i = 0; i < size; i++) {
            ModelBoxCapture.Box a = boxes.get(i);
            float[] ah = a.half();
            float va = ah[0] * ah[1] * ah[2];

            for (int j = 0; j < size; j++) {
                if (i == j) continue;
                ModelBoxCapture.Box b = boxes.get(j);
                if (!Objects.equals(a.texture(), b.texture())) continue;

                float dx = a.cx() - b.cx();
                float dy = a.cy() - b.cy();
                float dz = a.cz() - b.cz();
                if (dx * dx + dy * dy + dz * dz > 9.0E-4f) continue;

                float[] bh = b.half();
                float vb = bh[0] * bh[1] * bh[2];
                if (va > vb * 1.05f) continue outer;
            }
            kept.add(a);
        }
        return kept;
    }

    // ================= Данные =================

    public record Settings(float cubeSize, float impulse, long lifeMs,
                           float restitution, long waveMs, boolean topDown) {
    }

    private static final class CollapseCube {
        double x, y, z;
        float vx, vy, vz;
        final float half;
        final float u, v;
        final int color, light, overlay;
        final int texIndex;
        final float axX, axY, axZ;
        float angle, angVel;
        long releaseMs;
        boolean released;
        boolean dead;
        boolean sleeping;
        int restSteps;

        CollapseCube(double x, double y, double z, float vx, float vy, float vz, float half,
                     float u, float v, int color, int light, int overlay, int texIndex,
                     float axX, float axY, float axZ, float angle, float angVel) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.half = half;
            this.u = u;
            this.v = v;
            this.color = color;
            this.light = light;
            this.overlay = overlay;
            this.texIndex = texIndex;
            this.axX = axX;
            this.axY = axY;
            this.axZ = axZ;
            this.angle = angle;
            this.angVel = angVel;
        }
    }

    private static final class ShellQuad {
        final int texIndex;
        final float[] pos;
        final float[] uv;
        final int color, light, overlay;
        final float nx, ny, nz;
        final float minY, maxY;

        ShellQuad(int texIndex, float[] pos, float[] uv, int color, int light, int overlay,
                  float nx, float ny, float nz) {
            this.texIndex = texIndex;
            this.pos = pos;
            this.uv = uv;
            this.color = color;
            this.light = light;
            this.overlay = overlay;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;

            float lo = Float.MAX_VALUE;
            float hi = -Float.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                float y = pos[i * 3 + 1];
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
            }
            this.minY = lo;
            this.maxY = hi;
        }
    }

    private static final class Pile {
        final int entityId;
        long spawnMs;
        final long lifeMs;
        final double originX, originY, originZ;
        final List<Identifier> textures;
        final List<CollapseCube> cubes;
        final List<ShellQuad> shell;
        final float shellMinY, shellMaxY;
        final long waveMs;
        final boolean topDown;
        boolean frozen;
        final long createdMs = System.currentTimeMillis();

        Pile(int entityId, long spawnMs, long lifeMs,
             double originX, double originY, double originZ,
             List<Identifier> textures, List<CollapseCube> cubes, List<ShellQuad> shell,
             float shellMinY, float shellMaxY, long waveMs, boolean topDown) {
            this.entityId = entityId;
            this.spawnMs = spawnMs;
            this.lifeMs = lifeMs;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.textures = textures;
            this.cubes = cubes;
            this.shell = shell;
            this.shellMinY = shellMinY;
            this.shellMaxY = shellMaxY;
            this.waveMs = waveMs;
            this.topDown = topDown;
        }
    }
}

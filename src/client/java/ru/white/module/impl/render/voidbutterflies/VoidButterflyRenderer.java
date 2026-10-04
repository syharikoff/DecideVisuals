package ru.white.module.impl.render.voidbutterflies;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.white.utils.colors.ColorUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Порт рендерера «VoidButterflies» из Dima 26.2.
 *
 * <p>Оригинал: {@code sg.ec.DcCQ4WatKHyfGvHx} + шейдеры
 * {@code dimasik/shaders/core/surface_particle.vsh} и {@code surface_butterfly.fsh}.
 * Геометрия, фазы, затухания и константы перенесены один в один; изменён только
 * способ отрисовки (в 26.2 это ручной GPU-пасс с GpuBuffer/CommandEncoder, здесь —
 * обычный RenderLayer + POSITION_TEXTURE_COLOR, как у остальных модулей Nightix).
 *
 * <p>Вершинный шейдер работает только с {@code ProjMat}, поэтому позиции уходят
 * в camera-relative виде и CPU-матрицей (view-матрица камеры из EventRender3D)
 * приводятся к eye space — ровно как в оригинале.
 */
public final class VoidButterflyRenderer {

    // --- оригинальные константы Dima ---
    private static final int BUTTERFLIES_PER_DENSITY = 72;
    private static final int SPAWN_ATTEMPTS_PER_DENSITY = 64;
    private static final int MAX_SPAWNS_PER_TICK = 5;
    private static final int FADE_OUT_KEEP_TICKS = 12;
    private static final float RESET_DISTANCE_SQ_FACTOR = 4.0F;
    private static final float FADE_IN_TICKS = 14.0F;
    private static final float FADE_OUT_TICKS = 22.0F;
    private static final float FADE_REMOVE_TICKS = 12.0F;
    private static final float MIN_ALPHA = 0.005F;
    private static final float FRUSTUM_SCALE = 3.0F;
    private static final int WING_POINTS = 6;

    private static final Direction[] DIRECTIONS = Direction.values();

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[0])
                    .withLocation(Identifier.of("client", "void_butterfly"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withVertexShader(Identifier.of("client", "core/void_butterfly"))
                    .withFragmentShader(Identifier.of("client", "core/void_butterfly"))
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final RenderLayer LAYER = RenderLayer.of(
            "void_butterfly",
            RenderSetup.builder(PIPELINE)
                    .translucent()
                    .expectedBufferSize(1 << 15)
                    .build()
    );

    private final List<Butterfly> butterflies = new ArrayList<>();
    private final Random random = new Random();
    private final BlockPos.Mutable scratch = new BlockPos.Mutable();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f frustumMatrix = new Matrix4f();
    private final Vector3f cameraRight = new Vector3f();
    private final Vector3f cameraUp = new Vector3f();
    private final BufferAllocator allocator = new BufferAllocator(1 << 17);

    private ClientWorld world;
    private Vec3d lastCamera;
    private int tick;
    private int directionCursor;

    // ------------------------------------------------------------------ api

    /** Полный сброс: список, привязка к миру и счётчик тиков. */
    public void reset() {
        butterflies.clear();
        world = null;
        lastCamera = null;
        tick = 0;
    }

    /** Симуляция: спавн/смерть/деспавн. Вызывается раз в тик. */
    public void update(float radius, float density) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            reset();
            return;
        }

        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        if (world != mc.world
                || (lastCamera != null
                && lastCamera.squaredDistanceTo(camera) > radius * radius * RESET_DISTANCE_SQ_FACTOR)) {
            reset();
            world = mc.world;
        }
        lastCamera = camera;
        tick++;

        int target = (int) Math.round(BUTTERFLIES_PER_DENSITY * density);

        Iterator<Butterfly> iterator = butterflies.iterator();
        while (iterator.hasNext()) {
            Butterfly butterfly = iterator.next();
            if (tick - butterfly.spawnTick >= butterfly.lifetime
                    || (butterfly.fadeTick >= 0 && tick - butterfly.fadeTick >= FADE_OUT_KEEP_TICKS)) {
                iterator.remove();
                continue;
            }
            if (butterfly.fadeTick < 0
                    && (butterfly.anchor.squaredDistanceTo(camera) > (radius + 2.0F) * (radius + 2.0F)
                    || !mc.world.getBlockState(butterfly.pos).equals(butterfly.state)
                    || isInsideSolid(animatedPos(butterfly, tick)))) {
                butterfly.fadeTick = tick;
            }
        }

        if (butterflies.size() >= target) return;

        int attempts = (int) Math.round(SPAWN_ATTEMPTS_PER_DENSITY * density);
        int spawned = 0;
        while (attempts-- > 0 && spawned < MAX_SPAWNS_PER_TICK && butterflies.size() < target) {
            if (spawn(camera, radius)) spawned++;
        }
    }

    /** Отрисовка всех бабочек. Вызывается из EventRender3D. */
    public void render(Matrix4f view, Matrix4f projection, float tickDelta,
                       float radius, int colorFrom, int colorTo) {
        if (world == null || butterflies.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || world != mc.world || view == null || projection == null) return;

        float time = tick + tickDelta;
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();

        frustumMatrix.set(projection).mul(view);
        frustum.set(frustumMatrix);

        Quaternionf cameraRotation = mc.gameRenderer.getCamera().getRotation();
        cameraRight.set(1.0F, 0.0F, 0.0F).rotate(cameraRotation);
        cameraUp.set(0.0F, 1.0F, 0.0F).rotate(cameraRotation);
        Vec3d right = new Vec3d(cameraRight.x, cameraRight.y, cameraRight.z);
        Vec3d up = new Vec3d(cameraUp.x, cameraUp.y, cameraUp.z);

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        VertexConsumer consumer = immediate.getBuffer(LAYER);

        for (Butterfly butterfly : butterflies) {
            float age = time - butterfly.spawnTick;
            float fade = MathHelper.clamp(age / FADE_IN_TICKS, 0.0F, 1.0F)
                    * MathHelper.clamp((butterfly.lifetime - age) / FADE_OUT_TICKS, 0.0F, 1.0F);
            if (butterfly.fadeTick >= 0) {
                fade *= MathHelper.clamp(1.0F - (time - butterfly.fadeTick) / FADE_REMOVE_TICKS, 0.0F, 1.0F);
            }

            Vec3d rel = animatedPos(butterfly, time).subtract(camera);
            double distance = rel.length();
            fade *= MathHelper.clamp((float) ((radius + 1.0F - distance) / 2.0), 0.0F, 1.0F);
            fade *= MathHelper.clamp((float) ((distance - 0.2) / 0.6), 0.0F, 1.0F);
            if (fade <= MIN_ALPHA) continue;
            if (!frustum.testSphere((float) rel.x, (float) rel.y, (float) rel.z,
                    butterfly.scale * FRUSTUM_SCALE)) continue;

            int color = ColorUtil.interpolateColor(colorFrom, colorTo,
                    0.5F + 0.5F * (float) Math.sin(butterfly.phase));
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;

            float flap = 0.25F + 0.7F * (0.5F + 0.5F * (float) Math.sin(age * 0.6F + butterfly.phase));
            Vec3d axisUp = butterfly.up.multiply(Math.cos(flap) * butterfly.scale);
            Vec3d axisNormal = butterfly.normal.multiply(Math.sin(flap) * butterfly.scale);
            Vec3d axisSide = butterfly.side.multiply(butterfly.scale * 0.82F);

            float pulse = 0.5F + 0.5F * (float) Math.sin(age * 0.22F + butterfly.phase * 2.0F);
            float pulse3 = pulse * pulse * pulse;

            // гало, всегда лицом к камере
            quad(consumer, view, rel,
                    right.multiply(butterfly.scale * 1.9), up.multiply(butterfly.scale * 1.9),
                    4.0F, 5.0F, r, g, b, fade * (0.2F + 0.18F * pulse3));

            // два скрещённых крыла
            quad(consumer, view, rel, axisUp.add(axisNormal), axisSide,
                    0.0F, 1.0F, r, g, b, fade);
            quad(consumer, view, rel, axisUp.multiply(-1.0).add(axisNormal), axisSide,
                    0.0F, 1.0F, r, g, b, fade);

            // тело
            quad(consumer, view, rel.add(butterfly.normal.multiply(0.004)),
                    butterfly.up.multiply(butterfly.scale * 0.085),
                    butterfly.side.multiply(butterfly.scale * 0.65),
                    2.0F, 3.0F, r, g, b, fade);

            // жилки + усики
            float veins = fade * (0.28F + 0.72F * pulse3);
            if (veins > 0.01F) {
                wingVeins(consumer, view, rel, butterfly, age, 1.0F, r, g, b, veins);
                wingVeins(consumer, view, rel, butterfly, age, -1.0F, r, g, b, veins);
            }
        }

        immediate.draw();
    }

    // --------------------------------------------------------------- симуляция

    /** Позиция бабочки в момент {@code time} (тики, дробные допускаются). */
    private Vec3d animatedPos(Butterfly butterfly, float time) {
        float age = Math.max(0.0F, time - butterfly.spawnTick);
        float life = age / butterfly.lifetime;
        double out = 0.17 + 0.5 * Math.sin(Math.PI * Math.min(1.0F, life));
        double sway = 0.12 * Math.sin(age * 0.075 + butterfly.phase);
        return butterfly.anchor
                .add(butterfly.normal.multiply(out))
                .add(butterfly.up.multiply(sway))
                .add(butterfly.side.multiply(sway * 0.55 * Math.cos(age * 0.06 + butterfly.phase)));
    }

    private boolean isChunkLoaded(BlockPos pos) {
        return world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private boolean isInsideSolid(Vec3d pos) {
        BlockPos blockPos = BlockPos.ofFloored(pos.x, pos.y, pos.z);
        if (!isChunkLoaded(blockPos)) return true;

        VoxelShape shape = world.getBlockState(blockPos).getOutlineShape(world, blockPos);
        if (shape.isEmpty()) return false;

        double x = pos.x - blockPos.getX();
        double y = pos.y - blockPos.getY();
        double z = pos.z - blockPos.getZ();
        for (Box box : shape.getBoundingBoxes()) {
            if (x > box.minX && x < box.maxX
                    && y > box.minY && y < box.maxY
                    && z > box.minZ && z < box.maxZ) {
                return true;
            }
        }
        return false;
    }

    private boolean spawn(Vec3d camera, float radius) {
        int bx = MathHelper.floor(camera.x + (random.nextDouble() * 2.0 - 1.0) * radius);
        int by = MathHelper.floor(camera.y + (random.nextDouble() * 2.0 - 1.0) * radius);
        int bz = MathHelper.floor(camera.z + (random.nextDouble() * 2.0 - 1.0) * radius);
        scratch.set(bx, by, bz);
        if (!isChunkLoaded(scratch)) return false;

        BlockState state = world.getBlockState(scratch);
        if (state.isAir()) return false;

        VoxelShape shape = state.getOutlineShape(world, scratch);
        if (shape.isEmpty()) return false;

        List<Box> boxes = shape.getBoundingBoxes();
        Box box = boxes.get(random.nextInt(boxes.size()));

        Direction direction = DIRECTIONS[directionCursor++ % DIRECTIONS.length];
        Vec3d normal = new Vec3d(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());

        double x = MathHelper.lerp(0.12 + random.nextDouble() * 0.76, box.minX, box.maxX);
        double y = MathHelper.lerp(0.12 + random.nextDouble() * 0.76, box.minY, box.maxY);
        double z = MathHelper.lerp(0.12 + random.nextDouble() * 0.76, box.minZ, box.maxZ);

        switch (direction) {
            case DOWN -> y = box.minY;
            case UP -> y = box.maxY;
            case NORTH -> z = box.minZ;
            case SOUTH -> z = box.maxZ;
            case WEST -> x = box.minX;
            case EAST -> x = box.maxX;
            default -> {
            }
        }

        Vec3d anchor = new Vec3d(bx + x, by + y, bz + z);
        if (anchor.squaredDistanceTo(camera) > radius * radius) return false;
        if (isInsideSolid(anchor.add(normal.multiply(0.025)))) return false;
        if (isInsideSolid(anchor.add(normal.multiply(0.35)))) return false;

        Vec3d tangent = direction.getAxis() == Direction.Axis.Y
                ? new Vec3d(1.0, 0.0, 0.0)
                : new Vec3d(0.0, 1.0, 0.0);
        Vec3d across = normal.crossProduct(tangent);
        double angle = random.nextDouble() * Math.PI * 2.0;
        Vec3d up = tangent.multiply(Math.cos(angle)).add(across.multiply(Math.sin(angle)));
        Vec3d side = normal.crossProduct(up);

        butterflies.add(new Butterfly(
                // именно тот блок, на грани которого сидит бабочка (не BlockPos.ofFloored(anchor)):
                // для UP/SOUTH/EAST anchor лежит ровно на max-грани (== 1.0) и floor уводит
                // на соседний блок — проверка состояния блока ломалась бы сразу после спавна
                new BlockPos(bx, by, bz),
                state,
                anchor,
                normal,
                up,
                side,
                tick,
                Math.round(180.0F * (0.75F + random.nextFloat() * 0.5F)),
                0.13F + random.nextFloat() * 0.1F,
                random.nextFloat() * (float) (Math.PI * 2.0)
        ));
        return true;
    }

    // -------------------------------------------------------------- геометрия

    /** Крыло: контур из WING_POINTS отрезков, плюс усики из середины контура. */
    private void wingVeins(VertexConsumer consumer, Matrix4f view, Vec3d center,
                           Butterfly butterfly, float age, float side,
                           float r, float g, float b, float alpha) {
        float phase = age * 0.18F + butterfly.phase + side * 1.7F;

        double prevX = 0.0, prevY = 0.0, prevZ = 0.0;
        float prevAlpha = 0.0F;

        for (int i = 0; i <= WING_POINTS; i++) {
            float t = i / (float) WING_POINTS;
            float sweep = -1.25F + t * 2.5F;
            float bump = (float) Math.sin(t * Math.PI);
            float flutter = (float) Math.sin(i * 2.17F + phase)
                    * (float) Math.sin(i * 1.43F - phase * 0.7F);

            double along = side * butterfly.scale
                    * (0.3 + Math.cos(sweep) + flutter * 0.18 * bump);
            double chord = butterfly.scale
                    * (Math.sin(sweep) * 1.12 + flutter * 0.12 * bump);
            double lift = butterfly.scale
                    * (0.2 + 0.16 * Math.sin(phase + i * 1.9F));

            double px = center.x + butterfly.up.x * along + butterfly.side.x * chord + butterfly.normal.x * lift;
            double py = center.y + butterfly.up.y * along + butterfly.side.y * chord + butterfly.normal.y * lift;
            double pz = center.z + butterfly.up.z * along + butterfly.side.z * chord + butterfly.normal.z * lift;

            float segmentAlpha = side * (0.12F + 0.88F * bump);

            if (i > 0) {
                streak(consumer, view, prevX, prevY, prevZ, px, py, pz,
                        butterfly.scale * 0.12F, r, g, b, prevAlpha, segmentAlpha);
            }

            if (i == 3) {
                double ax = px + (butterfly.up.x * side * 0.45 + butterfly.normal.x * 0.24) * butterfly.scale;
                double ay = py + (butterfly.up.y * side * 0.45 + butterfly.normal.y * 0.24) * butterfly.scale;
                double az = pz + (butterfly.up.z * side * 0.45 + butterfly.normal.z * 0.24) * butterfly.scale;

                double qx = ax + (butterfly.up.x * side * 0.22 + butterfly.normal.x * 0.40) * butterfly.scale;
                double qy = ay + (butterfly.up.y * side * 0.22 + butterfly.normal.y * 0.40) * butterfly.scale;
                double qz = az + (butterfly.up.z * side * 0.22 + butterfly.normal.z * 0.40) * butterfly.scale;

                streak(consumer, view, px, py, pz, ax, ay, az,
                        butterfly.scale * 0.09F, r, g, b, side * 0.7F, side * 0.4F);
                streak(consumer, view, ax, ay, az, qx, qy, qz,
                        butterfly.scale * 0.07F, r, g, b, side * 0.4F, 0.0F);
            }

            prevX = px;
            prevY = py;
            prevZ = pz;
            prevAlpha = segmentAlpha;
        }
    }

    /** Светящийся отрезок: квад, ширина задаётся половиной толщины. */
    private void streak(VertexConsumer consumer, Matrix4f view,
                        double ax, double ay, double az,
                        double bx, double by, double bz, double halfWidth,
                        float r, float g, float b, float alphaA, float alphaB) {
        double nx = ay * bz - az * by;
        double ny = az * bx - ax * bz;
        double nz = ax * by - ay * bx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-12) return;

        double scale = halfWidth / length;
        nx *= scale;
        ny *= scale;
        nz *= scale;

        vertex(consumer, view, ax - nx, ay - ny, az - nz, 6.0F, 0.0F, r, g, b, alphaA);
        vertex(consumer, view, ax + nx, ay + ny, az + nz, 6.0F, 1.0F, r, g, b, alphaA);
        vertex(consumer, view, bx + nx, by + ny, bz + nz, 7.0F, 1.0F, r, g, b, alphaB);
        vertex(consumer, view, bx - nx, by - ny, bz - nz, 7.0F, 0.0F, r, g, b, alphaB);
    }

    /** Квад в плоскости (axisU, axisV); UV.x = uMin..uMax — «тип» примитива. */
    private void quad(VertexConsumer consumer, Matrix4f view, Vec3d center,
                      Vec3d axisU, Vec3d axisV,
                      float uMin, float uMax,
                      float r, float g, float b, float a) {
        vertex(consumer, view,
                center.x - axisU.x - axisV.x,
                center.y - axisU.y - axisV.y,
                center.z - axisU.z - axisV.z, uMin, 0.0F, r, g, b, a);
        vertex(consumer, view,
                center.x - axisU.x + axisV.x,
                center.y - axisU.y + axisV.y,
                center.z - axisU.z + axisV.z, uMin, 1.0F, r, g, b, a);
        vertex(consumer, view,
                center.x + axisU.x + axisV.x,
                center.y + axisU.y + axisV.y,
                center.z + axisU.z + axisV.z, uMax, 1.0F, r, g, b, a);
        vertex(consumer, view,
                center.x + axisU.x - axisV.x,
                center.y + axisU.y - axisV.y,
                center.z + axisU.z - axisV.z, uMax, 0.0F, r, g, b, a);
    }

    private void vertex(VertexConsumer consumer, Matrix4f view,
                        double x, double y, double z, float u, float v,
                        float r, float g, float b, float a) {
        consumer.vertex(view, (float) x, (float) y, (float) z)
                .texture(u, v)
                .color(r, g, b, a);
    }

    // ------------------------------------------------------------------ данные

    private static final class Butterfly {
        final BlockPos pos;
        final BlockState state;
        final Vec3d anchor;
        final Vec3d normal;
        final Vec3d up;
        final Vec3d side;
        final int spawnTick;
        final int lifetime;
        final float scale;
        final float phase;
        int fadeTick = -1;

        Butterfly(BlockPos pos, BlockState state, Vec3d anchor, Vec3d normal, Vec3d up, Vec3d side,
                  int spawnTick, int lifetime, float scale, float phase) {
            this.pos = pos;
            this.state = state;
            this.anchor = anchor;
            this.normal = normal;
            this.up = up;
            this.side = side;
            this.spawnTick = spawnTick;
            this.lifetime = lifetime;
            this.scale = scale;
            this.phase = phase;
        }
    }
}
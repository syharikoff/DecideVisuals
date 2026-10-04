package ru.white.utils.render;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMaps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.ModelPartCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockRenderView;
import ru.white.mixin.RenderLayerAccessor;
import ru.white.mixin.RenderSetupAccessor;
import ru.white.mixin.TextureSpecAccessor;
import ru.white.mixin.WorldRendererAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;

/**
 * Захват геометрии модели сущности: прогоняем её реальный рендер через
 * перехватывающий VertexConsumer и группируем записанные вершины в
 * ориентированные боксы (Box) с UV, цветом, светом и текстурой.
 *
 * Нужен, чтобы разваливать модель на осколки, а не хитбокс.
 * Для игроков, чей рендер не удаётся захватить, есть ручной фолбэк
 * по стандартной сетке скина 64x64.
 */
public final class ModelBoxCapture {

    private static final int MAX_VERTICES = 24000;
    private static final float CORNER_EPS = 1.0E-4f;
    private static final float MIN_HALF = 0.004f;
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final OrderedRenderCommandQueueImpl STORAGE = new OrderedRenderCommandQueueImpl();
    private static final OutlineVertexConsumerProvider OUTLINE_SOURCE = new OutlineVertexConsumerProvider();
    private static final ModelCommandRenderer MODEL_RENDERER = new ModelCommandRenderer();
    private static final ModelPartCommandRenderer MODEL_PART_RENDERER = new ModelPartCommandRenderer();
    private static final Map<RenderLayer, Identifier> TEXTURE_CACHE = new HashMap<>();

    private static final CapturingConsumer CONSUMER = new CapturingConsumer();
    private static final CaptureSource SOURCE = new CaptureSource(CONSUMER);

    private static float[] volumes = new float[64];

    private ModelBoxCapture() {
    }

    public static List<Box> capture(LivingEntity entity, float partialTick, boolean skinOnly) {
        Identifier skin = skinOnly && entity instanceof AbstractClientPlayerEntity player
                ? player.getSkin().body().texturePath()
                : null;

        int fallbackLight = entityLight(entity);
        List<Box> rendered = captureRendered(entity, partialTick, skin, fallbackLight);
        if (rendered.isEmpty() && entity instanceof AbstractClientPlayerEntity player) {
            rendered = captureHumanoidFallback(player, partialTick, fallbackLight);
        }
        return skinOnly ? removeOverlays(rendered) : rendered;
    }

    /** Выкидывает мелкие оверлеи (огонь и т.п.), если рядом есть такой же объёмный бокс. */
    private static List<Box> removeOverlays(List<Box> boxes) {
        int size = boxes.size();
        if (size < 2) return boxes;

        if (volumes.length < size) volumes = new float[size];
        float[] vol = volumes;
        for (int i = 0; i < size; i++) {
            float[] half = boxes.get(i).half();
            vol[i] = half[0] * half[1] * half[2];
        }

        List<Box> kept = new ArrayList<>(size);
        outer:
        for (int i = 0; i < size; i++) {
            Box a = boxes.get(i);
            float va = vol[i];
            for (int j = 0; j < size; j++) {
                if (i == j) continue;
                Box b = boxes.get(j);
                float dx = a.cx - b.cx;
                float dy = a.cy - b.cy;
                float dz = a.cz - b.cz;
                if (dx * dx + dy * dy + dz * dz > 9.0E-4f) continue;
                if (va > vol[j] * 1.05f) continue outer;
            }
            kept.add(a);
        }
        return kept;
    }

    private static int entityLight(LivingEntity entity) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return FULL_BRIGHT;
        try {
            return WorldRenderer.getLightmapCoordinates((BlockRenderView) world,
                    BlockPos.ofFloored(entity.getX(), entity.getY() + entity.getHeight() * 0.5f, entity.getZ()));
        } catch (Throwable ignored) {
            return FULL_BRIGHT;
        }
    }

    private static List<Box> captureRendered(LivingEntity entity, float partialTick, Identifier filter, int fallbackLight) {
        MinecraftClient client = MinecraftClient.getInstance();
        EntityRenderManager dispatcher = client.getEntityRenderDispatcher();
        if (dispatcher == null) return Collections.emptyList();

        WorldRenderer worldRenderer = client.worldRenderer;
        if (worldRenderer == null) return Collections.emptyList();

        WorldRenderState worldRenderState =
                ((WorldRendererAccessor) worldRenderer).nightix$worldRenderState();
        if (worldRenderState == null) return Collections.emptyList();

        CameraRenderState cameraState = worldRenderState.cameraRenderState;
        if (cameraState == null) return Collections.emptyList();

        SOURCE.filter = filter;
        CONSUMER.reset(MAX_VERTICES, fallbackLight);
        STORAGE.clear();

        try {
            EntityRenderState state = dispatcher.getAndUpdateRenderState(entity, partialTick);
            if (state == null) return Collections.emptyList();

            state.outlineColor = 0;
            state.shadowPieces.clear();

            dispatcher.render(state, cameraState, 0.0, 0.0, 0.0, new MatrixStack(), STORAGE);

            for (BatchingRenderCommandQueue queue : STORAGE.getBatchingQueues().values()) {
                try {
                    MODEL_RENDERER.render(queue, SOURCE, OUTLINE_SOURCE, SOURCE);
                } catch (Throwable ignored) {
                }
                try {
                    MODEL_PART_RENDERER.render(queue, SOURCE, OUTLINE_SOURCE, SOURCE);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
            return Collections.emptyList();
        } finally {
            STORAGE.clear();
        }

        return buildBoxes(CONSUMER);
    }

    /** Ручная раскладка стандартных кубов скина, когда реальный рендер игрока не захватился. */
    private static List<Box> captureHumanoidFallback(AbstractClientPlayerEntity player, float partialTick, int fallbackLight) {
        SkinTexturesView skin = new SkinTexturesView(player);
        Identifier texture = skin.texturePath;
        boolean slim = skin.slim;

        float yaw = MathHelper.lerpAngleDegrees(partialTick, player.lastBodyYaw, player.bodyYaw);
        double angle = Math.toRadians(180.0f - yaw);
        float cs = (float) Math.cos(angle);
        float sn = (float) Math.sin(angle);

        CapturingConsumer consumer = CONSUMER;
        consumer.reset(MAX_VERTICES, fallbackLight);
        consumer.texture(texture);

        float armW = slim ? 3.0f : 4.0f;
        float armY = slim ? 0.5f : 0.0f;
        float armRX = slim ? -7.0f : -8.0f;

        float[][] parts = {
                {-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, 0.0f, 0.0f, 0.0f},
                {-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, 32.0f, 0.0f, 0.5f},
                {-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f, 16.0f, 16.0f, 0.0f},
                {-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f, 16.0f, 32.0f, 0.25f},
                {armRX, armY, -2.0f, armW, 12.0f, 4.0f, 40.0f, 16.0f, 0.0f},
                {armRX, armY, -2.0f, armW, 12.0f, 4.0f, 40.0f, 32.0f, 0.25f},
                {4.0f, armY, -2.0f, armW, 12.0f, 4.0f, 32.0f, 48.0f, 0.0f},
                {4.0f, armY, -2.0f, armW, 12.0f, 4.0f, 48.0f, 48.0f, 0.25f},
                {-3.9f, 12.0f, -2.0f, 4.0f, 12.0f, 4.0f, 0.0f, 16.0f, 0.0f},
                {-3.9f, 12.0f, -2.0f, 4.0f, 12.0f, 4.0f, 0.0f, 32.0f, 0.25f},
                {-0.1f, 12.0f, -2.0f, 4.0f, 12.0f, 4.0f, 16.0f, 48.0f, 0.0f},
                {-0.1f, 12.0f, -2.0f, 4.0f, 12.0f, 4.0f, 0.0f, 48.0f, 0.25f},
        };

        for (float[] p : parts) {
            appendSkinCube(consumer, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], cs, sn);
        }
        return buildBoxes(consumer);
    }

    private static void appendSkinCube(CapturingConsumer c,
                                       float ox, float oy, float oz,
                                       float w, float h, float d,
                                       float tu, float tv, float g,
                                       float cs, float sn) {
        float x0 = ox - g, y0 = oy - g, z0 = oz - g;
        float x1 = ox + w + g, y1 = oy + h + g, z1 = oz + d + g;

        float u0 = tu;
        float u1 = u0 + d;
        float u2 = u1 + w;
        float u3 = u2 + w;
        float u4 = u2 + d;
        float u5 = u4 + w;

        float v0 = tv;
        float v1 = v0 + d;
        float v2 = v1 + h;

        float[][] v = {
                {x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0},
                {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1},
        };

        skinQuad(c, v[5], v[4], v[0], v[1], u1, v0, u2, v1, cs, sn);
        skinQuad(c, v[2], v[3], v[7], v[6], u2, v1, u3, v0, cs, sn);
        skinQuad(c, v[0], v[4], v[7], v[3], u0, v1, u1, v2, cs, sn);
        skinQuad(c, v[1], v[0], v[3], v[2], u1, v1, u2, v2, cs, sn);
        skinQuad(c, v[5], v[1], v[2], v[6], u2, v1, u4, v2, cs, sn);
        skinQuad(c, v[4], v[5], v[6], v[7], u4, v1, u5, v2, cs, sn);
    }

    private static void skinQuad(CapturingConsumer c, float[] a, float[] b, float[] cc, float[] d,
                                 float uA, float vA, float uB, float vB, float cs, float sn) {
        skinVertex(c, a, uB, vA, cs, sn);
        skinVertex(c, b, uA, vA, cs, sn);
        skinVertex(c, cc, uA, vB, cs, sn);
        skinVertex(c, d, uB, vB, cs, sn);
    }

    private static void skinVertex(CapturingConsumer c, float[] p, float u, float v, float cs, float sn) {
        float lx = -p[0] / 16.0f;
        float ly = 1.501f - p[1] / 16.0f;
        float lz = p[2] / 16.0f;
        float wx = lx * cs + lz * sn;
        float wz = -lx * sn + lz * cs;
        c.vertex(wx, ly, wz);
        c.texture(u / 64.0f, v / 64.0f);
        c.color(-1);
    }

    private static List<Box> buildBoxes(CapturingConsumer c) {
        float[] pos = c.positions.elements();
        float[] uv = c.uvs.elements();
        int[] col = c.colors.elements();
        int[] lit = c.lights.elements();
        int[] ovl = c.overlays.elements();
        int[] tex = c.textures.elements();
        List<Identifier> textureList = c.textureList;

        int quadCount = c.positions.size() / 12;
        List<Box> result = new ArrayList<>(quadCount);

        for (int q = 0; q < quadCount; ) {
            int take = 1;
            // шесть квадов с одной текстурой и ровно 8 уникальными вершинами = цельный куб
            if (q + 6 <= quadCount && sameTexture(tex, q, 6) && uniqueCorners(pos, q, 6) == 8) {
                take = 6;
            }
            Identifier texture = textureList.get(tex[q * 4]);
            result.add(makeBox(pos, uv, col, lit, ovl, q, take, texture));
            q += take;
        }
        return result;
    }

    private static boolean sameTexture(int[] tex, int q, int count) {
        int first = tex[q * 4];
        for (int i = 1; i < count; i++) {
            if (tex[(q + i) * 4] != first) return false;
        }
        return true;
    }

    private static int uniqueCorners(float[] pos, int q, int count) {
        int n = count * 4;
        int base = q * 12;
        int unique = 0;
        for (int i = 0; i < n; i++) {
            int io = base + i * 3;
            float ix = pos[io], iy = pos[io + 1], iz = pos[io + 2];
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                int jo = base + j * 3;
                if (Math.abs(pos[jo] - ix) < CORNER_EPS
                        && Math.abs(pos[jo + 1] - iy) < CORNER_EPS
                        && Math.abs(pos[jo + 2] - iz) < CORNER_EPS) {
                    seen = true;
                    break;
                }
            }
            if (!seen) unique++;
            if (unique > 8) return unique;
        }
        return unique;
    }

    /** Строит локальный базис (3 ортонормированные оси) и полуоси из набора вершин. */
    private static Box makeBox(float[] pos, float[] uv, int[] col, int[] lightList, int[] overlayList,
                               int q, int count, Identifier texture) {
        int n = count * 4;
        int base = q * 12;

        float cx = 0.0f, cy = 0.0f, cz = 0.0f;
        for (int i = 0; i < n; i++) {
            cx += pos[base + i * 3];
            cy += pos[base + i * 3 + 1];
            cz += pos[base + i * 3 + 2];
        }
        cx /= n;
        cy /= n;
        cz /= n;

        float ax = pos[base + 3] - pos[base];
        float ay = pos[base + 4] - pos[base + 1];
        float az = pos[base + 5] - pos[base + 2];
        float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1.0E-6f) {
            ax = 1.0f; ay = 0.0f; az = 0.0f; al = 1.0f;
        }
        ax /= al; ay /= al; az /= al;

        float bx = pos[base + 9] - pos[base];
        float by = pos[base + 10] - pos[base + 1];
        float bz = pos[base + 11] - pos[base + 2];

        // ортогонализуем вторую ось относительно первой (проекция Грама-Шмидта)
        float dotAB = ax * bx + ay * by + az * bz;
        bx -= ax * dotAB;
        by -= ay * dotAB;
        bz -= az * dotAB;
        float bl = (float) Math.sqrt(bx * bx + by * by + bz * bz);
        if (bl < 1.0E-6f) {
            float hx = Math.abs(ay) < 0.9f ? 0.0f : 1.0f;
            float hy = Math.abs(ay) < 0.9f ? 1.0f : 0.0f;
            bx = ay * 0.0f - az * hy;
            by = az * hx - ax * 0.0f;
            bz = ax * hy - ay * hx;
            bl = (float) Math.sqrt(bx * bx + by * by + bz * bz);
            if (bl < 1.0E-6f) {
                bx = 0.0f; by = 1.0f; bz = 0.0f; bl = 1.0f;
            }
        }
        bx /= bl; by /= bl; bz /= bl;

        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;

        float ha = 0.0f, hb = 0.0f, hn = 0.0f;
        float[] verts = new float[n * 3];
        float[] uvs = new float[n * 2];

        for (int i = 0; i < n; i++) {
            float lx = pos[base + i * 3] - cx;
            float ly = pos[base + i * 3 + 1] - cy;
            float lz = pos[base + i * 3 + 2] - cz;
            verts[i * 3] = lx;
            verts[i * 3 + 1] = ly;
            verts[i * 3 + 2] = lz;
            uvs[i * 2] = uv[(q * 4 + i) * 2];
            uvs[i * 2 + 1] = uv[(q * 4 + i) * 2 + 1];
            ha = Math.max(ha, Math.abs(lx * ax + ly * ay + lz * az));
            hb = Math.max(hb, Math.abs(lx * bx + ly * by + lz * bz));
            hn = Math.max(hn, Math.abs(lx * nx + ly * ny + lz * nz));
        }

        int[] colors = new int[count];
        int[] lights = new int[count];
        int[] overlays = new int[count];
        for (int i = 0; i < count; i++) {
            colors[i] = col[(q + i) * 4];
            lights[i] = lightList[(q + i) * 4];
            overlays[i] = overlayList[(q + i) * 4];
        }

        return new Box(cx, cy, cz,
                new float[]{ax, ay, az, bx, by, bz, nx, ny, nz},
                new float[]{Math.max(ha, MIN_HALF), Math.max(hb, MIN_HALF), Math.max(hn, MIN_HALF)},
                verts, uvs, colors, lights, overlays, texture);
    }

    private static Identifier textureOf(RenderLayer type) {
        Identifier cached = TEXTURE_CACHE.get(type);
        if (cached != null) return cached;

        if (TEXTURE_CACHE.size() > 512) TEXTURE_CACHE.clear();

        Identifier resolved = resolveTexture(type);
        TEXTURE_CACHE.put(type, resolved);
        return resolved;
    }

    private static Identifier resolveTexture(RenderLayer type) {
        try {
            RenderSetup setup = (RenderSetup) ((RenderLayerAccessor) (Object) type).nightix$getRenderSetup();
            if (setup == null) return null;

            // RenderSetup final: каст через Object, иначе компилятор считает его невозможным
            Map<String, Object> textures = ((RenderSetupAccessor) (Object) setup).nightix$getTextures();
            if (textures == null) return null;

            Object spec = textures.get("Sampler0");
            if (!(spec instanceof TextureSpecAccessor specAccessor)) return null;

            Identifier location = specAccessor.nightix$location();
            if (location == null) return null;
            if (location.getPath().contains("glint")) return null;
            return location;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isFinite(float value) {
        return Float.isFinite(value) && Math.abs(value) < 512.0f;
    }

    // ================= Вложенные классы =================

    private record SkinTexturesView(Identifier texturePath, boolean slim) {
        SkinTexturesView(AbstractClientPlayerEntity player) {
            this(player.getSkin().body().texturePath(), player.getSkin().model() == PlayerSkinType.SLIM);
        }
    }

    private static final class CaptureSource extends VertexConsumerProvider.Immediate {
        private static final BufferAllocator SCRATCH = new BufferAllocator(256);

        private final CapturingConsumer consumer;
        private Identifier filter;

        CaptureSource(CapturingConsumer consumer) {
            super(SCRATCH, (SequencedMap) Object2ObjectSortedMaps.emptyMap());
            this.consumer = consumer;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer renderType) {
            Identifier texture = textureOf(renderType);
            Identifier current = this.filter;
            consumer.texture(current != null && !Objects.equals(texture, current) ? null : texture);
            return consumer;
        }
    }

    private static final class CapturingConsumer implements VertexConsumer {
        final FloatArrayList positions = new FloatArrayList();
        final FloatArrayList uvs = new FloatArrayList();
        final IntArrayList colors = new IntArrayList();
        final IntArrayList lights = new IntArrayList();
        final IntArrayList overlays = new IntArrayList();
        final IntArrayList textures = new IntArrayList();
        final List<Identifier> textureList = new ArrayList<>();

        private int maxVertices;
        private int fallbackLight = FULL_BRIGHT;
        private int textureIndex = -1;
        private boolean accepted;

        void reset(int maxVertices, int fallbackLight) {
            this.maxVertices = maxVertices;
            this.fallbackLight = fallbackLight;
            positions.clear();
            uvs.clear();
            colors.clear();
            lights.clear();
            overlays.clear();
            textures.clear();
            textureList.clear();
            textureIndex = -1;
            accepted = false;
        }

        void texture(Identifier texture) {
            if (texture == null) {
                textureIndex = -1;
                return;
            }
            int index = textureList.indexOf(texture);
            if (index < 0) {
                index = textureList.size();
                textureList.add(texture);
            }
            textureIndex = index;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            accepted = textureIndex >= 0
                    && textures.size() < maxVertices
                    && isFinite(x) && isFinite(y) && isFinite(z);
            if (accepted) {
                positions.add(x);
                positions.add(y);
                positions.add(z);
                uvs.add(0.0f);
                uvs.add(0.0f);
                colors.add(-1);
                lights.add(fallbackLight);
                overlays.add(OverlayTexture.DEFAULT_UV);
                textures.add(textureIndex);
            }
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            if (accepted) colors.set(colors.size() - 1, alpha << 24 | red << 16 | green << 8 | blue);
            return this;
        }

        @Override
        public VertexConsumer color(int color) {
            if (accepted) colors.set(colors.size() - 1, color);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            if (accepted) {
                uvs.set(uvs.size() - 2, u);
                uvs.set(uvs.size() - 1, v);
            }
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            if (accepted) overlays.set(overlays.size() - 1, u & 0xFFFF | (v & 0xFFFF) << 16);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            if (accepted) lights.set(lights.size() - 1, u & 0xFFFF | (v & 0xFFFF) << 16);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
        }
    }

    /** Ориентированный бокс модели: центр, базис, полуоси, вершины, UV, цвет/свет/оверлей, текстура. */
    public record Box(float cx, float cy, float cz,
                       float[] axes, float[] half,
                       float[] verts, float[] uvs,
                       int[] colors, int[] lights, int[] overlays,
                       Identifier texture) {

        public int quadCount() {
            return colors.length;
        }
    }
}

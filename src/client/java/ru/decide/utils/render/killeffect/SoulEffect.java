package ru.decide.utils.render.killeffect;

import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMaps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.ModelPartCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.decide.mixin.RenderLayerAccessor;
import ru.decide.mixin.RenderSetupAccessor;
import ru.decide.mixin.TextureSpecAccessor;
import ru.decide.mixin.WorldRendererAccessor;
import ru.decide.utils.render.NoopVertexConsumer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Function;

/**
 * Режим Kill Effect «Душа».
 *
 * Убитая цель остаётся на месте полупрозрачной цветной копией, которая
 * поднимается вверх, по желанию проворачивается на 360° и растворяется.
 *
 * Копия — реальная модель того же entity (для игроков через OtherClientPlayerEntity
 * с тем же профилем, для мобов через EntityType.create + copyFrom). Цвет и
 * прозрачность задаются умножением vertexColor: шейдер сущностей делает
 * color *= vertexColor * ColorModulator, поэтому тонировка получается без
 * кастомного шейдера — ровно как через старый RenderSystem.setShaderColor.
 *
 * Рендер идёт тем же путём, что и захват модели в ModelBoxCapture:
 * dispatcher -> очередь команд -> ModelPartCommandRenderer в свой буфер.
 */
public final class SoulEffect {

    private static final int MAX_GHOSTS = 6;
    private static final float GHOST_PITCH = -12.0F;
    private static final float SPIN_MS = 650.0F;

    private static final SoulEffect INSTANCE = new SoulEffect();

    private static final OrderedRenderCommandQueueImpl QUEUE = new OrderedRenderCommandQueueImpl();
    private static final ModelCommandRenderer MODEL_RENDERER = new ModelCommandRenderer();
    private static final ModelPartCommandRenderer PART_RENDERER = new ModelPartCommandRenderer();
    private static final OutlineVertexConsumerProvider OUTLINE_SOURCE = new OutlineVertexConsumerProvider();

    /** Свой слой на каждую текстуру модели — иначе призрак был бы одноцветным. */
    private static final Function<Identifier, RenderLayer> GHOST_LAYERS =
            Util.memoize(SoulEffect::buildGhostLayer);

    private final GhostSource source = new GhostSource();
    private final Map<RenderLayer, Identifier> textureCache = new HashMap<>();

    private final List<Ghost> ghosts = new ArrayList<>();
    private final List<LivingEntity> pending = new ArrayList<>();

    /** Пустая матрица: мировые координаты призрака уже зашиты в модель. */
    private static final MatrixStack BLANK = new MatrixStack();

    private SoulEffect() {
    }

    public static SoulEffect get() {
        return INSTANCE;
    }

    public synchronized void queue(LivingEntity entity) {
        if (entity == null) return;
        pending.add(entity);
    }

    public synchronized void clear() {
        ghosts.clear();
        pending.clear();
        QUEUE.clear();
    }

    public synchronized void render(MatrixStack matrices, Vec3d cam, Settings settings, float partialTick) {
        if (matrices == null || cam == null || settings == null) return;

        spawnPending();

        long now = System.currentTimeMillis();
        ghosts.removeIf(g -> now - g.bornMs >= settings.durationMs());
        if (ghosts.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        EntityRenderManager dispatcher = client.getEntityRenderDispatcher();
        WorldRenderer worldRenderer = client.worldRenderer;
        if (dispatcher == null || worldRenderer == null) return;

        WorldRenderState worldRenderState = ((WorldRendererAccessor) worldRenderer).decide$worldRenderState();
        if (worldRenderState == null) return;
        CameraRenderState cameraState = worldRenderState.cameraRenderState;
        if (cameraState == null) return;

        int color = settings.color();
        // «светлота» поднимает канал к белому: 0 — чистый цвет, 1 — белый призрак
        float lift = MathHelper.clamp(settings.brightness(), 0.0F, 1.0F);
        float tintR = ((color >> 16) & 0xFF) / 255.0F;
        float tintG = ((color >> 8) & 0xFF) / 255.0F;
        float tintB = (color & 0xFF) / 255.0F;
        tintR += (1.0F - tintR) * lift;
        tintG += (1.0F - tintG) * lift;
        tintB += (1.0F - tintB) * lift;
        float transparency = MathHelper.clamp(settings.transparency(), 0.0F, 1.0F);

        boolean drew = false;

        for (Ghost ghost : ghosts) {
            long age = now - ghost.bornMs;
            float t = MathHelper.clamp(age / (float) settings.durationMs(), 0.0F, 1.0F);

            float fadeIn = KillEffectMath.smoothStep(0.0F, 0.08F, t);
            float fadeOut = 1.0F - KillEffectMath.smoothStep(0.5F, 1.0F, t);
            float alpha = ((color >>> 24) & 0xFF) / 255.0F * transparency * fadeIn * fadeOut;
            if (alpha <= 0.004F) continue;

            float rise = KillEffectMath.easeOutCubic(t) * settings.rise();
            float spin = settings.rotate360()
                    ? 360.0F * KillEffectMath.easeOutCubic(MathHelper.clamp(age / SPIN_MS, 0.0F, 1.0F))
                    : 0.0F;

            // вершины модели приходят в локальных координатах (диспетчер переносит их по
            // переданным x/y/z). Добавляем позицию призрака в camera-relative и
            // пропускаем через матрицу события — ровно так же, как все остальные
            // рендеры модуля. Без неё модель не повёрнута в вид и уезжает за кадр.
            Entity entity = ghost.entity;
            double wx = ghost.origin.x;
            double wy = ghost.origin.y + rise;
            double wz = ghost.origin.z;

            float yaw = ghost.yaw + spin;
            entity.refreshPositionAndAngles(wx, wy, wz, yaw, GHOST_PITCH);
            entity.lastRenderX = wx;
            entity.lastRenderY = wy;
            entity.lastRenderZ = wz;
            // last-углы обязаны совпадать с текущими, иначе интерполяция между тиками
            // закручивает призрак (в оригинале так же сделано в setupSoulEntity)
            entity.lastYaw = yaw;
            entity.lastPitch = GHOST_PITCH;
            entity.setHeadYaw(yaw);
            entity.setBodyYaw(yaw);
            if (entity instanceof LivingEntity living) {
                living.lastHeadYaw = yaw;
                living.lastBodyYaw = yaw;
            }

            source.setTint(tintR, tintG, tintB, alpha);
            source.setPlacement(matrices.peek(), (float) (wx - cam.x), (float) (wy - cam.y), (float) (wz - cam.z));

            drew |= drawGhost(dispatcher, cameraState, entity, partialTick, 0.0, 0.0, 0.0);
        }

        if (drew) source.draw();
    }

    private boolean drawGhost(EntityRenderManager dispatcher, CameraRenderState cameraState,
                              Entity ghost, float partialTick, double x, double y, double z) {
        try {
            EntityRenderState state = dispatcher.getAndUpdateRenderState(ghost, partialTick);
            if (state == null || state.invisible) return false;

            state.outlineColor = EntityRenderState.NO_OUTLINE;
            state.displayName = null;
            state.shadowPieces.clear();

            QUEUE.clear();
            dispatcher.render(state, cameraState, x, y, z, BLANK, QUEUE);

            int queues = 0;
            for (BatchingRenderCommandQueue queue : QUEUE.getBatchingQueues().values()) {
                queues++;
                MODEL_RENDERER.render(queue, source, OUTLINE_SOURCE, source);
                PART_RENDERER.render(queue, source, OUTLINE_SOURCE, source);
            }
            QUEUE.clear();
            return queues > 0;
        } catch (Throwable ignored) {
            QUEUE.clear();
            return false;
        }
    }

    // ───────────────────────────── призраки ─────────────────────────────

    private void spawnPending() {
        if (pending.isEmpty()) return;
        List<LivingEntity> due = new ArrayList<>(pending);
        pending.clear();

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;

        for (LivingEntity entity : due) {
            if (ghosts.size() >= MAX_GHOSTS) break;

            Entity ghost = createGhost(world, entity);
            if (ghost == null) continue;

            ghosts.add(new Ghost(
                    ghost,
                    new Vec3d(entity.getX(), entity.getY(), entity.getZ()),
                    entity.getYaw(),
                    System.currentTimeMillis()));
        }
    }

    private Entity createGhost(ClientWorld world, LivingEntity source) {
        try {
            if (source instanceof AbstractClientPlayerEntity player) {
                OtherClientPlayerEntity ghost = new OtherClientPlayerEntity(world, player.getGameProfile());
                ghost.refreshPositionAndAngles(source.getX(), source.getY(), source.getZ(), source.getYaw(), source.getPitch());
                ghost.setHeadYaw(source.headYaw);
                ghost.setBodyYaw(source.bodyYaw);
                ghost.setPose(source.getPose());
                ghost.setSneaking(source.isSneaking());
                ghost.setOnGround(true);
                ghost.setSilent(true);
                clearEquipment(ghost);
                return ghost;
            }

            Entity ghost = source.getType().create(world, SpawnReason.COMMAND);
            if (ghost == null) return null;
            ghost.copyFrom(source);
            ghost.refreshPositionAndAngles(source.getX(), source.getY(), source.getZ(), source.getYaw(), source.getPitch());
            ghost.setHeadYaw(source.headYaw);
            ghost.setBodyYaw(source.bodyYaw);
            ghost.setPose(source.getPose());
            return ghost;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** У призрака не должно быть брони/предметов в руках — как в оригинале. */
    private static void clearEquipment(LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            entity.equipStack(slot, ItemStack.EMPTY);
        }
    }

    // ───────────────────────────── буфер со слоями-призраками ─────────────────────────────

    private static RenderLayer buildGhostLayer(Identifier texture) {
        RenderSetup setup = RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE)
                .texture("Sampler0", texture)
                .translucent()
                .expectedBufferSize(1 << 15)
                .build();
        return RenderLayer.of("kill_effect_soul_" + texture.getPath().replace('/', '_'), setup);
    }

    /** Отдаёт буфер своего слоя вместо ванильного и тонирует вершины. */
    private final class GhostSource extends VertexConsumerProvider.Immediate {

        private float tintR = 1.0F;
        private float tintG = 1.0F;
        private float tintB = 1.0F;
        private float alpha = 1.0F;
        private MatrixStack.Entry pose;
        private float placeX, placeY, placeZ;

        private final TintedConsumer tinted = new TintedConsumer();

        GhostSource() {
            // пустая карта слоёв -> все буферы берутся из общего скретч-аллокатора
            super(new BufferAllocator(1 << 16), (SequencedMap) Object2ObjectSortedMaps.emptyMap());
        }

        void setTint(float r, float g, float b, float a) {
            tintR = r;
            tintG = g;
            tintB = b;
            alpha = a;
            tinted.inner = null;
        }

        void setPlacement(MatrixStack.Entry pose, double x, double y, double z) {
            this.pose = pose;
            this.placeX = (float) x;
            this.placeY = (float) y;
            this.placeZ = (float) z;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer renderType) {
            Identifier texture = textureOf(renderType);
            if (texture == null) return NoopVertexConsumer.INSTANCE;

            tinted.inner = super.getBuffer(GHOST_LAYERS.apply(texture));
            tinted.tintR = tintR;
            tinted.tintG = tintG;
            tinted.tintB = tintB;
            tinted.alpha = alpha;
            tinted.pose = pose;
            tinted.placeX = placeX;
            tinted.placeY = placeY;
            tinted.placeZ = placeZ;
            return tinted;
        }

        private Identifier textureOf(RenderLayer type) {
            Identifier cached = textureCache.get(type);
            if (cached != null) return cached;

            if (textureCache.size() > 512) textureCache.clear();

            Identifier resolved = resolveTexture(type);
            textureCache.put(type, resolved);
            return resolved;
        }

        private Identifier resolveTexture(RenderLayer type) {
            try {
                RenderSetup setup = ((RenderLayerAccessor) (Object) type).decide$getRenderSetup();
                if (setup == null) return null;

                Map<String, Object> textures = ((RenderSetupAccessor) (Object) setup).decide$getTextures();
                if (textures == null) return null;

                Object spec = textures.get("Sampler0");
                if (!(spec instanceof TextureSpecAccessor accessor)) return null;

                Identifier location = accessor.decide$location();
                if (location == null) return null;
                if (location.getPath().contains("glint")) return null;
                return location;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

/**
 * Переносит вершины из локальных координат модели в camera-relative и пропускает
 * через матрицу события, умножает цвет на цвет призрака и его прозрачность и
 * заставляет модель светиться. Шейдер сущностей:
 * color *= vertexColor * ColorModulator, затем (кроме EMISSIVE) color *= lightMapColor.
 */
    private static final class TintedConsumer implements VertexConsumer {
        MatrixStack.Entry pose;
        VertexConsumer inner;
        float tintR = 1.0F, tintG = 1.0F, tintB = 1.0F, alpha = 1.0F;
        float placeX, placeY, placeZ;

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            inner.vertex(pose, x + placeX, y + placeY, z + placeZ);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            inner.color(Math.round(r * tintR), Math.round(g * tintG), Math.round(b * tintB),
                    Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer color(int argb) {
            color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            inner.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            inner.overlay(OverlayTexture.DEFAULT_UV);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            inner.light(240, 240);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            inner.normal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            inner.lineWidth(width);
            return this;
        }
    }

    /** Параметры режима «Душа». */
    public record Settings(float rise, boolean rotate360, int color, long durationMs,
                           float transparency, float brightness) {
    }

    private static final class Ghost {
        final Entity entity;
        final Vec3d origin;
        final float yaw;
        final long bornMs;

        Ghost(Entity entity, Vec3d origin, float yaw, long bornMs) {
            this.entity = entity;
            this.origin = origin;
            this.yaw = yaw;
            this.bornMs = bornMs;
        }
    }
}
package ru.decide.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.decide.manager.event_impl.EventRender3D;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.ModeSetting;
import ru.decide.module.api.settings.impl.SliderSetting;
import ru.decide.module.impl.render.lootview.LootShape;
import ru.decide.module.impl.render.lootview.LootViewShapes;
import ru.decide.utils.colors.ColorUtil;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

/**
 * Портированный Kimiko Loot View: над каждым выброшенным предметом рисуются
 * мерцающие иглы по силуэту его модели, цвет берётся из текстуры предмета.
 */
@ModuleInfo(
        name = "Loot View",
        desc = "Рисует мерцающие иглы по силуэту выброшенных предметов",
        category = Category.VISUALS
)
public final class LootView extends Module {

    private static final int SIDES = 8;
    private static final String DENSITY_LOW = "Низкая";
    private static final String DENSITY_MEDIUM = "Средняя";
    private static final String DENSITY_HIGH = "Высокая";

    private static final float[] COS = new float[SIDES];
    private static final float[] SIN = new float[SIDES];

    private static final RenderPipeline LOOT_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("decide", "pipeline/loot_view"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final RenderLayer LOOT_LAYER = RenderLayer.of(
            "client_loot_view",
            RenderSetup.builder(LOOT_PIPELINE).translucent().expectedBufferSize(8192).build()
    );

    static {
        for (int i = 0; i < SIDES; i++) {
            COS[i] = (float) Math.cos((float) Math.PI * 2 * i / SIDES);
            SIN[i] = (float) Math.sin((float) Math.PI * 2 * i / SIDES);
        }
    }

    public final SliderSetting distance = new SliderSetting(this, "Дистанция", 32F, 8F, 64F, 1F);
    public final SliderSetting needleHeight = new SliderSetting(this, "Высота игл", 1.0F, 0.4F, 1.6F, 0.05F);
    public final SliderSetting silhouetteScale = new SliderSetting(this, "Масштаб", 1.0F, 0.5F, 1.5F, 0.05F);
    public final ModeSetting density = new ModeSetting(this, "Плотность", DENSITY_MEDIUM, DENSITY_LOW, DENSITY_HIGH);

    @Override
    protected void onDisable() {
        LootViewShapes.clear();
    }

    @EventHandler
    public void onRender(EventRender3D event) {
        ClientWorld level = mc.world;
        if (level == null || mc.player == null) return;

        Camera camera = mc.gameRenderer == null ? null : mc.gameRenderer.getCamera();
        if (camera == null) return;

        Vec3d cam = camera.getCameraPos();
        float partialTicks = event.getTickDelta();

        double maxDist = distance.getValue();
        double maxDistSqr = maxDist * maxDist;
        int pointCount = density.is(DENSITY_LOW) ? 7 : (density.is(DENSITY_HIGH) ? 15 : 11);

        RenderLayer layer = LOOT_LAYER;
        VertexConsumerProvider.Immediate provider = mc.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack.Entry pose = event.getMatrixStack().peek();

        float time = (System.nanoTime() / 1_000_000L % 3_600_000L) / 1000.0f;
        VertexConsumer consumer = null;

        for (Entity entity : level.getEntities()) {
            if (!(entity instanceof ItemEntity item) || entity.isRemoved()) continue;

            ItemStack stack = item.getStack();
            if (stack.isEmpty()) continue;

            double distSqr = entity.squaredDistanceTo(cam.x, cam.y, cam.z);
            if (distSqr > maxDistSqr) continue;

            if (consumer == null) consumer = provider.getBuffer(layer);

            double actualDistance = Math.sqrt(distSqr);
            float fade = MathHelper.clamp((float) ((maxDist - actualDistance) / (maxDist * 0.2f)), 0.0f, 1.0f);

            renderNeedles(pose, consumer, item, cam.x, cam.y, cam.z, partialTicks, time, pointCount, fade, (float) actualDistance);
        }

        if (consumer != null) provider.draw(layer);
    }

    private void renderNeedles(MatrixStack.Entry pose, VertexConsumer consumer, ItemEntity entity,
                               double camX, double camY, double camZ, float partialTicks,
                               float time, int pointCount, float fade, float distanceToCamera) {
        ItemStack stack = entity.getStack();
        LootShape shape = LootViewShapes.get(stack, pointCount);
        if (shape == null) return;

        Vec3d pos = entity.getLerpedPos(partialTicks);

        float baseX = (float) (pos.x - camX);
        float baseY = (float) (pos.y - camY) + 0.02f;
        float baseZ = (float) (pos.z - camZ);

        // предмет «вырастает» из земли первые 12 тиков
        float grow = MathHelper.clamp((entity.getItemAge() + partialTicks) / 12.0f, 0.0f, 1.0f);
        float growEase = 1.0f - (1.0f - grow) * (1.0f - grow) * (1.0f - grow);

        float height = needleHeight.getValue() * 1.2f * growEase;
        float scale = silhouetteScale.getValue() * (0.4f + 0.6f * growEase);
        float seedBase = (entity.getId() & 0xFFFF) * 0.137f;

        int color = shape.color();

        // толщина иглы растёт с дистанцией, инато вблизи всё сливается в кашу
        float distanceFactor = MathHelper.clamp(distanceToCamera / 16.0f, 0.0f, 4.0f);
        float adaptiveThickness = 0.01275f * (1.0f + distanceFactor * 0.045f);
        float minimumThickness = distanceToCamera * 0.0012f;
        float baseSpikeRadius = Math.max(adaptiveThickness, minimumThickness);

        int count = shape.count();
        for (int i = 0; i < count; i++) {
            // у каждой иглы свой период и фаза — получается мерцание без синхронизации
            float period = 1.1f + 0.5f * hash(i * 7.31f + seedBase);
            float local = time / period + hash(i * 3.7f + seedBase) * 4.0f;
            float cycle = (float) Math.floor(local);
            float p = local - cycle;

            float env = p < 0.3f ? p / 0.3f : (p > 0.7f ? (1.0f - p) / 0.3f : 1.0f);
            env *= env * (3.0f - 2.0f * env);
            env = smoothstep(0.0f, 1.0f, env);
            if (env <= 0.015f) continue;

            float spread = 0.175f;
            float jx = (hash(i * 12.9898f + cycle * 78.233f + seedBase) - 0.5f) * spread;
            float jz = (hash(i * 45.164f + cycle * 94.673f + seedBase) - 0.5f) * spread;

            float wx = baseX + shape.xs()[i] * scale + jx;
            float wz = baseZ + shape.zs()[i] * scale + jz;

            float hRand = 0.7f + 0.6f * hash(i * 27.17f + cycle * 17.929f + seedBase);
            float h = height * shape.jitter()[i] * hRand * env;
            if (h <= 0.01f) continue;

            float alpha = 0.9f * env * fade;
            float headRadius = 0.041399997f * (0.4f + 0.6f * env);
            float spikeRadius = baseSpikeRadius * (0.5f + 0.5f * env);

            int headCenterColor = ColorUtil.applyOpacity(color, Math.min(1.0f, alpha * 1.25f));
            int headEdgeColor = ColorUtil.applyOpacity(color, alpha * 0.3f);
            int baseColor = ColorUtil.applyOpacity(color, alpha);
            int apexColor = ColorUtil.applyOpacity(ColorUtil.interpolateColor(color, -1, 0.45f), alpha * 0.2f);

            float headY = baseY + 0.004f;
            float apexY = baseY + h;

            // «грибок» на макушке: веер треугольников от центра к окружности
            for (int j = 0; j < SIDES; j++) {
                int k = (j + 1) % SIDES;
                float ex0 = wx + COS[j] * headRadius;
                float ez0 = wz + SIN[j] * headRadius;
                float ex1 = wx + COS[k] * headRadius;
                float ez1 = wz + SIN[k] * headRadius;

                consumer.vertex(pose, wx, headY, wz).color(headCenterColor);
                consumer.vertex(pose, ex0, headY, ez0).color(headEdgeColor);
                consumer.vertex(pose, ex1, headY, ez1).color(headEdgeColor);
                consumer.vertex(pose, ex1, headY, ez1).color(headEdgeColor);
            }

            // конус иглы
            for (int j = 0; j < SIDES; j++) {
                int k = (j + 1) % SIDES;
                float bx0 = wx + COS[j] * spikeRadius;
                float bz0 = wz + SIN[j] * spikeRadius;
                float bx1 = wx + COS[k] * spikeRadius;
                float bz1 = wz + SIN[k] * spikeRadius;

                consumer.vertex(pose, bx0, baseY, bz0).color(baseColor);
                consumer.vertex(pose, bx1, baseY, bz1).color(baseColor);
                consumer.vertex(pose, wx, apexY, wz).color(apexColor);
                consumer.vertex(pose, wx, apexY, wz).color(apexColor);
            }

            // мягкий ореол вокруг иглы
            float haloRadius = spikeRadius * 2.8f;
            int haloBaseColor = ColorUtil.applyOpacity(color, alpha * 0.16f);
            int haloApexColor = ColorUtil.applyOpacity(color, 0.0f);
            float haloApexY = baseY + h * 0.92f;

            for (int j = 0; j < SIDES; j++) {
                int k = (j + 1) % SIDES;
                float bx0 = wx + COS[j] * haloRadius;
                float bz0 = wz + SIN[j] * haloRadius;
                float bx1 = wx + COS[k] * haloRadius;
                float bz1 = wz + SIN[k] * haloRadius;

                consumer.vertex(pose, bx0, baseY, bz0).color(haloBaseColor);
                consumer.vertex(pose, bx1, baseY, bz1).color(haloBaseColor);
                consumer.vertex(pose, wx, haloApexY, wz).color(haloApexColor);
                consumer.vertex(pose, wx, haloApexY, wz).color(haloApexColor);
            }
        }
    }

    /** Детерминированный псевдослучайный шум в [0,1) по индексу иглы и фазе. */
    private static float hash(float value) {
        float s = (float) Math.sin(value) * 43758.547f;
        return s - (float) Math.floor(s);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = MathHelper.clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}

package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.manager.event_impl.EventJump;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.preview.ModulePreview;
import ru.white.module.api.preview.PreviewContext;
import ru.white.module.api.preview.PreviewSettings;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easing;
import ru.white.utils.animation.Easings;
import ru.white.utils.colors.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@ModuleInfo(
        name = "Jump Circle",
        desc = "Кружочек под тобой при прижке",
        category = Category.VISUALS
)
public class JumpCircle extends Module implements ModulePreview {
    private final List<Circle> circles = new ArrayList<>();

    public ButtonSetting previewButton = PreviewSettings.button(this);

    public ModeSetting circleMode = new ModeSetting(this, "Круг", "Gradient", "Evola", "Ring");
    public ModeSetting animMode = new ModeSetting(this, "Анимация", "Bounce", "Smooth", "Elastic", "Spring", "Expo");
    public SliderSetting size_c = new SliderSetting(this, "Размер", 1, 0.2F, 3, 0.1F);
    public BooleanSetting glow = new BooleanSetting(this, "Свечение", true).setVisible(() -> circleMode.is("Gradient"));

    public SliderSetting ringTime = new SliderSetting(this, "Время жизни", 1500, 500, 5000, 50)
            .setVisible(() -> circleMode.is("Ring"));
    public SliderSetting ringThickness = new SliderSetting(this, "Толщина кольца", 0.5F, 0.3F, 0.8F, 0.05F)
            .setVisible(() -> circleMode.is("Ring"));
    public ModeSetting ringEasing = new ModeSetting(this, "Анимация кольца", "Обычная", "Обычная", "Эластичная", "Назад")
            .setVisible(() -> circleMode.is("Ring"));
    public ModeSetting ringColorMode = new ModeSetting(this, "Цвет кольца", "Радуга", "Радуга", "Клиент", "Свой")
            .setVisible(() -> circleMode.is("Ring"));
    public ColorSetting ringColor = new ColorSetting(this, "Свой цвет кольца", 0xFFFFFFFF)
            .setVisible(() -> circleMode.is("Ring") && ringColorMode.is("Свой"));

    private final PreviewSettings previewSettings = PreviewSettings.of(this, 3.5F, 0F, 2F);

    BufferAllocator allocator = new BufferAllocator(1 << 18);

    private static final int QUAD_BUFFER_SIZE_BYTES = 1 << 10;
    private static final RenderPipeline TEXTURED_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/world/textured_quads"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderPipeline RING_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/world/ring_quads"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );
    private static final RenderLayer RING_QUADS = RenderLayer.of("ring_quads",
            RenderSetup.builder(RING_QUADS_PIPELINE).translucent().expectedBufferSize(16384).build());

    private static final Identifier GRADIENT_TEXTURE = Identifier.of("client", "textures/visuals/jump_new.png");
    private static final Identifier EVOLA_TEXTURE = Identifier.of("client", "textures/visuals/jump_circle.png");

    private Easing getExpandEasing() {
        return switch (animMode.getValue()) {
            case "Smooth" -> Easings.SINE_OUT;
            case "Elastic" -> Easings.ELASTIC_OUT;
            case "Spring" -> Easings.BACK_OUT;
            case "Expo" -> Easings.EXPO_OUT;
            default -> Easings.BACK_OUT;
        };
    }

    private Easing getShrinkEasing() {
        return switch (animMode.getValue()) {
            case "Smooth" -> Easings.SINE_IN;
            case "Elastic" -> Easings.ELASTIC_IN;
            case "Spring" -> Easings.BACK_IN;
            case "Expo" -> Easings.EXPO_IN;
            default -> Easings.BACK_IN;
        };
    }

    private Identifier getTexture() {
        return circleMode.is("Evola") ? EVOLA_TEXTURE : GRADIENT_TEXTURE;
    }

    @EventHandler
    public void onJump(EventJump e) {
        circles.add(new Circle(mc.player.getEntityPos().add(0, 0.05, 0)));
    }

    // ───────────────────────────── предпоказ ─────────────────────────────

    @Override
    public PreviewSettings previewSettings() {
        return previewSettings;
    }

    @Override
    public void previewSpawn(PreviewContext ctx) {
        circles.add(new Circle(ctx.anchor().add(0, 0.05, 0)));
    }

    @Override
    public void previewStop() {
        circles.clear();
    }


    @EventHandler
    public void onRender(EventRender3D e) {
        if (circles.isEmpty()) {
            return;
        }


        long maxLife = circleMode.is("Ring") ? ringTime.getValue().longValue() : 2500L;
        circles.removeIf(c -> System.currentTimeMillis() - c.time > maxLife);

        if (circles.isEmpty()) {
            return;
        }
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        MatrixStack pose = e.getMatrixStack();

        if (circleMode.is("Ring")) {
            renderRings(e, immediate);
            immediate.draw();
            return;
        }

        int alpha = 255;

        for (Circle c : circles) {
            if (System.currentTimeMillis() - c.time > 800 && !c.isBack) {
                c.animation.run(0, 0.8, getShrinkEasing());
                c.animation2.run(0, 0.8, getShrinkEasing());
                c.isBack = true;
            }

            c.animation.update();
            c.animation2.update();
            float rad = (float) c.animation2.getValue();

            double posX = c.vector3d.x - mc.gameRenderer.getCamera().getCameraPos().x;
            double posY = c.vector3d.y - mc.gameRenderer.getCamera().getCameraPos().y;
            double posZ = c.vector3d.z - mc.gameRenderer.getCamera().getCameraPos().z;

            float size = size_c.getValue() * rad;

            pose.push();
            pose.translate(posX, posY, posZ);
            pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90 ));

            MatrixStack.Entry entry = pose.peek();
            Matrix4f matrix4f = entry.getPositionMatrix();
            Matrix3f normalMatrix = entry.getNormalMatrix();
            VertexConsumer buffer = immediate.getBuffer(ROMB_ESP.apply(getTexture()));

            drawTexturedQuad(buffer, matrix4f, normalMatrix, -size / 2f, -size / 2f, size, size, new int[]{
                    ColorUtil.fade(0),
                    ColorUtil.fade(90),
                    ColorUtil.fade(180)
                    ,ColorUtil.fade(360)
            }, (int) (alpha * c.animation.get()));

            pose.pop();

            if (glow.getValue() && circleMode.is("Gradient")) {
                pose.push();
                pose.translate(posX, posY, posZ);

                drawGlowLayers(immediate, pose, size , (float) c.animation.get());
                pose.pop();
            }
        }

        immediate.draw();
    }

    // ───────────────────────────── кольцо (decidevisuals) ─────────────────────────────

    private static final float RING_HOVER = 0.05F;

    private void renderRings(EventRender3D e, VertexConsumerProvider.Immediate immediate) {
        if (circles.isEmpty()) return;
        MatrixStack pose = e.getMatrixStack();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        VertexConsumer buf = immediate.getBuffer(RING_QUADS);

        long now = System.currentTimeMillis();
        float maxLife = Math.max(1.0F, ringTime.getValue());
        float maxRadius = Math.max(0.05F, size_c.getValue());
        float thickness = ringThickness.getValue();
        int segments = 64;

        for (int i = circles.size() - 1; i >= 0; i--) {
            Circle c = circles.get(i);
            float delta = (now - c.time) / maxLife;
            if (delta > 1.0F) {
                circles.remove(i);
                continue;
            }
            float eased = ringEase(delta);
            float currentRadius = Math.max(0.05F, eased * maxRadius);
            float innerRadius = Math.max(0.0F, currentRadius - thickness * (1.0F - delta * 0.5F));
            float alphaFactor = (1.0F - delta) * (delta < 0.1F ? delta / 0.1F : 1.0F);

            float cx = (float) (c.vector3d.x - cam.x);
            float cy = (float) (c.vector3d.y - cam.y) + RING_HOVER;
            float cz = (float) (c.vector3d.z - cam.z);

            Matrix4f m = pose.peek().getPositionMatrix();

            for (int s = 0; s < segments; s++) {
                float a1 = (float) s / segments;
                float a2 = (float) (s + 1) / segments;
                float rad1 = a1 * (float) (Math.PI * 2);
                float rad2 = a2 * (float) (Math.PI * 2);
                float cos1 = MathHelper.cos(rad1);
                float sin1 = MathHelper.sin(rad1);
                float cos2 = MathHelper.cos(rad2);
                float sin2 = MathHelper.sin(rad2);

                int color1 = ringColor((int) (a1 * 360.0F), alphaFactor);
                int color2 = ringColor((int) (a2 * 360.0F), alphaFactor);
                int innerColor1 = ColorUtil.multAlpha(color1, 0.15F);
                int innerColor2 = ColorUtil.multAlpha(color2, 0.15F);

                float x1 = cx + cos1 * innerRadius;
                float z1 = cz + sin1 * innerRadius;
                float x2 = cx + cos1 * currentRadius;
                float z2 = cz + sin1 * currentRadius;
                float x3 = cx + cos2 * currentRadius;
                float z3 = cz + sin2 * currentRadius;
                float x4 = cx + cos2 * innerRadius;
                float z4 = cz + sin2 * innerRadius;

                buf.vertex(m, x1, cy, z1).color(innerColor1);
                buf.vertex(m, x2, cy, z2).color(color1);
                buf.vertex(m, x3, cy, z3).color(color2);
                buf.vertex(m, x4, cy, z4).color(innerColor2);
                buf.vertex(m, x4, cy, z4).color(innerColor2);
                buf.vertex(m, x3, cy, z3).color(color2);
                buf.vertex(m, x2, cy, z2).color(color1);
                buf.vertex(m, x1, cy, z1).color(innerColor1);
            }
        }
    }

    private float ringEase(float delta) {
        float t = MathHelper.clamp(delta, 0.0F, 1.0F);
        return switch (ringEasing.getValue()) {
            case "Эластичная" -> elasticOut(t);
            case "Назад" -> backOut(t);
            default -> t;
        };
    }

    private static float elasticOut(float t) {
        return t != 0.0F && t != 1.0F ? (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * (Math.PI * 2.0 / 3.0)) + 1.0) : t;
    }

    private static float backOut(float t) {
        return (float) (1.0 + 2.70158 * Math.pow(t - 1.0, 3.0) + 1.70158 * Math.pow(t - 1.0, 2.0));
    }

    private int ringColor(int angle, float alpha) {
        if (ringColorMode.is("Радуга")) {
            return ColorUtil.rainbow(8, angle, 1.0F, 1.0F, alpha);
        }
        if (ringColorMode.is("Клиент")) {
            return ColorUtil.multAlpha(ColorUtil.client(), alpha);
        }
        return ColorUtil.replAlpha(ringColor.getValue(), (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255));
    }

    public static final Function<Identifier, RenderLayer> ROMB_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(1536)
                        .build();
                return RenderLayer.of("wtex", setup);
            });

    private void drawGlowLayers(VertexConsumerProvider.Immediate immediate, MatrixStack pose, float radius, float alpha) {
        VertexConsumer buffer = immediate.getBuffer(ROMB_ESP.apply(getTexture()));
        int layers = 20;
        float maxHeight = radius * 0.15f;
        float expand   = radius * 0.25f;

        Vector3f normal = new Vector3f(0, 1, 0);
        pose.peek().getNormalMatrix().transform(normal);
        normal.normalize();

        for (int i = 0; i < layers; i++) {
            float progress   = i / (float) layers;
            float yOff       = maxHeight * progress;
            float layerAlpha = alpha * (1f - progress) * 0.15f;
            if (layerAlpha <= 0.004f) continue;
            float r    = radius + expand * progress;
            float half = r / 2f;
            int   a    = (int) (layerAlpha * 255);

            Matrix4f matrix = pose.peek().getPositionMatrix();

            int c0   = ColorUtil.fade(0);
            int c90  = ColorUtil.fade(90);
            int c180 = ColorUtil.fade(180);
            int c270 = ColorUtil.fade(270);

            buffer.vertex(matrix, -half, yOff, -half).color((c0   >> 16) & 0xFF, (c0   >> 8) & 0xFF, c0   & 0xFF, a).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(normal.x, normal.y, normal.z);
            buffer.vertex(matrix, -half, yOff,  half).color((c90  >> 16) & 0xFF, (c90  >> 8) & 0xFF, c90  & 0xFF, a).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(normal.x, normal.y, normal.z);
            buffer.vertex(matrix,  half, yOff,  half).color((c180 >> 16) & 0xFF, (c180 >> 8) & 0xFF, c180 & 0xFF, a).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(normal.x, normal.y, normal.z);
            buffer.vertex(matrix,  half, yOff, -half).color((c270 >> 16) & 0xFF, (c270 >> 8) & 0xFF, c270 & 0xFF, a).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(normal.x, normal.y, normal.z);
        }
    }

    private void drawTexturedQuad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix, float x, float y,
                                  float width, float height,  int[] ints, int alpha) {


        Vector3f normal = new Vector3f(0, 0, 1);
        normalMatrix.transform(normal);
        normal.normalize();

        float x1 = x;
        float y1 = y;
        float x2 = x + width;
        float y2 = y + height;

        buffer.vertex(matrix, x1, y1, 0.0f).color(ColorUtil.replAlpha(ints[0],alpha)).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(normal.x, normal.y, normal.z);
        buffer.vertex(matrix, x2, y1, 0.0f).color(ColorUtil.replAlpha(ints[1],alpha)).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(normal.x, normal.y, normal.z);
        buffer.vertex(matrix, x2, y2, 0.0f).color(ColorUtil.replAlpha(ints[2],alpha)).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(normal.x, normal.y, normal.z);
        buffer.vertex(matrix, x1, y2, 0.0f).color(ColorUtil.replAlpha(ints[3],alpha)).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(normal.x, normal.y, normal.z);
    }

    private class Circle {

        private final Vec3d vector3d;

        private final long time;
        private final Animation animation = new Animation();
        private final Animation animation2 = new Animation();
        private boolean isBack;

        public Circle(Vec3d vector3d) {
            this.vector3d = vector3d;
            time = System.currentTimeMillis();
            animation2.run(1.0, 0.8, getExpandEasing());
            animation.run(1.0, 0.4, Easings.SINE_OUT);
        }

    }
}

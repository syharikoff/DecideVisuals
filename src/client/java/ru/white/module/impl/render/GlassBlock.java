package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.render.GlassBlockRenderer;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.List;

@Getter
@ModuleInfo(name = "Glass Block", category = Category.VISUALS, desc = "Делает наведенный блок стеклянным")
public class GlassBlock extends Module {

    private static GlassBlock instance;

    public BooleanSetting enableBlur = new BooleanSetting(this, "Блюр", true);
    public SliderSetting blurRadius = new SliderSetting(this, "Сила размытия", 2.5f, 1.0f, 5.0f, 0.1f)
            .setVisible(() -> enableBlur.getValue());
    public SliderSetting blurIterations = new SliderSetting(this, "Качество", 3, 1, 5, 1)
            .setVisible(() -> enableBlur.getValue());
    public SliderSetting saturation = new SliderSetting(this, "Насыщенность", 0, 0.0f, 2.0f, 0.1f)
            .setVisible(() -> enableBlur.getValue());
    public BooleanSetting enableTint = new BooleanSetting(this, "Оттенок", false);
    public SliderSetting tintIntensity = new SliderSetting(this, "Сила оттенка", 0.2f, 0.0f, 0.5f, 0.01f)
            .setVisible(() -> enableBlur.getValue() && enableTint.getValue());
    public ColorSetting tintColor = new ColorSetting(this, "Цвет оттенка", 0xFF00FFFF)
            .setVisible(() -> enableTint.getValue());
    public BooleanSetting enableEdgeGlow = new BooleanSetting(this, "Свечение краёв", true);
    public SliderSetting edgeGlowIntensity = new SliderSetting(this, "Сила свечения", 0.2f, 0.0f, 1.0f, 0.01f)
            .setVisible(() -> enableEdgeGlow.getValue());
    public BooleanSetting shimmer = new BooleanSetting(this, "Шиммер", true)
            .setVisible(() -> enableEdgeGlow.getValue());
    public SliderSetting shimmerWidth = new SliderSetting(this, "Ширина шиммера", 0.04f, 0.01f, 0.15f, 0.01f)
            .setVisible(() -> enableEdgeGlow.getValue() && shimmer.getValue());
    public SliderSetting shimmerPeriod = new SliderSetting(this, "Период шиммера", 5f, 1f, 15f, 0.5f)
            .setVisible(() -> enableEdgeGlow.getValue() && shimmer.getValue());
    public SliderSetting moveSpeed = new SliderSetting(this, "Скорость перемещения", 0.18f, 0.05f, 0.6f, 0.01f);

    public BooleanSetting enableShaderFill = new BooleanSetting(this, "Шейдер заливка", false);
    public ModeSetting shaderMode = new ModeSetting(this, "Режим шейдера", "Full", "WebShader", "Plasma", "ChamsFill", "BaseWarp")
            .setVisible(() -> enableShaderFill.getValue());
    public SliderSetting shaderFillAlpha = new SliderSetting(this, "Прозрачность шейдера", 0.5f, 0.1f, 1.0f, 0.05f)
            .setVisible(() -> enableShaderFill.getValue());
    public ColorSetting shaderFillColor = new ColorSetting(this, "Цвет шейдера", 0xFFFFFFFF)
            .setVisible(() -> enableShaderFill.getValue());

    private final BufferAllocator allocator = new BufferAllocator(1 << 14);
    private final Animation moveAnimation = new Animation();
    private final Animation alphaAnimation = new Animation();

    private BlockPos targetPos;
    private BlockPos lastTargetPos;
    private Vec3d fromOrigin = Vec3d.ZERO;
    private Vec3d toOrigin = Vec3d.ZERO;
    private List<Box> targetBoxes = Collections.emptyList();

    public GlassBlock() {
        instance = this;
    }

    public static GlassBlock getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        targetPos = null;
        lastTargetPos = null;
        fromOrigin = Vec3d.ZERO;
        toOrigin = Vec3d.ZERO;
        moveAnimation.set(1.0);
        alphaAnimation.set(0.0);

        GlassBlockRenderer renderer = GlassBlockRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
        updateRendererSettings();
    }

    @Override
    protected void onDisable() {
        GlassBlockRenderer.getInstance().setEnabled(false);
        targetPos = null;
        lastTargetPos = null;
        targetBoxes = Collections.emptyList();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!isEnabled()) return;
        targetPos = null;
        lastTargetPos = null;
        targetBoxes = Collections.emptyList();

        GlassBlockRenderer renderer = GlassBlockRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
        updateRendererSettings();
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        if (!isEnabled() || mc.world == null || mc.player == null) return;

        updateTarget();
        moveAnimation.update();
        alphaAnimation.update();

        if (alphaAnimation.get() <= 0.01f || targetBoxes.isEmpty()) return;

        if (enableShaderFill.getValue()) {
            renderShaderOverlay(event, getAnimatedOrigin(), alphaAnimation.get());
        } else {
            GlassBlockRenderer renderer = GlassBlockRenderer.getInstance();
            updateRendererSettings();

            if (!renderer.captureSceneBeforeBlock()) return;
            renderMask(event, getAnimatedOrigin(), alphaAnimation.get());
            renderer.captureSceneAfterBlock();
            renderer.renderGlassEffect();
        }
    }

    private void updateTarget() {
        BlockPos newTarget = getLookedBlock();

        if (newTarget != null) {
            BlockState state = mc.world.getBlockState(newTarget);
            VoxelShape shape = state.getOutlineShape(mc.world, newTarget);
            if (shape.isEmpty()) {
                shape = state.getCollisionShape(mc.world, newTarget);
            }

            List<Box> boxes = shape.isEmpty() ? Collections.singletonList(new Box(0, 0, 0, 1, 1, 1)) : shape.getBoundingBoxes();
            targetBoxes = boxes;
        }

        if (newTarget == null) {
            targetPos = null;
            alphaAnimation.run(0.0, moveSpeed.getValue(), Easings.QUAD_OUT, true);
            return;
        }

        if (!newTarget.equals(targetPos)) {
            fromOrigin = lastTargetPos == null ? Vec3d.of(newTarget) : getAnimatedOrigin();
            toOrigin = Vec3d.of(newTarget);
            targetPos = newTarget;
            lastTargetPos = newTarget;
            moveAnimation.set(0.0);
            moveAnimation.run(1.0, moveSpeed.getValue(), Easings.QUAD_OUT);
        }

        alphaAnimation.run(1.0, moveSpeed.getValue() * 0.75f, Easings.QUAD_OUT, true);
    }

    private BlockPos getLookedBlock() {
        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof BlockHitResult blockHit) || hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos pos = blockHit.getBlockPos();
        if (mc.world == null || mc.world.getBlockState(pos).isAir()) {
            return null;
        }

        return pos;
    }

    private Vec3d getAnimatedOrigin() {
        float t = moveAnimation.get();
        return new Vec3d(
                fromOrigin.x + (toOrigin.x - fromOrigin.x) * t,
                fromOrigin.y + (toOrigin.y - fromOrigin.y) * t,
                fromOrigin.z + (toOrigin.z - fromOrigin.z) * t
        );
    }

    private void renderMask(EventRender3D event, Vec3d origin, float alpha) {
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        VertexConsumer buffer = immediate.getBuffer(MASK_LAYER);
        Matrix4f matrix = event.getMatrixStack().peek().getPositionMatrix();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

        int color = (((int) (255 * alpha)) << 24) | 0x00FFFFFF;
        for (Box box : targetBoxes) {
            double x0 = origin.x + box.minX - cam.x;
            double y0 = origin.y + box.minY - cam.y;
            double z0 = origin.z + box.minZ - cam.z;
            double x1 = origin.x + box.maxX - cam.x;
            double y1 = origin.y + box.maxY - cam.y;
            double z1 = origin.z + box.maxZ - cam.z;
            drawBox(buffer, matrix, x0, y0, z0, x1, y1, z1, color);
        }

        immediate.draw();
    }

    private void updateRendererSettings() {
        GlassBlockRenderer renderer = GlassBlockRenderer.getInstance();
        renderer.setBlurEnabled(enableBlur.getValue());
        renderer.setBlurRadius(blurRadius.getValue());
        renderer.setBlurIterations(blurIterations.getValue().intValue());
        renderer.setSaturation(saturation.getValue());
        renderer.setReflect(true);

        if (enableBlur.getValue() && enableTint.getValue()) {
            renderer.setTintColor(tintColor.getValue());
            renderer.setTintIntensity(tintIntensity.getValue());
        } else {
            renderer.setTintColor(0x00000000);
            renderer.setTintIntensity(0.0f);
        }

        renderer.setOutlineEnabled(enableEdgeGlow.getValue());
        if (enableEdgeGlow.getValue()) {
            float strength = edgeGlowIntensity.getValue() * 20.0f;
            renderer.setOutlineGlowStrength(strength);
            int outlineRgb = enableTint.getValue() ? tintColor.getValue() : 0xFFFFFFFF;
            renderer.setOutlineColor(outlineRgb);
            renderer.setShimmerEnabled(shimmer.getValue());
            renderer.setShimmerWidth(shimmerWidth.getValue());
            renderer.setShimmerPeriodSec(shimmerPeriod.getValue());
        }
    }

    private static void drawBox(VertexConsumer buffer, Matrix4f matrix,
                                double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ,
                                int color) {
        float x0 = (float) minX, x1 = (float) maxX;
        float y0 = (float) minY, y1 = (float) maxY;
        float z0 = (float) minZ, z1 = (float) maxZ;

        buffer.vertex(matrix, x0, y0, z0).color(color);
        buffer.vertex(matrix, x1, y0, z0).color(color);
        buffer.vertex(matrix, x1, y0, z1).color(color);
        buffer.vertex(matrix, x0, y0, z1).color(color);

        buffer.vertex(matrix, x0, y1, z0).color(color);
        buffer.vertex(matrix, x0, y1, z1).color(color);
        buffer.vertex(matrix, x1, y1, z1).color(color);
        buffer.vertex(matrix, x1, y1, z0).color(color);

        buffer.vertex(matrix, x0, y0, z1).color(color);
        buffer.vertex(matrix, x1, y0, z1).color(color);
        buffer.vertex(matrix, x1, y1, z1).color(color);
        buffer.vertex(matrix, x0, y1, z1).color(color);

        buffer.vertex(matrix, x1, y0, z0).color(color);
        buffer.vertex(matrix, x0, y0, z0).color(color);
        buffer.vertex(matrix, x0, y1, z0).color(color);
        buffer.vertex(matrix, x1, y1, z0).color(color);

        buffer.vertex(matrix, x0, y0, z0).color(color);
        buffer.vertex(matrix, x0, y0, z1).color(color);
        buffer.vertex(matrix, x0, y1, z1).color(color);
        buffer.vertex(matrix, x0, y1, z0).color(color);

        buffer.vertex(matrix, x1, y0, z1).color(color);
        buffer.vertex(matrix, x1, y0, z0).color(color);
        buffer.vertex(matrix, x1, y1, z0).color(color);
        buffer.vertex(matrix, x1, y1, z1).color(color);
    }

    private static final RenderPipeline MASK_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "glass_block_mask"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );

    private static final RenderLayer MASK_LAYER = RenderLayer.of(
            "glass_block_mask",
            RenderSetup.builder(MASK_PIPELINE).expectedBufferSize(1 << 12).build()
    );

    private static final Identifier WHITE_TEXTURE = Identifier.of("minecraft", "textures/block/white_concrete.png");

    private static final RenderPipeline SHADER_OVERLAY_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "block_overlay_shader"))
                    .withVertexShader(Identifier.of("client", "block_overlay_shader_vertex"))
                    .withFragmentShader(Identifier.of("client", "block_overlay_shader_fragment"))
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );
    private static final RenderLayer SHADER_OVERLAY_LAYER = RenderLayer.of("client_block_overlay_shader",
            RenderSetup.builder(SHADER_OVERLAY_PIPELINE)
                    .texture("Sampler0", WHITE_TEXTURE)
                    .translucent()
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .expectedBufferSize(1536)
                    .build());

    private static final RenderPipeline SHADER_CHAMS_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "block_overlay_chams_fill"))
                    .withVertexShader(Identifier.of("client", "block_overlay_shader_vertex"))
                    .withFragmentShader(Identifier.of("client", "block_overlay_chams_fill_fragment"))
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );
    private static final RenderLayer SHADER_CHAMS_FILL_LAYER = RenderLayer.of("client_block_overlay_chams_fill",
            RenderSetup.builder(SHADER_CHAMS_FILL_PIPELINE)
                    .texture("Sampler0", WHITE_TEXTURE)
                    .translucent()
                    .expectedBufferSize(1536)
                    .build());

    private void renderShaderOverlay(EventRender3D event, Vec3d origin, float alpha) {
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        boolean chamsFill = shaderMode.is("ChamsFill");
        RenderLayer layer = chamsFill ? SHADER_CHAMS_FILL_LAYER : SHADER_OVERLAY_LAYER;
        VertexConsumer buffer = immediate.getBuffer(layer);
        Matrix4f matrix = event.getMatrixStack().peek().getPositionMatrix();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

        int color = shaderFillColor.getValue();
        float modeOffset = chamsFill ? 0.0f : shaderModeIndex() * 2.0f;
        float inset = 0.001f;

        for (Box box : targetBoxes) {
            float x0 = (float) (origin.x + box.minX - cam.x);
            float y0 = (float) (origin.y + box.minY - cam.y);
            float z0 = (float) (origin.z + box.minZ - cam.z);
            float x1 = (float) (origin.x + box.maxX - cam.x);
            float y1 = (float) (origin.y + box.maxY - cam.y);
            float z1 = (float) (origin.z + box.maxZ - cam.z);

            int a = (int) (MathHelper.clamp(alpha, 0f, 1f) * 255f);
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int vertexColor = (a << 24) | (r << 16) | (g << 8) | b;

            shaderQuad(buffer, matrix, vertexColor, x0, y1 + inset, z1, x1, y1 + inset, z1, x1, y1 + inset, z0, x0, y1 + inset, z0, modeOffset);
            shaderQuad(buffer, matrix, vertexColor, x0, y0 - inset, z0, x1, y0 - inset, z0, x1, y0 - inset, z1, x0, y0 - inset, z1, modeOffset);
            shaderQuad(buffer, matrix, vertexColor, x0, y0, z0 - inset, x0, y1, z0 - inset, x1, y1, z0 - inset, x1, y0, z0 - inset, modeOffset);
            shaderQuad(buffer, matrix, vertexColor, x0, y0, z1 + inset, x1, y0, z1 + inset, x1, y1, z1 + inset, x0, y1, z1 + inset, modeOffset);
            shaderQuad(buffer, matrix, vertexColor, x0 - inset, y0, z0, x0 - inset, y0, z1, x0 - inset, y1, z1, x0 - inset, y1, z0, modeOffset);
            shaderQuad(buffer, matrix, vertexColor, x1 + inset, y0, z0, x1 + inset, y1, z0, x1 + inset, y1, z1, x1 + inset, y0, z1, modeOffset);
        }

        immediate.draw();
    }

    private static void shaderQuad(VertexConsumer buffer, Matrix4f matrix, int color,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    float x3, float y3, float z3, float x4, float y4, float z4,
                                    float modeOffset) {
        shaderVertex(buffer, matrix, color, x1, y1, z1, 0f, 1f + modeOffset);
        shaderVertex(buffer, matrix, color, x2, y2, z2, 1f, 1f + modeOffset);
        shaderVertex(buffer, matrix, color, x3, y3, z3, 1f, 0f + modeOffset);
        shaderVertex(buffer, matrix, color, x4, y4, z4, 0f, 0f + modeOffset);
    }

    private static void shaderVertex(VertexConsumer buffer, Matrix4f matrix, int color,
                                      float x, float y, float z, float u, float v) {
        buffer.vertex(matrix, x, y, z)
                .texture(u, v)
                .color(color)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0, 0, 1);
    }

    private int shaderModeIndex() {
        if (shaderMode.is("WebShader")) return 2;
        if (shaderMode.is("Plasma")) return 4;
        if (shaderMode.is("BaseWarp")) return 8;
        return 0;
    }
}

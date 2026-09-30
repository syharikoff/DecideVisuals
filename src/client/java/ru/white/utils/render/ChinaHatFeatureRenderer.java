package ru.white.utils.render;


import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.Client;
import ru.white.module.impl.render.ChinaHat;
import ru.white.utils.colors.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

public class ChinaHatFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final float PI2 = (float) (Math.PI * 2);
    private static final int CIRCLE_SEGMENTS = 128;
    private static final int OUTLINE_SEGMENTS = 64;

    public ChinaHatFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrixStack, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        MinecraftClient mc = MinecraftClient.getInstance();

        ChinaHat chinaHat = ChinaHat.getInstance();
        if (chinaHat == null || !chinaHat.isEnabled()) return;

        if (mc.player == null) return;

        boolean local  = isLocalPlayer(state, mc);
        boolean friend = isFriendPlayer(state);

        if (!local && !friend) return;
        if (local && mc.options.getPerspective().isFirstPerson()) return;

        matrixStack.push();

        this.getContextModel().head.applyTransform(matrixStack);

        matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90f));
        matrixStack.translate(0, 0.5f, 0);
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        renderFlatHat(matrixStack, immediate, chinaHat);
        renderOutline(matrixStack, immediate, chinaHat);
        immediate.draw();

        matrixStack.pop();
    }

    private boolean isFriendPlayer(PlayerEntityRenderState state) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return false;
            net.minecraft.entity.Entity entity = mc.world.getEntityById(state.id);
            if (entity instanceof net.minecraft.entity.player.PlayerEntity p) {
                return Client.get().friendManager().isFriend(p.getName().getString());
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isLocalPlayer(PlayerEntityRenderState state, MinecraftClient mc) {
        try {
            if (state.id == mc.player.getId()) {
                return true;
            }
        } catch (Exception ignored) {}

        try {
            if (state.playerName != null && mc.player.getName() != null) {
                return state.playerName.getString().equals(mc.player.getName().getString());
            }
        } catch (Exception ignored) {}

        return false;
    }

    private void renderFlatHat(MatrixStack stack, VertexConsumerProvider provider, ChinaHat chinaHat) {
        VertexConsumer consumer = provider.getBuffer(CHINA_HAT);

        Matrix4f matrix = stack.peek().getPositionMatrix();

        float width = 0.55f;
        float coneHeight = 0.31f;
        int alpha = 200;
        float animSpeed = 5;

        int centerColor = getGradientColor(0, CIRCLE_SEGMENTS, chinaHat, animSpeed);
        centerColor = ColorUtil.replAlpha(centerColor, alpha);

        consumer.vertex(matrix, 0, coneHeight, 0).color(centerColor);

        for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
            int color = getGradientColor(i, CIRCLE_SEGMENTS, chinaHat, animSpeed);
            color = ColorUtil.replAlpha(color, alpha);

            float angle = i * PI2 / CIRCLE_SEGMENTS;
            float x = -MathHelper.sin(angle) * width;
            float z = MathHelper.cos(angle) * width;

            consumer.vertex(matrix, x, 0, z).color(color);
        }

        for (int i = CIRCLE_SEGMENTS; i >= 0; i--) {
            int color = getGradientColor(i, CIRCLE_SEGMENTS, chinaHat, animSpeed);
            color = ColorUtil.replAlpha(color, alpha);

            float angle = i * PI2 / CIRCLE_SEGMENTS;
            float x = -MathHelper.sin(angle) * width;
            float z = MathHelper.cos(angle) * width;

            consumer.vertex(matrix, x, 0, z).color(color);
        }

        consumer.vertex(matrix, 0, coneHeight, 0).color(centerColor);
    }

    private void renderOutline(MatrixStack stack, VertexConsumerProvider provider, ChinaHat chinaHat) {
        VertexConsumer consumer = provider.getBuffer(CHINA_HAT_OUTLINE);

        Matrix4f matrix = stack.peek().getPositionMatrix();

        float width = 0.55f;
        float animSpeed = 5;
        int outlineAlpha = 255;

        for (int i = 0; i <= OUTLINE_SEGMENTS; i++) {
            int color = getGradientColor(i * 2, OUTLINE_SEGMENTS * 2, chinaHat, animSpeed);
            color = ColorUtil.replAlpha(color, outlineAlpha);

            float angle = i * PI2 / OUTLINE_SEGMENTS;
            float x = -MathHelper.sin(angle) * width;
            float z = MathHelper.cos(angle) * width;

            consumer.vertex(matrix, x, 0, z).color(color);
        }
    }

    public static final RenderPipeline CHINA_HAT_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/china_hat")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_FAN)
                    .build()
    );

    public static final RenderLayer CHINA_HAT = RenderLayer.of(
            "china_hat",
            RenderSetup.builder(CHINA_HAT_PIPELINE)
                    .translucent()
                    .expectedBufferSize(8192)
                    .build()
    );

    public static final RenderPipeline CHINA_HAT_OUTLINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/china_hat_outline")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINE_STRIP)
                    .build()
    );

    public static final RenderLayer CHINA_HAT_OUTLINE = RenderLayer.of(
            "china_hat_outline",
            RenderSetup.builder(CHINA_HAT_OUTLINE_PIPELINE)
                    .translucent()
                    .expectedBufferSize(4096)
                    .build()
    );

    private int getGradientColor(int index, int size, ChinaHat chinaHat, float animSpeed) {
        long time = System.currentTimeMillis();
        float timeOffset = (time / (1000f / animSpeed)) % size;
        return ColorUtil.fade((int)(index * (360f / size) + timeOffset * (360f / size)));
    }
}
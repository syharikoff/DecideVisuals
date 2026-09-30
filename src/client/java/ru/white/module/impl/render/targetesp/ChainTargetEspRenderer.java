package ru.white.module.impl.render.targetesp;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import ru.white.utils.colors.ColorUtil;

import static ru.white.module.impl.render.targetesp.RhombTargetEspRenderer.withAlpha;
import static ru.white.module.impl.render.targetesp.TargetEspPipelines.textured;

/** Цепь — две вращающиеся ленты из звеньев вокруг таргета. */
public final class ChainTargetEspRenderer {
    private static final Identifier CHAIN_TEXTURE =
            Identifier.of("client", "textures/features/targetesp/chain.png");

    private ChainTargetEspRenderer() {
    }

    public static void render(MatrixStack stack, VertexConsumerProvider.Immediate provider, TargetEspRenderContext context, float speed) {
        VertexConsumer consumer = provider.getBuffer(TargetEspPipelines.ROMB_ESP.apply(CHAIN_TEXTURE));

        float sizeScale = 1.0f;
        float speedScale = Math.max(0.05f, speed);
        float animationTime = (context.target().age + context.partialTicks()) * speedScale;
        float impact = TargetEspMath.easeOutCubic(context.chainImpactProgress());
        float baseRadius = context.target().getWidth() * 1.35f * sizeScale;
        float bandHeight = Math.max(0.90f, context.target().getHeight() * 0.54f * sizeScale);
        float compressedRadius = baseRadius * (1.0f - impact * 0.18f);
        float primaryBandHeight = bandHeight * (1.0f - impact * 0.08f);
        float secondaryBandHeight = bandHeight * 0.80f * (1.0f - impact * 0.12f);
        float roll = MathHelper.sin(animationTime * 0.22f) * 18.0f;
        float pitch = MathHelper.cos(animationTime * 0.18f) * 18.0f;
        float spin = animationTime * 5.0f;

        int startColor = tintForDamage(context.primaryColor(), context.hurtProgress());
        int endColor = tintForDamage(context.secondaryColor(), context.hurtProgress());

        stack.push();
        stack.translate(0.0f, context.target().getHeight() * 0.52f, 0.0f);

        renderChainBand(stack, consumer, compressedRadius * 0.90f, primaryBandHeight, roll, pitch, spin, context.alpha(), startColor, endColor, 0.0f, speedScale);
        renderChainBand(stack, consumer, compressedRadius * 0.98f, secondaryBandHeight, -roll, -pitch, -spin * 0.92f, context.alpha() * 0.92f, endColor, startColor, 0.38f, speedScale);

        stack.pop();
    }

    private static void renderChainBand(MatrixStack stack, VertexConsumer consumer, float radius, float bandHeight, float roll, float pitch, float spin, float alpha, int startColor, int endColor, float uvOffset, float speedScale) {
        stack.push();
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

        Matrix4f entry = stack.peek().getPositionMatrix();
        float bottomY = -bandHeight * 0.5f;
        float topY = bandHeight * 0.5f;
        int segments = 64;
        float uvRepeats = 4.0f;

        for (int i = 0; i < segments; i++) {
            float progress0 = (float) i / segments;
            float progress1 = (float) (i + 1) / segments;
            float angle0 = (progress0 * 360.0f) + spin;
            float angle1 = (progress1 * 360.0f) + spin;
            float rad0 = angle0 * MathHelper.RADIANS_PER_DEGREE;
            float rad1 = angle1 * MathHelper.RADIANS_PER_DEGREE;

            float x0 = MathHelper.sin(rad0) * radius;
            float z0 = MathHelper.cos(rad0) * radius;
            float x1 = MathHelper.sin(rad1) * radius;
            float z1 = MathHelper.cos(rad1) * radius;

            float wobble0 = MathHelper.sin((progress0 * MathHelper.TAU) + spin * 0.05f) * (0.06f * speedScale);
            float wobble1 = MathHelper.sin((progress1 * MathHelper.TAU) + spin * 0.05f) * (0.06f * speedScale);

            int color0 = scaleAlpha(lerpColor(startColor, endColor, progress0), alpha);
            int color1 = scaleAlpha(lerpColor(startColor, endColor, progress1), alpha);

            float u0 = (progress0 * uvRepeats) + uvOffset;
            float u1 = (progress1 * uvRepeats) + uvOffset;

            textured(consumer, entry, x0, bottomY + wobble0, z0, u0, 0.0f, color0);
            textured(consumer, entry, x1, bottomY + wobble1, z1, u1, 0.0f, color1);
            textured(consumer, entry, x1, topY + wobble1, z1, u1, 1.0f, color1);
            textured(consumer, entry, x0, topY + wobble0, z0, u0, 1.0f, color0);
        }

        stack.pop();
    }

    private static int tintForDamage(int color, float hurt) {
        return ColorUtil.interpolateColor(color, 0xFFFF3A3A, MathHelper.clamp(hurt, 0.0f, 1.0f));
    }

    private static int lerpColor(int a, int b, float f) {
        return ColorUtil.interpolateColor(a, b, MathHelper.clamp(f, 0.0f, 1.0f));
    }

    private static int scaleAlpha(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        return withAlpha(color, Math.round(a * factor));
    }
}

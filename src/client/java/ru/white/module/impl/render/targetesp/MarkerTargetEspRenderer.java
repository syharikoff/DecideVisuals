package ru.white.module.impl.render.targetesp;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import static ru.white.module.impl.render.targetesp.RhombTargetEspRenderer.withAlpha;
import static ru.white.module.impl.render.targetesp.TargetEspPipelines.cameraRotation;
import static ru.white.module.impl.render.targetesp.TargetEspPipelines.textured;

/** Маркер (Ромб 2) — вращающаяся пульсирующая плашка поверх таргета. */
public final class MarkerTargetEspRenderer {
    private static final Identifier MARKER_TEXTURE =
            Identifier.of("client", "textures/features/targetesp/marker.png");

    private MarkerTargetEspRenderer() {
    }

    public static void render(MatrixStack stack, VertexConsumerProvider.Immediate provider, TargetEspRenderContext context, float speed) {
        VertexConsumer consumer = provider.getBuffer(TargetEspPipelines.ROMB_ESP.apply(MARKER_TEXTURE));
        Quaternionf camRot = cameraRotation();

        stack.push();
        stack.translate(0.0f, context.target().getHeight() / 2.0f, 0.0f);
        stack.multiply(camRot);

        float rotation = ((long) (context.frameTimeMs() * Math.max(0.05f, speed)) % 3600L) / 3600.0f * 360.0f;
        float pulse = 1.0f + MathHelper.sin((context.frameTimeMs() % 1400L) / 1400.0f * MathHelper.TAU) * 0.08f;
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        stack.scale(0.65f * pulse, 0.65f * pulse, 1.0f);

        int c1 = withAlpha(context.primaryColor(), (int) (255.0f * context.alpha()));
        int c2 = withAlpha(context.secondaryColor(), (int) (255.0f * context.alpha()));
        int c3 = withAlpha(context.primaryColor(), (int) (215.0f * context.alpha()));
        int c4 = withAlpha(context.secondaryColor(), (int) (215.0f * context.alpha()));

        Matrix4f entry = stack.peek().getPositionMatrix();
        textured(consumer, entry, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, c1);
        textured(consumer, entry, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, c2);
        textured(consumer, entry, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, c3);
        textured(consumer, entry, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f, c4);
        stack.pop();
    }
}

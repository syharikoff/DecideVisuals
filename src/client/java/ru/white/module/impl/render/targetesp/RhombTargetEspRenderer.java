package ru.white.module.impl.render.targetesp;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import static ru.white.module.impl.render.targetesp.TargetEspPipelines.cameraRotation;
import static ru.white.module.impl.render.targetesp.TargetEspPipelines.textured;

/** Ромб и Ромб 2 (маркер) портированы из Polairis. */
public final class RhombTargetEspRenderer {
    private static final Identifier RHOMB_TEXTURE = Identifier.of("client", "textures/world/cube.png");

    private RhombTargetEspRenderer() {
    }

    public static void render(MatrixStack stack, VertexConsumerProvider.Immediate provider, TargetEspRenderContext context, float speed) {
        VertexConsumer consumer = provider.getBuffer(TargetEspPipelines.ROMB_ESP.apply(RHOMB_TEXTURE));
        Quaternionf camRot = cameraRotation();

        stack.push();
        stack.translate(0.0f, context.target().getHeight() / 2.0f, 0.0f);
        stack.multiply(camRot);

        long ms = context.frameTimeMs();
        float timeRotation = ((ms % 360000L) / 1000.0f) * 90.0f * Math.max(0.05f, speed);
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(timeRotation));
        stack.scale(0.35f, 0.35f, 1.0f);

        int c1 = withAlpha(context.primaryColor(), (int) (255.0f * context.alpha()));
        int c2 = withAlpha(context.secondaryColor(), (int) (255.0f * context.alpha()));

        Matrix4f entry = stack.peek().getPositionMatrix();
        textured(consumer, entry, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, c2);
        textured(consumer, entry, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, c1);
        textured(consumer, entry, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, c2);
        textured(consumer, entry, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f, c1);
        stack.pop();
    }

    static int withAlpha(int color, int alpha) {
        return ((MathHelper.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF));
    }
}

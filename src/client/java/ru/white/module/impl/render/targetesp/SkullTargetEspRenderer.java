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

/** Череп — билборд с текстурой черепа над таргетом. */
public final class SkullTargetEspRenderer {
    private static final Identifier SKULL_TEXTURE =
            Identifier.of("client", "textures/features/targetesp/skull.png");

    private SkullTargetEspRenderer() {
    }

    public static void render(MatrixStack stack, VertexConsumerProvider.Immediate provider, TargetEspRenderContext context) {
        VertexConsumer consumer = provider.getBuffer(TargetEspPipelines.ROMB_ESP.apply(SKULL_TEXTURE));
        Quaternionf camRot = cameraRotation();

        float impact = TargetEspMath.easeOutCubic(context.chainImpactProgress());
        float scale = 0.48f * (1.0f - 0.14f * impact);
        float verticalOffset = context.target().getHeight() * 0.50f;
        float alpha = Math.min(1.0f, context.alpha() * 1.85f);

        int c1 = withAlpha(context.primaryColor(), (int) (255.0f * alpha));
        int c2 = withAlpha(context.secondaryColor(), (int) (255.0f * alpha));
        int c3 = withAlpha(context.primaryColor(), (int) (250.0f * alpha));
        int c4 = withAlpha(context.secondaryColor(), (int) (250.0f * alpha));

        stack.push();
        stack.translate(0.0f, verticalOffset, 0.0f);
        stack.multiply(camRot);
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
        stack.scale(scale, scale, 1.0f);

        Matrix4f entry = stack.peek().getPositionMatrix();
        textured(consumer, entry, -0.8f, -0.8f, 0.0f, 0.0f, 0.0f, c1);
        textured(consumer, entry, -0.8f, 0.8f, 0.0f, 0.0f, 1.0f, c2);
        textured(consumer, entry, 0.8f, 0.8f, 0.0f, 1.0f, 1.0f, c3);
        textured(consumer, entry, 0.8f, -0.8f, 0.0f, 1.0f, 0.0f, c4);
        stack.pop();
    }
}

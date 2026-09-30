package ru.white.module.impl.render.targetesp;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.white.module.impl.render.TargetEsp;
import ru.white.utils.colors.ColorUtil;

public class CrossTargetEspRenderer {

    private static final int CROSS_COUNT = 3;
    private static final float CROSS_SIZE = 0.2f;
    private static final double CROSS_RADIUS = 1.4;
    private static final float ORBIT_SPEED = 90.0f;
    private static final float SELF_SPIN_SPEED = 135.0f;

    public static void render(MatrixStack matrices, VertexConsumerProvider.Immediate immediate,
                               TargetEspRenderContext ctx, float speedMultiplier) {
        if (ctx.target() == null) return;

        double timeSec = (ctx.frameTimeMs() % 100_000L) / 1000.0;
        float orbitAngle = (float) ((timeSec * ORBIT_SPEED * speedMultiplier) % 360.0);
        float selfSpin = (float) ((timeSec * SELF_SPIN_SPEED * speedMultiplier) % 360.0);
        double angleStep = Math.PI * 2.0 / CROSS_COUNT;

        double entityRadius = Math.max(0.2, ctx.target().getWidth() / 2.0);
        double entityHeight = Math.max(0.2, ctx.target().getHeight());
        double currentRadius = entityRadius * CROSS_RADIUS * 1.5;
        double yOffset = entityHeight * 0.5;

        float thickness = CROSS_SIZE / 8.0f;
        float alpha = ctx.alpha();
        int primaryColor = ctx.primaryColor();

        VertexConsumer fillBuf = immediate.getBuffer(TargetEsp.RING_FILL_LAYER);

        for (int i = 0; i < CROSS_COUNT; i++) {
            double angle = Math.toRadians(orbitAngle) + i * angleStep;
            double x = Math.cos(angle) * currentRadius;
            double z = Math.sin(angle) * currentRadius;

            matrices.push();
            matrices.translate(x, yOffset, z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(selfSpin));

            Matrix4f matrix = matrices.peek().getPositionMatrix();

            drawFillBox(fillBuf, matrix,
                    -thickness, -CROSS_SIZE, -thickness,
                    thickness, CROSS_SIZE * 1.5f, thickness,
                    primaryColor, alpha);

            drawFillBox(fillBuf, matrix,
                    -CROSS_SIZE * 0.8f, CROSS_SIZE * 0.35f, -thickness,
                    CROSS_SIZE * 0.8f, CROSS_SIZE * 0.35f + thickness * 2, thickness,
                    primaryColor, alpha);

            matrices.pop();
        }
    }

    private static void drawFillBox(VertexConsumer buf, Matrix4f m,
                                     float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ,
                                     int color, float alpha) {
        int c = ColorUtil.replAlpha(color, (int) (MathHelper.clamp(alpha, 0f, 1f) * 255f));

        buf.vertex(m, minX, maxY, minZ).color(c);
        buf.vertex(m, maxX, maxY, minZ).color(c);
        buf.vertex(m, maxX, maxY, maxZ).color(c);
        buf.vertex(m, minX, maxY, maxZ).color(c);

        buf.vertex(m, minX, minY, minZ).color(c);
        buf.vertex(m, minX, minY, maxZ).color(c);
        buf.vertex(m, maxX, minY, maxZ).color(c);
        buf.vertex(m, maxX, minY, minZ).color(c);

        buf.vertex(m, minX, minY, maxZ).color(c);
        buf.vertex(m, maxX, minY, maxZ).color(c);
        buf.vertex(m, maxX, maxY, maxZ).color(c);
        buf.vertex(m, minX, maxY, maxZ).color(c);

        buf.vertex(m, maxX, minY, minZ).color(c);
        buf.vertex(m, minX, minY, minZ).color(c);
        buf.vertex(m, minX, maxY, minZ).color(c);
        buf.vertex(m, maxX, maxY, minZ).color(c);

        buf.vertex(m, minX, minY, minZ).color(c);
        buf.vertex(m, minX, minY, maxZ).color(c);
        buf.vertex(m, minX, maxY, maxZ).color(c);
        buf.vertex(m, minX, maxY, minZ).color(c);

        buf.vertex(m, maxX, minY, maxZ).color(c);
        buf.vertex(m, maxX, minY, minZ).color(c);
        buf.vertex(m, maxX, maxY, minZ).color(c);
        buf.vertex(m, maxX, maxY, maxZ).color(c);
    }
}

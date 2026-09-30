package ru.white.module.impl.render.targetesp;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.mojang.blaze3d.vertex.VertexFormat.DrawMode.TRIANGLES;

/**
 * TargetESP crystals, портировано из Polairis (Cataclysm). Светящиеся кристаллы,
 * вращающиеся вокруг таргета.
 */
public final class CrystalTargetEspRenderer {
    private static final Identifier BLOOM = Identifier.of("client", "textures/bloom.png");

    private static final Vector3f[] VERTICES = {
            new Vector3f(0.0F, 1.5F, 0.0F),
            new Vector3f(0.0F, -1.5F, 0.0F),
            new Vector3f(1.0F, 0.0F, 0.0F),
            new Vector3f(-1.0F, 0.0F, 0.0F),
            new Vector3f(0.0F, 0.0F, 1.0F),
            new Vector3f(0.0F, 0.0F, -1.0F)
    };

    private static final int[][] FACES = {
            {0, 2, 4}, {0, 4, 3}, {0, 3, 5}, {0, 5, 2},
            {1, 4, 2}, {1, 3, 4}, {1, 5, 3}, {1, 2, 5}
    };

    private static final float[] FACE_BRIGHTNESS = {1.0F, 0.8F, 0.6F, 0.9F, 0.7F, 0.5F, 0.4F, 0.6F};

    private static final float CRYSTAL_SIZE = 0.1F;
    private static final float BLOOM_SIZE = 1.0F;

    public static final RenderPipeline CRYSTAL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "targetesp_crystal"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer CRYSTAL_LAYER = RenderLayer.of("targetesp_crystal",
            RenderSetup.builder(CRYSTAL_PIPELINE).expectedBufferSize(1 << 16).build());

    private CrystalTargetEspRenderer() {
    }

    public static void render(MatrixStack stack, VertexConsumerProvider.Immediate provider, TargetEspRenderContext context, float speed) {
        LivingEntity target = context.target();
        float anim = MathHelper.clamp(context.alpha(), 0.0f, 1.0f);
        if (anim <= 0.01f || target == null) {
            return;
        }

        float moving = (context.frameTimeMs() % 100_000L) * 0.2f * Math.max(0.05f, speed);
        float width = target.getWidth() * 1.5F;
        float height = target.getHeight();

        int rgb = context.primaryColor() | 0xFF000000;
        int baseColor = withAlpha(rgb, Math.round(255.0f * anim));
        int bloomColor = withAlpha(rgb, Math.round(255.0f * anim * 0.2f));

        VertexConsumer crystal = provider.getBuffer(CRYSTAL_LAYER);

        for (int i = 0; i < 360; i += 20) {
            float val = 1.2F - 0.5F * anim;
            float angle = (float) Math.toRadians(i + moving * 0.3F);
            float sin = (float) (Math.sin(angle) * width * val);
            float cos = (float) (Math.cos(angle) * width * val);

            float yOff = 0.1F + height * Math.abs((float) Math.sin(i));

            stack.push();
            stack.translate(sin, yOff, cos);

            Vector3f dir = new Vector3f(-sin, height * 0.5F - 1.0F, -cos);
            if (dir.lengthSquared() < 1.0e-8f) {
                dir.set(0.0F, 1.0F, 0.0F);
            } else {
                dir.normalize();
            }
            stack.multiply(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), dir));

            renderCrystalMesh(stack, crystal, CRYSTAL_SIZE, baseColor);
            stack.pop();
        }

        VertexConsumer bloom = provider.getBuffer(TargetEspPipelines.ROMB_ESP.apply(BLOOM));
        Quaternionf camRot = TargetEspPipelines.cameraRotation();
        float half = BLOOM_SIZE * 0.5F;

        for (int i = 0; i < 360; i += 20) {
            float val = 1.2F - 0.5F * anim;
            float angle = (float) Math.toRadians(i + moving * 0.3F);
            float sin = (float) (Math.sin(angle) * width * val);
            float cos = (float) (Math.cos(angle) * width * val);
            float yOff = 0.1F + height * Math.abs((float) Math.sin(i));

            stack.push();
            stack.translate(sin, yOff, cos);
            stack.multiply(camRot);

            Matrix4f matrix = stack.peek().getPositionMatrix();
            TargetEspPipelines.textured(bloom, matrix, -half, -half, 0f, 0f, 0f, bloomColor);
            TargetEspPipelines.textured(bloom, matrix, half, -half, 0f, 1f, 0f, bloomColor);
            TargetEspPipelines.textured(bloom, matrix, half, half, 0f, 1f, 1f, bloomColor);
            TargetEspPipelines.textured(bloom, matrix, -half, half, 0f, 0f, 1f, bloomColor);
            stack.pop();
        }
    }

    private static void renderCrystalMesh(MatrixStack matrices, VertexConsumer buffer, float size, int color) {
        matrices.push();
        matrices.scale(size, size, size);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (int i = 0; i < FACES.length; i++) {
            int[] face = FACES[i];
            int shaded = applyBrightness(color, FACE_BRIGHTNESS[i]);
            Vector3f v1 = VERTICES[face[0]];
            Vector3f v2 = VERTICES[face[1]];
            Vector3f v3 = VERTICES[face[2]];
            buffer.vertex(matrix, v1.x, v1.y, v1.z).color(shaded);
            buffer.vertex(matrix, v2.x, v2.y, v2.z).color(shaded);
            buffer.vertex(matrix, v3.x, v3.y, v3.z).color(shaded);
        }
        matrices.pop();
    }

    private static int applyBrightness(int color, float brightness) {
        int alpha = color >> 24 & 0xFF;
        int red = Math.min(255, Math.max(0, (int) ((color >> 16 & 0xFF) * brightness)));
        int green = Math.min(255, Math.max(0, (int) ((color >> 8 & 0xFF) * brightness)));
        int blue = Math.min(255, Math.max(0, (int) ((color & 0xFF) * brightness)));
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int withAlpha(int color, int alpha) {
        return ((MathHelper.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF));
    }
}

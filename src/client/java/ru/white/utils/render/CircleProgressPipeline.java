package ru.white.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.nio.ByteBuffer;

/**
 * Круговой прогресс: элементы копятся чанками и уходят в общий пасс
 * DrawBatcher. См. {@link UniformArrayPipeline}.
 */
public class CircleProgressPipeline extends UniformArrayPipeline {

    private static final Identifier PIPELINE_ID = Identifier.of("client", "pipeline/circle_progress");
    private static final Identifier VERTEX_SHADER = Identifier.of("client", "core/circle_progress");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("client", "core/circle_progress");

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("CircleData", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final int MAX_CIRCLES = 64;
    private static final int CIRCLE_SIZE = 4 * 16;
    private static final int UNIFORM_RING = 32;

    public CircleProgressPipeline() {
        super("circle_progress", PIPELINE, "CircleData", MAX_CIRCLES, CIRCLE_SIZE, UNIFORM_RING);
    }

    @Override
    public int batchLayer() {
        return 0; // вместе с заливками
    }

    public void draw(float centerX, float centerY, float radius, float thickness, float progress, int color) {
        if (radius <= 0F || thickness <= 0F) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) return;

        ByteBuffer dataBuffer = beginItem();

        float padding = thickness * 0.75F + 1F;
        float size = (radius + padding) * 2F;
        float drawX = centerX - size / 2F;
        float drawY = centerY - size / 2F;

        dataBuffer.putFloat(drawX);
        dataBuffer.putFloat(drawY);
        dataBuffer.putFloat(size);
        dataBuffer.putFloat(size);

        dataBuffer.putFloat(radius + padding);
        dataBuffer.putFloat(radius);
        dataBuffer.putFloat(thickness);
        dataBuffer.putFloat(Math.max(0F, Math.min(1F, progress)));

        dataBuffer.putFloat(((color >> 16) & 0xFF) / 255F);
        dataBuffer.putFloat(((color >> 8) & 0xFF) / 255F);
        dataBuffer.putFloat((color & 0xFF) / 255F);
        dataBuffer.putFloat(((color >> 24) & 0xFF) / 255F);

        dataBuffer.putFloat(0F);
        dataBuffer.putFloat(0F);
        dataBuffer.putFloat(0F);
        dataBuffer.putFloat(0F);

        endItem();
    }
}

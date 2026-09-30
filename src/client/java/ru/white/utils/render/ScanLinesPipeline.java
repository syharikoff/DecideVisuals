package ru.white.utils.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Скан-линии фона меню: вся сетка строк рисуется одним полноэкранным quad'ом,
 * шаг строк и бегущая волна яркости считаются во фрагментном шейдере.
 */
public class ScanLinesPipeline {

    private static final Identifier PIPELINE_ID = Identifier.of("client", "pipeline/scanlines");
    private static final Identifier VERTEX_SHADER = Identifier.of("client", "core/scanlines");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("client", "core/scanlines");

    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final float FIXED_GUI_SCALE = 2.0f;
    private static final int BUFFER_SIZE = 64;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("ScanLinesData", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized;

    private void ensureInitialized() {
        if (initialized) return;

        dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "client:scanlines_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        initialized = true;
    }

    /**
     * @param step      расстояние между строками в пикселях гуи
     * @param lineWidth толщина строки
     * @param edgeFade  ширина затухания у левого/правого края
     * @param period    период пробега волны, мс
     * @param tower     высота ореола волны в пикселях
     */
    public void draw(float width, float height, float alpha, int color,
                     float step, float lineWidth, float edgeFade, float period, float tower) {
        if (alpha <= 0.01f || width <= 0f || height <= 0f || step <= 0f) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) return;

        ensureInitialized();

        // рисуем немедленно — сначала выпускаем накопленные батчи (порядок отрисовки)
        DrawBatcher.flushPending();

        float time = (System.currentTimeMillis() % (long) Math.max(1f, period)) / Math.max(1f, period);

        dataBuffer.clear();

        dataBuffer.putFloat(client.getWindow().getFramebufferWidth() / FIXED_GUI_SCALE);
        dataBuffer.putFloat(client.getWindow().getFramebufferHeight() / FIXED_GUI_SCALE);
        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);

        dataBuffer.putFloat(step);
        dataBuffer.putFloat(lineWidth);
        dataBuffer.putFloat(edgeFade);
        dataBuffer.putFloat(tower);

        dataBuffer.putFloat(((color >> 16) & 0xFF) / 255.0f);
        dataBuffer.putFloat(((color >> 8) & 0xFF) / 255.0f);
        dataBuffer.putFloat((color & 0xFF) / 255.0f);
        dataBuffer.putFloat(Math.max(0f, Math.min(1f, alpha)));

        dataBuffer.putFloat(time);
        dataBuffer.putFloat(0f);
        dataBuffer.putFloat(0f);
        dataBuffer.putFloat(0f);

        dataBuffer.flip();

        uploadAndDraw(client);
    }

    private void uploadAndDraw(MinecraftClient client) {
        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "client:scanlines_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "client:scanlines_pass",
                client.getFramebuffer().getColorAttachmentView(),
                OptionalInt.empty(),
                client.getFramebuffer().getDepthAttachmentView(),
                OptionalDouble.empty())) {

            renderPass.setPipeline(PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("ScanLinesData", uniformBuffer);

            renderPass.draw(0, 6);
        }
    }

    public void close() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
        initialized = false;
    }
}

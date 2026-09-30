package ru.white.utils.render.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Фуллскрин-пасс сканирующей волны: реконструирует мировые координаты из
 * depth-буфера и аддитивно подмешивает кольцо к цветовому аттачменту.
 */
public final class ScanWorldCompositePipeline {

    private static final Identifier PIPELINE_ID = Identifier.of("client", "pipeline/scanworld_composite");
    private static final Identifier SHADER_ID = Identifier.of("client", "core/scanworld_composite");

    private static final BlendFunction ADD_BLEND = new BlendFunction(
            SourceFactor.ONE, DestFactor.ONE,
            SourceFactor.ONE, DestFactor.ONE
    );

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(SHADER_ID)
                    .withFragmentShader(SHADER_ID)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("ScanData", UniformType.UNIFORM_BUFFER)
                    .withSampler("DepthSampler")
                    .withBlend(ADD_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0f, 0f, 0f);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static final int BUFFER_SIZE = 256;
    // Кольцо uniform-буферов: несколько сканов за кадр нельзя рисовать через один
    // буфер — GPU исполняет draw позже записи, и волны получили бы чужие данные
    // (та же причина, что и у BlurPipeline.UNIFORM_RING)
    private static final int UNIFORM_RING = 16;

    private GpuBuffer[] uniformBuffers;
    private int uniformRingIndex;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized;

    private void ensureInitialized() {
        if (initialized) return;

        dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummy = MemoryUtil.memAlloc(4);
        dummy.putInt(0).flip();

        dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "client:scanworld_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummy
        );

        MemoryUtil.memFree(dummy);
        uniformBuffers = new GpuBuffer[UNIFORM_RING];
        initialized = true;
    }

    private GpuBuffer nextUniformBuffer() {
        GpuBuffer buf = uniformBuffers[uniformRingIndex];
        if (buf == null) {
            final int idx = uniformRingIndex;
            buf = RenderSystem.getDevice().createBuffer(
                    () -> "client:scanworld_uniform_" + idx,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    BUFFER_SIZE
            );
            uniformBuffers[uniformRingIndex] = buf;
        }
        uniformRingIndex = (uniformRingIndex + 1) % UNIFORM_RING;
        return buf;
    }

    public void render(
            GpuTextureView targetView,
            GpuTextureView depthView,
            int width,
            int height,
            Vec3d cameraPos,
            Vec3d center,
            Matrix4f invView,
            Matrix4f invProj,
            float radius,
            float bandWidth,
            float fade,
            Color outerColor,
            Color midColor,
            Color innerColor,
            Color scanlineColor
    ) {
        ensureInitialized();

        prepareUniformData(width, height, cameraPos, center, invView, invProj, radius, bandWidth,
                fade,
                outerColor, midColor, innerColor, scanlineColor);

        GpuBuffer uniformBuffer = nextUniformBuffer();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(0, dataBuffer.remaining()), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().write(
                RenderSystem.getModelViewMatrix(),
                COLOR_MODULATOR,
                MODEL_OFFSET,
                TEXTURE_MATRIX
        );

        GpuSampler depthSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "client:scanworld_pass",
                targetView,
                OptionalInt.empty()
        )) {
            pass.setPipeline(PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);

            pass.bindTexture("DepthSampler", depthView, depthSampler);

            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform("ScanData", uniformBuffer);

            pass.draw(0, 6);
        }
    }

    private void prepareUniformData(
            int width,
            int height,
            Vec3d cameraPos,
            Vec3d center,
            Matrix4f invView,
            Matrix4f invProj,
            float radius,
            float bandWidth,
            float fade,
            Color outerColor,
            Color midColor,
            Color innerColor,
            Color scanlineColor
    ) {
        dataBuffer.clear();

        putMatrix(dataBuffer, invView);
        putMatrix(dataBuffer, invProj);

        dataBuffer.putFloat((float) cameraPos.x);
        dataBuffer.putFloat((float) cameraPos.y);
        dataBuffer.putFloat((float) cameraPos.z);
        dataBuffer.putFloat(0.0f);

        dataBuffer.putFloat((float) center.x);
        dataBuffer.putFloat((float) center.y);
        dataBuffer.putFloat((float) center.z);
        dataBuffer.putFloat(radius);

        putColor(dataBuffer, outerColor);
        putColor(dataBuffer, midColor);
        putColor(dataBuffer, innerColor);
        putColor(dataBuffer, scanlineColor);

        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);
        dataBuffer.putFloat(bandWidth);
        dataBuffer.putFloat((System.currentTimeMillis() % 1_000_000L) / 1000.0f);

        dataBuffer.putFloat(fade);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);

        dataBuffer.flip();
    }

    private static void putColor(ByteBuffer buffer, Color color) {
        buffer.putFloat(color.getRed() / 255.0f);
        buffer.putFloat(color.getGreen() / 255.0f);
        buffer.putFloat(color.getBlue() / 255.0f);
        buffer.putFloat(color.getAlpha() / 255.0f);
    }

    private static void putMatrix(ByteBuffer buffer, Matrix4f matrix) {
        buffer.putFloat(matrix.m00()).putFloat(matrix.m01()).putFloat(matrix.m02()).putFloat(matrix.m03());
        buffer.putFloat(matrix.m10()).putFloat(matrix.m11()).putFloat(matrix.m12()).putFloat(matrix.m13());
        buffer.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(matrix.m22()).putFloat(matrix.m23());
        buffer.putFloat(matrix.m30()).putFloat(matrix.m31()).putFloat(matrix.m32()).putFloat(matrix.m33());
    }

    public void close() {
        if (uniformBuffers != null) {
            for (int i = 0; i < uniformBuffers.length; i++) {
                if (uniformBuffers[i] != null) {
                    uniformBuffers[i].close();
                    uniformBuffers[i] = null;
                }
            }
            uniformBuffers = null;
        }
        uniformRingIndex = 0;
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

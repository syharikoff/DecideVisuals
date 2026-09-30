package ru.white.utils.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

public class KawaseBlurPipeline {

    private static final Identifier DOWN_PIPELINE_ID = Identifier.of("client", "pipeline/kawase_down");
    private static final Identifier DOWN_VERTEX_SHADER = Identifier.of("client", "core/kawase_down");
    private static final Identifier DOWN_FRAGMENT_SHADER = Identifier.of("client", "core/kawase_down");

    private static final Identifier UP_PIPELINE_ID = Identifier.of("client", "pipeline/kawase_up");
    private static final Identifier UP_VERTEX_SHADER = Identifier.of("client", "core/kawase_up");
    private static final Identifier UP_FRAGMENT_SHADER = Identifier.of("client", "core/kawase_up");

    private static final RenderPipeline DOWN_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(DOWN_PIPELINE_ID)
                    .withVertexShader(DOWN_VERTEX_SHADER)
                    .withFragmentShader(DOWN_FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("KawaseParams", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline UP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(UP_PIPELINE_ID)
                    .withVertexShader(UP_VERTEX_SHADER)
                    .withFragmentShader(UP_FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("KawaseParams", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static final int MAX_ITERATIONS = 8;
    // down + up passes + 1 final
    private static final int MAX_PASSES = 2 * MAX_ITERATIONS + 1;
    // vec4 (4 floats × 4 bytes) with std140
    private static final int UNIFORM_DATA_SIZE = 16;

    private GpuBuffer[] passUniformBuffers;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;

    private GpuTexture[] downTextures;
    private GpuTextureView[] downTextureViews;
    private GpuTexture[] upTextures;
    private GpuTextureView[] upTextureViews;

    private int[] downWidths;
    private int[] downHeights;
    private int[] upWidths;
    private int[] upHeights;

    private GpuTexture finalTexture;
    private GpuTextureView finalTextureView;

    private int lastWidth = 0;
    private int lastHeight = 0;
    private boolean initialized = false;

    public KawaseBlurPipeline() {
    }

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(UNIFORM_DATA_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:kawase_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        // one buffer per pass so each pass has its own uniform data
        this.passUniformBuffers = new GpuBuffer[MAX_PASSES];
        for (int i = 0; i < MAX_PASSES; i++) {
            final int idx = i;
            passUniformBuffers[i] = RenderSystem.getDevice().createBuffer(
                    () -> "minecraft:kawase_uniform_" + idx,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_DATA_SIZE
            );
        }

        this.downTextures = new GpuTexture[MAX_ITERATIONS];
        this.downTextureViews = new GpuTextureView[MAX_ITERATIONS];
        this.upTextures = new GpuTexture[MAX_ITERATIONS];
        this.upTextureViews = new GpuTextureView[MAX_ITERATIONS];
        this.downWidths = new int[MAX_ITERATIONS];
        this.downHeights = new int[MAX_ITERATIONS];
        this.upWidths = new int[MAX_ITERATIONS];
        this.upHeights = new int[MAX_ITERATIONS];

        initialized = true;
    }

    private void ensureFramebuffers(int width, int height) {
        if (width == lastWidth && height == lastHeight) return;

        cleanupFramebuffers();

        finalTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:kawase_final",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8,
                width, height, 1, 1
        );
        finalTextureView = RenderSystem.getDevice().createTextureView(finalTexture);

        int w = width;
        int h = height;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);

            final int index = i;
            final int fw = w;
            final int fh = h;

            downWidths[i] = fw;
            downHeights[i] = fh;
            upWidths[i] = fw;
            upHeights[i] = fh;

            downTextures[i] = RenderSystem.getDevice().createTexture(
                    () -> "minecraft:kawase_down_" + index,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    TextureFormat.RGBA8,
                    fw, fh, 1, 1
            );
            downTextureViews[i] = RenderSystem.getDevice().createTextureView(downTextures[i]);

            upTextures[i] = RenderSystem.getDevice().createTexture(
                    () -> "minecraft:kawase_up_" + index,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    TextureFormat.RGBA8,
                    fw, fh, 1, 1
            );
            upTextureViews[i] = RenderSystem.getDevice().createTextureView(upTextures[i]);
        }

        lastWidth = width;
        lastHeight = height;
    }

    private void cleanupFramebuffers() {
        if (finalTextureView != null) { finalTextureView.close(); finalTextureView = null; }
        if (finalTexture != null)     { finalTexture.close();     finalTexture = null;     }

        if (downTextureViews != null) {
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                if (downTextureViews[i] != null) { downTextureViews[i].close(); downTextureViews[i] = null; }
                if (downTextures[i]     != null) { downTextures[i].close();     downTextures[i]     = null; }
                if (upTextureViews[i]   != null) { upTextureViews[i].close();   upTextureViews[i]   = null; }
                if (upTextures[i]       != null) { upTextures[i].close();       upTextures[i]       = null; }
            }
        }
    }

    public GpuTextureView blur(GpuTexture sourceTexture, GpuTextureView sourceView,
                               int width, int height, int iterations, float offset) {
        if (sourceTexture == null || sourceView == null) return null;
        if (sourceTexture.isClosed() || sourceView.isClosed()) return null;

        ensureInitialized();
        ensureFramebuffers(width, height);

        iterations = Math.min(Math.max(iterations, 1), MAX_ITERATIONS);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        // Матрицы одинаковы для всех проходов блюра — пишем динамические трансформы ОДИН раз
        // вместо 2*iterations+1 раз. Результат идентичен, меньше нагрузка на драйвер.
        GpuBufferSlice dynTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        GpuTextureView currentSource = sourceView;
        int currentWidth  = width;
        int currentHeight = height;
        int passIdx = 0;

        // ── down passes ──────────────────────────────────────────────────────
        for (int i = 0; i < iterations; i++) {
            GpuBuffer passBuf = passUniformBuffers[passIdx++];
            writeUniform(encoder, passBuf, currentWidth, currentHeight, offset);

            final int fi = i;
            try (RenderPass rp = encoder.createRenderPass(
                    () -> "minecraft:kawase_down_" + fi, downTextureViews[fi], OptionalInt.empty())) {
                rp.setPipeline(DOWN_PIPELINE);
                rp.setVertexBuffer(0, dummyVertexBuffer);
                rp.bindTexture("Sampler0", currentSource, sampler);
                RenderSystem.bindDefaultUniforms(rp);
                rp.setUniform("DynamicTransforms", dynTransforms);
                rp.setUniform("KawaseParams", passBuf);
                rp.draw(0, 6);
            }

            currentSource = downTextureViews[i];
            currentWidth  = downWidths[i];
            currentHeight = downHeights[i];
        }

        // ── up passes ────────────────────────────────────────────────────────
        for (int i = iterations - 1; i >= 0; i--) {
            GpuBuffer passBuf = passUniformBuffers[passIdx++];
            writeUniform(encoder, passBuf, currentWidth, currentHeight, offset);

            final int fi = i;
            try (RenderPass rp = encoder.createRenderPass(
                    () -> "minecraft:kawase_up_" + fi, upTextureViews[fi], OptionalInt.empty())) {
                rp.setPipeline(UP_PIPELINE);
                rp.setVertexBuffer(0, dummyVertexBuffer);
                rp.bindTexture("Sampler0", currentSource, sampler);
                RenderSystem.bindDefaultUniforms(rp);
                rp.setUniform("DynamicTransforms", dynTransforms);
                rp.setUniform("KawaseParams", passBuf);
                rp.draw(0, 6);
            }

            currentSource = upTextureViews[i];
            currentWidth  = upWidths[i];
            currentHeight = upHeights[i];
        }

        // ── final composite ──────────────────────────────────────────────────
        GpuBuffer passBuf = passUniformBuffers[passIdx];
        writeUniform(encoder, passBuf, currentWidth, currentHeight, offset);

        try (RenderPass rp = encoder.createRenderPass(
                () -> "minecraft:kawase_final", finalTextureView, OptionalInt.empty())) {
            rp.setPipeline(UP_PIPELINE);
            rp.setVertexBuffer(0, dummyVertexBuffer);
            rp.bindTexture("Sampler0", currentSource, sampler);
            RenderSystem.bindDefaultUniforms(rp);
            rp.setUniform("DynamicTransforms", dynTransforms);
            rp.setUniform("KawaseParams", passBuf);
            rp.draw(0, 6);
        }

        return finalTextureView;
    }

    private void writeUniform(CommandEncoder encoder, GpuBuffer buf, int width, int height, float offset) {
        dataBuffer.clear();
        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);
        dataBuffer.putFloat(offset);
        dataBuffer.putFloat(0f);
        dataBuffer.flip();
        encoder.writeToBuffer(buf.slice(), dataBuffer);
    }

    public void close() {
        cleanupFramebuffers();

        if (passUniformBuffers != null) {
            for (GpuBuffer buf : passUniformBuffers) {
                if (buf != null) buf.close();
            }
            passUniformBuffers = null;
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

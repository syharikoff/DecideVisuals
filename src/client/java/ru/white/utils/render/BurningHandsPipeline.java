package ru.white.utils.render;

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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * "Burning hands": трёхпроходный огонь вокруг рук/предмета.
 * 1) glow  — мягкое пламя-ореол вокруг силуэта (по маске);
 * 2) trail — накопительный буфер (пинг-понг), дым уносится вверх и затухает;
 * 3) composite — аддитивное наложение glow+trail на фреймбуфер.
 */
public class BurningHandsPipeline {

    private static final BlendFunction REPLACE_BLEND = new BlendFunction(
            SourceFactor.ONE, DestFactor.ZERO,
            SourceFactor.ONE, DestFactor.ZERO
    );

    private static final BlendFunction ADDITIVE_BLEND = new BlendFunction(
            SourceFactor.ONE, DestFactor.ONE,
            SourceFactor.ZERO, DestFactor.ONE
    );

    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/burn_glow"))
                    .withVertexShader(Identifier.of("client", "core/burn_glow"))
                    .withFragmentShader(Identifier.of("client", "core/burn_glow"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("BurnData", UniformType.UNIFORM_BUFFER)
                    .withSampler("HandSampler")
                    .withSampler("MaskSampler")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline TRAIL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/burn_trail"))
                    .withVertexShader(Identifier.of("client", "core/burn_trail"))
                    .withFragmentShader(Identifier.of("client", "core/burn_trail"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("BurnData", UniformType.UNIFORM_BUFFER)
                    .withSampler("TrailSampler")
                    .withSampler("GlowSampler")
                    .withSampler("HandSampler")
                    .withSampler("MaskSampler")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline COMPOSITE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/burn_composite"))
                    .withVertexShader(Identifier.of("client", "core/burn_composite"))
                    .withFragmentShader(Identifier.of("client", "core/burn_composite"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("BurnData", UniformType.UNIFORM_BUFFER)
                    .withSampler("MaskSampler")
                    .withSampler("GlowSampler")
                    .withSampler("TrailSampler")
                    .withBlend(ADDITIVE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int BUFFER_SIZE = 64;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:burn_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        initialized = true;
    }

    /** Обновляет UBO один раз на кадр — все три прохода читают одни данные. */
    public void updateUniforms(int width, int height, boolean fillMode,
                               float radiusPx, float strength, float flameSpeed, float colorMix,
                               float decay, float flameHeightPx, float flowSpeed, float trailStrength,
                               int fireColorRgb, float glowStrength) {
        ensureInitialized();

        dataBuffer.clear();
        float time = (System.currentTimeMillis() % 100000L) / 1000.0f;

        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);
        dataBuffer.putFloat(time);
        dataBuffer.putFloat(fillMode ? 1.0f : 0.0f);

        dataBuffer.putFloat(radiusPx);
        dataBuffer.putFloat(strength);
        dataBuffer.putFloat(flameSpeed);
        dataBuffer.putFloat(colorMix);

        dataBuffer.putFloat(decay);
        dataBuffer.putFloat(flameHeightPx);
        dataBuffer.putFloat(flowSpeed);
        dataBuffer.putFloat(trailStrength);

        dataBuffer.putFloat(((fireColorRgb >> 16) & 0xFF) / 255.0f);
        dataBuffer.putFloat(((fireColorRgb >> 8) & 0xFF) / 255.0f);
        dataBuffer.putFloat((fireColorRgb & 0xFF) / 255.0f);
        dataBuffer.putFloat(glowStrength);

        dataBuffer.flip();

        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) uniformBuffer.close();
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minecraft:burn_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);
    }

    public void renderGlow(GpuTextureView targetView, GpuTextureView handView, GpuTextureView maskView) {
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "minecraft:burn_glow_pass", targetView, OptionalInt.of(0x00000000))) {
            pass.setPipeline(GLOW_PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("HandSampler", handView, sampler);
            pass.bindTexture("MaskSampler", maskView, sampler);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform("BurnData", uniformBuffer);
            pass.draw(0, 6);
        }
    }

    public void renderTrail(GpuTextureView targetView, GpuTextureView prevTrailView,
                            GpuTextureView glowView, GpuTextureView handView, GpuTextureView maskView) {
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "minecraft:burn_trail_pass", targetView, OptionalInt.of(0x00000000))) {
            pass.setPipeline(TRAIL_PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("TrailSampler", prevTrailView, sampler);
            pass.bindTexture("GlowSampler", glowView, sampler);
            pass.bindTexture("HandSampler", handView, sampler);
            pass.bindTexture("MaskSampler", maskView, sampler);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform("BurnData", uniformBuffer);
            pass.draw(0, 6);
        }
    }

    public void composite(GpuTextureView targetView, GpuTextureView maskView,
                          GpuTextureView glowView, GpuTextureView trailView) {
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "minecraft:burn_composite_pass", targetView, OptionalInt.empty())) {
            pass.setPipeline(COMPOSITE_PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("MaskSampler", maskView, sampler);
            pass.bindTexture("GlowSampler", glowView, sampler);
            pass.bindTexture("TrailSampler", trailView, sampler);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform("BurnData", uniformBuffer);
            pass.draw(0, 6);
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

package ru.decide.utils.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class WetWorldPipeline {

    private static final int UNIFORM_SIZE = 304;

    private static RenderPipeline pipeline;
    private static GpuBuffer dummyVertexBuffer;
    private static GpuBuffer uniformBuffer;
    private static ByteBuffer uniformData;

    private static GpuTexture colorCopy;
    private static GpuTextureView colorCopyView;
    private static GpuTexture depthCopy;
    private static GpuTextureView depthCopyView;

    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private WetWorldPipeline() {}

    private static void init() {
        if (pipeline != null) return;

        pipeline = RenderPipeline.builder()
                .withLocation(Identifier.of("decide", "pipeline/wet_world"))
                .withVertexShader(Identifier.of("decide", "wet_world_vertex"))
                .withFragmentShader(Identifier.of("decide", "wet_world_fragment"))
                .withVertexFormat(VertexFormat.builder().build(), VertexFormat.DrawMode.TRIANGLES)
                .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withBlend(new BlendFunction(
                        SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
                        SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA
                ))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build();

        ByteBuffer dummy = MemoryUtil.memAlloc(4);
        dummy.putInt(0).flip();
        dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "client:wet_world_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummy
        );
        MemoryUtil.memFree(dummy);

        uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "client:wet_world_uniform",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE
        );
        uniformData = MemoryUtil.memAlloc(UNIFORM_SIZE);
    }

    public static boolean draw(
            float width, float height, float time, int traceSteps,
            float reflectionStrength, float humidity, float ripple, boolean hasSkyLight,
            float camX, float camY, float camZ, float traceDistance,
            float skyR, float skyG, float skyB,
            float sunX, float sunY, float sunZ,
            Matrix4f viewProj,
            Matrix4f invViewProj
    ) {
        if (!ensureResources()) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getFramebuffer() == null) return false;

        var fb = mc.getFramebuffer();
        var colorAttachment = fb.getColorAttachment();
        var depthAttachment = fb.getDepthAttachment();
        if (colorAttachment == null || depthAttachment == null) return false;

        int fbW = fb.textureWidth;
        int fbH = fb.textureHeight;
        if (fbW <= 0 || fbH <= 0) return false;

        ensureTextures(fbW, fbH);
        if (colorCopy == null || colorCopyView == null || depthCopy == null || depthCopyView == null) return false;

        DrawBatcher.flushPending();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(colorAttachment, colorCopy, 0, 0, 0, 0, 0, fbW, fbH);
        encoder.copyTextureToTexture(depthAttachment, depthCopy, 0, 0, 0, 0, 0, fbW, fbH);

        writeUniforms(width, height, time, traceSteps,
                reflectionStrength, humidity, ripple, hasSkyLight,
                camX, camY, camZ, traceDistance,
                skyR, skyG, skyB,
                sunX, sunY, sunZ,
                viewProj, invViewProj);

        encoder.writeToBuffer(uniformBuffer.slice(), uniformData);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "client:wet_world_pass",
                fb.getColorAttachmentView(),
                OptionalInt.empty()
        )) {
            pass.setPipeline(pipeline);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("Sampler0", colorCopyView, sampler);
            pass.bindTexture("Sampler1", depthCopyView, sampler);
            pass.setUniform("Uniforms", uniformBuffer);
            pass.draw(0, 3);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean ensureResources() {
        if (pipeline != null) return true;
        init();
        return pipeline != null;
    }

    private static void ensureTextures(int width, int height) {
        if (colorCopy != null && width == lastWidth && height == lastHeight) return;

        closeTextures();

        colorCopy = RenderSystem.getDevice().createTexture(
                () -> "client:wet_world_color",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8,
                width, height, 1, 1
        );
        colorCopyView = RenderSystem.getDevice().createTextureView(colorCopy);

        depthCopy = RenderSystem.getDevice().createTexture(
                () -> "client:wet_world_depth",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.DEPTH32,
                width, height, 1, 1
        );
        depthCopyView = RenderSystem.getDevice().createTextureView(depthCopy);

        lastWidth = width;
        lastHeight = height;
    }

    private static void writeUniforms(
            float width, float height, float time, int traceSteps,
            float reflectionStrength, float humidity, float ripple, boolean hasSkyLight,
            float camX, float camY, float camZ, float traceDistance,
            float skyR, float skyG, float skyB,
            float sunX, float sunY, float sunZ,
            Matrix4f viewProj,
            Matrix4f invViewProj
    ) {
        uniformData.clear();

        uniformData.putFloat(width).putFloat(height).putFloat(time).putFloat(traceSteps);

        uniformData.putFloat(reflectionStrength).putFloat(humidity).putFloat(ripple).putFloat(hasSkyLight ? 1.0F : 0.0F);

        uniformData.putFloat(camX).putFloat(camY).putFloat(camZ).putFloat(traceDistance);

        uniformData.putFloat(skyR).putFloat(skyG).putFloat(skyB).putFloat(0.0F);

        uniformData.putFloat(sunX).putFloat(sunY).putFloat(sunZ).putFloat(0.0F);

        putMatrix(uniformData, viewProj);
        putMatrix(uniformData, invViewProj);

        while (uniformData.position() < UNIFORM_SIZE) {
            uniformData.put((byte) 0);
        }
        uniformData.flip();
    }

    private static void putMatrix(ByteBuffer buf, Matrix4f m) {
        buf.putFloat(m.m00()).putFloat(m.m01()).putFloat(m.m02()).putFloat(m.m03());
        buf.putFloat(m.m10()).putFloat(m.m11()).putFloat(m.m12()).putFloat(m.m13());
        buf.putFloat(m.m20()).putFloat(m.m21()).putFloat(m.m22()).putFloat(m.m23());
        buf.putFloat(m.m30()).putFloat(m.m31()).putFloat(m.m32()).putFloat(m.m33());
    }

    private static void closeTextures() {
        if (depthCopyView != null) { depthCopyView.close(); depthCopyView = null; }
        if (depthCopy != null) { depthCopy.close(); depthCopy = null; }
        if (colorCopyView != null) { colorCopyView.close(); colorCopyView = null; }
        if (colorCopy != null) { colorCopy.close(); colorCopy = null; }
        lastWidth = -1;
        lastHeight = -1;
    }

    public static void shutdown() {
        closeTextures();
        if (uniformBuffer != null) { uniformBuffer.close(); uniformBuffer = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (uniformData != null) { MemoryUtil.memFree(uniformData); uniformData = null; }
        pipeline = null;
    }
}

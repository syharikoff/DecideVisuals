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

public class GlassOutlinePipeline {

    private static final int MIP_LEVELS = 3;

    private static final Identifier DOWN_PIPELINE_ID = Identifier.of("client", "pipeline/glass_outline_down");
    private static final Identifier DOWN_VSH = Identifier.of("client", "core/glass_outline");
    private static final Identifier DOWN_FSH = Identifier.of("client", "core/glass_outline_down");

    private static final Identifier UP_PIPELINE_ID = Identifier.of("client", "pipeline/glass_outline_up");
    private static final Identifier UP_VSH = Identifier.of("client", "core/glass_outline");
    private static final Identifier UP_FSH = Identifier.of("client", "core/glass_outline_up");

    private static final Identifier COMBINE_PIPELINE_ID = Identifier.of("client", "pipeline/glass_outline_combine");
    private static final Identifier COMBINE_VSH = Identifier.of("client", "core/glass_outline");
    private static final Identifier COMBINE_FSH = Identifier.of("client", "core/glass_outline_combine");

    private static final RenderPipeline DOWN_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(DOWN_PIPELINE_ID)
                    .withVertexShader(DOWN_VSH)
                    .withFragmentShader(DOWN_FSH)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("KawaseOutlineData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline UP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(UP_PIPELINE_ID)
                    .withVertexShader(UP_VSH)
                    .withFragmentShader(UP_FSH)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("KawaseOutlineData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline COMBINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(COMBINE_PIPELINE_ID)
                    .withVertexShader(COMBINE_VSH)
                    .withFragmentShader(COMBINE_FSH)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("OutlineData", UniformType.UNIFORM_BUFFER)
                    .withSampler("maskTexture")
                    .withSampler("glowTexture")
                    .withSampler("sceneTexture")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    // Mip level textures
    private final GpuTexture[] mipTextures = new GpuTexture[MIP_LEVELS];
    private final GpuTextureView[] mipViews = new GpuTextureView[MIP_LEVELS];
    private final int[] mipWidths = new int[MIP_LEVELS];
    private final int[] mipHeights = new int[MIP_LEVELS];

    private static final int MAX_FRIEND_RECTS = 8;

    private GpuBuffer kawaseBuffer;   // 16 bytes: KawaseOutlineData (vec4)
    private GpuBuffer combineBuffer;  // 208 bytes: OutlineData (13 * vec4)
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer kawaseData;
    private ByteBuffer combineData;

    private boolean initialized = false;
    private int lastWidth = 0;
    private int lastHeight = 0;
    private boolean shimmerEnabled = true;
    private float shimmerWidth = 0.04f;
    private float shimmerPeriodSec = 5.0f;

    // friend rects: [u1, v1, u2, v2] in UV space, up to 8 entries
    private final float[] friendRects = new float[MAX_FRIEND_RECTS * 4];
    private int friendCount = 0;

    public void setShimmerEnabled(boolean enabled) { this.shimmerEnabled = enabled; }
    public void setShimmerWidth(float width) { this.shimmerWidth = Math.max(0.005f, width); }
    public void setShimmerPeriodSec(float sec) { this.shimmerPeriodSec = Math.max(0.5f, sec); }
    public void setFriendRects(float[] rects, int count) {
        this.friendCount = Math.min(count, MAX_FRIEND_RECTS);
        System.arraycopy(rects, 0, this.friendRects, 0, this.friendCount * 4);
    }

    public GlassOutlinePipeline() {
    }

    private void ensureInitialized() {
        if (initialized) return;

        kawaseData = MemoryUtil.memAlloc(16);
        combineData = MemoryUtil.memAlloc(208); // 5 × vec4 + 8 × vec4 (friend rects)

        ByteBuffer dummy = MemoryUtil.memAlloc(4);
        dummy.putInt(0);
        dummy.flip();
        dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:glass_outline_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummy
        );
        MemoryUtil.memFree(dummy);

        kawaseBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:glass_outline_kawase_ubo",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                16
        );
        combineBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:glass_outline_combine_ubo",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                208
        );

        initialized = true;
    }

    private void ensureMips(int width, int height) {
        if (width == lastWidth && height == lastHeight && mipTextures[0] != null) return;

        cleanupMips();

        int w = width;
        int h = height;
        for (int i = 0; i < MIP_LEVELS; i++) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
            mipWidths[i] = w;
            mipHeights[i] = h;
            final int fw = w, fh = h, idx = i;
            mipTextures[i] = RenderSystem.getDevice().createTexture(
                    () -> "minecraft:glass_outline_mip_" + idx,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    TextureFormat.RGBA8,
                    fw, fh, 1, 1
            );
            mipViews[i] = RenderSystem.getDevice().createTextureView(mipTextures[i]);
        }

        lastWidth = width;
        lastHeight = height;
    }

    private void cleanupMips() {
        for (int i = 0; i < MIP_LEVELS; i++) {
            if (mipViews[i] != null) { mipViews[i].close(); mipViews[i] = null; }
            if (mipTextures[i] != null) { mipTextures[i].close(); mipTextures[i] = null; }
        }
    }

    private void writeKawaseUbo(CommandEncoder encoder, float halfPixelX, float halfPixelY, float offset) {
        kawaseData.clear();
        kawaseData.putFloat(halfPixelX);
        kawaseData.putFloat(halfPixelY);
        kawaseData.putFloat(offset);
        kawaseData.putFloat(0);
        kawaseData.flip();
        encoder.writeToBuffer(kawaseBuffer.slice(), kawaseData);
    }

    private void writeCombineUbo(CommandEncoder encoder, int width, int height,
                                 int fillColor, int outlineColor, int outlineWidth, int glowMode,
                                 float shimmerT, boolean useItemColor, float itemColorReach) {
        combineData.clear();
        putColor(fillColor);
        putColor(outlineColor);
        combineData.putFloat(1.0f / width); combineData.putFloat(1.0f / height);
        combineData.putFloat(outlineWidth); combineData.putFloat(friendCount);
        combineData.putFloat(glowMode); combineData.putFloat(0);
        combineData.putFloat(shimmerT); combineData.putFloat(shimmerEnabled ? 1.0f : 0.0f);
        combineData.putFloat(shimmerWidth); combineData.putFloat(useItemColor ? 1.0f : 0.0f); combineData.putFloat(itemColorReach); combineData.putFloat(0);
        // friend rects: 8 × vec4 (u1, v1, u2, v2)
        for (int i = 0; i < MAX_FRIEND_RECTS; i++) {
            int base = i * 4;
            if (i < friendCount) {
                combineData.putFloat(friendRects[base]);
                combineData.putFloat(friendRects[base + 1]);
                combineData.putFloat(friendRects[base + 2]);
                combineData.putFloat(friendRects[base + 3]);
            } else {
                combineData.putFloat(0); combineData.putFloat(0);
                combineData.putFloat(0); combineData.putFloat(0);
            }
        }
        combineData.flip();
        encoder.writeToBuffer(combineBuffer.slice(), combineData);
    }

    private void putColor(int argb) {
        combineData.putFloat(((argb >> 16) & 0xFF) / 255f);
        combineData.putFloat(((argb >>  8) & 0xFF) / 255f);
        combineData.putFloat(( argb        & 0xFF) / 255f);
        combineData.putFloat(((argb >> 24) & 0xFF) / 255f);
    }

    public void renderOutline(GpuTextureView maskView, GpuTextureView targetView,
                              int width, int height, float glowStrength, int outlineWidth,
                              int glowMode, int fillColor, int outlineColor) {
        // старый вызов без цвета предмета — сцена не сэмплится, привязываем маску как заглушку
        renderOutline(maskView, targetView, width, height, glowStrength, outlineWidth,
                glowMode, fillColor, outlineColor, maskView, false);
    }

    public void renderOutline(GpuTextureView maskView, GpuTextureView targetView,
                              int width, int height, float glowStrength, int outlineWidth,
                              int glowMode, int fillColor, int outlineColor,
                              GpuTextureView sceneView, boolean useItemColor) {
        ensureInitialized();
        ensureMips(width, height);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuSampler linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        // -- Downsample: maskView → mip[0] → mip[1] → mip[2] --
        GpuTextureView currentInput = maskView;
        int inputW = width, inputH = height;

        for (int i = 0; i < MIP_LEVELS; i++) {
            writeKawaseUbo(encoder, 0.5f / inputW, 0.5f / inputH, 1.0f);

            GpuBufferSlice dt = RenderSystem.getDynamicUniforms()
                    .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

            final GpuTextureView src = currentInput;
            final int fi = i;
            try (RenderPass rp = encoder.createRenderPass(
                    () -> "minecraft:glass_outline_down_" + fi,
                    mipViews[fi], OptionalInt.of(0))) {
                rp.setPipeline(DOWN_PIPELINE);
                rp.setVertexBuffer(0, dummyVertexBuffer);
                rp.bindTexture("Sampler0", src, linearSampler);
                RenderSystem.bindDefaultUniforms(rp);
                rp.setUniform("DynamicTransforms", dt);
                rp.setUniform("KawaseOutlineData", kawaseBuffer);
                rp.draw(0, 6);
            }

            currentInput = mipViews[i];
            inputW = mipWidths[i];
            inputH = mipHeights[i];
        }

        // -- Upsample: mip[2] → mip[1] → mip[0] --
        for (int i = MIP_LEVELS - 1; i > 0; i--) {
            float rawOffset = Math.max(0.5f, glowStrength / 4.0f);
            float offset = Math.min(rawOffset, 4.0f);

            writeKawaseUbo(encoder, 0.5f / mipWidths[i], 0.5f / mipHeights[i], offset);

            GpuBufferSlice dt = RenderSystem.getDynamicUniforms()
                    .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

            final int fi = i;
            try (RenderPass rp = encoder.createRenderPass(
                    () -> "minecraft:glass_outline_up_" + (fi - 1),
                    mipViews[fi - 1], OptionalInt.of(0))) {
                rp.setPipeline(UP_PIPELINE);
                rp.setVertexBuffer(0, dummyVertexBuffer);
                rp.bindTexture("Sampler0", mipViews[fi], linearSampler);
                RenderSystem.bindDefaultUniforms(rp);
                rp.setUniform("DynamicTransforms", dt);
                rp.setUniform("KawaseOutlineData", kawaseBuffer);
                rp.draw(0, 6);
            }
        }

        // -- Combine: mask + mip[0] → framebuffer --
        long periodMs = (long)(shimmerPeriodSec * 1000f);
        float shimmerT = (System.currentTimeMillis() % periodMs) / (float) periodMs;
        // дальность марша к силуэту — примерно ширина свечения в пикселях (+запас)
        float itemColorReach = Math.max(8.0f, glowStrength + (float) outlineWidth + 6.0f);
        writeCombineUbo(encoder, width, height, fillColor, outlineColor, outlineWidth, glowMode, shimmerT, useItemColor, itemColorReach);

        GpuBufferSlice dt = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass rp = encoder.createRenderPass(
                () -> "minecraft:glass_outline_combine",
                targetView, OptionalInt.empty())) {
            rp.setPipeline(COMBINE_PIPELINE);
            rp.setVertexBuffer(0, dummyVertexBuffer);
            rp.bindTexture("maskTexture", maskView, linearSampler);
            rp.bindTexture("glowTexture", mipViews[0], linearSampler);
            rp.bindTexture("sceneTexture", sceneView, linearSampler);
            RenderSystem.bindDefaultUniforms(rp);
            rp.setUniform("DynamicTransforms", dt);
            rp.setUniform("OutlineData", combineBuffer);
            rp.draw(0, 6);
        }
    }

    public void close() {
        cleanupMips();
        if (kawaseBuffer != null) { kawaseBuffer.close(); kawaseBuffer = null; }
        if (combineBuffer != null) { combineBuffer.close(); combineBuffer = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (kawaseData != null) { MemoryUtil.memFree(kawaseData); kawaseData = null; }
        if (combineData != null) { MemoryUtil.memFree(combineData); combineData = null; }
        initialized = false;
        lastWidth = 0;
        lastHeight = 0;
    }
}

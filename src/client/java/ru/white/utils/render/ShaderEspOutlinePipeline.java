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

/**
 * Ореольный ESP: силуэт сущностей размывается dual-Kawase пирамидой,
 * затем комбинируется поверх сцены аддитивно — внешний ореол, заливка
 * и яркая кромка.
 */
public class ShaderEspOutlinePipeline {

    /** Максимум уровней пирамиды; сколько реально используется — задаёт радиус свечения. */
    private static final int MAX_MIPS = 5;
    private static final int UBO_SIZE = 112; // 7 × vec4

    private static final Identifier VSH = Identifier.of("client", "core/shader_esp_glow");

    private static final RenderPipeline DOWN_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/shader_esp_down"))
                    .withVertexShader(VSH)
                    .withFragmentShader(Identifier.of("client", "core/shader_esp_down"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("EspKawaseData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline UP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/shader_esp_up"))
                    .withVertexShader(VSH)
                    .withFragmentShader(Identifier.of("client", "core/shader_esp_up"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("EspKawaseData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline COMBINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/shader_esp_combine"))
                    .withVertexShader(VSH)
                    .withFragmentShader(Identifier.of("client", "core/shader_esp_combine"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("EspData", UniformType.UNIFORM_BUFFER)
                    .withSampler("maskTexture")
                    .withSampler("glowTexture")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    /** Все параметры внешнего вида — заполняются модулем каждый кадр. */
    public static final class Params {
        public int color = 0xFF00FFFF;
        public int friendColor = 0xFF55FF55;
        public boolean friendEnabled = true;
        public float opacity = 1.0f;
        public float saturation = 1.0f;

        public boolean glowEnabled = true;
        public float glowRadius = 0.5f;   // 0..1
        public float glowStrength = 1.4f;
        public float glowFalloff = 1.2f;

        public boolean fillEnabled = true;
        public float fillOpacity = 0.35f;
        public float innerGlow = 0.5f;

        public boolean outlineEnabled = true;
        public int outlineWidth = 2;
        public int outlineMode = 0;       // 0 снаружи, 1 внутри, 2 обе
        public float outlineStrength = 1.5f;
        public float outlineWhite = 0.5f;

        public boolean pulseEnabled = false;
        public float pulseSpeed = 1.0f;
        public float pulseAmount = 0.3f;

        public boolean shimmerEnabled = true;
        public float shimmerWidth = 0.04f;
        public float shimmerPeriodSec = 5.0f;
        public float shimmerBrightness = 0.8f;
    }

    private final GpuTexture[] mipTextures = new GpuTexture[MAX_MIPS];
    private final GpuTextureView[] mipViews = new GpuTextureView[MAX_MIPS];
    private final int[] mipWidths = new int[MAX_MIPS];
    private final int[] mipHeights = new int[MAX_MIPS];

    private GpuBuffer kawaseBuffer;
    private GpuBuffer espBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer kawaseData;
    private ByteBuffer espData;

    private boolean initialized = false;
    private int lastWidth = 0;
    private int lastHeight = 0;

    private void ensureInitialized() {
        if (initialized) return;

        kawaseData = MemoryUtil.memAlloc(16);
        espData = MemoryUtil.memAlloc(UBO_SIZE);

        ByteBuffer dummy = MemoryUtil.memAlloc(4);
        dummy.putInt(0);
        dummy.flip();
        dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:shader_esp_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummy
        );
        MemoryUtil.memFree(dummy);

        kawaseBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:shader_esp_kawase_ubo",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                16
        );
        espBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:shader_esp_ubo",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UBO_SIZE
        );

        initialized = true;
    }

    private void ensureMips(int width, int height) {
        if (width == lastWidth && height == lastHeight && mipTextures[0] != null) return;

        cleanupMips();

        int w = width;
        int h = height;
        for (int i = 0; i < MAX_MIPS; i++) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
            mipWidths[i] = w;
            mipHeights[i] = h;
            final int fw = w, fh = h, idx = i;
            mipTextures[i] = RenderSystem.getDevice().createTexture(
                    () -> "minecraft:shader_esp_mip_" + idx,
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
        for (int i = 0; i < MAX_MIPS; i++) {
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

    private void writeEspUbo(CommandEncoder encoder, int width, int height, Params p) {
        espData.clear();

        putRgb(p.color);
        espData.putFloat(clamp(p.opacity, 0f, 1f));

        putRgb(p.friendColor);
        espData.putFloat(p.friendEnabled ? 1f : 0f);

        // params1: texelX, texelY, outlineWidth, outlineMode
        espData.putFloat(1f / width);
        espData.putFloat(1f / height);
        espData.putFloat(Math.max(0, Math.min(5, p.outlineWidth)));
        espData.putFloat(p.outlineMode);

        // params2: glowStrength, glowFalloff, fillOpacity, innerGlow
        espData.putFloat(Math.max(0f, p.glowStrength));
        espData.putFloat(Math.max(0.05f, p.glowFalloff));
        espData.putFloat(Math.max(0f, p.fillOpacity));
        espData.putFloat(Math.max(0f, p.innerGlow));

        // params3: outlineStrength, outlineWhite, glowEnabled, fillEnabled
        espData.putFloat(Math.max(0f, p.outlineStrength));
        espData.putFloat(clamp(p.outlineWhite, 0f, 1f));
        espData.putFloat(p.glowEnabled ? 1f : 0f);
        espData.putFloat(p.fillEnabled ? 1f : 0f);

        // params4: shimmerT, shimmerEnabled, shimmerWidth, shimmerBrightness
        espData.putFloat(phase(p.shimmerPeriodSec));
        espData.putFloat(p.shimmerEnabled ? 1f : 0f);
        espData.putFloat(Math.max(0.005f, p.shimmerWidth));
        espData.putFloat(Math.max(0f, p.shimmerBrightness));

        // params5: pulseAmount, pulseT, saturation, outlineEnabled
        espData.putFloat(p.pulseEnabled ? clamp(p.pulseAmount, 0f, 1f) : 0f);
        espData.putFloat(phase(1f / Math.max(0.05f, p.pulseSpeed)));
        espData.putFloat(Math.max(0f, p.saturation));
        espData.putFloat(p.outlineEnabled ? 1f : 0f);

        espData.flip();
        encoder.writeToBuffer(espBuffer.slice(), espData);
    }

    private void putRgb(int argb) {
        espData.putFloat(((argb >> 16) & 0xFF) / 255f);
        espData.putFloat(((argb >> 8) & 0xFF) / 255f);
        espData.putFloat((argb & 0xFF) / 255f);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    /** Позиция в цикле длиной periodSec, 0..1. */
    private static float phase(float periodSec) {
        long periodMs = Math.max(1L, (long) (periodSec * 1000f));
        return (System.currentTimeMillis() % periodMs) / (float) periodMs;
    }

    public void render(GpuTextureView maskView, GpuTextureView targetView, int width, int height, Params p) {
        ensureInitialized();
        ensureMips(width, height);

        // радиус свечения: сколько уровней пирамиды проходим и насколько широко разводим на подъёме
        float radius = clamp(p.glowRadius, 0f, 1f);
        int levels = Math.max(1, Math.min(MAX_MIPS, 1 + Math.round(radius * (MAX_MIPS - 1))));
        float upOffset = 0.5f + radius * 3.5f;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuSampler linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        // -- Downsample: маска → mip[0] → ... → mip[levels-1] --
        GpuTextureView currentInput = maskView;
        int inputW = width, inputH = height;

        for (int i = 0; i < levels; i++) {
            writeKawaseUbo(encoder, 0.5f / inputW, 0.5f / inputH, 1.0f);

            GpuBufferSlice dt = RenderSystem.getDynamicUniforms()
                    .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

            final GpuTextureView src = currentInput;
            final int fi = i;
            try (RenderPass rp = encoder.createRenderPass(
                    () -> "minecraft:shader_esp_down_" + fi,
                    mipViews[fi], OptionalInt.of(0))) {
                rp.setPipeline(DOWN_PIPELINE);
                rp.setVertexBuffer(0, dummyVertexBuffer);
                rp.bindTexture("Sampler0", src, linearSampler);
                RenderSystem.bindDefaultUniforms(rp);
                rp.setUniform("DynamicTransforms", dt);
                rp.setUniform("EspKawaseData", kawaseBuffer);
                rp.draw(0, 6);
            }

            currentInput = mipViews[i];
            inputW = mipWidths[i];
            inputH = mipHeights[i];
        }

        // -- Upsample: mip[levels-1] → ... → mip[0] --
        for (int i = levels - 1; i > 0; i--) {
            writeKawaseUbo(encoder, 0.5f / mipWidths[i], 0.5f / mipHeights[i], upOffset);

            GpuBufferSlice dt = RenderSystem.getDynamicUniforms()
                    .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

            final int fi = i;
            try (RenderPass rp = encoder.createRenderPass(
                    () -> "minecraft:shader_esp_up_" + (fi - 1),
                    mipViews[fi - 1], OptionalInt.of(0))) {
                rp.setPipeline(UP_PIPELINE);
                rp.setVertexBuffer(0, dummyVertexBuffer);
                rp.bindTexture("Sampler0", mipViews[fi], linearSampler);
                RenderSystem.bindDefaultUniforms(rp);
                rp.setUniform("DynamicTransforms", dt);
                rp.setUniform("EspKawaseData", kawaseBuffer);
                rp.draw(0, 6);
            }
        }

        // -- Combine: маска + mip[0] → экран --
        writeEspUbo(encoder, width, height, p);

        GpuBufferSlice dt = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass rp = encoder.createRenderPass(
                () -> "minecraft:shader_esp_combine",
                targetView, OptionalInt.empty())) {
            rp.setPipeline(COMBINE_PIPELINE);
            rp.setVertexBuffer(0, dummyVertexBuffer);
            rp.bindTexture("maskTexture", maskView, linearSampler);
            rp.bindTexture("glowTexture", mipViews[0], linearSampler);
            RenderSystem.bindDefaultUniforms(rp);
            rp.setUniform("DynamicTransforms", dt);
            rp.setUniform("EspData", espBuffer);
            rp.draw(0, 6);
        }
    }

    public void close() {
        cleanupMips();
        if (kawaseBuffer != null) { kawaseBuffer.close(); kawaseBuffer = null; }
        if (espBuffer != null) { espBuffer.close(); espBuffer = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (kawaseData != null) { MemoryUtil.memFree(kawaseData); kawaseData = null; }
        if (espData != null) { MemoryUtil.memFree(espData); espData = null; }
        initialized = false;
        lastWidth = 0;
        lastHeight = 0;
    }
}

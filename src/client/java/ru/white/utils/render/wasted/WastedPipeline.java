package ru.white.utils.render.wasted;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Портированный Kimiko WastedRenderer — пост-обработка кадра при смерти:
 * обесцвечивание, тонировка, вспышка и радиальное размытие.
 *
 * Исходники GLSL в ShaderData (post/wasted/*), НЕ в assets.
 */
public final class WastedPipeline {

    private static final int UNIFORM_FLOATS = 12;
    private static final int UNIFORM_SIZE = 48;

    private static final Logger LOGGER = LoggerFactory.getLogger("client/Wasted");

    private static final Identifier PIPELINE_ID = Identifier.of("client", "pipeline/post/wasted");
    private static final Identifier SHADER = Identifier.of("client", "post/wasted/wasted");

    private static RenderPipeline pipeline;
    private static ByteBuffer dataBuffer;
    private static GpuBuffer uniformBuffer;

    private static GpuTexture sceneCopy;
    private static GpuTextureView sceneCopyView;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static boolean disabledAfterError;

    private WastedPipeline() {
    }

    public static void invalidate() {
        disabledAfterError = false;
        closeTextures();
    }

    public static void apply(float tintR, float tintG, float tintB, float tintStrength) {
        if (disabledAfterError || !WastedState.isActive()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer fb = client.getFramebuffer();
        if (fb == null) return;

        int width = fb.textureWidth;
        int height = fb.textureHeight;
        if (width <= 0 || height <= 0) return;
        if (!ensureInitialized()) return;

        try {
            ensureTextures(width, height);

            ByteBuffer data = dataBuffer;
            GpuBuffer uniform = uniformBuffer;

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(fb.getColorAttachment(), sceneCopy, 0, 0, 0, 0, 0, width, height);

            data.clear();
            data.putFloat(WastedState.progress());
            data.putFloat((System.currentTimeMillis() % 100000L) / 1000.0f);
            data.putFloat(width / (float) Math.max(1, height));
            data.putFloat(WastedState.strength());
            data.putFloat(tintR);
            data.putFloat(tintG);
            data.putFloat(tintB);
            data.putFloat(tintStrength);
            data.putFloat(WastedState.flash());
            data.putFloat(WastedState.radialBlur());
            data.putFloat(0.0f);
            data.putFloat(0.0f);
            data.flip();

            encoder.writeToBuffer(uniform.slice(), data);

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "client:wasted",
                    fb.getColorAttachmentView(),
                    OptionalInt.empty())) {
                pass.setPipeline(pipeline);
                pass.bindTexture("Sampler0", sceneCopyView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                pass.setUniform("WastedData", uniform.slice());
                pass.draw(0, 6);
            }
        } catch (Throwable t) {
            disabledAfterError = true;
            LOGGER.error("[Wasted] apply failed, effect disabled", t);
            closeTextures();
        }
    }

    public static boolean deviceReady() {
        return RenderSystem.tryGetDevice() != null;
    }

    public static boolean validate() {
        if (disabledAfterError) return false;
        if (!deviceReady()) return false;
        return ensureInitialized();
    }

    private static boolean ensureInitialized() {
        GpuBuffer current = uniformBuffer;
        if (pipeline != null && dataBuffer != null && current != null && !current.isClosed()) return true;

        try {
            if (pipeline == null) {
                pipeline = RenderPipelines.register(RenderPipeline.builder()
                        .withLocation(PIPELINE_ID)
                        .withVertexShader(SHADER)
                        .withFragmentShader(SHADER)
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .withUniform("WastedData", UniformType.UNIFORM_BUFFER)
                        .withSampler("Sampler0")
                        .withoutBlend()
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build());
            }
            if (dataBuffer == null) dataBuffer = MemoryUtil.memAlloc(UNIFORM_SIZE);

            GpuBuffer uniform = uniformBuffer;
            if (uniform == null || uniform.isClosed()) {
                uniformBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "client:wasted_uniforms",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        UNIFORM_SIZE);
            }
            return true;
        } catch (Throwable t) {
            disabledAfterError = true;
            pipeline = null;
            LOGGER.error("[Wasted] pipeline init failed, effect disabled", t);
            closeUniform();
            return false;
        }
    }

    private static void ensureTextures(int width, int height) {
        GpuTexture current = sceneCopy;
        if (current != null && !current.isClosed() && width == lastWidth && height == lastHeight) return;

        closeTextures();

        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) return;

        sceneCopy = device.createTexture(
                () -> "client:wasted_scene",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, width, height, 1, 1);
        sceneCopyView = device.createTextureView(sceneCopy);

        lastWidth = width;
        lastHeight = height;
    }

    private static void closeTextures() {
        if (sceneCopyView != null) { sceneCopyView.close(); sceneCopyView = null; }
        if (sceneCopy != null) { sceneCopy.close(); sceneCopy = null; }
        lastWidth = -1;
        lastHeight = -1;
    }

    private static void closeUniform() {
        if (uniformBuffer != null) { uniformBuffer.close(); uniformBuffer = null; }
    }
}

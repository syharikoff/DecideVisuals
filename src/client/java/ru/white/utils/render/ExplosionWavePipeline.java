package ru.white.utils.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
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
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Порт Kimiko ExplosionWaveRenderer — ударная волна по кадру:
 * преломление по depth + хроматическая аберрация + вспышка.
 *
 * Исходники GLSL в ShaderData (post/explosionwave/*), НЕ в assets.
 */
public final class ExplosionWavePipeline {

    public static final int UNIFORM_FLOATS = 120;
    private static final int UNIFORM_SIZE = 480;

    private static final Logger LOGGER = LoggerFactory.getLogger("client/ExplosionWave");

    private static final Identifier PIPELINE_ID = Identifier.of("client", "pipeline/post/explosionwave");
    private static final Identifier SHADER = Identifier.of("client", "post/explosionwave/explosionwave");

    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;

    private static GpuTexture sceneCopy;
    private static GpuTextureView sceneCopyView;
    private static GpuTexture depthCopy;
    private static GpuTextureView depthCopyView;

    private static int sceneWidth = -1;
    private static int sceneHeight = -1;
    private static boolean disabledAfterError;

    private ExplosionWavePipeline() {
    }

    public static void apply(Framebuffer renderTarget, float[] uniform) {
        if (disabledAfterError) return;
        if (renderTarget == null) return;
        if (renderTarget.getColorAttachment() == null || renderTarget.getColorAttachmentView() == null) return;
        if (renderTarget.getDepthAttachment() == null) return;
        if (renderTarget.textureWidth <= 0 || renderTarget.textureHeight <= 0) return;

        init();
        if (pipeline == null || uniformBuffer == null) return;
        if (!ensureSceneCopy(renderTarget.textureWidth, renderTarget.textureHeight)) return;

        try {
            GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            GpuSampler nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(renderTarget.getColorAttachment(), sceneCopy,
                    0, 0, 0, 0, 0, renderTarget.textureWidth, renderTarget.textureHeight);
            encoder.copyTextureToTexture(renderTarget.getDepthAttachment(), depthCopy,
                    0, 0, 0, 0, 0, renderTarget.textureWidth, renderTarget.textureHeight);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = stack.calloc(UNIFORM_SIZE);
                for (int i = 0; i < UNIFORM_FLOATS; i++) {
                    data.putFloat(i * 4, i < uniform.length ? uniform[i] : 0.0F);
                }
                data.position(0);
                encoder.writeToBuffer(uniformBuffer.slice(0L, UNIFORM_SIZE), data);
            }

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "client:explosion_wave",
                    renderTarget.getColorAttachmentView(),
                    OptionalInt.empty())) {
                pass.setPipeline(pipeline);
                pass.bindTexture("Scene", sceneCopyView, linear);
                pass.bindTexture("DepthSampler", depthCopyView, nearest);
                pass.setUniform("Waves", uniformBuffer);
                pass.draw(0, 6);
            }
        } catch (Throwable t) {
            disabledAfterError = true;
            LOGGER.error("[ExplosionWave] apply failed, effect disabled", t);
            closeTargets();
        }
    }

    /** Готов ли рендер-девайс (на старте клиента может быть ещё нет). */
    public static boolean deviceReady() {
        return RenderSystem.tryGetDevice() != null;
    }

    /**
     * Ранняя проверка: регистрирует пайплайн и буфер, чтобы сбой всплыл в логе на старте,
     * а не молча отключал эффект на первом взрыве.
     * Если девайс ещё не готов — просто false, без отключения эффекта.
     */
    public static boolean validate() {
        if (disabledAfterError) return false;
        if (!deviceReady()) return false;
        init();
        return pipeline != null && uniformBuffer != null;
    }

    private static void init() {
        if (disabledAfterError) return;
        try {
            if (pipeline == null) {
                pipeline = RenderPipelines.register(RenderPipeline.builder()
                        .withLocation(PIPELINE_ID)
                        .withVertexShader(SHADER)
                        .withFragmentShader(SHADER)
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .withUniform("Waves", UniformType.UNIFORM_BUFFER)
                        .withSampler("Scene")
                        .withSampler("DepthSampler")
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build());
            }
            GpuBuffer current = uniformBuffer;
            if (current == null || current.isClosed() || current.size() < UNIFORM_SIZE) {
                closeUniform();
                uniformBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "client:explosion_wave_uniforms",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        UNIFORM_SIZE);
            }
        } catch (Throwable t) {
            disabledAfterError = true;
            LOGGER.error("[ExplosionWave] pipeline init failed, effect disabled", t);
            pipeline = null;
            closeUniform();
        }
    }

    private static boolean ensureSceneCopy(int width, int height) {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) return false;
        if (sceneCopy != null && sceneWidth == width && sceneHeight == height) return true;

        closeTargets();

        sceneCopy = device.createTexture(
                () -> "client:explosion_wave_scene",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, width, height, 1, 1);
        sceneCopyView = device.createTextureView(sceneCopy);

        depthCopy = device.createTexture(
                () -> "client:explosion_wave_depth",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.DEPTH32, width, height, 1, 1);
        depthCopyView = device.createTextureView(depthCopy);

        sceneWidth = width;
        sceneHeight = height;
        return true;
    }

    public static void clear() {
        closeTargets();
    }

    private static void closeTargets() {
        if (sceneCopyView != null) { sceneCopyView.close(); sceneCopyView = null; }
        if (sceneCopy != null) { sceneCopy.close(); sceneCopy = null; }
        if (depthCopyView != null) { depthCopyView.close(); depthCopyView = null; }
        if (depthCopy != null) { depthCopy.close(); depthCopy = null; }
        sceneWidth = -1;
        sceneHeight = -1;
    }

    private static void closeUniform() {
        if (uniformBuffer != null) { uniformBuffer.close(); uniformBuffer = null; }
    }
}

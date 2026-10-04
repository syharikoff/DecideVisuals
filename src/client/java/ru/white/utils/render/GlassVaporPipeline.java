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
 * Порт Kimiko GlassVaporRenderer — пост-обработка кадра: преломление и блики
 * по «каплям» пара над водой.
 *
 * UBO Vapor: header(4) + header2(4) + header3(4) + header4(4) + data[240*4] + palette[6].
 * Исходники GLSL в ShaderData (post/glassvapor/*), НЕ в assets.
 */
public final class GlassVaporPipeline {

    /** Kimiko: 3856 float + 6 vec4 палитры (header4.x = число цветов). */
    public static final int UNIFORM_FLOATS = 3856 + 24;
    private static final int UNIFORM_SIZE = UNIFORM_FLOATS * 4;

    private static final Logger LOGGER = LoggerFactory.getLogger("client/GlassVapor");

    private static final Identifier PIPELINE_ID = Identifier.of("client", "pipeline/post/glassvapor");
    private static final Identifier SHADER = Identifier.of("client", "post/glassvapor/glassvapor");

    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;

    private static GpuTexture sceneCopy;
    private static GpuTextureView sceneCopyView;
    private static GpuTexture depthCopy;
    private static GpuTextureView depthCopyView;

    private static int sceneWidth = -1;
    private static int sceneHeight = -1;
    private static boolean disabledAfterError;

    private GlassVaporPipeline() {
    }

    public static boolean deviceReady() {
        return RenderSystem.tryGetDevice() != null;
    }

    public static boolean validate() {
        if (disabledAfterError) return false;
        if (!deviceReady()) return false;
        return init();
    }

    public static void apply(Framebuffer renderTarget, float[] uniform) {
        if (disabledAfterError || renderTarget == null) return;
        if (renderTarget.getColorAttachment() == null || renderTarget.getColorAttachmentView() == null) return;
        if (renderTarget.getDepthAttachment() == null) return;
        if (renderTarget.textureWidth <= 0 || renderTarget.textureHeight <= 0) return;

        if (!init()) return;
        if (!ensureSceneCopy(renderTarget.textureWidth, renderTarget.textureHeight)) return;

        try {
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
                    () -> "client:glass_vapor",
                    renderTarget.getColorAttachmentView(),
                    OptionalInt.empty())) {
                pass.setPipeline(pipeline);
                pass.bindTexture("Scene", sceneCopyView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                pass.bindTexture("DepthSampler", depthCopyView, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                pass.setUniform("Vapor", uniformBuffer);
                pass.draw(0, 6);
            }
        } catch (Throwable t) {
            disabledAfterError = true;
            LOGGER.error("[GlassVapor] apply failed, effect disabled", t);
            closeTargets();
        }
    }

    private static boolean init() {
        GpuBuffer current = uniformBuffer;
        if (pipeline != null && current != null && !current.isClosed()) return true;

        try {
            if (pipeline == null) {
                pipeline = RenderPipelines.register(RenderPipeline.builder()
                        .withLocation(PIPELINE_ID)
                        .withVertexShader(SHADER)
                        .withFragmentShader(SHADER)
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .withUniform("Vapor", UniformType.UNIFORM_BUFFER)
                        .withSampler("Scene")
                        .withSampler("DepthSampler")
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build());
            }
            if (current == null || current.isClosed()) {
                uniformBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "client:glass_vapor_uniforms",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        UNIFORM_SIZE);
            }
            return true;
        } catch (Throwable t) {
            disabledAfterError = true;
            pipeline = null;
            LOGGER.error("[GlassVapor] pipeline init failed, effect disabled", t);
            closeUniform();
            return false;
        }
    }

    private static boolean ensureSceneCopy(int width, int height) {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) return false;
        if (sceneCopy != null && sceneWidth == width && sceneHeight == height) return true;

        closeTargets();

        sceneCopy = device.createTexture(
                () -> "client:glass_vapor_scene",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, width, height, 1, 1);
        sceneCopyView = device.createTextureView(sceneCopy);

        depthCopy = device.createTexture(
                () -> "client:glass_vapor_depth",
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
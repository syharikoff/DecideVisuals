package ru.white.utils.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
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

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Порт Kimiko JumpSoulsRenderer — режим «Разлом» (неоновые трещины + объёмный
 * столб света при приземлении).
 *
 * Три прохода:
 *  1) ground-pass — трещины по земле, пишут цвет + премультипликативную альфу в кадр;
 *  2) volume-pass — то же в свечение 1/4 разрешения (очищается в 0);
 *  3) composite — добавляет свечение поверх кадра через альфу.
 *
 * Исходники GLSL лежат в ShaderData (post/jumpsouls/*), НЕ в assets — иначе
 * ShaderLoader.prepare попытается собрать их как отдельные программы.
 */
public final class JumpSoulsPipeline {

    public static final int UNIFORM_FLOATS = 412;
    private static final int UNIFORM_SIZE = 1648;
    private static final int GLOW_DOWNSCALE = 4;

    private static final Identifier PIPELINE_ID = Identifier.of("client", "pipeline/post/jumpsouls");
    private static final Identifier COMPOSITE_PIPELINE_ID = Identifier.of("client", "pipeline/post/jumpsouls_composite");
    private static final Identifier SHADER = Identifier.of("client", "post/jumpsouls/jumpsouls");
    private static final Identifier COMPOSITE_SHADER = Identifier.of("client", "post/jumpsouls/composite");

    private static RenderPipeline pipeline;
    private static RenderPipeline compositePipeline;
    private static GpuBuffer groundUniform;
    private static GpuBuffer volumeUniform;

    private static GpuTexture depthCopy;
    private static GpuTextureView depthCopyView;
    private static GpuTexture glowTexture;
    private static GpuTextureView glowTextureView;

    private static int targetWidth = -1;
    private static int targetHeight = -1;
    private static boolean disabledAfterError;

    private JumpSoulsPipeline() {
    }

    public static void apply(Framebuffer renderTarget, float[] uniform) {
        if (disabledAfterError) return;
        if (renderTarget == null) return;
        if (renderTarget.getColorAttachmentView() == null || renderTarget.getDepthAttachment() == null) return;
        if (renderTarget.textureWidth <= 0 || renderTarget.textureHeight <= 0) return;

        init();
        if (pipeline == null || compositePipeline == null) return;
        if (groundUniform == null || volumeUniform == null) return;
        if (!ensureTargets(renderTarget.textureWidth, renderTarget.textureHeight)) return;

        boolean glowOn = uniform.length > 5 && uniform[5] > 0.001F;

        try {
            GpuSampler nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
            GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(renderTarget.getDepthAttachment(), depthCopy,
                    0, 0, 0, 0, 0, renderTarget.textureWidth, renderTarget.textureHeight);

            upload(encoder, groundUniform, uniform, 0.0F);

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "client:jumpsouls_ground",
                    renderTarget.getColorAttachmentView(),
                    OptionalInt.empty())) {
                pass.setPipeline(pipeline);
                pass.bindTexture("DepthSampler", depthCopyView, nearest);
                pass.setUniform("Souls", groundUniform);
                pass.draw(0, 6);
            }

            if (!glowOn) return;

            upload(encoder, volumeUniform, uniform, 1.0F);

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "client:jumpsouls_volume",
                    glowTextureView,
                    OptionalInt.of(0))) {
                pass.setPipeline(pipeline);
                pass.bindTexture("DepthSampler", depthCopyView, nearest);
                pass.setUniform("Souls", volumeUniform);
                pass.draw(0, 6);
            }

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "client:jumpsouls_composite",
                    renderTarget.getColorAttachmentView(),
                    OptionalInt.empty())) {
                pass.setPipeline(compositePipeline);
                pass.bindTexture("GlowSampler", glowTextureView, linear);
                pass.draw(0, 6);
            }
        } catch (Throwable t) {
            disabledAfterError = true;
            closeTargets();
        }
    }

    /** Режим прохода едет в header3.x (float 32) — там же, где Kimiko его писал руками. */
    private static void upload(CommandEncoder encoder, GpuBuffer buffer, float[] uniform, float mode) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(UNIFORM_SIZE);
            for (int i = 0; i < UNIFORM_FLOATS; i++) {
                data.putFloat(i * 4, i < uniform.length ? uniform[i] : 0.0F);
            }
            data.putFloat(32, mode);
            data.position(0);
            encoder.writeToBuffer(buffer.slice(0L, UNIFORM_SIZE), data);
        }
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
                        .withUniform("Souls", UniformType.UNIFORM_BUFFER)
                        .withSampler("DepthSampler")
                        .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA))
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build());
            }
            if (compositePipeline == null) {
                compositePipeline = RenderPipelines.register(RenderPipeline.builder()
                        .withLocation(COMPOSITE_PIPELINE_ID)
                        .withVertexShader(SHADER)
                        .withFragmentShader(COMPOSITE_SHADER)
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .withSampler("GlowSampler")
                        .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA))
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build());
            }
            groundUniform = ensureUniform(groundUniform, "client:jump_souls_uniforms");
            volumeUniform = ensureUniform(volumeUniform, "client:jump_souls_volume_uniforms");
        } catch (Throwable t) {
            disabledAfterError = true;
            pipeline = null;
            compositePipeline = null;
            closeUniforms();
        }
    }

    private static GpuBuffer ensureUniform(GpuBuffer current, String name) {
        if (current != null && !current.isClosed() && current.size() >= UNIFORM_SIZE) {
            return current;
        }
        if (current != null) current.close();
        return RenderSystem.getDevice().createBuffer(
                () -> name,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE);
    }

    private static boolean ensureTargets(int width, int height) {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) return false;
        if (depthCopy != null && glowTexture != null && targetWidth == width && targetHeight == height) {
            return true;
        }

        closeTargets();

        depthCopy = device.createTexture(
                () -> "client:jumpsouls_depth_copy",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.DEPTH32, width, height, 1, 1);
        depthCopyView = device.createTextureView(depthCopy);

        glowTexture = device.createTexture(
                () -> "client:jumpsouls_glow",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8,
                Math.max(1, width / GLOW_DOWNSCALE), Math.max(1, height / GLOW_DOWNSCALE), 1, 1);
        glowTextureView = device.createTextureView(glowTexture);

        targetWidth = width;
        targetHeight = height;
        return true;
    }

    public static void clear() {
        closeTargets();
    }

    private static void closeTargets() {
        if (depthCopyView != null) { depthCopyView.close(); depthCopyView = null; }
        if (depthCopy != null) { depthCopy.close(); depthCopy = null; }
        if (glowTextureView != null) { glowTextureView.close(); glowTextureView = null; }
        if (glowTexture != null) { glowTexture.close(); glowTexture = null; }
        targetWidth = -1;
        targetHeight = -1;
    }

    private static void closeUniforms() {
        if (groundUniform != null) { groundUniform.close(); groundUniform = null; }
        if (volumeUniform != null) { volumeUniform.close(); volumeUniform = null; }
    }
}

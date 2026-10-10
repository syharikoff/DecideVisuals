package ru.decide.utils.render.post.guilayerblur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.decide.utils.render.others.RenderSampler;
import ru.decide.utils.render.post.GuiShatterAnimation;

public final class GuiLayerBlurRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("DecideVisuals:GuiLayerBlur");
    private static final int BLUR_UNIFORM_BYTES = 16;
    private static final int COMPOSITE_UNIFORM_BYTES = 16;
    private static final float TAPS_HALF = 16.0F;
    private static final Identifier BLUR_PIPELINE_ID = id("pipeline/post/guilayerblur/gaussian");
    private static final Identifier COMPOSITE_PIPELINE_ID = id("pipeline/post/guilayerblur/composite");
    private static final Identifier FULLSCREEN = id("post/guilayerblur/fullscreen");
    private static final Identifier SHARD_PIPELINE_ID = id("pipeline/post/guilayerblur/shard");
    private static final Identifier SHARD_SHADER = id("post/guilayerblur/shard");
    private static final Identifier GAUSSIAN_SHADER = id("post/guilayerblur/gaussian");
    private static final Identifier COMPOSITE_SHADER = id("post/guilayerblur/composite");
    private static final VertexFormat SHARD_FORMAT = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .build();
    private static RenderPipeline blurPipeline;
    private static RenderPipeline compositePipeline;
    private static RenderPipeline shardPipeline;
    private static GpuBuffer blurUniform;
    private static GpuBuffer compositeUniform;
    private static GpuBuffer shardVertexBuffer;
    private static ByteBuffer shardVertexData;
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();
    private static SimpleFramebuffer guiFbo;
    private static SimpleFramebuffer tempH;
    private static SimpleFramebuffer tempV;
    private static int texWidth = -1;
    private static int texHeight = -1;
    private static boolean captureActive;
    private static boolean panelRangeActive;
    private static boolean disabledAfterError;

    private GuiLayerBlurRenderer() {}

    public static void shutdown() { captureActive = false; closeTargets(); }

    private static Identifier id(String s) { return Identifier.of("decide", s); }

    private static SimpleFramebuffer destroy(SimpleFramebuffer t) {
        if (t != null) t.delete();
        return null;
    }

    public static boolean available() { return !disabledAfterError; }

    private static float clamp01(float f) { return Math.max(0.0F, Math.min(1.0F, f)); }

    private static boolean drawShards(CommandEncoder ce, GpuTextureView guiTex, float progress, float shatterAlpha, float screenScale, boolean worldMode) {
        if (shardPipeline == null || shardVertexBuffer == null || shardVertexData == null || tempH == null) return false;
        int count = GuiShatterAnimation.buildGeometry(shardVertexData, progress, screenScale, screenScale, worldMode);
        if (count <= 0 || shardVertexData.remaining() <= 0) return false;
        ce.writeToBuffer(shardVertexBuffer.slice(0L, shardVertexData.remaining()), shardVertexData);
        RenderPass rp = ce.createRenderPass(() -> "decidevisuals:gui_capture_shards", tempH.getColorAttachmentView(), OptionalInt.of(0));
        try {
            rp.setPipeline(shardPipeline);
            rp.setVertexBuffer(0, shardVertexBuffer);
            rp.bindTexture("uGui", guiTex, RenderSampler.linear());
            rp.draw(0, count);
        } catch (Throwable t) { try { rp.close(); } catch (Throwable t2) { t.addSuppressed(t2); } throw t; }
        rp.close();
        return true;
    }

    public static Framebuffer captureTarget() {
        return captureActive && panelRangeActive && guiFbo != null ? guiFbo : null;
    }

    public static void beginCapture(boolean enable) {
        captureActive = false;
        panelRangeActive = false;
        if (!enable || disabledAfterError) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer fb = mc != null ? mc.getFramebuffer() : null;
        if (fb == null || fb.getColorAttachmentView() == null || fb.textureWidth <= 0 || fb.textureHeight <= 0) return;
        try {
            if (!ensurePipelines() || !ensureTargets(fb.textureWidth, fb.textureHeight)) return;
            CommandEncoder ce = RenderSystem.getDevice().createCommandEncoder();
            RenderPass rp = ce.createRenderPass(
                () -> "decidevisuals:gui_capture_clear", guiFbo.getColorAttachmentView(), OptionalInt.of(0),
                guiFbo.getDepthAttachmentView(), OptionalDouble.of(1.0));
            if (rp != null) rp.close();
            captureActive = true;
        } catch (Throwable t) { disableAfterError(t); }
    }

    public static void markPanelRange() { if (captureActive) panelRangeActive = true; }

    public static void composite(float screenScale, float blurRadius) {
        if (!captureActive || guiFbo == null || disabledAfterError) { captureActive = false; return; }
        captureActive = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer fb = mc != null ? mc.getFramebuffer() : null;
        if (fb == null || fb.getColorAttachmentView() == null || compositePipeline == null) return;
        int w = fb.textureWidth;
        int h = fb.textureHeight;
        if (w != texWidth || h != texHeight) return;
        try {
            CommandEncoder ce = RenderSystem.getDevice().createCommandEncoder();
            float shatterP = GuiShatterAnimation.progress(0.0F);
            boolean hasShatter = GuiShatterAnimation.isActive() && shatterP > 0.0F && tempH != null && tempV != null;

            if (hasShatter) {
                if (!drawShards(ce, guiFbo.getColorAttachmentView(), shatterP, 1.0F, screenScale, false)) return;
                if (blurRadius >= 0.5F) {
                    float b = blurRadius / 16.0F;
                    gaussianPass(ce, tempH.getColorAttachmentView(), tempV, b / w, 0.0F);
                    gaussianPass(ce, tempV.getColorAttachmentView(), tempH, 0.0F, b / h);
                }
                writeCompositeUniform(ce, 1.0F, 1.0F);
                RenderPass rp = ce.createRenderPass(() -> "decidevisuals:gui_capture_shard_composite", fb.getColorAttachmentView(), OptionalInt.empty());
                try {
                    rp.setPipeline(compositePipeline);
                    rp.bindTexture("uGui", tempH.getColorAttachmentView(), RenderSampler.linear());
                    rp.setUniform("CompositeData", compositeUniform);
                    rp.draw(0, 6);
                } catch (Throwable t) { try { rp.close(); } catch (Throwable t2) { t.addSuppressed(t2); } throw t; }
                rp.close();
                return;
            }

            GpuTextureView src = guiFbo.getColorAttachmentView();
            if (blurRadius >= 0.5F && tempH != null && tempV != null) {
                float b = blurRadius / 16.0F;
                gaussianPass(ce, guiFbo.getColorAttachmentView(), tempH, b / w, 0.0F);
                gaussianPass(ce, tempH.getColorAttachmentView(), tempV, 0.0F, b / h);
                src = tempV.getColorAttachmentView();
            }
            float invScale = screenScale > 1.0E-4F ? 1.0F / screenScale : 1.0F;
            writeCompositeUniform(ce, invScale, 1.0F);
            RenderPass rp = ce.createRenderPass(() -> "decidevisuals:gui_capture_composite", fb.getColorAttachmentView(), OptionalInt.empty());
            try {
                rp.setPipeline(compositePipeline);
                rp.bindTexture("uGui", src, RenderSampler.linear());
                rp.setUniform("CompositeData", compositeUniform);
                rp.draw(0, 6);
            } catch (Throwable t) { try { rp.close(); } catch (Throwable t2) { t.addSuppressed(t2); } throw t; }
            rp.close();
        } catch (Throwable t) { disableAfterError(t); }
    }

    public static boolean captureActiveThisFrame() { return captureActive && guiFbo != null; }

    private static void closeTargets() {
        guiFbo = destroy(guiFbo); tempH = destroy(tempH); tempV = destroy(tempV);
        texWidth = -1; texHeight = -1;
    }

    private static void gaussianPass(CommandEncoder ce, GpuTextureView input, SimpleFramebuffer output, float dx, float dy) {
        MemoryStack stack = MemoryStack.stackPush();
        try {
            ByteBuffer buf = stack.calloc(16);
            buf.putFloat(0, dx); buf.putFloat(4, dy); buf.position(0);
            ce.writeToBuffer(blurUniform.slice(0L, 16L), buf);
        } catch (Throwable t) { stack.close(); throw t; }
        stack.close();
        RenderPass rp = ce.createRenderPass(() -> "decidevisuals:gui_capture_gaussian", output.getColorAttachmentView(), OptionalInt.of(0));
        try {
            rp.setPipeline(blurPipeline);
            rp.bindTexture("uInput", input, RenderSampler.linear());
            rp.setUniform("BlurData", blurUniform);
            rp.draw(0, 6);
        } catch (Throwable t) { try { rp.close(); } catch (Throwable t2) { t.addSuppressed(t2); } throw t; }
        rp.close();
    }

    private static boolean ensureTargets(int w, int h) {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null || w <= 0 || h <= 0) return false;
        if (guiFbo != null && tempH != null && tempV != null && w == texWidth && h == texHeight) return true;
        closeTargets();
        guiFbo = new SimpleFramebuffer("decidevisuals_gui_capture", w, h, true);
        tempH = new SimpleFramebuffer("decidevisuals_gui_capture_h", w, h, false);
        tempV = new SimpleFramebuffer("decidevisuals_gui_capture_v", w, h, false);
        texWidth = w; texHeight = h;
        return true;
    }

    private static void writeCompositeUniform(CommandEncoder ce, float scale, float alpha) {
        MemoryStack stack = MemoryStack.stackPush();
        try {
            ByteBuffer buf = stack.calloc(16);
            buf.putFloat(0, scale); buf.putFloat(4, alpha); buf.position(0);
            ce.writeToBuffer(compositeUniform.slice(0L, 16L), buf);
        } catch (Throwable t) { stack.close(); throw t; }
        stack.close();
    }

    private static boolean ensurePipelines() {
        if (blurPipeline != null && compositePipeline != null && shardPipeline != null
            && blurUniform != null && compositeUniform != null && shardVertexBuffer != null)
            return true;
        if (blurPipeline == null)
            blurPipeline = RenderPipelines.register(RenderPipeline.builder(new Snippet[0])
                .withLocation(BLUR_PIPELINE_ID).withVertexShader(FULLSCREEN).withFragmentShader(GAUSSIAN_SHADER)
                .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES).withSampler("uInput")
                .withUniform("BlurData", UniformType.UNIFORM_BUFFER).withoutBlend()
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build());
        if (compositePipeline == null)
            compositePipeline = RenderPipelines.register(RenderPipeline.builder(new Snippet[0])
                .withLocation(COMPOSITE_PIPELINE_ID).withVertexShader(FULLSCREEN).withFragmentShader(COMPOSITE_SHADER)
                .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES).withSampler("uGui")
                .withUniform("CompositeData", UniformType.UNIFORM_BUFFER)
                .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build());
        if (shardPipeline == null)
            shardPipeline = RenderPipelines.register(RenderPipeline.builder(new Snippet[0])
                .withLocation(SHARD_PIPELINE_ID).withVertexShader(SHARD_SHADER).withFragmentShader(SHARD_SHADER)
                .withVertexFormat(SHARD_FORMAT, VertexFormat.DrawMode.TRIANGLES).withSampler("uGui")
                .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build());
        if (shardVertexBuffer == null)
            shardVertexBuffer = RenderSystem.getDevice().createBuffer(() -> "decidevisuals:guilayerblur_shard_vertices", 40, 44352L);
        if (shardVertexData == null)
            shardVertexData = ByteBuffer.allocateDirect(44352).order(ByteOrder.nativeOrder());
        if (blurUniform == null)
            blurUniform = RenderSystem.getDevice().createBuffer(() -> "decidevisuals:guilayerblur_blur_uniform", 136, 16L);
        if (compositeUniform == null)
            compositeUniform = RenderSystem.getDevice().createBuffer(() -> "decidevisuals:guilayerblur_composite_uniform", 136, 16L);
        return blurPipeline != null && compositePipeline != null && shardPipeline != null
            && blurUniform != null && compositeUniform != null && shardVertexBuffer != null && shardVertexData != null;
    }

    private static void disableAfterError(Throwable t) {
        disabledAfterError = true; captureActive = false; closeTargets();
        LOGGER.error("[GuiLayerBlur] disabled after error", t);
    }
}

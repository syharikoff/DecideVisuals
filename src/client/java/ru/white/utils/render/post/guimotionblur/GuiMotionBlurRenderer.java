package ru.white.utils.render.post.guimotionblur;

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
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.white.utils.render.others.FullscreenQuadBuffer;
import ru.white.utils.render.others.RenderSampler;

public final class GuiMotionBlurRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("DecideVisuals:GuiMotionBlur");
    private static final int MAX_MASK_RECTS = 192;
    public static final int MASK_RECT_STRIDE = 6;
    private static final int MASK_VERTICES_PER_RECT = 6;
    private static final VertexFormat MASK_FORMAT = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("UV1", VertexFormatElement.UV1)
        .add("Color", VertexFormatElement.COLOR)
        .build();
    private static final int MASK_VERTEX_STRIDE_BYTES = MASK_FORMAT.getVertexSize();
    private static final int MASK_VERTEX_BYTES = 1152 * MASK_VERTEX_STRIDE_BYTES;
    private static final Identifier BLUR_PIPELINE_ID = id("pipeline/post/guimotionblur/gaussian");
    private static final Identifier MASK_PIPELINE_ID = id("pipeline/post/guimotionblur/mask");
    private static final Identifier MASK_REPLACE_PIPELINE_ID = id("pipeline/post/guimotionblur/mask_replace");
    private static final Identifier COMPOSITE_PIPELINE_ID = id("pipeline/post/guimotionblur/composite");
    private static final Identifier FULLSCREEN_SHADER = id("post/guimotionblur/fullscreen");
    private static final Identifier GAUSSIAN_SHADER = id("post/guimotionblur/gaussian");
    private static final Identifier MASK_SHADER = id("post/guimotionblur/mask");
    private static final Identifier COMPOSITE_SHADER = id("post/guimotionblur/composite");
    private static final RenderPipeline BLUR_PIPELINE = RenderPipeline.builder(new Snippet[0])
        .withLocation(BLUR_PIPELINE_ID)
        .withVertexShader(FULLSCREEN_SHADER)
        .withFragmentShader(GAUSSIAN_SHADER)
        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
        .withSampler("MotionInput")
        .withUniform("MotionBlurParams", UniformType.UNIFORM_BUFFER)
        .withoutBlend()
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withCull(false)
        .build();
    private static final RenderPipeline MASK_PIPELINE = RenderPipeline.builder(new Snippet[0])
        .withLocation(MASK_PIPELINE_ID)
        .withVertexShader(MASK_SHADER)
        .withFragmentShader(MASK_SHADER)
        .withVertexFormat(MASK_FORMAT, VertexFormat.DrawMode.TRIANGLES)
        .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE))
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withCull(false)
        .build();
    private static final RenderPipeline MASK_REPLACE_PIPELINE = RenderPipeline.builder(new Snippet[0])
        .withLocation(MASK_REPLACE_PIPELINE_ID)
        .withVertexShader(MASK_SHADER)
        .withFragmentShader(MASK_SHADER)
        .withVertexFormat(MASK_FORMAT, VertexFormat.DrawMode.TRIANGLES)
        .withoutBlend()
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withCull(false)
        .build();
    private static final RenderPipeline COMPOSITE_PIPELINE = RenderPipeline.builder(new Snippet[0])
        .withLocation(COMPOSITE_PIPELINE_ID)
        .withVertexShader(FULLSCREEN_SHADER)
        .withFragmentShader(COMPOSITE_SHADER)
        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
        .withSampler("MotionScene")
        .withSampler("MotionBackground")
        .withSampler("MotionBlurred")
        .withSampler("MotionBackgroundBlurred")
        .withSampler("MotionMask")
        .withUniform("MotionCompositeParams", UniformType.UNIFORM_BUFFER)
        .withoutBlend()
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withCull(false)
        .build();
    private static GpuBuffer fullscreenVertexBuffer;
    private static GpuBuffer maskVertexBuffer;
    private static GpuBuffer blurUniformBuffer;
    private static GpuBuffer compositeUniformBuffer;
    private static GpuTexture sceneCopyTexture;
    private static GpuTextureView sceneCopyTextureView;
    private static GpuTexture backgroundCopyTexture;
    private static GpuTextureView backgroundCopyTextureView;
    private static GpuTexture remoteBackgroundCopyTexture;
    private static GpuTextureView remoteBackgroundCopyTextureView;
    private static SimpleFramebuffer maskTarget;
    private static SimpleFramebuffer horizontalTarget;
    private static SimpleFramebuffer verticalTarget;
    private static SimpleFramebuffer backgroundHorizontalTarget;
    private static SimpleFramebuffer backgroundVerticalTarget;
    private static int sourceWidth = -1;
    private static int sourceHeight = -1;
    private static boolean backgroundCaptured;
    private static Framebuffer backgroundCaptureTarget;
    private static boolean remoteBackgroundCaptured;
    private static Framebuffer remoteBackgroundCaptureTarget;
    private static boolean disabledAfterError;
    private static boolean pipelinesRegistered;

    private GuiMotionBlurRenderer() {}

    public static void shutdown() {
        releaseResources();
        disabledAfterError = false;
    }

    private static float clamp(float f, float min, float max) {
        return f < min ? min : Math.max(min, Math.min(max, f));
    }

    private static Identifier id(String s) {
        return Identifier.of("wvisual", s);
    }

    private static void closeBuffer(GpuBuffer buf) {
        if (buf != null) buf.close();
    }

    private static void composite(
        CommandEncoder ce, GpuTextureView fbView, GpuTextureView scene, GpuTextureView blurred,
        GpuTextureView bgView, GpuTextureView bgBlurred,
        float alpha, float blurVal, int sx, int sy, int sw, int sh,
        int fbW, int fbH, float[] rects, int rx, int ry, int rw, int rh,
        float scale, float origX, float origY
    ) {
        if (scene == null || blurred == null || bgView == null || bgBlurred == null) return;
        int adjustedY = Math.max(0, fbH - (sy + sh));
        writeCompositeUniform(ce, alpha, blurVal, fbW, fbH, rects, rx, ry, rw, rh, scale, origX, origY);
        RenderPass rp = ce.createRenderPass(() -> "decidevisuals:gui_motion_blur_composite", scene, OptionalInt.empty(), null, OptionalDouble.empty());
        try {
            rp.setPipeline(COMPOSITE_PIPELINE);
            rp.setVertexBuffer(0, fullscreenVertexBuffer);
            rp.enableScissor(sx, adjustedY, sw, sh);
            rp.bindTexture("MotionScene", scene, RenderSampler.linear());
            rp.bindTexture("MotionBackground", bgBlurred, RenderSampler.linear());
            rp.bindTexture("MotionBlurred", blurred, RenderSampler.linear());
            rp.bindTexture("MotionBackgroundBlurred", bgView, RenderSampler.linear());
            rp.bindTexture("MotionMask", fbView, RenderSampler.linear());
            rp.setUniform("MotionCompositeParams", compositeUniformBuffer.slice());
            rp.draw(0, 6);
        } catch (Throwable t) {
            try { rp.close(); } catch (Throwable t2) { t.addSuppressed(t2); }
            throw t;
        }
        rp.close();
    }

    private static boolean ensureReady(int w, int h, float f) {
        try {
            GpuDevice device = RenderSystem.tryGetDevice();
            if (device == null) return false;
            if (!pipelinesRegistered) {
                RenderPipelines.register(BLUR_PIPELINE);
                RenderPipelines.register(MASK_PIPELINE);
                RenderPipelines.register(MASK_REPLACE_PIPELINE);
                RenderPipelines.register(COMPOSITE_PIPELINE);
                pipelinesRegistered = true;
            }
            if (fullscreenVertexBuffer == null || fullscreenVertexBuffer.isClosed())
                fullscreenVertexBuffer = FullscreenQuadBuffer.getOrCreate();
            if (maskVertexBuffer == null || maskVertexBuffer.isClosed() || maskVertexBuffer.size() < MASK_VERTEX_BYTES) {
                closeBuffer(maskVertexBuffer);
                maskVertexBuffer = device.createBuffer(() -> "decidevisuals:gui_motion_blur_mask_vertices", 40, MASK_VERTEX_BYTES);
            }
            if (blurUniformBuffer == null || blurUniformBuffer.isClosed() || blurUniformBuffer.size() < 16L) {
                closeBuffer(blurUniformBuffer);
                blurUniformBuffer = device.createBuffer(() -> "decidevisuals:gui_motion_blur_uniform", 136, 16L);
            }
            if (compositeUniformBuffer == null || compositeUniformBuffer.isClosed() || compositeUniformBuffer.size() < 80L) {
                closeBuffer(compositeUniformBuffer);
                compositeUniformBuffer = device.createBuffer(() -> "decidevisuals:gui_motion_blur_composite_uniform", 136, 80L);
            }
            ensureTargets(device, w, h, f);
            return fullscreenVertexBuffer != null && maskVertexBuffer != null && blurUniformBuffer != null
                && compositeUniformBuffer != null && sceneCopyTextureView != null
                && backgroundCopyTextureView != null && remoteBackgroundCopyTextureView != null
                && maskTarget != null && horizontalTarget != null && verticalTarget != null
                && backgroundHorizontalTarget != null && backgroundVerticalTarget != null;
        } catch (Throwable t) {
            disableAfterError(t);
            return false;
        }
    }

    public static void captureBackground(Framebuffer fb, float f) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isMain = mc != null && fb == mc.getFramebuffer();
        if (disabledAfterError || !Float.isFinite(f)) { clearBackgroundCapture(isMain); return; }
        if (fb == null || fb.textureWidth <= 0 || fb.textureHeight <= 0 || fb.getColorAttachment() == null) { clearBackgroundCapture(isMain); return; }
        if (!ensureReady(fb.textureWidth, fb.textureHeight, 1.0F)) { clearBackgroundCapture(isMain); return; }
        try {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                fb.getColorAttachment(), isMain ? backgroundCopyTexture : remoteBackgroundCopyTexture,
                0, 0, 0, 0, 0, fb.textureWidth, fb.textureHeight
            );
            if (isMain) { backgroundCaptured = true; backgroundCaptureTarget = fb; }
            else { remoteBackgroundCaptured = true; remoteBackgroundCaptureTarget = fb; }
        } catch (Throwable t) { clearBackgroundCapture(isMain); disableAfterError(t); }
    }

    public static void captureBackground(float f) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.gameRenderer != null && mc.getFramebuffer() != null)
            captureBackground(mc.getFramebuffer(), f);
        else { backgroundCaptured = false; backgroundCaptureTarget = null; }
    }

    public static void applyWithCopy(
        Framebuffer fb, float alpha, float blurVal, int sx, int sy, int sw, int sh,
        float[] rects, int mask, int rx, int ry, int rw, int rh,
        float renderScale, float origX, float origY, boolean replace
    ) {
        if (disabledAfterError) return;
        float a = clamp(alpha, 0.0F, 1.0F);
        float rs = Float.isFinite(renderScale) ? clamp(renderScale, 0.25F, 4.0F) : 1.0F;
        boolean hasScale = Math.abs(rs - 1.0F) > 5.0E-4F;
        int rectCount = safeMaskRectCount(rects, mask);
        if ((hasScale || !(a <= 0.002F)) && Float.isFinite(blurVal) && rectCount > 0) {
            if (fb != null && fb.textureWidth > 0 && fb.textureHeight > 0 && fb.getColorAttachment() != null && fb.getColorAttachmentView() != null) {
                int csx = clampInt(sx, 0, fb.textureWidth);
                int csy = clampInt(sy, 0, fb.textureHeight);
                int csx2 = clampInt(sx + sw, csx, fb.textureWidth);
                int csy2 = clampInt(sy + sh, csy, fb.textureHeight);
                int csw = csx2 - csx;
                int csh = csy2 - csy;
                if (csw > 0 && csh > 0) {
                    int crx = hasScale ? clampInt(rx, 0, fb.textureWidth) : 0;
                    int cry = hasScale ? clampInt(ry, 0, fb.textureHeight) : 0;
                    int crx2 = hasScale ? clampInt(rx + rw, crx, fb.textureWidth) : 0;
                    int cry2 = hasScale ? clampInt(ry + rh, cry, fb.textureHeight) : 0;
                    int crw = crx2 - crx;
                    int crh = cry2 - cry;
                    if (!hasScale || (crw > 0 && crh > 0)) {
                        float b = clamp(blurVal, 0.0F, 48.0F);
                        if (ensureReady(fb.textureWidth, fb.textureHeight, 1.0F)) {
                            boolean isBg = backgroundCaptured && fb == backgroundCaptureTarget;
                            boolean isRemote = remoteBackgroundCaptured && fb == remoteBackgroundCaptureTarget;
                            if (isBg || isRemote) {
                                GpuTextureView bgView = isBg ? backgroundCopyTextureView : remoteBackgroundCopyTextureView;
                                clearBackgroundCapture(isBg);
                                try {
                                    CommandEncoder ce = RenderSystem.getDevice().createCommandEncoder();
                                    ce.copyTextureToTexture(fb.getColorAttachment(), sceneCopyTexture, 0, 0, 0, 0, 0, fb.textureWidth, fb.textureHeight);
                                    gaussianChain(ce, sceneCopyTextureView, fb.textureWidth, fb.textureHeight, horizontalTarget, verticalTarget, b, csx, csy, csw, csh);
                                    gaussianChain(ce, bgView, fb.textureWidth, fb.textureHeight, backgroundHorizontalTarget, backgroundVerticalTarget, b, csx, csy, csw, csh);
                                    renderMask(ce, rects, rectCount, fb.textureWidth, fb.textureHeight, replace);
                                    composite(ce, fb.getColorAttachmentView(), sceneCopyTextureView, verticalTarget.getColorAttachmentView(),
                                        bgView, backgroundVerticalTarget.getColorAttachmentView(),
                                        a, b, csx, csy, csw, csh, fb.textureWidth, fb.textureHeight, rects, crx, cry, crw, crh, rs, origX, origY);
                                } catch (Throwable t) { disableAfterError(t); }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void applyWithCopy(float alpha, float blurVal, int sx, int sy, int sw, int sh, float[] rects, int mask, int rx, int ry, int rw, int rh, float renderScale, float origX, float origY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.gameRenderer != null && mc.getFramebuffer() != null)
            applyWithCopy(mc.getFramebuffer(), alpha, blurVal, sx, sy, sw, sh, rects, mask, rx, ry, rw, rh, renderScale, origX, origY, false);
    }

    public static void applyWithCopy(Framebuffer fb, float alpha, float blurVal, float bx, float by, float bw, float bh, int mask, int cardCount, float ox, float oy, float ow, float oh, float renderScale, float origX, float origY) {
        applyWithCopy(fb, alpha, blurVal, (int)bx, (int)by, (int)bw, (int)bh, null, mask, (int)ox, (int)oy, (int)ow, (int)oh, renderScale, origX, origY, false);
    }

    private static void gaussianChain(CommandEncoder ce, GpuTextureView input, int w, int h, SimpleFramebuffer hTarget, SimpleFramebuffer vTarget, float blurVal, int sx, int sy, int sw, int sh) {
        float scaledBlur = blurVal / 16.0F;
        int pad = (int)Math.ceil(blurVal) + 8;
        int cx = clampInt(sx - pad, 0, w);
        int cy = clampInt(sy - pad, 0, h);
        int cx2 = clampInt(sx + sw + pad, cx, w);
        int cy2 = clampInt(sy + sh + pad, cy, h);
        if (cx2 - cx > 0 && cy2 - cy > 0) {
            gaussianPass(ce, input, hTarget, scaledBlur / Math.max(1, w), 0.0F, cx, cy, cx2 - cx, cy2 - cy);
            gaussianPass(ce, hTarget.getColorAttachmentView(), vTarget, 0.0F, scaledBlur / Math.max(1, h), cx, cy, cx2 - cx, cy2 - cy);
        }
    }

    private static void clearBackgroundCapture(boolean isMain) {
        if (isMain) { backgroundCaptured = false; backgroundCaptureTarget = null; }
        else { remoteBackgroundCaptured = false; remoteBackgroundCaptureTarget = null; }
    }

    private static void releaseResources() {
        closeTargets();
        closeBuffer(maskVertexBuffer); closeBuffer(blurUniformBuffer); closeBuffer(compositeUniformBuffer);
        maskVertexBuffer = null; blurUniformBuffer = null; compositeUniformBuffer = null; fullscreenVertexBuffer = null;
    }

    private static int safeMaskRectCount(float[] rects, int n) {
        return rects != null && n > 0 ? Math.min(Math.min(n, 192), rects.length / 6) : 0;
    }

    private static int writeMaskVertices(ByteBuffer buf, float[] rects, int count, int w, int h) {
        int written = 0;
        int n = safeMaskRectCount(rects, count);
        float fw = Math.max(1.0F, (float)w);
        float fh = Math.max(1.0F, (float)h);
        for (int i = 0; i < n; i++) {
            int off = i * 6;
            if (rects[off + 4] < 0.0F) continue;
            float rw = rects[off + 2];
            float rh = rects[off + 3];
            if (rw <= 0.5F || rh <= 0.5F) continue;
            float rx = rects[off] - 8.0F;
            float ry = rects[off + 1] - 8.0F;
            float rx2 = rects[off] + rw + 8.0F;
            float ry2 = rects[off + 1] + rh + 8.0F;
            float nx1 = rx / fw * 2.0F - 1.0F;
            float nx2 = rx2 / fw * 2.0F - 1.0F;
            float ny1 = 1.0F - ry / fh * 2.0F;
            float ny2 = 1.0F - ry2 / fh * 2.0F;
            float cr = Math.min(8.0F, Math.min(rw, rh) * 0.5F);
            int cw = Math.min(32767, Math.round(rw * 8.0F));
            int ch = Math.min(32767, Math.round(rh * 8.0F));
            putVertex(buf, nx1, ny2, cr, -8.0F, rh + 8.0F, cw, ch, Math.round(clamp(rects[off + 4], 0, 1) * 255), Math.round(clamp(rects[off + 5], 0, 1) * 255));
            putVertex(buf, nx2, ny2, cr, rw + 8.0F, rh + 8.0F, cw, ch, Math.round(clamp(rects[off + 4], 0, 1) * 255), Math.round(clamp(rects[off + 5], 0, 1) * 255));
            putVertex(buf, nx2, ny1, cr, rw + 8.0F, -8.0F, cw, ch, Math.round(clamp(rects[off + 4], 0, 1) * 255), Math.round(clamp(rects[off + 5], 0, 1) * 255));
            putVertex(buf, nx1, ny2, cr, -8.0F, rh + 8.0F, cw, ch, Math.round(clamp(rects[off + 4], 0, 1) * 255), Math.round(clamp(rects[off + 5], 0, 1) * 255));
            putVertex(buf, nx2, ny1, cr, rw + 8.0F, -8.0F, cw, ch, Math.round(clamp(rects[off + 4], 0, 1) * 255), Math.round(clamp(rects[off + 5], 0, 1) * 255));
            putVertex(buf, nx1, ny1, cr, -8.0F, -8.0F, cw, ch, Math.round(clamp(rects[off + 4], 0, 1) * 255), Math.round(clamp(rects[off + 5], 0, 1) * 255));
            written += 6;
        }
        return written;
    }

    private static void closeTargets() {
        if (sceneCopyTextureView != null) { sceneCopyTextureView.close(); sceneCopyTextureView = null; }
        if (sceneCopyTexture != null) { sceneCopyTexture.close(); sceneCopyTexture = null; }
        if (backgroundCopyTextureView != null) { backgroundCopyTextureView.close(); backgroundCopyTextureView = null; }
        if (backgroundCopyTexture != null) { backgroundCopyTexture.close(); backgroundCopyTexture = null; }
        if (remoteBackgroundCopyTextureView != null) { remoteBackgroundCopyTextureView.close(); remoteBackgroundCopyTextureView = null; }
        if (remoteBackgroundCopyTexture != null) { remoteBackgroundCopyTexture.close(); remoteBackgroundCopyTexture = null; }
        if (maskTarget != null) { maskTarget.delete(); maskTarget = null; }
        if (horizontalTarget != null) { horizontalTarget.delete(); horizontalTarget = null; }
        if (verticalTarget != null) { verticalTarget.delete(); verticalTarget = null; }
        if (backgroundHorizontalTarget != null) { backgroundHorizontalTarget.delete(); backgroundHorizontalTarget = null; }
        if (backgroundVerticalTarget != null) { backgroundVerticalTarget.delete(); backgroundVerticalTarget = null; }
        sourceWidth = -1; sourceHeight = -1;
        backgroundCaptured = false; backgroundCaptureTarget = null;
        remoteBackgroundCaptured = false; remoteBackgroundCaptureTarget = null;
    }

    private static void gaussianPass(CommandEncoder ce, GpuTextureView input, SimpleFramebuffer output, float dx, float dy, int sx, int sy, int sw, int sh) {
        if (input == null || output == null || output.getColorAttachmentView() == null) return;
        MemoryStack stack = MemoryStack.stackPush();
        try {
            ByteBuffer buf = stack.calloc(16);
            buf.putFloat(0, dx); buf.putFloat(4, dy); buf.position(0);
            ce.writeToBuffer(blurUniformBuffer.slice(0L, 16L), buf);
        } catch (Throwable t) { stack.close(); throw t; }
        stack.close();
        RenderPass rp = ce.createRenderPass(() -> "decidevisuals:gui_motion_blur_gaussian", output.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty());
        try {
            rp.setPipeline(BLUR_PIPELINE);
            rp.setVertexBuffer(0, fullscreenVertexBuffer);
            int adjY = Math.max(0, output.textureHeight - (sy + sh));
            rp.enableScissor(sx, adjY, sw, sh);
            rp.bindTexture("MotionInput", input, RenderSampler.linear());
            rp.setUniform("MotionBlurParams", blurUniformBuffer.slice(0L, 16L));
            rp.draw(0, 6);
        } catch (Throwable t) { try { rp.close(); } catch (Throwable t2) { t.addSuppressed(t2); } throw t; }
        rp.close();
    }

    private static void ensureTargets(GpuDevice device, int w, int h, float f) {
        if (sceneCopyTextureView != null && backgroundCopyTextureView != null && remoteBackgroundCopyTextureView != null
            && sourceWidth == w && sourceHeight == h
            && maskTarget != null && maskTarget.textureWidth == w && maskTarget.textureHeight == h) return;
        closeTargets();
        sceneCopyTexture = device.createTexture(() -> "decidevisuals:gui_motion_blur_scene_copy", 5, TextureFormat.RGBA8, w, h, 1, 1);
        sceneCopyTextureView = device.createTextureView(sceneCopyTexture);
        backgroundCopyTexture = device.createTexture(() -> "decidevisuals:gui_motion_blur_background_copy", 5, TextureFormat.RGBA8, w, h, 1, 1);
        backgroundCopyTextureView = device.createTextureView(backgroundCopyTexture);
        remoteBackgroundCopyTexture = device.createTexture(() -> "decidevisuals:gui_motion_blur_remote_bg_copy", 5, TextureFormat.RGBA8, w, h, 1, 1);
        remoteBackgroundCopyTextureView = device.createTextureView(remoteBackgroundCopyTexture);
        maskTarget = new SimpleFramebuffer("decidevisuals_gui_motion_blur_mask", w, h, false);
        horizontalTarget = new SimpleFramebuffer("decidevisuals_gui_motion_blur_scene_h", w, h, false);
        verticalTarget = new SimpleFramebuffer("decidevisuals_gui_motion_blur_scene_result", w, h, false);
        backgroundHorizontalTarget = new SimpleFramebuffer("decidevisuals_gui_motion_blur_bg_h", w, h, false);
        backgroundVerticalTarget = new SimpleFramebuffer("decidevisuals_gui_motion_blur_bg_result", w, h, false);
        sourceWidth = w; sourceHeight = h;
    }

    private static void writeCompositeUniform(CommandEncoder ce, float alpha, float blurVal, int fbW, int fbH, float[] rects, int rx, int ry, int rw, int rh, float scale, float origX, float origY) {
        MemoryStack stack = MemoryStack.stackPush();
        try {
            ByteBuffer buf = stack.calloc(80);
            float rrx = 0.0F, rry = 0.0F, rrw = fbW, rrh = fbH;
            if (rects != null && rects.length >= 4) {
                rrx = rects[0]; rry = rects[1];
                rrw = Math.max(0.0F, rects[2]); rrh = Math.max(0.0F, rects[3]);
            }
            buf.putFloat(0, alpha); buf.putFloat(4, 0.035F); buf.putFloat(8, 1.0F); buf.putFloat(12, 10.0F);
            buf.putFloat(16, rrx); buf.putFloat(20, rry); buf.putFloat(24, rrw); buf.putFloat(28, rrh);
            buf.putFloat(32, fbW); buf.putFloat(36, fbH); buf.putFloat(40, 18.0F); buf.putFloat(44, scale);
            buf.putFloat(48, rx); buf.putFloat(52, ry); buf.putFloat(56, Math.max(0, rw)); buf.putFloat(60, Math.max(0, rh));
            buf.putFloat(64, origX); buf.putFloat(68, origY); buf.putFloat(72, Math.max(0.0F, blurVal)); buf.putFloat(76, 0.0F);
            buf.position(0);
            ce.writeToBuffer(compositeUniformBuffer.slice(0L, 80L), buf);
        } catch (Throwable t) { stack.close(); throw t; }
        stack.close();
    }

    private static void disableAfterError(Throwable t) {
        LOGGER.error("[GuiMotionBlur] disabled after render error", t);
        backgroundCaptured = false; releaseResources(); disabledAfterError = true;
    }

    private static int clampInt(int v, int min, int max) { return v < min ? min : Math.max(min, Math.min(max, v)); }

    private static void renderMask(CommandEncoder ce, float[] rects, int count, int w, int h, boolean replace) {
        if (rects == null || maskTarget == null || maskTarget.getColorAttachmentView() == null || maskVertexBuffer == null) return;
        MemoryStack stack = MemoryStack.stackPush();
        int written;
        try {
            ByteBuffer buf = stack.malloc(count * 6 * MASK_VERTEX_STRIDE_BYTES);
            written = writeMaskVertices(buf, rects, count, w, h);
            if (written > 0) { buf.flip(); ce.writeToBuffer(maskVertexBuffer.slice(0L, buf.remaining()), buf); }
        } catch (Throwable t) { stack.close(); throw t; }
        stack.close();
        RenderPass rp = ce.createRenderPass(() -> "decidevisuals:gui_motion_blur_mask", maskTarget.getColorAttachmentView(), OptionalInt.of(0), null, OptionalDouble.empty());
        try {
            if (written > 0) {
                rp.setPipeline(replace ? MASK_REPLACE_PIPELINE : MASK_PIPELINE);
                rp.setVertexBuffer(0, maskVertexBuffer);
                rp.draw(0, written);
            }
        } catch (Throwable t) { try { rp.close(); } catch (Throwable t2) { t.addSuppressed(t2); } throw t; }
        rp.close();
    }

    private static void putVertex(ByteBuffer buf, float x, float y, float z, float u, float v, int cu, int cv, int cr, int ca) {
        buf.putFloat(x); buf.putFloat(y); buf.putFloat(z); buf.putFloat(u); buf.putFloat(v);
        buf.putShort((short)cu); buf.putShort((short)cv);
        buf.put((byte)cr); buf.put((byte)ca); buf.put((byte)0); buf.put((byte)-1);
    }
}

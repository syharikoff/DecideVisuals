package ru.white.optimization.velotune.hud;

import ru.white.optimization.velotune.perf.TextRefreshThrottle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

@Environment(value=EnvType.CLIENT)
public final class ScreenFramebufferCache {
    private static Framebuffer framebuffer;
    private static Object owner;
    private static boolean active;
    private static boolean valid;
    private static boolean captureFrame;
    private static boolean renderingCapture;
    private static long lastCaptureNanos;
    private static volatile boolean invalidated;

    private ScreenFramebufferCache() {
    }

    public static void beginFrame(Object candidate, int width, int height, long now, int rate) {
        active = true;
        if (owner != candidate) {
            owner = candidate;
            valid = false;
            invalidated = true;
        }
        ScreenFramebufferCache.ensureFramebuffer(width, height);
        captureFrame = invalidated || TextRefreshThrottle.shouldRefresh(valid, now - lastCaptureNanos, rate);
    }

    public static void disable() {
        active = false;
        captureFrame = false;
        renderingCapture = false;
        HudFramebufferRouter.endCapture();
    }

    public static boolean active() {
        return active && framebuffer != null;
    }

    public static boolean shouldCaptureFrame() {
        return ScreenFramebufferCache.active() && captureFrame;
    }

    public static void beginRenderCapture() {
        if (!ScreenFramebufferCache.shouldCaptureFrame()) {
            return;
        }
        renderingCapture = true;
        HudFramebufferRouter.beginScreenCapture(framebuffer);
    }

    public static boolean isRenderingCapture() {
        return renderingCapture;
    }

    public static void finishRenderCapture(long now) {
        if (!renderingCapture) {
            return;
        }
        HudFramebufferRouter.endCapture();
        renderingCapture = false;
        captureFrame = false;
        invalidated = false;
        valid = true;
        lastCaptureNanos = now;
    }

    public static void composite(Framebuffer target) {
        if (valid && framebuffer != null && target != framebuffer) {
            framebuffer.drawBlit(target.getColorAttachmentView());
        }
    }

    public static void invalidate() {
        invalidated = true;
    }

    private static void ensureFramebuffer(int width, int height) {
        if (framebuffer != null && framebuffer.textureWidth == width && framebuffer.textureHeight == height) {
            return;
        }
        if (framebuffer != null) {
            framebuffer.delete();
        }
        framebuffer = new SimpleFramebuffer("VeloTune GUI Cache", width, height, true);
        valid = false;
        invalidated = true;
    }

    static {
        invalidated = true;
    }
}

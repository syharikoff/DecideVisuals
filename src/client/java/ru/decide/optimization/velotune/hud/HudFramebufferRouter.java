package ru.decide.optimization.velotune.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;

@Environment(value=EnvType.CLIENT)
public final class HudFramebufferRouter {
    private static Framebuffer captureTarget;
    private static boolean screenCapture;

    private HudFramebufferRouter() {
    }

    public static void beginCapture(Framebuffer target) {
        captureTarget = target;
        screenCapture = false;
    }

    public static void beginScreenCapture(Framebuffer target) {
        captureTarget = target;
        screenCapture = true;
    }

    public static void endCapture() {
        captureTarget = null;
        screenCapture = false;
    }

    public static Framebuffer captureTarget() {
        return captureTarget;
    }

    public static boolean isScreenCapture() {
        return captureTarget != null && screenCapture;
    }
}

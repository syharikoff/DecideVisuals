package ru.decide.optimization;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class FpsIndicator {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static long lastUpdateTime = 0L;
    private static int frameCount = 0;
    private static double currentFps = 0.0;

    private FpsIndicator() {
    }

    public static void updateFps() {
        ++frameCount;
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= 1000L) {
            currentFps = (double) frameCount * 1000.0 / (double) (currentTime - lastUpdateTime);
            frameCount = 0;
            lastUpdateTime = currentTime;
        }
    }

    public static void render(DrawContext context) {
        ModConfig config = ModConfig.getInstance();
        if (config.showFpsIndicator && !client.options.hudHidden && RenderManager.getInstance().isInitialized()) {
            int effectiveLimit = config.getEffectiveHz();
            boolean unlimited = !config.enableRenderLimit || config.isUnlimitedFps();
            String fpsText = unlimited
                    ? String.format("FPS: %.1f (Unlimited)", currentFps)
                    : String.format("FPS: %.1f / %d Hz", currentFps, effectiveLimit);
            int color = unlimited ? 0xFFFFFF : getFpsColor(currentFps, effectiveLimit);
            int x = context.getScaledWindowWidth() - client.textRenderer.getWidth(fpsText) - 10;
            int y = 10;
            context.fill(x - 2, y - 2, x + client.textRenderer.getWidth(fpsText) + 2, y + 9 + 2, Integer.MIN_VALUE);
            context.drawTextWithShadow(client.textRenderer, Text.literal(fpsText), x, y, color);
        }
    }

    private static int getFpsColor(double currentFps, int targetFps) {
        double ratio = currentFps / targetFps;
        if (ratio >= 0.9) {
            return 0x00FF00;
        }
        return ratio >= 0.7 ? 0xFFFF00 : 0xFF0000;
    }
}
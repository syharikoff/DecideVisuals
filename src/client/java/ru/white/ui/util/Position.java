package ru.white.ui.util;

import net.minecraft.client.MinecraftClient;
import ru.white.ui.util.Render2DCoordinateSpace;

public final class Position {
    public static final float SCREEN_MARGIN = 5.0F;
    private float x;
    private float y;

    public Position(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float x() {
        return this.x;
    }

    public void set(float x, float y, float width, float height) {
        this.x = clampX(x, width);
        this.y = clampY(y, height);
    }

    public float y() {
        return this.y;
    }

    public static float clampX(float x, float width) {
        float max = Math.max(5.0F, screenWidth() - width - 5.0F);
        return Math.max(5.0F, Math.min(max, x));
    }

    public static float clampY(float y, float height) {
        float max = Math.max(5.0F, screenHeight() - height - 5.0F);
        return Math.max(5.0F, Math.min(max, y));
    }

    public void setRaw(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public static float mouseX() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (minecraftClient == null) {
            return 0.0F;
        } else {
            double d = minecraftClient.mouse.getScaledX(minecraftClient.getWindow());
            return (float) (d / Render2DCoordinateSpace.guiIndependentScale());
        }
    }

    public static float mouseY() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (minecraftClient == null) {
            return 0.0F;
        } else {
            double d = minecraftClient.mouse.getScaledY(minecraftClient.getWindow());
            return (float) (d / Render2DCoordinateSpace.guiIndependentScale());
        }
    }

    public static float screenWidth() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        return minecraftClient != null && minecraftClient.getWindow() != null
                ? minecraftClient.getWindow().getWidth() / Render2DCoordinateSpace.designGuiScale()
                : 960.0F;
    }

    public static float screenHeight() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        return minecraftClient != null && minecraftClient.getWindow() != null
                ? minecraftClient.getWindow().getHeight() / Render2DCoordinateSpace.designGuiScale()
                : 540.0F;
    }
}
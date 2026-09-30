package ru.white.ui.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

public final class Render2DCoordinateSpace {
    private static final float DESIGN_GUI_SCALE = 2.0F;

    private Render2DCoordinateSpace() {
    }

    public static int guiScale() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (minecraftClient == null) {
            return 1;
        } else {
            Window window = minecraftClient.getWindow();
            return window == null ? 1 : Math.max(1, window.getScaleFactor());
        }
    }

    public static float guiIndependentScale() {
        return 2.0F / guiScale();
    }

    public static float designGuiScale() {
        return 2.0F;
    }

    public static float toGui(float f) {
        return f * guiIndependentScale();
    }

    public static int toGuiInt(float f) {
        return Math.round(toGui(f));
    }
}
package ru.white.utils.math;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class ScrollUtil {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private float target, scroll, max;
    private float speed = 8F;
    private boolean enabled;

    private double wheel; // теперь сами храним wheel

    public ScrollUtil() {
        setEnabled(true);


        GLFW.glfwSetScrollCallback(mc.getWindow().getHandle(), (window, xOffset, yOffset) -> {
            wheel += yOffset;
        });
    }

    public void update() {
        float wheelDelta = (float) wheel * (speed * 10F);
        float stretch = 0;

        scroll = lerp(scroll, target, speed / 100F);

        if (!enabled) {
            wheel = 0;
            return;
        }

        target = Math.min(
                Math.max(target + (wheelDelta / 2F), max - (wheelDelta == 0 ? 0 : stretch)),
                (wheelDelta == 0 ? 0 : stretch)
        );

        wheel = 0;
    }

    float barHeight;

    public void render(float x, float y, float width, float height, float alpha) {
        if (max == 0) return;

        float percentage = (getScroll() / getMax());

        barHeight = lerp(
                height - ((getMax() / (getMax() - height)) * height),
                barHeight,
                0.9f
        );

        boolean allowed = (barHeight < height);
        if (!allowed) return;

        float scrollX = x;
        float scrollY = y + (height * percentage) - (barHeight * percentage);


    }

    public void reset() {
        this.scroll = 0F;
        this.target = 0F;
    }

    public void setMax(float max, float height) {
        this.max = -max + height;
    }

    // utils
    public float lerp(float a, float b, double f) {
        return (float) (a + f * (b - a));
    }

    public float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    // getters/setters
    public float getScroll() {
        return scroll;
    }

    public float getMax() {
        return max;
    }

    public void setMax(float max) {
        this.max = max;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
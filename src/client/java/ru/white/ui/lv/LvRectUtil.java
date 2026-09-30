package ru.white.ui.lv;

import java.awt.Color;

import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.ThemeManager;
import ru.white.utils.render.Draw;

public final class LvRectUtil {

    private static final float DEFAULT_CORNER_RADIUS = 7.0F;
    private static final float COLOR_MOVE_SPEED = 1.0F / 2400.0F;

    private static float colorOffset = 0.0F;
    private static long lastColorOffsetMs = 0L;
    private static boolean colorMovementActive = true;

    private LvRectUtil() {
    }

    public static float getColorOffset() {
        long now = System.currentTimeMillis();
        if (lastColorOffsetMs == 0L) {
            lastColorOffsetMs = now;
            return colorOffset;
        }
        long dt = Math.min(Math.max(0L, now - lastColorOffsetMs), 100L);
        lastColorOffsetMs = now;
        if (colorMovementActive) {
            colorOffset = normalizeOffset(colorOffset + (float) dt * COLOR_MOVE_SPEED);
        }
        return colorOffset;
    }

    private static float normalizeOffset(float f) {
        return !Float.isFinite(f) ? 0.0F : f - (float) Math.floor(f);
    }

    public static void rect(float x, float y, float w, float h, int color) {
        Draw.rect(x, y, w, h, color);
    }

    public static void rect(float x, float y, float w, float h, float radius, int color) {
        Draw.rect(x, y, w, h, color, radius);
    }

    public static void roundedRect(float x, float y, float w, float h, float r, int color) {
        Draw.rect(x, y, w, h, color, r);
    }

    public static void glow(float x, float y, float w, float h, int color, float radius, float glowSize, float strength) {
        Draw.glow(x, y, w, h, color, radius, glowSize, strength);
    }

    public static void outline(float x, float y, float w, float h, float thickness, int color) {
        Draw.outline(x, y, w, h, thickness, color);
    }

    public static void outline(float x, float y, float w, float h, float thickness, int color, float radius) {
        Draw.outline(x, y, w, h, thickness, color, radius);
    }

    public static void gradientRect(float x, float y, float w, float h, int[] colors, float radius) {
        Draw.gradientRect(x, y, w, h, colors, radius);
    }

    public static void gradientRect(float x, float y, float w, float h,
                                    int[] colors, float tl, float tr, float br, float bl) {
        Draw.gradientRect(x, y, w, h, colors, tl, tr, br, bl);
    }

    public static void gradientRectFromTheme(float x, float y, float w, float h, float radius, float alpha) {
        int cA = ThemeManager.gradientA(alpha);
        int cB = ThemeManager.gradientB(alpha);
        Draw.gradientRect(x, y, w, h, new int[]{cA, cB, cB, cA}, radius);
    }

    public static void drawClientRect(float x, float y, float w, float h) {
        drawClientRect(x, y, w, h, DEFAULT_CORNER_RADIUS, 1.0F);
    }

    public static void drawClientRect(float x, float y, float w, float h, float alpha) {
        drawClientRect(x, y, w, h, DEFAULT_CORNER_RADIUS, alpha);
    }

    public static void drawClientRect(float x, float y, float w, float h, float radius, float alpha) {
        drawClientRect(x, y, w, h, radius, alpha, 0.0F);
    }

    public static void drawClientRect(float x, float y, float w, float h, float radius, float alpha, float extraRadius) {
        float clampedAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
        if (clampedAlpha <= 0.0F || w <= 0.0F || h <= 0.0F) {
            return;
        }
        float r = Math.max(0.0F, Math.min(radius + extraRadius, Math.min(w, h) * 0.5F));

        int primaryColor = ThemeManager.accent(255);
        int secondColor = ThemeManager.gradientB(255);

        float glowStrength = clampedAlpha * 0.5F;
        if (glowStrength > 0.004F) {
            int glowColor = ThemeManager.accent(Math.round(glowStrength * 255.0F));
            Draw.glow(x, y, w, h, glowColor, r, 10.0F, 0.55F, 0.8F);
        }

        Draw.blur(x, y, w, h, clampedAlpha, r, 0);

        float glassAlpha = clampedAlpha * 0.30F;
        int glassTint = ThemeManager.accent(Math.round(glassAlpha * 255.0F));
        Draw.glass(x, y, w, h, glassAlpha, r, r, r, r, glassTint);

        Draw.glassOutline(x, y, w, h, 0.65F, r, clampedAlpha * 0.35F, 0.2F);
    }

    public static void drawClientShape(float x, float y, float w, float h, float alpha) {
        drawClientRect(x, y, w, h, DEFAULT_CORNER_RADIUS, alpha);
    }

    public static void drawClientShape(float x, float y, float w, float h, float radius, float alpha) {
        drawClientRect(x, y, w, h, radius, alpha);
    }

    public static void drawClientSector(float x, float y, float w, float h,
                                         float startAngle, float sweep, float radius, float alpha) {
        float clampedAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
        if (clampedAlpha <= 0.0F || w <= 0.0F || h <= 0.0F) {
            return;
        }
        Draw.blur(x, y, w, h, clampedAlpha, radius, 0);
        int tint = ThemeManager.accent(Math.round(clampedAlpha * 0.30F * 255.0F));
        Draw.glass(x, y, w, h, clampedAlpha * 0.30F, radius, radius, radius, radius, tint);
    }

    public static void drawClientRectNoGlow(float x, float y, float w, float h, float radius, float alpha) {
        float clampedAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
        if (clampedAlpha <= 0.0F || w <= 0.0F || h <= 0.0F) {
            return;
        }
        float r = Math.max(0.0F, Math.min(radius, Math.min(w, h) * 0.5F));
        Draw.blur(x, y, w, h, clampedAlpha, r, 0);
        int tint = ThemeManager.accent(Math.round(clampedAlpha * 0.30F * 255.0F));
        Draw.glass(x, y, w, h, clampedAlpha * 0.30F, r, r, r, r, tint);
    }

    public static void drawPanelBg(float x, float y, float w, float h, float alpha) {
        drawPanelBg(x, y, w, h, DEFAULT_CORNER_RADIUS, DEFAULT_CORNER_RADIUS, DEFAULT_CORNER_RADIUS, DEFAULT_CORNER_RADIUS, alpha);
    }

    public static void drawPanelBg(float x, float y, float w, float h, float radius, float alpha) {
        drawPanelBg(x, y, w, h, radius, radius, radius, radius, alpha);
    }

    public static void drawPanelBg(float x, float y, float w, float h,
                                   float tl, float tr, float br, float bl, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(40.0F * alpha)));
        if (a > 0) {
            Draw.rect(x, y, w, h, new Color(0, 0, 0, a).getRGB(), tl, tr, br, bl);
        }
    }

    public static float effectiveCornerRadius(float radius, float w, float maxRef) {
        float cap = Math.min(w, maxRef) * 0.5F;
        return Math.min(cap, radius);
    }

    public static float cornerEdgeInset(float radius, float thickness) {
        if (radius <= thickness) {
            return 0.0F;
        }
        float d = radius - thickness;
        return radius - (float) Math.sqrt(Math.max(0.0F, radius * radius - d * d));
    }

    public static void drawDropBackground(float x, float y, float w, float h, float alpha) {
        drawClientRect(x, y, w, h, 2.0F, alpha);
    }

    public static void drawString(String text, float x, float y, float size, int color) {
        FontsLV.MONTSERRAT_MEDIUM.draw(text, x, y, size, color);
    }

    public static float stringWidth(String text, float size) {
        return FontsLV.MONTSERRAT_MEDIUM.width(text, size);
    }

    public static void drawCenteredString(String text, float x, float y, float size, int color) {
        FontsLV.MONTSERRAT_MEDIUM.drawCentered(text, x, y, size, color);
    }

    public static void drawIcon(String glyph, float x, float y, float size, int color) {
        FontsLV.WVISUAL.msdf(glyph, x, y, size, color);
    }

    public static float iconWidth(String glyph, float size) {
        return FontsLV.WVISUAL.msdfWidth(glyph, size);
    }
}

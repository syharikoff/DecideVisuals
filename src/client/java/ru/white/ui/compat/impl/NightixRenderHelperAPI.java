package ru.white.ui.compat.impl;

import java.awt.Color;

import ru.white.ui.compat.RenderHelperAPI;
import ru.white.ui.theme.ThemeManager;
import ru.white.utils.render.Draw;

public final class NightixRenderHelperAPI implements RenderHelperAPI {

    private static final float DEFAULT_RADIUS = 7.0F;

    @Override
    public void drawPanelBg(float x, float y, float w, float h,
                            float tl, float tr, float br, float bl, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(40.0F * alpha)));
        if (a > 0) {
            Draw.rect(x, y, w, h, new Color(0, 0, 0, a).getRGB(), tl, tr, br, bl);
        }
    }

    @Override
    public void drawPanelBg(float x, float y, float w, float h, float alpha) {
        drawPanelBg(x, y, w, h, DEFAULT_RADIUS, DEFAULT_RADIUS, DEFAULT_RADIUS, DEFAULT_RADIUS, alpha);
    }

    @Override
    public void drawDropBackground(float x, float y, float w, float h, float alpha) {
        drawClientRect(x, y, w, h, 2.0F, alpha, 0.0F);
    }

    @Override
    public void drawClientRect(float x, float y, float w, float h,
                               float radius, float opacity, float extraRadius) {
        float clampedAlpha = Math.max(0.0F, Math.min(1.0F, opacity));
        if (clampedAlpha <= 0.0F || w <= 0.0F || h <= 0.0F) return;
        float r = Math.max(0.0F, Math.min(radius + extraRadius, Math.min(w, h) * 0.5F));

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

    @Override
    public void drawClientRectFixedRadius(float x, float y, float w, float h,
                                          float radius, float opacity, float extraRadius) {
        drawClientRect(x, y, w, h, radius, opacity, extraRadius);
    }

    @Override
    public float effectiveCornerRadius(float base, float extra, float maxRef) {
        float cap = Math.min(extra, maxRef) * 0.5F;
        return Math.min(cap, base);
    }

    @Override
    public float cornerEdgeInset(float radius, float thickness) {
        if (radius <= thickness) return 0.0F;
        float d = radius - thickness;
        return radius - (float) Math.sqrt(Math.max(0.0F, radius * radius - d * d));
    }
}

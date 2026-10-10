package ru.decide.ui.theme;

import ru.decide.utils.render.Draw;

public final class AccentGradient {
    private AccentGradient() {
    }

    private static int rgba(int r, int g, int b, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        return a <= 0 ? 0 : a << 24 | r << 16 | g << 8 | b;
    }

    private static int samplePalette(int[] palette, float t) {
        if (palette == null || palette.length == 0) {
            return 0;
        } else if (palette.length == 1) {
            return palette[0];
        } else {
            float p = Math.max(0.0F, Math.min(1.0F, t)) * (palette.length - 1);
            int i = (int) Math.floor(p);
            int j = Math.min(i + 1, palette.length - 1);
            return rgbLerp(palette[i], palette[j], p - i);
        }
    }

    private static int rgbLerp(int c1, int c2, float t) {
        float p = Math.max(0.0F, Math.min(1.0F, t));
        int r = Math.round((c1 >> 16 & 0xFF) + ((c2 >> 16 & 0xFF) - (c1 >> 16 & 0xFF)) * p);
        int g = Math.round((c1 >> 8 & 0xFF) + ((c2 >> 8 & 0xFF) - (c1 >> 8 & 0xFF)) * p);
        int b = Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * p);
        return r << 16 | g << 8 | b;
    }

    private static int themeColor(float position, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        if (a <= 0) return 0;
        int[] palette = ThemeManager.blendedPalette();
        int rgb = samplePalette(palette, position);
        return a << 24 | rgb;
    }

    public static void msdfIcon(String fontName, String glyph, float x, float y, float size, float alpha) {
        msdfIcon(fontName, glyph, x, y, size, alpha, 0.5F);
    }

    public static void msdfIcon(String fontName, String glyph, float x, float y, float size, float alpha, float position) {
        if (alpha <= 0.0F || glyph == null || glyph.isEmpty()) {
            return;
        }
        float t = position < 0.0F ? 0.0F : (position > 1.0F ? 1.0F : position);
        int color = themeColor(t * 0.7F, alpha);
        ru.decide.utils.render.font.Font font = switch (fontName) {
            case "montserrat-medium", "MONT" -> ru.decide.utils.render.font.Fonts.sf_medium;
            case "small-pixel", "SMALL" -> ru.decide.utils.render.font.Fonts.sf_bold;
            case "decidevisuals", "DV" -> ru.decide.utils.render.font.Fonts.gui;
            default -> ru.decide.utils.render.font.Fonts.sf_medium;
        };
        font.draw(glyph, x, y, size, color);
    }

    public static void fillHorizontal(float x, float y, float w, float h, float radii, float alphaInt) {
        paletteFill(x, y, w, h, radii, 2, 1.0F, alphaInt);
    }

    public static void fillVertical(float x, float y, float w, float h, float radii, float alphaInt) {
        paletteFill(x, y, w, h, radii, 1, 1.0F, alphaInt);
    }

    public static void overlayTrackShade(float x, float y, float w, float h, float radii, float alphaInt) {
        paletteFill(x, y, w, h, radii, 2, 0.6F, alphaInt * 0.3F);
    }

    private static void paletteFill(float x, float y, float w, float h, float radii, int mode, float intensity, float alphaInt) {
        if (w <= 0.0F || h <= 0.0F || alphaInt <= 0.0F) {
            return;
        }
        float alphaFactor = Math.min(1.0F, (alphaInt / 255.0F) * intensity);
        if (alphaFactor <= 0.0F) {
            return;
        }
        int alphaChannel = Math.round(alphaFactor * 255);

        int cA = themeColor(0.0F, alphaChannel);
        int cB = themeColor(1.0F, alphaChannel);

        int[] colors = switch (mode) {
            case 1 -> new int[]{cA, cA, cB, cB};
            case 3 -> new int[]{cA, cB, cA, cB};
            default -> new int[]{cA, cB, cB, cA};
        };

        Draw.gradientRect(x, y, w, h, colors, radii, radii, radii, radii);
    }

    public static void fillHorizontalLoop(float x, float y, float w, float h, float radii, float alphaInt) {
        paletteFill(x, y, w, h, radii, 3, 1.0F, alphaInt);
    }

    public static void overlayKnobFade(float left, float right, float y, float h, float radii, float alphaInt) {
        if (right - left <= 0.5F || alphaInt <= 0.0F) {
            return;
        }
        float segLeft = left + (right - left) * 0.66666F;
        float segW = right - segLeft;
        if (segW <= 0.5F) {
            return;
        }
        int c = rgba(0, 0, 0, alphaInt * 0.61F);
        Draw.rect(segLeft, y, segW, h, c, radii);
    }

    public static float categoryListIndexT(String categoryName) {
        if (categoryName == null) return 0.0F;
        String[] all = {"VISUALS", "HUD", "UTILITIES"};
        for (int i = 0; i < all.length; i++) {
            if (all[i].equals(categoryName)) {
                return (float) i / Math.max(1, all.length - 1);
            }
        }
        return 0.0F;
    }

    public static void fillKnob(float x, float y, float w, float h, float radii, float alphaInt) {
        fillVertical(x, y, w, h, radii, alphaInt);
    }

    public static boolean usesDual() {
        return ThemeManager.current().gradientA() != ThemeManager.current().gradientB();
    }
}

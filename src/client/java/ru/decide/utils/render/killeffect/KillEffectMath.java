package ru.decide.utils.render.killeffect;

import net.minecraft.util.math.MathHelper;

import java.awt.Color;

/** Общая математика для эффектов Kill Effect (сглаживания, альфа, сдвиг оттенка). */
public final class KillEffectMath {

    private KillEffectMath() {
    }

    public static float smoothStep(float edge0, float edge1, float x) {
        float v = MathHelper.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return v * v * (3.0F - 2.0F * v);
    }

    public static float easeOutCubic(float t) {
        float inv = 1.0F - MathHelper.clamp(t, 0.0F, 1.0F);
        return 1.0F - inv * inv * inv;
    }

    public static float easeOutExpo(float t) {
        float v = Math.min(t, 1.0F);
        return v == 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, -10.0F * v);
    }

    public static int withAlpha(int argb, float alpha01) {
        int a = Math.round(MathHelper.clamp(alpha01, 0.0F, 1.0F) * 255.0F);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    /** Сдвиг оттенка на N градусов — для разноцветных искр. */
    public static int shiftHue(int argb, float degrees) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        hsb[0] = (hsb[0] + degrees / 360.0F) % 1.0F;
        if (hsb[0] < 0.0F) hsb[0] += 1.0F;
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF;
        return (argb & 0xFF000000) | rgb;
    }
}
package ru.white.utils.render.wasted;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Состояние кинематографичной смерти: таймеры, точка якоря и кривые эффекта. */
public final class WastedState {

    public static final long FADE_IN_MS = 900L;
    public static final long TEXT_DELAY_MS = 1100L;

    private static boolean active;
    private static long startedAtMs;
    private static long durationMs;
    private static Vec3d anchor = Vec3d.ZERO;
    private static float startYaw;
    private static boolean detached;

    private WastedState() {
    }

    public static void begin(Vec3d deathPosition, float yaw, long duration) {
        active = true;
        startedAtMs = System.currentTimeMillis();
        durationMs = Math.max(1000L, duration);
        anchor = deathPosition;
        startYaw = yaw;
        detached = false;
    }

    public static void stop() {
        active = false;
        detached = false;
    }

    public static void setDetached(boolean value) {
        detached = value;
    }

    /** Камера оторвалась от тела и облетает его по орбите. */
    public static boolean isDetached() {
        return detached && active;
    }

    public static boolean isActive() {
        if (active && elapsedMs() >= durationMs) active = false;
        return active;
    }

    public static long elapsedMs() {
        return System.currentTimeMillis() - startedAtMs;
    }

    public static float progress() {
        return MathHelper.clamp(elapsedMs() / (float) durationMs, 0.0f, 1.0f);
    }

    /** Плавное нарастание эффекта в начале. */
    public static float strength() {
        float rise = MathHelper.clamp(elapsedMs() / (float) FADE_IN_MS, 0.0f, 1.0f);
        return rise * rise * (3.0f - 2.0f * rise);
    }

    public static Vec3d anchor() {
        return anchor;
    }

    public static float startYaw() {
        return startYaw;
    }

    /** Короткая вспышка в момент смерти. */
    public static float flash() {
        float t = elapsedMs() / 320.0f;
        if (t >= 1.0f) return 0.0f;
        float fall = 1.0f - t;
        return fall * fall;
    }

    public static float radialBlur() {
        float t = elapsedMs() / 1400.0f;
        if (t >= 1.0f) return 0.0f;
        float fall = 1.0f - t;
        return fall * fall;
    }

    /** Прозрачность надписи: появляется с задержкой и гаснет к концу. */
    public static float textAlpha() {
        long elapsed = elapsedMs();
        if (elapsed < TEXT_DELAY_MS) return 0.0f;

        float in = MathHelper.clamp((elapsed - TEXT_DELAY_MS) / 550.0f, 0.0f, 1.0f);
        float out = 1.0f - MathHelper.clamp((progress() - 0.85f) / 0.15f, 0.0f, 1.0f);
        return in * in * (3.0f - 2.0f * in) * out;
    }
}

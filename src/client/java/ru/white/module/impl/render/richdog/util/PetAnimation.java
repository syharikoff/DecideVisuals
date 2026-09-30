package ru.white.module.impl.render.richdog.util;

public final class PetAnimation {

    private float current = 0F;
    private float target = 0F;
    private float origin = 0F;
    private long startMs = 0L;
    private int durationMs = 1;

    public PetAnimation() {
    }

    public float animate(float destination, int ms) {
        if (destination == target) {
            return get();
        }
        origin = get();
        target = destination;
        durationMs = Math.max(1, ms);
        startMs = System.currentTimeMillis();
        return get();
    }

    public float get() {
        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed >= durationMs) {
            current = target;
            return current;
        }
        float t = (float) elapsed / durationMs;
        t = t * t * (3F - 2F * t);
        current = origin + (target - origin) * t;
        return current;
    }

    public boolean finished() {
        return System.currentTimeMillis() - startMs >= durationMs;
    }
}

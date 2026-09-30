package ru.white.ui.util.anim;

public final class DecelerateValue {
    private final Decelerate anim;
    private float from;
    private float to;
    private float current;
    private boolean initialised;

    public DecelerateValue(int n) {
        this.anim = (Decelerate) new Decelerate().setMs(n).setValue(1.0);
        this.anim.counter.setTime(System.currentTimeMillis() - 10000L);
    }

    public float update(float f) {
        if (!this.initialised) {
            this.snap(f);
            return this.current;
        } else {
            if (f != this.to) {
                this.from = this.current;
                this.to = f;
                this.anim.reset();
            }

            this.current = this.from + (this.to - this.from) * this.anim.getOutput().floatValue();
            return this.current;
        }
    }

    public float current() {
        return this.current;
    }

    public void snap(float f) {
        this.initialised = true;
        this.from = f;
        this.to = f;
        this.current = f;
        this.anim.counter.setTime(System.currentTimeMillis() - 10000L);
    }
}
package ru.white.ui.util.anim;

public class SmoothAnimation {
    private long start;
    private double duration;
    private double fromValue;
    private double toValue;
    private double value;
    private double prevValue;
    private Easing easing = Easings.EXPO_OUT;
    private boolean finished;

    public SmoothAnimation() {
    }

    public SmoothAnimation run(double to, double durationSeconds, Easing easing, boolean check) {
        if (this.check(check, to)) {
            return this;
        } else {
            this.easing = easing;
            this.start = System.currentTimeMillis();
            this.duration = durationSeconds * 1000.0;
            this.fromValue = this.value;
            this.toValue = to;
            this.finished = this.fromValue == this.toValue;
            return this;
        }
    }

    public SmoothAnimation run(double to, double durationSeconds, boolean check) {
        return this.run(to, durationSeconds, Easings.EXPO_OUT, check);
    }

    public SmoothAnimation run(double to, double durationSeconds, Easing easing) {
        return this.run(to, durationSeconds, easing, false);
    }

    public SmoothAnimation run(double to, double durationSeconds) {
        return this.run(to, durationSeconds, Easings.EXPO_OUT, false);
    }

    public boolean update() {
        this.prevValue = this.value;
        boolean alive = this.isAlive();
        if (System.currentTimeMillis() - this.start > this.duration / 1.5) {
            this.finished = this.fromValue == this.toValue;
        }

        if (alive) {
            double p = Math.min(1.0, Math.max(0.0, this.calculatePart()));
            this.value = this.interpolate(this.fromValue, this.toValue, this.easing.ease(p));
        } else {
            this.start = 0L;
            this.value = this.toValue;
        }

        return alive;
    }

    public double getValue() {
        return this.value;
    }

    public float get() {
        return (float) this.value;
    }

    public void set(double value) {
        this.run(value, 1.0E-4);
        this.update();
        this.value = value;
    }

    public boolean isAlive() {
        return !this.isFinished();
    }

    public void setValue(double value) {
        this.value = value;
    }

    public boolean check(boolean check, double value) {
        return check && this.isAlive() && (value == this.fromValue || value == this.toValue || value == this.value);
    }

    public double interpolate(double from, double to, double ratio) {
        return from + (to - from) * ratio;
    }

    public double getDuration() {
        return this.duration;
    }

    public boolean isFinished() {
        return this.calculatePart() >= 1.0;
    }

    public Easing getEasing() {
        return this.easing;
    }

    public double getToValue() {
        return this.toValue;
    }

    public float getPrev() {
        return (float) this.prevValue;
    }

    public long getStart() {
        return this.start;
    }

    public double calculatePart() {
        return this.duration <= 0.0 ? 1.0 : (System.currentTimeMillis() - this.start) / this.duration;
    }

    public double getFromValue() {
        return this.fromValue;
    }

    public double getPrevValue() {
        return this.prevValue;
    }
}
package ru.white.ui.util.anim;

public class Animation implements AnimationCalculation {
    public final TimerUtil counter = new TimerUtil();
    protected int ms;
    protected double value;
    protected Direction direction = Direction.FORWARDS;

    public Animation() {
    }

    public void reset() {
        this.counter.resetCounter();
    }

    public void update() {
    }

    public boolean isDone() {
        return this.counter.isReached(this.ms);
    }

    public Animation setValue(double d) {
        this.value = d;
        return this;
    }

    private double getProgress() {
        return this.ms <= 0 ? 1.0 : Math.min(1.0, Math.max(0.0, (double) this.counter.getTime() / this.ms));
    }

    protected double endValue() {
        return this.isDone() ? this.value : this.calculation(this.getProgress()) * this.value;
    }

    public Double getOutput() {
        double d = (1.0 - this.calculation(this.getProgress())) * this.value;
        return this.direction == Direction.FORWARDS ? this.endValue() : (this.isDone() ? 0.0 : d);
    }

    public boolean isFinished(Direction direction) {
        return this.direction == direction && this.isDone();
    }

    public Animation setMs(int n) {
        this.ms = n;
        return this;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public boolean isDirection(Direction direction) {
        return this.direction == direction;
    }

    public void setDirection(Direction direction) {
        if (this.direction != direction) {
            this.direction = direction;
            this.adjustTimer();
        }
    }

    private void adjustTimer() {
        this.counter.setTime(System.currentTimeMillis() - (this.ms - Math.min((long) this.ms, this.counter.getTime())));
    }

    @Override
    public double calculation(double x) {
        return x;
    }
}
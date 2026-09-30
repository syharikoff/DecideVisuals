package ru.white.ui.util.anim;

public class Decelerate extends Animation {
    public Decelerate() {
    }

    @Override
    public double calculation(double x) {
        return 1.0 - (1.0 - x) * (1.0 - x);
    }

    @Override
    public Decelerate setValue(double d) {
        super.setValue(d);
        return this;
    }

    @Override
    public Decelerate setMs(int n) {
        super.setMs(n);
        return this;
    }
}
package ru.white.ui.util.anim;

public final class Easings {
    public static final Easing LINEAR = x -> x;
    public static final Easing QUAD_OUT = x -> 1.0 - (1.0 - x) * (1.0 - x);
    public static final Easing CUBIC_OUT = x -> 1.0 - Math.pow(1.0 - x, 3.0);
    public static final Easing QUART_OUT = x -> 1.0 - Math.pow(1.0 - x, 4.0);
    public static final Easing EXPO_IN = x -> x == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * (x - 1.0));
    public static final Easing EXPO_OUT = x -> x == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * x);
    public static final Easing EXPO_IN_OUT = x -> {
        if (x == 0.0) {
            return 0.0;
        } else if (x == 1.0) {
            return 1.0;
        } else {
            return x < 0.5 ? Math.pow(2.0, 20.0 * x - 10.0) / 2.0 : (2.0 - Math.pow(2.0, -20.0 * x + 10.0)) / 2.0;
        }
    };
    public static final Easing SINE_OUT = x -> Math.sin(x * Math.PI / 2.0);
    public static final Easing BACK_OUT = x -> {
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        return 1.0 + c3 * Math.pow(x - 1.0, 3.0) + c1 * Math.pow(x - 1.0, 2.0);
    };

    private Easings() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
package ru.white.utils.aura;


import ru.white.utils.annotation.IMinecraft;

import static java.lang.Math.round;

public class GCDUtil implements IMinecraft {
    public static float getSensitivity(float rot) {
        return getDeltaMouse(rot) * getGCDValue();
    }

    public static float getGCDValue() {
        return (float) (getGCD() * 0.15);
    }

    public static float getGCD() {
        float f1;
        return (f1 = (float) (mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2)) * f1 * f1 * 8;
    }

    public static float getDeltaMouse(float delta) {
        return round(delta / getGCDValue());
    }
    public static float applyGCD(float targetRotation, float currentRotation) {

        float sensitivity = mc.options.getMouseSensitivity().getValue().floatValue();

        float f = sensitivity * 0.6F + 0.2F;
        float f1 = f * f * f * 8.0F;


        float delta = targetRotation - currentRotation;

        float adjustedDelta = Math.round(delta / (f1 * 0.15F)) * (f1 * 0.15F);


        return currentRotation + adjustedDelta;
    }
}



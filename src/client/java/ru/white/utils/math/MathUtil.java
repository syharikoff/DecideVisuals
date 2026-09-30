package ru.white.utils.math;


import ru.white.utils.annotation.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@UtilityClass
public class MathUtil implements IMinecraft {
    public int floorNearestMulN(int x, int n) {
        return n * (int) Math.floor((double) x / (double) n);
    }

    public static double deltaTime() {
        return mc.getCurrentFps() > 0 ? (1.0000 / mc.getCurrentFps()) : 1;
    }
    private static final double[] TRIG_TABLE = new double[65536];

    private static float[] lastSmoothRandom;
    private static float[] lastSmoothTarget;
    private static long lastSmoothTime = 0L;


    public static float invertScaleValue(float value, float minInput, float maxInput, float minOutput, float maxOutput) {
        if (maxInput - minInput == 0) {
            throw new IllegalArgumentException("\u0414\u0438\u0430\u043F\u0430\u0437\u043E\u043D \u0432\u0445\u043E\u0434\u043D\u044B\u0445 \u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0439 \u043D\u0435 \u043C\u043E\u0436\u0435\u0442 \u0431\u044B\u0442\u044C \u0440\u0430\u0432\u0435\u043D \u043D\u0443\u043B\u044E.");
        }

        float scaledValue = (maxInput - value) / (maxInput - minInput) * (maxOutput - minOutput) + minOutput;

        return Math.max(minOutput, Math.min(maxOutput, scaledValue));
    }
    public static float smoothRandom(float min, float max, float smoothness) {

        if (lastSmoothRandom == null) {
            lastSmoothRandom = new float[]{(min + max) / 2F};
            lastSmoothTarget = new float[]{randomValue(min, max)};
        }


        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSmoothTime > 250 + Math.random() * 300) {
            lastSmoothTarget[0] = randomValue(min, max);
            lastSmoothTime = currentTime;
        }

        lastSmoothRandom[0] += (lastSmoothTarget[0] - lastSmoothRandom[0]) * smoothness;

        return lastSmoothRandom[0];
    }

    public double PI2 = Math.PI * 2;
    public Vec3d cosSin(int i, int size, double width) {
        int index = Math.min(i, size);
        float cos = (float) (Math.cos(index * MathUtil.PI2 / size) * width);
        float sin = (float) (-Math.sin(index * MathUtil.PI2 / size) * width);
        return new Vec3d(cos, 0, sin);
    }   public float wrapLerp(float step, float input, float target) {
        return input + step * MathHelper.wrapDegrees(target - input);
    }
    public static double easeInOutQuadWave(double value) {
        value = (value > 0.5 ? 1.0 - value : value) * 2.0;
        double d = value = value < 0.5 ? 2.0 * value * value : 1.0 - Math.pow(-2.0 * value + 2.0, 2.0) / 2.0;
        value = value > 1.0 ? 1.0 : (value < 0.0 ? 0.0 : value);
        return value;
    }
    public static float wrapAngleTo180_float(float p_76142_0_) {
        if ((p_76142_0_ %= 360.0f) >= 180.0f) {
            p_76142_0_ -= 360.0f;
        }
        if (p_76142_0_ < -180.0f) {
            p_76142_0_ += 360.0f;
        }
        return p_76142_0_;
    }

    public static double sin(double radians) {
        int index = (int)(radians * 10430.378350470453) & 65535;
        return TRIG_TABLE[index];
    }

    public static double cos(double radians) {
        int index = (int)(radians * 10430.378350470453 + 16384.0) & 65535;
        return TRIG_TABLE[index];
    }


    public static float random1(float min, float max) {
        return (float) (Math.random() * (max - min) + min);
    }
    public static float random(double min, double max) {
        return (float)(min + (max - min) * Math.random());
    }

    public static float randomSin(double min, double max,double time) {
        return (float) (Math.sin(System.currentTimeMillis() / time) * randomLerp((float) min, (float) max));
    }
    public static float randomCos(double min, double max,double time) {
        return (float) (Math.cos(System.currentTimeMillis() / time) * randomLerp((float) min, (float) max));
    }

    private static final Random RANDOM = new Random();

    public static float randomGaussian(double min, double max) {
        double mean = (min + max) * 0.5;
        double deviation = (max - min) / 6.0; // ~99.7% значений будут в диапазоне

        double value = mean + RANDOM.nextGaussian() * deviation;

        return (float) Math.max(min, Math.min(max, value));
    }
    public static float randomGaussianSmooth(double min, double max, double time) {
        double mean = (min + max) * 0.5;
        double deviation = (max - min) / 6.0;

        double noise = RANDOM.nextGaussian() * deviation;
        double smooth = Math.sin(System.currentTimeMillis() / time);

        double value = mean + noise * smooth;

        return (float) Math.max(min, Math.min(max, value));
    }

    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }
    public static float fast(float end, float start, float multiple) {
        return (1 - MathHelper.clamp((float) (deltaTime() * multiple), 0, 1)) * end + MathHelper.clamp((float) (deltaTime() * multiple), 0, 1) * start;
    }

    public double clamps(double min, double max, double value) {
        return Math.max(min, Math.min(max, value));
    }
    public float randomLerp(float min, float max) {
        return Interpolator.lerp(max, min, new SecureRandom().nextFloat());
    }    public static double getRandomNumberBetween(double min, double max) {
        return Math.random() * (max - min) + min;
    }

    public static float interpolate(double oldValue, double newValue, double interpolationValue) {
        return (float)(oldValue + (newValue - oldValue) * interpolationValue);
    }
    public Vec3d interpolate(Vec3d prevPos, Vec3d pos) {
        return new Vec3d(interpolate(prevPos.x, pos.x), interpolate(prevPos.y, pos.y), interpolate(prevPos.z, pos.z));
    }

    public double interpolate(double prev, double orig) {
        return lerp(mc.getRenderTickCounter().getTickProgress(false), prev, orig);
    }

    public static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }
    public static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        } else {
            return value;
        }
    }
    public static int randomInt(int min, int max) {
        return min + (int) (Math.random() * ((max - min) + 1));
    }

    public double randomValue(double min, double max) {
        validateRange(min, max);
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    public float randomValue(float min, float max) {
        validateRange(min, max);
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }
    private static void validateRange(double min, double max) {
        if (max < min) {
            throw new IllegalArgumentException("max не может быть меньше min.");
        }
    }
    public float textScrolling(float textWidth) {
        int speed = 6000;
        return (float) MathHelper.clamp((System.currentTimeMillis() % speed * Math.PI / speed), 0, 1) * textWidth;
    }
    public static int clampMin(int value, int min) {
        return value < min ? min : value;
    }

    public static float clampMin(float value, float min) {
        return value < min ? min : value;
    }

    public static double clampMin(double value, double min) {
        return value < min ? min : value;
    }

    public static boolean isInRegion(int mouseX, int mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }    public boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {

        return mouseX > x && mouseX < x + width && mouseY > y && mouseY < y + height;
    }


    public static float step(float value, float step) {
        return Math.round(value / step) * step;
    }

    public double round(double value, int increment) {
        double num = Math.pow(10, increment);
        return Math.round(value * num) / num;
    }
    public static float round(float value, int decimals) {
        float multiplier = (float) Math.pow(10, decimals);
        return Math.round(value * multiplier) / multiplier;
    }

    public static float round(float value, float increment) {
        return Math.round(value / increment) * increment;
    }

    public static final Matrix4f lastProjMat = new Matrix4f();
    public static final Matrix4f lastModMat = new Matrix4f();
    public static final Matrix4f lastWorldSpaceMatrix = new Matrix4f();

    public static double getScaleFactor() {
        return mc.getWindow().getScaleFactor();
    }

    static {
        for (int i = 0; i < 65536; i++) {
            TRIG_TABLE[i] = Math.sin(i * (Math.PI * 2) / 65536.0);
        }
    }
}


package ru.white.utils.math;

import org.jetbrains.annotations.NotNull;

public record BorderRadius(float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius) {

    public static final BorderRadius ZERO = new BorderRadius(0f, 0f, 0f, 0f);

    /**
     * Создает экземпляр BorderRadius с одинаковыми радиусами для всех углов.
     */
    public static BorderRadius all(float radius) {
        return new BorderRadius(radius, radius, radius, radius);
    }

    /**
     * Создает экземпляр BorderRadius с радиусом только для верхнего левого угла.
     */
    public static BorderRadius topLeft(float radius) {
        return new BorderRadius(radius, 0f, 0f, 0f);
    }

    /**
     * Создает экземпляр BorderRadius с радиусом только для верхнего правого угла.
     */
    public static BorderRadius topRight(float radius) {
        return new BorderRadius(0f, radius, 0f, 0f);
    }

    /**
     * Создает экземпляр BorderRadius с радиусом только для нижнего правого угла.
     */
    public static BorderRadius bottomRight(float radius) {
        return new BorderRadius(0f, 0f, radius, 0f);
    }

    /**
     * Создает экземпляр BorderRadius с радиусом только для нижнего левого угла.
     */
    public static BorderRadius bottomLeft(float radius) {
        return new BorderRadius(0f, 0f, 0f, radius);
    }

    /**
     * Создает экземпляр BorderRadius с радиусами только для верхней грани.
     */
    public static BorderRadius top(float leftRadius, float rightRadius) {
        return new BorderRadius(leftRadius, rightRadius, 0f, 0f);
    }

    /**
     * Создает экземпляр BorderRadius с радиусами только для нижней грани.
     */
    public static BorderRadius bottom(float leftRadius, float rightRadius) {
        return new BorderRadius(0f, 0f, rightRadius, leftRadius);
    }

    /**
     * Создает экземпляр BorderRadius с радиусами только для левой грани.
     */
    public static BorderRadius left(float topRadius, float bottomRadius) {
        return new BorderRadius(topRadius, 0f, 0f, bottomRadius);
    }

    /**
     * Создает экземпляр BorderRadius с радиусами только для правой грани.
     */
    public static BorderRadius right(float topRadius, float bottomRadius) {
        return new BorderRadius(0f, topRadius, bottomRadius, 0f);
    }

    @Override
    public @NotNull String toString() {
        return "BorderRadius{" +
                "topLeftRadius=" + topLeftRadius +
                ", topRightRadius=" + topRightRadius +
                ", bottomRightRadius=" + bottomRightRadius +
                ", bottomLeftRadius=" + bottomLeftRadius +
                '}';
    }
}

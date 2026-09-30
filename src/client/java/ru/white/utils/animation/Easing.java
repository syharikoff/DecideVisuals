package ru.white.utils.animation;

@FunctionalInterface
public interface Easing {
    double ease(double value);
}
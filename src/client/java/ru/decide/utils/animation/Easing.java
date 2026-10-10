package ru.decide.utils.animation;

@FunctionalInterface
public interface Easing {
    double ease(double value);
}
package ru.white.ui.compat;

/**
 * Абстрактный API для цветов клиента (accent, gradient, toggle, rainbow).
 */
public interface ColorAPI {

    int toggleOn(float alpha);
    int gradientA(float alpha);
    int gradientB(float alpha);
    int accent(float alpha);
    int accentSoft(float alpha);
    int accentBright(float alpha);
    int accentFill(float alpha);
    int rgba(int rgb, float alpha);
    int mix(int c1, int c2, float t);
    int[] currentPalette();
    int rainbowFlow(float hueOffset, float alpha);
    float rainbowBaseHue();
    int gradientColor(float position, float alpha);
    void beginFrame();
}

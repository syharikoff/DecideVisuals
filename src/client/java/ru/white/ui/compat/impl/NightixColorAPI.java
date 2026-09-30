package ru.white.ui.compat.impl;

import ru.white.ui.compat.ColorAPI;
import ru.white.ui.theme.ClientAccent;

public final class NightixColorAPI implements ColorAPI {

    @Override
    public int toggleOn(float alpha) {
        return ClientAccent.toggleOn(alpha);
    }

    @Override
    public int gradientA(float alpha) {
        return ClientAccent.gradientA(alpha);
    }

    @Override
    public int gradientB(float alpha) {
        return ClientAccent.gradientB(alpha);
    }

    @Override
    public int accent(float alpha) {
        return ClientAccent.accent(alpha);
    }

    @Override
    public int accentSoft(float alpha) {
        return ClientAccent.accentSoft(alpha);
    }

    @Override
    public int accentBright(float alpha) {
        return ClientAccent.accentBright(alpha);
    }

    @Override
    public int accentFill(float alpha) {
        return ClientAccent.accentFill(alpha);
    }

    @Override
    public int rgba(int rgb, float alpha) {
        return ClientAccent.rgba(rgb, alpha);
    }

    @Override
    public int mix(int c1, int c2, float t) {
        return ClientAccent.mix(c1, c2, t);
    }

    @Override
    public int[] currentPalette() {
        return ClientAccent.currentPalette();
    }

    @Override
    public int rainbowFlow(float hueOffset, float alpha) {
        return ClientAccent.rainbowFlow(hueOffset, alpha);
    }

    @Override
    public float rainbowBaseHue() {
        return ClientAccent.rainbowBaseHue();
    }

    @Override
    public int gradientColor(float position, float alpha) {
        return ClientAccent.gradientColor(position, alpha);
    }

    @Override
    public void beginFrame() {
        ClientAccent.beginFrame();
    }
}

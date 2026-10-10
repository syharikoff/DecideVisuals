package ru.decide.ui.compat.impl;

import net.minecraft.client.MinecraftClient;
import ru.decide.ui.compat.PositionAPI;

public final class DecideVisualsPositionAPI implements PositionAPI {

    @Override
    public float mouseX() {
        return (float) MinecraftClient.getInstance().mouse.getX();
    }

    @Override
    public float mouseY() {
        return (float) MinecraftClient.getInstance().mouse.getY();
    }

    @Override
    public float screenWidth() {
        return MinecraftClient.getInstance().getWindow().getScaledWidth();
    }

    @Override
    public float screenHeight() {
        return MinecraftClient.getInstance().getWindow().getScaledHeight();
    }
}

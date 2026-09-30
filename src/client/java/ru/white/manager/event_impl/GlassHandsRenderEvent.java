package ru.white.manager.event_impl;

import ru.white.manager.events.CancellableEvent;
import net.minecraft.client.util.math.MatrixStack;

public class GlassHandsRenderEvent extends CancellableEvent {

    public enum Phase {
        PRE,
        POST
    }

    private final Phase phase;
    private final MatrixStack matrices;
    private final float tickDelta;

    public GlassHandsRenderEvent(Phase phase, MatrixStack matrices, float tickDelta) {
        this.phase = phase;
        this.matrices = matrices;
        this.tickDelta = tickDelta;
    }

    public Phase getPhase() {
        return phase;
    }

    public MatrixStack getMatrices() {
        return matrices;
    }

    public float getTickDelta() {
        return tickDelta;
    }
}

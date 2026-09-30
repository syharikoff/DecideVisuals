package ru.white.manager.event_impl;


import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import ru.white.manager.events.Event;

public class EventRender3D extends Event {
    private final MatrixStack matrixStack;

    private final float tickDelta;

    /** Матрица проекции кадра; нужна рендерам, которые сами собирают пайплайн. */
    private final Matrix4f projectionMatrix;

    public EventRender3D(MatrixStack matrixStack, float tickDelta) {
        this(matrixStack, tickDelta, null);
    }

    public EventRender3D(MatrixStack matrixStack, float tickDelta, Matrix4f projectionMatrix) {
        this.matrixStack = matrixStack;

        this.tickDelta = tickDelta;
        this.projectionMatrix = projectionMatrix;
    }

    public MatrixStack getMatrixStack() {
        return matrixStack;
    }


    public float getTickDelta() {
        return tickDelta;
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }
}

package ru.decide.utils.render;

import net.minecraft.client.render.VertexConsumer;

/**
 * Заглушка VertexConsumer: все вызовы игнорируются.
 * Нужна, когда рендер-команда не должна ничего рисовать (например, слой без текстуры).
 */
public final class NoopVertexConsumer implements VertexConsumer {

    public static final NoopVertexConsumer INSTANCE = new NoopVertexConsumer();

    private NoopVertexConsumer() {
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        return this;
    }

    @Override
    public VertexConsumer color(int argb) {
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return this;
    }

    @Override
    public VertexConsumer lineWidth(float width) {
        return this;
    }
}
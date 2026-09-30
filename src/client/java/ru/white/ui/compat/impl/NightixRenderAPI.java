package ru.white.ui.compat.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.white.ui.compat.RenderAPI;
import ru.white.utils.render.Draw;
import ru.white.utils.render.Scissor;

public final class NightixRenderAPI implements RenderAPI {

    @Override
    public void rect(float x, float y, float w, float h, int color) {
        Draw.rect(x, y, w, h, color);
    }

    @Override
    public void rect(float x, float y, float w, float h, float radius, int color) {
        Draw.rect(x, y, w, h, color, radius);
    }

    @Override
    public void rect(float x, float y, float w, float h,
                     float tl, float tr, float br, float bl, int color) {
        Draw.rect(x, y, w, h, color, tl, tr, br, bl);
    }

    @Override
    public void gradientRect(float x, float y, float w, float h,
                             int tlC, int trC, int brC, int blC, float radius) {
        Draw.gradientRect(x, y, w, h,
                new int[]{tlC, trC, brC, blC}, radius);
    }

    @Override
    public void gradientRect(float x, float y, float w, float h,
                             int tlC, int trC, int brC, int blC,
                             float tl, float tr, float br, float bl) {
        Draw.gradientRect(x, y, w, h,
                new int[]{tlC, trC, brC, blC}, tl, tr, br, bl);
    }

    @Override
    public void outline(float x, float y, float w, float h,
                        float thickness, int color) {
        Draw.outline(x, y, w, h, thickness, color);
    }

    @Override
    public void outline(float x, float y, float w, float h,
                        float thickness, int color, float radius) {
        Draw.outline(x, y, w, h, thickness, color, radius);
    }

    @Override
    public void outline(float x, float y, float w, float h,
                        float thickness, int color,
                        float tl, float tr, float br, float bl) {
        Draw.outline(x, y, w, h, thickness, color, tl, tr, br, bl);
    }

    @Override
    public void gradientOutline(float x, float y, float w, float h,
                                float thickness,
                                int tlC, int trC, int brC, int blC,
                                float radius) {
        Draw.gradientOutline(x, y, w, h, thickness, tlC, trC, brC, blC, radius);
    }

    @Override
    public void gradientOutline(float x, float y, float w, float h,
                                float thickness,
                                int tlC, int trC, int brC, int blC,
                                float tl, float tr, float br, float bl) {
        Draw.gradientOutline(x, y, w, h, thickness, tlC, trC, brC, blC, tl, tr, br, bl);
    }

    @Override
    public void blur(float x, float y, float w, float h, float radius, float alpha) {
        Draw.blur(x, y, w, h, alpha, radius, 0);
    }

    @Override
    public void blur(float x, float y, float w, float h, float radius,
                     float tl, float tr, float br, float bl) {
        Draw.blur(x, y, w, h, 1.0F, tl, tr, br, bl, 0);
    }

    @Override
    public void texture(Identifier id, float x, float y, float w, float h, int color) {
        Draw.texture(id, x, y, w, h, color);
    }

    @Override
    public void texture(Identifier id, float x, float y, float w, float h,
                        float smoothness, float radius, int color) {
        Draw.texture(id, x, y, w, h, smoothness, radius, color);
    }

    @Override
    public void scissor(float x, float y, float w, float h) {
        Scissor.enable(x, y, w, h);
    }

    @Override
    public void scissorEnd() {
        Scissor.disable();
    }

    @Override
    public void beginOverlay() {
        Draw.flush();
    }

    @Override
    public void endOverlay() {
        Draw.flush();
    }

    @Override
    public void flush() {
        Draw.flush();
    }
}

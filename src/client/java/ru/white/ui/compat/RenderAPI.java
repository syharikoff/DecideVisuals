package ru.white.ui.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Абстрактный рендер API — покрывает все вызовы, что делает GUI.
 */
public interface RenderAPI {

    // ── Прямоугольники ────────────────────────────────────────────────
    void rect(float x, float y, float w, float h, int color);
    void rect(float x, float y, float w, float h, float radius, int color);
    void rect(float x, float y, float w, float h,
              float tl, float tr, float br, float bl, int color);

    // ── Градиентные прямоугольники ────────────────────────────────────
    void gradientRect(float x, float y, float w, float h,
                      int tlC, int trC, int brC, int blC, float radius);
    void gradientRect(float x, float y, float w, float h,
                      int tlC, int trC, int brC, int blC,
                      float tl, float tr, float br, float bl);

    // ── Обводки ──────────────────────────────────────────────────────
    void outline(float x, float y, float w, float h, float thickness, int color);
    void outline(float x, float y, float w, float h, float thickness, int color, float radius);
    void outline(float x, float y, float w, float h, float thickness, int color,
                 float tl, float tr, float br, float bl);

    // ── Градиентные обводки ──────────────────────────────────────────
    void gradientOutline(float x, float y, float w, float h, float thickness,
                         int tlC, int trC, int brC, int blC, float radius);
    void gradientOutline(float x, float y, float w, float h, float thickness,
                         int tlC, int trC, int brC, int blC,
                         float tl, float tr, float br, float bl);

    // ── Блюр ─────────────────────────────────────────────────────────
    void blur(float x, float y, float w, float h, float radius, float alpha);
    void blur(float x, float y, float w, float h, float radius,
              float tl, float tr, float br, float bl);

    // ── Текстуры ─────────────────────────────────────────────────────
    void texture(Identifier id, float x, float y, float w, float h, int color);
    void texture(Identifier id, float x, float y, float w, float h,
                 float smoothness, float radius, int color);

    // ── Scissor ──────────────────────────────────────────────────────
    void scissor(float x, float y, float w, float h);
    void scissorEnd();

    // ── Frame ────────────────────────────────────────────────────────
    void beginOverlay();
    void endOverlay();
    void flush();
}

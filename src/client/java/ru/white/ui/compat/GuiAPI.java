package ru.white.ui.compat;

import net.minecraft.client.gui.DrawContext;

/**
 * Абстрактный API для GUI, позволяющий писать код один раз
 * и использовать в разных проектах (lastvisuals, DecideVisuals и т.д.).
 *
 * Каждый проект реализует этот интерфейс, делегируя вызовы
 * своему собственному рендер-движку.
 */
public interface GuiAPI {

    // ═══════════════════════════════════════════════════════════════════
    // Прямоугольники
    // ═══════════════════════════════════════════════════════════════════

    void rect(float x, float y, float w, float h, int color);
    void rect(float x, float y, float w, float h, float radius, int color);
    void rect(float x, float y, float w, float h, float tl, float tr, float br, float bl, int color);
    void rect(float x, float y, float w, float h, float tl, float tr, float br, float bl,
              int tlC, int trC, int brC, int blC);
    void rect(float x, float y, float w, float h, float radius,
              int tlC, int trC, int brC, int blC);

    // ═══════════════════════════════════════════════════════════════════
    // Обводки
    // ═══════════════════════════════════════════════════════════════════

    void outline(float x, float y, float w, float h, float radius, float thickness, int color);
    void outline(float x, float y, float w, float h, float tl, float tr, float br, float bl,
                 float thickness, int color);
    void outline(float x, float y, float w, float h, float radius, float thickness,
                 int tlC, int trC, int brC, int blC);

    // ═══════════════════════════════════════════════════════════════════
    // Текст (обычный)
    // ═══════════════════════════════════════════════════════════════════

    void text(String font, String text, float x, float y, float size, int color);
    void text(String font, String text, float x, float y, float size,
              int tlC, int trC, int brC, int blC);
    void text(String font, String text, float x, float y, float size, int color,
              float fadeL, float fadeR, float fadeW);
    float textWidth(String font, String text, float size);

    // ═══════════════════════════════════════════════════════════════════
    // MSDF текст
    // ═══════════════════════════════════════════════════════════════════

    void msdfText(String font, String text, float x, float y, float size, int color);
    void msdfText(String font, String text, float x, float y, float size,
                  int tlC, int trC, int brC, int blC);
    void msdfText(String font, String text, float x, float y, float size, int color,
                  float fadeL, float fadeR, float fadeW);
    float msdfWidth(String font, String text, float size);
    float[] msdfBounds(String font, String text, float size);
    void msdfShimmer(String font, String text, float x, float y, float size,
                     int color, float offset, float width, float strength, float alpha);

    // ═══════════════════════════════════════════════════════════════════
    // Изображения
    // ═══════════════════════════════════════════════════════════════════

    void image(String path, float x, float y, float w, float h, float radius, int color);
    void image(String path, float x, float y, float w, float h, int... colors);
    boolean imageReady(String path);

    // ═══════════════════════════════════════════════════════════════════
    // Scissor (обрезка)
    // ═══════════════════════════════════════════════════════════════════

    void pushScissor(DrawContext ctx, float x, float y, float w, float h);
    void popScissor(DrawContext ctx);

    // ═══════════════════════════════════════════════════════════════════
    // Размытие
    // ═══════════════════════════════════════════════════════════════════

    void blur(float x, float y, float w, float h, float radius);
    void blur(float x, float y, float w, float h, float radius, float strength);

    // ═══════════════════════════════════════════════════════════════════
    // Линии
    // ═══════════════════════════════════════════════════════════════════

    void line(float x1, float y1, float x2, float y2, float width, int color);

    // ═══════════════════════════════════════════════════════════════════
    // Круги
    // ═══════════════════════════════════════════════════════════════════

    void circle(float x, float y, float radius, int color);
    void circleOutline(float x, float y, float radius, float thickness, int color);

    // ═══════════════════════════════════════════════════════════════════
    // Жизненный цикл
    // ═══════════════════════════════════════════════════════════════════

    void beginFrame(DrawContext ctx);
    void flush();
}

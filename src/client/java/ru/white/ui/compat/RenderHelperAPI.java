package ru.white.ui.compat;

/**
 * Абстрактный API для вспомогательных функций рендеринга (панели, клиентский прямоугольник).
 */
public interface RenderHelperAPI {

    void drawPanelBg(float x, float y, float w, float h,
                     float tl, float tr, float br, float bl, float alpha);
    void drawPanelBg(float x, float y, float w, float h, float alpha);
    void drawDropBackground(float x, float y, float w, float h, float alpha);
    void drawClientRect(float x, float y, float w, float h,
                        float radius, float opacity, float extraRadius);
    void drawClientRectFixedRadius(float x, float y, float w, float h,
                                   float radius, float opacity, float extraRadius);
    float effectiveCornerRadius(float base, float extra, float max);
    float cornerEdgeInset(float base, float extra);
}

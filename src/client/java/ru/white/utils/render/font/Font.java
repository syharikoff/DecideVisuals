package ru.white.utils.render.font;


import net.minecraft.client.util.math.MatrixStack;
import ru.white.Client;
import ru.white.utils.colors.ColorFormatting;
import ru.white.utils.colors.ColorUtil;

public class Font {
    private final String name;

    public Font(String name) {
        this.name = name;
    }

    // ВАЖНО: никаких flushAll перед текстом — порядок слоёв гарантирует DrawBatcher
    // (текст всегда поверх заливок/обводок/текстур своего цикла). Раньше каждый
    // вызов draw* сбрасывал ВСЕ батчи и создавал отдельный RenderPass на строку.

    public void draw(String text, float x, float y, float size, int color) {
        Client.get().render2D().getFontRenderer().drawText(name, text, x, y, size, color);
    }

    public void drawCentered(String text, float x, float y, float size, int color) {
        Client.get().render2D().getFontRenderer().drawCenteredText(name, text, x, y, size, color);
    }

    public void drawCenterGradient(String text, float x, float y, float size, int color,int color2) {
        drawGradient(text, x - getWidth(text, size) / 2, y, color, color2, size);
    }

    public void drawGradient(String text, float x, float y, int color1, int color2, float size) {
        drawGradient(text, x, y, color1, color2, size, 8);
    }

    public void drawGradient(String text, float x, float y, int color1, int color2, float size, int speed) {
        StringBuilder sb = new StringBuilder(text.length() * 12);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            sb.append(ColorFormatting.getColor(ColorUtil.fade(speed, i * speed, color1, color2))).append(ch);
        }
        draw(sb.toString(), x, y, size, -1);
    }

    public void drawGradientB(String text, float x, float y, int color1, int color2, float size) {
        if (text == null || text.isEmpty()) {
            return;
        }

        int len = text.length();
        if (len == 1) {
            draw(ColorFormatting.getColor(color1) + text, x, y, size, -1);
            return;
        }

        int a1 = (color1 >> 24) & 0xFF, r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF, r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        float inv = 1f / (len - 1);
        StringBuilder sb = new StringBuilder(len * 12);

        for (int i = 0; i < len; i++) {
            float t = i * inv;
            t = 0.5f - 0.5f * (float) Math.cos(Math.PI * t);

            int a = a1 + Math.round((a2 - a1) * t);
            int r = r1 + Math.round((r2 - r1) * t);
            int g = g1 + Math.round((g2 - g1) * t);
            int b = b1 + Math.round((b2 - b1) * t);
            int argb = (a << 24) | (r << 16) | (g << 8) | b;

            sb.append(ColorFormatting.getColor(argb)).append(text.charAt(i));
        }

        draw(sb.toString(), x, y, size, -1);
    }


    public void drawGRS(CharSequence text, double x, double y, int c1, int c2, float size) {
        if (text == null) return;
        final int len = text.length();
        if (len == 0) return;

        float xOff = 0f;

        // Предотвращаем деление на ноль и лишние вычисления
        final boolean single = (len == 1);
        final float invDen = single ? 0f : 1f / (len - 1);

        // Извлекаем компоненты один раз
        final int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        final int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;

        // Маленький буфер вместо String.valueOf для каждого символа
        final StringBuilder sb = new StringBuilder(1);
        sb.append('\0');

        for (int i = 0; i < len; i++) {
            final char ch = text.charAt(i);
            final float t = single ? 0f : (i * invDen);

            // Линейная интерполяция компонент (целочисленно, без Math*)
            final int a = a1 + Math.round((a2 - a1) * t);
            final int r = r1 + Math.round((r2 - r1) * t);
            final int g = g1 + Math.round((g2 - g1) * t);
            final int b = b1 + Math.round((b2 - b1) * t);
            final int argb = (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);

            sb.setCharAt(0, ch);
            String chunk = ColorFormatting.getColor(argb) + sb;
            draw(chunk, (float) (x + xOff), (float) y, size, -1);
            xOff += getWidth(chunk, size);
        }
    }

    public float getWidth(String text, float size) {
        return Client.get().render2D().getFontRenderer().getTextWidth(name, text, size);
    }
    public void drawFadingText(String text, float x, float y, float maxWidth, int color,float size) {
        Client.get().render2D().getFontRenderer().drawTextFading(name, text, x, y, size, maxWidth, color);
    }

    /** Затухание слева; при переполнении показывает конец строки (зеркало {@link #drawFadingText}). */
    public void drawFadingTextReverse(String text, float x, float y, float maxWidth, int color, float size) {
        Client.get().render2D().getFontRenderer().drawTextFadingReverse(name, text, x, y, size, maxWidth, color);
    }


    public void drawWrappedText(String text, float x, float y, float maxWidth, int color,float size) {
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        float currentY = y;

        for (String word : words) {
            // Проверяем ширину текущей строки с новым словом
            String potentialLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            float lineWidth = getWidth(potentialLine,size);

            if (lineWidth > maxWidth) {
                // Если не влезает, рисуем накопленную строку и переходим на новую
                draw( currentLine.toString(), x, currentY,size, color);
                currentLine = new StringBuilder(word);
                currentY += this.getHeight(size) ; // getHeight() возвращает высоту шрифта + отступ
            } else {
                currentLine = new StringBuilder(potentialLine);
            }
        }

        // Рисуем последний остаток текста
        if (currentLine.length() > 0) {
            draw( currentLine.toString(), x, currentY,size, color);
        }
    }

    /** Высота, которую займёт текст при отрисовке через {@link #drawWrappedText} с теми же параметрами. */
    public float getWrappedHeight(String text, float maxWidth, float size) {
        if (text == null || text.isEmpty()) return 0F;
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        int lines = 1;

        for (String word : words) {
            String potentialLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (getWidth(potentialLine, size) > maxWidth) {
                currentLine = new StringBuilder(word);
                lines++;
            } else {
                currentLine = new StringBuilder(potentialLine);
            }
        }

        return lines * getHeight(size);
    }

    /** Выравнивание по правому краю; левый край затухает при наезде на {@code fadeStartX}. */
    public void drawRightAlignFadeLeft(String text, float rightX, float y, float fadeStartX, int color, float size) {
        Client.get().render2D().getFontRenderer().drawTextRightAlignFadeLeft(name, text, rightX, y, size, fadeStartX, color);
    }

    public float getHeight(float size) {
        return Client.get().render2D().getFontRenderer().getLineHeight(name, size);
    }

    public String getName() {
        return name;
    }
}
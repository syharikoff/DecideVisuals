package ru.white.utils.render;

import ru.white.Client;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.colors.ColorUtil;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.awt.*;

/**
 * Старый фасад рендера. Оставлен для совместимости — вся логика в {@link Draw}.
 * В новом коде используй {@code Draw.rect / Draw.blur / Draw.outline / ...} напрямую.
 */
@UtilityClass
public class RenderUtil implements IMinecraft {

    @UtilityClass
    public static class Blur {
        // alpha: 0.0–1.0 controls fade in/out of the blur rect
        public static void blur(float x, float y, float width, float height, float alpha, int tintColor) {
            Draw.blur(x, y, width, height, alpha, tintColor);
        }

        public static void blur(float x, float y, float width, float height, float alpha,
                                float cornerRadius, int tintColor) {
            Draw.blur(x, y, width, height, alpha, cornerRadius, tintColor);
        }

        public static void blur(float x, float y, float width, float height, float alpha,
                                float topLeft, float topRight, float bottomRight, float bottomLeft, int tintColor) {
            Draw.blur(x, y, width, height, alpha, topLeft, topRight, bottomRight, bottomLeft, tintColor);
        }

        public static void glass(float x, float y, float width, float height,
                                 float alpha, float radius, int tintColor,
                                 float distortion, float waveSize, float edgeLight, float shine) {
            Draw.blur(x, y, width, height, alpha, radius, tintColor);
        }

        public static void glass(float x, float y, float width, float height,
                                 float alpha, float radius,
                                 float distortion, float waveSize) {
            Draw.blur(x, y, width, height, alpha, radius, 0);
        }

        public static void glass(float x, float y, float width, float height,
                                 float alpha, float topLeft, float topRight, float bottomRight, float bottomLeft,
                                 int tintColor, float distortion, float waveSize, float edgeLight, float shine) {
            Draw.glass(x, y, width, height, alpha, topLeft, topRight, bottomRight, bottomLeft, tintColor);
        }
    }

    @UtilityClass
    public static class Images {

        public static Identifier urlIdentifier(String url) {
            return UrlImageTexture.getIdentifier(url);
        }

        public static boolean isUrlLoaded(String url) {
            return UrlImageTexture.get(url).isLoaded();
        }

        public static void url(String url, float x, float y, float width, float height, int color) {
            url(url, x, y, width, height, 1f, 0f, color);
        }

        public static void url(String url, float x, float y, float width, float height, float smoothness, int color) {
            url(url, x, y, width, height, smoothness, 0f, color);
        }

        public static void url(String url, float x, float y, float width, float height,
                               float smoothness, float radius, int color) {
            Identifier id = UrlImageTexture.getIdentifier(url);
            if (id == null) return;
            texture(id, x, y, width, height, smoothness, radius, color);
        }

        public static void texture(Identifier id, float x, float y, float width, float height, int color) {
            Draw.texture(id, x, y, width, height, 0, 0, 1, 1, color, 1f, 0f);
        }

        public static void texture(Identifier id, float x, float y, float width, float height, float smoothness, int color) {
            Draw.texture(id, x, y, width, height, 0, 0, 1, 1, color, smoothness, 0f);
        }

        public static void texture(Identifier id, float x, float y, float width, float height, float smoothness, float radius, int color) {
            Draw.texture(id, x, y, width, height, 0, 0, 1, 1, color, smoothness, radius);
        }

        public static void texture(Identifier id, float x, float y, float width, float height,
                                   float u0, float v0, float u1, float v1, int color) {
            Draw.texture(id, x, y, width, height, u0, v0, u1, v1, color, 1f, 0f);
        }

        public static void texture(Identifier id, float x, float y, float width, float height,
                                   float u0, float v0, float u1, float v1, int color, float radius) {
            Draw.texture(id, x, y, width, height, u0, v0, u1, v1, color, 1f, radius);
        }

        public static void texture(Identifier id, float x, float y, float width, float height,
                                   float u0, float v0, float u1, float v1, int color, float smoothness, float radius) {
            Draw.texture(id, x, y, width, height, u0, v0, u1, v1, color, smoothness, radius);
        }

        public static void drawTexture(DrawContext context, Identifier id,
                                       float x, float y, float width, float height,
                                       float u, float v, float regionWidth, float regionHeight,
                                       float textureWidth, float textureHeight,
                                       int color) {
            drawTexture(context, id, x, y, width, height, u, v, regionWidth, regionHeight,
                    textureWidth, textureHeight, color, 0f);
        }

        public static void drawTexture(DrawContext context, Identifier id,
                                       float x, float y, float width, float height,
                                       float u, float v, float regionWidth, float regionHeight,
                                       float textureWidth, float textureHeight,
                                       int color, float radius) {
            float u0 = u / textureWidth;
            float v0 = v / textureHeight;
            float u1 = (u + regionWidth) / textureWidth;
            float v1 = (v + regionHeight) / textureHeight;

            Draw.texture(id, x, y, width, height, u0, v0, u1, v1, color, 1f, radius);
        }
    }

    @UtilityClass
    public static class Render2D {

        public static void outline(float x, float y, float width, float height, float thickness, int color) {
            Draw.outline(x, y, width, height, thickness, color);
        }

        public static void outline(float x, float y, float width, float height, float thickness, int color, float radius) {
            Draw.outline(x, y, width, height, thickness, color, radius);
        }

        public static void outline(float x, float y, float width, float height, float thickness, int color,
                                   float topLeft, float topRight, float bottomRight, float bottomLeft) {
            Draw.outline(x, y, width, height, thickness, color, topLeft, topRight, bottomRight, bottomLeft);
        }

        public static void glassOutline(float x, float y, float width, float height,
                                        float thickness, float radius, float alpha, float shine) {
            Draw.glassOutline(x, y, width, height, thickness, radius, alpha, shine);
        }

        public static void glassOutline(float x, float y, float width, float height,
                                        float thickness, float topLeft, float topRight,
                                        float bottomRight, float bottomLeft, float alpha, float shine) {
            Draw.glassOutline(x, y, width, height, thickness, topLeft, topRight, bottomRight, bottomLeft, alpha, shine);
        }

        public static void rect(float x, float y, float width, float height, int color) {
            Draw.rect(x, y, width, height, color);
        }

        public static void rect(float x, float y, float width, float height, int color, float radius) {
            Draw.rect(x, y, width, height, color, radius);
        }

        public static void rect(float x, float y, float width, float height, int color,
                                float topLeft, float topRight, float bottomRight, float bottomLeft) {
            Draw.rect(x, y, width, height, color, topLeft, topRight, bottomRight, bottomLeft);
        }

        public static void glow(float x, float y, float width, float height,
                                int color, float radius, float glowSize, float strength) {
           // Draw.glow(x, y, width, height, color, radius, glowSize, strength);
        }

        public static void glow(float x, float y, float width, float height,
                                int color, float radius, float glowSize, float strength, float softness) {
        //    Draw.glow(x, y, width, height, color, radius, glowSize, strength, softness);
        }

        public static void glow(float x, float y, float width, float height,
                                int color, float topLeft, float topRight, float bottomRight, float bottomLeft,
                                float glowSize, float strength, float softness) {
          //  Draw.glow(x, y, width, height, color, topLeft, topRight, bottomRight, bottomLeft, glowSize, strength, softness);
        }

        public static void clientRect(float x, float y, float width, float height,
                                      float alphaPC, float radius) {
            int color = ColorUtil.getColor(255, alphaPC);

            gradientRect(
                    x, y, width, height,
                    new int[]{
                            new Color(24, 25, 31, ColorUtil.alpha(color)).getRGB(), // верх
                            new Color(30, 32, 37, ColorUtil.alpha(color)).getRGB(),
                            new Color(14, 15, 21, ColorUtil.alpha(color)).getRGB(),
                            new Color(13, 14, 20, ColorUtil.alpha(color)).getRGB()
                    },
                    radius
            );
        }

        public static void gradientRect(float x, float y, float width, float height,
                                        int[] colors, float radius) {
            Draw.gradientRect(x, y, width, height, colors, radius);
        }

        public static void gradientRect(float x, float y, float width, float height,
                                        int[] colors, float topLeft, float topRight,
                                        float bottomRight, float bottomLeft) {
            Draw.gradientRect(x, y, width, height, colors, topLeft, topRight, bottomRight, bottomLeft);
        }

        public static void roundedCircleProgress(DrawContext context, float centerX, float centerY,
                                                 float radius, float thickness, float progress,
                                                 int backgroundColor, int startColor, int endColor) {
            progress = Math.max(0F, Math.min(1F, progress));
            if (radius <= 0F || thickness <= 0F) return;

            var render2D = Client.get().render2D();
            render2D.flushAll();
            render2D.getCircleProgressPipeline()
                    .draw(centerX, centerY, radius, thickness, 1F, backgroundColor);
            if (progress > 0F) {
                render2D.getCircleProgressPipeline()
                        .draw(centerX, centerY, radius, thickness, progress,
                                ColorUtil.overCol(startColor, endColor, 0.65F));
            }
            render2D.getCircleProgressPipeline().flush();
        }
    }

    @UtilityClass
    public static class Render3D {

        public final Matrix4f lastProjMat = new Matrix4f();
        public final Matrix4f lastModMat = new Matrix4f();
        public final Matrix4f lastWorldSpaceMatrix = new Matrix4f();

    }

    @UtilityClass
    public static class RenderPlayer3DMatrix {

    }
}

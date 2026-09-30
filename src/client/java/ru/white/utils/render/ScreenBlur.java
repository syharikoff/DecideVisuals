package ru.white.utils.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import ru.white.theme.ThemeColor;

/**
 * Захват и блюр текущего кадра (Kawase). Один захват на кадр, дальше все
 * блюр-ректы сэмплят готовую текстуру.
 *
 * Kawase-цепочка — это ~2*N+1 рендер-пассов, поэтому ежекадровый prepare()
 * из HUD пропускается, если в предыдущем кадре блюр никто не рисовал
 * (BlurPipeline отмечает использование через markUsed()).
 */
public class ScreenBlur {

    private static final KawaseBlurPipeline pipeline = new KawaseBlurPipeline();

    private static GpuTextureView blurredView = null;
    private static int cachedWidth = 0;
    private static int cachedHeight = 0;

    // Кадровая когерентность: рисовали ли блюр в этом/прошлом кадре
    private static boolean usedThisFrame = false;
    private static boolean usedLastFrame = true; // первый кадр захватываем всегда

    /** BlurPipeline зовёт это при каждом запросе блюра. */
    public static void markUsed() {
        usedThisFrame = true;
    }

    /**
     * Ежекадровый захват из HUD (InGameHudMixin), строго один раз за кадр.
     * Если в прошлом кадре блюр не использовался — вся Kawase-цепочка пропускается.
     */
    public static void frame() {
        boolean wanted = usedThisFrame || usedLastFrame;
        usedLastFrame = usedThisFrame;
        usedThisFrame = false;

        if (!wanted) {
            blurredView = null;
            return;
        }

        int strength = ThemeColor.getBlur();
        doCapture(3, 2);
    }

    /** Безусловный захват с силой из темы (экраны: пере-захват посреди рендера). */
    public static void capture() {
        usedLastFrame = true;
        int strength = ThemeColor.getBlur();
        doCapture(3, 2);
    }

    /** Безусловный захват с заданной силой (экраны знают, что будут рисовать блюр). */
    public static void capture(int size) {
        usedLastFrame = true;
        doCapture(size, size);
    }

    private static void doCapture(int iterations, float offset) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        Framebuffer fb = mc.getFramebuffer();
        if (fb == null) return;

        // Захватываем кадр — всё накопленное должно быть уже отрисовано
        DrawBatcher.flushPending();

        try {
            var colorAttachment = fb.getColorAttachment();
            var colorAttachmentView = fb.getColorAttachmentView();
            if (colorAttachment == null || colorAttachmentView == null) {
                blurredView = null;
                return;
            }

            int w = fb.textureWidth;
            int h = fb.textureHeight;
            if (w <= 0 || h <= 0) {
                blurredView = null;
                return;
            }

            blurredView = pipeline.blur(colorAttachment, colorAttachmentView, w, h, iterations, offset);
            if (blurredView != null) {
                cachedWidth = w;
                cachedHeight = h;
            }
        } catch (Exception e) {
            blurredView = null;
        }
    }

    public static boolean isReady() {
        return blurredView != null;
    }

    public static GpuTextureView getBlurredView() {
        return blurredView;
    }

    public static int getCachedWidth() {
        return cachedWidth;
    }

    public static int getCachedHeight() {
        return cachedHeight;
    }

    public static void invalidate() {
        blurredView = null;
    }

    public static void close() {
        blurredView = null;
        pipeline.close();
    }
}

package ru.white.utils.render;

import ru.white.utils.render.font.FontRenderer;
import ru.white.utils.render.font.Fonts;
import net.minecraft.client.texture.Sprite;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import static ru.white.utils.render.RenderUtil.Images.texture;

public class Render2D {

    private final FontRenderer fontRenderer;
    private final RectPipeline rectPipeline;
    private final CircleProgressPipeline circleProgressPipeline;

    private final OutlinePipeline outlinePipeline;
    private final TexturePipeline texturePipeline;
    private final BlurPipeline blurPipeline;

    public Render2D() {
        this.fontRenderer = new FontRenderer();
        this.rectPipeline = new RectPipeline();
        this.circleProgressPipeline = new CircleProgressPipeline();
        this.outlinePipeline = new OutlinePipeline();
        this.blurPipeline = new BlurPipeline();
        this.texturePipeline = new TexturePipeline();
    }

    private boolean fontsLoaded = false;

    private void ensureFontsLoaded() {
        if (fontsLoaded) return;
        fontsLoaded = true;
        fontRenderer.loadAllFonts(Fonts.getRegistry());
    }

    public TexturePipeline getTexturePipeline() {
        return texturePipeline;
    }

    public BlurPipeline getBlurPipeline() {
        return blurPipeline;
    }

    public FontRenderer getFontRenderer() {
        ensureFontsLoaded();
        return fontRenderer;
    }

    public void flushAll() {
        DrawBatcher.flushPending();
        blurPipeline.flush();
        rectPipeline.flush();
        circleProgressPipeline.flush();
        fontRenderer.flush();
    }

    public void close() {
        rectPipeline.close();
        circleProgressPipeline.close();
        fontRenderer.close();
        texturePipeline.close();
        blurPipeline.close();
        outlinePipeline.close();
        ScreenBlur.close();
    }

    public OutlinePipeline getOutlinePipeline() {
        return outlinePipeline;
    }

    public RectPipeline getRectPipeline() {
        return rectPipeline;
    }

    public CircleProgressPipeline getCircleProgressPipeline() {
        return circleProgressPipeline;
    }

    private static boolean inOverlayMode = false;
    private static boolean savedDepthTest = false;
    private static boolean savedDepthMask = false;
    private static boolean savedBlend = false;

    public static void beginOverlay() {
        inOverlayMode = true;

        // HUD — контролируемая зона: включаем батчинг 2D-примитивов
        DrawBatcher.setEnabled(true);

        savedDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        savedBlend = GL11.glIsEnabled(GL11.GL_BLEND);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void endOverlay() {
        // Сбрасываем недорисованные батчи и выключаем батчинг (экраны рисуют немедленно)
        DrawBatcher.setEnabled(false);

        if (savedDepthMask) {
            GL11.glDepthMask(true);
        }
        if (savedDepthTest) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        if (!savedBlend) {
            GL11.glDisable(GL11.GL_BLEND);
        }

        inOverlayMode = false;
    }

    public static void drawSprite(Sprite sprite, float x, float y, float width, float height, int color, boolean pixelPerfect) {
        if (sprite == null || width == 0 || height == 0) return;

        float smoothness = pixelPerfect ? 1f : 0f;
        texture(sprite.getAtlasId(), x, y, width, height,
                sprite.getMinU(), sprite.getMinV(),
                sprite.getMaxU(), sprite.getMaxV(),
                color, smoothness, 0f);
    }



    }

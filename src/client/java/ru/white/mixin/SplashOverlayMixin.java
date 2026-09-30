package ru.white.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.Render2D;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.ScreenBlur;
import ru.white.utils.render.font.Fonts;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;
    @Shadow @Final private boolean reloading;
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;
    @Shadow private long reloadStartTime;

    @Unique private static final int DECIDEVISUALS_BG = 0x0A0E14;
    @Unique private static final Identifier MENU_BG = Identifier.of("client", "textures/frame/menu.png");
    @Unique private long decidevisualsStart = -1L;

    @Unique
    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0xFFFFFF) | (alpha << 24);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void decidevisualsRender(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        int i = context.getScaledWindowWidth();
        int j = context.getScaledWindowHeight();
        long l = Util.getMeasuringTimeMs();

        if (decidevisualsStart == -1L) decidevisualsStart = l;
        if (reloading && reloadStartTime == -1L) reloadStartTime = l;

        float f = reloadCompleteTime > -1L ? (float) (l - reloadCompleteTime) / 1000.0F : -1.0F;
        float g = reloadStartTime > -1L ? (float) (l - reloadStartTime) / 500.0F : -1.0F;

        float alpha;
        if (f >= 1.0F) {

            if (client.currentScreen != null) {
                client.currentScreen.renderWithTooltip(context, 0, 0, deltaTicks);
            } else {
                client.inGameHud.renderDeferredSubtitles();
            }
            context.createNewRootLayer();
            alpha = 1.0F - MathHelper.clamp(f - 1.0F, 0.0F, 1.0F);
        } else if (reloading) {

            if (client.currentScreen != null && g < 1.0F) {
                client.currentScreen.renderWithTooltip(context, mouseX, mouseY, deltaTicks);
            } else {
                client.inGameHud.renderDeferredSubtitles();
            }
            context.createNewRootLayer();
            alpha = MathHelper.clamp(g, 0.0F, 1.0F);
        } else {

            RenderSystem.getDevice().createCommandEncoder()
                    .clearColorTexture(client.getFramebuffer().getColorAttachment(), 0xFF000000 | DECIDEVISUALS_BG);
            alpha = 1.0F;
        }


        float rp = reload.getProgress();
        progress = MathHelper.clamp(progress * 0.95F + rp * 0.050000012F, 0.0F, 1.0F);

        boolean drawn = false;
        try {
            drawn = drawDecideVisuals(context, alpha, l);
        } catch (Throwable ignored) {
            drawn = false;
        }
        if (!drawn) {
            drawFallback(context, i, j, alpha);
        }

        if (f >= 2.0F) {
            client.setOverlay(null);
        }

        ci.cancel();
    }

    @Unique
    private boolean drawDecideVisuals(DrawContext context, float fade, long now) {
        double scaleFactor = client.getWindow().getScaleFactor();
        if (scaleFactor <= 0) return false;

        float scaleFix = 2.0F / (float) scaleFactor;
        int sw = (int) (client.getWindow().getScaledWidth() / scaleFix);
        int sh = (int) (client.getWindow().getScaledHeight() / scaleFix);
        if (sw <= 0 || sh <= 0) return false;

        float intro = MathHelper.clamp((now - decidevisualsStart) / 600.0F, 0.0F, 1.0F);
        float ease = 1.0F - (1.0F - intro) * (1.0F - intro) * (1.0F - intro);
        float a = MathHelper.clamp(fade * ease, 0.0F, 1.0F);
        if (a <= 0.01F) return true;

        float t = now / 1000.0F;
        float breathe = 1.0F + 0.03F * (float) Math.sin(t * 2.0F);
        float floatY = 2.0F * (float) Math.sin(t * 1.3F);
        float rise = (1.0F - ease) * 14.0F;

        context.getMatrices().pushMatrix();
        Render2D.beginOverlay();

        RenderUtil.Render2D.rect(0, 0, sw, sh, ColorUtil.getColor(0, a));
        RenderUtil.Images.texture(MENU_BG, 0, 0, sw, sh, ColorUtil.getColor(255, a));
        RenderUtil.Render2D.rect(0, 0, sw, sh, ColorUtil.getColor(0, a * 0.20F));
        ScreenBlur.capture(2);

        float cx = sw / 2.0F;
        float cy = sh / 2.0F - 18.0F + floatY + rise;


        Fonts.sf_medium.drawCentered("a", cx + 0.25F, cy + 0.25F, 40F * breathe, ColorUtil.getColor(138, 161, 186, a * 0.9F));
        Fonts.sf_medium.drawCentered("a", cx, cy, 39F * breathe, ColorUtil.getColor(175, 199, 230, a));

        Fonts.sf_bold.drawCentered("DecideVisuals", cx, cy + 34F, 24F, ColorUtil.getColor(255, a));


        int dots = (int) ((now / 400) % 4);
        String sub = "загрузка ресурсов" + ".".repeat(dots);
        Fonts.sf_regular.drawCentered(sub, cx, cy + 68F, 9F, ColorUtil.getColor(154, 164, 184, a * 0.85F));


        drawBarBranded(sw, sh, a, t);

        Fonts.sf_bold.drawCentered(ru.white.lang.Lang.pick("DecideVisuals · 2026 · Все права защищены", "DecideVisuals · 2026 · All rights reserved"), cx, sh - 22F, 8F, ColorUtil.getColor(255, a * 0.12F));

        Render2D.endOverlay();
        context.getMatrices().popMatrix();
        return true;
    }

    @Unique
    private void drawBarBranded(int sw, int sh, float a, float t) {
        float barW = Math.min(sw * 0.36F, 280F);
        float barH = 4.0F;
        float x = sw / 2.0F - barW / 2.0F;
        float y = sh * 0.74F;


        RenderUtil.Render2D.rect(x, y, barW, barH, ColorUtil.getColor(35, 42, 58, a * 0.85F), barH / 2.0F);

        float fillW = barW * progress;
        if (fillW > 0.5F) {

            RenderUtil.Render2D.rect(x, y, fillW, barH, ColorUtil.getColor(125, 145, 244, a), barH / 2.0F);

            float sweep = (t * 0.6F) % 1.0F;
            float sx = x + sweep * fillW;
            RenderUtil.Render2D.rect(sx - 6F, y, 12F, barH, ColorUtil.getColor(210, 220, 255, a * 0.35F), barH / 2.0F);
        }


        String pct = (int) (progress * 100F) + "%";
        Fonts.sf_bold.draw(pct, x + barW + 8F, y - 2.5F, 8F, ColorUtil.getColor(220, 226, 240, a));
    }


    @Unique
    private void drawFallback(DrawContext context, int width, int height, float alpha) {
        int a = MathHelper.ceil(MathHelper.clamp(alpha, 0F, 1F) * 255F);
        if (a <= 4) return;

        context.fill(0, 0, width, height, withAlpha(DECIDEVISUALS_BG, a));

        TextRenderer tr = client.textRenderer;
        var matrices = context.getMatrices();
        String title = "DECIDEVISUALS";
        float s = 4.0F;
        matrices.pushMatrix();
        matrices.scale(s, s);
        float cx = (width / 2.0F) / s;
        float ty = (height / 2.0F - 26F) / s;
        float half = tr.getWidth(title) / 2.0F;
        context.drawText(tr, title, (int) (cx - half), (int) ty, withAlpha(0x7D91F4, a), false);
        matrices.popMatrix();

        int barW = (int) Math.min(width * 0.5F, 320F);
        int x = width / 2 - barW / 2;
        int y = (int) (height * 0.78F);
        context.fill(x, y, x + barW, y + 4, withAlpha(0x232A3A, (int) (a * 0.85F)));
        int fillW = MathHelper.ceil((barW - 2) * progress);
        context.fill(x + 1, y + 1, x + 1 + fillW, y + 3, withAlpha(0x7D91F4, a));
        String pct = (int) (progress * 100F) + "%";
        context.drawText(tr, pct, x + barW + 8, y - 2, ColorHelper.getArgb(a, 220, 226, 240), false);
    }
}

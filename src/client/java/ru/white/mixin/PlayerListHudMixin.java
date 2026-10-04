package ru.white.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.white.module.impl.render.BetterMinecraft;
import ru.white.utils.render.TabAnimationAccess;

/**
 * Better Minecraft: плавное появление и схлопывание таб-листа (easeOutBack / easeInBack).
 * Реализация та же, что в Kimiko, но без бейджей — они относятся к другой функции Kimiko.
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin implements TabAnimationAccess {

    @Unique
    private static final long TAB_ANIMATION_MS = 300L;

    @Unique
    private long nightix$animationStart;
    @Unique
    private long nightix$animationDuration;
    @Unique
    private float nightix$animationFrom;
    @Unique
    private float nightix$animationTarget;
    @Unique
    private boolean nightix$visible;
    @Unique
    private boolean nightix$scaledForAnimation;

    @Inject(method = "setVisible", at = @At("HEAD"))
    private void nightix$trackVisibility(boolean visible, CallbackInfo ci) {
        if (visible == nightix$visible) return;

        float current = currentScale();
        nightix$visible = visible;
        nightix$animationFrom = current;
        nightix$animationTarget = visible ? 1.0f : 0.0f;
        nightix$animationStart = System.currentTimeMillis();
        nightix$animationDuration = Math.max(1L,
                Math.round(TAB_ANIMATION_MS * Math.abs(nightix$animationTarget - nightix$animationFrom)));
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void nightix$beginTabAnimation(DrawContext context, int windowWidth,
                                           Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        nightix$scaledForAnimation = BetterMinecraft.tabAnimationEnabled();
        if (!nightix$scaledForAnimation) return;

        float scale = Math.max(0.01f, Math.min(1.15f, currentScale()));
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(windowWidth / 2.0f, 10.0f);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-windowWidth / 2.0f, -10.0f);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void nightix$endTabAnimation(DrawContext context, int windowWidth,
                                         Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        if (!nightix$scaledForAnimation) return;
        context.getMatrices().popMatrix();
        nightix$scaledForAnimation = false;
    }

    @Override
    public boolean nightix$shouldRenderClosingTab() {
        return BetterMinecraft.tabAnimationEnabled() && !nightix$visible && currentScale() > 0.01f;
    }

    @Unique
    private float currentScale() {
        long duration = Math.max(1L, nightix$animationDuration);
        float progress = Math.min(1.0f, (System.currentTimeMillis() - nightix$animationStart) / (float) duration);
        float eased = nightix$animationTarget > nightix$animationFrom ? easeOutBack(progress) : easeInBack(progress);
        return nightix$animationFrom + (nightix$animationTarget - nightix$animationFrom) * eased;
    }

    @Unique
    private static float easeOutBack(float value) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float t = value - 1.0f;
        return 1.0f + c3 * t * t * t + c1 * t * t;
    }

    @Unique
    private static float easeInBack(float value) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return c3 * value * value * value - c1 * value * value;
    }
}
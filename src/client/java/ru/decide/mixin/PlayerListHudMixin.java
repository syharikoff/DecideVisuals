package ru.decide.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.decide.module.impl.render.BetterMinecraft;
import ru.decide.utils.render.TabAnimationAccess;

/**
 * Better Minecraft: плавное появление и схлопывание таб-листа (easeOutBack / easeInBack).
 * Реализация та же, что в Kimiko, но без бейджей — они относятся к другой функции Kimiko.
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin implements TabAnimationAccess {

    @Unique
    private static final long TAB_ANIMATION_MS = 300L;

    @Unique
    private long decide$animationStart;
    @Unique
    private long decide$animationDuration;
    @Unique
    private float decide$animationFrom;
    @Unique
    private float decide$animationTarget;
    @Unique
    private boolean decide$visible;
    @Unique
    private boolean decide$scaledForAnimation;

    @Inject(method = "setVisible", at = @At("HEAD"))
    private void decide$trackVisibility(boolean visible, CallbackInfo ci) {
        if (visible == decide$visible) return;

        float current = currentScale();
        decide$visible = visible;
        decide$animationFrom = current;
        decide$animationTarget = visible ? 1.0f : 0.0f;
        decide$animationStart = System.currentTimeMillis();
        decide$animationDuration = Math.max(1L,
                Math.round(TAB_ANIMATION_MS * Math.abs(decide$animationTarget - decide$animationFrom)));
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void decide$beginTabAnimation(DrawContext context, int windowWidth,
                                           Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        decide$scaledForAnimation = BetterMinecraft.tabAnimationEnabled();
        if (!decide$scaledForAnimation) return;

        float scale = Math.max(0.01f, Math.min(1.15f, currentScale()));
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(windowWidth / 2.0f, 10.0f);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-windowWidth / 2.0f, -10.0f);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void decide$endTabAnimation(DrawContext context, int windowWidth,
                                         Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        if (!decide$scaledForAnimation) return;
        context.getMatrices().popMatrix();
        decide$scaledForAnimation = false;
    }

    @Override
    public boolean decide$shouldRenderClosingTab() {
        return BetterMinecraft.tabAnimationEnabled() && !decide$visible && currentScale() > 0.01f;
    }

    @Unique
    private float currentScale() {
        long duration = Math.max(1L, decide$animationDuration);
        float progress = Math.min(1.0f, (System.currentTimeMillis() - decide$animationStart) / (float) duration);
        float eased = decide$animationTarget > decide$animationFrom ? easeOutBack(progress) : easeInBack(progress);
        return decide$animationFrom + (decide$animationTarget - decide$animationFrom) * eased;
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
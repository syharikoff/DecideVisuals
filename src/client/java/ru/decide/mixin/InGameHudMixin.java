package ru.decide.mixin;

import ru.decide.manager.DragComponent;
import ru.decide.manager.event_impl.EventDisplay;
import ru.decide.module.impl.render.CrossHair;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.decide.module.impl.render.BetterMinecraft;
import ru.decide.utils.render.TabAnimationAccess;
import ru.decide.module.impl.render.NoRender;
import ru.decide.utils.render.Render2D;
import ru.decide.utils.render.ScreenBlur;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.DrawContext;

import ru.decide.Client;
import net.minecraft.client.MinecraftClient;

import static ru.decide.utils.annotation.IMinecraft.mc;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

@Shadow @Final
    private PlayerListHud playerListHud;

    @Inject(method = "render", at = @At("TAIL"))
    public void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;


        ScreenBlur.frame();

        Screen screen = mc.currentScreen;

        if (isLoadingScreen(screen)) return;

        context.createNewRootLayer();
        Render2D.beginOverlay();




        context.getMatrices().pushMatrix();




        EventDisplay event = new EventDisplay(context, tickCounter.getTickProgress(false));

        Client.get().componentManager().get(DragComponent.class).post(context.getMatrices());
        event.hook();
        Client.get().render2D().flushAll();

        context.getMatrices().popMatrix();
        Render2D.endOverlay();

    }

    @Inject(
            method = "renderStatusEffectOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRenderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {


        ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void removeVanillaCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen != null) {
                ci.cancel();
                return;
            }
            CrossHair crosshairModule = CrossHair.getInstance();
            if ( crosshairModule.isEnabled()) {
                ci.cancel();
            }
        } catch (Exception e) {

        }
    }
    @Unique
    private boolean isLoadingScreen(Screen screen) {
        if (screen == null) return false;
        String className = screen.getClass().getSimpleName().toLowerCase();
        String fullName = screen.getClass().getName().toLowerCase();
        if (className.contains("loading")) return true;
        if (className.contains("progress")) return true;
        if (className.contains("connecting")) return true;
        if (className.contains("downloading")) return true;
        if (className.contains("terrain")) return true;
        if (className.contains("generating")) return true;
        if (className.contains("saving")) return true;
        if (className.contains("reload")) return true;
        if (className.contains("resource")) return true;
        if (className.contains("pack")) return true;
        if (fullName.contains("mojang")) return true;
        return false;
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderNauseaOverlay(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isEnabled() && noRender.ignoreZalupa.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderScoreboard(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isEnabled() && noRender.ignoreScoreboard.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBossBarHud", at = @At("HEAD"), cancellable = true)
    private void onRenderBossBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isEnabled() && noRender.ignoreBossBar.getValue()) {
            ci.cancel();
        }
    }

    // ================= Better Minecraft =================

    /** Плавное перемещение рамки выбранного слота хотбара. */
    @ModifyArg(
            method = "renderHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1),
            index = 2,
            require = 1)
    private int decide$animateHotbarSelection(int targetX) {
        return BetterMinecraft.animateHotbarSelectionX(targetX);
    }

    /** Насыщение пищи над ванильной строкой голода. */
    @Inject(method = "renderFood", at = @At("RETURN"))
    private void decide$renderSaturation(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo ci) {
        BetterMinecraft.renderSaturation(context, player, top, right);
    }

    /** Поднимает весь HUD на 14 px при открытом чате. */
    @WrapMethod(method = "renderMainHud")
    private void decide$liftHotbarWithChat(DrawContext context, RenderTickCounter tickCounter, Operation<Void> original) {
        float offset = BetterMinecraft.chatHotbarLiftOffset();
        if (offset <= 0.01f) {
            original.call(context, tickCounter);
            return;
        }

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0f, -offset);
        try {
            original.call(context, tickCounter);
} finally {
            context.getMatrices().popMatrix();
        }
    }

    /** Таб-лист продолжает схлопываться после setVisible(false) — дорисовываем его вручную. */
    @Inject(method = "renderPlayerList", at = @At("TAIL"))
    private void decide$renderClosingTabList(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!(playerListHud instanceof TabAnimationAccess tab)) return;
        if (!tab.decide$shouldRenderClosingTab()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.options.playerListKey.isPressed()) return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        playerListHud.render(context, context.getScaledWindowWidth(), scoreboard, objective);
    }
}

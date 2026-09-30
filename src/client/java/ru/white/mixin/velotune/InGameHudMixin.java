package ru.white.mixin.velotune;

import ru.white.optimization.velotune.VeloTuneManager;
import ru.white.optimization.velotune.hud.ScreenFramebufferCache;
import ru.white.optimization.velotune.perf.VeloTuneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={InGameHud.class})
abstract class InGameHudMixin {
    @Inject(method={"render"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$limitHudRate(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        VeloTuneConfig config;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!InGameHudMixin.velotune$shouldBuffer(client, config = VeloTuneManager.config())) {
            ScreenFramebufferCache.disable();
            return;
        }
        Framebuffer main = client.getFramebuffer();
        if (main.textureWidth <= 0 || main.textureHeight <= 0) {
            ScreenFramebufferCache.disable();
            return;
        }
        Object owner = client.currentScreen == null ? client.world : client.currentScreen;
        ScreenFramebufferCache.beginFrame(owner, main.textureWidth, main.textureHeight, System.nanoTime(), config.extreme.hudUpdatesPerSecond);
        if (!ScreenFramebufferCache.shouldCaptureFrame()) {
            ci.cancel();
        }
    }

    private static boolean velotune$shouldBuffer(MinecraftClient client, VeloTuneConfig config) {
        return config.enabled && config.extreme.enabled && config.extreme.hudBuffer && client.world != null && client.player != null && client.getOverlay() == null;
    }
}

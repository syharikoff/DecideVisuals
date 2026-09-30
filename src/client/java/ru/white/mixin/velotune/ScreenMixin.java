package ru.white.mixin.velotune;

import ru.white.optimization.velotune.hud.ScreenFramebufferCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={Screen.class})
abstract class ScreenMixin {
    @Inject(method={"render"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$limitScreenRate(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (ScreenFramebufferCache.active() && !ScreenFramebufferCache.shouldCaptureFrame()) {
            ci.cancel();
        }
    }

    @Inject(method={"renderBackground"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$skipBufferedScreenBlur(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ScreenFramebufferCache.active()) {
            ci.cancel();
        }
    }
}

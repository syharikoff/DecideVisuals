package ru.white.mixin.velotune;

import ru.white.optimization.velotune.hud.HudFramebufferRouter;
import ru.white.optimization.velotune.hud.ScreenFramebufferCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(value=EnvType.CLIENT)
@Mixin(value={MinecraftClient.class})
abstract class MinecraftClientFramebufferMixin {
    @Inject(method={"getFramebuffer"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$routeHudFramebuffer(CallbackInfoReturnable<Framebuffer> cir) {
        Framebuffer target = HudFramebufferRouter.captureTarget();
        if (target != null) {
            cir.setReturnValue(target);
        }
    }

    @Inject(method={"setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"}, at={@At(value="HEAD")})
    private void velotune$invalidateScreenBuffer(Screen screen, CallbackInfo ci) {
        ScreenFramebufferCache.invalidate();
    }
}

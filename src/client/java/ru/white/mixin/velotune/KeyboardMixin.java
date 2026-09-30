package ru.white.mixin.velotune;

import ru.white.optimization.velotune.hud.ScreenFramebufferCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={Keyboard.class})
abstract class KeyboardMixin {
    @Inject(method={"onKey(JILnet/minecraft/client/input/KeyInput;)V"}, at={@At(value="HEAD")})
    private void velotune$invalidateScreenOnKey(long window, int action, KeyInput input, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen != null) {
            ScreenFramebufferCache.invalidate();
        }
    }

    @Inject(method={"onChar(JLnet/minecraft/client/input/CharInput;)V"}, at={@At(value="HEAD")})
    private void velotune$invalidateScreenOnChar(long window, CharInput input, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen != null) {
            ScreenFramebufferCache.invalidate();
        }
    }
}

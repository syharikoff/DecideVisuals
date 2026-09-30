package ru.white.mixin;

import ru.white.Client;
import ru.white.screen.MainMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
     @Inject(method = "init", at = @At("HEAD"))
     private void onInit(CallbackInfo ci) {
         MinecraftClient.getInstance().setScreen(new MainMenuScreen());
     }
}

package ru.white.mixin;

import ru.white.manager.event_impl.EventKey;
import ru.white.manager.event_impl.EventKeyRelease;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"))
    private void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        Screen screen = client.currentScreen;


        int key = input.key();

        if (key == -1 || key == 0) return;


        if (key >= 0 && key <= 7) return;

        if (action == 1 && screen == null) {
            EventKey event = new EventKey(key);
            event.hook();;
        }

        if (action == 0 && screen == null) {
            EventKeyRelease event = new EventKeyRelease(key);
            event.hook();
        }
    }
}


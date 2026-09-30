package ru.white.mixin;

import ru.white.module.impl.player.PvpSafe;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Блокирует выход с сервера (кнопки выхода в меню паузы) во время ПВП,
 * если включён модуль {@link PvpSafe}. Кики сервера сюда не попадают —
 * они идут через disconnect(Text)/onDisconnected.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientDisconnectMixin {

    @Inject(
            method = {"disconnectWithProgressScreen()V", "disconnectWithSavingScreen()V"},
            at = @At("HEAD"),
            cancellable = true
    )
    private void pvpSafe$blockLeave(CallbackInfo ci) {
        if (PvpSafe.isActive()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "disconnectWithProgressScreen(Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void pvpSafe$blockLeaveTransfer(boolean transferring, CallbackInfo ci) {
        if (PvpSafe.isActive()) {
            ci.cancel();
        }
    }
}

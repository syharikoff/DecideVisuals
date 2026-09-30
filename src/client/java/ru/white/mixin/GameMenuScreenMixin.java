package ru.white.mixin;

import ru.white.module.impl.player.PvpSafe;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Делает кнопку выхода в меню паузы неактивной (нельзя нажать) во время ПВП,
 * если включён модуль {@link PvpSafe}.
 */
@Mixin(GameMenuScreen.class)
public class GameMenuScreenMixin {

    @Shadow
    private ButtonWidget exitButton;

    @Inject(method = "render", at = @At("HEAD"))
    private void pvpSafe$disableExit(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (exitButton != null) {
            exitButton.active = !PvpSafe.isActive();
        }
    }
}

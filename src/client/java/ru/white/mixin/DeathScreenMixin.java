package ru.white.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.white.module.impl.render.WastedDeath;

/**
 * Wasted: перехватываем ванильный экран смерти — вместо него кинематографичный
 * эффект с облётом камеры и надписью.
 */
@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        if (!WastedDeath.interceptDeathScreen((DeathScreen) (Object) this)) return;
        ci.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!WastedDeath.isRunning()) return;

        WastedDeath module = WastedDeath.getInstance();
        if (module != null) module.renderOverlay(graphics);
        ci.cancel();
    }
}

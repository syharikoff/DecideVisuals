package ru.decide.mixin;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.decide.module.impl.render.BetterMinecraft;

/** Better Minecraft: убирает ванильный индикатор сообщений слева от строки чата. */
@Mixin(ChatHudLine.Visible.class)
public abstract class ChatHudLineVisibleMixin {

    @Inject(method = "indicator", at = @At("HEAD"), cancellable = true)
    private void decide$removeIndicator(CallbackInfoReturnable<MessageIndicator> cir) {
        if (BetterMinecraft.chatAnimationsEnabled()) {
            cir.setReturnValue(null);
        }
    }
}
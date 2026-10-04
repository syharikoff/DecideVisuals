package ru.white.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.white.module.impl.render.BetterMinecraft;

/**
 * Better Minecraft: новое сообщение чата выезжает снизу вверх за 150 мс
 * вместо мгновенного появления.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    /** Kimiko ModConfig.fadeTimeMessage */
    private static final int FADE_TIME_MS = 150;

    @Shadow
    private int scrolledLines;

    @Shadow
    private int getLineHeight() {
        return 0;
    }

    @Unique
    private long nightix$lastMessageTime;

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("TAIL"))
    private void nightix$trackMessageAdded(Text contents, MessageSignatureData signature,
                                           MessageIndicator tag, CallbackInfo ci) {
        if (BetterMinecraft.chatAnimationsEnabled()) {
            nightix$lastMessageTime = System.currentTimeMillis();
        }
    }

    @Unique
    private float nightix$displacement() {
        // при прокрутке вверх анимация не мешает
        if (scrolledLines != 0) return 0.0f;

        float maxDisplacement = getLineHeight() * 0.8f;
        long lifetime = System.currentTimeMillis() - nightix$lastMessageTime;
        float alpha = Math.min(lifetime / (float) FADE_TIME_MS, 1.0f);
        return maxDisplacement - alpha * maxDisplacement;
    }

    @WrapOperation(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;render(Lnet/minecraft/client/gui/hud/ChatHud$Backend;IIZ)V"))
    private void nightix$wrapRender(ChatHud instance, ChatHud.Backend queue, int x, int y,
                                   boolean focused, Operation<Void> original,
                                   @Local(argsOnly = true) DrawContext graphics) {
        if (!BetterMinecraft.chatAnimationsEnabled()) {
            original.call(instance, queue, x, y, focused);
            return;
        }

        float displacement = nightix$displacement();
        if (displacement == 0.0f) {
            original.call(instance, queue, x, y, focused);
            return;
        }

        graphics.getMatrices().pushMatrix();
        graphics.getMatrices().translate(0.0f, displacement);
        try {
            original.call(instance, queue, x, y, focused);
        } finally {
            graphics.getMatrices().popMatrix();
        }
    }
}
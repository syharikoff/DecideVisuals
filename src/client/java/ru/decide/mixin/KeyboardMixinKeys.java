package ru.decide.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.decide.module.impl.utils.SoundsKey;

/**
 * Звук нажатия клавиши (Sounds key).
 * <p>
 * Играем только на нажатии ({@code action == 1}), иначе звук сыпал бы дважды
 * на одно нажатие - при нажатии и при отпускании.
 */
@Mixin(Keyboard.class)
public abstract class KeyboardMixinKeys {

    @Inject(method = "onKey", at = @At("TAIL"))
    private void decide$playKeySound(long window, int action, KeyInput key, CallbackInfo info) {
        if (action != 1) return;
        SoundsKey.playKey();
    }
}

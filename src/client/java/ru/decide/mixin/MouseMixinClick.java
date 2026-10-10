package ru.decide.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.decide.module.impl.utils.SoundsKey;

/**
 * Звук клика мыши (Sounds key).
 * <p>
 * Как и в CreamyKeys, звучит только ЛКМ и ПКМ (кнопки 0 и 1): средняя кнопка
 * и колесо используются для скролла и перетаскивания, их озвучивать не нужно.
 */
@Mixin(Mouse.class)
public abstract class MouseMixinClick {

    @Inject(method = "onMouseButton", at = @At("TAIL"))
    private void decide$playMouseSound(long window, MouseInput button, int action, CallbackInfo info) {
        if (action != 1) return;
        int code = button.button();
        if (code != 0 && code != 1) return;
        SoundsKey.playMouse();
    }
}

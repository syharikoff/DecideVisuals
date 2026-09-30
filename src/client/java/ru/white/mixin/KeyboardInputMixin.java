package ru.white.mixin;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import ru.white.manager.event_impl.EventMoveInput;
import ru.white.manager.event_impl.InputEvent;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @ModifyExpressionValue(method = "tick", at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/util/PlayerInput;"))
    private PlayerInput tickHook(PlayerInput original) {
        InputEvent event = new InputEvent(original);
        event.hook();
        return original;
    }
    @Shadow
    @Final
    private GameOptions settings;

    @Unique
    private float abobaGetMovementMultiplier(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        } else {
            return positive ? 1.0F : -1.0F;
        }
    }


    @Inject(method = "tick",at = @At(value = "FIELD", target = "Lnet/minecraft/client/input/KeyboardInput;playerInput:Lnet/minecraft/util/PlayerInput;",ordinal = 0, shift = At.Shift.AFTER), cancellable = true)
    public void injectInputEvent(CallbackInfo ci) {

        EventMoveInput event = new EventMoveInput(this.playerInput,
                abobaGetMovementMultiplier(this.playerInput.forward(), this.playerInput.backward()),
                abobaGetMovementMultiplier(this.playerInput.left(), this.playerInput.right())
        );
        event.hook();

        if (event.isCancelled()) return;

        float forward = event.getForward();
        float strafe = event.getStrafe();
        this.movementVector = new Vec2f(strafe, forward).normalize();
        this.playerInput = new PlayerInput(
                forward > 0,
                forward < 0,
                strafe > 0,
                strafe < 0,
                this.settings.jumpKey.isPressed(),
                this.settings.sneakKey.isPressed(),
                this.settings.sprintKey.isPressed()
        );
        ci.cancel();
    }
}

package ru.white.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import ru.white.Client;
import ru.white.module.impl.render.ShaderEsp;
import ru.white.utils.annotation.IMinecraft;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin implements IMinecraft {
    @ModifyExpressionValue(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;isLogicalSideForUpdatingMovement()Z",
                    ordinal = 1
            )
    )
    public boolean fixFalldistanceValue(boolean original) {
        if ((Object) this == mc.player) {
            return true;
        }

        return original;
    }

    @Inject(method = "isGlowing", at = @At("RETURN"), cancellable = true)
    private void espMarkGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        ShaderEsp esp = ShaderEsp.getInstance();
        if (esp != null && esp.isEnabled() && ShaderEsp.isEspTarget((Entity)(Object)this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true, require = 0)
    private void espFriendTeamColor(CallbackInfoReturnable<Integer> cir) {
        ShaderEsp esp = ShaderEsp.getInstance();
        if (esp == null || !esp.isEnabled()) return;
        Entity self = (Entity)(Object)this;
        if (!(self instanceof PlayerEntity p)) return;
        if (Client.get().friendManager().isFriend(p.getName().getString())) {
            cir.setReturnValue(0x55FF55);
        }
    }
}

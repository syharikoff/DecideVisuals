package ru.white.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.white.module.impl.render.custompet.entity.CustomPetEntity;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class CustomPetClientPlayerInteractionMixin {
    public CustomPetClientPlayerInteractionMixin() {
    }

    @Inject(
            method = "attackEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void white_cancelCustomPetAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (target instanceof CustomPetEntity) {
            ci.cancel();
        }
    }

    @Inject(
            method = "interactEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void white_cancelCustomPetInteract(PlayerEntity player, Entity target, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (target instanceof CustomPetEntity) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }

    @Inject(
            method = "interactEntityAtLocation(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/hit/EntityHitResult;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void white_cancelCustomPetInteractAt(PlayerEntity player, Entity target, EntityHitResult hitResult, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (target instanceof CustomPetEntity) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }
}
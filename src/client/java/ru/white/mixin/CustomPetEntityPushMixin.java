package ru.white.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.white.module.impl.render.custompet.entity.CustomPetEntity;

@Mixin(Entity.class)
public abstract class CustomPetEntityPushMixin {
    public CustomPetEntityPushMixin() {
    }

    @Inject(
            method = "pushAwayFrom(Lnet/minecraft/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void white_cancelCustomPetPush(Entity other, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        Entity self = (Entity) (Object) this;
        boolean playerInvolved = self == mc.player || other == mc.player;
        boolean petInvolved = self instanceof CustomPetEntity || other instanceof CustomPetEntity;
        if (playerInvolved && petInvolved) {
            ci.cancel();
        }
    }
}
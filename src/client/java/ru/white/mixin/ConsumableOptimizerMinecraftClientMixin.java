package ru.white.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.white.module.impl.utils.consumable.ConsumableHandler;

@Mixin(MinecraftClient.class)
public class ConsumableOptimizerMinecraftClientMixin {

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void onDoItemUse(CallbackInfo ci) {
        if (!ConsumableHandler.isEnabled()) return;
        MinecraftClient client = (MinecraftClient) (Object) this;
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        if (ConsumableHandler.STATE.isWaitingForServer()) {
            ItemStack mainHand = player.getMainHandStack();
            ItemStack offHand = player.getOffHandStack();
            UseAction mainAction = mainHand.getUseAction();
            UseAction offAction = offHand.getUseAction();
            boolean mainConsumable = mainAction == UseAction.EAT || mainAction == UseAction.DRINK;
            boolean offConsumable = offAction == UseAction.EAT || offAction == UseAction.DRINK;

            if (mainConsumable || offConsumable) {
                ci.cancel();
            }
        }
    }
}

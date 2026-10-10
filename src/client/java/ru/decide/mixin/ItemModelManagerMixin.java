package ru.decide.mixin;

import net.minecraft.client.item.ItemModelManager;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.decide.module.impl.render.swordreplacer.SwordReplacer;

@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {

    @ModifyVariable(
            method = "clearAndUpdate",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private ItemStack decidevisuals$replaceRenderedSword(ItemStack stack) {
        return SwordReplacer.getRenderStack(stack);
    }
}

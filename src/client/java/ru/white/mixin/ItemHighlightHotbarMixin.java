package ru.white.mixin;

import ru.white.module.impl.render.ItemHighlight;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class ItemHighlightHotbarMixin {

    @Inject(method = "renderHotbarItem", at = @At("HEAD"), require = 0)
    private void onRenderHotbarItem(DrawContext context, int x, int y, RenderTickCounter tickCounter,
                                    PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        if (stack != null && !stack.isEmpty()) {
            ItemHighlight module = ItemHighlight.getInstance();
            if (module != null) {
                int argb = module.backgroundFor(stack, true);
                if (argb != 0) {
                    module.drawSlotBackground(context, x, y, argb);
                }
            }
        }
    }
}
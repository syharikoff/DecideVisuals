package ru.white.mixin;

import ru.white.module.impl.render.ItemHighlight;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class ItemHighlightContainerMixin {

    @Inject(method = "drawSlot", at = @At("HEAD"), require = 0)
    private void onDrawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (slot != null) {
            ItemHighlight module = ItemHighlight.getInstance();
            if (module != null) {
                int argb = module.backgroundFor(slot.getStack(), false);
                if (argb != 0) {
                    module.drawSlotBackground(context, slot.x, slot.y, argb);
                }
            }
        }
    }
}
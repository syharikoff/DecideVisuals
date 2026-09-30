package ru.white.mixin;

import ru.white.Client;
import ru.white.inventorypreset.InventoryPreset;
import ru.white.inventorypreset.InventoryPresetManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    @Final
    protected ScreenHandler handler;

    @Shadow
    public abstract ScreenHandler getScreenHandler();

    @Inject(
            method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V",
            at = @At("TAIL")
    )
    private void onDrawPresetSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!((Object) this instanceof InventoryScreen) || Client.get() == null
                || Client.get().inventoryPresetManager() == null) return;

        InventoryPresetManager manager = Client.get().inventoryPresetManager();
        InventoryPreset preset = manager.activePreset().orElse(null);
        if (preset == null) return;

        int logical = InventoryPresetManager.screenToLogicalSlot(slot.id);
        if (logical < 0) return;

        InventoryPreset.Entry expected = preset.slot(logical);
        if (expected.isEmpty()) return;

        InventoryPresetManager.SlotState state = manager.stateFor(logical);
        int color = switch (state) {
            case CORRECT -> 0xFF42C96B;
            case WRONG_SLOT -> 0xFFE4B94F;
            case MISSING -> 0xFFE05C61;
            default -> 0;
        };

        if (state != InventoryPresetManager.SlotState.CORRECT) {
            ItemStack ghost = expected.toStack();
            if (!ghost.isEmpty()) {
                context.drawItem(ghost, slot.x, slot.y);
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x650A0A0A);
                context.drawStackOverlay(MinecraftClient.getInstance().textRenderer, ghost, slot.x, slot.y);
            }
        }

        if (color != 0) {
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 1, color);
            context.fill(slot.x, slot.y + 15, slot.x + 16, slot.y + 16, color);
            context.fill(slot.x, slot.y, slot.x + 1, slot.y + 16, color);
            context.fill(slot.x + 15, slot.y, slot.x + 16, slot.y + 16, color);
        }
    }
}

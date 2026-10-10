package ru.decide.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ru.decide.Client;
import ru.decide.module.impl.render.BetterMinecraft;
import ru.decide.utils.render.ItemMoveAnimator;
import ru.decide.inventorypreset.InventoryPreset;
import ru.decide.inventorypreset.InventoryPresetManager;
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
import org.spongepowered.asm.mixin.Unique;
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

    @Shadow
    protected int x;

    @Shadow
    protected int y;

    // ================= Better Minecraft =================

    @Unique
    private boolean decide$panelAnimated;

    @Unique
    private boolean decide$contentsAnimated;

    /** Сброс времени открытия — от него считается выезд панели. */
    @Inject(method = "init", at = @At("TAIL"))
    private void decide$trackInventoryOpen(CallbackInfo ci) {
        BetterMinecraft.markInventoryOpen();
    }

    @Inject(method = "renderBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/Screen;renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            shift = At.Shift.AFTER))
    private void decide$animatePanel(DrawContext context, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        decide$panelAnimated = BetterMinecraft.inventoryAnimationEnabled();
        if (!decide$panelAnimated) return;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0f, BetterMinecraft.inventorySlideOffset());
    }

    @Inject(method = "renderBackground", at = @At("RETURN"))
    private void decide$endPanelAnimation(DrawContext context, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!decide$panelAnimated) return;
        decide$panelAnimated = false;
        context.getMatrices().popMatrix();
    }

    @Inject(method = "renderMain", at = @At("HEAD"))
    private void decide$beginContentsAnimation(DrawContext context, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        decide$contentsAnimated = BetterMinecraft.inventoryAnimationEnabled();
        if (!decide$contentsAnimated) return;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0f, BetterMinecraft.inventorySlideOffset());
    }

    @Inject(method = "renderMain", at = @At("RETURN"))
    private void decide$endContentsAnimation(DrawContext context, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!decide$contentsAnimated) return;
        decide$contentsAnimated = false;
        context.getMatrices().popMatrix();
    }

    @Inject(method = "drawSlots", at = @At("HEAD"))
    private void decide$beginSlotAnim(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (!BetterMinecraft.itemMoveAnimationEnabled()) return;
        ItemMoveAnimator.beginFrame(handler, mouseX, mouseY, x, y);
    }

    /** Смещение и масштаб слота по анимации перелёта предмета. */
    @WrapOperation(method = "drawSlots", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V"))
    private void decide$animateSlot(HandledScreen<?> instance, DrawContext context, Slot slot,
                                    int mouseX, int mouseY, Operation<Void> original) {
        float[] off = BetterMinecraft.itemMoveAnimationEnabled() ? ItemMoveAnimator.offset(slot) : null;
        if (off == null) {
            original.call(instance, context, slot, mouseX, mouseY);
            return;
        }

        context.getMatrices().pushMatrix();
        if (off[2] != 1.0f) {
            float cx = slot.x + 8.0f;
            float cy = slot.y + 8.0f;
            context.getMatrices().translate(cx, cy);
            context.getMatrices().scale(off[2], off[2]);
            context.getMatrices().translate(-cx, -cy);
        } else {
            context.getMatrices().translate(off[0], off[1]);
        }
        original.call(instance, context, slot, mouseX, mouseY);
        context.getMatrices().popMatrix();
    }

    // ================= Inventory Presets =================

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

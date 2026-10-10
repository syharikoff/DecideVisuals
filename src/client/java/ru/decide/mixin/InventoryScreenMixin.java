package ru.decide.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ru.decide.Client;
import ru.decide.module.impl.player.LockSlot;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {

    @Unique private static final int BTN_W = 52;
    @Unique private static final int BTN_H = 12;
    @Unique private static final int BTN_OFFSET_X = 3;
    @Unique private static final int BTN_OFFSET_Y = -15;

    @Unique private int btnX, btnY;
    @Unique private boolean hovered = false;

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        InventoryScreen self = (InventoryScreen) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        int screenX = ((HandledScreenAccessor) self).getScreenX();
        int screenY = ((HandledScreenAccessor) self).getScreenY();

        btnX = screenX + BTN_OFFSET_X;
        btnY = screenY + BTN_OFFSET_Y;

        hovered = mouseX >= btnX && mouseX <= btnX + BTN_W
                && mouseY >= btnY && mouseY <= btnY + BTN_H;

        int bg = hovered ? 0xFFCC3333 : 0xFF882222;
        ctx.fill(btnX, btnY, btnX + BTN_W, btnY + BTN_H, bg);

        String label = "Выкинуть всё";
        int textW = mc.textRenderer.getWidth(label);
        int textX = btnX + (BTN_W - textW) / 2;
        int textY = btnY + (BTN_H - mc.textRenderer.fontHeight) / 2 + 1;
        ctx.drawText(mc.textRenderer, label, textX, textY, 0xFFFFFFFF, false);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(Click click, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != 0) return;
        if (!hovered) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;

        int syncId = mc.player.currentScreenHandler.syncId;
        int slotCount = mc.player.currentScreenHandler.slots.size();
        LockSlot lockSlot = Client.get().moduleManager().get(LockSlot.class);

        for (int i = 0; i < slotCount; i++) {
            if (mc.player.currentScreenHandler.slots.get(i).hasStack()
                    && (lockSlot == null || !lockSlot.isDropProtected(
                    mc.player.currentScreenHandler.slots.get(i).getStack(),
                    toInventorySlot(i)))) {
                mc.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, mc.player);
            }
        }

        cir.setReturnValue(true);
        cir.cancel();
    }

    @Unique
    private static int toInventorySlot(int screenSlot) {
        if (screenSlot >= 36 && screenSlot <= 44) return screenSlot - 36;
        if (screenSlot >= 9 && screenSlot <= 35) return screenSlot;
        return -1;
    }
}

package ru.white.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.white.module.impl.render.BetterMinecraft;

/** Better Minecraft: модель игрока в инвентаре едет вместе с выезжающей панелью. */
@Mixin(value = {InventoryScreen.class, CreativeInventoryScreen.class})
public abstract class InventoryScreenEntityMixin {

    @Redirect(method = "drawBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/ingame/InventoryScreen;drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V"))
    private void nightix$slideInventoryEntity(DrawContext graphics, int x1, int y1, int x2, int y2,
                                              int size, float inflation, float mouseX, float mouseY,
                                              LivingEntity entity) {
        int off = Math.round(BetterMinecraft.inventorySlideOffset());
        InventoryScreen.drawEntity(graphics, x1, y1 + off, x2, y2 + off, size, inflation, mouseX, mouseY, entity);
    }
}
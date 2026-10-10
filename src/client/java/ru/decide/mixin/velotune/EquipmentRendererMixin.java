package ru.decide.mixin.velotune;

import ru.decide.optimization.velotune.VeloTuneManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(value=EnvType.CLIENT)
@Mixin(value={EquipmentRenderer.class})
abstract class EquipmentRendererMixin {
    @Redirect(method={"render"}, at=@At(value="INVOKE", target="Lnet/minecraft/item/ItemStack;hasGlint()Z"), require=0)
    private boolean velotune$skipEquipmentGlint(ItemStack stack) {
        return !EquipmentRendererMixin.velotune$simplifyEquipment() && stack.hasGlint();
    }

    private static boolean velotune$simplifyEquipment() {
        return VeloTuneManager.enabled() && VeloTuneManager.config().extreme.enabled && VeloTuneManager.config().extreme.simplifyEquipmentRendering;
    }
}

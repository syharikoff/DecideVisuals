package ru.white.mixin.velotune;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;

@Environment(value=EnvType.CLIENT)
@Mixin(targets={"net/minecraft/client/render/entity/EntityRenderManager$LabelRenderer"}, remap=false)
abstract class LabelCommandsMixin {
}

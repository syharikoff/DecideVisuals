package ru.white.mixin.velotune;

import ru.white.optimization.velotune.compat.sodium.SodiumRenderSectionAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Environment(value=EnvType.CLIENT)
@Pseudo
@Mixin(targets={"net/caffeinemc/mods/sodium/client/render/chunk/RenderSection"}, remap=false)
abstract class SodiumRenderSectionMixin
implements SodiumRenderSectionAccess {
    @Shadow(remap=false)
    public abstract int getOriginY();

    @Override
    public int velotune$getOriginY() {
        return this.getOriginY();
    }
}

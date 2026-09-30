package ru.white.mixin.velotune;

import ru.white.optimization.velotune.compat.sodium.SodiumRenderSectionAccess;
import ru.white.optimization.velotune.perf.ExtremeRenderBudget;
import ru.white.optimization.velotune.perf.Metrics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Pseudo
@Mixin(targets={"net/caffeinemc/mods/sodium/client/render/chunk/lists/SectionCollector"}, remap=false)
abstract class SodiumVisibleChunkCollectorMixin {
    @Inject(method={"visit(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;)V"}, at={@At(value="HEAD")}, cancellable=true, require=0, remap=false)
    private void velotune$cullSection(@Coerce Object section, CallbackInfo ci) {
        SodiumRenderSectionAccess access;
        if (section instanceof SodiumRenderSectionAccess && ExtremeRenderBudget.cullChunkSectionY((access = (SodiumRenderSectionAccess)section).velotune$getOriginY())) {
            Metrics.chunkSectionCull();
            ci.cancel();
        }
    }
}

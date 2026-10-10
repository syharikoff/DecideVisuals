package ru.decide.mixin.velotune;

import ru.decide.optimization.velotune.perf.ExtremeRenderBudget;
import ru.decide.optimization.velotune.perf.Metrics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(value=EnvType.CLIENT)
@Mixin(value={ChunkBuilder.BuiltChunk.class})
abstract class ChunkBuilderBuiltChunkMixin {
    @Shadow
    public abstract BlockPos getOrigin();

    @Inject(method={"needsRebuild()Z"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$skipHiddenSectionBuild(CallbackInfoReturnable<Boolean> cir) {
        if (ExtremeRenderBudget.cullChunkSection(this.getOrigin())) {
            Metrics.chunkSectionCull();
            cir.setReturnValue(false);
        }
    }
}

package ru.white.mixin.velotune;

import ru.white.optimization.velotune.perf.ExtremeRenderBudget;
import ru.white.optimization.velotune.perf.Metrics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(value=EnvType.CLIENT)
@Mixin(value={BlockEntityRenderManager.class})
abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method={"getRenderState(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;"}, at={@At(value="HEAD")}, cancellable=true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void velotune$cullBlockEntity(E blockEntity, float tickDelta, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay, CallbackInfoReturnable<S> cir) {
        if (ExtremeRenderBudget.cullBlockEntity(blockEntity)) {
            Metrics.blockEntityCull();
            cir.setReturnValue(null);
        }
    }
}

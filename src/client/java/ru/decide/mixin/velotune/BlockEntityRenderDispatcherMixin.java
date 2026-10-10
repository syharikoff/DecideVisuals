package ru.decide.mixin.velotune;

import ru.decide.module.impl.render.Optimizer;
import ru.decide.optimization.velotune.perf.ExtremeRenderBudget;
import ru.decide.optimization.velotune.perf.Metrics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(value=EnvType.CLIENT)
@Mixin(value={BlockEntityRenderManager.class})
abstract class BlockEntityRenderDispatcherMixin {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Inject(method={"getRenderState(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;"}, at={@At(value="HEAD")}, cancellable=true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void velotune$cullBlockEntity(E blockEntity, float tickDelta, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay, CallbackInfoReturnable<S> cir) {
        // Optimizer: отсечение блок-сущностей по дистанции уровня
        float cullDist = Optimizer.blockEntityCullDist();
        if (cullDist > 0.0F && mc.world != null) {
            BlockPos pos = blockEntity.getPos();
            if (pos != null) {
                Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
                double dx = pos.getX() + 0.5 - cam.x;
                double dy = pos.getY() + 0.5 - cam.y;
                double dz = pos.getZ() + 0.5 - cam.z;
                if (dx * dx + dy * dy + dz * dz > (double) cullDist * cullDist) {
                    cir.setReturnValue(null);
                    return;
                }
            }
        }

        if (ExtremeRenderBudget.cullBlockEntity(blockEntity)) {
            Metrics.blockEntityCull();
            cir.setReturnValue(null);
        }
    }
}

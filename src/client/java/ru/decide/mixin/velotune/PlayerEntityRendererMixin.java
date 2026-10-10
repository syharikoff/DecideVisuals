package ru.decide.mixin.velotune;

import ru.decide.optimization.velotune.VeloTuneManager;
import ru.decide.optimization.velotune.perf.ExtremeRenderBudget;
import ru.decide.optimization.velotune.perf.Metrics;
import ru.decide.optimization.velotune.perf.NameTagTextBuffer;
import ru.decide.optimization.velotune.perf.PlayerLodPolicy;
import ru.decide.optimization.velotune.perf.VeloTuneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(value=EnvType.CLIENT)
@Mixin(value={PlayerEntityRenderer.class})
abstract class PlayerEntityRendererMixin {
    @Inject(method={"shouldRenderFeatures"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$limitFarFeatures(PlayerEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (!VeloTuneManager.enabled()) {
            return;
        }
        VeloTuneConfig config = VeloTuneManager.config();
        if (config.extreme.preservePlayerEquipment) {
            return;
        }
        if (ExtremeRenderBudget.active() && config.extreme.hidePlayerFeatures) {
            Metrics.featureCull();
            cir.setReturnValue(false);
            return;
        }
        if (PlayerLodPolicy.skipFeatures(state.squaredDistanceToCamera, VeloTuneManager.governor().pressureLevel(), config.players)) {
            Metrics.featureCull();
            cir.setReturnValue(false);
        }
    }

    @Inject(method={"renderLabelIfPresent"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$limitFarLabels(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue commandQueue, CameraRenderState cameraState, CallbackInfo ci) {
        if (!VeloTuneManager.enabled()) {
            return;
        }
        VeloTuneConfig config = VeloTuneManager.config();
        if (config.extreme.preservePlayerLabels) {
            return;
        }
        if (ExtremeRenderBudget.active() && config.extreme.hidePlayerLabels) {
            Metrics.labelCull();
            ci.cancel();
            return;
        }
        if (PlayerLodPolicy.skipLabel(state.squaredDistanceToCamera, VeloTuneManager.governor().pressureLevel(), config.players)) {
            Metrics.labelCull();
            ci.cancel();
        }
    }
}

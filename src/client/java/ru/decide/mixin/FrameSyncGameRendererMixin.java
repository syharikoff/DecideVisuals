package ru.decide.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.decide.optimization.FrameSyncHandler;
import ru.decide.optimization.velotune.VeloTuneManager;
import ru.decide.optimization.velotune.perf.ExtremeRenderBudget;

@Mixin(GameRenderer.class)
public abstract class FrameSyncGameRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void framesync$skipRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        ExtremeRenderBudget.beginFrame();
        VeloTuneManager.governor().beginFrame();
        if (!FrameSyncHandler.allowDraw()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void framesync$endFrame(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        VeloTuneManager.governor().endFrame();
    }
}

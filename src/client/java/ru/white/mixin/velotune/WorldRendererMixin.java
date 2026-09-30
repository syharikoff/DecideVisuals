package ru.white.mixin.velotune;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import ru.white.optimization.velotune.VeloTuneManager;
import ru.white.optimization.velotune.perf.ExtremeRenderBudget;
import ru.white.optimization.velotune.perf.Metrics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.FrameGraphBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={WorldRenderer.class})
abstract class WorldRendererMixin {
    @Inject(method={"renderClouds"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$disableClouds(FrameGraphBuilder frameGraphBuilder, CloudRenderMode cloudRenderMode, Vec3d cameraPos, long cloudTime, float tickDelta, int color, float cloudHeight, CallbackInfo ci) {
        if (ExtremeRenderBudget.active() && VeloTuneManager.config().extreme.disableClouds) {
            ci.cancel();
        }
    }

    @Inject(method={"renderWeather"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$disableWeather(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice fogBuffer, CallbackInfo ci) {
        if (ExtremeRenderBudget.active() && VeloTuneManager.config().extreme.disableWeather) {
            ci.cancel();
        }
    }
}

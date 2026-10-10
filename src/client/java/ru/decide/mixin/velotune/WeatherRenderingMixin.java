package ru.decide.mixin.velotune;

import ru.decide.module.impl.render.Optimizer;
import ru.decide.optimization.velotune.VeloTuneManager;
import ru.decide.optimization.velotune.perf.ExtremeRenderBudget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.WeatherRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={WeatherRendering.class})
abstract class WeatherRenderingMixin {
    @Inject(method={"renderPrecipitation"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$disablePrecipitation(CallbackInfo ci) {
        if (Optimizer.isNoWeather()) {
            ci.cancel();
            return;
        }
        if (ExtremeRenderBudget.active() && VeloTuneManager.config().extreme.disableWeather) {
            ci.cancel();
        }
    }

    @Inject(method={"addParticlesAndSound"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$disableWeatherParticles(CallbackInfo ci) {
        if (Optimizer.isNoWeather()) {
            ci.cancel();
            return;
        }
        if (ExtremeRenderBudget.active() && VeloTuneManager.config().extreme.disableWeather) {
            ci.cancel();
        }
    }
}

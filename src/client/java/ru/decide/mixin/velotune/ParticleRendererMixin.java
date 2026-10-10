package ru.decide.mixin.velotune;

import ru.decide.optimization.velotune.perf.ExtremeRenderBudget;
import ru.decide.optimization.velotune.perf.Metrics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.ParticleRenderer;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={ParticleRenderer.class})
abstract class ParticleRendererMixin {
    @Inject(method={"add"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$removeCulledParticle(Particle particle, CallbackInfo ci) {
        if (ExtremeRenderBudget.cullParticle(particle)) {
            Metrics.particleDrop();
            particle.markDead();
            ci.cancel();
        }
    }
}

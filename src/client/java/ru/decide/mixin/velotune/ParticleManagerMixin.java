package ru.decide.mixin.velotune;

import ru.decide.module.impl.render.Optimizer;
import ru.decide.optimization.velotune.perf.ParticleBudget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={ParticleManager.class})
abstract class ParticleManagerMixin {
    @Inject(method={"addParticle(Lnet/minecraft/client/particle/Particle;)V"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$limitParticleQueue(Particle particle, CallbackInfo ci) {
        if (!ParticleBudget.allow(particle)) {
            ci.cancel();
            return;
        }
        if (!Optimizer.allowParticle(particle)) {
            ci.cancel();
        }
    }
}

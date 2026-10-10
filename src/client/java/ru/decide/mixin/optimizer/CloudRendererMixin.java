package ru.decide.mixin.optimizer;

import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.decide.module.impl.render.Optimizer;

/** Optimizer: на уровне «Ультра» облака не рисуются. */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void decide$cullClouds(int x, CloudRenderMode mode, float tickDelta, Vec3d pos,
                                   long seed, float bright, CallbackInfo ci) {
        if (Optimizer.cullClouds()) {
            ci.cancel();
        }
    }
}
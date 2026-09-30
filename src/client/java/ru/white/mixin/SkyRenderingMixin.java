package ru.white.mixin;

import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.white.module.impl.render.ShaderSky;

@Mixin(SkyRendering.class)
public class SkyRenderingMixin {

    @Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true, require = 0)
    private void hideCelestialObjectsForShaderSky(MatrixStack matrices, float pitch, float yaw, float roll, MoonPhase moonPhase, float risingLevel, float settingLevel, CallbackInfo ci) {
        ShaderSky shaderSky = ShaderSky.getInstance();
        if (shaderSky != null && shaderSky.isEnabled() && !shaderSky.mode.is("Blur")) {
            ci.cancel();
        }
    }
}
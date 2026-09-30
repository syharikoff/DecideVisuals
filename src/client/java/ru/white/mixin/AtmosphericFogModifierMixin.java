package ru.white.mixin;

import ru.white.module.impl.render.WorldTweaks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AtmosphericFogModifier.class)
public class AtmosphericFogModifierMixin {

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void onGetFogColor(ClientWorld world, Camera camera, int viewDistance, float tickProgress, CallbackInfoReturnable<Integer> cir) {
        WorldTweaks worldTweaks = WorldTweaks.get();
        if (worldTweaks == null || !worldTweaks.isEnabled() || !worldTweaks.fogs.getValue()) {
            return;
        }

        cir.setReturnValue( worldTweaks.getColor());
    }

    @Inject(method = "applyStartEndModifier", at = @At("RETURN"))
    private void onApplyStartEnd(FogData fogData, Camera camera, ClientWorld world, float viewDistance, RenderTickCounter tickCounter, CallbackInfo ci) {
        WorldTweaks worldTweaks = WorldTweaks.get();
        if (worldTweaks == null || !worldTweaks.isEnabled() || !worldTweaks.fogs.getValue()) {
            return;
        }

        float end = worldTweaks.fog.getValue();
        fogData.environmentalStart = Math.min(2.0F, end * 0.25F);
        fogData.environmentalEnd = end;
        fogData.skyEnd = end;
        fogData.cloudEnd = end;
    }
}

package ru.white.mixin;

import ru.white.Client;
import ru.white.module.impl.render.Gamma;
import ru.white.module.impl.render.NoRender;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
    private float night$getGammaValue(Double instance) {
        if (Client.get() != null) {
            Gamma module = Client.get().moduleManager().get(Gamma.class);
            if (module != null && module.isEnabled()) {
                return 200F;
            }
        }
        return instance.floatValue();
    }

    @Inject(method = "getDarkness", at = @At("HEAD"), cancellable = true)
    private void removeDarknessEffect(CallbackInfoReturnable<Float> cir) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isEnabled() && noRender.ignoreZalupa.getValue()) {
            cir.setReturnValue(0.0F);
        }
    }
}

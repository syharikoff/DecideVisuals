package ru.white.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import ru.white.module.impl.render.WorldTweaks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.Properties.class)
public abstract class ClientWorldPropertiesMixin {
    @Shadow private long time;

    @Shadow private long timeOfDay;

    @ModifyReturnValue(method = "getTimeOfDay", at = @At("RETURN"))
    private long hookGetTime(long original) {
        return WorldTweaks.get().isEnabled() && WorldTweaks.get().times.getValue() ?  (long) (WorldTweaks.get().time.getValue() * 1000L) : this.timeOfDay;
    }
}


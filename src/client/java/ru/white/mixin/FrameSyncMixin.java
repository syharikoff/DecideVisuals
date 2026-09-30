package ru.white.mixin;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.white.optimization.FrameSyncHandler;

@Mixin(MinecraftClient.class)
public abstract class FrameSyncMixin {

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;ILcom/mojang/blaze3d/textures/GpuTexture;D)V"
            )
    )
    private void framesync$skipClear(
            CommandEncoder encoder,
            GpuTexture colorTexture,
            int clearColor,
            GpuTexture depthTexture,
            double clearDepth
    ) {
        if (FrameSyncHandler.beginFrame()) {
            encoder.clearColorAndDepthTextures(colorTexture, clearColor, depthTexture, clearDepth);
        }
    }
}

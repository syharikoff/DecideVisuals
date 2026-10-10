package ru.decide.mixin.velotune;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import ru.decide.optimization.velotune.hud.ScreenFramebufferCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(value=EnvType.CLIENT)
@Mixin(value={GuiRenderer.class})
abstract class GuiRendererMixin {
    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void velotune$beginGuiPass(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        if (!ScreenFramebufferCache.active()) {
            return;
        }
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        if (ScreenFramebufferCache.shouldCaptureFrame()) {
            ScreenFramebufferCache.beginRenderCapture();
        } else {
            ScreenFramebufferCache.composite(main);
        }
    }

    @Inject(method={"render"}, at={@At(value="RETURN")})
    private void velotune$finishGuiPass(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        if (!ScreenFramebufferCache.isRenderingCapture()) {
            return;
        }
        ScreenFramebufferCache.finishRenderCapture(System.nanoTime());
        ScreenFramebufferCache.composite(MinecraftClient.getInstance().getFramebuffer());
    }
}

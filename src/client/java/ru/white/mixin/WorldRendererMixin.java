package ru.white.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.jspecify.annotations.Nullable;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.module.impl.render.NoRender;
import ru.white.module.impl.render.ShaderEsp;
import ru.white.module.impl.render.ShaderSky;
import ru.white.module.impl.render.ExplosionWave;

import ru.white.module.impl.render.JumpCircle;
import ru.white.utils.render.ShaderSkyRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.render.WorldRenderer.class)
public class WorldRendererMixin {


    @Shadow private @Nullable Framebuffer entityOutlineFramebuffer;

    @Shadow private @Nullable BuiltChunkStorage chunks;

    @Inject(method = "drawEntityOutlinesFramebuffer", at = @At("HEAD"), cancellable = true, require = 0)
    private void espCancelOutlineDraw(CallbackInfo ci) {
        ShaderEsp esp = ShaderEsp.getInstance();
        if (esp != null && esp.isEnabled() && !ShaderEsp.getAllTargets().isEmpty()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "method_62215",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/SkyRendering;renderTopSky(I)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private static void renderShaderSkyLayer(GpuBufferSlice fogBuffer, SkyRenderState skyRenderState, SkyRendering skyRendering, CallbackInfo ci) {
        ShaderSky shaderSky = ShaderSky.getInstance();
        if (shaderSky == null || !shaderSky.isEnabled() || shaderSky.mode.is("Blur")) return;

        ShaderSkyRenderer.getInstance().renderCelestialShader();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void publishWorldRenderEvent(
            ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci
    ) {

        MatrixStack stack = new MatrixStack();
        Matrix4f basePositionMatrix = new Matrix4f(positionMatrix);
        stack.multiplyPositionMatrix(new Matrix4f(basePositionMatrix));

        new EventRender3D(stack, tickCounter.getTickProgress(true), projectionMatrix).hook();

        ShaderEsp.getInstance().customFboReference = this.entityOutlineFramebuffer;

        if(ShaderEsp.getInstance().isEnabled()) {
            ShaderEsp.getInstance().onRender3D();
        }

        ShaderSky shaderSky = ShaderSky.getInstance();
        if (shaderSky != null && shaderSky.isEnabled()) {
            ShaderSkyRenderer.getInstance().renderSky();
        }

        // Jump Circle в режиме «Разлом» — пост-проход по готовому кадру
        JumpCircle jumpCircle = JumpCircle.getInstance();
        if (jumpCircle != null && jumpCircle.isEnabled()) {
            try {
                jumpCircle.renderSouls(MinecraftClient.getInstance().getFramebuffer(), positionMatrix, projectionMatrix);
            } catch (Throwable ignored) {
            }
        }

        // Explosion Wave — ударная волна по кадру
        ExplosionWave explosionWave = ExplosionWave.getInstance();
        if (explosionWave != null && explosionWave.isEnabled()) {
            try {
                explosionWave.renderWaves(MinecraftClient.getInstance().getFramebuffer(), positionMatrix, projectionMatrix);
            } catch (Throwable ignored) {
            }
        }
    }

    @Inject(method = "hasBlindnessOrDarkness", at = @At("HEAD"), cancellable = true)
    private void onHasBlindnessOrDarkness(Camera camera, CallbackInfoReturnable<Boolean> cir) {
        NoRender noRender = NoRender.getInstance();
        if (noRender == null || !noRender.isEnabled()) return;

        Entity entity = camera.getFocusedEntity();
        if (!(entity instanceof LivingEntity livingEntity)) return;

        boolean hasBlindness = livingEntity.hasStatusEffect(StatusEffects.BLINDNESS);
        boolean hasDarkness = livingEntity.hasStatusEffect(StatusEffects.DARKNESS);

        if (noRender.ignoreZalupa.getValue() && hasBlindness && !hasDarkness) {
            cir.setReturnValue(false);
        }

        if (noRender.ignoreZalupa.getValue() && hasDarkness && !hasBlindness) {
            cir.setReturnValue(false);
        }

        if (noRender.ignoreZalupa.getValue()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "scheduleBlockRenders", at = @At("HEAD"), cancellable = true)
    private void onScheduleBlockRenders(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
        if (this.chunks == null) ci.cancel();
    }

    @Inject(method = "scheduleChunkRender", at = @At("HEAD"), cancellable = true)
    private void onScheduleChunkRender(int x, int y, int z, CallbackInfo ci) {
        if (this.chunks == null) ci.cancel();
    }


}

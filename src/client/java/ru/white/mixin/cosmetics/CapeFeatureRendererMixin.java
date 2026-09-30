package ru.white.mixin.cosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.white.cosmetics.CosmeticCategory;
import ru.white.cosmetics.CosmeticManager;

/**
 * Гасит ванильный плащ, когда косметический плащ надет.
 * <p>
 * Проверка строго по локальному игроку: у остальных игроков в мире ванильные плащи
 * должны продолжать рисоваться, иначе все ходят без плащей.
 */
@Mixin(CapeFeatureRenderer.class)
public abstract class CapeFeatureRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelVanillaCape(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                   PlayerEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (mc.world.getEntityById(state.id) != mc.player) return;
        if (CosmeticManager.get().getEquipped(CosmeticCategory.CAPES) > 0) {
            ci.cancel();
        }
    }
}

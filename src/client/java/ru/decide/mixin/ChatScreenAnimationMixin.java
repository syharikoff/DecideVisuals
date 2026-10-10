package ru.decide.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.decide.module.impl.render.BetterMinecraft;

/**
 * Better Minecraft: поле ввода чата выезжает снизу с back-ease за 170 мс при открытии.
 * Смещение применяется к ванильной заливке фона и к super.render (поле + подсказки).
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenAnimationMixin {

    /** Kimiko ModConfig.fadeTimeTextField */
    private static final int FADE_TIME_MS = 170;
    private static final float FADE_OFFSET = 8.0f;

    @Unique
    private boolean decide$wasOpenedLastFrame;
    @Unique
    private long decide$lastOpenTime;
    @Unique
    private float decide$displacement;

    @Unique
    private float decide$chatDisplacement() {
        if (!BetterMinecraft.chatAnimationsEnabled()) return 0.0f;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && !decide$wasOpenedLastFrame && !client.player.isSleeping()) {
            decide$wasOpenedLastFrame = true;
            decide$lastOpenTime = System.currentTimeMillis();
        }

        float screenFactor = client.getWindow().getFramebufferHeight() / 1080.0f;
        float timeSinceOpen = Math.min(System.currentTimeMillis() - decide$lastOpenTime, (float) FADE_TIME_MS);
        float alpha = 1.0f - timeSinceOpen / FADE_TIME_MS;

        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float eased = c3 * alpha * alpha * alpha - c1 * alpha * alpha;
        return eased * FADE_OFFSET * screenFactor;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
    private void decide$wrapBackgroundFill(DrawContext graphics, int x0, int y0, int x1, int y1,
                                            int color, Operation<Void> original) {
        decide$displacement = decide$chatDisplacement();
        if (decide$displacement == 0.0f) {
            original.call(graphics, x0, y0, x1, y1, color);
            return;
        }

        graphics.getMatrices().pushMatrix();
        graphics.getMatrices().translate(0.0f, decide$displacement);
        try {
            original.call(graphics, x0, y0, x1, y1, color);
        } finally {
            graphics.getMatrices().popMatrix();
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
    private void decide$wrapSuperAndSuggestions(ChatScreen instance, DrawContext graphics, int mouseX, int mouseY,
                                              float delta, Operation<Void> original) {
        if (decide$displacement == 0.0f) {
            original.call(instance, graphics, mouseX, mouseY, delta);
            return;
        }

        graphics.getMatrices().pushMatrix();
        graphics.getMatrices().translate(0.0f, decide$displacement);
        try {
            original.call(instance, graphics, mouseX, mouseY, delta);
        } finally {
            graphics.getMatrices().popMatrix();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void decide$chatClosed(CallbackInfo ci) {
        decide$wasOpenedLastFrame = false;
    }
}
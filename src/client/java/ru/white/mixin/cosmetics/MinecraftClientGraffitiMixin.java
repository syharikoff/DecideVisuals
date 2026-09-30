package ru.white.mixin.cosmetics;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;
import ru.white.cosmetics.GraffitiManager;

/**
 * Перехватывает ПКМ для установки граффити.
 * <p>
 * Действие срабатывает только при надетом граффити и зажатом Ctrl, иначе
 * взаимодействие с блоками и предметами идёт как обычно.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientGraffitiMixin {

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void nightix$placeGraffiti(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.world == null || client.player == null) {
            return;
        }
        if (!isControlDown(client)) {
            return;
        }
        if (GraffitiManager.handleUse(client)) {
            ci.cancel();
        }
    }

    private static boolean isControlDown(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
}

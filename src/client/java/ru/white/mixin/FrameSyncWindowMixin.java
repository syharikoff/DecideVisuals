package ru.white.mixin;

import com.mojang.blaze3d.platform.GLX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.glfw.GLFW;
import ru.white.optimization.FrameSyncManager;

@Mixin(GLX.class)
public abstract class FrameSyncWindowMixin {

    @Inject(method = "_initGlfw", at = @At("HEAD"))
    private static void framesync$beforeGlfwInit(CallbackInfoReturnable<?> cir) {
        if (FrameSyncManager.isEnabled()) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE);
            GLFW.glfwWindowHint(GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_FALSE);
        }
    }
}

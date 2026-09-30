package ru.white.mixin;

import ru.white.manager.event_impl.EventKey;
import ru.white.manager.event_impl.EventLook;
import ru.white.manager.event_impl.MousePressEvent;
import ru.white.manager.event_impl.MouseReleaseEvent;
import ru.white.screen.editor.OverlayEditor;
import ru.white.screen.editor.OverlayEditors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(Mouse.class)
public class MouseMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        OverlayEditor editor = OverlayEditors.active();
        if (editor != null && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE)) {
            editor.rawMouseButton(editorMouseX(client.mouse.getX()), editorMouseY(client.mouse.getY()),
                    input.button(), action == GLFW.GLFW_PRESS);
        }

        if (action == 1 && client.currentScreen == null || action == 1 && client.currentScreen instanceof ChatScreen) {
            EventKey event = new EventKey(input.getKeycode());
            event.hook();
        }

        if (action == 1 && client.currentScreen == null || action == 1 && client.currentScreen instanceof ChatScreen) {

            double mouseX = client.mouse.getX();
            double mouseY = client.mouse.getY();

            double d0 = mouseX * client.getWindow().getScaledWidth()
                    / client.getWindow().getWidth();

            double d1 = mouseY * client.getWindow().getScaledHeight()
                    / client.getWindow().getHeight();


            Screen screen = client.currentScreen;
            MousePressEvent event = new MousePressEvent(input.button(), action, input.modifiers(), screen,d0,d1);

            event.hook();
        }

        if (action != 0) return; // GLFW_RELEASE
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) return;

        double x = mc.mouse.getX()
                * mc.getWindow().getScaledWidth()
                / mc.getWindow().getWidth();

        double y = mc.mouse.getY()
                * mc.getWindow().getScaledHeight()
                / mc.getWindow().getHeight();

        new MouseReleaseEvent(x, y, input.button(), mc.currentScreen).hook();

    }

    /** Редакторы тянут содержимое сырым курсором — Screen'у события мыши при этом не приходят. */
    @Inject(method = "onCursorPos", at = @At("TAIL"))
    private void onEditorCursorPos(long window, double x, double y, CallbackInfo ci) {
        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) {
            editor.rawMouseMoved(editorMouseX(x), editorMouseY(y));
        }
    }

    @Unique
    private float editorMouseX(double rawX) {
        float scaleFix = 2F / (float) client.getWindow().getScaleFactor();
        return (float) (Mouse.scaleX(client.getWindow(), rawX) / scaleFix);
    }

    @Unique
    private float editorMouseY(double rawY) {
        float scaleFix = 2F / (float) client.getWindow().getScaleFactor();
        return (float) (Mouse.scaleY(client.getWindow(), rawY) / scaleFix);
    }

    @Inject(
            method = "updateMouse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            cancellable = true
    )
    private void onLook(double timeDelta, CallbackInfo ci, double i, double j) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {

            EventLook event = new EventLook(i, j);
            event.hook();
            if (!event.isCancelled()) {
                client.player.changeLookDirection(event.yaw, event.pitch);
            }
            ci.cancel();
        }
    }

}

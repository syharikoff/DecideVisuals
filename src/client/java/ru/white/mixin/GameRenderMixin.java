package ru.white.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import ru.white.Client;
import ru.white.manager.DragComponent;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.module.impl.render.NoRender;
import ru.white.screen.GuiCloseAnimation3D;
import ru.white.screen.Menu;
import ru.white.utils.render.Render2D;
import ru.white.inventorypreset.InventoryPresetOverlay;
import ru.white.screen.HandsEditor;
import ru.white.utils.player.Spectator;
import ru.white.utils.render.RenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class GameRenderMixin {

    @Inject(method = "close", at = @At("RETURN"))
    private void onClose(CallbackInfo ci) {
        if (Client.get() != null && Client.get().render2D() != null) {
            Client.get().render2D().close();
        }
    }


    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private GuiRenderer guiRenderer;

    @Shadow
    @Final
    private FogRenderer fogRenderer;

    @Shadow
    @Final
    GuiRenderState guiState;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", shift = At.Shift.AFTER))
    private void afterGuiRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (client.world == null || client.player == null) return;
        if (isLoadingScreen(client.currentScreen)) return;
        if (client.getOverlay() != null) return;
        if (!shouldRenderOnTop(client.currentScreen)) return;

        guiState.clear();

        int mouseX = (int) client.mouse.getScaledX(client.getWindow());
        int mouseY = (int) client.mouse.getScaledY(client.getWindow());
        float tickDelta = tickCounter.getTickProgress(false);

        DrawContext context = new DrawContext(client, guiState, mouseX, mouseY);


        if (client.currentScreen instanceof Menu menu) {
            menu.renderOverlay(context, tickCounter);
        } else if (client.currentScreen instanceof ru.white.ui.lv.LvGui) {
            context.createNewRootLayer();
            Render2D.beginOverlay();
            context.getMatrices().pushMatrix();
            EventDisplay event = new EventDisplay(context, tickDelta);
            Client.get().componentManager().get(DragComponent.class).post(context.getMatrices());
            event.hook();
            Client.get().render2D().flushAll();
            context.getMatrices().popMatrix();
            Render2D.endOverlay();
        }

        if (client.currentScreen instanceof InventoryScreen) {
            InventoryPresetOverlay.render(context, mouseX, mouseY);
        }





        guiRenderer.render(fogRenderer.getFogBuffer(FogRenderer.FogType.NONE));
    }


    @ModifyExpressionValue(method = "renderWorld", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F", ordinal = 0))
    private float onNauseaDistortion(float original) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isEnabled() && noRender.ignoreZalupa.getValue()) {
            return 0.0F;
        }
        return original;
    }

    // убирает анимацию тотема (и прочих floating-предметов) на весь экран
    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    private void onShowFloatingItem(net.minecraft.item.ItemStack stack, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isEnabled() && noRender.ignoreTotemPop.getValue()) {
            ci.cancel();
        }
    }
    @Unique
    private boolean shouldRenderOnTop(Screen screen) {
        if (screen == null) return true;
        if (screen instanceof Menu) return true;
        if (screen instanceof ru.white.ui.lv.LvGui) return true;
        return screen instanceof InventoryScreen;
    }

    @Unique
    private boolean isLoadingScreen(Screen screen) {
        if (screen == null) return false;
        String className = screen.getClass().getSimpleName().toLowerCase();
        String fullName = screen.getClass().getName().toLowerCase();
        if (className.contains("loading")) return true;
        if (className.contains("progress")) return true;
        if (className.contains("connecting")) return true;
        if (className.contains("downloading")) return true;
        if (className.contains("terrain")) return true;
        if (className.contains("generating")) return true;
        if (className.contains("saving")) return true;
        if (className.contains("reload")) return true;
        if (className.contains("resource")) return true;
        if (className.contains("pack")) return true;
        if (fullName.contains("mojang")) return true;
        return false;
    }

    @Shadow
    public abstract float getFov(Camera camera, float tickDelta, boolean changingFov);

    @Shadow
    @Final
    private Camera camera;

    /** В режиме .spec рука от первого лица не рисуется — как в гм 3. */
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void spectator$hideHand(CallbackInfo ci) {
        if (Spectator.isActive()) {
            ci.cancel();
        }
    }

    /** Кадр с руками для редактора: снимок берётся сразу после их отрисовки. */
    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;draw()V",
                    shift = At.Shift.AFTER
            )
    )
    private void captureHandsForEditor(RenderTickCounter tickCounter, CallbackInfo ci) {
        HandsEditor editor = HandsEditor.getInstance();
        if (editor.isActive()) {
            editor.captureAfterHands();
        }
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = {"ldc=hand"}))
    public void hookWorldRender(RenderTickCounter tickCounter, CallbackInfo ci, @Local(ordinal = 0) Matrix4f projection, @Local(ordinal = 1) Matrix4f view, @Local(ordinal = 0) float tickDelta, @Local MatrixStack matrixStack) {
        if (client.world == null || client.player == null) return;

        if (GuiCloseAnimation3D.isActive()) {
            GuiCloseAnimation3D.render(view, tickDelta);
        }

        MatrixStack worldSpaceStack = new MatrixStack();


        worldSpaceStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        worldSpaceStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));

        RenderUtil.Render3D.lastProjMat.set(client.gameRenderer.getBasicProjectionMatrix(getFov(camera, tickDelta, true)));
        RenderUtil.Render3D.lastModMat.set(RenderSystem.getModelViewMatrix());
        RenderUtil.Render3D.lastWorldSpaceMatrix.set(worldSpaceStack.peek().getPositionMatrix());



    }
}

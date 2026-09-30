package ru.white.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import ru.white.manager.event_impl.GlassHandsRenderEvent;
import ru.white.manager.event_impl.HandAnimationEvent;
import ru.white.manager.event_impl.RenderItemEvent;
import ru.white.module.impl.render.GlassHands;
import ru.white.module.impl.render.SwingAnimation;
import ru.white.module.impl.render.Hands;
import ru.white.module.impl.utils.consumable.ConsumableHandler;
import ru.white.screen.HandsEditor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.white.module.impl.render.NoRender;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @WrapWithCondition(
        method = "renderMapInOneHand",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V", ordinal = 1)
    )
    private boolean cancelMapRotationX(MatrixStack matrices, Quaternionfc quaternion) {
        return false;
    }

    @WrapWithCondition(
        method = "renderMapInOneHand",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V", ordinal = 2)
    )
    private boolean cancelMapRotationY(MatrixStack matrices, Quaternionfc quaternion) {
        return false;
    }



    @WrapOperation(
        method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;" +
                "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;" +
                "Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
        at = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V",
                ordinal = 0),
        slice = @Slice(
                from = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;getHandRenderType(Lnet/minecraft/client/network/ClientPlayerEntity;)Lnet/minecraft/client/render/item/HeldItemRenderer$HandRenderType;"),
                to = @At(value = "FIELD", target = "Lnet/minecraft/client/render/item/HeldItemRenderer$HandRenderType;renderMainHand:Z")
        )
    )
    private void replacePitchRotation(MatrixStack matrices, Quaternionfc quaternion, Operation<Void> original,
            @Local(ordinal = 3) float h) {

        if(!NoRender.getInstance().removeCamreZalupa.getValue()) {
            float cameraPitch = MinecraftClient.getInstance().gameRenderer.getCamera().getPitch();
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((cameraPitch - h) * 0.1F));
        }
    }

    @WrapOperation(
        method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;" +
                "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;" +
                "Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
        at = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V",
                ordinal = 1),
        slice = @Slice(
                from = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;getHandRenderType(Lnet/minecraft/client/network/ClientPlayerEntity;)Lnet/minecraft/client/render/item/HeldItemRenderer$HandRenderType;"),
                to = @At(value = "FIELD", target = "Lnet/minecraft/client/render/item/HeldItemRenderer$HandRenderType;renderMainHand:Z")
        )
    )
    private void replaceYawRotation(MatrixStack matrices, Quaternionfc quaternion, Operation<Void> original,
            @Local(ordinal = 4) float i) {

        if(!NoRender.getInstance().removeCamreZalupa.getValue()) {
        float cameraYaw = MinecraftClient.getInstance().gameRenderer.getCamera().getYaw();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((cameraYaw - i) * 0.1F));
        }
    }



    /** В редакторе руки показываются всегда, даже если ванильно их бы не рисовали. */
    @ModifyExpressionValue(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;" +
                    "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;" +
                    "Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer$HandRenderType;renderMainHand:Z")
    )
    private boolean white$showEditorMainHand(boolean original) {
        return HandsEditor.getInstance().isActive() || original;
    }

    @ModifyExpressionValue(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;" +
                    "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;" +
                    "Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer$HandRenderType;renderOffHand:Z")
    )
    private boolean white$showEditorOffHand(boolean original) {
        return HandsEditor.getInstance().isActive() || original;
    }

    /** Пустые руки настраивать нечем, поэтому в редакторе подставляются предметы-превью. */
    @ModifyArg(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;" +
                    "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;" +
                    "Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(" +
                            "Lnet/minecraft/client/network/AbstractClientPlayerEntity;FF" +
                            "Lnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;F" +
                            "Lnet/minecraft/client/util/math/MatrixStack;" +
                            "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
                    ordinal = 0),
            index = 5
    )
    private ItemStack white$useEditorMainHandItem(ItemStack original) {
        if (!HandsEditor.getInstance().isActive()) return original;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return original;
        return HandsEditor.getInstance().previewStackFor(player.getMainArm());
    }

    @ModifyArg(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;" +
                    "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;" +
                    "Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(" +
                            "Lnet/minecraft/client/network/AbstractClientPlayerEntity;FF" +
                            "Lnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;F" +
                            "Lnet/minecraft/client/util/math/MatrixStack;" +
                            "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
                    ordinal = 1),
            index = 5
    )
    private ItemStack white$useEditorOffHandItem(ItemStack original) {
        if (!HandsEditor.getInstance().isActive()) return original;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return original;
        return HandsEditor.getInstance().previewStackFor(player.getMainArm().getOpposite());
    }

    @Inject(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("HEAD")
    )
    private void onRenderItemPre(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, ClientPlayerEntity player, int light, CallbackInfo ci) {
        HandsEditor editor = HandsEditor.getInstance();
        if (editor.isActive()) {
            editor.captureBeforeHands();
            return;
        }

        GlassHands glassHands = GlassHands.getInstance();
        if (glassHands != null && glassHands.isEnabled()) {
            new GlassHandsRenderEvent(GlassHandsRenderEvent.Phase.PRE, matrices, tickProgress).hook();
        }
    }

    @Inject(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("TAIL")
    )
    private void onRenderItemPost(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, ClientPlayerEntity player, int light, CallbackInfo ci) {
        // команды рук здесь ещё в очереди — снимок делает GameRenderMixin после её отправки
        if (HandsEditor.getInstance().isActive()) return;

        GlassHands glassHands = GlassHands.getInstance();
        if (glassHands != null && glassHands.isEnabled()) {
            new GlassHandsRenderEvent(GlassHandsRenderEvent.Phase.POST, matrices, tickProgress).hook();
        }
    }


    @Inject(
            method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER, ordinal = 0)
    )
    private void onRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        RenderItemEvent event = new RenderItemEvent(matrices, hand, arm);
        event.hook();

        // напрямую, а не через событие модуля: редактор двигает руки и при выключенном Hands
        if (!item.isEmpty()) {
            Hands hands = Hands.get();
            if (hands != null) hands.applyHandTranslation(matrices, arm);
        }
    }


    @WrapWithCondition(
            method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V", ordinal = 4)
    )
    private boolean cancelEquipOffsetForCustomAnim(HeldItemRenderer instance, MatrixStack matrices, Arm arm, float equipProgress) {
        if (arm != MinecraftClient.getInstance().player.getMainArm()) return true;
        SwingAnimation hands = SwingAnimation.get();
        if (hands == null || !hands.isEnabled() || !hands.auraCheck()) return true;
        return hands.swingMode.is("Отключено");
    }

    @WrapOperation(
            method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;swingArm(FLnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/util/Arm;)V")
    )
    private void handAnimationHook(HeldItemRenderer instance, float swingProgress, MatrixStack matrices, int armX, Arm arm, Operation<Void> original,
                                   @Local(ordinal = 0, argsOnly = true) AbstractClientPlayerEntity player,
                                   @Local(ordinal = 0, argsOnly = true) Hand hand,
                                   @Local(ordinal = 3, argsOnly = true) float equipProgress,
                                   @Local(ordinal = 0, argsOnly = true) ItemStack item) {
        if (item.getItem() instanceof CrossbowItem) {
            original.call(instance, swingProgress, matrices, armX, arm);
            return;
        }
        HandAnimationEvent event = new HandAnimationEvent(matrices, hand, swingProgress, equipProgress, arm);
        event.hook();
        if (!event.isCancelled()) {
            original.call(instance, swingProgress, matrices, armX, arm);
        }
    }

    @Inject(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
                    ordinal = 0
            )
    )
    public void injectBeforeRenderCrossBowItem(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        Hands viewModel = Hands.get();
        if (viewModel.shouldApplyTransforms()) {
            boolean isMainHand = hand == Hand.MAIN_HAND;
            Arm arm = isMainHand ? player.getMainArm() : player.getMainArm().getOpposite();
            viewModel.applyHandScale(matrices, arm);
            HandsEditor.getInstance().updateHandBounds(arm, matrices);
        }
    }

    @Inject(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
                    ordinal = 1
            )
    )
    public void injectBeforeRenderItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        Hands viewModel = Hands.get();
        if (viewModel.shouldApplyTransforms()) {
            boolean isMainHand = hand == Hand.MAIN_HAND;
            Arm arm = isMainHand ? player.getMainArm() : player.getMainArm().getOpposite();
            viewModel.applyHandScale(matrices, arm);
            HandsEditor.getInstance().updateHandBounds(arm, matrices);
        }
    }

    @Inject(
            method = "shouldSkipHandAnimationOnSwap",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onShouldSkipHandAnimationOnSwap(CallbackInfoReturnable<Boolean> cir) {
        if (ConsumableHandler.isEnabled() && ConsumableHandler.shouldSkipHandAnimationOnSwap()) {
            cir.setReturnValue(true);
        }
    }


}

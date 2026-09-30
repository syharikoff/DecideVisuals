package ru.white.mixin;

import com.mojang.authlib.GameProfile;
import ru.white.Client;
import ru.white.manager.event_impl.*;
import ru.white.manager.event_impl.*;
import ru.white.module.impl.player.LockSlot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static ru.white.utils.annotation.IMinecraft.mc;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity  {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        new EventUpdate().hook();
    }

    @Shadow
    public abstract boolean isUsingItem();

    public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Redirect(method = "applyMovementSpeedFactors", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec2f;multiply(F)Lnet/minecraft/util/math/Vec2f;", ordinal = 1))
    private Vec2f cancelItemSlowdown(Vec2f vec2f, float multiplier) {
        UsingItemEvent event = new UsingItemEvent(EventType.ON);
        event.hook();

        if (event.isCancelled() && this.isUsingItem() && !this.hasVehicle()) {
            return vec2f.multiply(1.0F);
        }

        return vec2f.multiply(multiplier);
    }



    @ModifyVariable(
            method = "sendSprintingPacket()V",
            at = @At(value = "STORE", ordinal = 0),
            ordinal = 0
    )
    private boolean modifySprintingFlag(boolean flag) {

        ActionEvent actionEvent = new ActionEvent(flag);
        actionEvent.hook();

        return actionEvent.isSprintState();
    }

    @Shadow public float renderPitch;
    @Shadow public float renderYaw;
    @Shadow public float lastRenderPitch;
    @Shadow public float lastRenderYaw;

    @Inject(method = "tickMovementInput", at = @At("TAIL"))
    private void fixCameraRenderAngles(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player != (Object) this) return;
        Camera camera = mc.gameRenderer.getCamera();
        renderPitch = lastRenderPitch + (camera.getPitch() - lastRenderPitch) * 0.5F;
        renderYaw   = lastRenderYaw   + (camera.getYaw()   - lastRenderYaw)   * 0.5F;
    }

    @Inject(method = "sendMovementPackets", at = @At(value = "HEAD"), cancellable = true)
    private void preMotion(CallbackInfo ci) {
        MotionEvent event = new MotionEvent(getX(), getY(), getZ(), getYaw(1), getPitch(1), isOnGround());
        event.hook();
        if (event.isCancelled()) ci.cancel();
    }

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void onDropSelectedItem(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        LockSlot lockSlot = Client.get().moduleManager().get(LockSlot.class);
        if (lockSlot == null || !lockSlot.isEnabled()) return;
        int slot = mc.player.getInventory().getSelectedSlot();
        if (lockSlot.isSlotLocked(slot) || lockSlot.isDropProtected(mc.player.getMainHandStack(), slot)) {
            cir.setReturnValue(false);
        }
    }

    @Shadow
    protected abstract void autoJump(float dx, float dz);

    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"), cancellable = true)
    public void onMoveHook(MovementType movementType, Vec3d movement, CallbackInfo ci) {
        MoveEvent event = new MoveEvent(movement);
        event.hook();
        double d = this.getX();
        double e = this.getZ();
        super.move(movementType, event.getMovement());
        this.autoJump((float) (this.getX() - d), (float) (this.getZ() - e));
        ci.cancel();
    }


}

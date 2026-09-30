package ru.white.mixin;

import ru.white.manager.event_impl.CameraPositionEvent;
import ru.white.manager.event_impl.EventRotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.impl.render.NoRender;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique
    private EventRotation night$rotationEvent;

    @Unique
    private float night$originalYaw;

    @Unique
    private float night$originalPitch;

    @Unique
    private boolean night$inverseView;

    @Inject(method = "update", at = @At("HEAD"))
    private void onUpdateHead(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        this.night$inverseView = thirdPerson && inverseView;
        if (focusedEntity != null) {

            this.night$originalYaw = focusedEntity.getYaw(tickProgress);
            this.night$originalPitch = focusedEntity.getPitch(tickProgress);
            this.night$rotationEvent = new EventRotation(this.night$originalYaw, this.night$originalPitch, tickProgress);
            night$rotationEvent.hook();
        } else {
            this.night$rotationEvent = null;
        }
    }

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void onClipToSpace(float desiredCameraDistance, CallbackInfoReturnable<Float> cir) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isEnabled() && noRender.noCameraClip.getValue()) {
            cir.setReturnValue(desiredCameraDistance);
        }
    }

    @Shadow
    public abstract void setRotation(float yaw, float pitch);

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void redirectSetRotation(Camera instance, float yaw, float pitch) {
        if (this.night$rotationEvent != null && (this.night$rotationEvent.getYaw() != this.night$originalYaw || this.night$rotationEvent.getPitch() != this.night$originalPitch)) {

            if (this.night$inverseView) {
                this.setRotation(this.night$rotationEvent.getYaw() + 180.0F, -this.night$rotationEvent.getPitch());
            } else {
                this.setRotation(this.night$rotationEvent.getYaw(), this.night$rotationEvent.getPitch());
            }
        } else {
            this.setRotation(yaw, pitch);
        }
    }


    @Shadow
    private Vec3d pos;

    @Shadow @Final
    private BlockPos.Mutable blockPos;


    @Inject(method = "setPos(Lnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"), cancellable = true)
    private void posHook(Vec3d pos, CallbackInfo ci) {
        CameraPositionEvent event = new CameraPositionEvent(pos);
        event.hook();
        this.pos = pos = event.getPos();
        blockPos.set(pos.x,pos.y,pos.z);
        ci.cancel();
    }
}

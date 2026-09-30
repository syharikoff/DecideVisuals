package ru.white.manager.rotation;


import ru.white.utils.annotation.IMinecraft;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector2f;

public class Rotation implements IMinecraft {

    @Getter
    @Setter
    public float yaw, pitch;

    public Rotation(Entity entity) {
        yaw = entity.getYaw();
        pitch = entity.getPitch();
    }
    public Rotation(float yawN, float pitchN) {
        yaw = yawN;
        pitch = pitchN;
    }


    public float getDelta(Rotation target) {
        float yawDelta = MathHelper.wrapDegrees(target.yaw - this.yaw);
        float pitchDelta = target.pitch - this.pitch;
        return (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
    }

    public double getDeltaDouble(Rotation target) {
        double yawDelta = MathHelper.wrapDegrees(target.yaw - yaw);
        double pitchDelta = MathHelper.wrapDegrees(target.pitch - pitch);
        return Math.hypot(yawDelta, pitchDelta);
    }

    public static Vector2f camera() {
        return new Vector2f(cameraYaw(), cameraPitch());
    }


    public static float cameraYaw() {
        return MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw() + (mc.options.getPerspective().isFrontView() ? 180 : 0));
    }

    public static float cameraPitch() {
        return (mc.options.getPerspective().isFrontView() ? -1 : 1) * mc.gameRenderer.getCamera().getPitch();
    }


}

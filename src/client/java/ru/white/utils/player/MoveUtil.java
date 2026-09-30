package ru.white.utils.player;

import ru.white.manager.event_impl.EventMoveInput;
import ru.white.utils.annotation.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import static net.minecraft.util.math.MathHelper.wrapDegrees;

@UtilityClass
public class MoveUtil implements IMinecraft {

    public static boolean isMoving() {
        if (mc.player == null) return false;

        return mc.player.input.getMovementInput().y != 0 || mc.player.input.getMovementInput().x != 0;
    }
    public static float getDirection() {
        float rotationYaw = mc.player.getYaw();
        float strafeFactor = 0f;

        // Проверяем нажатие клавиш вперед/назад
        if (mc.player.input.playerInput.forward())
            strafeFactor = 1;
        if (mc.player.input.playerInput.backward())
            strafeFactor = -1;

        // Проверяем нажатие клавиш влево/вправо (pressingLeft в MC означает движение влево, то есть стрейф > 0)
        if (strafeFactor == 0) {
            if (mc.player.input.playerInput.left())
                rotationYaw -= 90;

            if (mc.player.input.playerInput.right())
                rotationYaw += 90;
        } else {
            if (mc.player.input.playerInput.left())
                rotationYaw -= 45 * strafeFactor;

            if (mc.player.input.playerInput.right())
                rotationYaw += 45 * strafeFactor;
        }

        if (strafeFactor < 0)
            rotationYaw -= 180;

        return (float) Math.toRadians(rotationYaw);
    }
    public double getDegreesRelativeToView(
            Vec3d positionRelativeToPlayer,
            float yaw) {

        float optimalYaw =
                (float) Math.atan2(-positionRelativeToPlayer.x, positionRelativeToPlayer.z);
        double currentYaw = Math.toRadians(wrapDegrees(yaw));

        return Math.toDegrees(wrapDegrees((optimalYaw - currentYaw)));
    }

    public PlayerInput getDirectionalInputForDegrees(PlayerInput input, double dgs) {
        return getDirectionalInputForDegrees(input, dgs, 20.0F);
    }

    public static void setSpeed(double speed) {
        if (mc.player == null) return;

        float yaw = mc.player.getYaw();
        double rad = Math.toRadians(yaw);

        double x = 0;
        double z = 0;

        Vec2f move = mc.player.input.getMovementInput();
        float forward = move.y;
        float strafe  = move.x;

        if (forward > 0) {
            x -= Math.sin(rad) * speed;
            z += Math.cos(rad) * speed;
        }
        if (forward < 0) {
            x += Math.sin(rad) * speed;
            z -= Math.cos(rad) * speed;
        }
        if (strafe > 0) {
            x += Math.cos(rad) * speed;
            z += Math.sin(rad) * speed;
        }
        if (strafe < 0) {
            x -= Math.cos(rad) * speed;
            z -= Math.sin(rad) * speed;
        }

        if (strafe > 0 && forward > 0) {
            x = Math.cos(Math.toRadians(yaw + 45)) * speed;
            z = Math.sin(Math.toRadians(yaw + 45)) * speed;
        }
        if (strafe < 0 && forward > 0) {
            x = -Math.cos(Math.toRadians(yaw - 45)) * speed;
            z = -Math.sin(Math.toRadians(yaw - 45)) * speed;
        }
        if (strafe > 0 && forward < 0) {
            x = -Math.cos(Math.toRadians(yaw + 135)) * speed;
            z = -Math.sin(Math.toRadians(yaw + 135)) * speed;
        }
        if (strafe < 0 && forward < 0) {
            x = Math.cos(Math.toRadians(yaw - 135)) * speed;
            z = Math.sin(Math.toRadians(yaw - 135)) * speed;
        }

        mc.player.setVelocity(x, mc.player.getVelocity().y, z);
    }

    public PlayerInput getDirectionalInputForDegrees(PlayerInput input, double dgs, float deadAngle) {
        boolean forwards = input.forward();
        boolean backwards = input.backward();
        boolean left = input.left();
        boolean right = input.right();

        if (dgs >= (-90.0F + deadAngle) && dgs <= (90.0F - deadAngle)) {
            forwards = true;
        } else if (dgs < (-90.0F - deadAngle) || dgs > (90.0F + deadAngle)) {
            backwards = true;
        }

        if (dgs >= (0.0F + deadAngle) && dgs <= (180.0F - deadAngle)) {
            right = true;
        } else if (dgs >= (-180.0F + deadAngle) && dgs <= (0.0F - deadAngle)) {
            left = true;
        }

        return new PlayerInput(forwards, backwards, left, right, input.jump(), input.sneak(), input.sprint());
    }

    public float[] getMovementFromKeys() {
        float forward = 0;
        float strafe = 0;


        if (InputUtil.isKeyPressed(mc.getWindow(), mc.options.forwardKey.getDefaultKey().getCode())) {
            forward += 1.0F;
        }
        if (InputUtil.isKeyPressed(mc.getWindow(), mc.options.backKey.getDefaultKey().getCode())) {
            forward -= 1.0F;
        }
        if (InputUtil.isKeyPressed(mc.getWindow(), mc.options.leftKey.getDefaultKey().getCode())) {
            strafe += 1.0F;
        }
        if (InputUtil.isKeyPressed(mc.getWindow(), mc.options.rightKey.getDefaultKey().getCode())) {
            strafe -= 1.0F;
        }

        return new float[]{forward, strafe};
    }
    public double[] calculateDirection(double distance) {
        float[] movement = MoveUtil.getMovementFromKeys();
        return calculateDirection(
                movement[0],
                movement[1],
                distance
        );
    }
    public double[] calculateDirection(float forward, float sideways, double distance) {
        float yaw = mc.player.getYaw();
        if (forward != 0.0f) {
            if (sideways > 0.0f) {
                yaw += (forward > 0.0f) ? -45 : 45;
            } else if (sideways < 0.0f) {
                yaw += (forward > 0.0f) ? 45 : -45;
            }
            sideways = 0.0f;
            forward = (forward > 0.0f) ? 1.0f : -1.0f;
        }

        double sinYaw = Math.sin(Math.toRadians(yaw + 90.0f));
        double cosYaw = Math.cos(Math.toRadians(yaw + 90.0f));
        double xMovement = forward * distance * cosYaw + sideways * distance * sinYaw;
        double zMovement = forward * distance * sinYaw - sideways * distance * cosYaw;

        return new double[]{xMovement, zMovement};
    }


    public static void fixMovementTest(EventMoveInput event, float yaw, float targetYaw) {
        float forward = event.getForward();
        float strafe = event.getStrafe();

        double angle = wrapDegrees(Math.toDegrees(direction(mc.player.isGliding() ? yaw : targetYaw, forward, strafe)));

        if (forward == 0 && strafe == 0) {
            return;
        }

        float closestForward = 0, closestStrafe = 0, closestDifference = Float.MAX_VALUE;

        for (float predictedForward = -1F; predictedForward <= 1F; predictedForward += 1F) {
            for (float predictedStrafe = -1F; predictedStrafe <= 1F; predictedStrafe += 1F) {
                if (predictedStrafe == 0 && predictedForward == 0) continue;

                final double predictedAngle = wrapDegrees(Math.toDegrees(direction(yaw, predictedForward, predictedStrafe)));
                final double difference = Math.abs(angle - predictedAngle);

                if (difference < closestDifference) {
                    closestDifference = (float) difference;
                    closestForward = predictedForward;
                    closestStrafe = predictedStrafe;
                }
            }
        }

        event.setForward(closestForward);
        event.setStrafe(closestStrafe);
    }
    public static void fixMovement(EventMoveInput event, float yaw, float targetYaw) {
        float forward = event.getForward();
        float strafe = event.getStrafe();

        double angle = wrapDegrees(Math.toDegrees(direction(mc.player.isGliding() ? yaw : targetYaw, forward, strafe)));

        if (forward == 0 && strafe == 0) {
            return;
        }

        float closestForward = 0, closestStrafe = 0, closestDifference = Float.MAX_VALUE;

        for (float predictedForward = -1F; predictedForward <= 1F; predictedForward += 1F) {
            for (float predictedStrafe = -1F; predictedStrafe <= 1F; predictedStrafe += 1F) {
                if (predictedStrafe == 0 && predictedForward == 0) continue;

                final double predictedAngle = wrapDegrees(Math.toDegrees(direction(yaw, predictedForward, predictedStrafe)));
                final double difference = Math.abs(angle - predictedAngle);

                if (difference < closestDifference) {
                    closestDifference = (float) difference;
                    closestForward = predictedForward;
                    closestStrafe = predictedStrafe;
                }
            }
        }

        event.setForward(closestForward);
        event.setStrafe(closestStrafe);
    }
    public double direction(float rotationYaw, final float moveForward, final float moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;
        float forward = 1F;
        if (moveForward < 0F) forward = -0.5F;
        if (moveForward > 0F) forward = 0.5F;
        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;
        return Math.toRadians(rotationYaw);
    }

}

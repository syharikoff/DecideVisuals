package ru.white.utils.aura;


import ru.white.manager.rotation.Rotation;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.math.MathUtil;
import lombok.experimental.UtilityClass;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.util.math.MathHelper.clamp;

@UtilityClass
public class AuraUtil implements IMinecraft {

    public static Vec3d getNearestPoint(LivingEntity entity) {
        Vec3d pos = mc.player.getEyePos();
        return new Vec3d(MathHelper.clamp((double)pos.x, (double)entity.getBoundingBox().minX, (double)entity.getBoundingBox().maxX), MathHelper.clamp((double)pos.y, (double)entity.getBoundingBox().minY, (double)entity.getBoundingBox().maxY), MathHelper.clamp((double)pos.z, (double)entity.getBoundingBox().minZ, (double)entity.getBoundingBox().maxZ));
    }

    public double[] calculateDirection(double distance) {
        float[] movement = getMovementFromKeys();
        return calculateDirection(
                movement[0],
                movement[1],
                distance
        );
    }
    public boolean nullCheck() {
        return mc.player == null || mc.world == null;
    }
    public boolean isPlayerInWeb() {
        Box playerBox = mc.player.getBoundingBox();
        BlockPos playerPosition = mc.player.getBlockPos();

        return getNearbyBlockPositions(playerPosition).stream()
                .anyMatch(pos -> isBlockCobweb(playerBox, pos));
    }
    private boolean isBlockCobweb(Box playerBox, BlockPos pos) {
        if (!mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) {
            return false;
        }

        Box blockBox = new Box(pos);
        return playerBox.intersects(blockBox);
    }
    private List<BlockPos> getNearbyBlockPositions(BlockPos center) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = center.getX() - 2; x <= center.getX() + 2; x++) {
            for (int y = center.getY() - 1; y <= center.getY() + 4; y++) {
                for (int z = center.getZ() - 2; z <= center.getZ() + 2; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
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
    public float[] getMovementFromKeys() {
        float forward = 0;
        float strafe = 0;

        Window handle = mc.getWindow();

        if (InputUtil.isKeyPressed(handle, mc.options.forwardKey.getDefaultKey().getCode())) {
            forward += 1.0F;
        }
        if (InputUtil.isKeyPressed(handle, mc.options.backKey.getDefaultKey().getCode())) {
            forward -= 1.0F;
        }
        if (InputUtil.isKeyPressed(handle, mc.options.leftKey.getDefaultKey().getCode())) {
            strafe += 1.0F;
        }
        if (InputUtil.isKeyPressed(handle, mc.options.rightKey.getDefaultKey().getCode())) {
            strafe -= 1.0F;
        }

        return new float[]{forward, strafe};
    }

    public static float calculateCorrectYawOffset(float yaw) {

        double xDiff = mc.player.getX() - mc.player.lastX;
        double zDiff = mc.player.getZ() - mc.player.lastZ;
        float distSquared = (float) (xDiff * xDiff + zDiff * zDiff);
        float renderYawOffset = mc.player.lastBodyYaw;
        float offset = renderYawOffset;
        float yawOffsetDiff;


        if (distSquared > 0.0025000002f) {
            offset = (float) MathHelper.atan2(zDiff, xDiff) * 180.0f / (float) Math.PI - 90.0f;
        }
        if (mc.player != null && mc.player.handSwingProgress > 0.0f) {
             offset = yaw;
        }
         yawOffsetDiff = MathHelper.wrapDegrees(yaw - (renderYawOffset + MathHelper.wrapDegrees(offset - renderYawOffset) * 0.3f));
        yawOffsetDiff = MathHelper.clamp(yawOffsetDiff, -15.0f, 15.0f);
        renderYawOffset = yaw - yawOffsetDiff;
        if (yawOffsetDiff * yawOffsetDiff > 2500.0f) {
            renderYawOffset += yawOffsetDiff * 0.2f;
        }

        return renderYawOffset;
    }

    public static boolean collideWith(LivingEntity entity, float grow) {
        Box box = mc.player.getBoundingBox();
        Box targetbox = entity.getBoundingBox().expand((double)grow, 0.0, (double)grow);
        return box.maxX > targetbox.minX && box.maxY > targetbox.minY && box.maxZ > targetbox.minZ && box.minX < targetbox.maxX && box.minY < targetbox.maxY && box.minZ < targetbox.maxZ;
    }public static float getAngleDiff(float targetYaw, float currentYaw) {

        float diff = targetYaw - currentYaw;


        while (diff <= -180.0F) diff += 360.0F;
        while (diff > 180.0F) diff -= 360.0F;


        return Math.abs(diff);
    }
    public BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType, Entity entity) {
        return mc.world.raycast(new RaycastContext(start, end, shapeType, RaycastContext.FluidHandling.NONE, entity));
    }
    public static Rotation getOffset(Rotation deltaToTarget, float progress) {
        float curveStrength = 3.0f;
        float smoothness = 1.0f - (float) Math.cos(progress * Math.PI);

        float yawSign = Math.signum(deltaToTarget.getYaw());
        float pitchSign = Math.signum(deltaToTarget.getPitch());

        float offsetYaw = 0f;
        float offsetPitch = 0f;

        boolean verticalAiming = Math.abs(deltaToTarget.getYaw()) < 1.5f && Math.abs(deltaToTarget.getYaw()) > 10f;

        if (verticalAiming) {
            offsetYaw = pitchSign * curveStrength * (1f - progress);
            offsetPitch = 0f;
        } else {

            if (pitchSign > 0 && yawSign >= 0) {
                offsetYaw = -curveStrength * (1f - progress);
                offsetPitch = -curveStrength * smoothness;
            } else if (pitchSign > 0 && yawSign < 0) {
                offsetYaw = curveStrength * (1f - progress);
                offsetPitch = -curveStrength * smoothness;
            } else if (pitchSign < 0 && yawSign >= 0) {
                offsetYaw = -curveStrength * (1f - progress);
                offsetPitch = curveStrength * smoothness;
            } else if (pitchSign < 0 && yawSign < 0) {
                offsetYaw = curveStrength * (1f - progress);
                offsetPitch = curveStrength * smoothness;
            }
        }

        return new Rotation(offsetYaw, offsetPitch);
    }
    public static double getStrictDistance(Entity entity) {
        return getClosestVec(entity).length();
    }
    public Rotation cameraAngle() {return new Rotation(mc.player.getYaw(), mc.player.getPitch());}
    public static float interpolate(double oldValue, double newValue, double interpolationValue) {
        return (float)(oldValue + (newValue - oldValue) * interpolationValue);
    }
    public static boolean validDistance(Entity entity, float distance, boolean smart) {
        return getStrictDistance(entity) < distance;

    }
    public static Vec3d getClosestVec(Entity entity) {
        Vec3d eyePosVec = mc.player.getEyePos();
        return getClosestVec(eyePosVec, entity).subtract(eyePosVec);
    }

    public static Vec3d getClosestVec(Vec3d vec, Box AABB) {
        return new Vec3d(
                MathUtil.clamp(vec.x, AABB.minX, AABB.maxX),
                MathUtil.clamp(vec.y, AABB.minY, AABB.maxY),
                MathUtil.clamp(vec.z, AABB.minZ, AABB.maxZ)
        );
    }


    public static Vec3d getClosestVec(Vec3d vec, Entity entity) {
        return getClosestVec(vec, entity.getBoundingBox());
    }

    public static Vec3d getVector4(LivingEntity target) {

        double wHalf = target.getWidth() / 2;

        double yExpand = clamp(target.getY() - 6, 0, target.getHeight());

        double xExpand = clamp(mc.player.getX() - target.getX(), -wHalf, wHalf);
        double zExpand = clamp(mc.player.getZ() - target.getZ(), -wHalf, wHalf);

        return new Vec3d(
                target.getX() - mc.player.getX() + xExpand,
                target.getY() - mc.player.getY()  - 0.8F,
                target.getZ() - mc.player.getZ() + zExpand
        );
    }
    public static Vec3d getVector3(LivingEntity target) {
        double yExpand = net.minecraft.util.math.MathHelper.clamp(target.getY() - target.getY(), 0, target.getHeight());
        double xExpand = net.minecraft.util.math.MathHelper.clamp(mc.player.getX() - target.getX(), -0, 0);
        double zExpand = net.minecraft.util.math.MathHelper.clamp(mc.player.getZ() - target.getZ(), -0, 0);

        return new Vec3d(
                target.getX() - mc.player.getX() + xExpand,
                target.getY() - mc.player.getY()  - 0.8F,
                target.getZ() - mc.player.getZ() + zExpand
        );
    }


    public static Vec2f getVecTAKSA1(Vec3d targetedEntity) {
        double posX = targetedEntity.getX();
        double posY = targetedEntity.getY();
        double posZ = targetedEntity.getZ();

        double deltaX = posX - mc.player.getX();
        double deltaY = posY
                - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double deltaZ = posZ - mc.player.getZ();

        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

        return new Vec2f(yaw, pitch);
    }
    public static float wrapAngleTo180(float angle) {
        // Приводим угол к диапазону от -180 до 180
        angle %= 360.0F;

        if (angle >= 180.0F) {
            angle -= 360.0F;
        }

        if (angle < -180.0F) {
            angle += 360.0F;
        }

        return angle;
    }
    public static Vec3d getVector2(LivingEntity target) {
        double yExpand = net.minecraft.util.math.MathHelper.clamp(target.getEyeY() - target.getY(), 0, target.getHeight());
        double xExpand = net.minecraft.util.math.MathHelper.clamp(mc.player.getX() - target.getX(), -0, 0);
        double zExpand = net.minecraft.util.math.MathHelper.clamp(mc.player.getZ() - target.getZ(), -0, 0);

        return new Vec3d(
                target.getX() - mc.player.getX() + xExpand,
                target.getY() - mc.player.getEyeY() + yExpand,
                target.getZ() - mc.player.getZ() + zExpand
        );
    }

    public static Vec3d getVector(LivingEntity target) {

        double wHalf = target.getWidth() / 2;

        double yExpand = clamp(target.getEyeY() - target.getY(), 0, target.getHeight());

        double xExpand = clamp(mc.player.getX() - target.getX(), -wHalf, wHalf);
        double zExpand = clamp(mc.player.getZ() - target.getZ(), -wHalf, wHalf);

        return new Vec3d(
                target.getX() - mc.player.getX() + xExpand,
                target.getY() - mc.player.getEyeY() + yExpand,
                target.getZ() - mc.player.getZ() + zExpand
        );
    }

    public static double direction(float rotationYaw, final float moveForward, final float moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;
        float forward = 1F;
        if (moveForward < 0F) forward = -0.5F;
        if (moveForward > 0F) forward = 0.5F;
        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;
        return Math.toRadians(rotationYaw);
    }

    public Vec3d getClosestTargetPoint(Vec3d vec, Entity entity, float point) {
        if (entity == null) {
            return Vec3d.ZERO;
        }

        // В Yarn grow -> expand, но чаще используется contraction (отрицательный expand)
        Box box = entity.getBoundingBox().expand(-point);
        Vec3d center = box.getCenter();
        Vec3d closestPoint = null;
        double closestDistance = Double.MAX_VALUE;

        for (double offsetX = 0; offsetX <= (box.maxX - box.minX) / 2; offsetX += 0.1) {
            for (double offsetY = 0; offsetY <= (box.maxY - box.minY) / 2; offsetY += 0.1) {
                for (double offsetZ = 0; offsetZ <= (box.maxZ - box.minZ) / 2; offsetZ += 0.1) {
                    for (int signX : new int[]{-1, 1}) {
                        for (int signY : new int[]{-1, 1}) {
                            for (int signZ : new int[]{-1, 1}) {
                                double x = center.x + signX * offsetX;
                                double y = center.y + signY * offsetY;
                                double z = center.z + signZ * offsetZ;
                                Vec3d potentialPoint = new Vec3d(x, y, z);

                                // Твой кастомный утилит для расчета углов к точке
                                Vector2f rotation = calculate(potentialPoint);

                                // Рейтрейс (нужно адаптировать твой RayTraceUtil под 1.21)
                                HitResult result = RayTraceUtil.calculateRayTrace(
                                        6.0D,
                                        rotation.x,
                                        rotation.y,mc.player,false
                                );

                                if (result instanceof EntityHitResult entityTrace && entityTrace.getEntity().equals(entity)) {
                                    double distance = vec.distanceTo(potentialPoint);
                                    if (distance < closestDistance) {
                                        closestDistance = distance;
                                        closestPoint = potentialPoint;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (closestPoint != null) {
            return closestPoint;
        }

        // Кламп вектора к боксу
        double closestX = MathUtil.clamp(vec.x, box.minX, box.maxX);
        double closestY = MathUtil.clamp(vec.y, box.minY, box.maxY);
        double closestZ = MathUtil.clamp(vec.z, box.minZ, box.maxZ);

        return new Vec3d(closestX, closestY, closestZ);
    }


    public Vector2f calculate(final Vec3d toVec) {
        return calculate(mc.player.getEntityPos().add(0, mc.player.getEyeY(), 0), toVec);
    }

    public Vector2f calculate(final Vec3d fromVec, final Vec3d toVec) {
        final double TO_DEGREES = 180.0F / Math.PI;
        final Vec3d diff = toVec.subtract(fromVec);
        final double distance = Math.hypot(diff.x, diff.z);
        float yaw = (float) (MathHelper.atan2(diff.z, diff.x) * TO_DEGREES) - 90.0F;
        final float pitch = (float) (-(MathHelper.atan2(diff.y, distance) * TO_DEGREES));
        return new Vector2f(yaw, pitch);
    }

    public Vec3d getClosestTargetPoint(Entity entity) {
        // Получаем частичные тики в 1.21 Fabric
        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        return getClosestTargetPoint(mc.player.getCameraPosVec(tickDelta), entity,
                Math.min(entity.getWidth(), entity.getHeight()) / 4F);
    }

    public Vector4f calculateRotationFromCamera(LivingEntity target) {
        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d eyePos = mc.player.getCameraPosVec(tickDelta);
        Vec3d vec = getClosestTargetPoint(target).subtract(eyePos);

        float rawYaw = (float) MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90.0F));
        float rawPitch = (float) (-Math.toDegrees(Math.atan2(vec.y, Math.sqrt(vec.x * vec.x + vec.z * vec.z))));

        // В Fabric/Yarn yaw -> getYaw(), pitch -> getPitch()
        float yawDelta = MathHelper.wrapDegrees(rawYaw - mc.player.getYaw());
        float pitchDelta = rawPitch - mc.player.getPitch();

        return new Vector4f(rawYaw, rawPitch, yawDelta, pitchDelta);
    }

    public double calculateFOVFromCamera(LivingEntity target) {
        Vector4f rotation = calculateRotationFromCamera(target);
        // В JOML 1.21+ поля x, y, z, w доступны напрямую или через методы
        float yawDelta = rotation.z;
        float pitchDelta = rotation.w;

        return Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
    }


}

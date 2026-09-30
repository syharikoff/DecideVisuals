package ru.white.utils.aura;


import ru.white.utils.annotation.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.function.Predicate;

@UtilityClass
public class RayTraceUtil implements IMinecraft {






    public static EntityHitResult traceEntities(Entity shooter, Vec3d startVector, Vec3d endVector, Box boundingBox, Predicate<Entity> filter, double distance) {
        World world = shooter.getEntityWorld();
        double closestDistance = distance;
        Entity closestEntity = null;
        Vec3d closestHitVector = null;

        // getEntities -> getOtherEntities (обычно используется для исключения shooter, но здесь передаем вручную)
        for (Entity entity : world.getOtherEntities(shooter, boundingBox, filter)) {
            // inflate -> expand
            // getPickRadius -> getTargetingMargin
            Box entityBoundingBox = entity.getBoundingBox().expand(entity.getTargetingMargin());
            // clip -> raycast
            Optional<Vec3d> optional = entityBoundingBox.raycast(startVector, endVector);

            if (entityBoundingBox.contains(startVector) || optional.isPresent()) {
                // distanceToSqr -> squaredDistanceTo
                double distanceToHit = optional.map(startVector::squaredDistanceTo).orElse(0.0D);
                distanceToHit = Math.sqrt(distanceToHit);
                if (distanceToHit < closestDistance || closestDistance == 0.0D) {
                    if (entity.getRootVehicle() != shooter.getRootVehicle()) {
                        closestEntity = entity;
                        closestHitVector = optional.orElse(startVector);
                        closestDistance = distanceToHit;
                    }
                }
            }
        }

        return closestEntity == null ? null : new EntityHitResult(closestEntity, closestHitVector);
    }

    public HitResult calculateRayTrace(double distance, float yaw, float pitch, Entity entity, boolean ignoreBlocks) {
        // В 1.21.2+ getDeltaTracker изменился на getRenderTickCounter
        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);

        // getEyePosition -> getCameraPosVec (для интерполяции)
        Vec3d startVector = mc.player.getCameraPosVec(tickDelta);
        Vec3d directionVector = getVectorForRotation(pitch, yaw);
        // scale -> multiply
        Vec3d endVector = startVector.add(directionVector.multiply(distance));

        // ClipContext -> RaycastContext
        HitResult blockResult = traceBlock(startVector, endVector, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE);
        // getLocation -> getPos
        double entityDistance = blockResult.getPos().squaredDistanceTo(startVector);

        // expandTowards -> stretch
        Box entityBoundingBox = entity.getBoundingBox().stretch(directionVector.multiply(distance)).expand(1.0D);

        // ProjectileUtil.getEntityHitResult -> ProjectileUtil.raycast
        // canBeCollidedWith -> canHit
        EntityHitResult entityHitResult = ProjectileUtil.raycast(
                entity,
                startVector,
                endVector,
                entityBoundingBox,
                (x) -> !x.isSpectator() && x.isAlive() && x.canHit(),
                distance * distance
        );

        if (entityHitResult != null && (ignoreBlocks || entityHitResult.getPos().squaredDistanceTo(startVector) < entityDistance)) {
            return entityHitResult;
        }

        return blockResult;
    }

    public static boolean rayTraceEntity(float yaw, float pitch, double distance, Entity entity) {
        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        Vec3d eyeVec = mc.player.getCameraPosVec(tickDelta);
        Vec3d lookVec = getVectorForRotation(pitch, yaw);
        Vec3d endVec = eyeVec.add(lookVec.multiply(distance));
        if(mc.player != null && mc.player.isGliding() && entity.distanceTo(entity) > distance * 3) {
            return true;
        }

        Box entityBox = entity.getBoundingBox();
        return entityBox.contains(eyeVec) || entityBox.raycast(eyeVec, endVec).isPresent();
    }

    public static Vec3d getVectorForRotation(float pitch, float yaw) {
        float yawRadians = -yaw * ((float) Math.PI / 180) - (float) Math.PI;
        float pitchRadians = -pitch * ((float) Math.PI / 180);

        // Mth -> MathHelper
        float cosYaw = MathHelper.cos(yawRadians);
        float sinYaw = MathHelper.sin(yawRadians);
        float cosPitch = -MathHelper.cos(pitchRadians);
        float sinPitch = MathHelper.sin(pitchRadians);

        return new Vec3d(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    public HitResult traceBlock(Vec3d startVec, Vec3d endVec, RaycastContext.ShapeType blockMode, RaycastContext.FluidHandling fluidMode) {
        // level.clip -> world.raycast
        return mc.world.raycast(new RaycastContext(
                startVec,
                endVec,
                blockMode,
                fluidMode,
                mc.player)
        );
    }

    public Vec3d calculateViewVector(float yaw, float pitch) {
        float pitchRad = (float) (pitch * (Math.PI / 180F));
        float yawRad = (float) (-yaw * (Math.PI / 180F));

        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);

        return new Vec3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }
}
package ru.white.utils.aura;


import ru.white.utils.animation.Easings;
import ru.white.utils.annotation.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@UtilityClass
public class UBoxPoints implements IMinecraft {

    public static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(value, min));
    }

    public static int lerp(int a, int b, float f) {
        return a + (int) (f * (b - a));
    }

    public static double lerp(double a, double b, double f) {
        return a + f * (b - a);
    }

    public static HitResult traceBlock(Vec3d startVec, Vec3d endVec, RaycastContext.ShapeType blockMode, RaycastContext.FluidHandling fluidMode) {
        return mc.world.raycast(new RaycastContext(
                startVec,
                endVec,
                blockMode,
                fluidMode,
                mc.player)
        );
    }

    private static double getDistanceXZ(ClientPlayerEntity self, double x, double z) {
        double d0 = self.getX() - x, d1 = self.getZ() - z;
        return MathHelper.sqrt((float) (d0 * d0 + d1 * d1));
    }

    private static boolean seenOnce3(ClientPlayerEntity self, double x, double y, double z) {
        Vec3d vector3d1 = new Vec3d(x, y, z);
        return mc.world != null && traceBlock(self.getEyePos(), vector3d1, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE).getType() != HitResult.Type.BLOCK;
    }

    private static boolean seenOnceVector3d(ClientPlayerEntity self, Vec3d vec) {
        Vec3d vector3d = new Vec3d(self.getX(), self.getEyeY(), self.getZ());
        return mc.world != null && traceBlock(vector3d, vec, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE).getType() != HitResult.Type.BLOCK;
    }

    private static boolean localSeen(ClientPlayerEntity selfEntity, final Vec3d xyz, final float scale) {
        return scale == 0 ? seenOnce3(selfEntity, xyz.x, xyz.y, xyz.z) :
                seenOnce3(selfEntity, xyz.x, xyz.y, xyz.z) &&
                        seenOnce3(selfEntity, xyz.x, xyz.y + scale, xyz.z) &&
                        seenOnce3(selfEntity, xyz.x, xyz.y - scale, xyz.z) &&
                        seenOnce3(selfEntity, xyz.x + scale, xyz.y, xyz.z) &&
                        seenOnce3(selfEntity, xyz.x - scale, xyz.y, xyz.z) &&
                        seenOnce3(selfEntity, xyz.x, xyz.y, xyz.z + scale) &&
                        seenOnce3(selfEntity, xyz.x, xyz.y, xyz.z - scale);
    }

    public static List<Vec3d> entityBoxVec3dsAlternate(Box aabb) {
        final List<Vec3d> vecs = new ArrayList<>();
        double offsetXYZ = .01F;
        int maxPointsCountXZ = 17, minPointsCountXZ = 5;
        int maxPointsCountY = 24, minPointsCountY = 6;
        aabb = aabb.offset(-offsetXYZ,-offsetXYZ,-offsetXYZ);
        double[] whh = new double[]{aabb.maxX - aabb.minX, aabb.maxY - aabb.minY, (aabb.maxY - aabb.minY) / 1.05D};
        double[] xyz = new double[]{aabb.minX + whh[0] / 2.D, aabb.minY, aabb.minZ + whh[0] / 2.D};
        double[] xyz1 = new double[]{aabb.minX, aabb.minY, aabb.minZ};
        double[] xyz2 = new double[]{aabb.maxX, aabb.maxY, aabb.maxZ};
        float sqrtWHH0CubeD2 = (float) Math.sqrt(whh[0] * whh[0] + whh[0] * whh[0] + whh[0] * whh[0]) / 2.F;
        final ClientPlayerEntity me = mc.player;
        if (me == null) return null;
        final float factorCount = (float) ((1.F - Math.min(me.getEntityPos().distanceTo(new Vec3d(xyz[0], xyz[1], xyz[2])) / 5.F, 1.F)) * Math.min(me.getEntityPos().distanceTo(new Vec3d(xyz[0], me.getY(), xyz[2])) / .6F, 1.F));
        final int pointsCountXZ = lerp(minPointsCountXZ, maxPointsCountXZ, factorCount);
        final int pointsCountY = lerp(minPointsCountY, maxPointsCountY, factorCount);


        float scaleSeenCheck = .0F;
        for (final Integer xsI : IntStream.range(0, pointsCountXZ).toArray()) {
            final boolean edgeX = xsI == 0 || xsI == pointsCountXZ - 1;
            final double xs = lerp(xyz1[0], xyz2[0], xsI / (float) (pointsCountXZ - 1));
            for (final Integer zsI : IntStream.range(0, pointsCountXZ).toArray()) {
                final boolean edgeZ = zsI == 0 || zsI == pointsCountXZ - 1;
                final double zs = lerp(xyz1[2], xyz2[2], zsI / (float) (pointsCountXZ - 1));
                for (final Integer ysI : IntStream.range(0, pointsCountY).toArray()) {
                    final boolean edgeY = ysI == 0 || ysI == pointsCountY - 1;
                    final double ys = lerp(xyz1[1], xyz2[1], ysI / (float) (pointsCountY - 1));
                    final Vec3d vec = new Vec3d(xs, ys, zs);
                    if (!edgeX && !edgeZ && !edgeY || me.getEntityPos().distanceTo(vec.add(0.D, -me.getEyeHeight(EntityPose.STANDING), 0.D)) < sqrtWHH0CubeD2 || !localSeen(me, vec, scaleSeenCheck))
                        continue;
                    if (!vecs.add(vec)) break;
                }
            }
        }
        return vecs;
    }

    private static double getDistanceAtVec3dToVec3d(Vec3d first, Vec3d second) {
        final double xDiff, yDiff, zDiff;
        return Math.sqrt((xDiff = first.x - second.x) * xDiff + (yDiff = first.y - second.y) * yDiff + (zDiff = first.z - second.z) * zDiff);
    }
    public static Vec3d getBestVector3dOnEntityBox(Box aabb) {
        return getBestVector3dOnEntityBox(aabb, false);
    }
    public static Vec3d getBestVector3dOnEntityBox(Box aabb, boolean alwaysMultipoints) {
        if (aabb == null) return mc.player.getEyePos();
        double[] whh = new double[]{aabb.maxX - aabb.minX, aabb.maxY - aabb.minY, (aabb.maxY - aabb.minY) / 1.1F};
        double[] xyz = new double[]{aabb.minX + whh[0] / 2.D, aabb.minY, aabb.minZ + whh[0] / 2.D};
        double[] diffs = new double[]{mc.player.getY() - xyz[1], getDistanceXZ(mc.player, xyz[0], xyz[2])};
        double ddtn = clamp(Easings.BACK_OUT.ease((diffs[1] - whh[0] / 2.F) / (5.D + whh[0] / 2.D)), 0.1D, .95D);
        double pca = clamp(ddtn * ddtn, 0.D, 1.D);
        final double pitchPointHeight = clamp((whh[2] / 2.D * pca + (whh[2] / 2.D) * (clamp(diffs[0] + pca, 0.D, 1.D))), 0, whh[2]);
        Vec3d defaultVec = new Vec3d(xyz[0], xyz[1] + pitchPointHeight, xyz[2]);
        if (!alwaysMultipoints && !seenOnceVector3d(mc.player, defaultVec))
            defaultVec = defaultVec.add(0.D, -pitchPointHeight / 2.D, 0.D);
        if (whh[1] <= 1D || !alwaysMultipoints && seenOnceVector3d(mc.player, defaultVec)) {
            return defaultVec;
        } else {
            final List<Vec3d> normalVecs = entityBoxVec3dsAlternate(aabb);
            float factorDown = 1.F - (float) Math.max(Math.min((diffs[1] - 2.F) / 3.F, 1.F), 0.F);
            final Vec3d toSortVec = new Vec3d(mc.player.getX(), mc.player.getY() + .6F + lerp(pitchPointHeight, pitchPointHeight / 2.5F, factorDown), mc.player.getZ());
            if (normalVecs != null && normalVecs.size() > 1)
                normalVecs.sort(Comparator.comparing(vec3 -> getDistanceAtVec3dToVec3d(toSortVec, vec3)));
            return normalVecs != null && !normalVecs.isEmpty() ? normalVecs.getFirst() : defaultVec;
        }
    }
    public static Vec3d getVecNewStatic(Box aabb) {

        if (aabb == null) return mc.player.getEyePos();


        double centerX = aabb.minX + (aabb.maxX - aabb.minX) / 2.0;
        double centerY = aabb.minY + (aabb.maxY - aabb.minY) / 2.0;
        double centerZ = aabb.minZ + (aabb.maxZ - aabb.minZ) / 2.0;


        Vec3d centerVec = new Vec3d(centerX, centerY + 0.25F, centerZ);



        if (!seenOnceVector3d(mc.player, centerVec)) {


            final List<Vec3d> normalVecs = entityBoxVec3dsAlternate(aabb);
            if (normalVecs != null && !normalVecs.isEmpty()) {
                normalVecs.sort(Comparator.comparing(vec -> vec.squaredDistanceTo(mc.player.getEyePos())));
                return normalVecs.get(0);
            }
        }

        return centerVec;
    }

}
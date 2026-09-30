package ru.white.module.impl.render.richdog;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.white.module.impl.render.richdog.util.PetAnimation;

import java.util.concurrent.ThreadLocalRandom;

public final class PetBrain {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private Vec3d pos;
    private Vec3d motion = Vec3d.ZERO;
    private float direction = randomAngle();
    private float yaw;
    private float body;

    private final PetAnimation x = new PetAnimation();
    private final PetAnimation y = new PetAnimation();
    private final PetAnimation z = new PetAnimation();
    private final PetAnimation bodyAnim = new PetAnimation();
    private final PetAnimation yawAnim = new PetAnimation();
    private final PetAnimation pitchAnim = new PetAnimation();

    private boolean lay;
    private long stayingStart = 0L;

    public float prevLimbSwingAmount;
    public float limbSwingAmount;
    public float limbSwing;

    private PlayerEntity entity;

    public void setEntity(PlayerEntity entity) {
        this.entity = entity;
    }

    public void onUpdate() {
        if (entity == null || mc.world == null) {
            return;
        }

        Vec3d playerPos = entity.getEntityPos();

        if (pos == null || pos.distanceTo(playerPos) > 10) {
            pos = playerPos;
            x.animate((float) pos.x, 1);
            y.animate((float) pos.y, 1);
            z.animate((float) pos.z, 1);
        }

        motion = motion.add(0, -0.2, 0);
        Vec3d newPos = pos.add(motion);

        if (isBlockSolid(newPos.x, newPos.y, newPos.z)) {
            int blockY = (int) newPos.y;
            double correctedY = blockY + 1 + 0.1;
            newPos = new Vec3d(newPos.x, correctedY, newPos.z);
            motion = new Vec3d(motion.x, 0, motion.z);
        }

        if (collidesHorizontally(newPos)) {
            newPos = new Vec3d(pos.x, newPos.y, pos.z);
            motion = new Vec3d(-motion.x * 0.35, motion.y, -motion.z * 0.35);
            direction = randomAngle();
        }

        motion = new Vec3d(motion.x, 0, motion.z);

        if (newPos.distanceTo(playerPos) > 2) {
            motion = motion.add(playerPos.subtract(newPos).normalize());
        }

        handleRotation();

        pos = newPos;

        if (pos.distanceTo(playerPos) < 0.1) {
            direction = randomAngle();
            double xMot = -Math.sin(Math.toRadians(direction)) * 0.1;
            double zMot = Math.cos(Math.toRadians(direction)) * 0.1;
            motion = motion.add(xMot, 0, zMot);
        }

        motion = motion.multiply(0.5, 0, 0.5);

        int speed = 150;
        x.animate((float) pos.x, speed);
        y.animate((float) pos.y, speed);
        z.animate((float) pos.z, speed);

        limbTick();

        if (Math.abs(pos.x - x.get()) > 0.1F || Math.abs(pos.z - z.get()) > 0.1F) {
            stayingStart = System.currentTimeMillis();
        }
        lay = System.currentTimeMillis() - stayingStart >= 1000;
    }

    private void handleRotation() {
        if (motion.x != 0 || motion.z != 0) {
            double angle = Math.atan2(motion.z, motion.x);
            yaw = (float) Math.toDegrees(angle) - 90;
            yaw %= 360;
            if (yaw < 0) yaw += 360;
        }

        float[] rotation = lookAt(pos, entity.getEyePos());

        float gradus = lay ? 200 : 150;
        float gradus1 = lay ? 100 : 50;
        if (rotation[0] - yaw < -gradus || rotation[0] - yaw > gradus) {
            yaw = rotation[0];
        }

        float shortestYawPath = (((((yaw - body) % 360) + 540) % 360) - 180);

        if (!lay) {
            bodyAnim.animate(body + shortestYawPath, 150);
        }
        yawAnim.animate(MathHelper.clamp(rotation[0] - yaw, -gradus1, gradus1), 150);
        pitchAnim.animate(rotation[1], 150);

        body = body + shortestYawPath;
    }

    private void limbTick() {
        prevLimbSwingAmount = limbSwingAmount;
        double d0 = x.get() - pos.x;
        double d2 = z.get() - pos.z;
        float f = MathHelper.sqrt((float) (d0 * d0 + d2 * d2)) * 4.0F;
        if (f > 1.0F) f = 1.0F;
        limbSwingAmount += (f - limbSwingAmount) * 0.4F;
        limbSwing += limbSwingAmount;
    }

    public float getBody()  { return bodyAnim.get(); }
    public float getYaw()   { return yawAnim.get(); }
    public float getPitch() { return pitchAnim.get(); }
    public boolean isLay()  { return lay; }

    public Vec3d getPos() {
        return new Vec3d(x.get(), y.get(), z.get());
    }

    private static float[] lookAt(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double dxz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, dxz));
        return new float[]{yaw, pitch};
    }

    private static float randomAngle() {
        return ThreadLocalRandom.current().nextFloat() * 360.0F;
    }

    private boolean isBlockSolid(double bx, double by, double bz) {
        if (mc.world == null) return false;
        BlockPos bp = BlockPos.ofFloored(bx, by, bz);
        return !mc.world.getBlockState(bp).getCollisionShape(mc.world, bp).isEmpty();
    }

    private boolean collidesHorizontally(Vec3d position) {
        double radius = 0.35;
        double baseY = horizontalBaseY(position);
        double[] heights = {0.0, 0.45};
        for (double height : heights) {
            double sy = baseY + height;
            if (isBlockSolid(position.x + radius, sy, position.z + radius)
                    || isBlockSolid(position.x + radius, sy, position.z - radius)
                    || isBlockSolid(position.x - radius, sy, position.z + radius)
                    || isBlockSolid(position.x - radius, sy, position.z - radius)) {
                return true;
            }
        }
        return false;
    }

    private double horizontalBaseY(Vec3d position) {
        double supportRadius = 0.32;
        double probeY = position.y - 0.2;
        double highest = Double.NEGATIVE_INFINITY;
        double[][] offsets = {
                {0.0, 0.0}, {supportRadius, supportRadius}, {supportRadius, -supportRadius},
                {-supportRadius, supportRadius}, {-supportRadius, -supportRadius}
        };
        for (double[] off : offsets) {
            if (isBlockSolid(position.x + off[0], probeY, position.z + off[1])) {
                highest = Math.max(highest, Math.floor(probeY) + 1.0);
            }
        }
        if (highest == Double.NEGATIVE_INFINITY) {
            return position.y + 0.05;
        }
        return Math.max(position.y + 0.05, highest + 0.05);
    }
}

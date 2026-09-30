package ru.white.module.impl.render.custompet.control;

import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import ru.white.module.impl.render.custompet.CustomPetVariant;
import ru.white.module.impl.render.custompet.entity.CustomPetEntity;

public final class CustomPetFollowerController {
    private static final double MAX_WANDER_DISTANCE = 3.0;
    private static final double SNAP_BACK_DISTANCE = 8.0;
    private static final double FOLLOW_BREAK_DISTANCE = 7.0;
    private static final double FOLLOW_NEAR_DISTANCE = 3.0;
    private static final double TARGET_REACHED_DISTANCE = 0.9;
    private static final double PLAYER_PERSONAL_SPACE = 0.65;
    private static final double OWL_HOVER_RADIUS_MIN = 1.6;
    private static final double OWL_HOVER_RADIUS_MAX = 2.8;
    private static final double OWL_HOVER_HEIGHT_MIN = 1.6;
    private static final double OWL_HOVER_HEIGHT_MAX = 3.2;
    private static final int OWL_FLIGHT_COOLDOWN_MIN = 300;
    private static final int OWL_FLIGHT_COOLDOWN_MAX = 640;
    private static final int OWL_FLIGHT_DURATION_MIN = 90;
    private static final int OWL_FLIGHT_DURATION_MAX = 190;
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();
    private CustomPetEntity pet;
    private boolean petIsOwl;
    private Vec3d roamTarget;
    private Vec3d airHoverOffset;
    private Vec3d airSmoothedTarget;
    private Vec3d airFlightDirection;
    private boolean airChasing;
    private int idleTicks;
    private int followRefreshTicks;
    private int followSide = 1;
    private int ticks;
    private int flightTicksRemaining;
    private int nextFlightAtTick;
    private int gazeBuildTicks;
    private int gazeLookActiveTicks;
    private int gazeCooldownTicks;

    public CustomPetFollowerController() {
    }

    public void reset() {
        this.pet = null;
        this.roamTarget = null;
        this.airHoverOffset = null;
        this.airSmoothedTarget = null;
        this.airFlightDirection = null;
        this.airChasing = false;
        this.idleTicks = 0;
        this.followRefreshTicks = 0;
        this.followSide = 1;
        this.flightTicksRemaining = 0;
    }

    private void ensurePet(PlayerEntity playerEntity, CustomPetVariant customPetVariant, boolean bl) {
        if (this.pet != null && this.pet.getEntityWorld() == this.mc.world && !this.pet.isRemoved()) {
            this.pet.setPetVariant(customPetVariant);
            this.pet.setOwl(bl);
            this.petIsOwl = bl;
        } else {
            this.reset();
            this.petIsOwl = bl;
            this.pet = new CustomPetEntity(this.mc.world);
            this.pet.setPetVariant(customPetVariant);
            this.pet.setOwl(bl);
            this.nextFlightAtTick = this.ticks + this.randomBetween(OWL_FLIGHT_COOLDOWN_MIN, OWL_FLIGHT_COOLDOWN_MAX);
            this.roamTarget = this.pickRoamTarget(playerEntity, true);
            this.airHoverOffset = null;
            this.airSmoothedTarget = null;
            this.airFlightDirection = null;
            this.airChasing = false;
            this.idleTicks = this.randomBetween(18, 34);
            this.followRefreshTicks = 0;
            this.followSide = this.random.nextBoolean() ? 1 : -1;
            this.pet.snapTo(this.roamTarget, this.random.nextFloat() * 360.0F);
        }
    }

    private void tickPet(PlayerEntity playerEntity, CustomPetVariant customPetVariant) {
        if (this.pet != null) {
            this.pet.setPetVariant(customPetVariant);
            Vec3d vec3d = this.pet.getEntityPos();
            Vec3d vec3d2 = this.snapCurrentToGround(vec3d, playerEntity.getY());
            Vec3d vec3d3 = playerEntity.getEntityPos();
            double d2 = vec3d.squaredDistanceTo(vec3d3);
            double d3 = Math.sqrt(d2);
            double d4 = playerEntity.getVelocity().horizontalLength();
            boolean bl = customPetVariant != null && customPetVariant.isRobot();
            if (bl) {
                this.tickRobotFlight(playerEntity, vec3d, vec3d3, d3, d4);
            } else {
                this.ticks++;
                boolean bl3 = playerEntity.isGliding() || playerEntity.getAbilities().flying;
                boolean bl4 = false;
                if (this.petIsOwl) {
                    if (this.flightTicksRemaining > 0) {
                        this.flightTicksRemaining--;
                        bl4 = true;
                        if (this.flightTicksRemaining == 0) {
                            this.nextFlightAtTick = this.ticks + this.randomBetween(OWL_FLIGHT_COOLDOWN_MIN, OWL_FLIGHT_COOLDOWN_MAX);
                        }
                    } else if (!bl3 && this.ticks >= this.nextFlightAtTick && d4 < 0.12 && d3 < 8.0) {
                        this.flightTicksRemaining = this.randomBetween(OWL_FLIGHT_DURATION_MIN, OWL_FLIGHT_DURATION_MAX);
                        this.nextFlightAtTick = this.ticks + this.flightTicksRemaining + this.randomBetween(OWL_FLIGHT_COOLDOWN_MIN, OWL_FLIGHT_COOLDOWN_MAX);
                        bl4 = true;
                    }
                }

                boolean bl5 = bl3 || bl4;
                boolean bl6 = d4 > 0.025 || Math.abs(playerEntity.getVelocity().y) > 0.05;
                boolean bl8 = d2 > FOLLOW_BREAK_DISTANCE * FOLLOW_BREAK_DISTANCE || bl6 && d2 > FOLLOW_NEAR_DISTANCE * FOLLOW_NEAR_DISTANCE;
                boolean bl9 = this.isRainingAbove(vec3d);
                if (d2 > SNAP_BACK_DISTANCE * SNAP_BACK_DISTANCE) {
                    this.roamTarget = this.pickRoamTarget(playerEntity, true);
                    this.airFlightDirection = null;
                    this.airChasing = false;
                    this.flightTicksRemaining = 0;
                    this.idleTicks = this.randomBetween(12, 22);
                    this.followRefreshTicks = 0;
                    this.pet.snapTo(this.roamTarget, this.pet.getYaw());
                    this.pet.setBehavior(this.roamTarget, 0.0, false, bl9, false, 1.0);
                } else if (this.pet.isMovementBlocked() && d2 <= FOLLOW_BREAK_DISTANCE * FOLLOW_BREAK_DISTANCE) {
                    this.roamTarget = null;
                    this.airFlightDirection = null;
                    this.airChasing = false;
                    this.followRefreshTicks = 0;
                    if (this.idleTicks <= 0) {
                        this.idleTicks = this.randomBetween(18, 38);
                    }
                    this.idleTicks--;
                    this.pet.setBehavior(vec3d2, 0.0, false, bl9, false, 1.0);
                } else if (!bl5 && !bl8 && d4 < 0.02 && d2 < 7.29) {
                    this.followRefreshTicks = 0;
                    this.roamTarget = null;
                    this.airFlightDirection = null;
                    this.airChasing = false;
                    this.pet.overrideLookYaw((float) Math.toDegrees(MathHelper.atan2(playerEntity.getZ() - vec3d.z, playerEntity.getX() - vec3d.x)) - 90.0F, 1.0F);
                    if (this.idleTicks <= 0) {
                        this.idleTicks = this.randomBetween(24, 52);
                    }
                    this.idleTicks--;
                    this.pet.setBehavior(vec3d2, 0.0, false, bl9, false, 1.0);
                } else {
                    if (bl5) {
                        this.idleTicks = 0;
                        boolean isMovingAir = d4 > 0.08 || Math.abs(playerEntity.getVelocity().y) > 0.08;
                        Vec3d vec3d4;
                        if (isMovingAir) {
                            this.followRefreshTicks = 0;
                            this.airHoverOffset = null;
                            vec3d4 = this.computeAirFollowTarget(playerEntity, d3, d4);
                        } else {
                            if (this.airHoverOffset != null && this.followRefreshTicks > 0) {
                                this.followRefreshTicks--;
                            } else {
                                this.airHoverOffset = this.pickAirHoverOffset(playerEntity, vec3d);
                                this.followRefreshTicks = this.randomBetween(20, 42);
                            }
                            vec3d4 = this.computeAirIdleTarget(playerEntity);
                        }

                        if (this.airSmoothedTarget == null) {
                            this.airSmoothedTarget = vec3d;
                        }

                        double d = Math.clamp(
                            0.07
                                + playerEntity.getVelocity().length() * (playerEntity.isGliding() ? 0.025 : 0.05)
                                + Math.max(0.0, d3 - 1.5) * 0.012,
                            0.07,
                            playerEntity.isGliding() ? 0.2 : 0.28
                        );
                        this.roamTarget = this.airSmoothedTarget = this.airSmoothedTarget.lerp(vec3d4, d);
                    } else if (bl8) {
                        this.airHoverOffset = null;
                        this.airSmoothedTarget = null;
                        this.airFlightDirection = null;
                        this.airChasing = false;
                        this.idleTicks = 0;
                        if (this.followRefreshTicks > 0 && this.roamTarget != null) {
                            this.followRefreshTicks--;
                        } else {
                            if (d3 < 4.5 && this.random.nextFloat() < 0.08F) {
                                this.followSide *= -1;
                            }

                            Vec3d vec3d5 = this.computeFollowTarget(playerEntity, d3, d4);
                            float f = (float) Math.clamp(0.22 + Math.max(0.0, d3 - 2.0) * 0.05 + d4 * 0.35, 0.22, 0.62);
                            this.roamTarget = this.roamTarget == null ? vec3d5 : this.roamTarget.lerp(vec3d5, f);
                            this.followRefreshTicks = d3 > 7.0 ? 1 : 2;
                        }
                    } else {
                        this.airHoverOffset = null;
                        this.airSmoothedTarget = null;
                        this.airFlightDirection = null;
                        this.airChasing = false;
                        this.followRefreshTicks = 0;
                        if (this.idleTicks > 0) {
                            this.idleTicks--;
                            this.pet.setBehavior(vec3d2, 0.0, false, bl9, false, 1.0);
                            return;
                        }

                        if (this.roamTarget == null || this.shouldPickNewTarget(playerEntity, vec3d)) {
                            if (this.random.nextFloat() < 0.24F) {
                                this.roamTarget = null;
                                this.idleTicks = this.randomBetween(18, 42);
                                this.pet.setBehavior(vec3d2, 0.0, false, bl9, false, 1.0);
                                return;
                            }

                            this.followSide = this.random.nextBoolean() ? 1 : -1;
                            boolean fast = vec3d.squaredDistanceTo(vec3d3) > 16.0 || playerEntity.getVelocity().horizontalLengthSquared() > 0.04;
                            this.roamTarget = this.pickRoamTarget(playerEntity, fast);
                        }
                    }

                    if (this.roamTarget == null) {
                        this.idleTicks = this.randomBetween(18, 36);
                        this.pet.setBehavior(vec3d2, 0.0, false, bl9, false, 1.0);
                    } else {
                        Vec3d vec3d6 = this.roamTarget;
                        Vec3d vec3d7 = vec3d6.subtract(vec3d);
                        double d = Math.hypot(vec3d7.x, vec3d7.z);
                        double d5 = vec3d7.length();
                        if (bl5) {
                            StatusEffectInstance statusEffectInstance = playerEntity.getStatusEffect(StatusEffects.SPEED);
                            int n = statusEffectInstance != null ? statusEffectInstance.getAmplifier() + 1 : 0;
                            double d6 = playerEntity.getVelocity().length();
                            double d7 = playerEntity.isGliding() ? 0.82 : 0.48;
                            double d8 = playerEntity.isGliding() ? 1.28 : 0.78;
                            boolean bl11 = d3 > 4.0 || d6 > 1.2 || d4 > 0.55;
                            if (bl11 || d5 >= d8) {
                                this.airChasing = true;
                            } else if (d5 <= d7) {
                                this.airChasing = false;
                            }

                            double d9 = Math.clamp(
                                0.22
                                    + d6 * (playerEntity.isGliding() ? 2.45 : 1.65)
                                    + Math.max(0.0, d3 - 1.0) * (playerEntity.isGliding() ? 0.24 : 0.18)
                                    + n * 0.07
                                    + (playerEntity.isSprinting() ? 0.08 : 0.0),
                                0.16,
                                playerEntity.isGliding()
                                    ? (d3 > 18.0 ? 6.8 : (d3 > 12.0 ? 5.0 : (d3 > 7.0 ? 3.6 : 1.9)))
                                    : (d3 > 16.0 ? 3.2 : (d3 > 10.0 ? 2.4 : (d3 > 6.0 ? 1.55 : 0.95)))
                            );
                            double d10 = Math.clamp(
                                1.15 + Math.max(0.0, d3 - 1.2) * 0.22 + d6 * (playerEntity.isGliding() ? 1.8 : 1.35) + n * 0.16,
                                1.0,
                                playerEntity.isGliding() ? 3.1 : 2.4
                            );
                            this.pet.setBehavior(vec3d6, d9, this.airChasing, false, true, d10);
                        } else if (d <= TARGET_REACHED_DISTANCE && Math.abs(vec3d7.y) <= 0.45 && !bl8) {
                            this.roamTarget = null;
                            this.idleTicks = this.randomBetween(18, 42);
                            this.pet.setBehavior(vec3d2, 0.0, false, bl9, false, 1.0);
                        } else {
                            StatusEffectInstance statusEffectInstance = playerEntity.getStatusEffect(StatusEffects.SPEED);
                            int n = statusEffectInstance != null ? statusEffectInstance.getAmplifier() + 1 : 0;
                            double d11 = bl8 ? Math.clamp((d - 0.4) / 1.8, 0.12, 1.0) : 1.0;
                            double d12 = Math.clamp(
                                    0.055
                                        + d4 * 0.31
                                        + Math.max(0.0, d3 - 1.8) * 0.072
                                        + n * 0.028
                                        + (playerEntity.isSprinting() ? 0.028 : 0.0),
                                    0.045,
                                    d3 > 8.5 ? 0.52 : (d3 > 6.0 ? 0.42 : (d3 > 4.0 ? 0.31 : 0.22))
                                )
                                * d11;
                            double d13 = Math.clamp(
                                    1.0 + Math.max(0.0, d3 - 1.4) * 0.14 + d4 * 0.88 + n * 0.12 + (playerEntity.isSprinting() ? 0.06 : 0.0),
                                    0.95,
                                    1.85
                                )
                                * (bl8 ? Math.clamp(d11 + 0.3, 0.4, 1.0) : 1.0);
                            this.pet.setBehavior(vec3d6, d12, d12 > 0.02, bl9, false, d13);
                            this.pushOutOfPlayer(playerEntity);
                        }
                    }
                }
            }
        }
    }

    public CustomPetEntity getPet() {
        return this.pet;
    }

    private Vec3d computeFollowTarget(PlayerEntity playerEntity, double d, double d2) {
        Vec3d vec3d = playerEntity.getVelocity();
        Vec3d vec3d2 = new Vec3d(vec3d.x, 0.0, vec3d.z);
        if (vec3d2.lengthSquared() < 1.0E-4) {
            float f = playerEntity.getYaw() * (float) (Math.PI / 180.0);
            vec3d2 = new Vec3d(-MathHelper.sin(f), 0.0, MathHelper.cos(f));
        }

        vec3d2 = vec3d2.normalize();
        double d3 = Math.clamp(0.6 + d2 * 7.0 + Math.max(0.0, d - 3.0) * 0.35, 0.6, 3.0);
        Vec3d vec3d3 = playerEntity.getEntityPos().add(playerEntity.getVelocity().multiply(d3));
        double d4 = d > 5.5 ? 0.65 : 1.45;
        double d5 = d > 5.5 ? 0.0 : 0.55 * this.followSide;
        Vec3d vec3d4 = new Vec3d(-vec3d2.z, 0.0, vec3d2.x);
        Vec3d vec3d5 = vec3d3.subtract(vec3d2.multiply(d4)).add(vec3d4.multiply(d5));
        double d6 = this.findGroundY(vec3d5.x, vec3d5.z, playerEntity.getY());
        return enforcePersonalSpace(new Vec3d(vec3d5.x, d6, vec3d5.z), playerEntity);
    }

    private boolean isRainingAbove(Vec3d vec3d) {
        return this.mc.world != null && this.mc.world.hasRain(BlockPos.ofFloored(vec3d.x, vec3d.y + 1.1, vec3d.z));
    }

    private Vec3d pickRobotRoamTarget(PlayerEntity playerEntity) {
        double d = this.random.nextDouble() * (float) (Math.PI * 2);
        double d2 = 2.0 + this.random.nextDouble() * 4.0;
        double d3 = playerEntity.getX() + Math.cos(d) * d2;
        double d4 = playerEntity.getZ() + Math.sin(d) * d2;
        double d5 = playerEntity.getY() + 1.0 + this.random.nextDouble() * 3.0;
        double d6 = this.ceilingClearance(d3, d4, d5, playerEntity.getY());
        return enforcePersonalSpace(new Vec3d(d3, Math.max(d5, d6), d4), playerEntity);
    }

    private double ceilingClearance(double d, double d2, double d3, double d4) {
        if (this.mc.world == null) {
            return d3;
        } else {
            int n = MathHelper.floor(d);
            int n2 = MathHelper.floor(d2);
            int n3 = MathHelper.floor(d3);
            BlockPos.Mutable mutable = new BlockPos.Mutable(n, n3, n2);

            for (int i = n3; i <= n3 + 3; i++) {
                mutable.setY(i);
                BlockState blockState = this.mc.world.getBlockState(mutable);
                VoxelShape voxelShape = blockState.getCollisionShape(this.mc.world, mutable);
                if (!voxelShape.isEmpty()) {
                    return i + voxelShape.getMax(Direction.Axis.Y) + 0.6;
                }
            }

            return d3;
        }
    }

    private static Vec3d enforcePersonalSpace(Vec3d vec3d, PlayerEntity playerEntity) {
        double d2 = vec3d.x - playerEntity.getX();
        double d;
        double d3 = d2 * d2 + (d = vec3d.z - playerEntity.getZ()) * d;
        if (d3 >= PLAYER_PERSONAL_SPACE * PLAYER_PERSONAL_SPACE) {
            return vec3d;
        } else {
            double d4 = Math.sqrt(d3);
            if (d4 < 1.0E-4) {
                float f = playerEntity.getYaw() * (float) (Math.PI / 180.0);
                d2 = -MathHelper.sin(f);
                d = MathHelper.cos(f);
                d4 = 1.0;
            }

            double d5 = PLAYER_PERSONAL_SPACE / d4;
            return new Vec3d(playerEntity.getX() + d2 * d5, vec3d.y, playerEntity.getZ() + d * d5);
        }
    }

    private void tickRobotFlight(PlayerEntity playerEntity, Vec3d vec3d, Vec3d vec3d2, double d, double d2) {
        if (d > SNAP_BACK_DISTANCE) {
            Vec3d vec3d5 = this.pickRobotRoamTarget(playerEntity);
            this.airSmoothedTarget = vec3d5;
            this.roamTarget = vec3d5;
            this.airFlightDirection = null;
            this.followRefreshTicks = 0;
            this.idleTicks = this.randomBetween(30, 60);
            this.pet.snapTo(vec3d5, this.pet.getYaw());
            this.pet.setBehavior(vec3d5, 0.0, false, false, true, 1.0);
        } else {
            Vec3d vec3d6 = playerEntity.getVelocity();
            Vec3d vec3d7 = new Vec3d(vec3d6.x, 0.0, vec3d6.z);
            if (vec3d7.lengthSquared() < 1.0E-4) {
                float f = playerEntity.getYaw() * (float) (Math.PI / 180.0);
                vec3d7 = new Vec3d(-MathHelper.sin(f), 0.0, MathHelper.cos(f));
            }

            vec3d7 = vec3d7.normalize();
            this.airFlightDirection = this.airFlightDirection == null ? vec3d7 : this.airFlightDirection.lerp(vec3d7, 0.18).normalize();
            boolean bl3 = d2 > 0.08;
            boolean bl2 = bl3 && d > 4.0 || d > 15.0;
            if (bl2) {
                this.roamTarget = null;
                this.idleTicks = 0;
            }

            boolean bl;
            Vec3d vec3d3;
            if (bl2) {
                Vec3d vec3d8 = this.airFlightDirection;
                Vec3d vec3d9 = new Vec3d(-vec3d8.z, 0.0, vec3d8.x);
                double d5 = d > 5.0 ? 0.5 : 1.2;
                double d4 = 1.3 * this.followSide;
                double d3 = Math.clamp(d2 * 4.5, 0.0, 2.2);
                Vec3d vec3d4 = vec3d2.add(vec3d8.multiply(d3 - d5)).add(vec3d9.multiply(d4));
                double d6 = playerEntity.getY() + 1.5;
                double d7 = this.ceilingClearance(vec3d4.x, vec3d4.z, d6, playerEntity.getY());
                vec3d3 = enforcePersonalSpace(new Vec3d(vec3d4.x, Math.max(d6, d7), vec3d4.z), playerEntity);
                bl = true;
            } else if (this.idleTicks > 0) {
                this.idleTicks--;
                if (this.idleTicks <= 0) {
                    this.roamTarget = null;
                }

                vec3d3 = this.roamTarget != null ? this.roamTarget : vec3d;
                bl = false;
            } else if (this.roamTarget != null) {
                double d8 = vec3d.distanceTo(this.roamTarget);
                if (d8 < 1.5) {
                    this.roamTarget = vec3d;
                    this.idleTicks = this.randomBetween(60, 140);
                    vec3d3 = vec3d;
                    bl = false;
                } else {
                    vec3d3 = this.roamTarget;
                    bl = true;
                }
            } else {
                vec3d3 = this.roamTarget = this.pickRobotRoamTarget(playerEntity);
                bl = true;
            }

            double d9 = Math.sin(playerEntity.age * 0.075 + this.followSide * 1.7) * 0.55;
            double d5 = Math.sin(playerEntity.age * 0.125 + this.followSide * 0.4) * 0.2;
            double d4 = d9 + d5;
            double d3 = bl ? 0.0 : Math.sin(playerEntity.age * 0.018 + this.followSide * 0.9) * 0.1;
            Vec3d vec3d4 = new Vec3d(-this.airFlightDirection.z, 0.0, this.airFlightDirection.x);
            Vec3d vec3d10 = new Vec3d(vec3d3.x + vec3d4.x * d3, vec3d3.y + d4, vec3d3.z + vec3d4.z * d3);
            if (this.airSmoothedTarget == null) {
                this.airSmoothedTarget = vec3d;
            }

            double d10 = bl ? Math.clamp(0.1 + d2 * 0.08 + Math.max(0.0, d - 1.5) * 0.018, 0.1, 0.34) : 0.07;
            this.airSmoothedTarget = this.airSmoothedTarget.lerp(vec3d10, d10);
            Vec3d vec3d11 = this.airSmoothedTarget.subtract(vec3d2);
            if (vec3d11.length() > 17.5) {
                this.airSmoothedTarget = vec3d2.add(vec3d11.normalize().multiply(17.5));
            }

            double d11 = vec3d.distanceTo(this.airSmoothedTarget);
            double d12 = bl ? Math.clamp(0.08 + d11 * 0.06 + d2 * 0.8 + (playerEntity.isSprinting() ? 0.06 : 0.0), 0.06, bl2 ? 1.2 : 0.25) : 0.04;
            double d13 = Math.clamp(1.0 + d11 * 0.08 + d2 * 0.6, 1.0, 1.8);
            this.tickGazeReaction(playerEntity, vec3d);
            this.pet.setBehavior(this.airSmoothedTarget, d12, bl || d11 > 0.3, false, true, d13);
            this.pushOutOfPlayer(playerEntity);
        }
    }

    private Vec3d pickAirHoverOffset(PlayerEntity playerEntity, Vec3d vec3d) {
        if (this.petIsOwl) {
            double d = this.random.nextDouble() * (float) (Math.PI * 2);
            double d2 = MathHelper.lerp(this.random.nextDouble(), OWL_HOVER_RADIUS_MIN, OWL_HOVER_RADIUS_MAX);
            double d3 = MathHelper.lerp(this.random.nextDouble(), OWL_HOVER_HEIGHT_MIN, OWL_HOVER_HEIGHT_MAX);
            return new Vec3d(Math.cos(d) * d2, d3, Math.sin(d) * d2);
        } else {
            Vec3d vec3d2 = vec3d.subtract(playerEntity.getEntityPos());
            Vec3d vec3d3 = new Vec3d(vec3d2.x, 0.0, vec3d2.z);
            double d = vec3d3.length();
            if (!(d < 0.9) && !(d > 2.5)) {
                Vec3d vec3d4 = vec3d3.normalize().multiply(MathHelper.clamp(d, 1.25, 1.95));
                double d6 = MathHelper.clamp(vec3d2.y, 0.24, 0.46);
                return new Vec3d(vec3d4.x, d6, vec3d4.z);
            } else {
                double d4 = this.random.nextDouble() * (float) (Math.PI * 2);
                double d5 = MathHelper.lerp(this.random.nextDouble(), 1.35, 1.95);
                return new Vec3d(Math.cos(d4) * d5, MathHelper.lerp(this.random.nextDouble(), 0.28, 0.44), Math.sin(d4) * d5);
            }
        }
    }

    private int randomBetween(int n, int n2) {
        return n + this.random.nextInt(n2 - n + 1);
    }

    private Vec3d pickRoamTarget(PlayerEntity playerEntity, boolean bl) {
        Vec3d vec3d = playerEntity.getVelocity();
        double d = vec3d.horizontalLengthSquared() > 0.0025 ? Math.atan2(vec3d.z, vec3d.x) : this.random.nextDouble() * (float) (Math.PI * 2);
        double d2 = d + MathHelper.lerp(this.random.nextDouble(), -1.65, 1.65);
        double d3 = bl ? MathHelper.lerp(this.random.nextDouble(), 1.3, 2.4) : MathHelper.lerp(this.random.nextDouble(), 1.8, 3.0);
        double d4 = playerEntity.getX() + Math.cos(d2) * d3;
        double d5 = playerEntity.getZ() + Math.sin(d2) * d3;
        double d6 = this.findGroundY(d4, d5, playerEntity.getY());
        return enforcePersonalSpace(new Vec3d(d4, d6, d5), playerEntity);
    }

    private Vec3d snapCurrentToGround(Vec3d vec3d, double d) {
        double d2 = this.findGroundY(vec3d.x, vec3d.z, Math.max(vec3d.y, d));
        return new Vec3d(vec3d.x, d2, vec3d.z);
    }

    private boolean shouldPickNewTarget(PlayerEntity playerEntity, Vec3d vec3d) {
        if (this.roamTarget == null) {
            return true;
        } else {
            return this.roamTarget.squaredDistanceTo(playerEntity.getEntityPos()) > MAX_WANDER_DISTANCE * MAX_WANDER_DISTANCE
                || vec3d.squaredDistanceTo(playerEntity.getEntityPos()) > FOLLOW_BREAK_DISTANCE * FOLLOW_BREAK_DISTANCE
                    && vec3d.squaredDistanceTo(this.roamTarget) > 25.0;
        }
    }

    private Vec3d computeAirFollowTarget(PlayerEntity playerEntity, double d, double d2) {
        Vec3d vec3d = playerEntity.getVelocity();
        double d3 = vec3d.length();
        Vec3d vec3d2 = new Vec3d(vec3d.x, 0.0, vec3d.z);
        if (vec3d2.lengthSquared() < 1.0E-4) {
            float f = playerEntity.getYaw() * (float) (Math.PI / 180.0);
            vec3d2 = new Vec3d(-MathHelper.sin(f), 0.0, MathHelper.cos(f));
        }

        vec3d2 = vec3d2.normalize();
        if (this.airFlightDirection != null && !(this.airFlightDirection.lengthSquared() < 1.0E-4)) {
            double d4 = playerEntity.isGliding() ? 0.14 : 0.26;
            this.airFlightDirection = this.airFlightDirection.lerp(vec3d2, d4);
            if (this.airFlightDirection.lengthSquared() < 1.0E-4) {
                this.airFlightDirection = vec3d2;
            }
        } else {
            this.airFlightDirection = vec3d2;
        }

        Vec3d vec3d3 = this.airFlightDirection.normalize();
        Vec3d vec3d4 = new Vec3d(-vec3d3.z, 0.0, vec3d3.x);
        boolean bl = this.mc.options.getPerspective().isFirstPerson();
        double d5 = (playerEntity.isGliding() ? 0.82 : 0.65) * this.followSide;
        double d6 = (playerEntity.isGliding() ? 1.45 : 1.05) + Math.min(d * 0.08, playerEntity.isGliding() ? 0.55 : 0.35);
        if (playerEntity.isGliding() && bl) {
            d5 *= 1.18;
            d6 += 0.42;
        }

        double d7 = playerEntity.isGliding() ? 0.2 : 0.45;
        double d8 = Math.clamp(
            0.45 + d2 * (playerEntity.isGliding() ? 5.8 : 4.8) + Math.max(0.0, d3 - 0.8) * (playerEntity.isGliding() ? 0.45 : 0.35),
            0.45,
            playerEntity.isGliding() ? 2.8 : 1.6
        );
        Vec3d vec3d5 = playerEntity.getEntityPos().add(vec3d3.multiply(d8));
        double d9 = Math.sin((playerEntity.age + this.followSide * 7) * 0.1) * 0.028;
        return vec3d5.subtract(vec3d3.multiply(d6)).add(vec3d4.multiply(d5)).add(0.0, playerEntity.getY() - vec3d5.y + d7 + d9, 0.0);
    }

    private void pushOutOfPlayer(PlayerEntity playerEntity) {
        if (this.pet != null) {
            double d2 = this.pet.getX() - playerEntity.getX();
            double d;
            double d3 = d2 * d2 + (d = this.pet.getZ() - playerEntity.getZ()) * d;
            if (!(d3 >= PLAYER_PERSONAL_SPACE * PLAYER_PERSONAL_SPACE)) {
                double d4 = Math.sqrt(d3);
                if (d4 < 1.0E-4) {
                    float f = playerEntity.getYaw() * (float) (Math.PI / 180.0);
                    d2 = -MathHelper.sin(f);
                    d = MathHelper.cos(f);
                    d4 = 1.0;
                }

                double d5 = PLAYER_PERSONAL_SPACE / d4;
                double d6 = playerEntity.getX() + d2 * d5;
                double d7 = playerEntity.getZ() + d * d5;
                this.pet.setPos(d6, this.pet.getY(), d7);
            }
        }
    }

    private Vec3d computeAirIdleTarget(PlayerEntity playerEntity) {
        Vec3d vec3d = this.petIsOwl ? new Vec3d(1.9 * this.followSide, 1.6, 0.0) : new Vec3d(1.6 * this.followSide, 0.35, 0.0);
        Vec3d vec3d2 = this.airHoverOffset != null ? this.airHoverOffset : vec3d;
        double d = this.petIsOwl ? 0.25 : 0.09;
        double d2 = Math.sin((playerEntity.age + this.followSide * 7) * 0.085) * d;
        return playerEntity.getEntityPos().add(vec3d2.x, vec3d2.y + d2, vec3d2.z);
    }

    private void tickGazeReaction(PlayerEntity playerEntity, Vec3d vec3d) {
        if (this.pet != null) {
            Vec3d vec3d2 = playerEntity.getRotationVec(1.0F);
            Vec3d vec3d3 = vec3d.subtract(playerEntity.getEntityPos());
            double d = Math.hypot(vec3d3.x, vec3d3.z);
            boolean bl = false;
            if (d > 0.05 && d < 3.5) {
                Vec3d vec3d4 = new Vec3d(vec3d2.x, 0.0, vec3d2.z);
                Vec3d vec3d5 = new Vec3d(vec3d3.x, 0.0, vec3d3.z);
                if (vec3d4.lengthSquared() > 1.0E-4 && vec3d5.lengthSquared() > 1.0E-4) {
                    vec3d4 = vec3d4.normalize();
                    vec3d5 = vec3d5.normalize();
                    double d2 = vec3d4.x * vec3d5.x + vec3d4.z * vec3d5.z;
                    bl = d2 > 0.88;
                }
            }

            if (this.gazeCooldownTicks > 0) {
                this.gazeCooldownTicks--;
            }

            if (this.gazeLookActiveTicks > 0) {
                this.gazeLookActiveTicks--;
                float f = (float) Math.toDegrees(MathHelper.atan2(playerEntity.getZ() - vec3d.z, playerEntity.getX() - vec3d.x)) - 90.0F;
                this.pet.overrideLookYaw(f, 1.0F);
                if (this.gazeLookActiveTicks == 0) {
                    this.gazeCooldownTicks = this.randomBetween(80, 160);
                }
            } else {
                this.pet.overrideLookYaw(0.0F, 0.0F);
                if (bl && this.gazeCooldownTicks <= 0) {
                    this.gazeBuildTicks++;
                    if (this.gazeBuildTicks >= 25) {
                        this.gazeBuildTicks = 0;
                        if (this.random.nextFloat() < 0.65F) {
                            this.gazeLookActiveTicks = this.randomBetween(40, 70);
                        } else {
                            this.gazeCooldownTicks = this.randomBetween(40, 90);
                        }
                    }
                } else if (!bl) {
                    this.gazeBuildTicks = 0;
                }
            }
        }
    }

    private double findGroundY(double d, double d2, double d3) {
        if (this.mc.world == null) {
            return d3;
        } else {
            int n = MathHelper.floor(d);
            int n2 = MathHelper.floor(d2);
            int n3 = MathHelper.floor(d3);
            BlockPos.Mutable mutable = new BlockPos.Mutable(n, n3 + 2, n2);

            for (int i = n3 + 2; i >= n3 - 6; i--) {
                mutable.setY(i);
                BlockState blockState = this.mc.world.getBlockState(mutable);
                VoxelShape voxelShape = blockState.getCollisionShape(this.mc.world, mutable);
                if (!voxelShape.isEmpty()) {
                    return i + voxelShape.getMax(Direction.Axis.Y);
                }
            }

            return d3;
        }
    }

    public void tick(PlayerEntity playerEntity, CustomPetVariant customPetVariant, boolean bl) {
        if (playerEntity != null && playerEntity.getEntityWorld() != null && this.mc.world != null) {
            this.ensurePet(playerEntity, customPetVariant, bl);
            this.tickPet(playerEntity, customPetVariant);
        } else {
            this.reset();
        }
    }
}
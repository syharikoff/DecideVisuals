package ru.white.module.impl.render.custompet.entity;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import ru.white.module.impl.render.custompet.CustomPetVariant;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CustomPetEntity extends FrogEntity implements GeoEntity {
    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(-20000);
    private static final EntityDimensions ZERO_DIMENSIONS = EntityDimensions.fixed(0.01F, 0.01F);
    private static final float GROUND_FULL_SPEED_ANGLE = 6.0F;
    private static final float GROUND_MIN_SPEED_ANGLE = 84.0F;
    private static final float AIR_START_MOVE_ANGLE = 9.0F;
    private static final double MOVEMENT_ANIMATION_THRESHOLD_SQR = 2.5E-5;
    private static final double MOVEMENT_VERTICAL_THRESHOLD = 0.003;
    private static SoundEvent ambientSound;
    private static SoundEvent stepSound;
    private static boolean soundsResolved;
    private static final RawAnimation IDLE = RawAnimation.begin().thenPlay("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenPlay("walk");
    private static final RawAnimation RAIN_IDLE = RawAnimation.begin().thenPlay("idle_holding_1");
    private static final RawAnimation RAIN_WALK = RawAnimation.begin().thenPlay("walk_holding_1");
    private static final RawAnimation HAT_IDLE = RawAnimation.begin().thenPlay("idle_holding_hat");
    private static final RawAnimation HAT_WALK = RawAnimation.begin().thenPlay("walk_holding_hat");
    private static final RawAnimation FISHERMAN_IDLE = RawAnimation.begin().thenPlay("idle_holding_fisherman");
    private static final RawAnimation FISHERMAN_WALK = RawAnimation.begin().thenPlay("walk_holding_fisherman");
    private static final RawAnimation AIR_IDLE = RawAnimation.begin().thenPlay("idle_holding_2");
    private static final RawAnimation AIR_WALK = RawAnimation.begin().thenPlay("walk_holding_2");
    private static final RawAnimation ROBOT_IDLE = RawAnimation.begin().thenPlay("robot_idle");
    private static final RawAnimation ROBOT_FLY = RawAnimation.begin().thenPlay("robot_fly");
    private static final RawAnimation OWL_IDLE = RawAnimation.begin().thenPlay("animation.owl_jump_rope.skip_idle");
    private static final RawAnimation OWL_WALK = RawAnimation.begin().thenPlay("animation.owl_jump_rope.walk_skip");
    private static final RawAnimation OWL_RUN = RawAnimation.begin().thenPlay("animation.owl_jump_rope.run_skip");
    private static final RawAnimation OWL_FLY = RawAnimation.begin().thenPlay("animation.owl_jump_rope.fly");
    private static final double OWL_RUN_ANIMATION_SPEED = 1.4;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private RawAnimation cachedAnimation;
    private Vec3d desiredPosition = Vec3d.ZERO;
    private double desiredSpeed;
    private boolean desiredMoving;
    private boolean desiredUmbrella;
    private boolean desiredAirborne;
    private CustomPetVariant variant = CustomPetVariant.NITWIT;
    private boolean owl;
    private int robotType;
    private boolean moving;
    private boolean usingUmbrella;
    private boolean airborneMode;
    private int movingTicks;
    private int ambientSoundCooldown;
    private int stepSoundCooldown;
    private int jumpCooldown;
    private int blockedTicks;
    private int pauseTicks;
    private int sideStepDir;
    private int sideStepTicks;
    private float targetYaw;
    private float smoothedYaw;
    private float lookOverrideYaw;
    private float lookOverrideWeight;
    private double animationSpeed = 1.0;
    private double currentAnimationSpeed = 1.0;
    private double currentGroundSpeed;
    private double verticalVelocity;
    private double lastHorizontalDistance = Double.MAX_VALUE;
    private Vec3d airMotion = Vec3d.ZERO;
    private int animationTick;

    public CustomPetEntity(World world) {
        super(EntityType.FROG, world);
        this.noClip = false;
        this.setNoGravity(false);
        this.setAiDisabled(true);
        this.setId(NEXT_ENTITY_ID.getAndDecrement());
        this.setUuid(UUID.randomUUID());
        this.setVelocity(Vec3d.ZERO);
        this.targetYaw = this.getYaw();
        this.smoothedYaw = this.getYaw();
        this.ambientSoundCooldown = 70;
        this.verticalVelocity = 0.0;
    }

    private <E extends GeoAnimatable> PlayState predicate(AnimationTest<E> animationTest) {
        AnimationController<E> animationController = animationTest.controller();
        animationController.setAnimationSpeed(this.currentAnimationSpeed);
        RawAnimation rawAnimation;
        if (this.owl) {
            rawAnimation = this.airborneMode ? OWL_FLY : (!this.moving ? OWL_IDLE : (this.currentAnimationSpeed >= 1.4 ? OWL_RUN : OWL_WALK));
        } else if (this.variant.isRobot()) {
            rawAnimation = !this.moving && !this.airborneMode ? ROBOT_IDLE : ROBOT_FLY;
        } else if (this.airborneMode) {
            rawAnimation = AIR_IDLE;
        } else {
            rawAnimation = switch (this.variant) {
                case FISHERMAN -> this.usingUmbrella ? (this.moving ? FISHERMAN_WALK : FISHERMAN_IDLE) : (this.moving ? AIR_WALK : AIR_IDLE);
                case GARDENER, SORCERER -> this.usingUmbrella ? (this.moving ? HAT_WALK : HAT_IDLE) : (this.moving ? WALK : IDLE);
                case DEFAULT, NITWIT, MERCHANT -> this.usingUmbrella ? (this.moving ? RAIN_WALK : RAIN_IDLE) : (this.moving ? WALK : IDLE);
                case ROBOT -> this.moving ? ROBOT_FLY : ROBOT_IDLE;
            };
        }

        if (rawAnimation != this.cachedAnimation) {
            this.cachedAnimation = rawAnimation;
            animationController.setAnimation(rawAnimation);
        }
        return PlayState.CONTINUE;
    }

    private double square(double value) {
        return value * value;
    }

    public boolean isOwl() {
        return this.owl;
    }

    public void setOwl(boolean owl) {
        this.owl = owl;
    }

    private static SoundEvent stepSound() {
        resolveSounds();
        return stepSound;
    }

    public void snapTo(Vec3d position, float yaw) {
        this.setPos(position.x, position.y, position.z);
        this.lastX = position.x;
        this.lastY = position.y;
        this.lastZ = position.z;
        this.lastYaw = yaw;
        this.lastPitch = 0.0F;
        this.moving = false;
        this.usingUmbrella = false;
        this.airborneMode = false;
        this.movingTicks = 0;
        this.stepSoundCooldown = 0;
        this.ambientSoundCooldown = 40;
        this.jumpCooldown = 0;
        this.blockedTicks = 0;
        this.pauseTicks = 0;
        this.sideStepTicks = 0;
        this.targetYaw = yaw;
        this.smoothedYaw = yaw;
        this.animationSpeed = 1.0;
        this.currentAnimationSpeed = 1.0;
        this.currentGroundSpeed = 0.0;
        this.verticalVelocity = 0.0;
        this.lastHorizontalDistance = Double.MAX_VALUE;
        this.airMotion = Vec3d.ZERO;
        this.setVelocity(Vec3d.ZERO);
        this.setYaw(yaw);
        this.setHeadYaw(yaw);
        this.setBodyYaw(yaw);
        this.lookOverrideYaw = 0.0F;
        this.lookOverrideWeight = 0.0F;
        this.cachedAnimation = null;
    }

    @Override
    protected float getMaxRelativeHeadRotation() {
        return 0.0F;
    }

    @Override
    public void tick() {
        this.animationTick++;
        this.setNoGravity(this.desiredAirborne);
        super.tick();
        this.tickCustomMovement();
    }

    @Override
    public float getStepHeight() {
        return this.airborneMode ? 0.0F : 1.0F;
    }

    @Override
    protected EntityDimensions getBaseDimensions(EntityPose pose) {
        return ZERO_DIMENSIONS;
    }

    @Override
    public boolean shouldRender(double distance) {
        return true;
    }

    @Override
    public boolean isInteractable() {
        return false;
    }

    public float getYaw(float tickDelta) {
        return MathHelper.lerpAngleDegrees(tickDelta, this.lastYaw, this.getYaw());
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void pushAwayFrom(Entity entity) {
    }

    @Override
    public boolean isCollidable(Entity entity) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public PistonBehavior getPistonBehavior() {
        return PistonBehavior.IGNORE;
    }

    public boolean isPetMoving() {
        return this.moving;
    }

    public int getAnimationAge() {
        return this.animationTick;
    }

    public double getGroundSurfaceY(double x, double y, double z) {
        World world = this.getEntityWorld();
        if (world == null) {
            return y;
        } else {
            int floorX = MathHelper.floor(x);
            int floorZ = MathHelper.floor(z);
            int floorY = MathHelper.floor(y);
            BlockPos.Mutable mutable = new BlockPos.Mutable(floorX, floorY + 1, floorZ);

            for (int i = floorY + 1; i >= floorY - 5; i--) {
                mutable.set(floorX, i, floorZ);
                BlockState blockState = world.getBlockState(mutable);
                VoxelShape voxelShape = blockState.getCollisionShape(world, mutable);
                if (!voxelShape.isEmpty()) {
                    return i + voxelShape.getMax(Direction.Axis.Y);
                }
            }

            return y;
        }
    }

    public double getCurrentAnimationSpeed() {
        return this.currentAnimationSpeed;
    }

    public void setRobotType(int type) {
        this.robotType = type;
    }

    public boolean shouldUseUmbrella() {
        return this.usingUmbrella;
    }

    public boolean isAirborneMode() {
        return this.airborneMode;
    }

    public void setPetVariant(CustomPetVariant variant) {
        this.variant = variant == null ? CustomPetVariant.NITWIT : variant;
    }

    private static SoundEvent ambientSound() {
        resolveSounds();
        return ambientSound;
    }

    public int getRobotType() {
        return this.robotType;
    }

    public boolean isMovementBlocked() {
        return this.pauseTicks > 0 || this.blockedTicks >= 3;
    }

    private void tickClientAudio(boolean moving, double distanceSqr) {
        World world = this.getEntityWorld();
        if (world != null && !this.owl) {
            if (!this.variant.isRobot()) {
                if (this.ambientSoundCooldown > 0) {
                    this.ambientSoundCooldown--;
                }
                if (this.stepSoundCooldown > 0) {
                    this.stepSoundCooldown--;
                }

                if (moving && distanceSqr > 4.0E-4 && this.isOnGround() && this.stepSoundCooldown <= 0) {
                    SoundEvent sound = stepSound();
                    if (sound != null) {
                        world.playSoundClient(this.getX(), this.getY(), this.getZ(), sound, this.getSoundCategory(), 0.55F, 0.94F + this.random.nextFloat() * 0.12F, false);
                    }
                    this.stepSoundCooldown = 8 + this.random.nextInt(4);
                }

                if (!moving && this.ambientSoundCooldown <= 0) {
                    SoundEvent sound = ambientSound();
                    if (sound != null) {
                        world.playSoundClient(this.getX(), this.getY(), this.getZ(), sound, this.getSoundCategory(), 0.75F, 0.94F + this.random.nextFloat() * 0.14F, false);
                    }
                    this.ambientSoundCooldown = 120 + this.random.nextInt(100);
                }
            }
        }
    }

    public CustomPetVariant getPetVariant() {
        return this.variant;
    }

    private void snapToGroundIfClose() {
        World world = this.getEntityWorld();
        if (world != null) {
            double x = this.getX();
            double y = this.getY();
            double z = this.getZ();
            int floorX = MathHelper.floor(x);
            int floorZ = MathHelper.floor(z);
            int floorY = MathHelper.floor(y + 0.2);
            BlockPos.Mutable mutable = new BlockPos.Mutable(floorX, floorY + 1, floorZ);

            for (int i = floorY + 1; i >= floorY - 4; i--) {
                mutable.set(floorX, i, floorZ);
                BlockState blockState = world.getBlockState(mutable);
                VoxelShape voxelShape = blockState.getCollisionShape(world, mutable);
                if (!voxelShape.isEmpty()) {
                    double surfaceY = i + voxelShape.getMax(Direction.Axis.Y);
                    double delta = y - surfaceY;
                    if (delta >= -0.45 && delta <= 0.72) {
                        this.setPos(x, surfaceY, z);
                        this.verticalVelocity = 0.0;
                        this.fallDistance = 0.0;
                        this.setVelocity(Vec3d.ZERO);
                        return;
                    }
                }
            }
        }
    }

    private static void resolveSounds() {
        if (!soundsResolved) {
            soundsResolved = true;
            try {
                ambientSound = SoundEvent.of(Identifier.of("wvisual", "entity.ribbit.ambient"));
                stepSound = SoundEvent.of(Identifier.of("wvisual", "entity.ribbit.step"));
            } catch (Throwable throwable) {
                ambientSound = null;
                stepSound = null;
            }
        }
    }

    public void setBehavior(Vec3d position, double speed, boolean moving, boolean umbrella, boolean airborne, double animationSpeed) {
        if (position.squaredDistanceTo(this.desiredPosition) > 0.2) {
            this.blockedTicks = 0;
            this.pauseTicks = 0;
            this.lastHorizontalDistance = Double.MAX_VALUE;
        }

        this.desiredPosition = position;
        this.desiredSpeed = speed;
        this.desiredMoving = moving;
        this.desiredUmbrella = umbrella;
        this.desiredAirborne = airborne;
        this.animationSpeed = animationSpeed;
    }

    private void tickAirMovement() {
        Vec3d startPos = this.getEntityPos();
        Vec3d toTarget = this.desiredPosition.subtract(startPos);
        Vec3d horizontal = new Vec3d(toTarget.x, 0.0, toTarget.z);
        double horizontalDistance = horizontal.horizontalLength();
        double targetDistance = toTarget.length();
        double verticalDelta = Math.abs(toTarget.y);
        boolean wantsMove = this.desiredMoving && targetDistance > 0.02;
        if (horizontalDistance > 1.0E-4) {
            float targetYaw = (float) Math.toDegrees(MathHelper.atan2(horizontal.z, horizontal.x)) - 90.0F;
            this.targetYaw = MathHelper.stepUnwrappedAngleTowards(this.targetYaw, targetYaw, 10.5F);
        }

        if (this.lookOverrideWeight > 0.001F) {
            float weight = wantsMove ? 0.15F : 1.0F;
            float step = 4.5F * this.lookOverrideWeight * weight;
            this.targetYaw = MathHelper.stepUnwrappedAngleTowards(this.targetYaw, this.lookOverrideYaw, step);
        }

        this.smoothedYaw = MathHelper.stepUnwrappedAngleTowards(this.smoothedYaw, this.targetYaw, wantsMove ? 14.0F : 6.0F);
        float yawDelta = Math.abs(MathHelper.wrapDegrees(this.targetYaw - this.smoothedYaw));
        boolean movingForward = wantsMove && (yawDelta <= 9.0F || targetDistance <= 0.35);
        this.movingTicks = 0;
        this.jumpCooldown = 0;
        this.blockedTicks = 0;
        this.pauseTicks = 0;
        this.verticalVelocity = 0.0;
        boolean hoverIdle = horizontalDistance <= 0.16 && verticalDelta <= 0.012;
        boolean hoverApproach = horizontalDistance <= 0.42 && verticalDelta <= 0.09;
        Vec3d motion = Vec3d.ZERO;
        if (movingForward && targetDistance > 1.0E-5 && !hoverIdle) {
            double speedMultiplier = MathHelper.clamp((targetDistance - 0.2) / 1.6, 0.08, 1.0);
            double speed = Math.clamp(this.desiredSpeed * speedMultiplier, 0.012, 6.5);
            motion = toTarget.normalize().multiply(Math.min(targetDistance, speed));
        } else if (!this.desiredMoving && verticalDelta > 0.001) {
            double speed = MathHelper.clamp(verticalDelta * 0.38, 0.0025, 0.018);
            motion = new Vec3d(0.0, Math.copySign(Math.min(verticalDelta, speed), toTarget.y), 0.0);
        }

        double lerpFactor = Math.clamp(0.12 + targetDistance * 0.06 + this.desiredSpeed * 0.028, 0.12, 0.38);
        this.airMotion = this.airMotion.lerp(motion, lerpFactor);
        if (!this.desiredMoving) {
            this.airMotion = new Vec3d(this.airMotion.x * 0.4, this.airMotion.y, this.airMotion.z * 0.4);
        }

        double damp = hoverIdle ? 0.18 : (hoverApproach ? 0.42 : (targetDistance < 1.5 ? 0.78 : 0.9));
        boolean verticalOnly = !this.desiredMoving && verticalDelta > 0.001;
        if ((!movingForward || motion.lengthSquared() < 1.0E-6) && !verticalOnly) {
            this.airMotion = this.airMotion.multiply(damp);
        } else if (verticalOnly) {
            this.airMotion = new Vec3d(this.airMotion.x, this.airMotion.y * 0.96, this.airMotion.z);
        }

        if ((hoverIdle && verticalDelta <= 0.0015) || this.airMotion.lengthSquared() < 4.0E-5) {
            this.airMotion = Vec3d.ZERO;
        }

        Vec3d movement = this.airMotion.lengthSquared() > 0.0 ? (this.airMotion.length() > targetDistance ? toTarget : this.airMotion) : Vec3d.ZERO;
        this.move(MovementType.SELF, movement);
        if (this.horizontalCollision && this.desiredMoving) {
            this.move(MovementType.SELF, new Vec3d(0.0, 0.12, 0.0));
        }

        Vec3d newPos = this.getEntityPos();
        double movedSquared = this.square(newPos.x - startPos.x) + this.square(newPos.z - startPos.z);
        this.moving = wantsMove && (movedSquared > 2.5E-5 || Math.abs(newPos.y - startPos.y) > 0.003);
        this.lastHorizontalDistance = wantsMove ? horizontalDistance : Double.MAX_VALUE;
        double targetAnimSpeed = this.moving ? Math.max(1.0, this.animationSpeed) : 1.0;
        this.currentAnimationSpeed = this.currentAnimationSpeed + (targetAnimSpeed - this.currentAnimationSpeed) * (this.moving ? 0.2 : 0.16);
        this.setVelocity(Vec3d.ZERO);
        this.setYaw(this.smoothedYaw);
        this.setHeadYaw(this.smoothedYaw);
        this.setBodyYaw(this.smoothedYaw);
        this.tickClientAudio(false, 0.0);
    }

    public void overrideLookYaw(float yaw, float weight) {
        this.lookOverrideYaw = yaw;
        this.lookOverrideWeight = Math.max(0.0F, Math.min(1.0F, weight));
    }

    private void tickCustomMovement() {
        boolean wasAirborne = this.airborneMode;
        this.airborneMode = this.desiredAirborne;
        this.usingUmbrella = this.desiredUmbrella && !this.airborneMode && !this.variant.isRobot();
        this.setNoGravity(this.airborneMode);
        if (!this.airborneMode && (wasAirborne || !this.desiredMoving)) {
            this.snapToGroundIfClose();
        }

        if (this.jumpCooldown > 0) {
            this.jumpCooldown--;
        }

        if (this.pauseTicks > 0) {
            this.pauseTicks--;
            this.moving = false;
            this.movingTicks = 0;
            this.currentGroundSpeed = 0.0;
            this.verticalVelocity = 0.0;
            this.airMotion = Vec3d.ZERO;
            this.setVelocity(Vec3d.ZERO);
            this.currentAnimationSpeed = this.currentAnimationSpeed + (1.0 - this.currentAnimationSpeed) * 0.28;
            this.lastYaw = this.smoothedYaw;
            this.setYaw(this.smoothedYaw);
            this.setHeadYaw(this.smoothedYaw);
            this.setBodyYaw(this.smoothedYaw);
            this.tickClientAudio(false, 0.0);
        } else if (this.airborneMode) {
            this.tickAirMovement();
        } else {
            this.airMotion = Vec3d.ZERO;
            Vec3d startPos = this.getEntityPos();
            Vec3d toTarget = this.desiredPosition.subtract(startPos);
            Vec3d horizontal = new Vec3d(toTarget.x, 0.0, toTarget.z);
            double horizontalDistance = horizontal.horizontalLength();
            boolean wantsMove = this.desiredMoving && horizontalDistance > 0.045;
            if (wantsMove) {
                float targetYaw = (float) Math.toDegrees(MathHelper.atan2(horizontal.z, horizontal.x)) - 90.0F;
                float turnRate = (float) Math.clamp(9.5 + horizontalDistance * 2.4 + this.desiredSpeed * 68.0, 9.5, 24.0);
                this.targetYaw = MathHelper.stepUnwrappedAngleTowards(this.targetYaw, targetYaw, turnRate);
            } else if (this.lookOverrideWeight > 0.001F) {
                this.targetYaw = MathHelper.stepUnwrappedAngleTowards(this.targetYaw, this.lookOverrideYaw, 6.0F + 6.0F * this.lookOverrideWeight);
            }

            float yawTurnRate = wantsMove ? (float) Math.clamp(8.5 + horizontalDistance * 1.8 + this.desiredSpeed * 86.0, 9.0, 22.0) : 4.8F;
            this.smoothedYaw = MathHelper.stepUnwrappedAngleTowards(this.smoothedYaw, this.targetYaw, yawTurnRate);
            float yawDelta = Math.abs(MathHelper.wrapDegrees(this.targetYaw - this.smoothedYaw));
            double speedMultiplier = wantsMove ? MathHelper.clamp(1.0 - Math.max(0.0, (double) (yawDelta - 6.0F)) / 78.0, 0.18, 1.0) : 0.0;
            double distanceMultiplier = wantsMove ? MathHelper.clamp((horizontalDistance - 0.04) / 1.6, 0.34, 1.0) : 0.0;
            double desiredGroundSpeed = wantsMove ? Math.min(horizontalDistance, this.desiredSpeed * distanceMultiplier * speedMultiplier) : 0.0;
            double lerpFactor = wantsMove ? Math.clamp(0.18 + this.desiredSpeed * 0.95 + horizontalDistance * 0.06, 0.18, 0.52) : 0.24;
            this.currentGroundSpeed = this.currentGroundSpeed + (desiredGroundSpeed - this.currentGroundSpeed) * lerpFactor;
            if (Math.abs(this.currentGroundSpeed) < 1.0E-4) {
                this.currentGroundSpeed = 0.0;
            }

            boolean groundMoving = this.currentGroundSpeed > 0.012;
            this.moving = wantsMove ? groundMoving || horizontalDistance > 0.18 : groundMoving;
            Vec3d motion = this.currentGroundSpeed > 1.0E-5 && horizontalDistance > 1.0E-5 ? horizontal.normalize().multiply(Math.min(horizontalDistance, this.currentGroundSpeed)) : Vec3d.ZERO;
            if (this.horizontalCollision && wantsMove && this.blockedTicks >= 1 && horizontalDistance > 0.25 && this.currentGroundSpeed > 0.02) {
                if (this.sideStepTicks <= 0) {
                    this.sideStepDir = this.random.nextBoolean() ? 1 : -1;
                    this.sideStepTicks = 12;
                }

                Vec3d facing = horizontal.lengthSquared() > 1.0E-6 ? horizontal.normalize() : new Vec3d(0.0, 0.0, 1.0);
                Vec3d side = new Vec3d(-facing.z * this.sideStepDir, 0.0, facing.x * this.sideStepDir);
                Vec3d slideMotion = facing.multiply(0.45).add(side.multiply(0.9));
                if (slideMotion.lengthSquared() > 1.0E-6) {
                    double speed = Math.max(this.currentGroundSpeed, this.desiredSpeed * 0.7);
                    motion = slideMotion.normalize().multiply(Math.min(horizontalDistance, speed));
                }
            }

            if (this.sideStepTicks > 0) {
                this.sideStepTicks--;
            }

            double targetHeightDelta = this.desiredPosition.y - this.getY();
            if (this.isOnGround()) {
                if (targetHeightDelta > 1.15 && horizontalDistance <= 1.6 && wantsMove && this.currentGroundSpeed > 0.035 && this.jumpCooldown <= 0 && this.blockedTicks < 3) {
                    this.verticalVelocity = 0.42;
                    this.jumpCooldown = 11;
                } else {
                    this.verticalVelocity = targetHeightDelta < -0.35 ? Math.max(-0.16, targetHeightDelta) : 0.0;
                }
            } else {
                this.verticalVelocity = Math.max(this.verticalVelocity - 0.08, -0.36);
            }

            this.move(MovementType.SELF, new Vec3d(motion.x, this.verticalVelocity, motion.z));
            Vec3d newPos = this.getEntityPos();
            double movedSquared = this.square(newPos.x - startPos.x) + this.square(newPos.z - startPos.z);
            boolean hasMoved = movedSquared > 2.5E-5 || Math.abs(newPos.y - startPos.y) > 0.003;
            if (this.isOnGround() && this.verticalVelocity < 0.0) {
                this.verticalVelocity = 0.0;
            }

            if (this.isOnGround()
                    && this.verticalVelocity <= 0.0
                    && this.desiredPosition.y <= this.getY()
                    && Math.abs(this.desiredPosition.y - this.getY()) < 0.18) {
                this.setPos(this.getX(), this.desiredPosition.y, this.getZ());
            }

            this.moving = wantsMove && hasMoved;
            Vec3d remaining = this.desiredPosition.subtract(this.getEntityPos());
            double remainingDistance = Math.hypot(remaining.x, remaining.z);
            boolean makingProgress = remainingDistance + 0.025 < this.lastHorizontalDistance;
            if (this.currentGroundSpeed > 0.02 && this.horizontalCollision && this.isOnGround()) {
                this.blockedTicks++;
            } else if (!wantsMove || makingProgress || remainingDistance < 0.8) {
                this.blockedTicks = 0;
            }

            if (this.blockedTicks >= 7) {
                this.pauseTicks = 8;
                this.blockedTicks = 0;
                this.sideStepTicks = 0;
                this.moving = false;
                this.movingTicks = 0;
                this.currentGroundSpeed = 0.0;
                this.verticalVelocity = 0.0;
            }

            this.lastHorizontalDistance = wantsMove ? remainingDistance : Double.MAX_VALUE;
            double targetAnimSpeed = this.moving ? Math.max(1.0, this.animationSpeed) : 1.0;
            this.currentAnimationSpeed = this.currentAnimationSpeed + (targetAnimSpeed - this.currentAnimationSpeed) * (this.moving ? 0.22 : 0.18);
            this.setVelocity(Vec3d.ZERO);
            this.setYaw(this.smoothedYaw);
            this.setHeadYaw(this.smoothedYaw);
            this.setBodyYaw(this.smoothedYaw);
            this.tickClientAudio(this.moving, horizontal.horizontalLengthSquared());
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>("controller", 5, this::predicate));
    }
}
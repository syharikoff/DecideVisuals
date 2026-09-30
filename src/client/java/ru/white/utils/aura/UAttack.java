package ru.white.utils.aura;


import net.minecraft.text.Text;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.math.ChatUtils;
import ru.white.utils.math.MathUtil;
import ru.white.utils.math.ServerUtil;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;

@UtilityClass
public class UAttack implements IMinecraft {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static long hitCounterCPSBypass;

    public static void hitCounterCPSBypassNext() {
        ++hitCounterCPSBypass;
    }

    public static void hitCounterCPSBypassReset() {
        hitCounterCPSBypass = 0;
    }

    public static boolean cpsBypassTrigger() {
        return hitCounterCPSBypass % 7 == 3;
    }

    public static PlayerEntity getSelf() {
        return mc.player;
    }

    public static World getWorld() {
        return mc.world;
    }

    public static float applyGaussianJitter(float rotation) {
        final float strength = .2F;
        return (float) (rotation + (secureRandom.nextGaussian() * strength * 2.F - strength));
    }

    public static boolean randomBoolean(int chance010) {
        return secureRandom.nextInt(chance010 + 1) >= 1 * (1.F / Math.max(chance010, 1.F));
    }

    public static boolean randomBoolean() {
        return secureRandom.nextInt(2) == 1;
    }

    public static float randomFloat(float min, float max) {
        return secureRandom.nextFloat(min, max);
    }

    public static float randomFloat() {
        return randomFloat(.0F, 1.F);
    }

    public static int randomInt1PosibleOrNot() {
        return randomBoolean() ? 1 : -1;
    }

    public static int getAxeSlot() {
        if (mc.player == null)
            return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }


    public static Runnable[] hitShieldBreakTaskForUse(LivingEntity livingIn, boolean enabled) {
        final Runnable[] pre$post = new Runnable[] { () -> {
        }, () -> {
        } };

        if (!enabled || mc.player == null)
            return pre$post;

        if (livingIn instanceof PlayerEntity player) {

            if (!player.isBlocking())
                return pre$post;

            final ItemStack main = player.getMainHandStack();
            final ItemStack off = player.getOffHandStack();
            final Item mainItem = main.isEmpty() ? null : main.getItem();
            final Item offItem = off.isEmpty() ? null : off.getItem();


            if (mainItem == Items.SHIELD || offItem == Items.SHIELD) {
                final int axeSlot = getAxeSlot();
                final int handSlot = mc.player.getInventory().getSelectedSlot();

                if (axeSlot != -1 && axeSlot != handSlot) {

                    pre$post[0] = () -> {
                        if (mc.getNetworkHandler() != null) {
                            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
                        }
                    };


                    pre$post[1] = () -> {
                        if (mc.getNetworkHandler() != null) {
                            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(handSlot));
                        }
                    };
                }
            }
        }

        return pre$post;
    }


    public static Runnable[] resetShieldSilentTaskForUse(boolean enabled) {
        final Runnable[] pre$post = new Runnable[] { () -> {
        }, () -> {
        } };
        if (!enabled || mc.player == null)
            return pre$post;

        if (mc.player.isBlocking()) {
            Hand active = mc.player.getActiveHand();

            if (active == null)
                return pre$post;

            pre$post[0] = () -> mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));

            pre$post[1] = () -> mc.getNetworkHandler()
                    .sendPacket(new PlayerInteractItemC2SPacket(active, 0, mc.player.getYaw(), mc.player.getPitch()));
        }
        return pre$post;
    }

    public static boolean isBestMomentToHit(boolean fallCheck) {
        if (mc.player == null)
            return true;

        if (mc.player.getMainHandStack().getItem() == Items.MACE) {
            return true;
        }

        if (!fallCheck) return true;

        if (AttackUtil.isPlayerInCriticalState()) return true;

        boolean isCritState = AttackUtil.isPlayerInCriticalState();

        final boolean skipFallCheck = isCritState || AttackUtil.hasMovementRestrictions();

        return skipFallCheck ;
    }

    public static boolean useEntity(LivingEntity livingIn, Runnable preHit, Runnable postHit, Hand hand,
                                    boolean cpsBypass) {
        if (preHit != null)
            preHit.run();
        if (livingIn != null && mc.interactionManager != null && mc.player != null) {




            mc.interactionManager.attackEntity(mc.player, livingIn);

            //mc.player.sendMessage(Text.of("Удар сделан " + cooldownTimer.elapsedTime()),true);
            if (hand != null)
                mc.player.swingHand(hand);
            if (cpsBypass)
                hitCounterCPSBypassNext();
            else
                hitCounterCPSBypassReset();

                cooldownTimer.reset();

        }
        if (postHit != null)
            postHit.run();
        return livingIn != null;
    }

    @Getter
    private static final StopWatch cooldownTimer = new StopWatch();

    public static long getMsCooldown() {
        if (mc.player == null)
            return 500L;

        double attackSpeed = mc.player.getAttributeValue(EntityAttributes.ATTACK_SPEED);


        long msCooldown;

        float maxDeviation = .2F;
        msCooldown = (long) ((1.F / attackSpeed) * 1000.F * (1.F - Math.min(maxDeviation, 1.F)));

        if (attackSpeed == 4.D || attackSpeed == 4.4000000059604645 || attackSpeed == 4.800000011920929)
            msCooldown = 450L;
        msCooldown = Math.max(msCooldown, 450L);





        return msCooldown;
    }

    public static boolean msCooldownReached(long msOffset) {
        return cooldownTimer.finished(getMsCooldown() + msOffset);
    }

    public static boolean msCooldownReached() {
        return msCooldownReached(0);
    }

    public static boolean msCooldownHasMs(long ms) {
        return cooldownTimer.finished(ms);
    }

    public static float msCooldownPC01() {
        return Math.min(cooldownTimer.elapsedTime() / (float) getMsCooldown(), 1.F);
    }

    public static float msCooldownReach() {
        return cooldownTimer.elapsedTime();
    }

    public static boolean anyEntityOnRay(LivingEntity livingIn, double range) {
        // RayTraceUtil должен быть обновлен отдельно, так как это кастомный класс
        return livingIn != null && RayTraceUtil.rayTraceEntity(MathHelper.wrapDegrees(mc.player.getYaw()),
                mc.player.getPitch(), (float) range, livingIn);
    }


    public static boolean shouldAttack(LivingEntity livingTarget, boolean rayCast, boolean distanceCheck,
                                       boolean fallCheck, long cooldownMSOffset, float[] ranges) {
        // dst - validDistance кастомный метод? Если стандартный - distanceTo
        // Предполагаем, что IMinecraft или миксин добавляет validDistance. Если нет,
        // замените на livingTarget.distanceTo(mc.player) <= ranges[0]
        if (distanceCheck && livingTarget != null && !AuraUtil.validDistance(livingTarget, ranges[0], true))
            return false;

        // cooldown
        if (!UAttack.msCooldownReached(cooldownMSOffset))
            return false;

        // best moment
        boolean validNext = UAttack.isBestMomentToHit(fallCheck);


        // ray cast rule
        if (validNext && rayCast && !anyEntityOnRay(livingTarget, ranges[0]))
            validNext = false;

        return validNext;
    }

    public static boolean shouldAttack(LivingEntity livingTarget, boolean rayCast, boolean fallCheck,
                                       long cooldownMSOffset, float[] ranges) {
        return shouldAttack(livingTarget, rayCast, true, fallCheck, cooldownMSOffset, ranges);
    }

    public static boolean resetSprintTickP(LivingEntity targetIn, float[] ranges) {
        if (targetIn != null && shouldAttack(targetIn, false, false, -20L, ranges)
                && !mc.player.isOnGround()
                && !mc.player.isSubmergedIn(FluidTags.WATER)
                && mc.player.getVelocity().y <= 0.0030162615090425808) {
            return true;
        }
        return false;
    }

    public static boolean resetSprintTick(LivingEntity targetIn, float[] ranges) {
        if (targetIn != null && shouldAttack(targetIn, false, false, -50L, ranges)) {
            if (!mc.player.isOnGround() && !mc.player.isSubmergedIn(FluidTags.WATER)) {
                if (mc.player.getVelocity().y <= .0030162615090425808)
                    return true;
            }
        }
        return false;
    }

    private static boolean missDetected;
    private static int counterTo0PostMissHits;

    private static int maxHitsCountOnMiss() {
        return 3;
    }

    public static void antiMissesHittingReset() {
        missDetected = false;
        counterTo0PostMissHits = 0;
    }


    public static void antiMissesHittingUpdate(LivingEntity targetIn, boolean cpsBypass, boolean rayCastCheck,
                                               boolean enabled) {
        if (targetIn == null || counterTo0PostMissHits == 0 || !enabled || targetIn.hurtTime != 0)
            antiMissesHittingReset();

        if (enabled && targetIn != null && UAttack.msCooldownHasMs(cpsBypassTrigger() ? 250 : 150)
                && mc.player.handSwinging) {
            if (!missDetected && counterTo0PostMissHits == 0 && targetIn.hurtTime == 0) {
                missDetected = true;
                counterTo0PostMissHits = maxHitsCountOnMiss();
            }
            if (missDetected && counterTo0PostMissHits > 0 && targetIn != null) {
                if ((!rayCastCheck || anyEntityOnRay(targetIn, 6.F)) && useEntity(targetIn, () -> {
                }, () -> {
                }, Hand.MAIN_HAND, cpsBypass))
                    --counterTo0PostMissHits;
            }
        }
    }
}
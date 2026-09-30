package ru.white.utils.aura;


import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.math.MathUtil;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.player.SimulatedPlayer;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.function.Predicate;

@Setter
@Getter
@UtilityClass
public class AttackUtil implements IMinecraft {

    private int count = 0;




    public void attackEntity(Entity entity) {
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);

        count++;

    }




    public boolean hasMovementRestrictions() {
        return mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                || isPlayerInBlock(Blocks.COBWEB)
                || mc.player.isSubmergedInWater()
                || mc.player.isInLava()
                || mc.player.isClimbing()
                || !canChangeIntoPose(EntityPose.STANDING) && mc.player.isInSneakingPose()
                || mc.player.getAbilities().flying;

    }

    public boolean hasPreMovementRestrictions(SimulatedPlayer simulatedPlayer) {
        return simulatedPlayer.hasStatusEffect(StatusEffects.BLINDNESS)
                || simulatedPlayer.hasStatusEffect(StatusEffects.LEVITATION)
                || isBoxInBlock(simulatedPlayer.boundingBox, Blocks.COBWEB)
                || simulatedPlayer.isSubmergedInWater()
                || simulatedPlayer.isInLava()
                || simulatedPlayer.isClimbing()
                || !canChangeIntoPose(EntityPose.STANDING) && mc.player.isInSneakingPose()
                || mc.player.getAbilities().flying;
    }


    public boolean canChangeIntoPose(EntityPose pose) {
        return mc.player.getEntityWorld().isSpaceEmpty(mc.player, mc.player.getDimensions(pose).getBoxAt(mc.player.getEntityPos()).contract(1.0E-7));
    }

    public boolean isPlayerInBlock(Block block) {
        return isBoxInBlock(mc.player.getBoundingBox().expand(-1e-3), block);
    }
    public boolean isBoxInBlock(Box box, Block block) {
        return isBox(box,pos -> mc.world.getBlockState(pos).getBlock().equals(block));
    }    public boolean isBox(Box box, Predicate<BlockPos> pos) {
        return BlockPos.stream(box).anyMatch(pos);
    }

    public boolean isPlayerInCriticalState() {
        boolean crit = mc.player.fallDistance > ((ServerUtil.isCopyTime() || ServerUtil.isFunTime())
                ? MathUtil.random(0.01F,0.08F) : 0) && (mc.player.fallDistance < 0.08 || !SimulatedPlayer.simulateLocalPlayer(1).onGround);
        return !mc.player.isOnGround() && (crit );
    }

    public boolean isPrePlayerInCriticalState(  SimulatedPlayer simulatedPlayer) {
        boolean crit = simulatedPlayer.fallDistance > 0 && (simulatedPlayer.fallDistance < 0.08 || !SimulatedPlayer.simulateLocalPlayer(2).onGround);
        return !simulatedPlayer.onGround && (crit );
    }

}

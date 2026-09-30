package ru.white.module.impl.utils.consumable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class ConsumableHandler {
    public static final ConsumptionStateHandler STATE = new ConsumptionStateHandler();
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    public static boolean isEnabled() {
        ru.white.module.impl.utils.ConsumableOptimizer mod = ru.white.module.impl.utils.ConsumableOptimizer.get();
        return mod != null && mod.isEnabled();
    }

    public static void handleItemStackUsage(ItemStack stack, CallbackInfo ci) {
        ClientPlayerEntity player = CLIENT.player;
        if (player == null) return;
        ConsumableComponent component = stack.get(DataComponentTypes.CONSUMABLE);
        if (component == null) return;

        if (!component.canConsume(player, stack) || player.getAbilities().flying) {
            CLIENT.interactionManager.stopUsingItem(player);
        }

        if (player.getItemUseTimeLeft() <= 0) {
            processAudio(player, stack, component);
            consume(player);
            ci.cancel();
        }
    }

    public static void handlePacket(Packet<?> packet, CallbackInfo ci) {
        if (CLIENT.world == null || CLIENT.player == null) return;
        if (packet instanceof EntityStatusS2CPacket status) {
            if (status.getEntity(CLIENT.world) == CLIENT.player
                    && status.getStatus() == 9
                    && STATE.isWaitingForServer()) {
                STATE.stopServerWait();
                ci.cancel();
            }
        }
    }

    public static void handleServerSounds(SoundEvent sound, CallbackInfo ci) {
        if (STATE.shouldSuppressBurp() && sound == SoundEvents.ENTITY_PLAYER_BURP) {
            STATE.resetBurpSuppression();
            ci.cancel();
        } else if (STATE.isSoundSuppressed(sound)) {
            STATE.clearSoundSuppression();
            ci.cancel();
        }
    }

    public static boolean shouldSkipHandAnimationOnSwap() {
        return STATE.shouldSuppressEquipmentAnimation();
    }

    private static void processAudio(ClientPlayerEntity player, ItemStack stack, ConsumableComponent comp) {
        SoundEvent sound = comp.sound().value();
        STATE.setSoundSuppression(sound);
        playSound(player, sound, true);

        if (stack.getUseAction() == UseAction.EAT || stack.isOf(Items.GOLDEN_APPLE)) {
            playSound(player, SoundEvents.ENTITY_PLAYER_BURP, false, 0.5f,
                    MathHelper.nextFloat(player.getRandom(), 0.9f, 1.0f));
            STATE.suppressBurp();
        }
    }

    private static void consume(ClientPlayerEntity player) {
        if (player.isUsingItem()) {
            Hand hand = player.preferredHand;
            if (!player.getMainHandStack().equals(player.getStackInHand(hand))) {
                CLIENT.interactionManager.stopUsingItem(player);
            } else if (!player.getMainHandStack().isEmpty() && player.isUsingItem()) {
                finishUsing(player);
                player.clearActiveItem();
                STATE.startServerWait();
            }
        }
    }

    public static void finishUsing(ClientPlayerEntity user) {
        ItemStack stack = user.getMainHandStack();
        ConsumableComponent component = stack.get(DataComponentTypes.CONSUMABLE);
        if (component != null) {
            component.spawnParticlesAndPlaySound(user.getRandom(), user, stack, 16);
            STATE.suppressEquipmentAnimation();
        }
    }

    private static void playSound(ClientPlayerEntity player, SoundEvent sound, boolean randomPitch) {
        float pitch = randomPitch ? MathHelper.nextFloat(player.getRandom(), 0.9f, 1.1f) : 1.0f;
        playSound(player, sound, randomPitch, 1.0f, pitch);
    }

    private static void playSound(ClientPlayerEntity player, SoundEvent sound, boolean randomPitch, float volume, float pitch) {
        CLIENT.world.playSound(player, player.getX(), player.getY(), player.getZ(),
                sound, SoundCategory.PLAYERS, volume, pitch);
    }
}

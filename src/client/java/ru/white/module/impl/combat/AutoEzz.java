package ru.white.module.impl.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import ru.white.manager.event_impl.AttackEvent;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.StringSetting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ModuleInfo(
        name = "AutoEzz",
        desc = "Отправляет сообщения в чат после снятия тотема и убийств",
        category = Category.UTILITIES
)
public final class AutoEzz extends Module {

    private static final byte TOTEM_STATUS = 35;
    private static final byte DEATH_STATUS = 3;
    private static final long KILL_WINDOW_MS = 10000L;
    private static final long COOLDOWN_MS = 1000L;

    private final BooleanSetting writeOnTotem = new BooleanSetting(this, "Писать за тотем", true);
    private final StringSetting textOnTotem = new StringSetting(this, "Текст за тотем", "попнул тотем");
    private final BooleanSetting writeOnKill = new BooleanSetting(this, "Писать за килл", true);
    private final StringSetting textOnKill = new StringSetting(this, "Текст за килл", "убит");

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private UUID lastTargetUuid;
    private String lastTargetName;
    private long lastAttackTime;

    @Override
    protected void onDisable() {
        cooldowns.clear();
        lastTargetUuid = null;
        lastTargetName = null;
        lastAttackTime = 0;
    }

    @EventHandler
    public void onAttack(AttackEvent e) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        Entity target = e.getTarget();
        if (!(target instanceof PlayerEntity player)) return;
        if (player == mc.player) return;

        lastTargetUuid = player.getUuid();
        lastTargetName = player.getName().getString();
        lastAttackTime = System.currentTimeMillis();
    }

    @EventHandler
    public void onPacket(EventPacket e) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!e.isSend() && e.getPacket() instanceof EntityStatusS2CPacket packet) {
            handleIncomingPacket(packet, mc);
        }
    }

    private void handleIncomingPacket(EntityStatusS2CPacket packet, MinecraftClient mc) {
        byte status = packet.getStatus();

        if (status == TOTEM_STATUS && writeOnTotem.getValue()) {
            Entity entity = packet.getEntity(mc.world);
            if (entity == null || !(entity instanceof PlayerEntity player)) return;
            if (player == mc.player) return;
            sendTrashTalk(player.getUuid(), player.getName().getString(), textOnTotem.getValue());
        }

        if (status == DEATH_STATUS && writeOnKill.getValue()) {
            Entity entity = packet.getEntity(mc.world);
            if (entity == null || !(entity instanceof PlayerEntity player)) return;
            if (player == mc.player) return;
            if (lastTargetUuid != null && player.getUuid().equals(lastTargetUuid)
                    && System.currentTimeMillis() - lastAttackTime < KILL_WINDOW_MS) {
                sendTrashTalk(player.getUuid(), player.getName().getString(), textOnKill.getValue());
                lastTargetUuid = null;
                lastTargetName = null;
            }
        }
    }

    private void sendTrashTalk(UUID uuid, String playerName, String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) return;
        if (message == null || message.isBlank()) return;
        if (playerName == null || playerName.isBlank()) return;

        long now = System.currentTimeMillis();
        Long lastTime = cooldowns.get(uuid);
        if (lastTime != null && (now - lastTime) < COOLDOWN_MS) return;

        cooldowns.put(uuid, now);
        mc.player.networkHandler.sendChatMessage(message.trim());
    }
}

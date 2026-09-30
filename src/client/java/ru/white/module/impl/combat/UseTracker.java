package ru.white.module.impl.combat;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import ru.white.manager.event_impl.AttackEvent;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.MultiBooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.notification.NotificationManager;
import ru.white.utils.other.Instance;
import ru.white.utils.other.UseCooldowns;

import java.util.UUID;

/**
 * Держит кулдауны хилок текущей цели: цель ставится по удару, детект и хранение — в {@link UseCooldowns},
 * так что вернулся к прошлой цели, и её таймеры на месте.
 */
@ModuleInfo(
        name = "Use Tracker",
        desc = "Кулдауны хилок цели и её баффы",
        category = Category.UTILITIES
)
public class UseTracker extends Module {

    public static UseTracker getInstance() {
        return Instance.get(UseTracker.class);
    }

    public final MultiBooleanSetting items = new MultiBooleanSetting(this, "Предметы",
            new BooleanSetting(UseCooldowns.Item.NOTCH.label, true),
            new BooleanSetting(UseCooldowns.Item.GAPPLE.label, true),
            new BooleanSetting(UseCooldowns.Item.HEAL.label, true),
            new BooleanSetting(UseCooldowns.Item.NAUSEA.label, true),
            new BooleanSetting(UseCooldowns.Item.CHORUS.label, true),
            new BooleanSetting(UseCooldowns.Item.KELP.label, false),
            new BooleanSetting(UseCooldowns.Item.SCRAP.label, false));

    public final MultiBooleanSetting buffs = new MultiBooleanSetting(this, "Баффы цели",
            new BooleanSetting(UseCooldowns.Buff.STRENGTH.label, true),
            new BooleanSetting(UseCooldowns.Buff.SPEED.label, true),
            new BooleanSetting(UseCooldowns.Buff.RESISTANCE.label, true),
            new BooleanSetting(UseCooldowns.Buff.REGENERATION.label, true),
            new BooleanSetting(UseCooldowns.Buff.FIRE_RESISTANCE.label, true),
            new BooleanSetting(UseCooldowns.Buff.ABSORPTION.label, true));

    public final BooleanSetting notifications = new BooleanSetting(this, "Уведомления", true);

    public final SliderSetting forget = new SliderSetting(this, "Забывать цель, сек", 60, 10, 300, 10);

    private UUID target;
    private String targetName;
    private long lastAttack;

    private boolean wasNausea;

    public UseTracker() {
        UseCooldowns.listen(this::onUse);
    }

    @Override
    protected void onDisable() {
        target = null;
        targetName = null;
        lastAttack = 0;
        wasNausea = false;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        onDisable();
        UseCooldowns.clear();
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (!(event.getTarget() instanceof PlayerEntity player) || player == mc.player) return;

        target = player.getUuid();
        targetName = player.getName().getString();
        lastAttack = System.currentTimeMillis();
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        UseCooldowns.tick(event);

        if (mc.player == null || mc.world == null) return;

        // тошнотка прилетела в нас — значит цель только что её кинула
        boolean nausea = mc.player.hasStatusEffect(StatusEffects.NAUSEA);

        if (nausea && !wasNausea && hasTarget()) {
            PlayerEntity player = getTargetPlayer();
            if (player != null) UseCooldowns.trigger(player, UseCooldowns.Item.NAUSEA);
        }

        wasNausea = nausea;
    }

    /** Кто-то что-то съел — уведомляем, только если это наша цель. */
    private void onUse(PlayerEntity player, UseCooldowns.Item item) {
        if (!isEnabled() || !notifications.getValue()) return;
        if (!player.getUuid().equals(target)) return;
        if (!items.getValue(item.label)) return;

        NotificationManager.send(player.getName().getString() + " — " + item.label,
                NotificationManager.Type.INFO, new ItemStack(item.icon));
    }

    public boolean hasTarget() {
        return isEnabled() && target != null && System.currentTimeMillis() - lastAttack < (long) (forget.getValue() * 1000L);
    }

    public String getTargetName() {
        return targetName == null ? "" : targetName;
    }

    public UUID getTargetUuid() {
        return target;
    }

    public PlayerEntity getTargetPlayer() {
        return target == null || mc.world == null ? null : mc.world.getPlayerByUuid(target);
    }

    /** Остаток кулдауна цели в секундах, 0 — если кд нет или предмет отключён в настройках. */
    public int remaining(UseCooldowns.Item item) {
        if (!items.getValue(item.label)) return 0;

        return UseCooldowns.remaining(target, item);
    }
}

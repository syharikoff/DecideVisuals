package ru.white.utils.other;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.notification.NotificationManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Общий детект «съеденного» по всем игрокам в мире: съеденное определяется по завершённой
 * анимации использования, кулдауны лежат по UUID.
 * Use Tracker (по цели) и Use World Tracker (по всем) читают отсюда — логика в одном месте.
 */
public final class UseCooldowns implements IMinecraft {

    /**
     * Запас по тикам к длительности анимации предмета: флаг использования прилетает с задержкой
     * в пару тиков, поэтому «съел» никогда не совпадает с полной длительностью ровно.
     */
    private static final int USE_TOLERANCE = 8;

    /** Показывать в уведомлениях каждое замеченное использование — включается настройкой модуля. */
    public static boolean debug;

    public enum Item {
        NOTCH("Чарка", 150, Items.ENCHANTED_GOLDEN_APPLE),
        GAPPLE("Гепл", 30, Items.GOLDEN_APPLE),
        HEAL("Хилка", 20, Items.POTION),
        NAUSEA("Тошнотка", 60, Items.POTION),
        CHORUS("Хорус", 20, Items.CHORUS_FRUIT),
        KELP("Пласт", 20, Items.DRIED_KELP),
        SCRAP("Трапка", 15, Items.NETHERITE_SCRAP);

        public final String label;
        public final int seconds;
        public final net.minecraft.item.Item icon;

        Item(String label, int seconds, net.minecraft.item.Item icon) {
            this.label = label;
            this.seconds = seconds;
            this.icon = icon;
        }
    }

    public enum Buff {
        STRENGTH("Сила", StatusEffects.STRENGTH, 0xFFFF5555),
        SPEED("Скорость", StatusEffects.SPEED, 0xFF55FFFF),
        RESISTANCE("Резист", StatusEffects.RESISTANCE, 0xFF8888FF),
        REGENERATION("Регена", StatusEffects.REGENERATION, 0xFFFF55FF),
        FIRE_RESISTANCE("Огнеупор", StatusEffects.FIRE_RESISTANCE, 0xFFFFAA00),
        ABSORPTION("Абсорб", StatusEffects.ABSORPTION, 0xFFFFFF55);

        public final String label;
        public final RegistryEntry<StatusEffect> effect;
        public final int color;

        Buff(String label, RegistryEntry<StatusEffect> effect, int color) {
            this.label = label;
            this.effect = effect;
            this.color = color;
        }
    }

    private static final UseCooldowns INSTANCE = new UseCooldowns();

    /** UUID -> предмет -> время окончания кулдауна. */
    private static final Map<UUID, Map<Item, Long>> COOLDOWNS = new HashMap<>();

    /** Состояние поедания по каждому игроку в радиусе. */
    private static final Map<UUID, Use> USING = new HashMap<>();

    private static final List<BiConsumer<PlayerEntity, Item>> LISTENERS = new ArrayList<>();

    /** Последний обработанный тик: оба модуля дергают tick() одним и тем же событием. */
    private static EventUpdate lastTick;

    private UseCooldowns() {
    }

    private static class Use {
        boolean active;
        ItemStack stack = ItemStack.EMPTY;
        int ticks;
    }

    /** Уведомление о новом кулдауне — фильтровать по своим настройкам должен слушатель. */
    public static void listen(BiConsumer<PlayerEntity, Item> listener) {
        LISTENERS.add(listener);
    }

    /**
     * Дергается из EventUpdate включённых модулей — пока ни один не включён, детект не крутится.
     * Событие одно на тик, поэтому второй вызов за тот же тик отбрасывается.
     */
    public static void tick(EventUpdate event) {
        if (event == lastTick) return;
        lastTick = event;

        if (mc.player == null || mc.world == null) return;

        Set<UUID> seen = new HashSet<>();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            seen.add(player.getUuid());

            Use use = USING.computeIfAbsent(player.getUuid(), u -> new Use());

            boolean now = player.isUsingItem();

            if (now && !use.active) {
                ItemStack stack = player.getActiveItem();

                if (stack.isEmpty()) stack = player.getMainHandStack();
                if (stack.isEmpty()) stack = player.getOffHandStack();

                use.stack = stack.copy();
                use.ticks = 0;
            }

            if (now) use.ticks++;

            if (!now && use.active) {
                // порог берём у самого предмета: у водорослей анимация короче, чем у ешки,
                // а на кастомных серверных предметах она вообще любая
                int max = use.stack.isEmpty() ? 0 : use.stack.getMaxUseTime(player);

                if (debug && max > 0) {
                    NotificationManager.send(player.getName().getString() + ": "
                                    + use.stack.getItem().getName().getString() + " " + use.ticks + "/" + max,
                            NotificationManager.Type.INFO);
                }

                if (max > 0 && use.ticks >= max - USE_TOLERANCE) consumed(player, use.stack);

                use.stack = ItemStack.EMPTY;
                use.ticks = 0;
            }

            use.active = now;
        }

        // игроки, вышедшие из радиуса, больше не нужны — кулдауны при этом остаются в кэше
        USING.keySet().retainAll(seen);

        cleanup();
    }

    /** Определяет по стаку, что именно доели. */
    private static void consumed(PlayerEntity player, ItemStack stack) {
        if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
            trigger(player, Item.NOTCH);
        } else if (stack.isOf(Items.GOLDEN_APPLE)) {
            trigger(player, Item.GAPPLE);
        } else if (stack.isOf(Items.CHORUS_FRUIT)) {
            trigger(player, Item.CHORUS);
        } else if (stack.isOf(Items.DRIED_KELP)) {
            trigger(player, Item.KELP);
        } else if (stack.isOf(Items.NETHERITE_SCRAP)) {
            trigger(player, Item.SCRAP);
        } else if (stack.isOf(Items.POTION)) {
            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);

            if (contents == null) return;

            for (StatusEffectInstance effect : contents.getEffects()) {
                if (effect.getEffectType().value() == StatusEffects.INSTANT_HEALTH.value()) trigger(player, Item.HEAL);
                if (effect.getEffectType().value() == StatusEffects.NAUSEA.value()) trigger(player, Item.NAUSEA);
            }
        }
    }

    /** Ставит кулдаун вручную — например тошнотку, которая прилетела в нас. */
    public static void trigger(PlayerEntity player, Item item) {
        COOLDOWNS.computeIfAbsent(player.getUuid(), u -> new EnumMap<>(Item.class))
                .put(item, System.currentTimeMillis() + item.seconds * 1000L);

        for (BiConsumer<PlayerEntity, Item> listener : LISTENERS) listener.accept(player, item);
    }

    /** Остаток кулдауна в секундах, 0 — если кд нет. */
    public static int remaining(UUID uuid, Item item) {
        if (uuid == null) return 0;

        Map<Item, Long> map = COOLDOWNS.get(uuid);
        if (map == null) return 0;

        Long end = map.get(item);
        if (end == null) return 0;

        return (int) Math.max(0, Math.ceil((end - System.currentTimeMillis()) / 1000.0));
    }

    /** Активные кулдауны игрока в порядке enum. */
    public static Map<Item, Long> of(UUID uuid) {
        Map<Item, Long> map = uuid == null ? null : COOLDOWNS.get(uuid);

        return map == null ? Map.of() : map;
    }

    /** Сколько игроков сейчас в кэше кулдаунов. */
    public static int players() {
        return COOLDOWNS.size();
    }

    public static String format(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static void cleanup() {
        long now = System.currentTimeMillis();

        COOLDOWNS.values().forEach(map -> map.values().removeIf(end -> end <= now));
        COOLDOWNS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public static void clear() {
        COOLDOWNS.clear();
        USING.clear();
    }
}

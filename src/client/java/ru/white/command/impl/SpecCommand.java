package ru.white.command.impl;

import ru.white.Client;
import ru.white.command.Command;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.math.ChatUtils;
import ru.white.utils.player.Spectator;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * .spec — клиентское вселение в сущность. Камера уезжает к цели, как в гм 3,
 * но сервер об этом не знает: своё тело остаётся на месте.
 */
public class SpecCommand extends Command implements IMinecraft {

    private static final List<String> ACTIONS = List.of("off", "next", "prev", "info");

    /** Радиус проверки прогрузки вокруг цели, в чанках. */
    private static final int CHECK_RADIUS = 2;

    private boolean lastTerrainOk = true;

    public SpecCommand() {
        super("spec", ".spec [ник|id|off|next|prev|info]", "Вселиться в сущность (только клиент)");
        Client.eventHandler().subscribe(this);
    }

    @Override
    
    public void execute(String[] args) {
        if (mc.player == null || mc.world == null) return;

        if (args.length == 0) {
            if (Spectator.isActive()) {
                stop();
                return;
            }
            Entity looked = mc.targetedEntity;
            if (looked == null || looked == mc.player) {
                ChatUtils.addChatMessage("§7Наведись на сущность или укажи ник: §f.spec <ник>");
                return;
            }
            attach(looked);
            return;
        }

        String arg = args[0].toLowerCase(Locale.ROOT);

        switch (arg) {
            case "off", "stop", "exit" -> {
                if (!Spectator.isActive()) {
                    ChatUtils.addChatMessage("§7Ты никого не спектуешь");
                    return;
                }
                stop();
            }
            case "next" -> cycle(1);
            case "prev" -> cycle(-1);
            case "info" -> info();
            default -> {
                Entity found = find(args[0]);
                if (found == null) {
                    ChatUtils.addChatMessage("§cНе найдено: §r" + args[0]);
                    return;
                }
                attach(found);
            }
        }
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (Spectator.validate()) {
            ChatUtils.addChatMessage("§7Цель пропала — камера вернулась");
            lastTerrainOk = true;
            return;
        }
        if (!Spectator.isActive()) return;

        // терра вокруг цели может уехать, если она сама вышла за твой радиус прогрузки
        boolean ok = Spectator.isTerrainLoaded(Spectator.getTarget());
        if (ok != lastTerrainOk) {
            ChatUtils.addChatMessage(ok
                    ? "§aТерра вокруг цели прогрузилась"
                    : "§eТерра вокруг цели не прогружена — сервер не шлёт эти чанки");
            lastTerrainOk = ok;
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        Spectator.reset();
        lastTerrainOk = true;
    }

    @Override
    public List<String> getSuggestions(String subPrefix) {
        String prefix = subPrefix.toLowerCase(Locale.ROOT);
        Stream<String> names = mc.world == null
                ? Stream.empty()
                : mc.world.getPlayers().stream()
                    .filter(p -> p != mc.player)
                    .map(p -> p.getName().getString());

        return Stream.concat(names, ACTIONS.stream())
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                .distinct()
                .collect(Collectors.toList());
    }

    
    private void attach(Entity entity) {
        if (entity == mc.player) {
            ChatUtils.addChatMessage("§7В себя вселяться незачем");
            return;
        }
        if (!Spectator.start(entity)) {
            ChatUtils.addChatMessage("§cНе получилось вселиться");
            return;
        }
        lastTerrainOk = Spectator.isTerrainLoaded(entity);

        ChatUtils.addChatMessage("§7Вселился в §f" + entity.getName().getString()
                + " §8[" + entity.getId() + "]§7, §f" + (int) Math.sqrt(mc.player.squaredDistanceTo(entity)) + "§7 блоков"
                + " §8| §7терра: " + terrainStatus(entity));

        if (!lastTerrainOk) {
            ChatUtils.addChatMessage("§7Сервер не шлёт чанки в той точке — подгрузить их с клиента нельзя");
        }
    }

    /** Строка вида «§a9/25» — сколько чанков вокруг цели реально есть у клиента. */
    
    private String terrainStatus(Entity entity) {
        int total = Spectator.chunksInSquare(CHECK_RADIUS);
        int loaded = Spectator.loadedChunksAround(entity, CHECK_RADIUS);
        String color = loaded == total ? "§a" : loaded == 0 ? "§c" : "§e";
        return color + loaded + "§7/§f" + total + " §7чанков";
    }

    
    private void info() {
        Entity entity = Spectator.getTarget();
        if (entity == null) {
            ChatUtils.addChatMessage("§7Ты никого не спектуешь");
            return;
        }
        ChatUtils.addChatMessage("§7Цель: §f" + entity.getName().getString()
                + " §8[" + entity.getId() + "]");
        ChatUtils.addChatMessage("§7Дистанция: §f" + (int) Math.sqrt(mc.player.squaredDistanceTo(entity))
                + " §8| §7терра: " + terrainStatus(entity));
        ChatUtils.addChatMessage("§7Всего чанков у клиента: §f"
                + mc.world.getChunkManager().getLoadedChunkCount()
                + " §8| §7прорисовка: §f" + (int) mc.worldRenderer.getViewDistance());
    }

    
    private void stop() {
        Entity old = Spectator.getTarget();
        Spectator.stop();
        ChatUtils.addChatMessage("§7Камера вернулась"
                + (old != null ? " от §f" + old.getName().getString() : ""));
    }

    /** Поиск по нику игрока (точное совпадение, затем по началу), либо по id сущности. */
    
    private Entity find(String query) {
        String lower = query.toLowerCase(Locale.ROOT);

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player.getName().getString().equalsIgnoreCase(query)) return player;
        }
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player.getName().getString().toLowerCase(Locale.ROOT).startsWith(lower)) return player;
        }

        try {
            Entity byId = mc.world.getEntityById(Integer.parseInt(query));
            if (byId != null && byId != mc.player) return byId;
        } catch (NumberFormatException ignored) {
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (entity.getName().getString().toLowerCase(Locale.ROOT).startsWith(lower)) return entity;
        }
        return null;
    }

    /** Переключение на следующего/предыдущего игрока по удалённости от себя. */
    
    private void cycle(int direction) {
        List<Entity> candidates = new ArrayList<>(mc.world.getPlayers().stream()
                .filter(p -> p != mc.player)
                .sorted(Comparator.comparingDouble(p -> p.squaredDistanceTo(mc.player)))
                .collect(Collectors.toList()));

        if (candidates.isEmpty()) {
            ChatUtils.addChatMessage("§7Рядом никого нет");
            return;
        }

        int index = candidates.indexOf(Spectator.getTarget());
        int next = index < 0
                ? (direction > 0 ? 0 : candidates.size() - 1)
                : Math.floorMod(index + direction, candidates.size());

        attach(candidates.get(next));
    }
}

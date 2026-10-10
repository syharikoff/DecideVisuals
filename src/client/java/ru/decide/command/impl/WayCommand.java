package ru.decide.command.impl;

import com.google.gson.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import ru.decide.Client;
import ru.decide.command.Command;
import ru.decide.manager.event_impl.EventDisplay;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.theme.ThemeColor;
import ru.decide.utils.annotation.IMinecraft;
import ru.decide.utils.colors.ColorFormatting;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.math.ChatUtils;
import ru.decide.utils.other.Projection;
import ru.decide.utils.render.RenderUtil;
import ru.decide.utils.render.font.Font;
import ru.decide.utils.render.font.Fonts;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class WayCommand extends Command implements IMinecraft {

    private static WayCommand INSTANCE;
    public static WayCommand get() { return INSTANCE; }

    private static final Path FILE = Path.of("C:/decide/client1_21_11/waypoints.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Waypoint(String name, Vec3d pos) {}

    /** name_lower -> вейпоинт */
    private final Map<String, Waypoint> waypoints = new ConcurrentHashMap<>();

    public Collection<Waypoint> getWaypoints() { return waypoints.values(); }

    public void addWaypoint(String name, double x, double y, double z) {
        waypoints.put(name.toLowerCase(), new Waypoint(name, new Vec3d(x, y, z)));
        save();
    }

    public boolean removeWaypoint(String name) {
        boolean removed = waypoints.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public WayCommand() {
        super("way", ".way <add|delete|clear|list>", "Вейпоинты — метки на координатах");
        INSTANCE = this;
        load();
        Client.eventHandler().subscribe(this);
    }

    // ── Command ───────────────────────────────────────────────────────────────

    @Override
    
    public void execute(String[] args) {
        if (args.length == 0) { showHelp(); return; }

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 2) {
                    ChatUtils.addChatMessage("§7Использование: §f.way add <имя> [x y z]");
                    return;
                }
                String name = args[1];
                Vec3d pos;
                if (args.length >= 5) {
                    try {
                        pos = new Vec3d(
                                Double.parseDouble(args[2]),
                                Double.parseDouble(args[3]),
                                Double.parseDouble(args[4]));
                    } catch (NumberFormatException ex) {
                        ChatUtils.addChatMessage("§cОшибка: неверные координаты");
                        return;
                    }
                } else {
                    if (mc.player == null) {
                        ChatUtils.addChatMessage("§cВы не в мире, укажите координаты: §f.way add <имя> <x> <y> <z>");
                        return;
                    }
                    pos = mc.player.getEntityPos();
                }

                boolean replaced = waypoints.put(name.toLowerCase(), new Waypoint(name, pos)) != null;
                save();
                ChatUtils.addChatMessage((replaced ? "§aВейпоинт обновлён: §f" : "§aВейпоинт добавлен: §f")
                        + name + " §7(" + (int) pos.x + ", " + (int) pos.y + ", " + (int) pos.z + ")");
            }
            case "delete", "del", "remove" -> {
                if (args.length < 2) {
                    ChatUtils.addChatMessage("§7Использование: §f.way delete <имя>");
                    return;
                }
                if (waypoints.remove(args[1].toLowerCase()) != null) {
                    save();
                    ChatUtils.addChatMessage("§aВейпоинт удалён: §f" + args[1]);
                } else {
                    ChatUtils.addChatMessage("§cВейпоинт не найден: §f" + args[1]);
                }
            }
            case "clear" -> {
                int count = waypoints.size();
                waypoints.clear();
                save();
                ChatUtils.addChatMessage("§aУдалено вейпоинтов: §f" + count);
            }
            case "list" -> {
                if (waypoints.isEmpty()) {
                    ChatUtils.addChatMessage("§7Вейпоинтов нет");
                    return;
                }
                ChatUtils.addChatMessage("§7Вейпоинты (§f" + waypoints.size() + "§7):");
                for (Waypoint w : waypoints.values()) {
                    ChatUtils.addChatMessage("§8• §f" + w.name()
                            + " §7— §f" + (int) w.pos().x + ", " + (int) w.pos().y + ", " + (int) w.pos().z);
                }
            }
            default -> showHelp();
        }
    }

    @Override
    public List<String> getSuggestions(String subPrefix) {
        String lower = subPrefix.toLowerCase();

        // подсказка имён для .way delete <имя>
        if (lower.startsWith("delete ") || lower.startsWith("del ") || lower.startsWith("remove ")) {
            String sub = lower.substring(0, lower.indexOf(' '));
            String namePart = lower.substring(sub.length() + 1);
            return waypoints.values().stream()
                    .filter(w -> w.name().toLowerCase().startsWith(namePart))
                    .map(w -> sub + " " + w.name())
                    .toList();
        }

        return Stream.of("add", "delete", "clear", "list")
                .filter(s -> s.startsWith(lower))
                .toList();
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onDisplay(EventDisplay e) {
        if (mc.player == null || mc.world == null || waypoints.isEmpty()) return;
        if (mc.options.hudHidden) return;

        Font font = Fonts.sf_medium;
        float fontSize = 8;

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        // дальше этого расстояния точка вылетает за far plane проекции —
        // проецируем ближнюю точку на том же направлении, чтобы таг не исчезал
        float maxRenderDist = Math.max(64, mc.options.getViewDistance().getValue() * 16 - 16);

        for (Waypoint w : waypoints.values()) {
            float dist = (float) camPos.distanceTo(w.pos());

            Vec3d renderPos = w.pos();
            boolean clamped = dist > maxRenderDist;
            if (clamped) {
                renderPos = camPos.add(w.pos().subtract(camPos).normalize().multiply(maxRenderDist));
            }

            Vec3d screen = Projection.worldSpaceToScreenSpace(renderPos);
            if (screen.z <= 0 || screen.z >= 1) continue; // за спиной

            String text = w.name() + " " + ColorFormatting.getColor(ThemeColor.getDarkTextColor(1)) + (int) dist + "м";

            float wr = font.getWidth(text, fontSize) + 10;
            float h = 13;
            float x = (float) screen.x - wr / 2F;
            float y = (float) screen.y - h - 6;

            // плашка как у элементов худа
            RenderUtil.Blur.blur(x, y, wr, h, 1, 4, ColorUtil.getRectColor(1));
            RenderUtil.Render2D.outline(x, y, wr, h, 0.5F, ThemeColor.getOutlineColor(1), 4);

            font.drawCentered(text, x + wr / 2F, y + 1.8F, fontSize, ThemeColor.getTextColor(1));

            // иконка в самом месте вейпоинта (для приближённой позиции не рисуем)
            if (!clamped) {
                float iconSize = 8;
                RenderUtil.Images.texture(Identifier.of("decide", "textures/hud/marker.png"),
                        (float) screen.x - iconSize / 2F, (float) screen.y - iconSize / 2F,
                        iconSize, iconSize, ColorUtil.getClientColor(1));
            }
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    
    private void save() {
        JsonArray arr = new JsonArray();
        for (Waypoint w : waypoints.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", w.name());
            obj.addProperty("x", w.pos().x);
            obj.addProperty("y", w.pos().y);
            obj.addProperty("z", w.pos().z);
            arr.add(obj);
        }
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer wtr = new OutputStreamWriter(new FileOutputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(arr, wtr);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
    private void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = new InputStreamReader(new FileInputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
            JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
            waypoints.clear();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                String name = obj.get("name").getAsString();
                Vec3d pos = new Vec3d(
                        obj.get("x").getAsDouble(),
                        obj.get("y").getAsDouble(),
                        obj.get("z").getAsDouble());
                waypoints.put(name.toLowerCase(), new Waypoint(name, pos));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    
    private void showHelp() {
        ChatUtils.addChatMessage("§7.way §fadd §7<имя> [x y z] §8| §7.way §fdelete §7<имя> §8| §7.way §fclear §8| §7.way §flist");
    }
}

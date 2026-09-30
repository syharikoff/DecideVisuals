package ru.white.config;

import com.google.gson.*;
import ru.white.Client;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;
import ru.white.module.api.settings.impl.*;
import ru.white.module.api.settings.impl.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ConfigManager {
    public static final String AUTO_CONFIG = "auto";
    private static final Path CONFIG_DIR = Path.of("C:/wvisual/client1_21_11/config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private int tickTimer = 0;

    // Called before modules are initialized — only creates folder and subscribes
    
    public void setup() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Client.eventHandler().subscribe(this);
    }

    // Called after modules are initialized — loads the saved config
    
    public void init() {
        load(AUTO_CONFIG);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {

    }

    @EventHandler
    public void onTick(EventTick event) {
        if (++tickTimer >= 1200) {
            tickTimer = 0;
            save(AUTO_CONFIG);
        }
    }

    public void autoSave() {
        save(AUTO_CONFIG);
    }
    
    public void save(String name) {
        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();

        for (Module module : Client.get().moduleManager().values()) {
            JsonObject moduleObj = new JsonObject();
            moduleObj.addProperty("enabled", module.isEnabled());
            moduleObj.addProperty("key", module.getKey());

            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                serializeSetting(settings, setting);
            }
            moduleObj.add("settings", settings);
            modules.add(module.getName(), moduleObj);
        }

        root.add("modules", modules);

        Path file = CONFIG_DIR.resolve(name + ".json");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public boolean load(String name) {
        Path file = CONFIG_DIR.resolve(name + ".json");
        if (!Files.exists(file)) return false;

        try (Reader reader = new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject modules = root.getAsJsonObject("modules");

            for (Module module : Client.get().moduleManager().values()) {
                if (!modules.has(module.getName())) continue;
                JsonObject moduleObj = modules.getAsJsonObject(module.getName());

                if (moduleObj.has("enabled")) {
                    module.setEnabled(moduleObj.get("enabled").getAsBoolean(), false);
                }

                if (moduleObj.has("key")) {
                    module.setKey(moduleObj.get("key").getAsInt());
                }

                if (moduleObj.has("settings")) {
                    JsonObject settings = moduleObj.getAsJsonObject("settings");
                    for (Setting<?> setting : module.getSettings()) {
                        deserializeSetting(settings, setting);
                    }
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean delete(String name) {
        if (name.equals(AUTO_CONFIG)) return false;
        Path file = CONFIG_DIR.resolve(name + ".json");
        try {
            if (Files.exists(file)) { Files.delete(file); return true; }
        } catch (IOException e) { e.printStackTrace(); }
        return false;
    }

    public Path getConfigDir() { return CONFIG_DIR; }

    public List<String> listConfigs() {
        List<String> configs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(CONFIG_DIR, "*.json")) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                configs.add(fileName.substring(0, fileName.length() - 5));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return configs;
    }
    
    private void serializeSetting(JsonObject obj, Setting<?> setting) {
        if (setting instanceof DragSetting drag) {
            JsonObject vec = new JsonObject();
            vec.addProperty("x", drag.position.x);
            vec.addProperty("y", drag.position.y);
            obj.add(setting.getName(), vec);
            return;
        }
        if (setting instanceof MultiBooleanSetting ms) {
            JsonObject msObj = new JsonObject();
            for (BooleanSetting val : ms.getValues()) {
                msObj.addProperty(val.getName().toLowerCase(), val.getValue());
            }
            obj.add(setting.getName(), msObj);
            return;
        }
        if (setting instanceof BlockListSetting bl) {
            JsonArray arr = new JsonArray();
            for (String id : bl.getIds()) arr.add(id);
            obj.add(setting.getName(), arr);
            return;
        }
        if (setting instanceof PixelGridSetting grid) {
            JsonArray arr = new JsonArray();
            for (String row : grid.toRows()) arr.add(row);
            obj.add(setting.getName(), arr);
            return;
        }
        Object value = setting.getValue();
        if (value instanceof Boolean b)      obj.addProperty(setting.getName(), b);
        else if (value instanceof Float f)   obj.addProperty(setting.getName(), f);
        else if (value instanceof String s)  obj.addProperty(setting.getName(), s);
        else if (value instanceof Integer i) obj.addProperty(setting.getName(), i);
    }
    
    private void deserializeSetting(JsonObject obj, Setting<?> setting) {
        if (!obj.has(setting.getName())) return;
        JsonElement el = obj.get(setting.getName());
        try {
            deserializeValue(obj, setting, el);
        } catch (Exception e) {
            System.err.println("[ConfigManager] Не удалось загрузить настройку '" + setting.getName() + "': " + e);
        }
    }

    private void deserializeValue(JsonObject obj, Setting<?> setting, JsonElement el) {

        if (setting instanceof DragSetting drag && el.isJsonObject()) {
            JsonObject vec = el.getAsJsonObject();
            drag.position.set(vec.get("x").getAsFloat(), vec.get("y").getAsFloat());
            drag.targetPosition.set(drag.position);
        } else if (setting instanceof MultiBooleanSetting ms && el.isJsonObject()) {
            JsonObject msObj = el.getAsJsonObject();
            for (BooleanSetting val : ms.getValues()) {
                String key = val.getName().toLowerCase();
                if (msObj.has(key)) val.set(msObj.get(key).getAsBoolean());
            }
        } else if (setting instanceof BlockListSetting bl && el.isJsonArray()) {
            bl.getIds().clear();
            for (JsonElement e : el.getAsJsonArray()) {
                String id = e.getAsString();
                if (!bl.getIds().contains(id)) bl.getIds().add(id);
            }
        } else if (setting instanceof PixelGridSetting grid && el.isJsonArray()) {
            List<String> rows = new ArrayList<>();
            for (JsonElement e : el.getAsJsonArray()) rows.add(e.getAsString());
            grid.fromRows(rows);
        } else if (setting instanceof BooleanSetting bs)      bs.set(el.getAsBoolean());
        else if (setting instanceof BooleanSettingHud bs)      bs.set(el.getAsBoolean());
        else if (setting instanceof SliderSetting ss)  ss.set(el.getAsFloat());
        else if (setting instanceof ModeSetting ms)    ms.set(el.getAsString());
        else if (setting instanceof ModeSettingHud ms)    ms.set(el.getAsString());
        else if (setting instanceof BindSetting bind)  bind.set(el.getAsInt());
        else if (setting instanceof ColorSetting cs)   cs.set(el.getAsInt());
        else if (setting instanceof StringSetting str) str.set(el.getAsString());
    }
}

package ru.white.theme;

import com.google.gson.*;
import ru.white.utils.colors.ColorUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads/saves color themes as individual .json files in a dedicated "theme" folder,
 * next to the regular config folder. Keeps track of the active theme.
 *
 * Colors are NOT applied anywhere automatically — read them through {@link ThemeColor}.
 */
public class ThemeManager {

    private static final Path THEME_DIR = Path.of("C:/wvisual/client1_21_11/theme");
    private static final Path ACTIVE_FILE = THEME_DIR.resolve(".active");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ThemeManager instance;

    public static final int MAX_THEMES = 9;

    private final List<Theme> themes = new ArrayList<>();
    private Theme active;

    public static ThemeManager get() {
        if (instance == null) {
            instance = new ThemeManager();
            instance.load();
        }
        return instance;
    }

    public List<Theme> getThemes() {
        return themes;
    }

    public Theme getActive() {
        if (active == null) {
            active = themes.isEmpty() ? new Theme("Default") : themes.get(0);
            if (themes.isEmpty()) themes.add(active);
        }
        return active;
    }

    public void setActive(Theme theme) {
        if (theme == null) return;
        this.active = theme;
        saveActiveName();
    }

    public boolean canCreate() {
        return themes.size() < MAX_THEMES;
    }

    public Theme createTheme(String name) {
        if (!canCreate()) return getActive();
        name = uniqueName(name, null);
        Theme base = getActive();
        Theme created = base.copy(name);
        themes.add(created);
        setActive(created);
        saveTheme(created);
        return created;
    }

    public void renameTheme(Theme theme, String newName) {
        if (theme == null || theme.builtin) return;
        newName = newName == null ? "" : newName.trim();
        if (newName.isEmpty() || newName.equalsIgnoreCase(theme.name)) return;
        // удаляем старый файл, переименовываем, сохраняем заново
        try {
            Files.deleteIfExists(THEME_DIR.resolve(theme.name + ".json"));
        } catch (IOException ignored) {}
        theme.name = uniqueName(newName, theme);
        saveTheme(theme);
        if (active == theme) saveActiveName();
    }

    public void deleteTheme(Theme theme) {
        if (theme == null || theme.builtin || themes.size() <= 1) return;
        themes.remove(theme);
        try {
            Files.deleteIfExists(THEME_DIR.resolve(theme.name + ".json"));
        } catch (IOException ignored) {}
        if (active == theme) {
            active = themes.get(0);
            saveActiveName();
        }
    }

    // ── persistence ─────────────────────────────────────────────────────────

    /** встроенная тема: визуалы/худ задаются, остальное фиксированное */
    private static Theme builtin(String name, int visual, int hud) {
        Theme t = new Theme(name);
        t.builtin = true;
        t.colors[Theme.ACCENT]     = visual;
        t.colors[Theme.ELEMENT]    = hud;
        t.colors[Theme.TEXT]       = Theme.rgb(255,255,255);
        t.colors[Theme.TEXT2]      = Theme.rgb(177, 177, 177);
        t.colors[Theme.BACKGROUND] = Theme.rgb(15,15,15 );
        t.colors[Theme.OUTLINE]    = ColorUtil.getColor(255,255,255,(int) (15));
        t.colors[Theme.SEPARATOR]  = ColorUtil.getColor(255,255,255,(int) (15));
        t.colors[Theme.LIGHT_BG]   = ColorUtil.getColor(255,255,255,(int) (10));
        t.setSlider(Theme.OPACITY, 0.9F);
        t.setSlider(Theme.BLUR,    4F);
        t.setBool(Theme.SHADOW,     true);
        t.setBool(Theme.DISTORTION, false); // искажение в деф-темах выключено
        return t;
    }

    public void load() {
        themes.clear();


        themes.add(builtin("Blue",   Theme.rgb(124, 143, 246), Theme.rgb(143, 161, 250)));
        themes.add(builtin("Red",    Theme.rgb(246, 124, 126), Theme.rgb(246, 124, 126)));

        try {
            Files.createDirectories(THEME_DIR);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(THEME_DIR, "*.json")) {
                for (Path path : stream) {
                    Theme t = readTheme(path);
                    // пропускаем пользовательские файлы, конфликтующие с именами встроенных тем
                    if (t != null && !nameExists(t.name, null)) themes.add(t);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // restore active
        String activeName = readActiveName();
        active = themes.stream()
                .filter(t -> t.name.equals(activeName))
                .findFirst()
                .orElse(themes.get(0));
    }

    public void saveTheme(Theme theme) {
        if (theme == null || theme.builtin) return; // встроенные темы не сохраняются в файлы
        try {
            Files.createDirectories(THEME_DIR);
            JsonObject root = new JsonObject();
            root.addProperty("name", theme.name);
            for (int i = 0; i < Theme.COLOR_COUNT; i++) {
                root.addProperty(keyOf(i), theme.colors[i]);
            }
            for (int i = 0; i < theme.sliders.length; i++) {
                root.addProperty(sliderKeyOf(i), theme.sliders[i]);
            }
            for (int i = 0; i < theme.booleans.length; i++) {
                root.addProperty(boolKeyOf(i), theme.booleans[i]);
            }
            Path file = THEME_DIR.resolve(theme.name + ".json");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveActive() {
        saveTheme(getActive());
    }

    private Theme readTheme(Path path) {
        try (Reader reader = new InputStreamReader(new FileInputStream(path.toFile()), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String name = root.has("name") ? root.get("name").getAsString()
                    : stripExt(path.getFileName().toString());
            Theme t = new Theme(name);
            for (int i = 0; i < Theme.COLOR_COUNT; i++) {
                String k = keyOf(i);
                if (root.has(k)) t.colors[i] = root.get(k).getAsInt();
            }
            for (int i = 0; i < t.sliders.length; i++) {
                String k = sliderKeyOf(i);
                if (root.has(k)) t.setSlider(i, root.get(k).getAsFloat());
            }
            for (int i = 0; i < t.booleans.length; i++) {
                String k = boolKeyOf(i);
                if (root.has(k)) t.setBool(i, root.get(k).getAsBoolean());
            }
            return t;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveActiveName() {
        try {
            Files.createDirectories(THEME_DIR);
            Files.write(ACTIVE_FILE, getActive().name.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
    }

    private String readActiveName() {
        try {
            if (Files.exists(ACTIVE_FILE)) {
                return new String(Files.readAllBytes(ACTIVE_FILE), StandardCharsets.UTF_8).trim();
            }
        } catch (IOException ignored) {}
        return null;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String uniqueName(String base, Theme exclude) {
        if (base == null || base.isBlank()) base = "Theme";
        // имя файла не должно содержать запрещённых символов
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (base.isEmpty()) base = "Theme";
        String name = base;
        int n = 1;
        while (nameExists(name, exclude)) {
            name = base + " " + (++n);
        }
        return name;
    }

    private boolean nameExists(String name, Theme exclude) {
        return themes.stream().anyMatch(t -> t != exclude && t.name.equalsIgnoreCase(name));
    }

    private static String keyOf(int i) {
        return switch (i) {
            case Theme.ACCENT     -> "accent";
            case Theme.BACKGROUND -> "background";
            case Theme.OUTLINE    -> "outline";
            case Theme.TEXT       -> "text";
            case Theme.TEXT2      -> "text2";
            case Theme.ELEMENT    -> "element";
            case Theme.SEPARATOR  -> "separator";
            case Theme.LIGHT_BG   -> "lightbg";
            default -> "c" + i;
        };
    }

    private static String sliderKeyOf(int i) {
        return switch (i) {
            case Theme.OPACITY -> "opacity";
            case Theme.BLUR    -> "blur";
            default -> "s" + i;
        };
    }

    private static String boolKeyOf(int i) {
        return switch (i) {
            case Theme.SHADOW     -> "shadow";
            case Theme.DISTORTION -> "distortion";
            default -> "b" + i;
        };
    }

    private static String stripExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}

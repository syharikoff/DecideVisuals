package ru.white.ui.theme;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public final class ThemePersistence {
    private static final File FILE = new File("C:/wvisual/client1_21_11/config/theme", "lv_theme.json");

    private ThemePersistence() {
    }

    public static void init() {
        ThemeManager.setOnChange(ThemePersistence::save);
        File parent = FILE.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!FILE.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(FILE)) {
            Properties props = new Properties();
            props.load(reader);
            String name = props.getProperty("theme");
            try {
                ThemeManager.set(Theme.valueOf(name));
            } catch (IllegalArgumentException ignored) {
            }
        } catch (IOException e) {
            System.err.println("[ThemePersistence] Не удалось загрузить тему: " + e.getMessage());
        }
    }

    private static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(FILE)) {
                Properties props = new Properties();
                props.setProperty("theme", ThemeManager.current().name());
                props.store(writer, "DecideVisuals GUI Theme");
            }
        } catch (IOException e) {
            System.err.println("[ThemePersistence] Не удалось сохранить тему: " + e.getMessage());
        }
    }
}
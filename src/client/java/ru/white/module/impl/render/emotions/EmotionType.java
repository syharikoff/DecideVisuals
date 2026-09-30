package ru.white.module.impl.render.emotions;

import net.minecraft.util.Identifier;

public enum EmotionType {
    DEB("deb", "Деб", 2500L),
    FLOSS("floss", "Флосс", 4500L),
    MASTURBATE("masturbate", "Мастурбация", 3500L),
    HELLO("hello", "Приветствие", 2500L),
    GET_GRIDDY("get_griddy", "Get Griddy", 5500L),
    HAPPY("happy", "Радость", 2500L);

    private final String name;
    private final String localized;
    private final long durationMs;

    EmotionType(String name, String localized, long durationMs) {
        this.name = name;
        this.localized = localized;
        this.durationMs = durationMs;
    }

    public String getName() {
        return name;
    }

    public String getLocalized() {
        return localized;
    }

    public String getTitle() {
        return localized;
    }

    public String getDescription() {
        return localized;
    }

    public String getShortLabel() {
        return name.length() <= 2 ? name.toUpperCase() : name.substring(0, 2).toUpperCase();
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Identifier getTexture() {
        return Identifier.of("client", "textures/emotions/" + name + ".png");
    }
}

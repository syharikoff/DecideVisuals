package ru.white.module.impl.render.custompet;

import java.util.ArrayList;
import java.util.Locale;

public enum CustomPetVariant {
    DEFAULT("Обычная"),
    NITWIT("Нитвит"),
    GARDENER("Фермер"),
    FISHERMAN("Рыбак"),
    MERCHANT("Путешественник"),
    SORCERER("Ведьма"),
    ROBOT("Робот");

    private final String settingValue;

    CustomPetVariant(String settingValue) {
        this.settingValue = settingValue;
    }

    public boolean isRobot() {
        return this == ROBOT;
    }

    public static String[] settingValues() {
        ArrayList<String> values = new ArrayList<>();
        for (CustomPetVariant variant : values()) {
            if (variant != DEFAULT && variant != ROBOT) {
                values.add(variant.settingValue);
            }
        }
        return values.toArray(new String[0]);
    }

    public String getSettingValue() {
        return this.settingValue;
    }

    public static CustomPetVariant fromSettingValue(String value) {
        if (value != null && !value.isBlank()) {
            for (CustomPetVariant variant : values()) {
                if (variant.settingValue.equalsIgnoreCase(value)) {
                    return variant;
                }
            }

            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "default", "обычная" -> NITWIT;
                case "nitwit" -> NITWIT;
                case "gardener", "farmer", "садовник" -> GARDENER;
                case "fisherman" -> FISHERMAN;
                case "merchant", "traveler", "traveller", "торговец" -> MERCHANT;
                case "sorcerer", "witch", "колдун" -> SORCERER;
                default -> NITWIT;
            };
        }
        return NITWIT;
    }

    public static CustomPetVariant fromSerializedName(String name) {
        if (name != null && !name.isBlank()) {
            try {
                CustomPetVariant variant = valueOf(name.toUpperCase());
                return variant == DEFAULT ? NITWIT : variant;
            } catch (IllegalArgumentException ignored) {
                return fromSettingValue(name);
            }
        }
        return NITWIT;
    }

    public boolean usesMerchantBody() {
        return this == MERCHANT;
    }

    public boolean usesMerchantLeaf() {
        return this == MERCHANT;
    }

    public boolean usesSorcererHat() {
        return this == SORCERER;
    }

    public boolean usesFishermanGear() {
        return this == FISHERMAN;
    }

    public boolean usesGardenerGear() {
        return this == GARDENER;
    }
}
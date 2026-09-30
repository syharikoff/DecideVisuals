package ru.white.module.api.settings.impl;

import ru.white.module.api.settings.Setting;
import ru.white.utils.animation.Animation;
import lombok.Getter;
import ru.white.module.api.Module;


import java.util.*;
import java.util.function.Supplier;

public class MultiBooleanSetting extends Setting<Map<String, BooleanSetting>> {

    private final Map<String, BooleanSetting> settingsMap = new LinkedHashMap<>();

    @Getter
    public Animation animation = new Animation();
    public boolean opened;

    public MultiBooleanSetting(Module parent, String name, BooleanSetting... values) {
        super(parent, name, new LinkedHashMap<>());
        for (BooleanSetting value : values) {
            getValue().put(value.getName().toLowerCase(), value);
            settingsMap.put(value.getName().toLowerCase(), value);
        }
    }

    public BooleanSetting get(String name) {
        return settingsMap.get(name.toLowerCase());
    }

    public boolean getValue(String name) {
        BooleanSetting setting = get(name);
        return setting != null && setting.getValue() && getVisible().get();
    }

    public Collection<BooleanSetting> getValues() {
        return getValue().values();
    }

    @Override
    public MultiBooleanSetting setVisible(Supplier<Boolean> value) {
        return (MultiBooleanSetting) super.setVisible(value);
    }

    @Override
    public MultiBooleanSetting onAction(Runnable action) {
        return (MultiBooleanSetting) super.onAction(action);
    }

    @Override
    public MultiBooleanSetting onSetVisible(Runnable action) {
        return (MultiBooleanSetting) super.onSetVisible(action);
    }


    public String getNames() {
        List<String> includedOptions = new ArrayList<>();
        for (BooleanSetting option : getValues()) {
            if (option.getValue()) {
                includedOptions.add(option.getName());
            }
        }
        return String.join(", ", includedOptions);
    }

    public boolean isAnyTrue() {
        for (BooleanSetting setting : settingsMap.values()) {
            if (setting.getValue()) {
                return true;
            }
        }
        return false;
    }
}
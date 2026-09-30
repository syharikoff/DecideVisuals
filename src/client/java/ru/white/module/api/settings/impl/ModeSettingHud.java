package ru.white.module.api.settings.impl;


import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;
import ru.white.utils.animation.satoshi.EaseInOutQuad;

import java.util.List;
import java.util.function.Supplier;

public class ModeSettingHud extends Setting<String> {

    public List<String> values;
    private String cachedValue;


    public ru.white.utils.animation.satoshi.Animation animation = new EaseInOutQuad(300,1);


    public boolean opened;

    public ModeSettingHud(Module parent, String name, String... values) {
        super(parent, name, values[0]);
        this.values = List.of(values);
        this.set(values[0]);
        cachedValue = values[0];
    }

    public int getIndex() {
        int index = 0;
        for (String value : values) {
            if (value.equalsIgnoreCase(getValue())) {
                return index;
            }
            index++;
        }
        return 0;
    }

    public boolean is(String value) {
        return getValue().equalsIgnoreCase(value) && getVisible().get();
    }

    @Override
    public ModeSettingHud set(String value) {
        ModeSettingHud set = (ModeSettingHud) super.set(value);
        this.cachedValue = super.getValue();
        return set;
    }

    @Override
    public ModeSettingHud setVisible(Supplier<Boolean> value) {
        return (ModeSettingHud) super.setVisible(value);
    }

    @Override
    public ModeSettingHud onAction(Runnable action) {
        return (ModeSettingHud) super.onAction(() -> {
            action.run();
            this.cachedValue = super.getValue();
        });
    }

    @Override
    public ModeSettingHud onSetVisible(Runnable action) {
        return (ModeSettingHud) super.onSetVisible(action);
    }

    @Override
    public String getValue() {
        if (cachedValue == null) {
            cachedValue = super.getValue();
        }
        return cachedValue;
    }
}
package ru.white.module.api.settings.impl;


import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;

import java.util.function.Supplier;

public class BooleanSettingHud extends Setting<Boolean> {
    private Boolean cachedValue;


    public BooleanSettingHud(String name, Boolean value) {
        super(name, value);
        cachedValue = value;
    }

    public BooleanSettingHud(Module parent, String name, Boolean value) {
        super(parent, name, value);
        cachedValue = value;
    }

    public static BooleanSettingHud of(String name, Boolean value) {
        return new BooleanSettingHud(name, value);
    }

    @Override
    public BooleanSettingHud setVisible(Supplier<Boolean> value) {
        return (BooleanSettingHud) super.setVisible(value);
    }

    @Override
    public BooleanSettingHud set(Boolean value) {
        BooleanSettingHud set = (BooleanSettingHud) super.set(value);
        this.cachedValue = super.getValue() && getVisible().get();
        return set;
    }

    @Override
    public BooleanSettingHud onAction(Runnable action) {
        return (BooleanSettingHud) super.onAction(() -> {
            action.run();
            this.cachedValue = super.getValue() && getVisible().get();
        });
    }

    @Override
    public BooleanSetting onSetVisible(Runnable action) {
        return (BooleanSetting) super.onSetVisible(action);
    }

    @Override
    public Boolean getValue() {
        if (cachedValue == null) {
            cachedValue = super.getValue();
        }
        return cachedValue && getVisible().get();
    }
}
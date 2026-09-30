package ru.white.module.api.settings.impl;

import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;
import ru.white.utils.animation.satoshi.Animation;
import ru.white.utils.animation.satoshi.EaseInOutQuad;


import java.util.function.Supplier;

public class BooleanSetting extends Setting<Boolean> {
    private Boolean cachedValue;


    public Animation animation = new EaseInOutQuad(300,1);

    public  Animation animation2 = new EaseInOutQuad(300,1);

    public  Animation animation3 = new EaseInOutQuad(300,1);


    public BooleanSetting(String name, Boolean value) {
        super(name, value);
        cachedValue = value;
    }

    public BooleanSetting(Module parent, String name, Boolean value) {
        super(parent, name, value);
        cachedValue = value;
    }

    public static BooleanSetting of(String name, Boolean value) {
        return new BooleanSetting(name, value);
    }

    @Override
    public BooleanSetting setVisible(Supplier<Boolean> value) {
        return (BooleanSetting) super.setVisible(value);
    }

    @Override
    public BooleanSetting set(Boolean value) {
        BooleanSetting set = (BooleanSetting) super.set(value);
        this.cachedValue = super.getValue() && getVisible().get();
        return set;
    }

    @Override
    public BooleanSetting onAction(Runnable action) {
        return (BooleanSetting) super.onAction(() -> {
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
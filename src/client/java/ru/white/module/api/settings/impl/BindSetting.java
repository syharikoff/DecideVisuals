package ru.white.module.api.settings.impl;

import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;
import ru.white.utils.animation.satoshi.Animation;
import ru.white.utils.animation.satoshi.EaseInOutQuad;


import java.util.function.Supplier;

public class BindSetting extends Setting<Integer> {
    public final boolean allowMouse;


    public Animation animation = new EaseInOutQuad(300,1);

    private Integer cachedValue;

    public BindSetting(Module parent, String name) {
        super(parent, name, -1);
        this.allowMouse = true;
        cachedValue = -1;
    }


    public BindSetting(Module parent, String name, boolean allowMouse) {
        super(parent, name, -1);
        this.allowMouse = allowMouse;
        cachedValue = -1;
    }

    public BindSetting(Module parent, String name, Integer value) {
        super(parent, name, value);
        this.allowMouse = true;
        cachedValue = value;
    }

    public BindSetting(Module parent, String name, Integer value, boolean allowMouse) {
        super(parent, name, value);
        this.allowMouse = allowMouse;
        cachedValue = value;
    }

    @Override
    public BindSetting setVisible(Supplier<Boolean> value) {
        return (BindSetting) super.setVisible(value);
    }

    @Override
    public BindSetting set(Integer value) {
        BindSetting set = (BindSetting) super.set(value);
        this.cachedValue = super.getValue();
        return set;
    }

    @Override
    public BindSetting onAction(Runnable action) {
        return (BindSetting) super.onAction(() -> {
            action.run();
            this.cachedValue = super.getValue();
        });
    }

    @Override
    public BindSetting onSetVisible(Runnable action) {
        return (BindSetting) super.onSetVisible(action);
    }


    public Integer get() {
        if (cachedValue == null) {
            cachedValue = super.getValue();
        }
        return cachedValue;
    }
    @Override
    public Integer getValue() {
        if (cachedValue == null) {
            cachedValue = super.getValue();
        }
        return cachedValue;
    }
}
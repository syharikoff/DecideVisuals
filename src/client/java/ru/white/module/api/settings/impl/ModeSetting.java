package ru.white.module.api.settings.impl;


import ru.white.module.api.settings.Setting;
import ru.white.utils.animation.satoshi.Animation;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.module.api.Module;


import java.util.List;
import java.util.function.Supplier;

public class ModeSetting extends Setting<String> {

    public List<String> values;
    private String cachedValue;


    public ru.white.utils.animation.satoshi.Animation animation = new EaseInOutQuad(300,1);


    public  Animation animation2 = new EaseInOutQuad(300,1);

    public  Animation animation3 = new EaseInOutQuad(300,1);


    public boolean opened;

    public ModeSetting(Module parent, String name, String... values) {
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
    public ModeSetting set(String value) {
        ModeSetting set = (ModeSetting) super.set(value);
        this.cachedValue = super.getValue();
        return set;
    }

    @Override
    public ModeSetting setVisible(Supplier<Boolean> value) {
        return (ModeSetting) super.setVisible(value);
    }

    @Override
    public ModeSetting onAction(Runnable action) {
        return (ModeSetting) super.onAction(() -> {
            action.run();
            this.cachedValue = super.getValue();
        });
    }

    @Override
    public ModeSetting onSetVisible(Runnable action) {
        return (ModeSetting) super.onSetVisible(action);
    }

    @Override
    public String getValue() {
        if (cachedValue == null) {
            cachedValue = super.getValue();
        }
        return cachedValue;
    }
}
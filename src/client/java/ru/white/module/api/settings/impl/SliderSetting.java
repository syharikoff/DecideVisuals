package ru.white.module.api.settings.impl;


import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;
import ru.white.utils.animation.satoshi.Animation;
import ru.white.utils.animation.satoshi.EaseInOutQuad;


import java.util.function.Supplier;

public class SliderSetting extends Setting<Float> {
    public float min;
    public float max;
    public float increment;
    private Float cachedValue;

    public  Animation animation = new EaseInOutQuad(300,1);

    public  Animation animation2 = new EaseInOutQuad(300,1);

    public  Animation animation3 = new EaseInOutQuad(300,1);

    public SliderSetting(Module parent, String name, float value, float min, float max, float increment) {
        super(parent, name, value);
        this.min = min;
        this.max = max;
        this.increment = increment;
        cachedValue = value;
    }

    @Override
    public SliderSetting set(Float value) {
        SliderSetting set = (SliderSetting) super.set(value);
        this.cachedValue = super.getValue();
        return set;
    }

    @Override
    public SliderSetting setVisible(Supplier<Boolean> value) {
        return (SliderSetting) super.setVisible(value);
    }

    @Override
    public SliderSetting onAction(Runnable action) {
        return (SliderSetting) super.onAction(() -> {
            action.run();
            this.cachedValue = super.getValue();
        });
    }

    @Override
    public SliderSetting onSetVisible(Runnable action) {
        return (SliderSetting) super.onSetVisible(action);
    }

    @Override
    public Float getValue() {
        if (cachedValue == null) {
            cachedValue = super.getValue();
        }
        return cachedValue;
    }
}
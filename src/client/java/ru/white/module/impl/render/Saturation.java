package ru.white.module.impl.render;

import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.render.SaturationPipeline;

@ModuleInfo(
        name = "Saturation",
        desc = "Постобработка цвета: насыщенность, оттенок, контраст",
        category = Category.VISUALS
)
public class Saturation extends Module {

    public SliderSetting brightness = new SliderSetting(this, "Яркость", 0.00f, -1.00f, 1.00f, 0.01f);

    public SliderSetting saturation = new SliderSetting(this, "Насыщенность", 1.00f, 0.00f, 2.00f, 0.01f);

    public SliderSetting contrast = new SliderSetting(this, "Контраст", 1.00f, 0.00f, 2.00f, 0.01f);

    public SliderSetting hue = new SliderSetting(this, "Оттенок", 0.00f, -180.00f, 180.00f, 1.00f);

    @EventHandler
    public void onDisplay(EventDisplay e) {
        if (mc.player == null || mc.world == null) return;

        float b = brightness.getValue();
        float s = saturation.getValue();
        float c = contrast.getValue();
        float h = hue.getValue();

        boolean identity = Math.abs(b) < 0.001f
                && Math.abs(s - 1.0f) < 0.001f
                && Math.abs(c - 1.0f) < 0.001f
                && Math.abs(h) < 0.001f;
        if (identity) return;

        SaturationPipeline.draw(b, s, c, h);
    }
}

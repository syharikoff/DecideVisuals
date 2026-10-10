package ru.decide.module.impl.render;

import ru.decide.manager.event_impl.FogEvent;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.module.api.settings.impl.ColorSetting;
import ru.decide.module.api.settings.impl.ModeSetting;
import ru.decide.module.api.settings.impl.SliderSetting;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.other.Instance;

@ModuleInfo(
        name = "World Tweaks",
        desc = "Мелкие настройки отображения мира",
        category = Category.VISUALS
)
public class WorldTweaks extends Module {

    public static WorldTweaks get() {
        return Instance.get(WorldTweaks.class);
    }

    public BooleanSetting times = new BooleanSetting(this,"Менять время",true);
    public BooleanSetting fogs = new BooleanSetting(this,"Менять туман",true);
    public SliderSetting time = new SliderSetting(this,"Время", 12,0,24,1).setVisible(() -> times.getValue());
    public SliderSetting fog = new SliderSetting(this,"Дистанция тумана",100, 2,200,1).setVisible(() -> fogs.getValue());
    public ModeSetting typeColor = new ModeSetting(this,"Режим цвета","Тема","Свой");

    public ColorSetting tintColor = new ColorSetting(this, "Цвет", 0xFF00FFFF).setVisible(() -> typeColor.is("Свой"));

    public int getColor() {

        if(typeColor.is("Тема")) {
            return ColorUtil.getClientColor1(1);
        }

        return tintColor.getValue();
    }
    @EventHandler
    public void onFog(FogEvent e) {
        if(fogs.getValue()) {
            e.setDistance(fog.getValue());
            e.setColor(getColor());
            e.cancel();
        }

    }

}

package ru.white.module.impl.render;

import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.ShaderSkyRenderer;
import lombok.Getter;

@Getter
@ModuleInfo(name = "Shader Sky", category = Category.VISUALS, desc = "Shader sky with blur or procedural shader")
public class ShaderSky extends Module {

    private static ShaderSky instance;

    public ModeSetting mode = new ModeSetting(this, "Режим",  "Aurora", "Night", "Snow", "Sky", "Star", "Glow", "Full", "WebShader", "Plasma", "ChamsFill", "BaseWarp", "Waves", "PhobAurora", "BlackHole", "Galaxy");
    public SliderSetting intensity = new SliderSetting(this, "Сила",
            1.0f, 0.1f, 1.0f, 0.05f);
   // public SliderSetting blurRadius = new SliderSetting(this, "Сила размытия", 3.0f, 1.0f, 8.0f, 0.5f)
   //        ;
 //   public SliderSetting blurIterations = new SliderSetting(this, "Качество", 3, 1, 6, 1)
       ;
    public SliderSetting speed = new SliderSetting(this, "Скорость", 1.0f, 0.1f, 3.0f, 0.1f)
            ;
    public SliderSetting scale = new SliderSetting(this, "Масштаб", 1.1f, 0.45f, 3.0f, 0.05f)
          ;
    public SliderSetting stars = new SliderSetting(this, "Звёзды", 0.75f, 0.0f, 1.0f, 0.05f)
;


    public ModeSetting typeColor = new ModeSetting(this,"Режим цвета","Тема","Свой");

    public ColorSetting color1 = new ColorSetting(this, "Цвет 1", 0xFF79E6FF)
            .setVisible(() ->  typeColor.is("Свой"));
    public ColorSetting color2 = new ColorSetting(this, "Цвет 2", 0xFFFF7AE6)
            .setVisible(() ->  typeColor.is("Свой"));
    public BooleanSetting vanillaSky = new BooleanSetting(this, "Ванильное небо", false);
    public ShaderSky() {
        instance = this;
    }

    public static ShaderSky getInstance() {
        return instance;
    }


    public static int geteColor2() {

        if(getInstance().typeColor.is("Тема")) {
            return ColorUtil.multDark(ColorUtil.getClientColor1(1),0.25F);
        }

        return getInstance().color2.getValue();
    }


    public static int geteColor1() {

        if(getInstance().typeColor.is("Тема")) {
            return ColorUtil.getClientColor1(1);
        }

        return getInstance().color1.getValue();
    }

    @Override
    protected void onEnable() {
        ShaderSkyRenderer renderer = ShaderSkyRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
    }

    @Override
    protected void onDisable() {
        ShaderSkyRenderer.getInstance().setEnabled(false);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!isEnabled()) return;

        ShaderSkyRenderer renderer = ShaderSkyRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
    }
}

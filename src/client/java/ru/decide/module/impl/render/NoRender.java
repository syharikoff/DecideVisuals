package ru.decide.module.impl.render;

import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.utils.other.Instance;

@ModuleInfo(
        name = "No Render",
        desc = "Сухарк сотрёт всю хуйню с вашего экрана",
        category = Category.UTILITIES
)
public class NoRender extends Module {

    public static NoRender getInstance() {
        return Instance.get(NoRender.class);
    }

    public BooleanSetting ignoreFire = new BooleanSetting(this,"Убирать огонь",true);
    public BooleanSetting ignoreLava = new BooleanSetting(this,"Убирать туман лавы",true);
    public BooleanSetting ignoreZalupa = new BooleanSetting(this,"Убирать плохие эффекты",true);
    public BooleanSetting ignoreScoreboard = new BooleanSetting(this,"Убирать Скорборт",false);
    public BooleanSetting ignoreBossBar = new BooleanSetting(this,"Убирать Босс бар",false);
    public BooleanSetting noCameraClip = new BooleanSetting(this,"Камера сквозь блоки",false);
    public BooleanSetting ignoreTotemPop = new BooleanSetting(this,"Убирать тотем на экране",true);
    public BooleanSetting removeCamreZalupa = new BooleanSetting(this,"Убирать дерганее камеры",true);




}

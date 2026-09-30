package ru.white.module.impl.display;

import org.lwjgl.glfw.GLFW;
import ru.white.Client;
import ru.white.manager.event_impl.EventKey;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.MultiBooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.ui.lv.LvGui;
import ru.white.utils.math.Keyboard;

@ModuleInfo(
        name = "Click Gui",
        category = Category.HUD,
        key = GLFW.GLFW_KEY_RIGHT_SHIFT,
        desc = "Позволяет настроить вид GUI клиента",
        autoEnabled = true,
        allowDisable = false
)
public class ClickGui extends Module {

    public MultiBooleanSetting effect = new MultiBooleanSetting(this, "Эффекты",
            new BooleanSetting("Серый фон", false),
            new BooleanSetting("Затемнять фон", true),
            new BooleanSetting("Размывать фон", true),
            new BooleanSetting("Шейдер", false),
            new BooleanSetting("Частицы", true),
            new BooleanSetting("Скан линии", true),
            new BooleanSetting("Свечение", true),
            new BooleanSetting("Точки", true));

    public ModeSetting closeAnimation = new ModeSetting(this, "Анимация закрытия", "Default", "3D");

    public SliderSetting size = new SliderSetting(this,"Размер",1.0F,0.5F,1.5F,0.1F);

    public ClickGui() {
    }

    @EventHandler
    public void onKey(EventKey event) {
        if (event.getKey() == getKey()) {
            mc.setScreen(Client.get.clickGuiScreen());
        }
    }
}

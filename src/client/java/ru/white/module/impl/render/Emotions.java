package ru.white.module.impl.render;

import org.lwjgl.glfw.GLFW;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventKey;
import ru.white.manager.event_impl.EventKeyRelease;
import ru.white.manager.event_impl.EventLook;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.impl.render.emotions.EmotionWheelManager;

@ModuleInfo(
        name = "Emotions",
        desc = "Колесо эмоций с анимацией игрока",
        category = Category.VISUALS,
        autoEnabled = true
)
public class Emotions extends Module {
    private static Emotions instance;

    private final BindSetting wheelBind = new BindSetting(this, "Колесо эмоций", GLFW.GLFW_KEY_V);

    public Emotions() {
        instance = this;
    }

    public static Emotions getInstance() {
        return instance;
    }

    public BindSetting getWheelBind() {
        return wheelBind;
    }

    @EventHandler
    public void onKey(EventKey event) {
        EmotionWheelManager.getInstance().onKeyPress(event.getKey());
    }

    @EventHandler
    public void onKeyRelease(EventKeyRelease event) {
        EmotionWheelManager.getInstance().onKeyRelease(event.getKey());
    }

    @EventHandler
    public void onMouse(EventLook event) {
        EmotionWheelManager manager = EmotionWheelManager.getInstance();
        if (manager.isMouseCaptured()) {
            manager.onMouseMove(event.getYaw(), event.getPitch());
            event.cancel();
        }
    }

    @EventHandler
    public void onTick(EventTick event) {
        EmotionWheelManager.getInstance().onTick();
    }

    @EventHandler
    public void onDisplay(EventDisplay event) {
        EmotionWheelManager.getInstance().render();
    }

    @Override
    protected void onDisable() {
        EmotionWheelManager.getInstance().closeWheel(true);
    }
}

package ru.decide.module.impl.display;

import org.joml.Vector2f;
import ru.decide.manager.event_impl.EventDisplay;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.manager.event_impl.EventUpdate;
import ru.decide.manager.event_impl.MousePressEvent;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.*;
import ru.decide.module.impl.display.interfaceimpl.*;
import ru.decide.utils.other.Instance;

@ModuleInfo(
        name = "InterFace",
        desc = "Настройка интерфейса (HUD) клиента",
        category = Category.HUD
)
public class InterFace extends Module {

    public static InterFace getInstance() {
        return Instance.get(InterFace.class);
    }

    public MultiBooleanSetting element = new MultiBooleanSetting(this, "Элементы",
            new BooleanSetting("Water mark", true),
            new BooleanSetting("Information", true),
            new BooleanSetting("Key Binds", true),
            new BooleanSetting("Potions", true),
            new BooleanSetting("Cooldowns", true),
            new BooleanSetting("Music Player", true),
            new BooleanSetting("Notifications", true),
            new BooleanSetting("Target Hud", true),
            new BooleanSetting("Armor Hud", true));

    public SliderSetting volume = new SliderSetting(this, "Громкость уведомления", 0.5F, 0.1F, 1.0F, 0.1F);
    public ModeSetting typeNotify = new ModeSetting(this, "Тип уведомления",
            "Первый", "Второй", "Третий");

    public SliderSetting sizeHud = new SliderSetting(this,"Размер интерфейса",1.0F,0.5F,1.5F,0.05F);
    public SliderSetting alphaHUD = new SliderSetting(this,"Прозрачность худа",0.6F,0.0F,0.9F,0.1F);
    public StringSetting userName = new StringSetting(this, "Имя в худе", "User");

    public BooleanSetting notifyEffects = new BooleanSetting(this, "эффектах", true);
    public BooleanSetting notifyModules = new BooleanSetting(this, "модулях", true);
    public BooleanSetting notifyArmor = new BooleanSetting(this, "броне", true);

    public DragSetting waterMark = new DragSetting(this, "WaterMark", new Vector2f(10, 10));
    public DragSetting information = new DragSetting(this, "Information", new Vector2f(10, 30));
    public DragSetting keyBind = new DragSetting(this, "Key Binds", new Vector2f(10, 33));
    public DragSetting potion = new DragSetting(this, "Potions", new Vector2f(90, 33));
    public DragSetting cooldowns = new DragSetting(this, "Cooldowns", new Vector2f(160, 33));
    public DragSetting music = new DragSetting(this, "Music Player", new Vector2f(10, 400));
    public DragSetting notifications = new DragSetting(this, "Notifications", new Vector2f(0, 200));
    public DragSetting targetHudDrag = new DragSetting(this, "Target Hud", new Vector2f(90, 40));
    public DragSetting armorHud = new DragSetting(this, "Armor Hud", new Vector2f(300, 200));

    private final WaterMark waterMarkElemnt = new WaterMark();
    private final Information informationElemnt = new Information();
    private final KeyBinds keyBinds = new KeyBinds();
    private final Potions potions = new Potions();
    private final Cooldowns cooldownsHud = new Cooldowns();
    private final MusicHud musicHud = new MusicHud();
    private final Notify notifyHud = new Notify();
    private final TargetHud targetHud = new TargetHud();
    private final ArmorHud armorHudElement = new ArmorHud();

    public InterFace() {
        notifications.lockX = true;
    }

    @Override
    protected void onDisable() {
        musicHud.shutdown();
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || !isEnabled()) return;
        if (element.getValue("Music Player")) musicHud.onTick();
        if (element.getValue("Notifications")) {
            notifyHud.onTick(notifyModules, notifyArmor, notifyEffects);
        }
    }

    @EventHandler
    public void onMousePress(MousePressEvent event) {
        if (!isEnabled()) return;
        if (element.getValue("Music Player")) musicHud.onMouseClick(event);
    }

    @EventHandler
    public void onDisplayEvent(EventDisplay eventDisplay) {
        if (mc.player == null || mc.world == null || !isEnabled()) return;

        try { if (element.getValue("Potions")) potions.onRender(potion, this, eventDisplay); } catch (Exception ignored) {}
        try { if (element.getValue("Cooldowns")) cooldownsHud.onRender(cooldowns, this, eventDisplay); } catch (Exception ignored) {}
        try { if (element.getValue("Information")) informationElemnt.onRender(information, this); } catch (Exception ignored) {}
        try { if (element.getValue("Key Binds")) keyBinds.onRender(keyBind, this); } catch (Exception ignored) {}
        try { if (element.getValue("Water mark")) waterMarkElemnt.onRender(waterMark, this); } catch (Exception ignored) {}
        try { if (element.getValue("Music Player")) musicHud.onRender(music, this); } catch (Exception ignored) {}
        try { if (element.getValue("Notifications")) notifyHud.onRender(notifications, this, eventDisplay); } catch (Exception ignored) {}
        try { if (element.getValue("Target Hud")) targetHud.onRender(targetHudDrag, this, eventDisplay); } catch (Exception ignored) {}
        try { if (element.getValue("Armor Hud")) armorHudElement.onRender(armorHud, this); } catch (Exception ignored) {}
    }
}

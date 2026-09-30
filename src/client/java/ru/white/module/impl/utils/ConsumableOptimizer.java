package ru.white.module.impl.utils;

import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.impl.utils.consumable.ConsumableHandler;
import ru.white.utils.other.Instance;

@ModuleInfo(
        name = "Consumable Optimizer",
        category = Category.UTILITIES,
        desc = "Оптимизирует потребление еды и зелий"
)
public class ConsumableOptimizer extends Module {

    public static ConsumableOptimizer get() {
        return Instance.get(ConsumableOptimizer.class);
    }

    public BooleanSetting fastConsume = new BooleanSetting(this, "Быстрое потребление", true);
    public BooleanSetting soundFix = new BooleanSetting(this, "Исправление звука", true);
    public BooleanSetting animationSkip = new BooleanSetting(this, "Пропуск анимации", true);

    @Override
    public void onEnable() {
        ConsumableHandler.STATE.startServerWait();
    }

    @Override
    public void onDisable() {
        ConsumableHandler.STATE.stopServerWait();
        ConsumableHandler.STATE.resetEquipmentAnimation();
        ConsumableHandler.STATE.resetBurpSuppression();
    }
}

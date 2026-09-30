package ru.white.module.impl.render;

import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;

@ModuleInfo(
        name = "Hold My Items",
        desc = "Кастомные анимации рук от первого лица",
        category = Category.VISUALS
)
public class HoldMyItems extends Module {
    private static HoldMyItems INSTANCE;

    public final BooleanSetting swapHands = new BooleanSetting(this, "Смена рук", false);
    public final SliderSetting smoothness = new SliderSetting(this, "Плавность", 1.0F, 0.35F, 2.5F, 0.05F);
    public final SliderSetting mainHandX = new SliderSetting(this, "Главная X", 0.0F, -1.0F, 1.0F, 0.05F);
    public final SliderSetting mainHandY = new SliderSetting(this, "Главная Y", 0.0F, -1.0F, 1.0F, 0.05F);
    public final SliderSetting mainHandZ = new SliderSetting(this, "Главная Z", 0.0F, -2.5F, 2.5F, 0.05F);
    public final SliderSetting offHandX = new SliderSetting(this, "Вторая X", 0.0F, -1.0F, 1.0F, 0.05F);
    public final SliderSetting offHandY = new SliderSetting(this, "Вторая Y", 0.0F, -1.0F, 1.0F, 0.05F);
    public final SliderSetting offHandZ = new SliderSetting(this, "Вторая Z", 0.0F, -2.5F, 2.5F, 0.05F);
    public final BooleanSetting climbAndCrawl = new BooleanSetting(this, "Лазание и ползание", true);
    public final BooleanSetting swimmingAnimation = new BooleanSetting(this, "Плавание", true);
    public final BooleanSetting mb3DCompat = new BooleanSetting(this, "3D Совместимость", false);
    public final ModeSetting animationType = new ModeSetting(this, "Тип анимации", "Шарп", "Обычный");

    public HoldMyItems() {
        INSTANCE = this;
    }

    public static HoldMyItems getInstance() {
        return INSTANCE;
    }

    public boolean isSharpAnimation() {
        return animationType.is("Шарп");
    }
}
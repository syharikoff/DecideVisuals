package ru.white.module.impl.utils;

import net.minecraft.item.Item;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.other.Instance;

@ModuleInfo(
        name = "Item Scroller",
        desc = "Помогает скролить предметы",
        category = Category.UTILITIES
)
public class ItemScroller extends Module {

    public static ItemScroller getInstance() {
        return Instance.get(ItemScroller.class);
    }

    public SliderSetting delay = new SliderSetting(this,"Задержка",50,0,100,1);


}

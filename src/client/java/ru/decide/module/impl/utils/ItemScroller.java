package ru.decide.module.impl.utils;

import net.minecraft.item.Item;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.SliderSetting;
import ru.decide.utils.other.Instance;

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

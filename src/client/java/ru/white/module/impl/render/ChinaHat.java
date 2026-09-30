package ru.white.module.impl.render;

import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.utils.other.Instance;

@ModuleInfo(
        name = "China Hat",
        desc = "Китайская шляпа",
        category = Category.VISUALS
)
public class ChinaHat extends Module {


    public static ChinaHat getInstance() {
        return Instance.get(ChinaHat.class);
    }


}

package ru.decide.module.impl.render;

import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.utils.other.Instance;

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

package ru.decide.module.impl.utils;

import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.optimization.FrameSyncManager;
import ru.decide.utils.other.Instance;

@ModuleInfo(
        name = "Frame Sync",
        category = Category.UTILITIES,
        desc = "Ограничивает FPS до частоты монитора для повышения производительности",
        autoEnabled = true
)
public class FrameSync extends Module {

    public static FrameSync get() {
        return Instance.get(FrameSync.class);
    }

    @Override
    public void onEnable() {
        FrameSyncManager.init();
        FrameSyncManager.setEnabled(true);
    }

    @Override
    public void onDisable() {
        FrameSyncManager.setEnabled(false);
    }
}

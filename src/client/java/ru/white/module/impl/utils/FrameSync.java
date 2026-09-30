package ru.white.module.impl.utils;

import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.optimization.FrameSyncManager;
import ru.white.utils.other.Instance;

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

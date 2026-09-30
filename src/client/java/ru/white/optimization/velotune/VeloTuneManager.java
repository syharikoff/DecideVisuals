package ru.white.optimization.velotune;

import ru.white.optimization.velotune.perf.ConfigManager;
import ru.white.optimization.velotune.perf.PerformanceGovernor;
import ru.white.optimization.velotune.perf.VeloTuneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(value=EnvType.CLIENT)
public final class VeloTuneManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("VeloTune");
    private static volatile VeloTuneConfig config = new VeloTuneConfig();
    private static final PerformanceGovernor GOVERNOR = new PerformanceGovernor();

    public static void init() {
        config = ConfigManager.load();
        GOVERNOR.configure(config);
        if (!config.enabled) {
            LOGGER.warn("VeloTune is globally disabled in config/velotune.json");
        }
        LOGGER.info("VeloTune 0.4.1 Extreme active: target={} FPS, entities<={} blocks, vertical cutoff={} blocks, bones={} Hz, name-tag text={} Hz, HUD/screens={} Hz, raw input={}, mouse={} Hz (0=unlimited)",
            config.adaptive.targetFps, config.extreme.entityRenderDistance, config.extreme.verticalCullDepth,
            config.extreme.playerPoseUpdatesPerSecond, config.extreme.nameTagTextUpdatesPerSecond,
            config.extreme.hudUpdatesPerSecond, config.extreme.rawInputBuffer, config.extreme.mouseUpdatesPerSecond);
    }

    public static VeloTuneConfig config() {
        return config;
    }

    public static PerformanceGovernor governor() {
        return GOVERNOR;
    }

    public static void saveConfig() {
        config.sanitize();
        GOVERNOR.configure(config);
        ConfigManager.save(config);
    }

    public static boolean enabled() {
        return config.enabled;
    }
}

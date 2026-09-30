package ru.white.optimization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final String CONFIG_FILE_NAME = "framesync.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance;
    private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);

    public int targetMonitorHz = 60;
    public boolean enableRenderLimit = true;
    public boolean autoDetectMonitorHz = true;
    public int minFpsLimit = 30;
    public boolean showFpsIndicator = false;
    public boolean lowEndMode = false;
    public boolean unlimitedFps = false;

    public static ModConfig getInstance() {
        if (instance == null) {
            instance = loadConfig();
        }
        return instance;
    }

    private static ModConfig loadConfig() {
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                ModConfig config = GSON.fromJson(json, ModConfig.class);
                if (config.targetMonitorHz <= 0) {
                    config.targetMonitorHz = 60;
                }
                if (config.minFpsLimit <= 0) {
                    config.minFpsLimit = 30;
                }
                OptimizationInit.LOGGER.info("Config loaded: {} Hz, enabled: {}",
                        config.targetMonitorHz, config.enableRenderLimit);
                return config;
            } catch (Exception e) {
                OptimizationInit.LOGGER.error("Failed to load config, using defaults: {}", e.getMessage());
            }
        }
        ModConfig defaultConfig = new ModConfig();
        if (defaultConfig.autoDetectMonitorHz) {
            defaultConfig.targetMonitorHz = RenderLimiter.safeDetectMonitorHz();
        }
        defaultConfig.saveConfig();
        return defaultConfig;
    }

    public void saveConfig() {
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(this);
            Files.writeString(configPath, json);
            OptimizationInit.LOGGER.info("Config saved: {} Hz, enabled: {}", this.targetMonitorHz, this.enableRenderLimit);
        } catch (IOException e) {
            OptimizationInit.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    public void setTargetMonitorHz(int hz) {
        if (hz <= 0) {
            OptimizationInit.LOGGER.warn("Invalid Hz value: {}, ignoring", hz);
        } else {
            this.targetMonitorHz = hz;
            this.unlimitedFps = false;
            this.saveConfig();
        }
    }

    public void setUnlimitedFps(boolean unlimited) {
        this.unlimitedFps = unlimited;
        this.saveConfig();
    }

    public boolean isUnlimitedFps() {
        return this.unlimitedFps;
    }

    public void setEnableRenderLimit(boolean enabled) {
        this.enableRenderLimit = enabled;
        this.saveConfig();
    }

    public void setAutoDetectMonitorHz(boolean autoDetect) {
        this.autoDetectMonitorHz = autoDetect;
        if (autoDetect) {
            this.setTargetMonitorHz(RenderLimiter.safeDetectMonitorHz());
        }
        this.saveConfig();
    }

    public void setMinFpsLimit(int minFps) {
        if (minFps <= 0) {
            OptimizationInit.LOGGER.warn("Invalid min FPS value: {}, ignoring", minFps);
        } else {
            this.minFpsLimit = minFps;
            this.saveConfig();
        }
    }

    public void setShowFpsIndicator(boolean show) {
        this.showFpsIndicator = show;
        this.saveConfig();
    }

    public void setLowEndMode(boolean lowEnd) {
        this.lowEndMode = lowEnd;
        this.saveConfig();
    }

    public int getEffectiveHz() {
        return Math.max(this.targetMonitorHz, this.minFpsLimit);
    }

    public void resetToDefaults() {
        this.targetMonitorHz = this.autoDetectMonitorHz ? RenderLimiter.safeDetectMonitorHz() : 60;
        this.enableRenderLimit = true;
        this.autoDetectMonitorHz = true;
        this.minFpsLimit = 30;
        this.showFpsIndicator = false;
        this.lowEndMode = false;
        this.unlimitedFps = false;
        this.saveConfig();
        OptimizationInit.LOGGER.info("Config reset to defaults");
    }
}
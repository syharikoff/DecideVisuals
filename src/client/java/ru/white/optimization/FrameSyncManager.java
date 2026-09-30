package ru.white.optimization;

public class FrameSyncManager {
    private static FrameSyncManager instance;
    private boolean enabled;

    public static void applyLwjglTweaks() {
        System.setProperty("org.lwjgl.util.NoChecks", "true");
        System.setProperty("org.lwjgl.util.Debug", "false");
    }

    private FrameSyncManager() {
    }

    public static FrameSyncManager getInstance() {
        if (instance == null) {
            instance = new FrameSyncManager();
        }
        return instance;
    }

    public static void init() {
        ModConfig config = ModConfig.getInstance();
        int hz = config.autoDetectMonitorHz ? detectMonitorHz() : config.targetMonitorHz;
        FrameSyncHandler.init(hz);
        OptimizationInit.LOGGER.info("FrameSyncManager initialized with {} Hz", hz);
    }

    public static void setEnabled(boolean enabled) {
        getInstance().enabled = enabled;
        if (enabled) {
            ModConfig config = ModConfig.getInstance();
            int hz = config.autoDetectMonitorHz ? detectMonitorHz() : config.targetMonitorHz;
            FrameSyncHandler.init(hz);
        }
        OptimizationInit.LOGGER.info("FrameSyncManager {}", enabled ? "enabled" : "disabled");
    }

    public static boolean isEnabled() {
        return getInstance().enabled;
    }

    private static int detectMonitorHz() {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc != null && mc.getWindow() != null) {
                return FrameSyncHandler.detectMonitorHz();
            }
        } catch (Exception e) {
            OptimizationInit.LOGGER.debug("Client not ready for monitor detection: {}", e.getMessage());
        }
        return 60;
    }
}

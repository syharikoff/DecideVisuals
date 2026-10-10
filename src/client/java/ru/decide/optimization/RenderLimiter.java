package ru.decide.optimization;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

public class RenderLimiter {
    private int monitorHz;
    private long lastRenderTime;
    private boolean enabled;
    private long frameInterval;
    private long[] frameTimeHistory;
    private int frameTimeIndex;
    private static final int FRAME_HISTORY_SIZE = 10;
    private long accumulatedTime;
    private boolean lowEndMode;

    public RenderLimiter(int monitorHz) {
        this.monitorHz = monitorHz;
        this.enabled = true;
        this.lastRenderTime = System.nanoTime();
        this.frameInterval = 1000000000L / monitorHz;
        this.frameTimeHistory = new long[FRAME_HISTORY_SIZE];
        this.frameTimeIndex = 0;
        this.accumulatedTime = 0L;
        this.lowEndMode = ModConfig.getInstance().lowEndMode;
        OptimizationInit.LOGGER.info("RenderLimiter initialized with {} Hz", monitorHz);
    }

    public boolean canRender() {
        if (!this.enabled) {
            return true;
        }
        ModConfig config = ModConfig.getInstance();
        return config.lowEndMode ? this.canRenderSmooth() : this.canRenderAggressive();
    }

    private boolean canRenderSmooth() {
        long now = System.nanoTime();
        long deltaTime = now - this.lastRenderTime;
        this.accumulatedTime += deltaTime;
        if (this.accumulatedTime >= this.frameInterval) {
            this.frameTimeHistory[this.frameTimeIndex] = deltaTime;
            this.frameTimeIndex = (this.frameTimeIndex + 1) % FRAME_HISTORY_SIZE;
            this.accumulatedTime -= this.frameInterval;
            if (this.accumulatedTime > this.frameInterval * 2L) {
                this.accumulatedTime = this.frameInterval;
            }
            this.lastRenderTime = now;
            return true;
        }
        if (deltaTime > this.frameInterval * 3L) {
            this.lastRenderTime = now;
            this.accumulatedTime = 0L;
            return true;
        }
        return false;
    }

    private boolean canRenderAggressive() {
        long now = System.nanoTime();
        if (now - this.lastRenderTime >= this.frameInterval) {
            this.frameTimeHistory[this.frameTimeIndex] = now - this.lastRenderTime;
            this.frameTimeIndex = (this.frameTimeIndex + 1) % FRAME_HISTORY_SIZE;
            this.lastRenderTime = now;
            return true;
        }
        return false;
    }

    public boolean canRenderOld() {
        if (!this.enabled) {
            return true;
        }
        long now = System.nanoTime();
        if (now - this.lastRenderTime >= this.frameInterval) {
            this.lastRenderTime = now;
            return true;
        }
        return false;
    }

    public void setMonitorHz(int hz) {
        if (hz <= 0) {
            OptimizationInit.LOGGER.warn("Invalid monitor Hz value: {}, using default 60", hz);
            hz = 60;
        }
        this.monitorHz = hz;
        this.frameInterval = 1000000000L / hz;
        this.lastRenderTime = System.nanoTime();
        this.accumulatedTime = 0L;
        this.frameTimeIndex = 0;
        OptimizationInit.LOGGER.info("Monitor Hz updated to: {}", hz);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            this.lastRenderTime = System.nanoTime();
            this.accumulatedTime = 0L;
            this.frameTimeIndex = 0;
        }
        OptimizationInit.LOGGER.info("RenderLimiter {}", enabled ? "enabled" : "disabled");
    }

    public int getMonitorHz() {
        return this.monitorHz;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public double getCurrentFPS() {
        if (!this.enabled) {
            return -1.0;
        }
        long totalTime = 0L;
        int validFrames = 0;
        for (long frameTime : this.frameTimeHistory) {
            if (frameTime <= 0L) continue;
            totalTime += frameTime;
            ++validFrames;
        }
        if (validFrames > 0) {
            long avgFrameTime = totalTime / validFrames;
            return 1.0E9 / avgFrameTime;
        }
        return this.monitorHz;
    }

    public double getCurrentFPSOld() {
        return !this.enabled ? -1.0 : this.monitorHz;
    }

    public static int detectMonitorHz() {
        try {
            if (!GLFW.glfwInit()) {
                OptimizationInit.LOGGER.warn("GLFW not initialized, using default refresh rate: 60 Hz");
                return 60;
            }
            long primaryMonitor = GLFW.glfwGetPrimaryMonitor();
            if (primaryMonitor == 0L) {
                OptimizationInit.LOGGER.warn("No primary monitor found, using default refresh rate: 60 Hz");
                return 60;
            }
            GLFWVidMode vidMode = GLFW.glfwGetVideoMode(primaryMonitor);
            if (vidMode != null) {
                int refreshRate = vidMode.refreshRate();
                OptimizationInit.LOGGER.info("Detected monitor refresh rate: {} Hz", refreshRate);
                return refreshRate;
            }
        } catch (Exception e) {
            OptimizationInit.LOGGER.warn("Failed to detect monitor refresh rate: {}", e.getMessage());
        }
        OptimizationInit.LOGGER.info("Using default refresh rate: 60 Hz");
        return 60;
    }

    public static int safeDetectMonitorHz() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getWindow() != null) {
                return RenderLimiter.detectMonitorHz();
            }
        } catch (Exception e) {
            OptimizationInit.LOGGER.debug("Client not ready for monitor detection: {}", e.getMessage());
        }
        OptimizationInit.LOGGER.info("Client not ready, using default refresh rate: 60 Hz");
        return 60;
    }
}
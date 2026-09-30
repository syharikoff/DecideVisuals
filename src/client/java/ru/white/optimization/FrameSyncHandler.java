package ru.white.optimization;

public final class FrameSyncHandler {
    private static final long NANOS = 1_000_000_000L;
    private static boolean drawThisFrame = true;
    private static int monitorHz = 60;
    private static long frameInterval = 16_666_666L;
    private static long lastRenderTime = System.nanoTime();

    private FrameSyncHandler() {
    }

    public static void init(int hz) {
        if (hz <= 0) hz = 60;
        monitorHz = hz;
        frameInterval = NANOS / hz;
        lastRenderTime = System.nanoTime();
        drawThisFrame = true;
        OptimizationInit.LOGGER.info("FrameSyncHandler initialized with {} Hz, interval={}ns", hz, frameInterval);
    }

    public static void tick() {
        if (!FrameSyncManager.isEnabled()) return;
        int hz = detectMonitorHz();
        if (hz <= 0) hz = 60;
        if (hz != monitorHz) {
            monitorHz = hz;
            frameInterval = NANOS / hz;
            OptimizationInit.LOGGER.info("FrameSyncHandler Hz updated to {}", hz);
        }
    }

    public static boolean beginFrame() {
        if (!FrameSyncManager.isEnabled()) {
            drawThisFrame = true;
            return true;
        }
        long now = System.nanoTime();
        if (now - lastRenderTime < frameInterval) {
            drawThisFrame = false;
            return false;
        }
        lastRenderTime = now;
        drawThisFrame = true;
        return true;
    }

    public static boolean allowDraw() {
        return drawThisFrame;
    }

    public static int detectMonitorHz() {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            long monitor = 0L;
            if (mc != null && mc.getWindow() != null) {
                monitor = org.lwjgl.glfw.GLFW.glfwGetWindowMonitor(mc.getWindow().getHandle());
            }
            if (monitor == 0L) monitor = org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor();
            if (monitor != 0L) {
                org.lwjgl.glfw.GLFWVidMode mode = org.lwjgl.glfw.GLFW.glfwGetVideoMode(monitor);
                if (mode != null && mode.refreshRate() > 0) {
                    return mode.refreshRate();
                }
            }
        } catch (Exception e) {
            // fall through
        }
        return 60;
    }
}

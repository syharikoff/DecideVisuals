package ru.white.optimization;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptimizationInit {
    public static final Logger LOGGER = LoggerFactory.getLogger("achrone-framesync");

    private static KeyBinding configKeyBinding;
    private static boolean renderSystemInitialized;

    private OptimizationInit() {
    }

    public static void init() {
        configKeyBinding = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("Achrone Optimization Config", InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_F7, KeyBinding.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            initializeRenderSystemSafely();
            while (configKeyBinding.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ConfigScreen(null));
                }
            }
        });
    }

    private static void initializeRenderSystemSafely() {
        if (renderSystemInitialized) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        try {
            ModConfig config = ModConfig.getInstance();
            RenderManager.getInstance().initialize(config.getEffectiveHz(), config.enableRenderLimit);
            renderSystemInitialized = true;
            LOGGER.info("Render system initialized with {} Hz", config.getEffectiveHz());
        } catch (Exception e) {
            LOGGER.error("Failed to initialize render system: {}", e.getMessage());
        }
    }
}
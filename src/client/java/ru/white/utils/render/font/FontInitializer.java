package ru.white.utils.render.font;

import ru.white.Client;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class FontInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("client/FontInitializer");
    private static boolean registered = false;
    private static boolean initialized = false;
    
    public static void register() {
        if (registered) return;
        registered = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!initialized && client.getResourceManager() != null && client.getWindow() != null) {
                try {
                    FontRenderer fontRenderer = Client.get().render2D().getFontRenderer();
                    if (fontRenderer != null && !fontRenderer.isInitialized()) {
                        fontRenderer.initialize();
                        initialized = true;
                        LOGGER.info("Fonts initialized successfully");
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to initialize fonts", e);
                }
            }
        });
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
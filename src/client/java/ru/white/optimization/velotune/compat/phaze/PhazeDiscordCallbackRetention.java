package ru.white.optimization.velotune.compat.phaze;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class PhazeDiscordCallbackRetention {
    private static final Set<Object> CALLBACKS = Collections.newSetFromMap(new IdentityHashMap());

    private PhazeDiscordCallbackRetention() {
    }

    public static synchronized boolean retain(Object callback) {
        return callback != null && CALLBACKS.add(callback);
    }
}

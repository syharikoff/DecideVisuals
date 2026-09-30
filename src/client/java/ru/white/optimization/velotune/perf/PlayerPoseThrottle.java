/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 */
package ru.white.optimization.velotune.perf;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class PlayerPoseThrottle {
    private PlayerPoseThrottle() {
    }

    public static boolean canReuse(boolean strict, int cachedSignature, int currentSignature, long framesSinceUpdate, int frameInterval, long nanosSinceUpdate, int updatesPerSecond, boolean urgent) {
        if (strict) {
            long nanosPerUpdate = 1000000000L / (long)Math.max(1, updatesPerSecond);
            return nanosSinceUpdate < nanosPerUpdate;
        }
        int effectiveInterval = !urgent ? frameInterval : Math.max(2, frameInterval / 2);
        return cachedSignature == currentSignature && framesSinceUpdate < (long)effectiveInterval;
    }
}

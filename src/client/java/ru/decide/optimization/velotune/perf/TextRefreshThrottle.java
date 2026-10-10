/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 */
package ru.decide.optimization.velotune.perf;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class TextRefreshThrottle {
    private TextRefreshThrottle() {
    }

    public static long intervalNanos(int updatesPerSecond) {
        return 1000000000L / (long)Math.max(1, updatesPerSecond);
    }

    public static boolean shouldRefresh(boolean initialized, long nanosSinceUpdate, int updatesPerSecond) {
        return !initialized || nanosSinceUpdate >= TextRefreshThrottle.intervalNanos(updatesPerSecond);
    }
}

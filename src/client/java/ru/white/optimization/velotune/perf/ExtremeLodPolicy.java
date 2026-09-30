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
public final class ExtremeLodPolicy {
    private ExtremeLodPolicy() {
    }

    public static boolean belowCutoff(double objectY, double playerY, double depth) {
        return objectY < playerY - depth;
    }

    public static boolean beyondDistance(double distanceSquared, double distance) {
        return distanceSquared > distance * distance;
    }

    public static boolean cullChunkSection(int originY, double playerY, double verticalDepth) {
        return (double)(originY + 16) < playerY - verticalDepth;
    }
}

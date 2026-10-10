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
public final class PlayerLodPolicy {
    private PlayerLodPolicy() {
    }

    public static int poseInterval(double distanceSquared, int pressure, VeloTuneConfig.Players config) {
        int base = distanceSquared < PlayerLodPolicy.square(config.nearPoseDistance) ? 1 : (distanceSquared < PlayerLodPolicy.square(config.midPoseDistance) ? 2 : (distanceSquared < PlayerLodPolicy.square(config.farPoseDistance) ? 4 : 8));
        if (pressure >= 3) {
            return Math.min(16, base * 2);
        }
        if (pressure == 2) {
            return Math.min(12, base + Math.max(1, base / 2));
        }
        return base;
    }

    public static boolean skipFeatures(double distanceSquared, int pressure, VeloTuneConfig.Players config) {
        double multiplier = switch (pressure) {
            case 0 -> 1.25;
            case 1 -> 1.0;
            case 2 -> 0.75;
            default -> 0.55;
        };
        return distanceSquared > PlayerLodPolicy.square(config.featureDistance * multiplier);
    }

    public static boolean skipLabel(double distanceSquared, int pressure, VeloTuneConfig.Players config) {
        if (pressure == 0) {
            return false;
        }
        double multiplier = pressure == 1 ? 1.25 : (pressure == 2 ? 1.0 : 0.75);
        return distanceSquared > PlayerLodPolicy.square(config.labelDistance * multiplier);
    }

    public static boolean cullPlayer(double distanceSquared, int pressure, VeloTuneConfig.Players config) {
        return config.emergencyCullDistantPlayers && pressure >= 3 && distanceSquared > PlayerLodPolicy.square(config.emergencyCullDistance);
    }

    private static double square(double value) {
        return value * value;
    }
}

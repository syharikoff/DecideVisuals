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
public final class Metrics {
    private static long poseHits;
    private static long poseMisses;
    private static long featureCulls;
    private static long labelCulls;
    private static long playerCulls;
    private static long entityCulls;
    private static long blockEntityCulls;
    private static long chunkSectionCulls;
    private static long shadowCulls;
    private static long particleDrops;

    private Metrics() {
    }

    public static void poseHit() {
        ++poseHits;
    }

    public static void poseMiss() {
        ++poseMisses;
    }

    public static void featureCull() {
        ++featureCulls;
    }

    public static void labelCull() {
        ++labelCulls;
    }

    public static void playerCull() {
        ++playerCulls;
    }

    public static void entityCull() {
        ++entityCulls;
    }

    public static void blockEntityCull() {
        ++blockEntityCulls;
    }

    public static void chunkSectionCull() {
        ++chunkSectionCulls;
    }

    public static void shadowCull() {
        ++shadowCulls;
    }

    public static void particleDrop() {
        ++particleDrops;
    }

    public static String snapshotAndReset() {
        String result = "pose=" + poseHits + "/" + (poseHits + poseMisses) + ", features=" + featureCulls + ", labels=" + labelCulls + ", players=" + playerCulls + ", entities=" + entityCulls + ", blockEntities=" + blockEntityCulls + ", chunkSections=" + chunkSectionCulls + ", shadows=" + shadowCulls + ", particles=" + particleDrops;
        poseHits = 0L;
        poseMisses = 0L;
        featureCulls = 0L;
        labelCulls = 0L;
        playerCulls = 0L;
        entityCulls = 0L;
        blockEntityCulls = 0L;
        chunkSectionCulls = 0L;
        shadowCulls = 0L;
        particleDrops = 0L;
        return result;
    }
}

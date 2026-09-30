/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.minecraft.Box
 *  net.minecraft.Vec3d
 *  net.minecraft.MinecraftClient
 *  net.minecraft.Particle
 */
package ru.white.optimization.velotune.perf;

import ru.white.optimization.velotune.VeloTuneManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;

@Environment(value=EnvType.CLIENT)
public final class ParticleBudget {
    private static long frame = Long.MIN_VALUE;
    private static int accepted;

    private ParticleBudget() {
    }

    public static boolean allow(Particle particle) {
        boolean keep;
        double nearSquared;
        VeloTuneConfig config = VeloTuneManager.config();
        if (!config.enabled || !config.particles.enabled) {
            return true;
        }
        PerformanceGovernor governor = VeloTuneManager.governor();
        long currentFrame = governor.frameIndex();
        if (currentFrame != frame) {
            frame = currentFrame;
            accepted = 0;
        }
        if (ExtremeRenderBudget.active() && config.extreme.enabled) {
            if (ExtremeRenderBudget.cullParticle(particle)) {
                Metrics.particleDrop();
                return false;
            }
            if (accepted++ < config.extreme.particleBudgetPerFrame) {
                return true;
            }
            Metrics.particleDrop();
            return false;
        }
        int pressure = governor.pressureLevel();
        int budget = ParticleBudget.scaledBudget(config.particles.spawnBudgetPerFrame, pressure);
        if (accepted++ < budget) {
            return true;
        }
        double distanceSquared = ParticleBudget.distanceSquaredToCamera(particle);
        if (distanceSquared <= (nearSquared = config.particles.nearDistance * config.particles.nearDistance) && accepted <= budget + Math.max(64, budget / 3)) {
            return true;
        }
        double hardSquared = config.particles.hardDistance * config.particles.hardDistance;
        if (pressure >= 2 && distanceSquared > hardSquared) {
            Metrics.particleDrop();
            return false;
        }
        int divisor = switch (pressure) {
            case 0 -> 2;
            case 1 -> 3;
            case 2 -> 5;
            default -> 8;
        };
        boolean bl = keep = Math.floorMod(System.identityHashCode(particle) ^ (int)currentFrame, divisor) == 0;
        if (!keep) {
            Metrics.particleDrop();
        }
        return keep;
    }

    private static int scaledBudget(int base, int pressure) {
        return switch (pressure) {
            case 0 -> base;
            case 1 -> Math.max(64, base * 3 / 4);
            case 2 -> Math.max(64, base * 2 / 5);
            default -> Math.max(64, base / 5);
        };
    }

    private static double distanceSquaredToCamera(Particle particle) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null) {
            return 0.0;
        }
        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
        Box box = particle.getBoundingBox();
        double x = (box.minX + box.maxX) * 0.5;
        double y = (box.minY + box.maxY) * 0.5;
        double z = (box.minZ + box.maxZ) * 0.5;
        double dx = x - camera.x;
        double dy = y - camera.y;
        double dz = z - camera.z;
        return dx * dx + dy * dy + dz * dz;
    }
}

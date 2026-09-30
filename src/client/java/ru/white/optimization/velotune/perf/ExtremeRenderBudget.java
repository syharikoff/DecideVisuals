/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.minecraft.Entity
 *  net.minecraft.BlockPos
 *  net.minecraft.Box
 *  net.minecraft.BlockEntity
 *  net.minecraft.MinecraftClient
 *  net.minecraft.Particle
 */
package ru.white.optimization.velotune.perf;

import ru.white.optimization.velotune.VeloTuneManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;

@Environment(value=EnvType.CLIENT)
public final class ExtremeRenderBudget {
    private static boolean active;
    private static double playerX;
    private static double playerY;
    private static double playerZ;
    private static int renderedPlayers;
    private static int renderedEntities;

    private ExtremeRenderBudget() {
    }

    public static void beginFrame() {
        VeloTuneConfig config = VeloTuneManager.config();
        MinecraftClient client = MinecraftClient.getInstance();
        active = config.enabled && config.extreme.enabled && client.player != null;
        renderedPlayers = 0;
        renderedEntities = 0;
        if (active) {
            playerX = client.player.getX();
            playerY = client.player.getY();
            playerZ = client.player.getZ();
        }
    }

    public static boolean active() {
        return active;
    }

    public static boolean cullEntity(Entity entity, double distanceSquared, boolean player) {
        double distance;
        if (!active) {
            return false;
        }
        VeloTuneConfig.Extreme config = VeloTuneManager.config().extreme;
        if (ExtremeLodPolicy.belowCutoff(entity.getY(), playerY, config.verticalCullDepth)) {
            return true;
        }
        double d = distance = player ? config.playerRenderDistance : config.entityRenderDistance;
        if (ExtremeLodPolicy.beyondDistance(distanceSquared, distance)) {
            return true;
        }
        if (player) {
            return renderedPlayers++ >= config.maxRenderedPlayers;
        }
        return renderedEntities++ >= config.maxRenderedEntities;
    }

    public static boolean cullBlockEntity(BlockEntity blockEntity) {
        if (!active || !VeloTuneManager.config().extreme.cullBlockEntities) {
            return false;
        }
        VeloTuneConfig.Extreme config = VeloTuneManager.config().extreme;
        BlockPos pos = blockEntity.getPos();
        if (ExtremeLodPolicy.belowCutoff(pos.getY(), playerY, config.verticalCullDepth)) {
            return true;
        }
        return ExtremeLodPolicy.beyondDistance(ExtremeRenderBudget.squaredDistance((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5), config.blockEntityRenderDistance);
    }

    public static boolean cullParticle(Particle particle) {
        if (!active) {
            return false;
        }
        VeloTuneConfig.Extreme config = VeloTuneManager.config().extreme;
        Box box = particle.getBoundingBox();
        double x = (box.minX + box.maxX) * 0.5;
        double y = (box.minY + box.maxY) * 0.5;
        double z = (box.minZ + box.maxZ) * 0.5;
        return ExtremeLodPolicy.belowCutoff(y, playerY, config.verticalCullDepth) || ExtremeLodPolicy.beyondDistance(ExtremeRenderBudget.squaredDistance(x, y, z), config.particleRenderDistance);
    }

    public static boolean cullChunkSection(BlockPos origin) {
        return ExtremeRenderBudget.cullChunkSectionY(origin.getY());
    }

    public static boolean cullChunkSectionY(int originY) {
        if (!active || !VeloTuneManager.config().extreme.cullChunkSections) {
            return false;
        }
        VeloTuneConfig.Extreme config = VeloTuneManager.config().extreme;
        return ExtremeLodPolicy.cullChunkSection(originY, playerY, config.verticalCullDepth);
    }

    private static double squaredDistance(double x, double y, double z) {
        double dx = x - playerX;
        double dy = y - playerY;
        double dz = z - playerZ;
        return dx * dx + dy * dy + dz * dz;
    }
}

package ru.white.cosmetics.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import ru.white.cosmetics.CosmeticCategory;
import ru.white.cosmetics.CosmeticManager;
import ru.white.cosmetics.PetMovementTracker;

/**
 * Питомцы рисуются в мире, а не на модели игрока: отдельная сущность со своей анимацией.
 * <p>
 * Позиция берётся у игрока (питомец идёт следом), поворот — в мировых координатах.
 */
public final class PetWorldRenderer {
    private static final long START_NANOS = System.nanoTime();

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) {
                return;
            }
            renderPet(context, mc);
        });
    }

    private static void renderPet(WorldRenderContext context, MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player.isInvisible()) {
            return;
        }
        if (mc.options.getPerspective().isFirstPerson()) {
            return;
        }

        int petId = CosmeticManager.get().getEquipped(CosmeticCategory.PETS);
        if (petId <= 0) {
            return;
        }

        CosmeticSourceModel source = CosmeticSourceModelLoader.get(CosmeticCategory.PETS, petId);
        if (source == null) {
            return;
        }

        Identifier texture = CosmeticModelLoader.getTextureId(CosmeticCategory.PETS, petId);
        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d pos = player.getLerpedPos(tickDelta);
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();

        MatrixStack matrices = context.matrices();
        matrices.push();
        matrices.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-player.getLerpedYaw(tickDelta)));
        matrices.translate(source.getX(), player.getHeight() - source.getY(), source.getZ());
        applyPetOffset(CosmeticModelLoader.getRawId(CosmeticCategory.PETS, petId), matrices);

        float time = (float) ((System.nanoTime() - START_NANOS) / 1.0E9);
        CosmeticWorldGeoRenderer.renderPet(CosmeticCategory.PETS, petId, texture, matrices, context.consumers(),
                WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(pos)),
                player.isGliding(), player.isSwimming(), player.isSneaking(),
                PetMovementTracker.isMoving(player), time);
        matrices.pop();
    }

    /** Ручная подгонка питомцев, у которых пивот в модели не совпадает с ногами игрока. */
    private static void applyPetOffset(int rawId, MatrixStack matrices) {
        switch (rawId) {
            case 45:
            case 46:
            case 90:
                matrices.translate(0.0, -3.0, 0.0);
                break;
            case 132:
                matrices.translate(0.0, -2.0, 0.0);
                break;
            case 133:
                matrices.translate(-1.0, -0.5, 0.0);
                break;
            case 134:
            case 135:
                matrices.translate(-1.0, 0.0, 0.0);
                break;
            default:
                break;
        }
    }

    private PetWorldRenderer() {
    }
}

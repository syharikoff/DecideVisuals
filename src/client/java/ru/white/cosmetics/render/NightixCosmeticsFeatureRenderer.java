package ru.white.cosmetics.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import ru.white.cosmetics.CosmeticCategory;
import ru.white.cosmetics.CosmeticManager;

/**
 * Косметика на модели игрока.
 * <p>
 * Каждая категория крепится к своей части модели ({@code body} / {@code head}), поэтому
 * косметика наследует позу игрока: приседание, размах рук, полёт на элитре.
 * <p>
 * Рендер идёт только для локального игрока: выбор косметики хранится локально и
 * другим игрокам не рассылается.
 */
public final class NightixCosmeticsFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final long START_NANOS = System.nanoTime();

    public NightixCosmeticsFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (state.invisible || state.spectator) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return;
        }

        ClientPlayerEntity player = resolveLocalPlayer(mc, state);
        if (player == null || player.isInvisible()) {
            return;
        }

        CosmeticManager cosmetics = CosmeticManager.get();
        int clothesId = cosmetics.getEquipped(CosmeticCategory.CLOTHES);
        int capeId = cosmetics.getEquipped(CosmeticCategory.CAPES);
        int wingsId = cosmetics.getEquipped(CosmeticCategory.WINGS);
        int hatId = cosmetics.getEquipped(CosmeticCategory.HATS);
        if (clothesId <= 0 && capeId <= 0 && wingsId <= 0 && hatId <= 0) {
            return;
        }

        float time = (float) ((System.nanoTime() - START_NANOS) / 1.0E9);
        boolean moving = Math.abs(limbDistance) > 0.01F;
        boolean gliding = player.isGliding();
        boolean swimming = state.touchingWater;
        boolean sneaking = state.sneaking;
        PlayerEntityModel model = this.getContextModel();

        // 1. Одежда (рюкзаки, катаны)
        if (clothesId > 0) {
            matrices.push();
            model.body.applyTransform(matrices);
            matrices.translate(0.0, 0.25, 0.0);
            CosmeticCommandGeoRenderer.render(CosmeticCategory.CLOTHES, clothesId,
                    CosmeticModelLoader.getTextureId(CosmeticCategory.CLOTHES, clothesId),
                    matrices, queue, light, gliding, swimming, sneaking, moving, time);
            matrices.pop();
        }

        // 2. Плащ
        if (capeId > 0) {
            CosmeticCapeData cape = CosmeticModelLoader.getCape(capeId);
            if (cape != null) {
                matrices.push();
                model.body.applyTransform(matrices);
                CosmeticCommandCapeRenderer.render(cape, matrices, queue, light, time, state);
                matrices.pop();
            }
        }

        // 3. Крылья
        if (wingsId > 0) {
            matrices.push();
            model.body.applyTransform(matrices);
            matrices.translate(0.0, 0.27, 0.0);
            CosmeticCommandGeoRenderer.render(CosmeticCategory.WINGS, wingsId,
                    CosmeticModelLoader.getTextureId(CosmeticCategory.WINGS, wingsId),
                    matrices, queue, light, gliding, swimming, sneaking, moving, time);
            matrices.pop();
        }

        // 4. Шапки, маски, рога, нимбы
        if (hatId > 0) {
            int rawId = CosmeticModelLoader.getRawId(CosmeticCategory.HATS, hatId);
            Identifier texture = CosmeticModelLoader.getTextureId(CosmeticCategory.HATS, hatId);

            matrices.push();
            model.head.applyTransform(matrices);
            matrices.scale(1.08F, 1.08F, 1.08F);
            applyHatOffset(rawId, matrices);

            CosmeticCommandGeoRenderer.render(CosmeticCategory.HATS, hatId, texture,
                    matrices, queue, light, gliding, swimming, sneaking, moving, time);
            matrices.pop();
        }
    }

    /**
     * Render state хранит только id сущности, поэтому игрока достаём из мира.
     * Возвращает null, если это не наш локальный игрок.
     */
    private static ClientPlayerEntity resolveLocalPlayer(MinecraftClient mc, PlayerEntityRenderState state) {
        if (mc.world.getEntityById(state.id) == mc.player) {
            return mc.player;
        }
        return null;
    }

    /** Подгонка шапок под конкретные модели — у каждой свой пивот. */
    private static void applyHatOffset(int rawId, MatrixStack matrices) {
        switch (rawId) {
            case 48:
            case 49:
            case 50:
            case 53:
            case 57:
                matrices.translate(0.0, -0.365, 0.0);
                break;
            case 80:
                matrices.translate(0.0, 0.06, 0.0);
                break;
            case 85:
            case 98:
                break;
            case 88:
            case 97:
            case 125:
                matrices.translate(0.0, 0.0625, 0.0);
                break;
            case 92:
                matrices.translate(0.0, 0.0, -0.00625);
                break;
            case 96:
                matrices.translate(0.0, 0.0, -0.06875);
                break;
            default:
                matrices.translate(0.0, -0.15, 0.0);
        }
    }
}

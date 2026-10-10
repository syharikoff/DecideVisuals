package ru.decide.cosmetics;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import ru.decide.cosmetics.model.CosmeticModel;
import ru.decide.cosmetics.render.CosmeticCapeData;
import ru.decide.cosmetics.render.CosmeticCapes;
import ru.decide.cosmetics.render.CosmeticCommandCapeRenderer;
import ru.decide.cosmetics.render.CosmeticRenderer;
import ru.decide.cosmetics.sync.PlayerCosmeticsSync;

import java.util.List;

/**
 * Косметика на модели игрока (порт Lexora).
 * <p>
 * Рендерим через свой immediate-буфер: feature renderer в 1.21.11 получает
 * {@link OrderedRenderCommandQueue}, а не {@link VertexConsumerProvider}.
 */
@Environment(EnvType.CLIENT)
public class CosmeticFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public CosmeticFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (state.spectator || state.invisible) {
            debug("пропуск: spectator=" + state.spectator + " invisible=" + state.invisible);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        AbstractClientPlayerEntity player = resolvePlayer(client, state);
        if (player == null || player.isInvisible()) {
            debug("пропуск: player не найден или невидим");
            return;
        }

        List<CosmeticModel> models;
        boolean isLocal = player == client.player;
        if (isLocal) {
            models = CosmeticManager.getInstance().getEquipped3DModels();
        } else {
            // чужой игрок: косметика приходит только с сервера (см. PlayerCosmeticsSync)
            models = player.getName() != null ? PlayerCosmeticsSync.getEquippedModels(player.getName().getString()) : null;
        }

        PlayerEntityModel playerModel = this.getContextModel();
        float tickDelta = client.getRenderTickCounter().getTickProgress(true);

        if (models != null && !models.isEmpty()) {
            VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(1024));
            try {
                int drawn = 0;
                for (CosmeticModel model : models) {
                    if (model == null || model.getTextureId() == null) {
                        continue;
                    }
                    CosmeticRenderer.getInstance()
                            .renderCosmetic(model, player, matrices, immediate, light, playerModel, tickDelta);
                    drawn++;
                }
                immediate.draw();
                debug("отрисовано моделей: " + drawn + "/" + models.size());
            } finally {
                immediate.draw();
            }
        } else {
            debug("список надетого пуст");
        }

        // Плащ рисуем только на своём игроке: выбор хранится локально
        if (isLocal) {
            CosmeticCapeData cape = CosmeticCapes.getCapeData();
            if (cape != null) {
                matrices.push();
                playerModel.body.applyTransform(matrices);
                CosmeticCommandCapeRenderer.render(cape, matrices, queue, light, currentTimeSeconds(), state);
                matrices.pop();
            }
        }
    }

    private static float currentTimeSeconds() {
        return (System.nanoTime() % 1_000_000_000L) / 1.0E9F;
    }

    private static final org.slf4j.Logger DEBUG_LOGGER =
            org.slf4j.LoggerFactory.getLogger("DecideVisuals:CosmeticOnPlayer");
    private static long lastDebugMs;

    /**
     * Раз в 2 секунды пишем, что именно происходит с косметикой на игроке.
     * Без этого «молчание» рендера невозможно отличить от «всё на месте,
     * просто игрок невидим» (креатив/наблюдатель делают игрока невидимым).
     */
    private static void debug(String message) {
        long now = System.currentTimeMillis();
        if (now - lastDebugMs < 2000L) return;
        lastDebugMs = now;
        DEBUG_LOGGER.info("[cosmetic-on-player] {}", message);
    }

    /**
     * Render state хранит только id сущности, поэтому игрока достаём из мира.
     */
    private static AbstractClientPlayerEntity resolvePlayer(MinecraftClient client, PlayerEntityRenderState state) {
        if (client.player != null && state.id == client.player.getId()) {
            return client.player;
        }
        Entity entity = client.world.getEntityById(state.id);
        return entity instanceof AbstractClientPlayerEntity player ? player : null;
    }
}
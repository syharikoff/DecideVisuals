package ru.decide.mixin.velotune;

import ru.decide.module.impl.render.Optimizer;
import ru.decide.optimization.velotune.VeloTuneManager;
import ru.decide.optimization.velotune.perf.ExtremeRenderBudget;
import ru.decide.optimization.velotune.perf.Metrics;
import ru.decide.optimization.velotune.perf.PlayerLodPolicy;
import ru.decide.optimization.velotune.perf.VeloTuneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.render.entity.EntityRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(value=EnvType.CLIENT)
@Mixin(value={EntityRenderManager.class})
abstract class EntityRenderDispatcherMixin {
    @Inject(method={"shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"}, at={@At(value="RETURN")}, cancellable=true)
    private <E extends Entity> void velotune$cullEmergencyPlayer(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        EntityRenderManager dispatcher = (EntityRenderManager)(Object)this;
        double distanceSquared = dispatcher.getSquaredDistanceToCamera(entity);

        // Optimizer: отсечение по дистанции уровня
        float cullDist = Optimizer.entityCullDist();
        if (cullDist > 0.0F && distanceSquared > (double) cullDist * cullDist) {
            cir.setReturnValue(false);
            return;
        }

        if (!VeloTuneManager.enabled()) {
            return;
        }
        boolean player;
        if (ExtremeRenderBudget.cullEntity(entity, distanceSquared, player = entity instanceof PlayerEntity)) {
            if (player) {
                Metrics.playerCull();
            } else {
                Metrics.entityCull();
            }
            cir.setReturnValue(false);
            return;
        }
        if (!player || entity == dispatcher.targetedEntity) {
            return;
        }
        VeloTuneConfig config = VeloTuneManager.config();
        if (PlayerLodPolicy.cullPlayer(distanceSquared, VeloTuneManager.governor().pressureLevel(), config.players)) {
            Metrics.playerCull();
            cir.setReturnValue(false);
        }
    }
}

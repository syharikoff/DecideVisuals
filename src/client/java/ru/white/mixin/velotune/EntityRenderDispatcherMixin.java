package ru.white.mixin.velotune;

import ru.white.optimization.velotune.VeloTuneManager;
import ru.white.optimization.velotune.perf.ExtremeRenderBudget;
import ru.white.optimization.velotune.perf.Metrics;
import ru.white.optimization.velotune.perf.PlayerLodPolicy;
import ru.white.optimization.velotune.perf.VeloTuneConfig;
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
        boolean player;
        if (!cir.getReturnValueZ() || !VeloTuneManager.enabled()) {
            return;
        }
        EntityRenderManager dispatcher = (EntityRenderManager)(Object)this;
        double distanceSquared = dispatcher.getSquaredDistanceToCamera(entity);
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

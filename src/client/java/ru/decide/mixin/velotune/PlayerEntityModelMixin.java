package ru.decide.mixin.velotune;

import ru.decide.optimization.velotune.VeloTuneManager;
import ru.decide.optimization.velotune.perf.CachedPlayerPose;
import ru.decide.optimization.velotune.perf.Metrics;
import ru.decide.optimization.velotune.perf.PlayerLodPolicy;
import ru.decide.optimization.velotune.perf.PlayerPoseThrottle;
import ru.decide.optimization.velotune.perf.VeloTuneConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={PlayerEntityModel.class})
abstract class PlayerEntityModelMixin {
    @Unique
    private final Map<Integer, CachedPlayerPose> velotune$poseCache = new HashMap<Integer, CachedPlayerPose>();

    @Inject(method={"setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$reusePose(PlayerEntityRenderState state, CallbackInfo ci) {
        List parts;
        VeloTuneConfig config = VeloTuneManager.config();
        if (!config.enabled || !config.players.poseCache || state.id == 0 || PlayerEntityModelMixin.velotune$isLocalPlayer(state)) {
            return;
        }
        int pressure = VeloTuneManager.governor().pressureLevel();
        int interval = PlayerLodPolicy.poseInterval(state.squaredDistanceToCamera, pressure, config.players);
        if (config.extreme.enabled) {
            interval = Math.max(interval, config.extreme.playerPoseInterval);
        }
        if (interval <= 1 && !config.extreme.enabled) {
            return;
        }
        if (this.velotune$poseCache.size() > config.players.maxCachedPlayers) {
            this.velotune$poseCache.clear();
        }
        CachedPlayerPose cached = this.velotune$poseCache.get(state.id);
        int signature = PlayerEntityModelMixin.velotune$signature(state);
        long frame = VeloTuneManager.governor().frameIndex();
        long nowNanos = System.nanoTime();
        if (cached != null && PlayerPoseThrottle.canReuse(config.extreme.enabled, cached.signature(), signature, frame - cached.frame(), interval, nowNanos - cached.capturedNanos(), config.extreme.playerPoseUpdatesPerSecond, PlayerEntityModelMixin.velotune$urgent(state)) && cached.apply(parts = ((PlayerEntityModel)(Object)this).getParts())) {
            Metrics.poseHit();
            ci.cancel();
            return;
        }
        Metrics.poseMiss();
    }

    @Inject(method={"setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V"}, at={@At(value="RETURN")})
    private void velotune$capturePose(PlayerEntityRenderState state, CallbackInfo ci) {
        VeloTuneConfig config = VeloTuneManager.config();
        if (!config.enabled || !config.players.poseCache || state.id == 0 || PlayerEntityModelMixin.velotune$isLocalPlayer(state)) {
            return;
        }
        int interval = PlayerLodPolicy.poseInterval(state.squaredDistanceToCamera, VeloTuneManager.governor().pressureLevel(), config.players);
        if (config.extreme.enabled) {
            interval = Math.max(interval, config.extreme.playerPoseInterval);
        }
        if (interval <= 1 && !config.extreme.enabled) {
            return;
        }
        List parts = ((PlayerEntityModel)(Object)this).getParts();
        CachedPlayerPose cached = this.velotune$poseCache.computeIfAbsent(state.id, ignored -> new CachedPlayerPose());
        cached.capture(parts, PlayerEntityModelMixin.velotune$signature(state), VeloTuneManager.governor().frameIndex(), System.nanoTime());
    }

    @Unique
    private static boolean velotune$isLocalPlayer(PlayerEntityRenderState state) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return false;
        return state.id == mc.player.getId();
    }

    @Unique
    private static boolean velotune$urgent(PlayerEntityRenderState state) {
        return state.isInSneakingPose || state.handSwingProgress > 0.0f || state.limbSwingAmplitude > 0.0f || state.isSwimming || state.isUsingItem;
    }

    @Unique
    private static int velotune$signature(PlayerEntityRenderState state) {
        int result = Objects.hashCode(state.pose);
        result = 31 * result + Objects.hashCode(state.leaningPitch);
        result = 31 * result + Objects.hashCode(state.limbSwingAnimationProgress);
        result = 31 * result + (state.hatVisible ? 1 : 0);
        result = 31 * result + (state.jacketVisible ? 1 : 0);
        result = 31 * result + (state.leftSleeveVisible ? 1 : 0);
        result = 31 * result + (state.rightSleeveVisible ? 1 : 0);
        result = 31 * result + (state.leftPantsLegVisible ? 1 : 0);
        result = 31 * result + (state.rightPantsLegVisible ? 1 : 0);
        return result;
    }
}

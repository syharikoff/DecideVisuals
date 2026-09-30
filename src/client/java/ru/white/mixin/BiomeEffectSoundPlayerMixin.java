package ru.white.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import ru.white.utils.player.Spectator;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.BiomeEffectSoundPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Пока активен .spec, окружающий эмбиент (биомный луп, additions и пещерный
 * ambient.cave) считается от цели, а не от своего тела — иначе слушатель стоит
 * рядом с челом, а атмосфера играет та, что вокруг тебя.
 */
@Mixin(BiomeEffectSoundPlayer.class)
public class BiomeEffectSoundPlayerMixin {

    /** Позиция, по которой берутся атрибуты окружения (какой луп/additions играть). */
    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;getEntityPos()Lnet/minecraft/util/math/Vec3d;"
            ),
            require = 0
    )
    private Vec3d spectator$ambientPos(Vec3d original) {
        Entity target = Spectator.getTarget();
        return target != null ? target.getEntityPos() : original;
    }

    // -- размещение mood-звука (ambient.cave) вокруг слушателя --

    @ModifyExpressionValue(
            method = "method_75840",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getX()D"),
            require = 0
    )
    private double spectator$moodX(double original) {
        Entity target = Spectator.getTarget();
        return target != null ? target.getX() : original;
    }

    @ModifyExpressionValue(
            method = "method_75840",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getEyeY()D"),
            require = 0
    )
    private double spectator$moodY(double original) {
        Entity target = Spectator.getTarget();
        return target != null ? target.getEyeY() : original;
    }

    @ModifyExpressionValue(
            method = "method_75840",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D"),
            require = 0
    )
    private double spectator$moodZ(double original) {
        Entity target = Spectator.getTarget();
        return target != null ? target.getZ() : original;
    }
}

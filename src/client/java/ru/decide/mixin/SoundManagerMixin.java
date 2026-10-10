package ru.decide.mixin;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.decide.module.impl.utils.EffectSounds;
import ru.decide.module.impl.utils.SoundController;

/**
 * Гасит звуки игры, пока играет звук эффекта (Effect Sounds).
 * <p>
 * Иначе на одно событие слышно два звука: наш (срабатывает на
 * EntityStatusS2CPacket) плюс ванильный, который проигрывает сама игра -
 * например, {@code item.totem.use} при сносе тотема или звук смерти моба.
 * <p>
 * Свои звуки не глушим: {@link EffectSounds#isSuppressingGameSounds()} помечает
 * момент вызова, иначе миксин проглотил бы и наш собственный звук.
 * <p>
 * Перехватываются оба пути воспроизведения: одноаргументный {@code play}
 * (через него идут звуки мира из ClientWorld.playSoundClient) и
 * двухаргументный - часть звуков игры идёт через него с задержкой, и без него
 * ванильный дубль пробивался бы.
 * <p>
 * <b>Важно:</b> {@code cancel()} здесь нельзя - метод возвращает
 * {@code PlayResult}, и ванильный {@code SoundSystem.tick()} вызывает на нём
 * {@code ordinal()}, что даёт NPE. Поэтому подставляем штатное значение
 * {@link SoundSystem.PlayResult#STARTED_SILENTLY}: звук не стартует, но поток
 * выполнения остаётся корректным.
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
            at = @At("HEAD"), cancellable = true)
    private void decide$suppressGameSoundDuringEffectSound(SoundInstance sound,
                                                          CallbackInfoReturnable<SoundSystem.PlayResult> info) {
        if (EffectSounds.isSuppressingGameSounds()) {
            info.setReturnValue(SoundSystem.PlayResult.STARTED_SILENTLY);
        }
    }

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;I)V",
            at = @At("HEAD"), cancellable = true)
    private void decide$suppressDelayedGameSoundDuringEffectSound(SoundInstance sound, int delay, CallbackInfo info) {
        if (EffectSounds.isSuppressingGameSounds()) {
            info.cancel();
        }
    }

    /**
     * Sound Controller: громкость отдельных звуковых событий.
     * <p>
     * В 1.21.x громкость читается один раз на входе в SoundManager
     * ({@code SoundSystem.play} делает {@code sound.getVolume()} и дальше
     * только умножает её на громкость категории), поэтому менять её позже
     * негде - подменяем инстанс на обёртку с приглушённой громкостью.
     * <p>
     * Сделано через повторный вызов {@code play} вместо
     * {@code @ModifyVariable}: этот инжектор почему-то не применялся
     * (в логе оставались только два {@code @Inject}), а так поведение
     * гарантированно совпадает с ванильным. Рекурсии нет: обёртка
     * повторно не оборачивается, поэтому второй вызов сразу уходит
     * в оригинальный метод.
     */
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
            at = @At("HEAD"), cancellable = true)
    private void decide$scaleSoundVolume(SoundInstance sound,
                                         CallbackInfoReturnable<SoundSystem.PlayResult> info) {
        SoundInstance scaled = SoundController.scale(sound);
        if (scaled != sound) {
            info.setReturnValue(((SoundManager) (Object) this).play(scaled));
        }
    }

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;I)V",
            at = @At("HEAD"), cancellable = true)
    private void decide$scaleSoundVolumeDelayed(SoundInstance sound, int delay, CallbackInfo info) {
        SoundInstance scaled = SoundController.scale(sound);
        if (scaled != sound) {
            ((SoundManager) (Object) this).play(scaled, delay);
            info.cancel();
        }
    }
}

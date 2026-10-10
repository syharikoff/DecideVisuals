package ru.decide.module.impl.utils;

import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

/**
 * Обёртка звука с приглушённой громкостью: подставляется в SoundManager вместо
 * оригинала, поэтому громкость меняется ровно на входе в движок звука
 * (иначе в 1.21.x громкость уже посчитана и пересчитать её негде).
 */
public final class ScaledSoundInstance implements SoundInstance {

    private final SoundInstance origin;
    private final float scale;

    private ScaledSoundInstance(SoundInstance origin, float scale) {
        this.origin = origin;
        this.scale = scale;
    }

    /** Возвращает обёртку только если масштаб реально отличается от 1. */
    public static SoundInstance of(SoundInstance original, float scale) {
        if (original == null || scale == 1.0F) return original;
        return new ScaledSoundInstance(original, scale);
    }

    public SoundInstance origin() {
        return this.origin;
    }

    @Override
    public Identifier getId() {
        return this.origin.getId();
    }

    @Override
    public WeightedSoundSet getSoundSet(SoundManager manager) {
        return this.origin.getSoundSet(manager);
    }

    @Override
    public Sound getSound() {
        return this.origin.getSound();
    }

    @Override
    public SoundCategory getCategory() {
        return this.origin.getCategory();
    }

    @Override
    public boolean isRepeatable() {
        return this.origin.isRepeatable();
    }

    @Override
    public boolean isRelative() {
        return this.origin.isRelative();
    }

    @Override
    public int getRepeatDelay() {
        return this.origin.getRepeatDelay();
    }

    @Override
    public float getVolume() {
        return this.origin.getVolume() * this.scale;
    }

    @Override
    public float getPitch() {
        return this.origin.getPitch();
    }

    @Override
    public double getX() {
        return this.origin.getX();
    }

    @Override
    public double getY() {
        return this.origin.getY();
    }

    @Override
    public double getZ() {
        return this.origin.getZ();
    }

    @Override
    public AttenuationType getAttenuationType() {
        return this.origin.getAttenuationType();
    }

    @Override
    public boolean shouldAlwaysPlay() {
        return this.origin.shouldAlwaysPlay();
    }

    @Override
    public String toString() {
        return "Scaled(" + this.origin + ")";
    }
}
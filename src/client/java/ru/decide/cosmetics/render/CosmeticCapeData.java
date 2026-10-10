package ru.decide.cosmetics.render;

import lombok.Getter;
import net.minecraft.util.Identifier;

/**
 * Описание плаща: текстура, её реальный размер и (если есть) анимация текстуры.
 * <p>
 * У плащей нет геометрии — она строится вручную в {@link CosmeticCommandCapeRenderer},
 * а размер текстуры нужен, чтобы выбрать правильное UV-окно.
 */
@Getter
public final class CosmeticCapeData {
    private final int rawId;
    private final String name;
    private final Identifier texture;
    private final int textureWidth;
    private final int textureHeight;
    private final int frameCount;
    private final int frameWidth;
    private final int frameHeight;
    private final int frameTime;

    public CosmeticCapeData(int rawId, String name, Identifier texture, int textureWidth, int textureHeight,
                            int frameCount, int frameWidth, int frameHeight, int frameTime) {
        this.rawId = rawId;
        this.name = name;
        this.texture = texture;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.frameCount = frameCount;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.frameTime = frameTime;
    }
}

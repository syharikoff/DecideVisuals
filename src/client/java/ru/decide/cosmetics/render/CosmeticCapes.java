package ru.decide.cosmetics.render;

import net.minecraft.util.Identifier;
import ru.decide.cosmetics.CosmeticManager;

/**
 * Мост между косметикой Lexora и нашим рендерером плаща.
 * <p>
 * У Lexora плащи описаны парой (текстура + {@link CosmeticManager.CapeAnimationInfo}),
 * а {@link CosmeticCommandCapeRenderer} ждёт {@link CosmeticCapeData} с размерами кадра.
 */
public final class CosmeticCapes {

    /** Индекс «пустого» плаща в Lexora (их дефолтная плащ-заглушка) — считаем, что плаща нет. */
    public static final int NO_CAPE_INDEX = 999;

    /**
     * Текстура надетого плаща или null, если плащ не выбран/выключен.
     */
    public static Identifier getEquippedTexture() {
        Integer index = CosmeticManager.getInstance().getSelectedByType().get("cape");
        if (index == null || index == NO_CAPE_INDEX) {
            return null;
        }
        return CosmeticManager.getInstance().getCapeTexture(index);
    }

    public static boolean hasCape() {
        return getEquippedTexture() != null;
    }

    /**
     * Собирает данные плаща для рендера: размеры кадра вычисляются из текстуры и
     * информации об анимации (у Lexora «плотные» плащи — набор кадров 22x17 друг под другом).
     */
    public static CosmeticCapeData getCapeData() {
        Identifier texture = getEquippedTexture();
        if (texture == null) {
            return null;
        }

        CosmeticManager.CapeAnimationInfo anim = CosmeticManager.getInstance().getCapeAnimation(texture);
        int frames = anim.frameCount;
        int[] size = CosmeticTextureInfo.size(texture);
        int textureWidth = size[0];
        int textureHeight = size[1];

        int frameWidth = textureWidth;
        int frameHeight = textureHeight;
        if (frames > 1 && textureWidth > 0 && textureHeight > 0) {
            if (anim.isTight) {
                frameWidth = textureWidth / frames;
                frameHeight = textureHeight / frames;
            } else {
                frameWidth = anim.baseWidth > 0 ? (int) anim.baseWidth : textureWidth / frames;
                frameHeight = textureHeight / frames;
            }
        } else {
            frames = 1;
        }

        return new CosmeticCapeData(0, "cape", texture, textureWidth, textureHeight,
                frames, frameWidth, frameHeight, anim.frameTime);
    }

    private CosmeticCapes() {
    }
}
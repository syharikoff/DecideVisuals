package ru.white.cosmetics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.white.utils.animation.satoshi.Animation;
import ru.white.utils.animation.satoshi.EaseInOutQuad;

@Getter
@RequiredArgsConstructor
public enum CosmeticCategory {
    WINGS("Крылья", 13, "W", "wings"),
    CAPES("Плащи", 22, "C", "capes"),
    HATS("Шапки", 13, "H", "hats"),
    CLOTHES("Одежда", 11, "O", "clothes"),
    PETS("Питомцы", 12, "P", "pets"),
    GRAFFITI("Граффити", 20, "G", "graffiti");

    private final String name;
    private final int count;
    private final String icon;
    private final String folder;

    public final Animation hoverAnim = new EaseInOutQuad(250, 1);
    public final Animation selectAnim = new EaseInOutQuad(250, 1);
}

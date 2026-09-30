package ru.white.module.api;


import ru.white.utils.animation.Animation;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Category {
    VISUALS("Визуалы","Q"),
    HUD("Худ","H"),
    UTILITIES("Утилиты","L"),
    COSMETICS("Косметика","T"),
    CONFIGS("Конфиги","R"),
    MARKERS("Метки","S");
    private final String name;
    private final String icon;



    public ru.white.utils.animation.satoshi.Animation alphaS = new EaseInOutQuad(300,1);
    public ru.white.utils.animation.satoshi.Animation alphaS2 = new EaseInOutQuad(300,1);


    public Animation animation = new Animation();

}

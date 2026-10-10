package ru.decide.cosmetics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.decide.utils.animation.satoshi.Animation;
import ru.decide.utils.animation.satoshi.EaseInOutQuad;

/**
 * Категории косметики для GUI (порт Lexora).
 * <p>
 * У Lexora категория - это просто строка в json модели ({@code "type"}), поэтому
 * здесь она тоже строка: {@link CosmeticManager#getFilteredEntries(String)}.
 * Количество предметов берётся из отсканированных ассетов, а не хардкодится.
 */
@Getter
@RequiredArgsConstructor
public enum CosmeticType {
    WINGS("Крылья", "wings"),
    CAPES("Плащи", "cape"),
    HATS("Шапки", "hat"),
    PETS("Питомцы", "pet"),
    BODYWEAR("Одежда", "bodywear");

    private final String name;
    private final String type;

    public final Animation hoverAnim = new EaseInOutQuad(250, 1);
    public final Animation selectAnim = new EaseInOutQuad(250, 1);

    /** Сколько предметов реально найдено в ассетах для этой категории. */
    public int count() {
        return CosmeticManager.getInstance().getFilteredEntries(type).size();
    }
}
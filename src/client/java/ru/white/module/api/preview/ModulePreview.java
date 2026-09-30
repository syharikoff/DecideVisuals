package ru.white.module.api.preview;

import ru.white.module.api.Module;
import ru.white.screen.PreviewEditor;

/**
 * Модуль умеет показывать себя в редакторе предпоказа: раз в интервал он проигрывает
 * один цикл эффекта перед игроком, чтобы настройки можно было крутить не выходя в бой.
 */
public interface ModulePreview {

    /** Блок «Предпоказ» в самом низу настроек модуля. */
    PreviewSettings previewSettings();

    /** Открыт ли прямо сейчас редактор предпоказа для этого модуля. */
    default boolean isPreviewActive() {
        return this instanceof Module module && PreviewEditor.getInstance().isEditing(module);
    }

    /** Один цикл демонстрации: спавн луча, волны, призрака и т.п. */
    void previewSpawn(PreviewContext ctx);

    /** Нужен ли модулю болванчик перед игроком (ESP, призраки, частицы по цели). */
    default boolean previewNeedsDummy() {
        return false;
    }

    /** Модуль сам двигает болванчика — редактор не будет ставить его в точку якоря. */
    default boolean previewControlsDummy() {
        return false;
    }

    default void previewStart(PreviewContext ctx) {
    }

    /** Вызывается каждый кадр, пока открыт редактор. */
    default void previewTick(PreviewContext ctx) {
    }

    /** Редактор закрывается — вернуть состояние модуля к обычному. */
    default void previewStop() {
    }
}

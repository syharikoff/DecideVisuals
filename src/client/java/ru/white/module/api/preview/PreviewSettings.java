package ru.white.module.api.preview;

import net.minecraft.client.MinecraftClient;
import ru.white.module.api.Module;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.module.api.settings.impl.DelimiterSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.screen.HandsEditor;
import ru.white.screen.Menu;
import ru.white.screen.PreviewEditor;
import ru.white.screen.editor.OverlayEditors;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.other.GuiSounds;

/**
 * Блок настроек предпоказа. Объявляется последним полем модуля, поэтому его строки
 * оказываются в самом низу панели редактора, а в обычном ClickGUI их не видно вовсе.
 * Ненужные модулю слайдеры просто не создаются: у постоянных эффектов нет интервала,
 * а у тех, что живут вокруг игрока, — точки спавна.
 */
public final class PreviewSettings {

    public final DelimiterSetting title;
    public final SliderSetting distance;
    public final SliderSetting height;
    public final SliderSetting interval;

    private PreviewSettings(Module parent, Float defaultDistance, Float defaultHeight, Float defaultInterval) {
        boolean anySlider = defaultDistance != null || defaultHeight != null || defaultInterval != null;

        // имя не должно совпадать с кнопкой: настройки в конфиге лежат по имени
        title = !anySlider ? null
                : new DelimiterSetting(parent, "Настройки предпоказа")
                .setVisible(() -> isEditing(parent));

        distance = defaultDistance == null ? null
                : new SliderSetting(parent, "Дистанция спавна", defaultDistance, 1.5F, 14F, 0.5F)
                .setVisible(() -> isEditing(parent));

        height = defaultHeight == null ? null
                : new SliderSetting(parent, "Высота спавна", defaultHeight, -2F, 4F, 0.1F)
                .setVisible(() -> isEditing(parent));

        interval = defaultInterval == null ? null
                : new SliderSetting(parent, "Интервал показа", defaultInterval, 0.5F, 10F, 0.5F)
                .setVisible(() -> isEditing(parent));
    }

    /** Полный набор: где спавнить и как часто повторять. */
    public static PreviewSettings of(Module parent, float distance, float height, float interval) {
        return new PreviewSettings(parent, distance, height, interval);
    }

    /** Эффект виден постоянно — повторять нечего, интервал не нужен. */
    public static PreviewSettings withoutInterval(Module parent, float distance, float height) {
        return new PreviewSettings(parent, distance, height, null);
    }

    /** Эффект живёт вокруг самого игрока — точку спавна выбирать негде. */
    public static PreviewSettings intervalOnly(Module parent, float interval) {
        return new PreviewSettings(parent, null, null, interval);
    }

    /** Настраивать нечего: эффект идёт сам и постоянно, блок в панели не появляется. */
    public static PreviewSettings none(Module parent) {
        return new PreviewSettings(parent, null, null, null);
    }

    public boolean repeats() {
        return interval != null;
    }

    public long intervalMs() {
        return interval == null ? 0L : (long) (interval.getValue() * 1000F);
    }

    public float distance(float fallback) {
        return distance == null ? fallback : distance.getValue();
    }

    public float height(float fallback) {
        return height == null ? fallback : height.getValue();
    }

    private static boolean isEditing(Module parent) {
        return PreviewEditor.getInstance().isEditing(parent);
    }

    /**
     * Кнопка открытия редактора. Объявляется первым полем модуля и прячется, пока редактор
     * уже открыт, — иначе внутри него была бы кнопка «открыть самого себя».
     */
    public static ButtonSetting button(Module parent) {
        return new ButtonSetting(parent, "Предпоказ", () -> {
            if (IMinecraft.mc.currentScreen instanceof Menu menu) {
                menu.openPreviewEditor(parent);
            } else {
                openEditorFromAnyScreen(parent);
            }
        }).setVisible(() -> !isEditing(parent));
    }

    private static void openEditorFromAnyScreen(Module parent) {
        MinecraftClient mc = MinecraftClient.getInstance();
        GuiSounds.editor();
        Menu.returnToGlassAfterEditor = true;
        mc.setScreen(null);
        mc.send(() -> {
            Menu menu = new Menu();
            mc.setScreen(menu);
            mc.send(() -> menu.openPreviewEditor(parent));
        });
    }
}

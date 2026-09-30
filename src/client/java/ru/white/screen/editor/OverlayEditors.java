package ru.white.screen.editor;

import ru.white.screen.CrosshairEditor;
import ru.white.screen.HandsEditor;
import ru.white.screen.PreviewEditor;

/** Реестр оверлей-редакторов: одновременно активным может быть только один. */
public final class OverlayEditors {

    /** Проверяется каждый кадр и на каждый клик, поэтому список собран один раз. */
    private static final OverlayEditor[] ALL = {
            HandsEditor.getInstance(),
            PreviewEditor.getInstance(),
            CrosshairEditor.getInstance()
    };

    private OverlayEditors() {
    }

    public static OverlayEditor[] all() {
        return ALL;
    }

    public static OverlayEditor active() {
        for (OverlayEditor editor : ALL) {
            if (editor.isActive()) return editor;
        }
        return null;
    }

    public static boolean anyActive() {
        return active() != null;
    }

    /** Меню закрывается — гасим всё, что могло остаться открытым. */
    public static void closeAll() {
        for (OverlayEditor editor : all()) {
            if (editor.isActive()) editor.closeFromMenuRemoval();
        }
    }
}

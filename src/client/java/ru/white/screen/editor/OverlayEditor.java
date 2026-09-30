package ru.white.screen.editor;

/**
 * Полноэкранный редактор, живущий внутри Menu: пока он активен, меню не рисует свою
 * раскладку и отдаёт ему весь ввод.
 */
public interface OverlayEditor {

    boolean isActive();

    void render(float width, float height, float mouseX, float mouseY, float parentAlpha);

    boolean mouseClicked(float mouseX, float mouseY, int button);

    boolean mouseReleased(int button);

    boolean mouseScrolled(float mouseX, float mouseY, double verticalAmount);

    /** Esc или кнопка выхода: сохранить конфиг и закрыться, оставив меню открытым. */
    void saveAndExit();

    /** Меню закрывается целиком — редактор обязан вернуть всё, что менял. */
    void closeFromMenuRemoval();

    /** Сырой ввод из MouseMixin: Screen'у события мыши во время перетаскивания не приходят. */
    default void rawMouseButton(float mouseX, float mouseY, int button, boolean pressed) {
    }

    default void rawMouseMoved(float mouseX, float mouseY) {
    }

    default boolean mouseDragged(float deltaX, float deltaY) {
        return isActive();
    }
}

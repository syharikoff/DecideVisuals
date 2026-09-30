package ru.white.ui.compat;

import ru.white.ui.compat.impl.DecideVisualsAccentAPI;
import ru.white.ui.compat.impl.DecideVisualsColorAPI;
import ru.white.ui.compat.impl.DecideVisualsFontAPI;
import ru.white.ui.compat.impl.DecideVisualsPositionAPI;
import ru.white.ui.compat.impl.DecideVisualsRenderAPI;
import ru.white.ui.compat.impl.DecideVisualsRenderHelperAPI;
import ru.white.ui.compat.impl.DecideVisualsSoundAPI;

/**
 * Единая точка доступа ко всем абстрактным API.
 * Проект регистрирует свои реализации при старте.
 *
 * <p>Пример использования в GUI:</p>
 * <pre>{@code
 * RenderAPI r = GuiContext.get().render();
 * FontAPI  f = GuiContext.get().font();
 * r.rect(x, y, w, h, color);
 * f.draw("montserrat-medium", text, x, y, size, color);
 * }</pre>
 */
public final class GuiContext {

    private static GuiContext INSTANCE;

    private RenderAPI render;
    private FontAPI font;
    private ColorAPI color;
    private AccentAPI accent;
    private PositionAPI position;
    private SoundAPI sound;
    private RenderHelperAPI renderHelper;

    public static GuiContext get() {
        if (INSTANCE == null) {
            INSTANCE = new GuiContext();
        }
        return INSTANCE;
    }

    /** Регистрирует DecideVisuals-реализации (вызывается при инициализации клиента). */
    public static void registerDecideVisuals() {
        get().init(
                new DecideVisualsRenderAPI(),
                new DecideVisualsFontAPI(),
                new DecideVisualsColorAPI(),
                new DecideVisualsAccentAPI(),
                new DecideVisualsPositionAPI(),
                new DecideVisualsSoundAPI(),
                new DecideVisualsRenderHelperAPI()
        );
    }

    public void init(RenderAPI render, FontAPI font, ColorAPI color,
                     AccentAPI accent, PositionAPI position, SoundAPI sound,
                     RenderHelperAPI renderHelper) {
        this.render = render;
        this.font = font;
        this.color = color;
        this.accent = accent;
        this.position = position;
        this.sound = sound;
        this.renderHelper = renderHelper;
    }

    public RenderAPI        render()       { return render; }
    public FontAPI          font()         { return font; }
    public ColorAPI         color()        { return color; }
    public AccentAPI        accent()       { return accent; }
    public PositionAPI      position()     { return position; }
    public SoundAPI         sound()        { return sound; }
    public RenderHelperAPI  renderHelper() { return renderHelper; }
}

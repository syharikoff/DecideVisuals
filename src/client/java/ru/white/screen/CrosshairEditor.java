package ru.white.screen;

import org.lwjgl.glfw.GLFW;
import ru.white.Client;
import ru.white.lang.Lang;
import ru.white.module.api.settings.impl.PixelGridSetting;
import ru.white.module.impl.render.CrossHair;
import ru.white.screen.editor.EditorButton;
import ru.white.screen.editor.EditorWidgets;
import ru.white.screen.editor.OverlayEditor;
import ru.white.screen.editor.Rect;
import ru.white.screen.editor.SettingsPanel;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Fonts;

import static ru.white.screen.editor.EditorTheme.PANEL_W;
import static ru.white.screen.editor.EditorTheme.ROW_H;

/**
 * Редактор своего прицела: пиксельный холст с зеркалами по осям. Живёт только пока в панели
 * выбран тип кастома «Свой рисунок» — при переключении закрывается сам.
 */
public final class CrosshairEditor implements OverlayEditor, IMinecraft {

    private static final CrosshairEditor INSTANCE = new CrosshairEditor();

    private final EditorWidgets widgets = new EditorWidgets();
    private final SettingsPanel panel = new SettingsPanel();

    private final EditorButton saveButton = new EditorButton("Выйти и сохранить", true);
    private final EditorButton clearButton = new EditorButton("Очистить", false);
    private final EditorButton resetButton = new EditorButton("Сбросить", false);

    private final Animation visibility = new Animation();

    private boolean active;

    private boolean mirrorX = true;
    private boolean mirrorY = true;

    /** 1 — рисуем, 0 — стираем, -1 — курсор не зажат. */
    private int paintMode = -1;

    private float canvasX;
    private float canvasY;
    private float canvasSize;
    private float cell;

    private Rect mirrorXBounds = Rect.EMPTY;
    private Rect mirrorYBounds = Rect.EMPTY;

    private CrosshairEditor() {
    }

    public static CrosshairEditor getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public void open() {
        if (active) return;

        active = true;
        paintMode = -1;
        panel.reset();
        panel.unplace();
        visibility.set(0);
        visibility.run(1, 0.24F, Easings.EXPO_OUT);
    }

    @Override
    public void saveAndExit() {
        if (!active) return;
        if (Client.get() != null && Client.get().configManager() != null) {
            Client.get().configManager().autoSave();
        }
        deactivate();
    }

    @Override
    public void closeFromMenuRemoval() {
        saveAndExit();
    }

    private void deactivate() {
        active = false;
        paintMode = -1;
        panel.releaseDrag();
        panel.closePickers();
    }

    // ───────────────────────────── рендер ─────────────────────────────

    @Override
    public void render(float width, float height, float mouseX, float mouseY, float parentAlpha) {
        if (!active) return;

        CrossHair module = CrossHair.getInstance();
        // тип кастома переключили прямо в панели — рисовать больше нечего
        if (module == null || !module.isDrawing()) {
            saveAndExit();
            return;
        }

        widgets.setMouse(mouseX, mouseY);
        layout(width, height);

        saveButton.update(mouseX, mouseY);
        clearButton.update(mouseX, mouseY);
        resetButton.update(mouseX, mouseY);

        panel.layout(module, "Cross Hair", width - PANEL_W - 16F, 14F, width, height, widgets);

        visibility.update();
        float alpha = visibility.get() * parentAlpha;

        RenderUtil.Render2D.rect(0, 0, width, height, ColorUtil.getColor(0, 0.55F * alpha));

        Client.get().render2D().flushAll();

        Fonts.sf_regular.drawCentered(Lang.tr("Редактор прицела"), width / 2F, 16F, 8F,
                ColorUtil.getColor(240, alpha));
        Fonts.sf_regular.drawCentered(
                Lang.tr("ЛКМ — рисовать") + "   •   " + Lang.tr("ПКМ — стирать") + "   •   " + Lang.tr("Esc — выйти"),
                width / 2F, 29F, 6F, ColorUtil.getColor(255, alpha * 0.45F));

        drawCanvas(module.grid, alpha);

        widgets.drawToggleRow(mirrorXBounds, Lang.tr("Зеркало по X"), mirrorX, "crosshair:mirrorx", alpha);
        widgets.drawToggleRow(mirrorYBounds, Lang.tr("Зеркало по Y"), mirrorY, "crosshair:mirrory", alpha);

        widgets.drawButton(clearButton, alpha);
        widgets.drawButton(resetButton, alpha);
        widgets.drawButton(saveButton, alpha);

        panel.draw(widgets, alpha);
    }

    private void layout(float width, float height) {
        float areaW = Math.max(200F, width - PANEL_W - 40F);
        canvasSize = MathUtil.clamp(Math.min(areaW * 0.75F, height - 150F), 110F, 340F);
        canvasX = areaW / 2F - canvasSize / 2F;
        canvasY = 50F;
        cell = canvasSize / CrossHair.GRID_SIZE;

        float toggleY = canvasY + canvasSize + 8F;
        float half = canvasSize / 2F - 3F;
        mirrorXBounds = new Rect(canvasX, toggleY, half, ROW_H);
        mirrorYBounds = new Rect(canvasX + canvasSize / 2F + 3F, toggleY, half, ROW_H);

        float buttonH = 18F;
        float saveW = 120F;
        float sideW = 88F;
        float bottomY = height - 30F;

        saveButton.set(canvasX + canvasSize / 2F - saveW / 2F, bottomY, saveW, buttonH);
        clearButton.set(canvasX + canvasSize / 2F - sideW - 4F, bottomY - 24F, sideW, buttonH);
        resetButton.set(canvasX + canvasSize / 2F + 4F, bottomY - 24F, sideW, buttonH);
    }

    private void drawCanvas(PixelGridSetting grid, float alpha) {
        RenderUtil.Render2D.glow(canvasX, canvasY, canvasSize, canvasSize,
                ColorUtil.getColor(0, 0.2F * alpha), 4, 12, 1);
        RenderUtil.Render2D.rect(canvasX, canvasY, canvasSize, canvasSize,
                ColorUtil.getColor(18, 0.85F * alpha), 4);

        int c = grid.center();
        int fill = ColorUtil.replAlpha(ColorUtil.client(), alpha);
        int gridLine = ColorUtil.getColor(255, 0.07F * alpha);
        int axisLine = ColorUtil.getColor(255, 0.18F * alpha);

        for (int i = 1; i < CrossHair.GRID_SIZE; i++) {
            float offset = i * cell;
            boolean axis = i == c || i == c + 1;
            int line = axis ? axisLine : gridLine;
            RenderUtil.Render2D.rect(canvasX + offset, canvasY, 0.5F, canvasSize, line);
            RenderUtil.Render2D.rect(canvasX, canvasY + offset, canvasSize, 0.5F, line);
        }

        for (int gy = 0; gy < CrossHair.GRID_SIZE; gy++) {
            for (int gx = 0; gx < CrossHair.GRID_SIZE; gx++) {
                if (!grid.get(gx, gy)) continue;
                int pixelFill = CrossHair.getInstance() != null ? CrossHair.getInstance().resolvePixelColor(gx, gy, alpha) : ColorUtil.replAlpha(ColorUtil.client(), alpha);
                RenderUtil.Render2D.rect(canvasX + gx * cell + 0.5F, canvasY + gy * cell + 0.5F,
                        cell - 1F, cell - 1F, pixelFill, 1F);
            }
        }

        // подсветка клетки под курсором вместе с её зеркальными близнецами
        int hx = cellAt(widgets.mouseX(), canvasX);
        int hy = cellAt(widgets.mouseY(), canvasY);
        if (grid.inBounds(hx, hy)) {
            int hover = CrossHair.getInstance() != null ? CrossHair.getInstance().resolvePixelColor(hx, hy, alpha * 0.4F) : ColorUtil.replAlpha(ColorUtil.client(), alpha * 0.35F);
            for (int[] point : mirrored(hx, hy)) {
                RenderUtil.Render2D.rect(canvasX + point[0] * cell + 0.5F, canvasY + point[1] * cell + 0.5F,
                        cell - 1F, cell - 1F, hover, 1F);
            }
        }

        RenderUtil.Render2D.outline(canvasX, canvasY, canvasSize, canvasSize, 0.5F,
                ColorUtil.replAlpha(ColorUtil.client(), alpha * 0.35F), 4);
    }

    // ───────────────────────────── ввод ─────────────────────────────

    private int cellAt(float mouse, float origin) {
        if (cell <= 0F) return -1;
        return (int) Math.floor((mouse - origin) / cell);
    }

    private boolean overCanvas(float mouseX, float mouseY) {
        return mouseX >= canvasX && mouseY >= canvasY
                && mouseX < canvasX + canvasSize && mouseY < canvasY + canvasSize;
    }

    private int[][] mirrored(int gx, int gy) {
        int last = CrossHair.GRID_SIZE - 1;
        if (mirrorX && mirrorY) {
            return new int[][]{{gx, gy}, {last - gx, gy}, {gx, last - gy}, {last - gx, last - gy}};
        }
        if (mirrorX) return new int[][]{{gx, gy}, {last - gx, gy}};
        if (mirrorY) return new int[][]{{gx, gy}, {gx, last - gy}};
        return new int[][]{{gx, gy}};
    }

    private void paint(float mouseX, float mouseY) {
        if (paintMode < 0 || !overCanvas(mouseX, mouseY)) return;

        CrossHair module = CrossHair.getInstance();
        if (module == null) return;

        int gx = cellAt(mouseX, canvasX);
        int gy = cellAt(mouseY, canvasY);
        if (!module.grid.inBounds(gx, gy)) return;

        for (int[] point : mirrored(gx, gy)) {
            module.grid.put(point[0], point[1], paintMode == 1);
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!active) return false;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (panel.handleClick(mouseX, mouseY, button)) return true;
        }

        if (overCanvas(mouseX, mouseY)) {
            paintMode = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 0 : 1;
            paint(mouseX, mouseY);
            return true;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;

        CrossHair module = CrossHair.getInstance();

        if (mirrorXBounds.contains(mouseX, mouseY)) {
            mirrorX = !mirrorX;
        } else if (mirrorYBounds.contains(mouseX, mouseY)) {
            mirrorY = !mirrorY;
        } else if (saveButton.contains(mouseX, mouseY)) {
            saveButton.press();
            saveAndExit();
        } else if (module != null && clearButton.contains(mouseX, mouseY)) {
            clearButton.press();
            module.grid.clear();
        } else if (module != null && resetButton.contains(mouseX, mouseY)) {
            resetButton.press();
            module.grid.reset();
        }

        return true;
    }

    @Override
    public boolean mouseReleased(int button) {
        if (!active) return false;
        paintMode = -1;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) panel.releaseDrag();
        return true;
    }

    @Override
    public boolean mouseScrolled(float mouseX, float mouseY, double verticalAmount) {
        if (!active) return false;
        panel.handleScroll(mouseX, mouseY, verticalAmount);
        return true;
    }

    @Override
    public void rawMouseButton(float mouseX, float mouseY, int button, boolean pressed) {
        if (!active) return;

        if (!pressed) {
            paintMode = -1;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) panel.releaseDrag();
            return;
        }

        // кнопки и панель обрабатывает mouseClicked — сюда приходит только рисование
        if (overCanvas(mouseX, mouseY)) {
            paintMode = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 0 : 1;
            paint(mouseX, mouseY);
        }
    }

    @Override
    public void rawMouseMoved(float mouseX, float mouseY) {
        if (!active) return;
        if (panel.rawMouseMoved(mouseX, mouseY)) return;
        paint(mouseX, mouseY);
    }
}

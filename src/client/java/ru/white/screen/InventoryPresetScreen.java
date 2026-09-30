package ru.white.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.white.Client;
import ru.white.inventorypreset.InventoryPreset;
import ru.white.inventorypreset.InventoryPresetManager;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.animation.satoshi.Direction;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.render.Render2D;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.Scissor;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Менеджер пресетов инвентаря, оформленный языком ClickGUI: блюр-панель, строки с ховером, чипсы. */
public final class InventoryPresetScreen extends Screen implements ru.white.utils.annotation.IMinecraft {

    private static final float PANEL_W = 400F;
    private static final float PANEL_H = 250F;
    private static final float LEFT_W = 160F;

    private static final float ROW_H = 18F;
    private static final float ROW_GAP = 3F;
    private static final float CELL = 19F;

    private static final float TEXT = 6.5F;
    private static final float SMALL = 6F;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, ru.white.utils.animation.satoshi.Animation> anims = new HashMap<>();

    private final Animation openAnim = new Animation();

    private String name = "";
    private boolean focused;
    private float scroll;
    private float scrollTarget;
    private float maxScroll;

    private float scaleFix = 1F;
    private float panelX;
    private float panelY;
    private float mouseX;
    private float mouseY;

    private InventoryPreset preview;

    public InventoryPresetScreen(Screen parent) {
        super(Text.literal("Менеджер пресетов"));
        this.parent = parent;
    }

    private InventoryPresetManager manager() {
        return Client.get().inventoryPresetManager();
    }

    @Override
    protected void init() {
        openAnim.set(0);
        openAnim.run(1, 0.25F, Easings.SINE_OUT);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int rawMouseX, int rawMouseY, float delta) {
        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        scaleFix = targetScale / currentScale;

        float screenW = mc.getWindow().getScaledWidth() / scaleFix;
        float screenH = mc.getWindow().getScaledHeight() / scaleFix;

        mouseX = rawMouseX / scaleFix;
        mouseY = rawMouseY / scaleFix;

        openAnim.update();
        float alpha = openAnim.get();

        scroll += (scrollTarget - scroll) * 0.2F;

        panelX = screenW / 2F - PANEL_W / 2F;
        panelY = screenH / 2F - PANEL_H / 2F + (1F - alpha) * 30F;

        Render2D.beginOverlay();

        RenderUtil.Render2D.rect(0, 0, screenW, screenH, ColorUtil.getColor(0, 0.6F * alpha));

        RenderUtil.Render2D.glow(panelX, panelY, PANEL_W, PANEL_H - 0.5F,
                ColorUtil.getColor(0, 0.15F * alpha), 8, 15, 1);

        RenderUtil.Blur.blur(panelX, panelY, PANEL_W, PANEL_H, alpha, 8,
                ColorUtil.multAlpha(ColorUtil.multDark(ColorUtil.background(), 0.6F), alpha));

        RenderUtil.Render2D.outline(panelX, panelY, PANEL_W, PANEL_H, 0.5F,
                ColorUtil.replAlpha(ColorUtil.client(), alpha * 0.35F), 8);

        Font font = Fonts.sf_regular;

        // разделитель колонок
        RenderUtil.Render2D.rect(panelX + LEFT_W, panelY + 8, 0.5F, PANEL_H - 16,
                ColorUtil.getColor(255, 0.05F * alpha), 1);

        Fonts.icon.draw("P", panelX + 10, panelY + 12.5F, 7, ColorUtil.multAlpha(ColorUtil.client(), alpha));
        font.draw("Пресеты инвентаря", panelX + 22, panelY + 11.5F, 7, ColorUtil.getColor(200, alpha));

        font.draw("Предпросмотр", panelX + LEFT_W + 12, panelY + 11.5F, 7, ColorUtil.getColor(200, alpha));

        float inputX = panelX + 10;
        float inputW = LEFT_W - 20;
        float inputY = panelY + 28;

        drawInput(font, inputX, inputY, inputW, alpha);

        float saveY = inputY + 16 + ROW_GAP;
        boolean saveHover = drawButton(font, "Сохранить текущий", inputX, saveY, inputW, alpha, true);

        rows.clear();

        List<InventoryPreset> presets = manager().presets();

        float listY = saveY + 16 + 6;
        float listH = panelY + PANEL_H - 32 - listY;

        maxScroll = Math.max(0, presets.size() * (ROW_H + ROW_GAP) - listH);
        scrollTarget = MathUtil.clamp(scrollTarget, 0, maxScroll);

        Scissor.enable(inputX, listY, inputW, listH, 2);

        InventoryPreset hoveredPreset = null;
        float rowY = listY - scroll;

        for (InventoryPreset preset : presets) {
            boolean active = preset.name().equalsIgnoreCase(manager().activeName());
            boolean hovered = inside(mouseX, mouseY, inputX, rowY, inputW, ROW_H);
            if (hovered) hoveredPreset = preset;

            float hover = hoverAnim("row:" + preset.name(), hovered);

            ru.white.utils.animation.satoshi.Animation activeAnim = anim("active:" + preset.name());
            activeAnim.setDirection(active ? Direction.FORWARDS : Direction.BACKWARDS);
            float sel = activeAnim.getOutput();

            RenderUtil.Render2D.rect(inputX, rowY, inputW, ROW_H, ColorUtil.overCol(
                    ColorUtil.getColor(40, 0.15F * alpha),
                    ColorUtil.replAlpha(ColorUtil.client(), 0.25F * alpha), sel), 5);

            RenderUtil.Render2D.outline(inputX, rowY, inputW, ROW_H, 0.5F,
                    ColorUtil.replAlpha(ColorUtil.client(), alpha * Math.max(hover, sel)), 5);

            font.drawFadingText(preset.name(), inputX + 6, rowY + 5.5F, inputW - 26,
                    ColorUtil.getColor(255, alpha * (0.5F + 0.5F * Math.max(hover, sel))), TEXT);

            boolean deleteHover = inside(mouseX, mouseY, inputX + inputW - 18, rowY, 18, ROW_H);
            Fonts.icon.drawCentered("Q", inputX + inputW - 9, rowY + 7f, 4,
                    deleteHover ? ColorUtil.getColor(235, 70, 70, alpha) : ColorUtil.getColor(255, alpha * 0.35F));

            rows.add(new Row(preset, inputX, rowY, inputW, ROW_H));

            rowY += ROW_H + ROW_GAP;
        }

        Scissor.disable();

        boolean hasActive = manager().activePreset().isPresent();
        drawButton(font, hasActive ? "Отключить пресет" : "Пресет не выбран",
                inputX, panelY + PANEL_H - 26, inputW, alpha, false);

        InventoryPreset selected = hoveredPreset != null ? hoveredPreset : preview;
        if (selected == null) {
            selected = manager().activePreset().orElse(presets.isEmpty() ? null : presets.get(0));
        }

        renderPreview(context, font, selected, alpha, rawMouseX, rawMouseY);

        Render2D.endOverlay();
    }

    private void drawInput(Font font, float x, float y, float w, float alpha) {
        float hover = hoverAnim("input", inside(mouseX, mouseY, x, y, w, 16) || focused);

        ru.white.utils.animation.satoshi.Animation focusAnim = anim("input:focus");
        focusAnim.setDirection(focused ? Direction.FORWARDS : Direction.BACKWARDS);
        float focus = focusAnim.getOutput();

        RenderUtil.Render2D.rect(x, y, w, 16, ColorUtil.overCol(
                ColorUtil.getColor(40, 0.15F * alpha),
                ColorUtil.getColor(25, 0.3F * alpha), Math.max(hover, focus)), 5);

        RenderUtil.Render2D.outline(x, y, w, 16, 0.5F,
                ColorUtil.replAlpha(ColorUtil.client(), alpha * focus), 5);

        boolean placeholder = name.isEmpty() && focus < 0.99F;

        if (placeholder) {
            font.draw("Название пресета", x + 6, y + 4.5F + 4 * focus, TEXT,
                    ColorUtil.getColor(255, alpha * (1 - focus) * 0.35F));
        }

        if (!name.isEmpty()) {
            font.drawFadingTextReverse(name, x + 6, y + 4.5F, w - 14,
                    ColorUtil.getColor(255, alpha * 0.85F), TEXT);
        }

        if (focus > 0.01F && (System.currentTimeMillis() / 450L) % 2 == 0) {
            RenderUtil.Render2D.rect(x + 6.5F + font.getWidth(name, TEXT), y + 4.5F, 0.6F, 8,
                    ColorUtil.replAlpha(ColorUtil.client(), alpha * focus), 0.3F);
        }
    }

    private boolean drawButton(Font font, String label, float x, float y, float w, float alpha, boolean accent) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, 16);
        float hover = hoverAnim("btn:" + label, hovered);

        RenderUtil.Render2D.glow(x, y, w, 16, ColorUtil.getColor(0, 0.06F * alpha), 5, 7, 1);

        RenderUtil.Render2D.rect(x, y, w, 16, ColorUtil.overCol(
                ColorUtil.getColor(40, 0.15F * alpha),
                ColorUtil.replAlpha(ColorUtil.client(), (accent ? 0.3F : 0.2F) * alpha), hover), 5);

        RenderUtil.Render2D.outline(x, y, w, 16, 0.5F, ColorUtil.replAlpha(ColorUtil.client(),
                alpha * (accent ? 0.4F + 0.6F * hover : hover)), 5);

        font.drawCentered(label, x + w / 2, y + 4.5F, TEXT,
                ColorUtil.getColor(255, alpha * (0.55F + 0.45F * hover)));

        return hovered;
    }

    private void renderPreview(DrawContext context, Font font, InventoryPreset preset, float alpha,
                               int rawMouseX, int rawMouseY) {
        float baseX = panelX + LEFT_W + 12;
        float baseY = panelY + 34;

        if (preset == null) {
            font.draw("Сохранённых пресетов пока нет", baseX, baseY, TEXT, ColorUtil.getColor(255, alpha * 0.35F));
            return;
        }

        font.draw(preset.name(), baseX, baseY, TEXT, ColorUtil.replAlpha(ColorUtil.client(), alpha));

        float gridX = baseX + 26;
        float gridY = baseY + 24;

        ItemStack hovered = ItemStack.EMPTY;

        font.draw("Броня", baseX, baseY + 14, SMALL, ColorUtil.getColor(255, alpha * 0.35F));
        for (int i = 0; i < 4; i++) {
            ItemStack stack = preset.slot(36 + i).toStack();
            if (drawCell(context, stack, baseX, gridY + i * CELL, alpha)) hovered = stack;
        }

        font.draw("Оффхенд", baseX, gridY + 4 * CELL + 4, SMALL, ColorUtil.getColor(255, alpha * 0.35F));
        ItemStack offhand = preset.slot(InventoryPreset.OFFHAND_SLOT).toStack();
        if (drawCell(context, offhand, baseX, gridY + 4 * CELL + 15, alpha)) hovered = offhand;

        font.draw("Инвентарь", gridX, baseY + 14, SMALL, ColorUtil.getColor(255, alpha * 0.35F));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                ItemStack stack = preset.slot(9 + row * 9 + col).toStack();
                if (drawCell(context, stack, gridX + col * CELL, gridY + row * CELL, alpha)) hovered = stack;
            }
        }

        font.draw("Хотбар", gridX, gridY + 3 * CELL + 4, SMALL, ColorUtil.getColor(255, alpha * 0.35F));
        for (int col = 0; col < 9; col++) {
            ItemStack stack = preset.slot(col).toStack();
            if (drawCell(context, stack, gridX + col * CELL, gridY + 3 * CELL + 15, alpha)) hovered = stack;
        }

        // тултип рисуется ванильно, поэтому и координаты ему нужны ванильные
        if (!hovered.isEmpty()) {
            Render2D.endOverlay();
            context.drawItemTooltip(textRenderer, hovered, rawMouseX, rawMouseY);
            Render2D.beginOverlay();
        }
    }

    /** @return true, если курсор над ячейкой */
    private boolean drawCell(DrawContext context, ItemStack stack, float x, float y, float alpha) {
        boolean hovered = inside(mouseX, mouseY, x, y, 17, 17);

        RenderUtil.Render2D.rect(x, y, 17, 17, ColorUtil.getColor(40, 0.15F * alpha), 4);

        if (hovered) {
            RenderUtil.Render2D.outline(x, y, 17, 17, 0.5F,
                    ColorUtil.replAlpha(ColorUtil.client(), alpha), 4);
        }

        if (!stack.isEmpty()) {
            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate((x + 8.5F) * scaleFix, (y + 8.5F) * scaleFix);
            matrices.scale(scaleFix, scaleFix);
            context.drawItem(stack, -8, -8);
            context.drawStackOverlay(textRenderer, stack, -8, -8);
            matrices.popMatrix();
        }

        return hovered && !stack.isEmpty();
    }

    private ru.white.utils.animation.satoshi.Animation anim(String key) {
        return anims.computeIfAbsent(key, k -> new EaseInOutQuad(300, 1));
    }

    private float hoverAnim(String key, boolean hovered) {
        ru.white.utils.animation.satoshi.Animation animation = anim(key + ":hover");
        animation.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);
        return animation.getOutput();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);

        float mx = (float) (click.x() / scaleFix);
        float my = (float) (click.y() / scaleFix);

        float inputX = panelX + 10;
        float inputW = LEFT_W - 20;
        float inputY = panelY + 28;

        focused = inside(mx, my, inputX, inputY, inputW, 16);

        if (inside(mx, my, inputX, inputY + 16 + ROW_GAP, inputW, 16)) {
            saveCurrent();
            return true;
        }

        for (Row row : rows) {
            if (inside(mx, my, row.x + row.w - 18, row.y, 18, row.h)) {
                manager().delete(row.preset);
                if (preview == row.preset) preview = null;
                return true;
            }
            if (inside(mx, my, row.x, row.y, row.w - 18, row.h)) {
                manager().activate(row.preset);
                client.setScreen(parent);
                return true;
            }
        }

        if (inside(mx, my, inputX, panelY + PANEL_H - 26, inputW, 16)
                && manager().activePreset().isPresent()) {
            manager().deactivate();
            return true;
        }

        return true;
    }

    private void saveCurrent() {
        InventoryPreset saved = manager().saveCurrent(name);
        if (saved != null) {
            preview = saved;
            name = "";
            focused = false;
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (focused) {
            if (input.key() == GLFW.GLFW_KEY_ENTER) {
                saveCurrent();
            } else if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !name.isEmpty()) {
                name = name.substring(0, name.length() - 1);
            } else if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                focused = false;
            }
            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            client.setScreen(parent);
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (focused && input.isValidChar() && name.length() < 32) {
            name += input.asString();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget = MathUtil.clamp((float) (scrollTarget - vertical * 24), 0, maxScroll);
        return true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static boolean inside(float mouseX, float mouseY, float x, float y, float w, float h) {
        return MathUtil.isHovered(mouseX, mouseY, x, y, w, h);
    }

    private record Row(InventoryPreset preset, float x, float y, float w, float h) {
    }
}

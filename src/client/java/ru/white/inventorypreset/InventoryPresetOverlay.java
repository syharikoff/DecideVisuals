package ru.white.inventorypreset;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import ru.white.Client;
import ru.white.mixin.HandledScreenAccessor;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.Render2D;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Панель недостающих предметов рядом с инвентарём.
 * Рисуется из GameRenderMixin уже после ванильного GUI: тот копит вызовы в GuiRenderState
 * и сабмитит их в конце кадра, поэтому из инжекта в InventoryScreen.render панель уходила под него.
 */
public final class InventoryPresetOverlay {

    private static final float PANEL_W = 118F;
    private static final float ROW_H = 21F;

    private InventoryPresetOverlay() {
    }

    public static void render(DrawContext context, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (Client.get() == null || Client.get().inventoryPresetManager() == null) return;
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;

        InventoryPresetManager manager = Client.get().inventoryPresetManager();
        HandledScreenAccessor accessor = (HandledScreenAccessor) screen;

        int guiX = accessor.getScreenX();
        int guiY = accessor.getScreenY();

        // RenderUtil живёт в пространстве «гуи-скейл 2», ванильный экран — в своём
        float scaleFix = 2F / (float) mc.getWindow().getScaleFactor();

        Render2D.beginOverlay();

        drawItem(context, Items.CHEST.getDefaultStack(), (guiX + 160) / scaleFix, (guiY - 11) / scaleFix, scaleFix);

        if (manager.activePreset().isEmpty()) {
            Render2D.endOverlay();
            return;
        }

        Map<String, InventoryPresetManager.MissingItem> missing = manager.missingItems();
        List<InventoryPresetManager.MissingItem> missingItems = new ArrayList<>(missing.values());

        int visibleRows = Math.min(8, Math.max(1, missingItems.size()));
        int missingScroll = manager.missingScroll(missingItems.size(), visibleRows);

        float panelX = (guiX + 182) / scaleFix;
        float panelY = (guiY + 8) / scaleFix;
        float panelH = 31F + visibleRows * ROW_H + 27F;

        float mx = mouseX / scaleFix;
        float my = mouseY / scaleFix;

        Font font = Fonts.sf_regular;

        RenderUtil.Render2D.glow(panelX, panelY, PANEL_W, panelH - 0.5F, ColorUtil.getColor(0, 0.15F), 8, 15, 1);
        RenderUtil.Blur.blur(panelX, panelY, PANEL_W, panelH, 1, 8,
                ColorUtil.multAlpha(ColorUtil.multDark(ColorUtil.background(), 0.6F), 1F));
        RenderUtil.Render2D.outline(panelX, panelY, PANEL_W, panelH, 0.5F,
                ColorUtil.replAlpha(ColorUtil.client(), 0.35F), 8);

        Fonts.icon.draw("K", panelX + 8, panelY + 11, 6.5F, ColorUtil.client());
        font.draw("Не хватает", panelX + 19, panelY + 10F, 6.5F, ColorUtil.getColor(200));

        if (missingItems.size() > visibleRows) {
            String counter = (missingScroll + 1) + "–"
                    + Math.min(missingItems.size(), missingScroll + visibleRows)
                    + " / " + missingItems.size();

            font.draw(counter, panelX + PANEL_W - 8 - font.getWidth(counter, 5.5F), panelY + 10.5F, 5.5F,
                    ColorUtil.getColor(255, 0.35F));
        }

        float rowY = panelY + 25F;

        if (missing.isEmpty()) {
            font.draw("Все предметы найдены", panelX + 8, rowY + 6F, 6F,
                    ColorUtil.getColor(110, 220, 140, 1F));
        } else {
            for (int index = missingScroll;
                 index < Math.min(missingItems.size(), missingScroll + visibleRows); index++) {

                InventoryPresetManager.MissingItem item = missingItems.get(index);
                boolean hovered = inside(mx, my, panelX + 4, rowY, PANEL_W - 8, ROW_H - 2);

                RenderUtil.Render2D.rect(panelX + 4, rowY, PANEL_W - 8, ROW_H - 2,
                        ColorUtil.getColor(40, hovered ? 0.3F : 0.15F), 5);

                if (hovered) {
                    RenderUtil.Render2D.outline(panelX + 4, rowY, PANEL_W - 8, ROW_H - 2, 0.5F,
                            ColorUtil.replAlpha(ColorUtil.client(), 1F), 5);
                }

                ItemStack stack = item.stack();
                if (!stack.isEmpty()) {
                    drawItem(context, stack, panelX + 13F, rowY + ROW_H / 2F - 1F, scaleFix);
                }

                font.draw("x" + item.count(), panelX + 24, rowY + 5.4f, 8, ColorUtil.getColor(230));

                rowY += ROW_H;
            }
        }

        float arrangeY = panelY + panelH - 23F;
        boolean arrangeHover = inside(mx, my, panelX + 5, arrangeY, PANEL_W - 10, 18F);
        boolean arranging = manager.isArranging();

        RenderUtil.Render2D.rect(panelX + 5, arrangeY, PANEL_W - 10, 18F, arranging
                ? ColorUtil.getColor(200, 60, 60, 0.35F)
                : ColorUtil.replAlpha(ColorUtil.client(), arrangeHover ? 0.35F : 0.2F), 5);

        RenderUtil.Render2D.outline(panelX + 5, arrangeY, PANEL_W - 10, 18F, 0.5F, arranging
                ? ColorUtil.getColor(200, 60, 60, 1F)
                : ColorUtil.replAlpha(ColorUtil.client(), arrangeHover ? 1F : 0.4F), 5);

        font.drawCentered(arranging ? "Остановить" : "Разложить всё",
                panelX + PANEL_W / 2F, arrangeY + 5.5F, 6.5F, ColorUtil.getColor(255, 0.9F));

        Render2D.endOverlay();
    }

    /** Предмет рисуется ванильно, поэтому центр переводится обратно в экранные координаты. */
    private static void drawItem(DrawContext context, ItemStack stack, float centerX, float centerY, float scaleFix) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(centerX * scaleFix, centerY * scaleFix);
        matrices.scale(scaleFix, scaleFix);
        context.drawItem(stack, -8, -8);
        matrices.popMatrix();
    }

    private static boolean inside(float mouseX, float mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}

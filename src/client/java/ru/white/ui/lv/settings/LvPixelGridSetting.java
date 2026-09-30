package ru.white.ui.lv.settings;

import java.awt.Color;
import ru.white.module.api.settings.impl.PixelGridSetting;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.ThemeManager;
import ru.white.ui.util.Position;
import ru.white.utils.other.GuiSounds;
import ru.white.utils.render.Draw;

/**
 * Инлайн-редактор пиксельной сетки (прицел): имя сверху, ниже сам холст.
 * ЛКМ по клетке — включить, повторный клик или перетаскивание по зажатой кнопке — стереть.
 */
public class LvPixelGridSetting extends SettingWidget {
   private static final float TITLE_H = 16.0F;
   private static final float PAD = 4.0F;
   private static final float CELL_MAX = 4.0F;
   private static final float EDGE = 2.0F;
   private final PixelGridSetting backend;
   private boolean painting;
   private boolean erase;

   public LvPixelGridSetting(PixelGridSetting pixelGridSetting) {
      this.backend = pixelGridSetting;
   }

   @Override
   public String name() {
      return this.backend.getName();
   }

   @Override
   public float height() {
      return TITLE_H + PAD + this.gridSize() + EDGE * 2.0F;
   }

   private float gridSize() {
      return this.backend.size * this.cellSize();
   }

   private float cellSize() {
      return Math.max(2.0F, Math.min(CELL_MAX, (Math.max(40.0F, 170.0F) - PAD * 2.0F - EDGE * 2.0F) / this.backend.size));
   }

   private static int rgba(int n, int n2, int n3, float f) {
      int n4 = Math.max(0, Math.min(255, Math.round(f)));
      return n4 <= 0 ? 0 : new Color(n, n2, n3, n4).getRGB();
   }

   @Override
   public void render(float f, float f2, float f3, float f4) {
      LvRenderHelper.drawName(this.backend.getName(), f, f2, f3 - 60.0F, f4);
      String sizeText = this.backend.size + "x" + this.backend.size;
      float stw = FontsLV.MONTSERRAT_MEDIUM.width(sizeText, 6.0F);
      FontsLV.MONTSERRAT_MEDIUM.draw(sizeText, f + f3 - stw, f2 + 5.0F, 6.0F, rgba(255, 255, 255, 150.0F * f4));
      if (this.painting) {
         int[] hover = this.cellAt(f, f2, f3, Position.mouseX(), Position.mouseY());
         if (hover != null) {
            this.backend.put(hover[0], hover[1], !this.erase);
         }
      }
      float cell = this.cellSize();
      int size = this.backend.size;
      float gridX = f + (f3 - this.gridSize()) * 0.5F;
      float gridY = f2 + TITLE_H + PAD;
      Draw.rect(gridX - EDGE, gridY - EDGE, this.gridSize() + EDGE * 2.0F, this.gridSize() + EDGE * 2.0F,
            rgba(0, 0, 0, 70.0F * f4), 2.0F);
      int[] hover = this.cellAt(f, f2, f3, Position.mouseX(), Position.mouseY());
      int hoverX = hover == null ? -1 : hover[0];
      int hoverY = hover == null ? -1 : hover[1];
      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            float px = gridX + x * cell;
            float py = gridY + y * cell;
            boolean filled = this.backend.get(x, y);
            if (filled) {
               Draw.rect(px, py, cell, cell, ThemeManager.accentSoft(220.0F * f4), 0.5F);
            } else if (x == hoverX && y == hoverY) {
               Draw.rect(px, py, cell, cell, rgba(255, 255, 255, 90.0F * f4), 0.5F);
            } else {
               Draw.rect(px, py, cell, cell, rgba(255, 255, 255, 12.0F * f4), 0.5F);
            }
         }
      }
   }

   @Override
   public boolean isVisible() {
      return this.backend.getVisible().get();
   }

   @Override
   public boolean click(float f, float f2, float f3, float f4, float f5) {
      int[] cell = this.cellAt(f, f2, f3, f4, f5);
      if (cell == null) {
         return false;
      }
      this.erase = this.backend.get(cell[0], cell[1]);
      this.backend.put(cell[0], cell[1], !this.erase);
      this.painting = true;
      GuiSounds.sliderGrab();
      return true;
   }

   @Override
   public void releaseDrag() {
      if (this.painting) {
         this.painting = false;
         GuiSounds.sliderRelease();
      }
   }

   private int[] cellAt(float f, float f2, float f3, float mouseX, float mouseY) {
      float cell = this.cellSize();
      int size = this.backend.size;
      float gridX = f + (f3 - this.gridSize()) * 0.5F;
      float gridY = f2 + TITLE_H + PAD;
      if (mouseX < gridX || mouseX > gridX + this.gridSize() || mouseY < gridY || mouseY > gridY + this.gridSize()) {
         return null;
      }
      int x = (int) Math.floor((mouseX - gridX) / cell);
      int y = (int) Math.floor((mouseY - gridY) / cell);
      return this.backend.inBounds(x, y) ? new int[]{x, y} : null;
   }

   @Override
   public float preferredWidth() {
      return 6.0F + FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getName(), 6.5F) + 6.0F + 40.0F + 8.0F;
   }
}
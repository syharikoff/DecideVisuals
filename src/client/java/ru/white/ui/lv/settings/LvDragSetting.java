package ru.white.ui.lv.settings;

import java.awt.Color;
import org.joml.Vector2f;
import ru.white.module.api.settings.impl.DragSetting;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.ThemeManager;
import ru.white.ui.util.Position;
import ru.white.utils.other.GuiSounds;
import ru.white.utils.render.Draw;

/**
 * Виджет позиции HUD-элемента: имя + два мини-слайдера (X и Y). ЛКМ — тянем,
 * средняя кнопка по полосе — сброс к значению по умолчанию.
 */
public class LvDragSetting extends SettingWidget {
   public static final float ROW_H = 16.0F;
   public static final float BAR_H = 3.0F;
   public static final float FIRST_BAR_TOP = 14.0F;
   public static final float SECOND_BAR_TOP = 24.0F;
   public static final float AXIS_LABEL_W = 13.0F;
   private final DragSetting backend;
   private final Vector2f defaultValue = new Vector2f();
   private int axis;
   private final float[] visValue = new float[2];
   private long lastNs = System.nanoTime();

   public LvDragSetting(DragSetting dragSetting) {
      this.backend = dragSetting;
      Vector2f value = dragSetting.getValue();
      this.defaultValue.set(value);
      this.visValue[0] = value.x;
      this.visValue[1] = value.y;
   }

   @Override
   public String name() {
      return this.backend.getName();
   }

   @Override
   public float height() {
      return 34.0F;
   }

   private static float clamp01(float f) {
      return f < 0.0F ? 0.0F : (f > 1.0F ? 1.0F : f);
   }

   private static int rgba(int n, int n2, int n3, float f) {
      int n4 = Math.max(0, Math.min(255, Math.round(f)));
      return n4 <= 0 ? 0 : new Color(n, n2, n3, n4).getRGB();
   }

   @Override
   public void render(float f, float f2, float f3, float f4) {
      long l = System.nanoTime();
      float dt = Math.min(0.1F, (float) (l - this.lastNs) / 1.0E9F);
      this.lastNs = l;
      Vector2f value = this.backend.getValue();
      if (this.axis == 1) {
         value.x = this.applyAxis(1, f, f3, Position.mouseX());
      } else if (this.axis == 2) {
         value.y = this.applyAxis(2, f, f3, Position.mouseX());
      }
      if (this.axis != 0) {
         this.backend.targetPosition.set(value);
      }
      float maxX = Math.max(1.0F, Position.screenWidth());
      float maxY = Math.max(1.0F, Position.screenHeight());
      float targetX = clamp01((value.x - 0.0F) / maxX);
      float targetY = clamp01((value.y - 0.0F) / maxY);
      float lerp = 1.0F - (float) Math.exp(-dt * 18.0F);
      this.visValue[0] = this.visValue[0] + (targetX - this.visValue[0]) * lerp;
      this.visValue[1] = this.visValue[1] + (targetY - this.visValue[1]) * lerp;
      if (Math.abs(targetX - this.visValue[0]) < 0.002F) {
         this.visValue[0] = targetX;
      }
      if (Math.abs(targetY - this.visValue[1]) < 0.002F) {
         this.visValue[1] = targetY;
      }
      String string = "X: " + Math.round(value.x) + "  Y: " + Math.round(value.y);
      float tw = FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F);
      LvRenderHelper.drawName(this.backend.getName(), f, f2, f3 - tw - 12.0F, f4);
      FontsLV.MONTSERRAT_MEDIUM.draw(string, f + f3 - tw, f2 + 5.0F, 6.0F, rgba(255, 255, 255, 230.0F * f4));
      this.renderBar(f, f2, f3, f4, "X", this.visValue[0], value.x, FIRST_BAR_TOP);
      this.renderBar(f, f2, f3, f4, "Y", this.visValue[1], value.y, SECOND_BAR_TOP);
   }

   private void renderBar(float f, float f2, float f3, float f4, String label, float progress, float value, float top) {
      float barX = f + 6.0F;
      float barW = f3 - 12.0F - AXIS_LABEL_W;
      float barY = f2 + top;
      FontsLV.MONTSERRAT_MEDIUM.draw(label, barX, barY - 2.0F, 5.0F, ThemeManager.accentSoft(200.0F * f4));
      float trackX = barX + AXIS_LABEL_W;
      float trackW = Math.max(4.0F, barW);
      Draw.rect(trackX, barY, trackW, BAR_H, rgba(16, 16, 16, 64.0F * f4), 1.5F);
      float fill = trackW * clamp01(progress);
      if (fill > 0.6F) {
         int n = ThemeManager.gradientA(215.0F * f4);
         int n2 = ThemeManager.gradientB(215.0F * f4);
         Draw.gradientRect(trackX, barY, fill, BAR_H, new int[]{n, n2, n2, n}, 1.5F);
      }
      float knobX = trackX + trackW * clamp01(progress) - 3.0F;
      float knobY = barY + 1.5F - 3.0F;
      Draw.rect(knobX, knobY, 6.0F, 6.0F, rgba(255, 255, 255, 245.0F * f4), 2.0F);
      String v = String.valueOf(Math.round(value));
      float vw = FontsLV.MONTSERRAT_MEDIUM.width(v, 5.0F);
      FontsLV.MONTSERRAT_MEDIUM.draw(v, trackX + trackW - vw, barY - 2.0F, 5.0F, rgba(255, 255, 255, 110.0F * f4));
   }

   private float applyAxis(int id, float f, float f3, float mouseX) {
      float trackX = f + 6.0F + AXIS_LABEL_W;
      float trackW = Math.max(4.0F, f3 - 12.0F - AXIS_LABEL_W);
      float progress = clamp01((mouseX - trackX) / trackW);
      return id == 1 ? progress * Position.screenWidth() : progress * Position.screenHeight();
   }

   @Override
   public boolean isVisible() {
      return this.backend.getVisible().get();
   }

   @Override
   public boolean click(float f, float f2, float f3, float f4, float f5) {
      int id = this.hitBar(f, f2, f3, f5);
      if (id == 0) {
         return false;
      }
      this.axis = id;
      GuiSounds.sliderGrab();
      Vector2f value = this.backend.getValue();
      if (id == 1) {
         value.x = this.applyAxis(1, f, f3, f4);
      } else {
         value.y = this.applyAxis(2, f, f3, f4);
      }
      this.backend.targetPosition.set(value);
      return true;
   }

   @Override
   public boolean middleClick(float f, float f2, float f3, float f4, float f5) {
      int id = this.hitBar(f, f2, f3, f5);
      if (id == 0) {
         return false;
      }
      this.axis = 0;
      Vector2f value = this.backend.getValue();
      if (id == 1) {
         value.x = this.defaultValue.x;
      } else {
         value.y = this.defaultValue.y;
      }
      this.backend.targetPosition.set(value);
      GuiSounds.sliderGrab();
      return true;
   }

   @Override
   public void releaseDrag() {
      if (this.axis != 0) {
         this.axis = 0;
         GuiSounds.sliderRelease();
      }
   }

   private int hitBar(float f, float f2, float f3, float mouseY) {
      float barX = f + 6.0F + AXIS_LABEL_W;
      float barW = Math.max(4.0F, f3 - 12.0F - AXIS_LABEL_W);
      if (mouseY >= f2 + FIRST_BAR_TOP - 4.0F && mouseY <= f2 + FIRST_BAR_TOP + BAR_H + 4.0F) {
         return 1;
      }
      if (mouseY >= f2 + SECOND_BAR_TOP - 4.0F && mouseY <= f2 + SECOND_BAR_TOP + BAR_H + 4.0F) {
         return 2;
      }
      return 0;
   }

   @Override
   public float preferredWidth() {
      return 6.0F + FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getName(), 6.5F) + 6.0F + 70.0F + 8.0F;
   }
}
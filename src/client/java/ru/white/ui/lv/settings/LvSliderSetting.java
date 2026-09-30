package ru.white.ui.lv.settings;

import java.awt.Color;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.ThemeManager;
import ru.white.ui.util.Position;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.other.GuiSounds;
import ru.white.utils.render.Draw;

public class LvSliderSetting extends SettingWidget {
   private final SliderSetting backend;
   private boolean dragging;
   private String lastSoundText;
   private float visProgress = -1.0F;
   private long lastNs = System.nanoTime();
   private final float defaultValue;
   public static final float ROW_H = 22.0F;
   public static final float BAR_H = 3.0F;
   public static final float BAR_TOP = 16.0F;

   public LvSliderSetting(SliderSetting sliderSetting) {
      this.backend = sliderSetting;
      this.defaultValue = sliderSetting.getValue();
   }

   @Override
   public String name() {
      return this.backend.getName();
   }

   @Override
   public float height() {
      return 22.0F;
   }

   private static float clamp01(float f) {
      return f < 0.0F ? 0.0F : (f > 1.0F ? 1.0F : f);
   }

   @Override
   public void render(float f, float f2, float f3, float f4) {
      long l = System.nanoTime();
      float f6 = Math.min(0.1F, (float)(l - this.lastNs) / 1.0E9F);
      this.lastNs = l;
      float f7 = f + 6.0F;
      float f8 = f3 - 12.0F;
      float f9 = f2 + 16.0F;
      if (this.dragging) {
         float f5 = Position.mouseX();
         float f10 = clamp01((f5 - f7) / f8);
         this.backend.set(this.backend.min + f10 * (this.backend.max - this.backend.min));
         String string = this.formatValue(this.backend.getValue());
         if (!string.equals(this.lastSoundText)) {
            this.lastSoundText = string;
            GuiSounds.sliderTick(this.getProgress());
         }
      }
      float f11 = 6.0F;
      String string = this.formatValue(this.backend.getValue());
      float f12 = FontsLV.MONTSERRAT_MEDIUM.width(string, f11);
      float f13 = f + f3 - 6.0F - f12;
      FontsLV.MONTSERRAT_MEDIUM.draw(string, f13, f2 + 5.0F + 0.25F, f11, rgba(255, 255, 255, 238.0F * f4));
      LvRenderHelper.drawName(this.backend.getName(), f, f2, f13 - (f + 6.0F) - 6.0F, f4);
      float f14 = this.getProgress();
      if (this.visProgress < 0.0F) {
         this.visProgress = f14;
      }
      this.visProgress = this.visProgress + (f14 - this.visProgress) * (1.0F - (float)Math.exp(-f6 * 18.0F));
      if (Math.abs(f14 - this.visProgress) < 0.0015F) {
         this.visProgress = f14;
      }
      float f15 = f7 + f8 * clamp01(this.visProgress);
      float f16 = 1.5F;
      Draw.rect(f7, f9, f8, 3.0F, rgba(16, 16, 16, 64.0F * f4), f16);
      if (f15 - f7 > 0.6F) {
         int n = ThemeManager.gradientA(215.0F * f4);
         int n2 = ThemeManager.gradientB(215.0F * f4);
         Draw.gradientRect(f7, f9, f15 - f7, 3.0F, new int[]{n, n2, n2, n}, f16);
         Draw.outline(f7, f9, f15 - f7, 3.0F, 0.5F, ThemeManager.accentBright(150.0F * f4), f16);
      }
      float f17 = 6.5F;
      float f18 = 4.0F;
      float f19 = 2.0F;
      float f20 = Math.max(f7, Math.min(f7 + f8 - f17, f15 - f17 * 0.5F));
      float f21 = f9 + 1.5F - f18 * 0.5F;
      Draw.rect(f20, f21, f17, f18, rgba(255, 255, 255, 245.0F * f4), f19);
      Draw.outline(f20 - 0.5F, f21 - 0.5F, f17 + 1.0F, f18 + 1.0F, 0.5F, rgba(16, 16, 16, 128.0F * f4), f19);
   }

   private static int rgba(int n, int n2, int n3, float f) {
      int n4 = Math.max(0, Math.min(255, Math.round(f)));
      return n4 <= 0 ? 0 : new Color(n, n2, n3, n4).getRGB();
   }

   @Override
   public boolean isVisible() {
      return this.backend.getVisible().get();
   }

   @Override
   public boolean click(float f, float f2, float f3, float f4, float f5) {
      float f6 = f + 6.0F;
      float f7 = f3 - 12.0F;
      float f8 = f2 + 16.0F;
      if (f4 >= f6 - 4.0F && f4 <= f6 + f7 + 4.0F && f5 >= f8 - 5.0F && f5 <= f8 + 3.0F + 5.0F) {
         this.dragging = true;
         GuiSounds.sliderGrab();
         float f9 = clamp01((f4 - f6) / f7);
         this.backend.set(this.backend.min + f9 * (this.backend.max - this.backend.min));
         this.lastSoundText = this.formatValue(this.backend.getValue());
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void releaseDrag() {
      if (this.dragging) {
         this.dragging = false;
         GuiSounds.sliderRelease();
      }
   }

   @Override
   public boolean middleClick(float f, float f2, float f3, float f4, float f5) {
      float f6 = f + 6.0F;
      float f7 = f3 - 12.0F;
      float f8 = f2 + 16.0F;
      if (f4 >= f6 - 4.0F && f4 <= f6 + f7 + 4.0F && f5 >= f8 - 5.0F && f5 <= f8 + 3.0F + 5.0F) {
         this.dragging = false;
         this.backend.set(this.defaultValue);
         GuiSounds.sliderGrab();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public float preferredWidth() {
      float f = FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getName(), 6.5F);
      float f2 = FontsLV.MONTSERRAT_MEDIUM.width(this.formatValue(this.backend.max), 6.0F);
      return Math.max(100.0F, 6.0F + f + 6.0F + f2 + 6.0F);
   }

   private float getProgress() {
      float f = this.backend.max - this.backend.min;
      return f <= 0.0F ? 0.0F : clamp01((this.backend.getValue() - this.backend.min) / f);
   }

   private boolean isInteger() {
      boolean bl = Math.abs(this.backend.min - Math.round(this.backend.min)) < 1.0E-4F;
      boolean bl2 = Math.abs(this.backend.max - Math.round(this.backend.max)) < 1.0E-4F;
      boolean bl3 = Math.abs(this.backend.increment - Math.round(this.backend.increment)) < 1.0E-4F;
      return bl && bl2 && bl3;
   }

   private String formatValue(float f) {
      if (this.isInteger()) {
         return String.format("%.0f", f);
      } else {
         float f2 = this.backend.increment;
         int n = 1;
         if (f2 > 0.0F) {
            float f3 = f2;
            for (n = 0; n < 3 && Math.abs(f3 - Math.round(f3)) > 1.0E-4F; n++) {
               f3 *= 10.0F;
            }
            n = Math.max(n, 1);
         }
         return String.format("%." + n + "f", f);
      }
   }
}
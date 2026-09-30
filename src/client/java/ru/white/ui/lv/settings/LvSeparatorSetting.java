package ru.white.ui.lv.settings;

import ru.white.module.api.settings.impl.DelimiterSetting;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.ThemeManager;
import ru.white.utils.render.Draw;

public class LvSeparatorSetting extends SettingWidget {
   public static final float HEIGHT = 18.0F;
   private final DelimiterSetting backend;

   public LvSeparatorSetting(DelimiterSetting delimiterSetting) {
      this.backend = delimiterSetting;
   }

   @Override
   public String name() {
      return this.backend.getName();
   }

   @Override
   public float height() {
      return 18.0F;
   }

   @Override
   public void render(float f, float f2, float f3, float f4) {
      String string = this.backend.getName();
      float f6 = 6.0F;
      float f7 = FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F);
      float f5;
      if (f7 > (f5 = Math.max(8.0F, f3 - 24.0F))) {
         f7 = f5;
      }
      float f8 = f + f3 / 2.0F;
      float f9 = f8 - f7 / 2.0F;
      float f10 = f2 + 9.0F - f6 / 2.0F;
      float f11 = f2 + 9.0F;
      float f12 = 6.0F;
      float f13 = 4.0F;
      int n = ThemeManager.accentSoft(210.0F * f4);
      int n2 = ThemeManager.accentSoft(55.0F * f4);
      float f14 = f + f13;
      float f15 = Math.max(0.0F, f9 - f12 - f14);
      float f16 = f9 + f7 + f12;
      float f17 = Math.max(0.0F, f + f3 - f13 - f16);
      if (f15 > 1.0F) {
         Draw.rect(f14, f11, f15, 1.0F, n2, 0.5F);
      }
      if (f17 > 1.0F) {
         Draw.rect(f16, f11, f17, 1.0F, n2, 0.5F);
      }
      FontsLV.MONTSERRAT_MEDIUM.draw(string, f9, f10, f6, n);
   }

   @Override
   public boolean isVisible() {
      return this.backend.getVisible().get();
   }

   @Override
   public boolean click(float f, float f2, float f3, float f4, float f5) {
      return false;
   }

   @Override
   public float preferredWidth() {
      return FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getName(), 6.0F) + 48.0F;
   }
}
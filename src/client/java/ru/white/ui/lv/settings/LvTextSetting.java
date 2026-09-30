package ru.white.ui.lv.settings;

import java.awt.Color;
import ru.white.module.api.settings.impl.StringSetting;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.ThemeManager;
import ru.white.utils.render.Draw;

public class LvTextSetting extends SettingWidget {
   private static final float FIELD_W = 92.0F;
   private static final int MAX_LENGTH = 64;
   private static LvTextSetting focused;
   private final StringSetting backend;

   public LvTextSetting(StringSetting stringSetting) {
      this.backend = stringSetting;
   }

   @Override
   public String name() {
      return this.backend.getName();
   }

   public void typeChar(int n) {
      if (!Character.isISOControl(n)) {
         String string = this.backend.getValue();
         if (string.length() < MAX_LENGTH) {
            this.backend.set(string + new String(Character.toChars(n)));
         }
      }
   }

   public boolean typeKey(int n) {
      switch (n) {
         case 256:
         case 257:
         case 335:
            focused = null;
            return true;
         case 259:
            String string = this.backend.getValue();
            if (!string.isEmpty()) {
               this.backend.set(string.substring(0, string.length() - 1));
            }
            return true;
         default:
            return false;
      }
   }

   @Override
   public float height() {
      return 16.0F;
   }

   @Override
   public void render(float f, float f2, float f3, float f4) {
      float f5 = 12.0F;
      float f6 = f + f3 - 92.0F - 4.0F;
      float f7 = f2 + (16.0F - f5) * 0.5F;
      boolean bl2 = this.isFocused();
      LvRenderHelper.drawName(this.backend.getName(), f, f2, f6 - (f + 6.0F) - 4.0F, f4);
      Draw.rect(f6, f7, 92.0F, f5, new Color(0, 0, 0, (int)(60.0F * f4)).getRGB(), 3.0F);
      if (bl2) {
         Draw.outline(f6, f7, 92.0F, f5, 0.6F, ThemeManager.accentBright(190.0F * f4), 3.0F);
      }
      String string;
      boolean bl = (string = this.backend.getValue()) == null || string.isEmpty();
      String string2 = bl ? "..." : string;
      float f8 = 82.0F;
      String string3 = string2;
      while (string3.length() > 1 && FontsLV.MONTSERRAT_MEDIUM.width(string3, 6.0F) > f8) {
         string3 = string3.substring(1);
      }
      int n = bl ? new Color(255, 255, 255, (int)(110.0F * f4)).getRGB() : ThemeManager.accentSoft(220.0F * f4);
      FontsLV.MONTSERRAT_MEDIUM.draw(string3, f6 + 5.0F, f7 + 2.5F, 6.0F, n);
      if (bl2 && System.currentTimeMillis() / 500L % 2L == 0L) {
         float f9 = f6 + 5.0F + (bl ? 0.0F : FontsLV.MONTSERRAT_MEDIUM.width(string3, 6.0F)) + 0.5F;
         Draw.rect(f9, f7 + 2.0F, 0.8F, f5 - 4.0F, ThemeManager.accentBright(220.0F * f4), 0.0F);
      }
   }

   @Override
   public boolean isVisible() {
      return this.backend.getVisible().get();
   }

   @Override
   public boolean click(float f, float f2, float f3, float f4, float f5) {
      float f6 = 12.0F;
      float f7 = f + f3 - 92.0F - 4.0F;
      float f8 = f2 + (16.0F - f6) * 0.5F;
      if (f4 >= f7 && f4 <= f7 + 92.0F && f5 >= f8 && f5 <= f8 + f6) {
         focused = this.isFocused() ? null : this;
         return true;
      } else {
         if (this.isFocused()) {
            focused = null;
         }
         return false;
      }
   }

   public boolean isFocused() {
      return focused == this;
   }

   public static void unfocusAll() {
      focused = null;
   }

   @Override
   public float preferredWidth() {
      return 6.0F + FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getName(), 6.5F) + 6.0F + 92.0F + 8.0F;
   }
}
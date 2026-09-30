package ru.white.ui.lv.settings;

import ru.white.ui.fonts.FontsLV;
import ru.white.ui.lv.LvRectUtil;
import ru.white.ui.theme.ThemeManager;
import ru.white.utils.render.Draw;

public final class LvRenderHelper {
   private static final float REFERENCE_RADIUS = 7.0F;

   private LvRenderHelper() {
   }

   public static float effectiveCornerRadius(float f, float f2, float f3) {
      float f4 = Math.min(f2, f3) * 0.5F;
      return Math.min(f4, f * radiusScale());
   }

   public static void drawPanelBg(float f, float f2, float f3, float f4, float f5, float f6, float f7, float f8, float f9) {
      int n = Math.max(0, Math.min(255, Math.round(40.0F * f9)));
      if (n > 0) {
         float f10 = radiusScale();
         float f11 = Math.min(f3, f4) * 0.5F;
         float f12 = Math.min(f11, f5 * f10);
         float f13 = Math.min(f11, f6 * f10);
         float f14 = Math.min(f11, f7 * f10);
         float f15 = Math.min(f11, f8 * f10);
         Draw.rect(f, f2, f3, f4, rgba(0, 0, 0, n), f12, f13, f14, f15);
      }
   }

   public static void drawPanelBg(float f, float f2, float f3, float f4, float f5, float f6) {
      drawPanelBg(f, f2, f3, f4, f5, f5, f5, f5, f6);
   }

   public static float cornerEdgeInset(float f, float f2) {
      if (f <= f2) {
         return 0.0F;
      } else {
         float f3 = f - f2;
         return f - (float)Math.sqrt(Math.max(0.0F, f * f - f3 * f3));
      }
   }

   public static void drawDropBackground(float f, float f2, float f3, float f4, float f5) {
      LvRectUtil.drawClientRectNoGlow(f, f2, f3, f4, 2.0F, f5);
   }

   private static float radiusScale() {
      return 1.0F;
   }

   public static void drawName(String string, float f, float f2, float f3, float f4) {
      int n = rgba(255, 255, 255, (int)(200.0F * f4));
      drawScrollingText(string, f + 6.0F, f2 + 5.0F, f3, 6.5F, n);
   }

   private static float easeInOutBack(float f) {
      float f2 = 1.70158F;
      float f3 = f2 * 1.525F;
      if (f < 0.5F) {
         float f4 = 2.0F * f;
         return f4 * f4 * ((f3 + 1.0F) * f4 - f3) * 0.5F;
      } else {
         float f5 = 2.0F * f - 2.0F;
         return (f5 * f5 * ((f3 + 1.0F) * f5 + f3) + 2.0F) * 0.5F;
      }
   }

   public static void drawScrollingText(String string, float f, float f2, float f3, float f4, int n) {
      float f5 = FontsLV.MONTSERRAT_MEDIUM.width(string, f4);
      float f6 = f5 - f3;
      if (f6 <= 1.0F) {
         FontsLV.MONTSERRAT_MEDIUM.draw(string, f, f2, f4, n);
      } else {
         float f7 = 11.0F;
         float f8 = 1.2F;
         float f9 = Math.max(0.35F, f6 / f7);
         double d = (f8 + f9) * 2.0;
         double d2 = System.nanoTime() / 1.0E9 % d;
         float f10 = d2 < f8
            ? 0.0F
            : (
               d2 < f8 + f9
                  ? f6 * easeInOutBack((float)((d2 - f8) / f9))
                  : (d2 < f8 * 2.0 + f9 ? f6 : f6 * (1.0F - easeInOutBack((float)((d2 - f8 * 2.0 - f9) / f9))))
            );
         float f11 = Math.max(0.0F, Math.min(1.0F, f10 / 4.0F));
         float f12 = Math.max(0.0F, Math.min(1.0F, (f6 - f10) / 4.0F));
         FontsLV.MONTSERRAT_MEDIUM.msdfFade(string, f - f10, f2, f4, n, f, f + f3, 5.0F, f11, f12);
      }
   }

   public static void drawBtn(float f, float f2, float f3, float f4, String string, float f5) {
      drawBtn(f, f2, f3, f4, string, f5, 0.0F);
   }

   public static void drawBtn(float f, float f2, float f3, float f4, String string, float f5, float f6) {
      drawPanelBg(f, f2, f3, f4, 3.0F, f5);
      float f7 = FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F);
      FontsLV.MONTSERRAT_MEDIUM.draw(string, f + (f3 - f7) * 0.5F + f6, f2 + (f4 - 7.0F) * 0.5F, 6.0F, ThemeManager.accentSoft(220.0F * f5));
   }

   private static int rgba(int n, int n2, int n3, int n4) {
      return n4 << 24 | n << 16 | n2 << 8 | n3;
   }
}
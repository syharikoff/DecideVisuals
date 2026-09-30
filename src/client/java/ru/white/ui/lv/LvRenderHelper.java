package ru.white.ui.lv;

import java.awt.Color;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.ThemeManager;
import ru.white.utils.render.Draw;

public final class LvRenderHelper {
   private static final float REFERENCE_RADIUS = 7.0F;

   private LvRenderHelper() {
   }

   public static float effectiveCornerRadius(float radius, float w, float maxRef) {
      return LvRectUtil.effectiveCornerRadius(radius, w, maxRef);
   }

   public static float cornerEdgeInset(float radius, float thickness) {
      return LvRectUtil.cornerEdgeInset(radius, thickness);
   }

   public static void drawPanelBg(float x, float y, float w, float h, float alpha) {
      LvRectUtil.drawPanelBg(x, y, w, h, alpha);
   }

   public static void drawPanelBg(float x, float y, float w, float h, float radius, float alpha) {
      LvRectUtil.drawPanelBg(x, y, w, h, radius, alpha);
   }

   public static void drawPanelBg(float x, float y, float w, float h,
                                  float tl, float tr, float br, float bl, float alpha) {
      LvRectUtil.drawPanelBg(x, y, w, h, tl, tr, br, bl, alpha);
   }

   public static void drawDropBackground(float x, float y, float w, float h, float alpha) {
      LvRectUtil.drawDropBackground(x, y, w, h, alpha);
   }

   public static void drawName(String text, float x, float y, float maxW, float alpha) {
      int color = new Color(255, 255, 255, (int)(200.0F * alpha)).getRGB();
      drawScrollingText(text, x + 6.0F, y + 5.0F, maxW, 6.5F, color);
   }

   public static void drawScrollingText(String text, float x, float y, float maxW, float size, int color) {
      float tw = FontsLV.MONTSERRAT_MEDIUM.width(text, size);
      float overflow = tw - maxW;
      if (overflow <= 1.0F) {
         FontsLV.MONTSERRAT_MEDIUM.draw(text, x, y, size, color);
      } else {
         float pauseDur = 1.2F;
         float scrollDur = Math.max(0.35F, overflow / 11.0F);
         double cycle = (pauseDur + scrollDur) * 2.0;
         double t = System.nanoTime() / 1.0E9 % cycle;
         float offset;
         if (t < pauseDur) {
            offset = 0.0F;
         } else if (t < pauseDur + scrollDur) {
            offset = overflow * easeInOutBack((float)((t - pauseDur) / scrollDur));
         } else if (t < pauseDur * 2.0 + scrollDur) {
            offset = overflow;
         } else {
            offset = overflow * (1.0F - easeInOutBack((float)((t - pauseDur * 2.0 - scrollDur) / scrollDur)));
         }
         float fadeIn = Math.max(0.0F, Math.min(1.0F, offset / 4.0F));
         float fadeOut = Math.max(0.0F, Math.min(1.0F, (overflow - offset) / 4.0F));
         FontsLV.MONTSERRAT_MEDIUM.msdfFade(text, x - offset, y, size, color, x, x + maxW, 5.0F, fadeIn, fadeOut);
      }
   }

   public static void drawBtn(float x, float y, float w, float h, String text, float alpha) {
      drawBtn(x, y, w, h, text, alpha, 0.0F);
   }

   public static void drawBtn(float x, float y, float w, float h, String text, float alpha, float xOff) {
      drawPanelBg(x, y, w, h, 3.0F, alpha);
      float tw = FontsLV.MONTSERRAT_MEDIUM.width(text, 6.0F);
      FontsLV.MONTSERRAT_MEDIUM.draw(text, x + (w - tw) * 0.5F + xOff, y + (h - 7.0F) * 0.5F, 6.0F,
            ThemeManager.accentSoft(220.0F * alpha));
   }

   private static float easeInOutBack(float t) {
      float c1 = 1.70158F;
      float c3 = c1 * 1.525F;
      if (t < 0.5F) {
         float f = 2.0F * t;
         return f * f * ((c3 + 1.0F) * f - c3) * 0.5F;
      } else {
         float f = 2.0F * t - 2.0F;
         return (f * f * ((c3 + 1.0F) * f + c3) + 2.0F) * 0.5F;
      }
   }
}

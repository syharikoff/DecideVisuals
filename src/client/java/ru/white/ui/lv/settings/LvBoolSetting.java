package ru.white.ui.lv.settings;

import java.awt.Color;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.AccentGradient;
import ru.white.ui.theme.ThemeManager;
import ru.white.ui.util.anim.Decelerate;
import ru.white.ui.util.anim.Direction;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.Draw;

public class LvBoolSetting extends SettingWidget {
   private final BooleanSetting backend;
   private final Decelerate anim;
   public static final float PW = 19.1F;
   public static final float PH = 10.4F;
   public static final float PR = 4.8F;
   public static final float PAD = 4.0F;

   public LvBoolSetting(BooleanSetting booleanSetting) {
      this.backend = booleanSetting;
      this.anim = new Decelerate();
      this.anim.setMs(120);
      this.anim.setValue(1.0);
      this.anim.setDirection(booleanSetting.getValue() ? Direction.FORWARDS : Direction.BACKWARDS);
      this.anim.counter.setTime(System.currentTimeMillis() - 10000L);
   }

   @Override
   public String name() {
      return this.backend.getName();
   }

   public boolean getValue() {
      return this.backend.getValue();
   }

   public void setValue(boolean bl) {
      this.backend.set(bl);
   }

   @Override
   public float height() {
      return 16.0F;
   }

   @Override
   public void render(float f, float f2, float f3, float f4) {
      float f5 = f + f3 - 19.1F - 4.0F;
      float f6 = f2 + 2.8F;
      LvRenderHelper.drawName(this.backend.getName(), f, f2, f5 - (f + 6.0F) - 4.0F, f4);
      this.anim.setDirection(this.backend.getValue() ? Direction.FORWARDS : Direction.BACKWARDS);
      float f7 = this.anim.getOutput().floatValue();
      drawToggle(f5, f6, f7, f4);
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
      float f6 = f + f3 - 19.1F - 4.0F;
      float f7 = f2 + 2.8F;
      if (f4 >= f6 && f4 <= f6 + 19.1F && f5 >= f7 && f5 <= f7 + 10.4F) {
         this.backend.set(!this.backend.getValue());
         return true;
      } else {
         return false;
      }
   }

   public static void drawToggle(float f, float f2, float f3, float f4, float f5, float f6) {
      float f7 = f4 * 0.4615385F;
      int n = rgba(96, 99, 105, 165.0F * f6 * (1.0F - f5));
      int n2 = rgba(78, 81, 87, 165.0F * f6 * (1.0F - f5));
      int n3 = rgba(120, 123, 129, 150.0F * f6);
      int n4 = ColorUtil.interpolateColor(n3, ThemeManager.accentBright(150.0F * f6), f5);
      if (f5 < 0.999F) {
         Draw.gradientRect(f + 0.5F, f2 + 0.5F, f3 - 1.0F, f4 - 1.0F, new int[]{n, n2, n2, n}, f7);
      }
      if (f5 > 0.001F) {
         AccentGradient.fillHorizontal(f + 0.5F, f2 + 0.5F, f3 - 1.0F, f4 - 1.0F, f7, 175.0F * f6 * f5);
      }
      Draw.outline(f, f2, f3, f4, 0.5F, n4, f7);
      float f8 = f4 * 0.8076923F;
      float f9 = f4 * 0.125F;
      float f10 = f + f9;
      float f11 = f + f3 - f8 - f9;
      float f12 = f10 + (f11 - f10) * f5;
      float f13 = f2 + (f4 - f8) * 0.5F;
      int n5 = rgba(239, 252, 255, 255.0F * f6);
      int n6 = rgba(242, 250, 255, 255.0F * f6);
      Draw.gradientRect(f12, f13, f8, f8, new int[]{n5, n5, n6, n5}, f8 * 0.5F);
      Draw.outline(f12, f13, f8, f8, 0.5F, rgba(255, 255, 255, 210.0F * f6), f8 * 0.5F);
   }

   public static void drawToggle(float f, float f2, float f3, float f4) {
      drawToggle(f, f2, 19.1F, 10.4F, f3, f4);
   }

   @Override
   public float preferredWidth() {
      return 6.0F + FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getName(), 6.5F) + 6.0F + 19.1F + 4.0F;
   }
}
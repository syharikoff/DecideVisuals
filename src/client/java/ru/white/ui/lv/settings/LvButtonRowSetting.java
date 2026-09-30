package ru.white.ui.lv.settings;

import java.awt.Color;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.ThemeManager;
import ru.white.ui.util.Position;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.Draw;

public class LvButtonRowSetting extends SettingWidget {
   public static final float HEIGHT = 18.0F;
   private final ButtonSetting backend;
   private float hoverT;
   private float pressT;
   private long lastNs = System.nanoTime();

   public LvButtonRowSetting(ButtonSetting buttonSetting) {
      this.backend = buttonSetting;
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
      long l = System.nanoTime();
      float dt = Math.min(0.1F, (float) (l - this.lastNs) / 1.0E9F);
      this.lastNs = l;
      float lerp = 1.0F - (float) Math.exp(-dt * 16.0F);
      boolean hover = Position.mouseX() >= f && Position.mouseX() <= f + f3
            && Position.mouseY() >= f2 && Position.mouseY() <= f2 + this.height();
      this.hoverT += ((hover ? 1.0F : 0.0F) - this.hoverT) * lerp;
      this.pressT += (this.backend.pressAnim.get() - this.pressT) * lerp;
      float accent = Math.max(this.hoverT, this.pressT);
      Draw.rect(f, f2, f3, 18.0F, rgba(0, 0, 0, 55.0F * f4), 4.0F);
      int n = ThemeManager.accentSoft(115.0F * accent * f4);
      int n2 = ColorUtil.interpolateColor(rgba(0, 0, 0, 55.0F * f4), n, Math.min(1.0F, accent));
      Draw.rect(f + 0.5F, f2 + 0.5F, f3 - 1.0F, 17.0F, n2, 4.0F);
      Draw.outline(f, f2, f3, 18.0F, 0.5F,
            ColorUtil.interpolateColor(rgba(255, 255, 255, 30.0F * f4), ThemeManager.accentBright(90.0F * f4), Math.min(1.0F, accent)),
            4.0F);
      String string = this.backend.getName();
      float tw = FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F);
      FontsLV.MONTSERRAT_MEDIUM.draw(string, f + (f3 - tw) * 0.5F, f2 + 5.0F, 6.0F,
            rgba(255, 255, 255, (int) ((150.0F + 90.0F * accent) * f4)));
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
      if (f4 >= f && f4 <= f + f3 && f5 >= f2 && f5 <= f2 + this.height()) {
         this.backend.press();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public float preferredWidth() {
      return FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getName(), 6.0F) + 48.0F;
   }
}
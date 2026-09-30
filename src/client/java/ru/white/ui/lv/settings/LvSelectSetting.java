package ru.white.ui.lv.settings;

import java.awt.Color;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.AccentGradient;
import ru.white.ui.theme.ThemeManager;
import ru.white.ui.util.anim.Decelerate;
import ru.white.ui.util.anim.Direction;
import ru.white.utils.other.GuiSounds;
import ru.white.utils.render.Draw;

public class LvSelectSetting extends SettingWidget {
   private static final int MAX_VISIBLE = 7;
   private static final float ITEM_H = 12.0F;
   private static final float PAD = 3.0F;
   private static final float TEXT_SIZE = 5.5F;
   private static final float EDGE_FADE = 4.0F;
   private static final float EDGE_INSET = 1.0F;
   private final ModeSetting backend;
   private boolean open;
   private final Decelerate dropAnim;
   private float scroll;
   private float scrollTarget;
   private long lastScrollNs;

   public LvSelectSetting(ModeSetting modeSetting) {
      this.backend = modeSetting;
      this.dropAnim = new Decelerate();
      this.dropAnim.setMs(200);
      this.dropAnim.setValue(0.0);
      this.dropAnim.setDirection(Direction.BACKWARDS);
   }

   @Override
   public String name() {
      return this.backend.getName();
   }

   private static int clamp(int n, int n2, int n3) {
      return n < n2 ? n2 : (n > n3 ? n3 : n);
   }

   private static float clamp(float f, float f2, float f3) {
      return f < f2 ? f2 : (f > f3 ? f3 : f);
   }

   @Override
   public float height() {
      return 16.0F;
   }

   @Override
   public void renderOverlay(DrawContext drawContext, float f, float f2, float f3, float f4) {
      float f9 = this.dropAnim.getOutput().floatValue();
      if (!(f9 <= 0.01F)) {
         float f10 = f9 * f4;
         List<String> list = this.backend.values;
         float f11 = this.dropWidth();
         float f12 = f + f3 - f11 - 4.0F;
         float f13 = f2 + 16.0F + 1.0F;
         int n = Math.min(list.size(), 7);
         float f14 = n * 12.0F + 6.0F;
         String string = this.backend.getValue();
         boolean bl = list.size() > 7;
         this.updateScroll();
         LvRenderHelper.drawDropBackground(f12, f13, f11, f14, f10);
         float f15 = f13 + 1.0F;
         float f16 = f13 + f14 - 1.0F;
         for (int i = 0; i < list.size(); i++) {
            float f8 = f13 + 3.0F + i * 12.0F - this.scroll;
            float f7 = f8 + 2.0F;
            float f6 = f8 + 12.0F - 2.0F;
            if (!(f6 <= f15) && !(f7 >= f16)) {
               float f5 = bl ? Math.min(edgeAlpha(f7, f15, f16), edgeAlpha(f6, f15, f16)) : 1.0F;
               if (!(f5 <= 0.01F)) {
                  boolean bl2 = list.get(i).equals(string);
                  int n2 = bl2 ? ThemeManager.accentSoft(230.0F * f10 * f5) : new Color(255, 255, 255, (int)(140.0F * f10 * f5)).getRGB();
                  FontsLV.MONTSERRAT_MEDIUM.draw(list.get(i), f12 + 6.0F, f8 + 2.5F, 5.5F, n2);
                  if (bl2) {
                     AccentGradient.fillVertical(f12 + f11 - (bl ? 8 : 6), f8 + 6.0F - 1.0F, 2.0F, 2.0F, 1.0F, 200.0F * f10 * f5);
                  }
               }
            }
         }
         if (bl) {
            float f18 = this.maxScroll();
            float f8 = f12 + f11 - 3.0F;
            float f7 = f13 + 2.0F;
            float f6 = f14 - 4.0F;
            Draw.rect(f8, f7, 1.6F, f6, new Color(255, 255, 255, (int)(28.0F * f10)).getRGB(), 0.8F);
            float f5 = Math.max(10.0F, f6 * 7.0F / list.size());
            float f19 = f7 + (f18 <= 0.0F ? 0.0F : this.scroll / f18 * (f6 - f5));
            Draw.rect(f8, f19, 1.6F, f5, ThemeManager.accentSoft(190.0F * f10), 0.8F);
         }
      }
   }

   @Override
   public void render(float f, float f2, float f3, float f4) {
      String string = this.backend.getValue();
      float f5 = FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F) + 10.0F;
      float f6 = f + f3 - f5 - 4.0F;
      float f7 = f2 + 2.0F;
      LvRenderHelper.drawName(this.backend.getName(), f, f2, f6 - (f + 6.0F) - 4.0F, f4);
      LvRenderHelper.drawBtn(f6, f7, f5, 12.0F, string, f4);
   }

   @Override
   public boolean isVisible() {
      return this.backend.getVisible().get();
   }

   @Override
   public boolean click(float f, float f2, float f3, float f4, float f5) {
      String string = this.backend.getValue();
      float f6 = FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F) + 10.0F;
      float f7 = f + f3 - f6 - 4.0F;
      float f8 = f2 + 2.0F;
      if (f4 >= f7 && f4 <= f7 + f6 && f5 >= f8 && f5 <= f8 + 12.0F) {
          this.open = !this.open;
          GuiSounds.expand(this.open);
          if (this.open) {
             this.dropAnim.setValue(1.0);
             this.dropAnim.setDirection(Direction.FORWARDS);
             this.dropAnim.counter.resetCounter();
             this.initScrollToSelected();
          } else {
             this.dropAnim.setDirection(Direction.BACKWARDS);
             this.dropAnim.counter.resetCounter();
          }
         return true;
      } else {
         return false;
      }
   }

   private void updateScroll() {
      long l = System.nanoTime();
      float f = this.lastScrollNs == 0L ? 0.0F : Math.min(0.05F, (float)(l - this.lastScrollNs) / 1.0E9F);
      this.lastScrollNs = l;
      float f2 = this.maxScroll();
      this.scrollTarget = clamp(this.scrollTarget, 0.0F, f2);
      this.scroll = this.scroll + (this.scrollTarget - this.scroll) * (1.0F - (float)Math.exp(-f * 18.0F));
      if (Math.abs(this.scrollTarget - this.scroll) < 0.05F) {
         this.scroll = this.scrollTarget;
      }
      this.scroll = clamp(this.scroll, 0.0F, f2);
   }

   private void initScrollToSelected() {
      List<String> list = this.backend.values;
      int n = Math.max(0, list.indexOf(this.backend.getValue()));
      int n2 = Math.max(0, list.size() - 7);
      int n3 = clamp(n - 3, 0, n2);
      this.scrollTarget = this.scroll = n3 * 12.0F;
      this.lastScrollNs = 0L;
   }

   private float dropWidth() {
      float f = 0.0F;
      for (String string : this.backend.values) {
         f = Math.max(f, FontsLV.MONTSERRAT_MEDIUM.width(string, 5.5F));
      }
      float f2 = f + 16.0F;
      return this.backend.values.size() > 7 ? f2 + 5.0F : f2;
   }

   @Override
   public void closeOverlay() {
      if (this.open) {
         this.open = false;
         GuiSounds.expand(false);
         this.dropAnim.setDirection(Direction.BACKWARDS);
         this.dropAnim.counter.resetCounter();
      }
   }

   @Override
   public boolean isOverlayOpen() {
      return this.open;
   }

   @Override
   public boolean scrollOverlay(float f, float f2, float f3, float f4, float f5, double d) {
      if (!this.open) {
         return false;
      } else {
         float f6 = this.dropWidth();
         float f7 = f + f3 - f6 - 4.0F;
         float f8 = f2 + 16.0F + 1.0F;
         int n = Math.min(this.backend.values.size(), 7);
         float f9 = n * 12.0F + 6.0F;
         if (!(f4 < f7) && !(f4 > f7 + f6) && !(f5 < f8) && !(f5 > f8 + f9)) {
            float f10 = this.maxScroll();
            if (f10 > 0.0F) {
               this.scrollTarget = clamp(this.scrollTarget - (float)d * 12.0F, 0.0F, f10);
            }
            return true;
         } else {
            return false;
         }
      }
   }

   @Override
   public boolean clickOverlay(float f, float f2, float f3, float f4, float f5) {
      float f6 = this.dropAnim.getOutput().floatValue();
      if (this.open && !(f6 <= 0.1F)) {
         List<String> list = this.backend.values;
         float f7 = this.dropWidth();
         float f8 = f + f3 - f7 - 4.0F;
         float f9 = f2 + 16.0F + 1.0F;
         int n = Math.min(list.size(), 7);
         float f10 = n * 12.0F + 6.0F;
         if (!(f4 < f8) && !(f4 > f8 + f7) && !(f5 < f9 + 3.0F) && !(f5 > f9 + f10 - 3.0F)) {
            int n2 = (int)Math.floor((f5 - (f9 + 3.0F) + this.scroll) / 12.0F);
            if (n2 >= 0 && n2 < list.size()) {
               this.backend.set(list.get(n2));
               this.closeOverlay();
            }
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static float edgeAlpha(float f, float f2, float f3) {
      float f4 = clamp((f - f2) / 4.0F, 0.0F, 1.0F);
      return Math.min(f4, clamp((f3 - f) / 4.0F, 0.0F, 1.0F));
   }

   @Override
   public boolean hasOverlay() {
      return this.dropAnim.getOutput().floatValue() > 0.01F;
   }

   private float maxScroll() {
      return Math.max(0, this.backend.values.size() - 7) * 12.0F;
   }

   @Override
   public float preferredWidth() {
      return 6.0F
         + FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getName(), 6.5F)
         + 6.0F
         + FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getValue(), 6.0F)
         + 10.0F
         + 8.0F;
   }
}
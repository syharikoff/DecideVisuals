package ru.white.ui.lv;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.gui.DrawContext;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.Theme;
import ru.white.ui.theme.ThemeManager;
import ru.white.ui.theme.AccentGradient;
import ru.white.ui.util.Position;
import ru.white.ui.util.anim.Decelerate;
import ru.white.ui.util.anim.Direction;
import ru.white.utils.other.GuiSounds;
import ru.white.utils.render.Draw;
import ru.white.utils.render.Scissor;

public final class LvThemesRenderer {
   private static final float ROW_FADE_MS = 380;
   private static final float SLIDE_PX = 6.0F;
   private static final float CARD_H = 35.0F;
   private static final float CONTENT_Y_OFFSET = 5.0F;
   private static final float CONTENT_HEIGHT = 280.0F;
   private final Map<Theme, Decelerate> selectAnims = new EnumMap<>(Theme.class);
   private final Map<Theme, Float> hoverAnims = new EnumMap<>(Theme.class);
   private final Map<Integer, Decelerate> rowAppearAnims = new HashMap<>();
   private int appearFadeMs = 320;
   private long appearBaseMs;
   private boolean appearInitialFrame;
   private boolean transitioning = false;
   private float fadeOutTime = 0.0F;
   private float scroll;
   private float scrollTarget;
   private float contentH;
   private boolean appearComposite;

   public LvThemesRenderer() {
   }

   private static float clamp(float f, float f2, float f3) {
      return Math.max(f2, Math.min(f3, f));
   }

   public void open() {
      this.open(false);
   }

   public void open(boolean bl) {
      this.rowAppearAnims.clear();
      this.scroll = 0.0F;
      this.scrollTarget = 0.0F;
      this.appearFadeMs = bl ? 320 : 380;
      this.appearBaseMs = System.currentTimeMillis();
      this.appearInitialFrame = true;
   }

   public void render(DrawContext m, float x, float y, float panelW, float f14, float f15, float dt) {
      float f7 = 1.0F - (float)Math.exp(-dt * 14.0F);
      this.scroll = this.scroll + (this.scrollTarget - this.scroll) * f7;
      if (Math.abs(this.scrollTarget - this.scroll) < 0.05F) {
         this.scroll = this.scrollTarget;
      }

      this.renderCards(m, x, y, panelW, f14, f15, dt, true);
      this.appearInitialFrame = false;
   }

   public boolean click(float x, float y, float panelW, float mx, float my) {
      if (this.transitioning) {
         return false;
      }
      float f6 = x + 117.0F;
      float f7 = y + 5.0F;
      float f8 = panelW - 122.0F;
      float f9 = 3.0F;
      float f10 = 4.0F;
      float f11 = (f8 - f9 - f10 * 2.0F) * 0.5F;
      float f12 = f6 + f10;
      float f13 = f12 + f11 + f9;
      if (!(mx < f6) && !(mx > f6 + f8) && !(my < f7) && !(my > f7 + 280.0F)) {
         Theme[] themes = Theme.values();
         for (int i = 0; i < themes.length; i++) {
            int n = i % 2;
            int n2 = i / 2;
            float fx = n == 0 ? f12 : f13;
            float fy = f7 + f10 + n2 * (35.0F + f9) - this.scroll;
            if (mx >= fx && mx <= fx + f11 && my >= fy && my <= fy + 35.0F) {
               ThemeManager.set(themes[i]);
               GuiSounds.theme(i, themes.length);
               return true;
            }
         }
         return false;
      } else {
         return false;
      }
   }

   private static Decelerate createAnim(int n) {
      Decelerate decelerate = new Decelerate().setMs(n).setValue(1.0);
      decelerate.setDirection(Direction.BACKWARDS);
      decelerate.counter.setTime(System.currentTimeMillis() - 10000L);
      return decelerate;
   }

   public void scroll(float amount, float h) {
      float f2 = Math.max(0.0F, this.contentH - h + 8.0F);
      this.scrollTarget = clamp(this.scrollTarget - amount * 18.0F, 0.0F, f2);
   }

   public void resetCardBlur() {
   }

   public void setAppearComposite(boolean b) {
      this.appearComposite = b;
   }

   public void finishTransition() {
      this.transitioning = false;
      this.fadeOutTime = 0.0F;
   }

   public void resetScroll() {
      this.scroll = 0.0F;
      this.scrollTarget = 0.0F;
   }

   private boolean hasAppearWork() {
      if (this.appearInitialFrame) {
         return true;
      } else if (Math.abs(this.scrollTarget - this.scroll) > 0.05F) {
         return true;
      } else {
         for (Decelerate decelerate : this.rowAppearAnims.values()) {
            if (decelerate.getOutput().floatValue() < 0.999F) {
               return true;
            }
         }
         return false;
      }
   }

   private void renderScrollBar(float fx, float fy, float fw, float fh, float f5, float alpha) {
      float f7 = 3.0F;
      float f8 = fx + fw + 0.25F;
      float f9 = fy + f7;
      float f10 = fh - f7 * 2.0F;
      float f11 = clamp(fh / Math.max(this.contentH, fh), 0.0F, 1.0F);
      float f12 = clamp(f10 * f11, 12.0F, f10);
      float f13 = Math.max(0.0F, f10 - f12);
      float f14 = clamp(this.scroll / Math.max(f5, 1.0F), 0.0F, 1.0F);
      float f15 = f9 + f13 * f14;
      Draw.rect(f8, f9, 1.25F, f10, ThemeManager.rgba(16777215, 18.0F * alpha));
      AccentGradient.fillVertical(f8, f15, 1.25F, f12, 1.0F, 165.0F * alpha);
   }

   private void renderCards(DrawContext m, float px, float py, float panelW, float alpha, float f15, float dt, boolean appear) {
      float f7 = px + 117.0F;
      float f8 = py + 5.0F;
      float f9 = panelW - 122.0F;
      float f10 = 3.0F;
      float f11 = 4.0F;
      float f12 = 4.0F;
      float f13 = (f9 - f10 - f11 * 2.0F) * 0.5F;
      float f14 = f7 + f11;
      float f15b = f14 + f13 + f10;
      float f16 = 280.0F;
      Scissor.enable(f7, f8, f9, f16, 2.0F);
      float mouseX = Position.mouseX();
      float mouseY = Position.mouseY();
      float f19 = (1.0F - alpha) * 8.0F;
      boolean hovered = mouseX >= f7 && mouseX <= f7 + f9 && mouseY >= f8 && mouseY <= f8 + f16;
      float f20 = 1.0F - (float)Math.exp(-dt * 16.0F);
      Theme[] themes = Theme.values();

      for (int n = 0; n < themes.length; n++) {
         Theme theme = themes[n];
         int col = n % 2;
         int row = n / 2;
         float fx = col == 0 ? f14 : f15b;
         float fy = f8 + f11 + row * (35.0F + f10) + f19 - this.scroll;
         boolean hoverCard = hovered && mouseX >= fx && mouseX <= fx + f13 && mouseY >= fy && mouseY <= fy + 35.0F;
         float hov = this.hoverAnims.getOrDefault(theme, 0.0F);
         hov += ((hoverCard ? 1.0F : 0.0F) - hov) * f20;
         this.hoverAnims.put(theme, hov);
         if (!(fy + 35.0F < f8 - 5.0F) && !(fy > f8 + f16 + 5.0F)) {
            float rowApp = appear ? this.rowAppear(row) : 1.0F;
            if (rowApp >= 0.001F) {
               float f23 = Math.min(1.0F, rowApp / 0.6F);
               float f24 = f23 * f23;
               float f25 = rowApp < 0.6F ? 0.0F : (rowApp - 0.6F) / 0.4F;
               float f26 = 1.0F - f25 * f25 * (3.0F - 2.0F * f25);
               fy += (1.0F - f23) * 6.0F;
               float f30 = alpha * (rowApp < 0.999F ? f24 : 1.0F);
               if (rowApp < 0.999F) {
                  float f33 = 0.85F + 0.15F * f23;
                  float cx = fx + f13 * 0.5F;
                  float cy = fy + 17.5F;
                  m.getMatrices().pushMatrix();
m.getMatrices().translate(cx, cy);
                   m.getMatrices().scale(f33, f33);
                   m.getMatrices().translate(-cx, -cy);
               }

               Decelerate decelerate = this.selectAnims.computeIfAbsent(theme, t -> createAnim(220));
               decelerate.setDirection(theme == ThemeManager.current() ? Direction.FORWARDS : Direction.BACKWARDS);
               float f21 = decelerate.getOutput().floatValue();
               Draw.rect(fx, fy, f13, 35.0F, ThemeManager.rgba(0, (40.0F + 18.0F * hov) * f30), f12);
               if (hov > 0.01F) {
                  Draw.rect(fx, fy, f13, 35.0F, ThemeManager.rgba(16777215, 8.0F * hov * f30), f12);
               }

               float edge;
               if ((edge = (14.0F + 12.0F * hov) * (1.0F - f21) * f30) > 0.5F) {
                  Draw.outline(fx, fy, f13, 35.0F, 0.6F, ThemeManager.rgba(16777215, edge), f12);
               }

               if (f21 > 0.01F) {
                  int n5 = theme.gradientA();
                  int n6 = theme.gradientB();
                  int n7 = ThemeManager.mix(ThemeManager.rgba(n5, 255.0F), ThemeManager.rgba(n6, 255.0F), 0.5F) & 16777215;
                  Draw.gradientRect(fx + 2.0F, fy + 3.0F, 1.0F, 29.0F,
                     new int[]{ThemeManager.rgba(n5, 185.0F * f21 * f30), ThemeManager.rgba(n5, 185.0F * f21 * f30),
                        ThemeManager.rgba(n6, 185.0F * f21 * f30), ThemeManager.rgba(n6, 185.0F * f21 * f30)}, 6.0F);
                  Draw.outline(fx, fy, f13, 35.0F, 0.7F,
                     ThemeManager.rgba(n5, 110.0F * f21 * f30), f12);
               }

               float f34 = fx + 8.0F + f21 * 3.0F;
               FontsLV.MONTSERRAT_MEDIUM.draw(theme.displayName(), f34, fy + 6.0F, 7.0F, ThemeManager.rgba(16777215, (200.0F + 35.0F * hov + 20.0F * f21) * f30));
               if (f21 > 0.01F) {
                  float f35 = fx + f13 - 9.0F;
                  Draw.rect(f35, fy + 8.5F, 3.0F, 3.0F, ThemeManager.rgba(theme.accentBrightRgb(), 220.0F * f21 * f30), 1.5F);
               }

               int[] palette = theme.palette().length >= 2 ? theme.palette() : theme.shades();
               float f36 = 9.0F;
               float f37 = 3.0F;
               float f38 = fy + 35.0F - f36 - 7.0F;
               int n8 = Math.min(palette.length, 5);
               for (int i = 0; i < n8; i++) {
                  float f39 = fx + 8.0F + i * (f36 + f37);
                  Draw.rect(f39, f38, f36, f36, ThemeManager.rgba(palette[i], 235.0F * f30), 3.0F);
               }

               if (rowApp < 0.999F) {
                  m.getMatrices().popMatrix();
               }
            }
         }
      }

      int var54 = (themes.length + 1) / 2;
      this.contentH = var54 * (35.0F + f10) - f10;
      float f40 = Math.max(0.0F, this.contentH - f16 + f11 * 2.0F);
      this.scrollTarget = clamp(this.scrollTarget, 0.0F, f40);
      this.scroll = clamp(this.scroll, 0.0F, f40);
      Scissor.disable();
      this.renderScrollBar(f7, f8, f9, f16, f40, alpha);
   }

   private float rowAppear(int n) {
      Decelerate decelerate = this.rowAppearAnims.get(n);
      if (decelerate == null) {
         long l = this.appearInitialFrame ? this.appearBaseMs : System.currentTimeMillis();
         decelerate = new Decelerate().setMs(this.appearFadeMs).setValue(1.0);
         decelerate.counter.setTime(l);
         this.rowAppearAnims.put(n, decelerate);
      }
      float f = decelerate.getOutput().floatValue();
      return Math.max(0.0F, Math.min(1.0F, f));
   }
}

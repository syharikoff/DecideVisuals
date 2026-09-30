package ru.white.ui.lv.settings;

import java.awt.Color;
import net.minecraft.client.gui.DrawContext;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.ThemeManager;
import ru.white.ui.util.Position;
import ru.white.ui.util.anim.Decelerate;
import ru.white.ui.util.anim.Direction;
import ru.white.utils.other.GuiSounds;
import ru.white.utils.render.Draw;

public class LvColorSetting extends SettingWidget {
   private static final float PAD = 6.0F;
   private static final float SV_W = 70.0F;
   private static final float SV_H = 50.0F;
   private static final float BAR_H = 4.0F;
   private static final float GAP = 4.0F;
   private static final float OV_W = 82.0F;
   private static final float OV_H = 78.0F;
   private final ColorSetting backend;
   private boolean open;
   private final Decelerate anim;
   private float hue;
   private float sat;
   private float bright;
   private float alphaPct;
   private float dispHue;
   private float dispSat;
   private float dispVal;
   private float dispAlpha;
   private float svVelX;
   private float svVelY;
   private long lastNs = System.nanoTime();
   private int drag;
   private boolean dragSv;
   private boolean dragHue;
   private boolean dragAlpha;

   public LvColorSetting(ColorSetting colorSetting) {
      this.backend = colorSetting;
      this.anim = new Decelerate();
      this.anim.setMs(220);
      this.anim.setValue(1.0);
      this.anim.setDirection(Direction.BACKWARDS);
      this.anim.counter.setTime(System.currentTimeMillis() - 10000L);
      this.parseBackend();
   }

   private void parseBackend() {
      float[] fs = unpack(this.backend.getValue().intValue());
      this.hue = fs[0];
      this.sat = fs[1];
      this.bright = fs[2];
      this.alphaPct = fs[3];
   }

   private static float clamp01(float f) {
      return f < 0.0F ? 0.0F : (f > 1.0F ? 1.0F : f);
   }

   private static int clamp255(float f) {
      return Math.max(0, Math.min(255, Math.round(f)));
   }

   private static float[] unpack(int n) {
      float f = (float)((n >>> 24) & 255) / 255.0F;
      float f2 = (float)((n >> 16) & 255) / 255.0F;
      float f3 = (float)((n >> 8) & 255) / 255.0F;
      float f4 = (float)(n & 255) / 255.0F;
      float[] fs = Color.RGBtoHSB((int)f2, (int)f3, (int)f4, null);
      return new float[]{fs[0], fs[1], fs[2], f};
   }

   private int currentArgb() {
      int n = clamp255(this.alphaPct * 255.0F);
      return (n << 24) | (0xFFFFFF & Color.HSBtoRGB(this.hue, this.sat, this.bright));
   }

   @Override
   public String name() {
      return this.backend.getName();
   }

   @Override
   public float height() {
      return 16.0F;
   }

   @Override
   public void renderOverlay(DrawContext drawContext, float f, float f2, float f3, float f4) {
      float f5 = this.anim.getOutput().floatValue();
      if (f5 <= 0.01F) {
         return;
      }
      if (this.drag != 0) {
         this.updateDrag(Position.mouseX(), Position.mouseY(), f, f2, f3);
      }
      float f6 = f5 * f4;
      long l = System.nanoTime();
      float f7 = Math.min(0.05F, (float)(l - this.lastNs) / 1.0E9F);
      this.lastNs = l;
      float f8 = 1.0F - (float)Math.exp(-f7 * 16.0F);
      this.dispHue += (this.hue - this.dispHue) * f8;
      this.dispAlpha += (this.alphaPct - this.dispAlpha) * f8;
      float f9 = 200.0F;
      float f10 = 22.0F;
      this.svVelX += ((this.sat - this.dispSat) * f9 - this.svVelX * f10) * f7;
      this.svVelY += ((this.bright - this.dispVal) * f9 - this.svVelY * f10) * f7;
      this.dispSat += this.svVelX * f7;
      this.dispVal += this.svVelY * f7;
      float f12 = f + f3 - OV_W - 4.0F;
      float f13 = f2 + 16.0F + 1.0F;
      LvRenderHelper.drawDropBackground(f12, f13, OV_W, OV_H, f6);
      float f14 = f12 + 6.0F;
      float f15 = f13 + 6.0F;
      int n = Color.HSBtoRGB(this.hue, 1.0F, 1.0F);
      int n2 = n & 0xFFFFFF | (int)(255.0F * f6) << 24;
      int n3 = (int)(255.0F * f6) << 24 | 0xFFFFFF;
      int n4 = (int)(255.0F * f6) << 24;
      Draw.gradientRect(f14, f15, SV_W, SV_H, new int[]{n3, n2, n2, n3}, 2.0F);
      Draw.gradientRect(f14, f15, SV_W, SV_H, new int[]{n4, n4, 0xFF000000, 0xFF000000}, 2.0F);
      float f16 = f14 + SV_W * this.dispSat;
      float f17 = f15 + SV_H * (1.0F - this.dispVal);
      int n5 = (int)(220.0F * f6) << 24 | 0xFFFFFF;
      Draw.outline(f16 - 3.0F, f17 - 3.0F, 5.0F, 5.0F, 1.0F, n5, 3.0F);
      float f18 = f15 + SV_H + GAP;
      this.renderHueBar(f14, f18, SV_W, BAR_H, f6);
      Draw.rect(f14 + SV_W * this.dispHue - 1.0F, f18 - 1.0F, 2.0F, 6.0F, n5, 1.0F);
      float f19 = f18 + BAR_H + GAP;
      this.renderAlphaBar(f14, f19, SV_W, BAR_H, f6);
      Draw.rect(f14 + SV_W * this.dispAlpha - 1.0F, f19 - 1.0F, 2.0F, 6.0F, n5, 1.0F);
   }

   @Override
   public void render(float f, float f2, float f3, float f4) {
      if (this.drag == 0) {
         this.parseBackend();
      }
      int n = this.backend.getValue().intValue();
      String string = String.format("#%06X", n & 0xFFFFFF);
      float f5 = FontsLV.MONTSERRAT_MEDIUM.width(string, 5.5F);
      float f6 = 8.0F;
      float f7 = f6 + 4.0F + f5 + 10.0F;
      float f8 = f + f3 - f7 - 4.0F;
      float f9 = f2 + 2.0F;
      LvRenderHelper.drawName(this.backend.getName(), f, f2, f8 - (f + 6.0F) - 4.0F, f4);
      LvRenderHelper.drawPanelBg(f8, f9, f7, 12.0F, 3.0F, f4);
      int n2 = n & 0xFFFFFF | (int)((float)(n >>> 24 & 0xFF) * f4) << 24;
      Draw.rect(f8 + 3.0F, f9 + (12.0F - f6) * 0.5F, f6, f6, n2, 2.0F);
      FontsLV.MONTSERRAT_MEDIUM.draw(string, f8 + 3.0F + f6 + 4.0F, f9 + 3.0F, 5.5F, ThemeManager.accentSoft(200.0F * f4));
   }

   @Override
   public boolean isVisible() {
      return this.backend.getVisible().get();
   }

   @Override
   public boolean click(float f, float f2, float f3, float f4, float f5) {
      int n = this.backend.getValue().intValue();
      String string = String.format("#%06X", n & 0xFFFFFF);
      float f6 = FontsLV.MONTSERRAT_MEDIUM.width(string, 5.5F);
      float f7 = 12.0F + f6 + 10.0F;
      float f8 = f + f3 - f7 - 4.0F;
      float f9 = f2 + 2.0F;
      if (f4 >= f8 && f4 <= f8 + f7 && f5 >= f9 && f5 <= f9 + 12.0F) {
         if (this.open) {
            this.closeOverlay();
         } else {
            this.openPicker();
         }
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void closeOverlay() {
      if (this.open) {
         this.open = false;
         this.drag = 0;
         this.dragSv = false;
         this.dragHue = false;
         this.dragAlpha = false;
         this.anim.setDirection(Direction.BACKWARDS);
         this.anim.counter.resetCounter();
      }
   }

   @Override
   public boolean isOverlayOpen() {
      return this.open;
   }

   @Override
   public void releaseDrag() {
      this.drag = 0;
      this.dragSv = false;
      this.dragHue = false;
      this.dragAlpha = false;
   }

   @Override
   public boolean clickOverlay(float f, float f2, float f3, float f4, float f5) {
      float f6 = this.anim.getOutput().floatValue();
      if (!this.open || f6 <= 0.1F) {
         return false;
      }
      float f7 = OV_W;
      float f8 = OV_H;
      float f9 = f + f3 - f7 - 4.0F;
      float f10 = f2 + 16.0F + 1.0F;
      if (f4 < f9 || f4 > f9 + f7 || f5 < f10 || f5 > f10 + f8) {
         return false;
      }
      float f11 = f9 + PAD;
      float f12 = f10 + PAD;
      float f13 = f12 + SV_H + GAP;
      float f14 = f13 + BAR_H + GAP;
      if (f4 >= f11 && f4 <= f11 + SV_W && f5 >= f12 && f5 <= f12 + SV_H) {
         this.sat = clamp01((f4 - f11) / SV_W);
         this.bright = clamp01(1.0F - (f5 - f12) / SV_H);
         this.drag = 1;
         this.dragSv = true;
         this.commit();
         return true;
      }
      if (f4 >= f11 && f4 <= f11 + SV_W && f5 >= f13 - 2.0F && f5 <= f13 + BAR_H + 2.0F) {
         this.hue = clamp01((f4 - f11) / SV_W);
         this.drag = 2;
         this.dragHue = true;
         this.commit();
         return true;
      }
      if (f4 >= f11 && f4 <= f11 + SV_W && f5 >= f14 - 2.0F && f5 <= f14 + BAR_H + 2.0F) {
         this.alphaPct = clamp01((f4 - f11) / SV_W);
         this.drag = 3;
         this.dragAlpha = true;
         this.commit();
         return true;
      }
      return true;
   }

   @Override
   public boolean hasOverlay() {
      return this.anim.getOutput().floatValue() > 0.01F;
   }

   private void updateDrag(float f, float f2, float f3, float f4, float f5) {
      float f6 = f3 + f5 - OV_W - 4.0F;
      float f7 = f4 + 16.0F + 1.0F;
      float f8 = f6 + PAD;
      float f9 = f7 + PAD;
      if (this.drag == 1) {
         this.sat = clamp01((f - f8) / SV_W);
         this.bright = clamp01(1.0F - (f2 - f9) / SV_H);
      } else if (this.drag == 2) {
         this.hue = clamp01((f - f8) / SV_W);
      } else if (this.drag == 3) {
         this.alphaPct = clamp01((f - f8) / SV_W);
      }
      this.commit();
   }

   private void openPicker() {
      this.open = true;
      int n = this.backend.getValue().intValue();
      float[] fArray = new float[3];
      Color.RGBtoHSB(n >>> 16 & 0xFF, n >>> 8 & 0xFF, n & 0xFF, fArray);
      this.hue = fArray[0];
      this.sat = fArray[1];
      this.bright = fArray[2];
      this.alphaPct = (float)(n >>> 24 & 0xFF) / 255.0F;
      this.dispHue = this.hue;
      this.dispSat = this.sat;
      this.dispVal = this.bright;
      this.dispAlpha = this.alphaPct;
      this.svVelX = 0.0F;
      this.svVelY = 0.0F;
      this.lastNs = System.nanoTime();
      GuiSounds.expand(true);
      this.anim.setDirection(Direction.FORWARDS);
      this.anim.counter.resetCounter();
   }

   private void renderHueBar(float f, float f2, float f3, float f4, float f5) {
      int n = 12;
      for (int i = 0; i < n; i++) {
         float f7 = (float)i / (float)n;
         float f8 = (float)(i + 1) / (float)n;
         int n2 = 0xFFFFFF & Color.HSBtoRGB((f7 + f8) * 0.5F, 1.0F, 1.0F);
         Draw.rect(f + i * (f3 / (float)n), f2, f3 / (float)n + 1.0F, f4, n2, 0.0F);
      }
   }

   private void renderAlphaBar(float f, float f2, float f3, float f4, float f5) {
      int n = 0xFFFFFF & Color.HSBtoRGB(this.hue, this.sat, this.bright);
      int n2 = 16;
      float f6 = f3 / (float)n2;
      for (int i = 0; i < n2; i++) {
         float f7 = (float)(i + 1) / (float)n2;
         int n3 = (clamp255(f7 * 255.0F) << 24) | n;
         Draw.rect(f + i * f6, f2, f6 + 0.5F, f4, n3, 0.0F);
      }
   }

   private void commit() {
      this.backend.set(this.currentArgb());
   }

   private static int rgba(int n, int n2, int n3, float f) {
      int n4 = Math.max(0, Math.min(255, Math.round(f)));
      return n4 <= 0 ? 0 : new Color(n, n2, n3, n4).getRGB();
   }

   @Override
   public float preferredWidth() {
      float f = FontsLV.MONTSERRAT_MEDIUM.width(String.format("#%06X", this.backend.getValue().intValue() & 0xFFFFFF), 5.5F);
      return 6.0F + FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getName(), 6.5F) + 6.0F + 8.0F + 4.0F + f + 10.0F + 8.0F;
   }
}
package ru.white.ui.lv.settings;

import java.awt.Color;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.DrawContext;
import ru.white.module.api.Module;
import ru.white.ui.lv.LvRectUtil;
import ru.white.ui.theme.AccentGradient;
import ru.white.ui.util.Position;
import ru.white.ui.util.anim.Decelerate;
import ru.white.ui.util.anim.Direction;
import ru.white.utils.render.Draw;
import ru.white.utils.render.Scissor;

public class LvSettingsPopup {
   private static final float MIN_WIDTH = 110.0F;
   private static final float MAX_WIDTH = 180.0F;
   private static final float RADIUS = 8.0F;
   private static final float SIDE_PAD = 5.0F;
   private static final float TOP_PAD = 7.0F;
   private static final float BOTTOM_PAD = 7.0F;
   private static final float EDGE = 4.0F;
   private final Decelerate anim = new Decelerate();
   private final ScrollBar scrollBar = new ScrollBar();
   private Module module;
   private boolean open;
   private float width = 180.0F;
   private float px;
   private float py;
   private float height;
   private float bodyViewH;
   private float anchorY;
   private float maxBodyH;
   private float scroll;
   private float scrollTarget;
   private boolean draggingMove;
   private final LvDragController drag = new LvDragController(0.0F, 0.0F);
   private long lastNs = System.nanoTime();
   private List<SettingWidget> widgets = Collections.emptyList();
   private final Set<String> widgetFailures = new HashSet<>();

   public LvSettingsPopup() {
      this.anim.setMs(220);
      this.anim.setValue(1.0);
      this.anim.setDirection(Direction.BACKWARDS);
      this.anim.counter.setTime(System.currentTimeMillis() - 10000L);
   }

   private static float clamp(float f, float f2, float f3) {
      return f < f2 ? f2 : Math.min(f, f3);
   }

   public boolean contains(float f, float f2) {
      return this.isVisible() && f >= this.px && f <= this.px + this.width && f2 >= this.py && f2 <= this.py + this.height;
   }

   public boolean isOpen() {
      return this.open;
   }

   public void close() {
      if (this.open) {
         this.open = false;
         this.draggingMove = false;
         this.drag.release();
         this.closeOverlays();
         this.anim.setDirection(Direction.BACKWARDS);
         this.anim.counter.resetCounter();
      }
   }

   public void render(DrawContext drawContext, float f) {
      if (this.module != null) {
         float f4 = this.anim.getOutput().floatValue();
         if (f4 <= 0.01F) {
            if (!this.open) {
               this.module = null;
               this.widgets = Collections.emptyList();
            }
         } else {
            float f5 = f4 * f;
            long l = System.nanoTime();
            float f6 = Math.min(0.1F, (float)(l - this.lastNs) / 1.0E9F);
            this.lastNs = l;
            if (this.draggingMove) {
               this.drag.tick(Position.mouseX(), Position.mouseY(), this.width, this.height, true);
            }
            this.px = this.drag.getRenderX();
            this.anchorY = this.drag.getRenderY();
            this.layout();
            this.scroll = this.scroll + (this.scrollTarget - this.scroll) * (1.0F - (float)Math.exp(-f6 * 16.0F));
            if (Math.abs(this.scrollTarget - this.scroll) < 0.05F) {
               this.scroll = this.scrollTarget;
            }

            float f7 = this.py + (1.0F - f4) * 4.0F;
            LvRectUtil.drawClientRect(this.px, f7, this.width, this.height, 8.0F, f5);
            Draw.outline(this.px, f7, this.width, this.height, 0.6F, rgba(255, 255, 255, 24.0F * f5), 8.0F);
            float f3 = f7 + 7.0F;
            Scissor.enable(this.px, f3, this.width, this.bodyViewH, 2.0F);
            float f9 = f3 - this.scroll;

            for (SettingWidget settingWidget : this.widgets) {
               if (settingWidget.isVisible()) {
                  float f10 = settingWidget.height();
                  if (f9 + f10 >= f3 - 6.0F && f9 <= f3 + this.bodyViewH + 6.0F) {
                     try {
                        settingWidget.render(this.px + 5.0F, f9, this.width - 10.0F, f5);
                     } catch (Throwable throwable) {
                        this.handleWidgetFailure(settingWidget, throwable);
                     }
                  }

                  f9 += f10 + 4.0F;
               }
            }

            Scissor.disable();
            float f11 = this.contentHeight();
            if (f11 > this.bodyViewH + 0.5F) {
               float f12 = this.scrollBar.render(this.px + this.width - 4.5F, f3, this.bodyViewH, this.bodyViewH, f11, this.scroll, f5);
               if (this.scrollBar.isDragging()) {
                  this.scroll = f12;
                  this.scrollTarget = f12;
               }
            }

            float f13 = f3 - this.scroll;

            for (SettingWidget settingWidget : this.widgets) {
               if (settingWidget.isVisible()) {
                  if (settingWidget.hasOverlay()) {
                     try {
                        settingWidget.renderOverlay(drawContext, this.px + 5.0F, f13, this.width - 10.0F, f5);
                     } catch (Throwable throwable2) {
                        this.handleWidgetFailure(settingWidget, throwable2);
                     }
                  }

                  f13 += settingWidget.height() + 4.0F;
               }
            }
         }
      }
   }

   private static int rgba(int n, int n2, int n3, float f) {
      int n4 = Math.max(0, Math.min(255, Math.round(f)));
      return n4 <= 0 ? 0 : new Color(n, n2, n3, n4).getRGB();
   }

   public boolean toggle(Module module, float f, float f2, float f3, float f4, float f5, float f6) {
      if (this.isOpenFor(module.getName())) {
         this.close();
         return false;
      } else {
         this.module = module;
         this.widgets = LvSettingsFactory.build(module);
         this.open = true;
         this.scroll = 0.0F;
         this.scrollTarget = 0.0F;
         this.draggingMove = false;
         this.maxBodyH = f6 - 7.0F - 7.0F - 8.0F;
         float f7 = 0.0F;

         for (SettingWidget settingWidget : this.widgets) {
            f7 = Math.max(f7, settingWidget.preferredWidth());
         }

         this.width = clamp(f7 + 10.0F + 4.0F, 110.0F, 180.0F);
         this.anchorY = f2;
         this.px = clamp(f + 2.5F, 4.0F, Position.screenWidth() - this.width - 4.0F);
         this.layout();
         this.anchorY = clamp(this.anchorY, 4.0F, Position.screenHeight() - this.height - 4.0F);
         this.layout();
         this.drag.release();
         this.drag.setTargetX(this.px);
         this.drag.setTargetY(this.anchorY);
         this.drag.syncToTarget();
         this.anim.setDirection(Direction.FORWARDS);
         this.anim.counter.resetCounter();
         return true;
      }
   }

   public boolean isVisible() {
      return this.module != null && this.anim.getOutput().floatValue() > 0.01F;
   }

   private void layout() {
      float f = this.contentHeight();
      this.bodyViewH = Math.min(f, this.maxBodyH);
      this.height = 7.0F + this.bodyViewH + 7.0F;
      this.py = this.anchorY;
      float f2 = Math.max(0.0F, f - this.bodyViewH);
      this.scrollTarget = clamp(this.scrollTarget, 0.0F, f2);
      this.scroll = clamp(this.scroll, 0.0F, f2);
   }

   public boolean click(float f, float f2) {
      if (this.open && this.module != null) {
         boolean bl = false;
         float f3 = this.bodyY() - this.scroll;

         for (SettingWidget settingWidget : this.widgets) {
            if (settingWidget.isVisible()) {
               if (settingWidget.isOverlayOpen()) {
                  bl = true;
                  if (settingWidget.clickOverlay(this.px + 5.0F, f3, this.width - 10.0F, f, f2)) {
                     return true;
                  }
               }

               f3 += settingWidget.height() + 4.0F;
            }
         }

         if (bl) {
            this.closeOverlays();
            return true;
         } else if (!this.contains(f, f2)) {
            this.close();
            return true;
         } else if (this.scrollBar.tryGrab(f, f2)) {
            return true;
         } else {
            if (f2 >= this.py + 7.0F) {
               float f4 = this.bodyY() - this.scroll;

               for (SettingWidget settingWidget : this.widgets) {
                  if (settingWidget.isVisible()) {
                     float f5 = settingWidget.height();
                     if (f2 >= f4 && f2 < f4 + f5) {
                        if (!(settingWidget instanceof LvSeparatorSetting)) {
                           boolean bl2 = settingWidget.click(this.px + 5.0F, f4, this.width - 10.0F, f, f2);
                           if (bl2 && settingWidget.isOverlayOpen()) {
                              this.closeOtherOverlays(settingWidget);
                           }

                           return true;
                        }
                        break;
                     }

                     f4 += f5 + 4.0F;
                  }
               }
            }

            this.beginDrag(f, f2);
            return true;
         }
      } else {
         return false;
      }
   }

   public List<? extends SettingWidget> widgets() {
      return this.open ? this.widgets : Collections.emptyList();
   }

   public boolean isOpenFor(String string) {
      return this.open && this.module != null && string != null && this.module.getName().equals(string);
   }

   public float blurPhase() {
      if (this.module == null) {
         return 0.0F;
      } else {
         float f = this.anim.getOutput().floatValue();
         return f <= 0.01F ? 0.0F : clamp(1.0F - f, 0.0F, 1.0F);
      }
   }

   public float shareX() {
      return this.px;
   }

   public float shareY() {
      return this.py;
   }

   public boolean scroll(float f, float f2, double d) {
      if (this.open && this.module != null) {
         float f3 = this.bodyY() - this.scroll;

         for (SettingWidget settingWidget : this.widgets) {
            if (settingWidget.isVisible()) {
               if (settingWidget.isOverlayOpen() && settingWidget.scrollOverlay(this.px + 5.0F, f3, this.width - 10.0F, f, f2, d)) {
                  return true;
               }

               f3 += settingWidget.height() + 4.0F;
            }
         }

         if (this.contains(f, f2)) {
            float f4 = Math.max(0.0F, this.contentHeight() - this.bodyViewH);
            this.scrollTarget = clamp(this.scrollTarget - (float)d * 12.0F, 0.0F, f4);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public String shareModuleName() {
      return this.open && this.module != null ? this.module.getName() : "";
   }

   public void releaseDrags() {
      this.draggingMove = false;
      this.drag.release();
      this.scrollBar.release();

      for (SettingWidget settingWidget : this.widgets) {
         settingWidget.releaseDrag();
      }
   }

   public boolean writeBlurRect(float[] fArray, int n) {
      if (this.module != null && fArray != null && n + 6 <= fArray.length) {
         float f = this.anim.getOutput().floatValue();
         if (f <= 0.01F) {
            return false;
         } else {
            fArray[n] = this.px;
            fArray[n + 1] = this.py + (1.0F - f) * 4.0F;
            fArray[n + 2] = this.width;
            fArray[n + 3] = this.height;
            fArray[n + 4] = 1.0F;
            fArray[n + 5] = clamp(1.0F - f, 0.0F, 1.0F);
            return true;
         }
      } else {
         return false;
      }
   }

   public Module shareModule() {
      return this.open ? this.module : null;
   }

   public float shareScroll() {
      return this.scroll;
   }

   public boolean hasOpenOverlay() {
      for (SettingWidget settingWidget : this.widgets) {
         if (settingWidget.isOverlayOpen()) {
            return true;
         }
      }

      return false;
   }

   public boolean middleClick(float f, float f2) {
      if (this.open && this.module != null && this.contains(f, f2)) {
         float f3 = this.bodyY() - this.scroll;

         for (SettingWidget settingWidget : this.widgets) {
            if (settingWidget.isVisible()) {
               float f4 = settingWidget.height();
               if (f2 >= f3 && f2 < f3 + f4) {
                  settingWidget.middleClick(this.px + 5.0F, f3, this.width - 10.0F, f, f2);
                  return true;
               }

               f3 += f4 + 4.0F;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public void closeOverlays() {
      for (SettingWidget settingWidget : this.widgets) {
         settingWidget.closeOverlay();
      }
   }

   private void closeOtherOverlays(SettingWidget settingWidget) {
      for (SettingWidget settingWidget2 : this.widgets) {
         if (settingWidget2 != settingWidget) {
            settingWidget2.closeOverlay();
         }
      }
   }

   private void handleWidgetFailure(SettingWidget settingWidget, Throwable throwable) {
      try {
         Draw.flush();
      } catch (Throwable throwable2) {
      }

      this.widgetFailures.add(settingWidget.getClass().getName());
   }

   private float contentHeight() {
      float f = 0.0F;
      int n = 0;

      for (SettingWidget settingWidget : this.widgets) {
         if (settingWidget.isVisible()) {
            f += settingWidget.height() + 4.0F;
            n++;
         }
      }

      return n == 0 ? 0.0F : f - 4.0F;
   }

   private void beginDrag(float f, float f2) {
      this.drag.setTargetX(this.px);
      this.drag.setTargetY(this.anchorY);
      this.draggingMove = this.drag.tryGrab(f, f2, this.width, this.height);
   }

   private float bodyY() {
      return this.py + 7.0F;
   }

   private static final class ScrollBar {
      private static final float BAR_W = 1.5F;
      private static final float GRAB_PAD_X = 4.0F;
      private static final float GRAB_PAD_Y = 2.0F;
      private static final float ANIM_RATE = 16.0F;
      private static final float VERTICAL_SHRINK = 1.0F;
      private boolean dragging;
      private float grabOffset;
      private float hoverT;
      private float dragT;
      private long lastNs = System.nanoTime();
      private boolean visible;
      private float gTrackX;
      private float gTrackY;
      private float gTrackH;
      private float gThumbY;
      private float gThumbH;
      private float gHitW;

      private ScrollBar() {
      }

      private static float clamp(float f, float f2, float f3) {
         return Math.max(f2, Math.min(f3, f));
      }

      void release() {
         this.dragging = false;
      }

      float render(float f, float f2, float f3, float f4, float f5, float f6, float f7) {
         long l = System.nanoTime();
         float f9 = Math.min(0.1F, (float)(l - this.lastNs) / 1.0E9F);
         this.lastNs = l;
         float f10 = Math.max(0.0F, f5 - f4);
         boolean bl = this.visible = f10 > 0.5F;
         if (!this.visible) {
            this.dragging = false;
            this.hoverT = this.hoverT + (0.0F - this.hoverT) * this.follow(f9);
            this.dragT = this.dragT + (0.0F - this.dragT) * this.follow(f9);
            return f6;
         } else {
            float f11 = f3 / 1.0F;
            float f12 = f2 + (f3 - f11) * 0.5F;
            float f13 = Math.max(12.0F, f11 * (f4 / f5));
            float f14 = Math.max(1.0F, f11 - f13);
            if (this.dragging) {
               float f8 = clamp(Position.mouseY() - this.grabOffset, f12, f12 + f14);
               f6 = (f8 - f12) / f14 * f10;
            }

            f6 = clamp(f6, 0.0F, f10);
            float f8 = f12 + f14 * (f6 / f10);
            float f15 = Position.mouseX();
            float f16 = Position.mouseY();
            boolean bl2 = f15 >= f - 4.0F && f15 <= f + 1.5F + 4.0F && f16 >= f8 - 2.0F && f16 <= f8 + f13 + 2.0F;
            this.hoverT = this.hoverT + ((bl2 ? 1.0F : 0.0F) - this.hoverT) * this.follow(f9);
            this.dragT = this.dragT + ((this.dragging ? 1.0F : 0.0F) - this.dragT) * this.follow(f9);
            float f17 = Math.max(this.hoverT, this.dragT);
            int n = clamp255((20.0F + 18.0F * f17) * f7);
            Draw.rect(f, f12, 1.5F, f11, rgba(255, 255, 255, n), 0.75F);
            float f18 = (160.0F + 95.0F * f17) * f7;
            AccentGradient.fillVertical(f, f8, 1.5F, f13, 0.75F, f18);
            this.gTrackX = f;
            this.gTrackY = f12;
            this.gTrackH = f11;
            this.gThumbY = f8;
            this.gThumbH = f13;
            this.gHitW = 1.5F;
            return f6;
         }
      }

      boolean isVisible() {
         return this.visible;
      }

      boolean isDragging() {
         return this.dragging;
      }

      boolean tryGrab(float f, float f2) {
         if (!this.visible) {
            return false;
         } else if (f < this.gTrackX - 4.0F || f > this.gTrackX + this.gHitW + 4.0F) {
            return false;
         } else if (f2 >= this.gThumbY - 2.0F && f2 <= this.gThumbY + this.gThumbH + 2.0F) {
            this.dragging = true;
            this.grabOffset = f2 - this.gThumbY;
            return true;
         } else if (f2 >= this.gTrackY && f2 <= this.gTrackY + this.gTrackH) {
            this.dragging = true;
            this.grabOffset = this.gThumbH * 0.5F;
            return true;
         } else {
            return false;
         }
      }

      private static int clamp255(float f) {
         return Math.max(0, Math.min(255, Math.round(f)));
      }

      private static int rgba(int n, int n2, int n3, float f) {
         int n4 = Math.max(0, Math.min(255, Math.round(f)));
         return n4 <= 0 ? 0 : new Color(n, n2, n3, n4).getRGB();
      }

      private float follow(float f) {
         return 1.0F - (float)Math.exp(-f * 16.0F);
      }
   }
}
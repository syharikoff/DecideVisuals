package ru.white.ui.lv;

import ru.white.ui.util.Position;
import ru.white.ui.theme.AccentGradient;
import ru.white.ui.theme.ThemeManager;
import ru.white.utils.render.Draw;

public final class LvScrollBar {
   private static final float BAR_W = 1.5F;
   private static final float ANIM_RATE = 16.0F;
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

   public LvScrollBar() {
   }

   public void release() {
      this.dragging = false;
   }

   public float render(float x, float y, float trackH, float viewH, float contentH, float scroll, float alpha) {
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (float)(now - this.lastNs) / 1.0E9F);
      this.lastNs = now;
      float maxScroll = Math.max(0.0F, contentH - viewH);
      this.visible = maxScroll > 0.5F;
      if (!this.visible) {
         this.dragging = false;
         this.hoverT += (0.0F - this.hoverT) * this.follow(dt);
         this.dragT += (0.0F - this.dragT) * this.follow(dt);
         return scroll;
      }
      float thumbH = Math.max(12.0F, trackH * (viewH / contentH));
      float scrollable = Math.max(1.0F, trackH - thumbH);
      if (this.dragging) {
         float raw = clamp(Position.mouseY() - this.grabOffset, y, y + scrollable);
         scroll = (raw - y) / scrollable * maxScroll;
      }
      scroll = clamp(scroll, 0.0F, maxScroll);
      float thumbY = y + scrollable * (scroll / maxScroll);
      float mx = Position.mouseX();
      float my = Position.mouseY();
      boolean hover = mx >= x - 4.0F && mx <= x + BAR_W + 4.0F && my >= thumbY - 2.0F && my <= thumbY + thumbH + 2.0F;
      this.hoverT += ((hover ? 1.0F : 0.0F) - this.hoverT) * this.follow(dt);
      this.dragT += ((this.dragging ? 1.0F : 0.0F) - this.dragT) * this.follow(dt);
      float ht = Math.max(this.hoverT, this.dragT);
      int trackAlpha = clamp255((20.0F + 18.0F * ht) * alpha);
      Draw.rect(x, y, BAR_W, trackH, ThemeManager.rgba(16777215, trackAlpha), 0.75F);
      float fillAlpha = (160.0F + 95.0F * ht) * Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F;
      AccentGradient.fillVertical(x, thumbY, BAR_W, thumbH, 0.75F, fillAlpha);
      this.gTrackX = x;
      this.gTrackY = y;
      this.gTrackH = trackH;
      this.gThumbY = thumbY;
      this.gThumbH = thumbH;
      this.gHitW = BAR_W;
      return scroll;
   }

   public boolean isVisible() {
      return this.visible;
   }

   public boolean isDragging() {
      return this.dragging;
   }

   public boolean tryGrab(float mx, float my) {
      if (!this.visible) {
         return false;
      }
      if (mx < this.gTrackX - 4.0F || mx > this.gTrackX + this.gHitW + 4.0F) {
         return false;
      }
      if (my >= this.gThumbY - 2.0F && my <= this.gThumbY + this.gThumbH + 2.0F) {
         this.dragging = true;
         this.grabOffset = my - this.gThumbY;
         return true;
      }
      if (my >= this.gTrackY && my <= this.gTrackY + this.gTrackH) {
         this.dragging = true;
         this.grabOffset = this.gThumbH * 0.5F;
         return true;
      }
      return false;
   }

   private static float clamp(float v, float min, float max) {
      return Math.max(min, Math.min(max, v));
   }

   private static int clamp255(float f) {
      return Math.max(0, Math.min(255, Math.round(f)));
   }

   private float follow(float dt) {
      return 1.0F - (float)Math.exp(-dt * 16.0F);
   }
}

package ru.white.ui.lv.settings;

import net.minecraft.client.gui.DrawContext;
import ru.white.ui.util.Position;

public final class LvDragController {
   private float xPos;
   private float yPos;
   private boolean dragging;
   private float startX;
   private float startY;

   public LvDragController(float f, float f2) {
      this.xPos = f;
      this.yPos = f2;
   }

   public void release() {
      this.dragging = false;
   }

   public void syncToTarget() {
   }

   public void setTargetX(float f) {
      this.xPos = f;
   }

   public void setTargetY(float f) {
      this.yPos = f;
   }

   public boolean tryGrab(float f, float f2, float f3, float f4) {
      if (this.isHovered(f, f2, f3, f4)) {
         this.dragging = true;
         this.startX = f - this.xPos;
         this.startY = f2 - this.yPos;
         return true;
      } else {
         return false;
      }
   }

   public void tick(float f, float f2, float f3, float f4, boolean bl) {
      if (this.dragging) {
         this.xPos = f - this.startX;
         this.yPos = f2 - this.startY;
         this.xPos = Math.max(0.0F, Math.min(Position.screenWidth() - f3, this.xPos));
         this.yPos = Math.max(0.0F, Math.min(Position.screenHeight() - f4, this.yPos));
      }
   }

   public void updateTilt(float f, boolean bl) {
   }

   public float getTiltAngle() {
      return 0.0F;
   }

   public void renderOverlay(DrawContext drawContext, float f, float f2, float f3, float f4) {
   }

   public boolean isDragging() {
      return this.dragging;
   }

   public float getTargetX() {
      return this.xPos;
   }

   public float getTargetY() {
      return this.yPos;
   }

   public float getRenderX() {
      return this.xPos;
   }

   public float getRenderY() {
      return this.yPos;
   }

   private boolean isHovered(float f, float f2, float f3, float f4) {
      return f > this.xPos && f < this.xPos + f3 && f2 > this.yPos && f2 < this.yPos + f4;
   }
}
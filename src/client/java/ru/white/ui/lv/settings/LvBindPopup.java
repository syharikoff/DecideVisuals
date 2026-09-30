package ru.white.ui.lv.settings;

import java.awt.Color;
import net.minecraft.client.gui.DrawContext;
import ru.white.module.api.Module;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.util.Position;
import ru.white.ui.util.anim.Decelerate;
import ru.white.ui.util.anim.Direction;
import ru.white.utils.math.Keyboard;

public class LvBindPopup {
   private static final float WIDTH = 96.0F;
   private static final float PAD = 6.0F;
   private static final float ROW_H = 13.0F;
   private static final float HEIGHT = 25.0F;
   private final Decelerate anim = new Decelerate();
   private Module module;
   private boolean open;
   private boolean listening;
   private float px;
   private float py;
   private long lastFrameMs = System.currentTimeMillis();
   private boolean dragging;
   private final LvDragController drag = new LvDragController(0.0F, 0.0F);

   public LvBindPopup() {
      this.anim.setMs(220);
      this.anim.setValue(1.0);
      this.anim.setDirection(Direction.BACKWARDS);
      this.anim.counter.setTime(System.currentTimeMillis() - 10000L);
   }

   public Module module() {
      return this.module;
   }

   public boolean isOpen() {
      return this.open;
   }

   public void close() {
      if (this.open) {
         this.open = false;
         this.listening = false;
         this.anim.setDirection(Direction.BACKWARDS);
         this.anim.counter.resetCounter();
      }
   }

   public void open(Module module, float f, float f2) {
      this.module = module;
      this.open = true;
      this.listening = false;
      this.px = f;
      this.py = f2 + 2.0F;
      this.dragging = false;
      this.drag.release();
      this.drag.setTargetX(this.px);
      this.drag.setTargetY(this.py);
      this.drag.syncToTarget();
      this.anim.setDirection(Direction.FORWARDS);
      this.anim.counter.resetCounter();
   }

   public void render(DrawContext drawContext, float f) {
      if (this.module != null) {
         float f4 = this.anim.getOutput().floatValue();
         if (f4 <= 0.01F) {
            if (!this.open) {
               this.module = null;
            }
         } else {
            float f5 = f4 * f;
            long l = System.currentTimeMillis();
            float f6 = Math.min((float)(l - this.lastFrameMs) / 1000.0F, 0.1F);
            this.lastFrameMs = l;
            if (this.dragging) {
               this.drag.tick(Position.mouseX(), Position.mouseY(), 96.0F, 25.0F, true);
            }
            this.px = this.drag.getRenderX();
            this.py = this.drag.getRenderY();
            float f8 = this.py + (1.0F - f4) * 4.0F;
            LvRenderHelper.drawDropBackground(this.px, f8, 96.0F, 25.0F, f5);
            float f2 = f8 + 6.0F;
            FontsLV.MONTSERRAT_MEDIUM.draw("\u0411\u0438\u043d\u0434", this.px + 6.0F, f2 + 3.0F, 6.0F, rgba(255, 255, 255, 0.85F * f5));
            String string = this.listening ? "..." : this.keyDisplay();
            float f10 = Math.max(34.0F, FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F) + 10.0F);
            float f11 = this.px + 96.0F - 6.0F - f10;
            LvRenderHelper.drawPanelBg(f11, f2, f10, 13.0F, 3.0F, f5);
            FontsLV.MONTSERRAT_MEDIUM.draw(string, f11 + (f10 - FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F)) * 0.5F, f2 + 3.0F, 6.0F, rgba(255, 255, 255, 0.9F * f5));
         }
      }
   }

   private static int rgba(int n, int n2, int n3, float f) {
      int n4 = Math.max(0, Math.min(255, Math.round(f)));
      return n4 <= 0 ? 0 : new Color(n, n2, n3, n4).getRGB();
   }

   public boolean isVisible() {
      return this.module != null && this.anim.getOutput().floatValue() > 0.01F;
   }

   public boolean click(float f, float f2) {
      if (!this.open || this.module == null) {
         return false;
      } else if (!(f < this.px) && !(f > this.px + 96.0F) && !(f2 < this.py) && !(f2 > this.py + 25.0F)) {
         float f3 = this.py + 6.0F;
         String string = this.listening ? "..." : this.keyDisplay();
         float f4 = Math.max(34.0F, FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F) + 10.0F);
         float f5 = this.px + 96.0F - 6.0F - f4;
         if (f >= f5 && f <= f5 + f4 && f2 >= f3 && f2 <= f3 + 13.0F) {
            this.listening = !this.listening;
            return true;
         } else {
            this.drag.setTargetX(this.px);
            this.drag.setTargetY(this.py);
            this.dragging = this.drag.tryGrab(f, f2, 96.0F, 25.0F);
            return true;
         }
      } else {
         this.close();
         return false;
      }
   }

   public float blurPhase() {
      if (this.module == null) {
         return 0.0F;
      } else {
         float f = this.anim.getOutput().floatValue();
         return f <= 0.01F ? 0.0F : Math.max(0.0F, Math.min(1.0F, 1.0F - f));
      }
   }

   public boolean mouseBind(int n) {
      if (this.open && this.module != null && this.listening) {
         this.module.setKey(n);
         this.listening = false;
         this.close();
         return true;
      } else {
         return false;
      }
   }

   public boolean keyPressed(int n) {
      if (this.open && this.module != null && this.listening) {
         if (n == 256 || n == 261) {
            this.module.setKey(-1);
         } else {
            this.module.setKey(n);
         }
         this.listening = false;
         this.close();
         return true;
      } else {
         return false;
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
            fArray[n + 2] = 96.0F;
            fArray[n + 3] = 25.0F;
            fArray[n + 4] = 1.0F;
            fArray[n + 5] = Math.max(0.0F, Math.min(1.0F, 1.0F - f));
            return true;
         }
      } else {
         return false;
      }
   }

   public void releaseDrag() {
      this.dragging = false;
      this.drag.release();
   }

   private String keyDisplay() {
      if (this.module == null) {
         return "None";
      } else {
         int n = this.module.getKey();
         return n <= 0 ? "None" : Keyboard.keyName(n);
      }
   }
}
package ru.white.ui.lv;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.ThemeManager;
import ru.white.utils.other.GuiSounds;
import ru.white.utils.render.Draw;
import ru.white.utils.render.Scissor;

public final class LvSearchField {
   private static final float SIZE = 7.0F;
   private String text = "";
   private String placeholder = "\u041f\u043e\u0438\u0441\u043a...";
   private String iconGlyph = "q";
   private boolean typing;
   private boolean dragging;
   private int cursorPosition;
   private int selectionStart = -1;
   private int selectionEnd = -1;
   private long lastClickTime;
   private float xOffset;
   private float bx;
   private float by;
   private float bw;
   private float bh;
   private float textStartX;
   private float textVisibleW;
   private float hoverT;
   private float focusT;
   private float animCursorX;
   private boolean cursorSnap = true;
   private final List<Float> charAnim = new ArrayList<>();

   public LvSearchField() {
   }

   public String getText() {
      return this.text;
   }

   public void setText(String s) {
      this.text = s == null ? "" : s;
      this.cursorPosition = Math.min(this.cursorPosition, this.text.length());
      this.clearSelection();
      this.dragging = false;
      this.cursorSnap = true;
      this.charAnim.clear();
      for (int i = 0; i < this.text.length(); i++) {
         this.charAnim.add(1.0F);
      }
   }

   public void render(DrawContext ctx, float x, float y, float w, float h, float alpha, float mx, float my, float delta) {
      this.bx = x;
      this.by = y;
      this.bw = w;
      this.bh = h;
      this.cursorPosition = clamp(this.cursorPosition, 0, this.text.length());
      boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
      float f13 = 1.0F - (float)Math.exp(-delta * 16.0F);
      float f14 = 1.0F - (float)Math.exp(-delta * 11.0F);
      this.hoverT += ((hover ? 1.0F : 0.0F) - this.hoverT) * f13;
      this.focusT += ((this.typing ? 1.0F : 0.0F) - this.focusT) * f14;
      float ht = Math.max(this.hoverT, this.focusT);
      int accentRGB = ThemeManager.accent(255.0F) & 16777215;
      Draw.rect(x, y, w, h, col(0, 0, 0, (40.0F + 12.0F * ht) * alpha), 4.0F);
      int n4 = withAlpha(new Color(9, 9, 9, 0), alpha);
      int n5 = withAlpha(new Color(9, 12, 14, 0), alpha);
      Draw.outline(x, y, w, h, 0.8F, n4);
      float iconSize = 7.5F;
      float iconX = x + 5.0F;
      if (this.iconGlyph.isEmpty()) {
         this.textStartX = x + 5.0F + 1.0F;
      } else {
         float iconY = y + (h - iconSize) * 0.5F + 0.3F;
         int mixC = mixRgb(11842740, accentRGB, ht);
         int iconAlpha = clampA((125.0F + 95.0F * ht) * alpha);
         FontsLV.WVISUAL.msdf(this.iconGlyph, iconX, iconY, iconSize, iconAlpha << 24 | mixC);
         this.textStartX = iconX + iconSize + 4.0F;
      }
      float rightEdge = x + w - 5.0F;
      this.textVisibleW = Math.max(4.0F, rightEdge - this.textStartX);
      this.updateXOffset();
      float textX = this.textStartX - this.xOffset;
      float textY = y + (h - 7.0F) * 0.5F - 0.5F;
      Scissor.enable(this.textStartX - 2.0F, y, this.textVisibleW + 2.0F, h, 2.0F);
      if (this.typing && this.hasSelection()) {
         int selStart = this.getStartOfSelection();
         int selEnd = this.getEndOfSelection();
         float sx = textX + FontsLV.MONTSERRAT_MEDIUM.width(this.text.substring(0, selStart), 7.0F);
         float ex = textX + FontsLV.MONTSERRAT_MEDIUM.width(this.text.substring(0, selEnd), 7.0F);
         Draw.rect(sx, y + 2.5F, ex - sx, h - 5.0F, col(60, 128, 240, 165.0F * alpha), 1.5F);
      }
      float revealSpeed = delta * 3.6F;
      boolean anyRevealing = false;
      for (int i = 0; i < this.charAnim.size(); i++) {
         float val = this.charAnim.get(i);
         if (val < 1.0F) {
            this.charAnim.set(i, Math.min(1.0F, val + revealSpeed));
            anyRevealing = true;
         }
      }
      if (!this.text.isEmpty()) {
         int textColor = col(255, 255, 255, 235.0F * alpha);
         if (!anyRevealing) {
            FontsLV.MONTSERRAT_MEDIUM.draw(this.text, textX, textY, 7.0F, textColor);
         } else {
            float charCenter = textY + 3.5F;
            for (int i = 0; i < this.text.length(); i++) {
               float cp = clamp01(this.charProgress(i));
               if (cp <= 0.0F) continue;
               float cx = textX + FontsLV.MONTSERRAT_MEDIUM.width(this.text.substring(0, i), 7.0F);
               String ch = String.valueOf(this.text.charAt(i));
               int charA = clampA(235.0F * alpha * cp);
               if (charA > 0) {
                  int charCol = charA << 24 | 16777215;
                  if (cp >= 1.0F) {
                     FontsLV.MONTSERRAT_MEDIUM.draw(ch, cx, textY, 7.0F, charCol);
                  }
               }
            }
         }
      } else {
         float placeholderAlpha = 1.0F - this.focusT;
         if (placeholderAlpha > 0.01F && !this.placeholder.isEmpty()) {
            FontsLV.MONTSERRAT_MEDIUM.draw(this.placeholder, textX, textY, 7.0F,
                  col(255, 255, 255, 95.0F * alpha * placeholderAlpha));
         }
      }
      Scissor.disable();
      float cursorOffsetX = FontsLV.MONTSERRAT_MEDIUM.width(this.text.substring(0, this.cursorPosition), 7.0F);
      if (!this.cursorSnap && this.typing) {
         this.animCursorX += (cursorOffsetX - this.animCursorX) * (1.0F - (float)Math.exp(-delta * 20.0F));
      } else {
         this.animCursorX = cursorOffsetX;
         this.cursorSnap = false;
      }
      long now = System.currentTimeMillis();
      if (this.focusT > 0.01F && !this.hasSelection()) {
         float blink = (float)(Math.sin(now / 200.0) * 0.5 + 0.5);
         float cx = textX + this.animCursorX;
         Draw.rect(cx, y + 2.5F, 0.6F, h - 5.0F, col(255, 255, 255, (70.0F + 185.0F * blink) * this.focusT * alpha), 0.0F);
      }
      if (this.dragging) {
         int idx = this.cursorIndexAt(mx);
         if (this.selectionStart == -1) {
            this.selectionStart = this.cursorPosition;
         }
         this.selectionEnd = this.cursorPosition = idx;
         if (this.selectionStart == this.selectionEnd) {
            this.clearSelection();
         }
      }
   }

   public void blur() {
      this.typing = false;
      this.dragging = false;
      this.clearSelection();
   }

   public boolean hasText() {
      return !this.text.isEmpty();
   }

   public boolean isTyping() {
      return this.typing;
   }

   public boolean charTyped(CharInput input) {
      if (!this.typing) {
         return false;
      }
      int cp = input.codepoint();
      if (Character.isISOControl(cp)) {
         return false;
      }
      this.deleteSelectedText();
      this.replaceText(this.cursorPosition, this.cursorPosition, Character.toString((char)cp));
      GuiSounds.type();
      return true;
   }

   public boolean keyPressed(KeyInput input) {
      if (!this.typing) {
         return false;
      }
      int key = input.key();
      boolean ctrl = ctrlDown();
      boolean shift = shiftDown();
      if (ctrl) {
         switch (key) {
            case 65:
               this.selectAllText();
               return true;
            case 67:
               this.copyToClipboard();
               return true;
            case 86:
               this.pasteFromClipboard();
               return true;
            case 88:
               this.cutToClipboard();
               return true;
            case 259:
               this.deletePrevious(true);
               return true;
            case 261:
               this.deleteNext(true);
               return true;
            case 262:
               this.moveCursorTo(this.findNextWordBoundary(this.cursorPosition), shift);
               return true;
            case 263:
               this.moveCursorTo(this.findPreviousWordBoundary(this.cursorPosition), shift);
               return true;
            default:
               return false;
         }
      } else {
         switch (key) {
            case 256:
               if (!this.text.isEmpty()) {
                  this.setText("");
               } else {
                  this.blur();
               }
               return true;
            case 257:
               this.blur();
               return true;
            case 259:
               this.deletePrevious(false);
               return true;
            case 261:
               this.deleteNext(false);
               return true;
            case 262:
               if (!shift && this.hasSelection()) {
                  this.moveCursorTo(this.getEndOfSelection(), false);
               } else {
                  this.moveCursorTo(this.cursorPosition + 1, shift);
               }
               return true;
            case 263:
               if (!shift && this.hasSelection()) {
                  this.moveCursorTo(this.getStartOfSelection(), false);
               } else {
                  this.moveCursorTo(this.cursorPosition - 1, shift);
               }
               return true;
            case 268:
               this.moveCursorTo(0, shift);
               return true;
            case 269:
               this.moveCursorTo(this.text.length(), shift);
               return true;
            default:
               return false;
         }
      }
   }

   public boolean mouseClicked(float mx, float my, int button) {
      boolean inside = mx >= this.bx && mx <= this.bx + this.bw && my >= this.by && my <= this.by + this.bh;
      if (inside && button == 0) {
         this.cursorSnap = true;
         long now = System.currentTimeMillis();
         int idx = this.cursorIndexAt(mx);
         if (now - this.lastClickTime < 250L) {
            this.typing = true;
            this.dragging = false;
            this.selectAllText();
         } else {
            this.typing = true;
            this.dragging = true;
            this.selectionStart = this.cursorPosition = idx;
            this.selectionEnd = this.cursorPosition;
         }
         this.lastClickTime = now;
         return true;
      } else if (!inside && button == 0) {
         this.blur();
      }
      return false;
   }

   public void mouseReleased(int button) {
      if (button == 0) {
         this.dragging = false;
      }
   }

   public void focus() {
      this.typing = true;
      this.dragging = false;
      this.cursorPosition = this.text.length();
      this.clearSelection();
      this.cursorSnap = true;
   }

   private void replaceText(int start, int end, String insertion) {
      int s = clamp(start, 0, this.text.length());
      int e = clamp(end, 0, this.text.length());
      if (s > e) {
         int tmp = s;
         s = e;
         e = tmp;
      }
      this.text = this.text.substring(0, s) + insertion + this.text.substring(e);
      this.cursorPosition = s + insertion.length();
      for (int i = Math.min(e, this.charAnim.size()) - 1; i >= s; i--) {
         this.charAnim.remove(i);
      }
      for (int i = 0; i < insertion.length(); i++) {
         this.charAnim.add(s + i, -(i * 0.14F));
      }
      this.clearSelection();
   }

   private boolean isWordCharacter(char c) {
      return Character.isLetterOrDigit(c) || c == '_';
   }

   private void selectAllText() {
      if (this.text.isEmpty()) {
         this.clearSelection();
         this.cursorPosition = 0;
      } else {
         this.selectionStart = 0;
         this.selectionEnd = this.text.length();
         this.cursorPosition = this.text.length();
      }
   }

   private void copyToClipboard() {
      if (this.hasSelection()) {
         MinecraftClient.getInstance().keyboard.setClipboard(this.getSelectedText());
      }
   }

   private void cutToClipboard() {
      if (this.hasSelection()) {
         MinecraftClient.getInstance().keyboard.setClipboard(this.getSelectedText());
         this.deleteSelectedText();
      }
   }

   private void pasteFromClipboard() {
      String clip = MinecraftClient.getInstance().keyboard.getClipboard();
      if (clip != null && !clip.isEmpty()) {
         String sanitized = clip.replace("\r", "").replace("\n", " ");
         this.deleteSelectedText();
         this.replaceText(this.cursorPosition, this.cursorPosition, sanitized);
      }
   }

   private void deleteSelectedText() {
      if (this.hasSelection()) {
         this.replaceText(this.getStartOfSelection(), this.getEndOfSelection(), "");
      }
   }

   private void deletePrevious(boolean word) {
      if (this.hasSelection()) {
         this.deleteSelectedText();
      } else if (this.cursorPosition > 0) {
         int n = word ? this.findPreviousWordBoundary(this.cursorPosition) : this.cursorPosition - 1;
         this.replaceText(n, this.cursorPosition, "");
      }
   }

   private void deleteNext(boolean word) {
      if (this.hasSelection()) {
         this.deleteSelectedText();
      } else if (this.cursorPosition < this.text.length()) {
         int n = word ? this.findNextWordBoundary(this.cursorPosition) : this.cursorPosition + 1;
         this.replaceText(this.cursorPosition, n, "");
      }
   }

   private void moveCursorTo(int pos, boolean select) {
      int clamped = clamp(pos, 0, this.text.length());
      if (select) {
         if (this.selectionStart == -1) {
            this.selectionStart = this.cursorPosition;
         }
         this.selectionEnd = this.cursorPosition = clamped;
         if (this.selectionStart == this.selectionEnd) {
            this.clearSelection();
         }
      } else {
         this.cursorPosition = clamped;
         this.clearSelection();
      }
   }

   private int findNextWordBoundary(int pos) {
      int n = clamp(pos, 0, this.text.length());
      while (n < this.text.length() && !isWordCharacter(this.text.charAt(n))) n++;
      while (n < this.text.length() && isWordCharacter(this.text.charAt(n))) n++;
      return n;
   }

   private int findPreviousWordBoundary(int pos) {
      int n = clamp(pos, 0, this.text.length());
      while (n > 0 && !isWordCharacter(this.text.charAt(n - 1))) n--;
      while (n > 0 && isWordCharacter(this.text.charAt(n - 1))) n--;
      return n;
   }

   private int cursorIndexAt(float mx) {
      float f = mx - this.textStartX + this.xOffset;
      int n;
      for (n = 0; n < this.text.length(); n++) {
         float cw = FontsLV.MONTSERRAT_MEDIUM.width(this.text.substring(n, n + 1), 7.0F);
         float offset = FontsLV.MONTSERRAT_MEDIUM.width(this.text.substring(0, n), 7.0F);
         if (offset + cw / 2.0F > f) break;
      }
      return clamp(n, 0, this.text.length());
   }

   private void updateXOffset() {
      float curX = FontsLV.MONTSERRAT_MEDIUM.width(this.text.substring(0, Math.min(this.cursorPosition, this.text.length())), 7.0F);
      if (curX < this.xOffset) {
         this.xOffset = Math.max(0.0F, curX - 6.0F);
      } else if (curX - this.xOffset > this.textVisibleW - 4.0F) {
         this.xOffset = curX - (this.textVisibleW - 4.0F) + 6.0F;
      }
      if (this.xOffset < 0.0F) this.xOffset = 0.0F;
      float maxOff = Math.max(0.0F, FontsLV.MONTSERRAT_MEDIUM.width(this.text, 7.0F) - this.textVisibleW + 4.0F);
      if (this.xOffset > maxOff) this.xOffset = maxOff;
   }

   private void clearSelection() {
      this.selectionStart = -1;
      this.selectionEnd = -1;
   }

   private boolean hasSelection() {
      return this.selectionStart != -1 && this.selectionEnd != -1 && this.selectionStart != this.selectionEnd;
   }

   private int getStartOfSelection() {
      return Math.min(this.selectionStart, this.selectionEnd);
   }

   private int getEndOfSelection() {
      return Math.max(this.selectionStart, this.selectionEnd);
   }

   private String getSelectedText() {
      return this.text.substring(this.getStartOfSelection(), this.getEndOfSelection());
   }

   private float charProgress(int i) {
      return i >= 0 && i < this.charAnim.size() ? this.charAnim.get(i) : 1.0F;
   }

   private static int clamp(int v, int min, int max) {
      return Math.max(min, Math.min(max, v));
   }

   private static float clamp01(float f) {
      return f < 0.0F ? 0.0F : (f > 1.0F ? 1.0F : f);
   }

   private static int col(int r, int g, int b, float a) {
      int ai = Math.max(0, Math.min(255, Math.round(a)));
      return ai <= 0 ? 0 : new Color(r, g, b, ai).getRGB();
   }

   private static int withAlpha(Color c, float f) {
      int a = clampA(c.getAlpha() * f);
      return a << 24 | c.getRGB() & 16777215;
   }

   private static int clampA(float f) {
      return Math.max(0, Math.min(255, Math.round(f)));
   }

   private static int mixRgb(int c1, int c2, float t) {
      t = clamp01(t);
      int r1 = c1 >> 16 & 0xFF, g1 = c1 >> 8 & 0xFF, b1 = c1 & 0xFF;
      int r2 = c2 >> 16 & 0xFF, g2 = c2 >> 8 & 0xFF, b2 = c2 & 0xFF;
      return Math.round(r1 + (r2 - r1) * t) << 16 | Math.round(g1 + (g2 - g1) * t) << 8 | Math.round(b1 + (b2 - b1) * t);
   }

   private static boolean shiftDown() {
      long handle = MinecraftClient.getInstance().getWindow().getHandle();
      return GLFW.glfwGetKey(handle, 340) == 1 || GLFW.glfwGetKey(handle, 344) == 1;
   }

   private static boolean ctrlDown() {
      long handle = MinecraftClient.getInstance().getWindow().getHandle();
      return GLFW.glfwGetKey(handle, 341) == 1 || GLFW.glfwGetKey(handle, 345) == 1;
   }
}

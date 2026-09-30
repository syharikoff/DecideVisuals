package ru.white.ui.lv;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.DrawContext;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.AccentGradient;
import ru.white.ui.theme.ThemeManager;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.Draw;
import ru.white.utils.render.Scissor;

public final class LvEventsRenderer {
   private static final float ROW_H = 31.0F;
   private static final float GAP = 3.0F;
   private static final float CONTENT_HEIGHT = 280.0F;
   private static final float TAB_BAR_H = 18.0F;
   private static final float BOTTOM_PAD = 18.0F;
   private static final float DETAIL_ANIM_MS = 400.0F;
   private static final long OPEN_FADE_MS = 240L;
   private final List<Row> rows = new ArrayList<>();
   private final List<MineRow> mineRows = new ArrayList<>();
   private float scroll;
   private float scrollTarget;
   private float contentH;
   private long openMs = System.currentTimeMillis();
   private boolean transitioning = false;
   private float fadeOutTime = 0.0F;
   private Tab tab = Tab.EVENTS;
   private Tab pendingTab = null;
   private float tabFade = 1.0F;
   private float eventsTabT = 1.0F;
   private float minesTabT = 0.0F;
   private final float[] eventsTabRect = new float[4];
   private final float[] minesTabRect = new float[4];

   public LvEventsRenderer() {
   }

   private static float clamp(float f, float f2, float f3) {
      return Math.max(f2, Math.min(f3, f));
   }

   public void open() {
      this.scroll = 0.0F;
      this.scrollTarget = 0.0F;
      this.openMs = System.currentTimeMillis();
      this.rows.clear();
      this.mineRows.clear();
   }

   private void renderContent(DrawContext m, float x, float y, float panelW, float alpha, float dt) {
      float f6 = x + 117.0F;
      float f7 = y + 5.0F;
      float f8 = panelW - 122.0F;
      float f9 = 280.0F;
      this.updateTabTransition(dt);
      float f12 = alpha * this.openFadeAlpha() * smooth01(this.tabFade);
      this.renderTabs(m, x, f7, f12);
      float f13 = f7 + TAB_BAR_H + 4.0F;
      float contentTop = f13;
      if (this.tab == Tab.MINES) {
         this.renderMines(m, f6, contentTop, f8, f9 - (contentTop - f7), f12, dt);
      } else {
         this.renderEvents(m, f6, contentTop, f8, f9 - (contentTop - f7), f12, dt);
      }
   }

   private void renderTabs(DrawContext m, float x, float y, float alpha) {
      float f4 = 5.5F;
      float f5 = 7.0F;
      float f6 = 12.0F;
      float f7 = 4.0F;
      float f8 = y + 4.0F;
      float f9 = FontsLV.MONTSERRAT_MEDIUM.width("\u0421\u043e\u0431\u044b\u0442\u0438\u044f", f4) + f5 * 2.0F;
      float f10 = FontsLV.MONTSERRAT_MEDIUM.width("\u0428\u0430\u0445\u0442\u044b", f4) + f5 * 2.0F;
      float f11 = x + 4.0F + 117.0F;
      float f12 = f11 + f9 + f7;
      this.drawTab(m, f11, f8, f9, f6, "\u0421\u043e\u0431\u044b\u0442\u0438\u044f", this.eventsTabT, alpha, this.eventsTabRect);
      this.drawTab(m, f12, f8, f10, f6, "\u0428\u0430\u0445\u0442\u044b", this.minesTabT, alpha, this.minesTabRect);
   }

   private void drawTab(DrawContext m, float x, float y, float w, float h, String text, float t, float alpha, float[] rect) {
      float f7 = smooth01(t);
      Draw.rect(x, y, w, h, ColorUtil.getColor(255, 255, 255, (int) (16.0F * alpha * (1.0F - f7))), 4.0F);
      if (f7 > 0.001F) {
         AccentGradient.fillVertical(x, y, w, h, 4.0F, 210.0F * alpha * f7);
      }
      float f8 = 5.5F;
      float f9 = FontsLV.MONTSERRAT_MEDIUM.width(text, f8);
      float f10 = (130.0F + 125.0F * f7) * alpha;
      FontsLV.MONTSERRAT_MEDIUM.draw(text, x + (w - f9) * 0.5F, y + h * 0.5F - f8 * 0.5F, f8, ColorUtil.getColor(255, 255, 255, (int) f10));
      rect[0] = x;
      rect[1] = y;
      rect[2] = w;
      rect[3] = h;
   }

   private void renderScrollBar(float x, float y, float w, float h, float alpha, float contentBase) {
      float trackX = x + w - 2.5F;
      float trackY = y + 3.0F;
      float trackH = h - 6.0F;
      float ratio = clamp(trackH / Math.max(this.contentH + BOTTOM_PAD, trackH), 0.0F, 1.0F);
      float handleH = clamp(trackH * ratio, 12.0F, trackH);
      float maxTravel = trackH - handleH;
      float progress = clamp(this.scroll / Math.max(contentBase, 1.0F), 0.0F, 1.0F);
      float handleY = trackY + maxTravel * progress;
      Draw.rect(trackX, trackY, 1.25F, trackH, ColorUtil.getColor(255, 255, 255, (int) (20.0F * alpha)));
      AccentGradient.fillVertical(trackX, handleY, 1.25F, handleH, 1.0F, 165.0F * alpha);
   }

   public void render(DrawContext m, float x, float y, float panelWidth, float alpha, float dt) {
      float f6 = 1.0F - (float)Math.exp(-dt * 14.0F);
      this.scroll = this.scroll + (this.scrollTarget - this.scroll) * f6;
      if (Math.abs(this.scrollTarget - this.scroll) < 0.05F) {
         this.scroll = this.scrollTarget;
      }
      if (this.transitioning) {
         if (this.fadeOutTime > 0.0F) {
            this.fadeOutTime -= dt;
            float f7 = alpha * Math.max(0.0F, this.fadeOutTime / 0.15F);
            this.renderContent(m, x, y, panelWidth, f7, dt);
         }
      } else {
         this.renderContent(m, x, y, panelWidth, alpha, dt);
      }
   }

   public boolean click(float mx, float my) {
      if (hit(this.eventsTabRect, mx, my)) {
         this.setTab(Tab.EVENTS);
         return true;
      } else if (hit(this.minesTabRect, mx, my)) {
         this.setTab(Tab.MINES);
         return true;
      } else if (this.tab == Tab.MINES) {
         for (MineRow r : this.mineRows) {
            if (r.contains(mx, my)) {
               return true;
            }
         }
         return false;
      } else {
         for (Row r : this.rows) {
            if (r.contains(mx, my)) {
               return true;
            }
         }
         return false;
      }
   }

   public void showMines() {
      this.setTab(Tab.MINES);
   }

   public void showEvents() {
      this.setTab(Tab.EVENTS);
   }

   public void scroll(float amount, float h) {
      float f2 = Math.max(0.0F, this.contentH - h + 18.0F);
      this.scrollTarget = clamp(this.scrollTarget - amount * 16.0F, 0.0F, f2);
   }

   private float openFadeAlpha() {
      float f = clamp((float)(System.currentTimeMillis() - this.openMs) / (float) OPEN_FADE_MS, 0.0F, 1.0F);
      return f * f * (3.0F - 2.0F * f);
   }

   private void renderMines(DrawContext m, float x, float top, float w, float h, float alpha, float dt) {
      this.mineRows.clear();
      Scissor.enable(x, top, w, h, 2.0F);
      float y = top + 5.0F - this.scroll;
      y = this.renderMineList(m, x, y, w, h, alpha, dt);
      this.contentH = Math.max(0.0F, y - (top + 5.0F - this.scroll));
      float max = Math.max(0.0F, this.contentH - h + 18.0F);
      this.scrollTarget = clamp(this.scrollTarget, 0.0F, max);
      this.scroll = clamp(this.scroll, 0.0F, max);
      Scissor.disable();
      this.renderScrollBar(x, top, w, h, alpha, max);
   }

   private float renderMineList(DrawContext m, float x, float y, float w, float h, float alpha, float dt) {
      float yy = y;
      for (int i = 0; i < 3; i++) {
         int n = i + 1;
         float cy = yy + i * (ROW_H + GAP);
         this.renderMineRow(m, x, cy, w, alpha, n, i);
         this.mineRows.add(new MineRow(x, cy, w, ROW_H));
         yy = cy + ROW_H + GAP;
      }
      return yy;
   }

   private void renderMineRow(DrawContext m, float x, float y, float w, float alpha, int anarchy, int idx) {
      Draw.rect(x, y, w, 31.0F, ColorUtil.getColor(0, 0, 0, (int) (48.0F * alpha)), 5.0F);
      float f5 = x + 4.0F;
      float f6 = y + 5.0F;
      float f7 = 31.0F;
      float f8 = 21.0F;
      int[] rarity = new int[]{150, 150, 165};
      int[] darker = darker(rarity);
      int n = rgba(rarity[0], rarity[1], rarity[2], 175.0F * alpha);
      int n2 = rgba(darker[0], darker[1], darker[2], 175.0F * alpha);
      int n3 = mixColor(n, n2, 0.5F);
      Draw.gradientRect(f5, f6, f7, f8, new int[]{n, n3, n2, n3}, 4.0F);
      Draw.rect(f5 + f7 * 0.5F - 5.0F, f6 + f8 * 0.5F - 5.0F, 10.0F, 10.0F, ColorUtil.getColor(255, 255, 255, (int) (235.0F * alpha)), 3.0F);
      float f9 = f5 + f7 + 7.0F;
      String cmd = "/an" + anarchy;
      float f10 = FontsLV.MONTSERRAT_MEDIUM.width(cmd, 5.0F);
      FontsLV.MONTSERRAT_MEDIUM.draw(cmd, x + w - f10 - 6.0F, y + 6.0F, 5.0F, ThemeManager.accentBright(200.0F * alpha));
      String remaining = "\u041e\u0431\u043d\u043e\u0432\u043b\u044f\u0435\u0442\u0441\u044f";
      float f11 = FontsLV.MONTSERRAT_MEDIUM.width(remaining, 5.0F);
      FontsLV.MONTSERRAT_MEDIUM.draw(remaining, x + w - f11 - 6.0F, y + 17.0F, 5.0F, ColorUtil.getColor(255, 255, 255, (int) (120.0F * alpha)));
      String server = "\u0410\u043d\u0430\u0440\u0445\u0438\u044f-" + anarchy;
      FontsLV.MONTSERRAT_MEDIUM.draw(server, f9, y + 5.0F, 6.5F, ColorUtil.getColor(255, 255, 255, (int) (225.0F * alpha)));
      FontsLV.MONTSERRAT_MEDIUM.draw("\u041e\u0431\u044b\u0447\u043d\u044b\u0439", f9, y + 17.0F, 5.0F, ColorUtil.getColor(255, 255, 255, (int) (200.0F * alpha)));
   }

   private void renderEvents(DrawContext m, float x, float top, float w, float h, float alpha, float dt) {
      this.rows.clear();
      Scissor.enable(x, top, w, h, 2.0F);
      float y = top + 5.0F - this.scroll;
      y = this.renderEventList(m, x, y, w, h, alpha, dt);
      if (this.rows.isEmpty()) {
         String empty = "\u0421\u043e\u0431\u044b\u0442\u0438\u0439 \u043f\u043e\u043a\u0430 \u043d\u0435\u0442";
         float tw = FontsLV.MONTSERRAT_MEDIUM.width(empty, 6.5F);
         FontsLV.MONTSERRAT_MEDIUM.draw(empty, x + (w - tw) * 0.5F, top + h * 0.5F, 6.5F, ColorUtil.getColor(255, 255, 255, (int) (115.0F * alpha)));
      }
      this.contentH = Math.max(0.0F, y - (top + 5.0F - this.scroll));
      float max = Math.max(0.0F, this.contentH - h + 18.0F);
      this.scrollTarget = clamp(this.scrollTarget, 0.0F, max);
      this.scroll = clamp(this.scroll, 0.0F, max);
      Scissor.disable();
      this.renderScrollBar(x, top, w, h, alpha, max);
   }

   private float renderEventList(DrawContext m, float x, float y, float w, float h, float alpha, float dt) {
      float yy = y;
      for (int i = 0; i < 4; i++) {
         float cy = yy + i * (ROW_H + GAP);
         this.renderEventRow(m, x, cy, w, alpha, i);
         this.rows.add(new Row(x, cy, w, ROW_H));
         yy = cy + ROW_H + GAP;
      }
      return yy;
   }

   private void renderEventRow(DrawContext m, float x, float y, float w, float alpha, int idx) {
      Draw.rect(x, y, w, 31.0F, ColorUtil.getColor(0, 0, 0, (int) (48.0F * alpha)), 5.0F);
      float f5 = x + 4.0F;
      float f6 = y + 5.0F;
      float f7 = 31.0F;
      float f8 = 21.0F;
      int[] c = new int[]{140, 140, 150};
      int[] darker = darker(c);
      int n = rgba(c[0], c[1], c[2], 175.0F * alpha);
      int n2 = rgba(darker[0], darker[1], darker[2], 175.0F * alpha);
      int n3 = mixColor(n, n2, 0.5F);
      Draw.gradientRect(f5, f6, f7, f8, new int[]{n, n3, n2, n3}, 4.0F);
      Draw.rect(f5 + f7 * 0.5F - 5.0F, f6 + f8 * 0.5F - 5.0F, 10.0F, 10.0F, ColorUtil.getColor(255, 255, 255, (int) (235.0F * alpha)), 3.0F);
      float f9 = f5 + f7 + 7.0F;
      String cmd = "/an" + (idx + 1);
      float f10 = FontsLV.MONTSERRAT_MEDIUM.width(cmd, 5.0F);
      FontsLV.MONTSERRAT_MEDIUM.draw(cmd, x + w - f10 - 6.0F, y + 6.0F, 5.0F, ThemeManager.accentBright(200.0F * alpha));
      String status = "\u0418\u0434\u0451\u0442";
      float f11 = FontsLV.MONTSERRAT_MEDIUM.width(status, 5.0F);
      FontsLV.MONTSERRAT_MEDIUM.draw(status, x + w - f11 - 6.0F, y + 17.0F, 5.0F, ColorUtil.getColor(255, 255, 255, (int) (120.0F * alpha)));
      String name = idx == 0 ? "\u0422\u0435\u043c\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u043e\u0435 \u0441\u043e\u0431\u044b\u0442\u0438\u0435"
         : (idx == 1 ? "\u0412\u0443\u043b\u043a\u0430\u043d" : (idx == 2 ? "\u041c\u0430\u044f\u043a" : "\u041c\u0435\u0442\u0435\u043e\u0440"));
      FontsLV.MONTSERRAT_MEDIUM.draw(name, f9, y + 5.0F, 6.5F, ColorUtil.getColor(255, 255, 255, (int) (225.0F * alpha)));
      String rarity = "\u041e\u0431\u044b\u0447\u043d\u044b\u0439";
      FontsLV.MONTSERRAT_MEDIUM.draw(rarity, f9, y + 17.0F, 5.0F, ColorUtil.getColor(255, 255, 255, (int) (200.0F * alpha)));
   }

   public void scrollbarRelease() {
   }

   public boolean scrollbarGrab(float mx, float my) {
      return false;
   }

   public void finishTransition() {
      this.transitioning = false;
      this.fadeOutTime = 0.0F;
   }

   private static int[] darker(int[] color) {
      return new int[]{Math.round(color[0] * 0.42F), Math.round(color[1] * 0.42F), Math.round(color[2] * 0.42F)};
   }

   private static int rgba(int r, int g, int b, float alpha) {
      int a = Math.max(0, Math.min(255, Math.round(alpha)));
      return a == 0 ? 0 : a << 24 | r << 16 | g << 8 | b;
   }

   private static int mixColor(int c1, int c2, float t) {
      int r = Math.round((c1 >> 16 & 0xFF) + ((c2 >> 16 & 0xFF) - (c1 >> 16 & 0xFF)) * t);
      int g = Math.round((c1 >> 8 & 0xFF) + ((c2 >> 8 & 0xFF) - (c1 >> 8 & 0xFF)) * t);
      int b = Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
      int a = Math.round((c1 >> 24 & 0xFF) + ((c2 >> 24 & 0xFF) - (c1 >> 24 & 0xFF)) * t);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private void setTab(Tab t) {
      Tab current = this.pendingTab != null ? this.pendingTab : this.tab;
      if (t != current) {
         this.pendingTab = t;
      }
   }

   private void updateTabTransition(float dt) {
      float target = this.pendingTab != null ? 0.0F : 1.0F;
      this.tabFade = this.tabFade + (target - this.tabFade) * (1.0F - (float)Math.exp(-dt * 16.0F));
      if (this.pendingTab != null && this.tabFade <= 0.04F) {
         this.tab = this.pendingTab;
         this.pendingTab = null;
         this.scroll = 0.0F;
         this.scrollTarget = 0.0F;
         this.tabFade = 0.0F;
      }
      Tab effective = this.pendingTab != null ? this.pendingTab : this.tab;
      float f = 1.0F - (float)Math.exp(-dt * 14.0F);
      this.eventsTabT = this.eventsTabT + ((effective == Tab.EVENTS ? 1.0F : 0.0F) - this.eventsTabT) * f;
      this.minesTabT = this.minesTabT + ((effective == Tab.MINES ? 1.0F : 0.0F) - this.minesTabT) * f;
   }

   private static float smooth01(float f) {
      f = clamp(f, 0.0F, 1.0F);
      return f * f * (3.0F - 2.0F * f);
   }

   private static boolean hit(float[] r, float x, float y) {
      return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
   }

   public static String formatTime(int n) {
      if (n <= 0) {
         return "\u0418\u0434\u0451\u0442";
      }
      int m = n / 60;
      int s = n % 60;
      return m + "\u043c\u0438\u043d. " + s + "\u0441\u0435\u043a.";
   }

   private enum Tab {
      EVENTS,
      MINES;
   }

   private static final class Row {
      final float x;
      final float y;
      final float w;
      final float h;

      Row(float x, float y, float w, float h) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
      }

      boolean contains(float mx, float my) {
         return mx >= this.x && mx <= this.x + this.w && my >= this.y && my <= this.y + this.h;
      }
   }

   private static final class MineRow {
      final float x;
      final float y;
      final float w;
      final float h;

      MineRow(float x, float y, float w, float h) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
      }

      boolean contains(float mx, float my) {
         return mx >= this.x && mx <= this.x + this.w && my >= this.y && my <= this.y + this.h;
      }
   }
}

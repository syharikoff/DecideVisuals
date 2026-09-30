package ru.white.ui.lv;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import ru.white.Client;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.ThemeManager;
import ru.white.ui.util.Position;
import ru.white.ui.util.anim.Decelerate;
import ru.white.ui.util.anim.Direction;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.Keyboard;
import ru.white.utils.other.GuiSounds;
import ru.white.utils.render.Draw;
import ru.white.utils.render.Scissor;

public final class LvModuleList {
   private static final float FADE_OUT_DURATION = 0.15F;
   private static final int ROW_FADE_MS = 380;
   private static final int QUICK_ROW_FADE_MS = 320;
   private static final float SLIDE_PX = 6.0F;
   private static final float CARD_RADIUS = 6.0F;
   private static final float CARD_GAP = 5.0F;
   private static final float PAD_X = 8.0F;
   private static final float NAME_SIZE = 7.5F;
   private static final float NAME_TOP = 7.0F;
   private static final float TITLE_CY = 10.75F;
   private static final float DESC_SIZE = 6.0F;
   private static final float DESC_LINE_H = 7.0F;
   private static final float DESC_TOP = 19.0F;
   private static final float DESC_RIGHT_PAD = 4.0F;
   private static final float DESC_BOTTOM_PAD = 7.0F;
   private static final float NO_DESC_H = 22.0F;
   private static final float TOGGLE_RIGHT_PAD = 8.0F;
   private static final float GEAR_GAP = 7.0F;
   private static final float GEAR_SIZE = 7.0F;
   private static final String GEAR_GLYPH = "f";
   private static final float CHECK_SIZE = 8.5F;
   private static final float CHECK_RADIUS = 2.9750001F;
   private static final float CHECK_RING_THICKNESS = Math.max(0.5F, 0.425F);
   public static final float HEADER_OFFSET = 30.0F;

   private final Map<String, Decelerate> enableAnims = new HashMap<>();
   private final Map<String, Float> hoverAnims = new HashMap<>();
   private final Map<String, Float> gearHoverAnims = new HashMap<>();
   private final Map<String, Float> gearOpenAnims = new HashMap<>();
   private final Map<String, List<String>> descLinesCache = new HashMap<>();
   private final Map<String, Float> baseHeightCache = new HashMap<>();
   private float scroll = 0.0F;
   private float scrollTarget = 0.0F;
   private float contentH = 0.0F;
   private LvCategory lastRendered = null;
   private final Map<Integer, Decelerate> rowAppearAnims = new HashMap<>();
   private int appearFadeMs = 320;
   private long appearBaseMs;
   private boolean appearInitialFrame;
   private final LvScrollBar scrollBar = new LvScrollBar();
   private boolean transitioning = false;
   private boolean quickAppear = false;
   private List<Module> fadingOut = Collections.emptyList();
   private float fadeOutTime = 0.0F;
   private String focusName;
   private long focusUntilMs;
   private boolean focusScrollPending;
   private float focusDimT;
   private boolean appearComposite;
   private int lastModulesSig;

   public LvModuleList() {
   }

   public void warmup() {
      int color = 33554431;
      String glyphs = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,:;!?-_()[]{}<>/\\|@#$%^&*+=\"'`\u0410\u0411\u0412\u0413\u0414\u0415\u0401\u0416\u0417\u0418\u0419\u041a\u041b\u041c\u041d\u041e\u041f\u0420\u0421\u0422\u0423\u0424\u0425\u0426\u0427\u0428\u0429\u042a\u042b\u042c\u042d\u042e\u042f\u0430\u0431\u0432\u0433\u0434\u0435\u0451\u0436\u0437\u0438\u0439\u043a\u043b\u043c\u043d\u043e\u043f\u0440\u0441\u0442\u0443\u0444\u0445\u0446\u0447\u0448\u0449\u044a\u044b\u044c\u044d\u044e\u044f";
      FontsLV.MONTSERRAT_MEDIUM.draw(glyphs, -500.0F, -500.0F, 6.0F, color);
      for (LvCategory cat : LvCategory.values()) {
         for (Module module : getModules(cat)) {
            enableAnims.computeIfAbsent(module.getName(), n -> createAnim(220));
            FontsLV.WVISUAL.msdf(String.valueOf(cat.getIcon()), -500.0F, -500.0F, 6.5F, color);
            FontsLV.MONTSERRAT_MEDIUM.draw(module.getBigName(), -500.0F, -500.0F, 7.5F, color);
            String desc = module.getDesc() != null ? module.getDesc() : "";
            FontsLV.MONTSERRAT_MEDIUM.draw(desc, -500.0F, -500.0F, 6.0F, color);
            FontsLV.MONTSERRAT_MEDIUM.width(module.getBigName(), 7.5F);
            FontsLV.MONTSERRAT_MEDIUM.width(desc, 6.0F);
         }
      }
      FontsLV.WVISUAL.msdf("f", -500.0F, -500.0F, 7.0F, color);
      Draw.rect(-500.0F, -500.0F, 30.0F, 30.0F, color, 4.0F, 4.0F, 4.0F, 4.0F);
      Draw.outline(-500.0F, -500.0F, 30.0F, 30.0F, 0.7F, color, 4.0F, 4.0F, 4.0F, 4.0F);
   }

   public void setAppearComposite(boolean v) {
      this.appearComposite = v;
   }

   public void render(DrawContext dc, float x, float y, float w, float f14, float f15, float dt, LvCategory category, List<Module> modules) {
      float contentX = x + 117.0F;
      float baseY = y + 5.0F + 30.0F;
      float cardW = colW(w - 122.0F);
      float col0X = contentX + 5.0F;
      float col1X = col0X + cardW + 5.0F;
      float viewH = 250.0F;
      float lerpFactor = 1.0F - (float)Math.exp(-dt * 14.0F);
      this.scroll += (this.scrollTarget - this.scroll) * lerpFactor;
      if (Math.abs(this.scrollTarget - this.scroll) < 0.05F) {
         this.scroll = this.scrollTarget;
      }
      if (this.transitioning) {
         if (this.fadeOutTime > 0.0F) {
            this.fadeOutTime -= dt;
            float alpha = f14 * Math.max(0.0F, this.fadeOutTime / 0.15F);
            renderCards(dc, contentX, baseY, w, cardW, col0X, col1X, viewH, alpha, f15, dt, this.fadingOut, false);
         }
      } else {
         int sig = modulesSignature(modules);
         boolean needsComposite = this.appearComposite && (category != this.lastRendered || sig != this.lastModulesSig
               || this.scrollBar.isDragging() || Math.abs(this.scrollTarget - this.scroll) > 0.05F
               || this.hasUnfinishedAppear());
         this.lastModulesSig = sig;
         if (category != this.lastRendered) {
            this.lastRendered = category;
            this.startRowAnims(rowCount(modules.size()));
         }
         this.contentH = this.totalContentH(modules, cardW);
         float maxScroll = Math.max(0.0F, this.contentH - viewH);
         this.scrollTarget = Math.max(0.0F, Math.min(this.scrollTarget, maxScroll));
         int focusIdx = this.focusName != null ? indexOfModule(modules, this.focusName) : -1;
         boolean focusing = focusIdx >= 0 && System.currentTimeMillis() < this.focusUntilMs;
         if (this.focusScrollPending && focusIdx >= 0) {
            float focusTop = 5.0F + this.cardTopInColumn(modules, focusIdx, cardW);
            float focusH = this.baseCardHeight(modules.get(focusIdx), cardW);
            this.scrollTarget = Math.max(0.0F, Math.min(maxScroll, focusTop - (viewH - focusH) * 0.5F));
            this.focusScrollPending = false;
         }
         this.focusDimT += ((focusing ? 1.0F : 0.0F) - this.focusDimT) * (1.0F - (float)Math.exp(-dt * 8.0F));
         if (!focusing && this.focusDimT < 0.01F && !this.focusScrollPending) {
            this.focusName = null;
         }
         float sbX = x + w - 7.5F;
         float sbTrackH = viewH - 6.0F - contentEdgeInset();
         float sbY = baseY + 3.0F;
         float scrollResult = this.scrollBar.render(sbX, sbY, sbTrackH, viewH, this.contentH, this.scroll, f14);
         if (this.scrollBar.isDragging()) {
            this.scroll = scrollResult;
            this.scrollTarget = scrollResult;
         }
         this.renderCards(dc, contentX, baseY, w, cardW, col0X, col1X, viewH, f14, f15, dt, modules, true);
         this.appearInitialFrame = false;
      }
   }

   private void renderCards(DrawContext dc, float x, float y, float w, float cardW,
                            float col0X, float col1X, float viewH,
                            float alpha, float mouseAlpha, float dt,
                            List<Module> modules, boolean withAppear) {
      Scissor.enable(x, y, w - 122.0F, viewH, 2.0F);
      float slideOffset = (1.0F - mouseAlpha) * SLIDE_PX;
      float mx = Position.mouseX();
      float my = Position.mouseY();
      float lerp = 1.0F - (float)Math.exp(-dt * 16.0F);
      for (int i = 0; i < modules.size(); i++) {
         Module module = modules.get(i);
         int col = i % 2;
         float cx = col == 0 ? col0X : col1X;
         float ch = this.baseCardHeight(module, cardW);
         float cy = y + 5.0F + this.cardTopInColumn(modules, i, cardW) - this.scroll + slideOffset;
         boolean hover = mx >= cx && mx <= cx + cardW && my >= cy && my <= cy + ch;
         float hov = this.hoverAnims.getOrDefault(module.getName(), 0.0F);
         hov += ((hover ? 1.0F : 0.0F) - hov) * lerp;
         this.hoverAnims.put(module.getName(), hov);
         if (cy + ch < y - 5.0F || cy > y + viewH + 5.0F) continue;
         float rowProgress = withAppear ? this.rowAppear(col == 0 ? i / 2 : i / 2) : 1.0F;
         if (rowProgress < 0.001F) continue;
         float cardAlpha = alpha;
         if (this.focusDimT > 0.01F && !module.getName().equals(this.focusName)) {
            cardAlpha *= (1.0F - 0.75F * this.focusDimT);
         }
         float progress2 = Math.min(1.0F, rowProgress / 0.6F);
         float p2sq = progress2 * progress2;
         cy += (1.0F - progress2) * SLIDE_PX;
         boolean appearing = rowProgress < 0.999F;
         float fadeAlpha = cardAlpha * (!appearing || this.appearComposite ? 1.0F : p2sq);
         float brRadius = CARD_RADIUS;
         if (appearing) {
            float scale = 0.85F + 0.15F * progress2;
            dc.getMatrices().pushMatrix();
            dc.getMatrices().translate(cx + cardW * 0.5F, cy + ch * 0.5F);
            dc.getMatrices().scale(scale, scale);
            dc.getMatrices().translate(-(cx + cardW * 0.5F), -(cy + ch * 0.5F));
         }
         if ((i & 1) == 1 && cy + ch > y + viewH - 16.0F) {
            float t = Math.max(0.0F, Math.min(1.0F, (cy + ch - (y + viewH - 16.0F)) / 16.0F));
            brRadius = CARD_RADIUS + CARD_RADIUS * t;
         }
         int bgBase = rgba(10, 12, 16, (72.0F + 26.0F * hov) * fadeAlpha);
         Draw.rect(cx, cy, cardW, ch, bgBase, CARD_RADIUS, CARD_RADIUS, brRadius, CARD_RADIUS);
         Decelerate enableAnim = this.enableAnims.computeIfAbsent(module.getName(), n -> createAnim(220));
         enableAnim.setDirection(module.isEnabled() ? Direction.FORWARDS : Direction.BACKWARDS);
         float enableT = enableAnim.getOutput().floatValue();
         if (enableT > 0.01F) {
            int gA = ThemeManager.gradientA(30.0F * enableT * fadeAlpha);
            int gB = ThemeManager.gradientB(30.0F * enableT * fadeAlpha);
            Draw.gradientRect(cx, cy, cardW, ch, new int[]{gA, gB, gB, gA},
                  CARD_RADIUS, CARD_RADIUS, brRadius, CARD_RADIUS);
         }
         int whiteBase = rgba(255, 255, 255, (14.0F + 12.0F * hov) * fadeAlpha);
          int outTL = ColorUtil.overCol(whiteBase, ThemeManager.gradientA(65.0F * fadeAlpha), enableT);
          int outTR = ColorUtil.overCol(whiteBase, ThemeManager.gradientB(65.0F * fadeAlpha), enableT);
         Draw.gradientOutline(cx, cy, cardW, ch, 0.6F, outTL, outTR, outTR, outTL,
               CARD_RADIUS, CARD_RADIUS, brRadius, CARD_RADIUS);
         float textOffsetX = 0.0F;
         int key = module.getKey();
         if (key != -1) {
            String badge = Keyboard.keyName(key);
            if (badge != null && !badge.equals("NONE")) {
               float badgeW = Math.max(10.0F, FontsLV.MONTSERRAT_MEDIUM.width(badge, 5.5F) + 6.0F);
               float badgeX = cx + 8.0F;
               float badgeY = cy + TITLE_CY - 5.0F;
               Draw.rect(badgeX, badgeY, badgeW, 10.0F, rgba(255, 255, 255, 16.0F * fadeAlpha), 3.0F);
               Draw.outline(badgeX, badgeY, badgeW, 10.0F, 0.5F, rgba(255, 255, 255, 30.0F * fadeAlpha), 3.0F);
               float tw = FontsLV.MONTSERRAT_MEDIUM.width(badge, 5.5F);
               FontsLV.MONTSERRAT_MEDIUM.draw(badge, badgeX + (badgeW - tw) * 0.5F, badgeY + 1.75F, 5.5F,
                     rgba(255, 255, 255, 205.0F * fadeAlpha));
               textOffsetX = badgeW + 5.0F;
            }
         }
         float nameX = cx + 8.0F + textOffsetX;
         FontsLV.MONTSERRAT_MEDIUM.draw(module.getBigName(), nameX, cy + NAME_TOP, NAME_SIZE,
               rgba(255, 255, 255, (160.0F + 95.0F * enableT) * fadeAlpha));
         this.renderDesc(module, cx, cy, cardW, fadeAlpha, enableT);
         float checkX = cx + cardW - CHECK_SIZE - TOGGLE_RIGHT_PAD;
         float checkY = cy + TITLE_CY - 4.25F;
         boolean checkHover = hover && mx >= checkX - 2.0F && mx <= checkX + CHECK_SIZE + 2.0F && my >= checkY - 2.0F && my <= checkY + CHECK_SIZE + 2.0F;
         drawModuleCheck(checkX, checkY, enableT, fadeAlpha, checkHover);
         if (!module.getSettings().isEmpty()) {
            float gearW = FontsLV.WVISUAL.msdfWidth(GEAR_GLYPH, 7.0F);
            float gearX = checkX - GEAR_GAP - gearW;
            float gearY = cy + TITLE_CY - 3.5F;
            boolean gearHover = hover && mx >= gearX - 3.0F && mx <= gearX + gearW + 3.0F && my >= cy + 4.0F && my <= cy + 17.5F;
            float gHov = this.gearHoverAnims.getOrDefault(module.getName(), 0.0F);
            gHov += ((gearHover ? 1.0F : 0.0F) - gHov) * lerp;
            this.gearHoverAnims.put(module.getName(), gHov);
            float gOpen = this.gearOpenAnims.getOrDefault(module.getName(), 0.0F);
            gOpen += ((0.0F - gOpen) * (1.0F - (float)Math.exp(-dt * 12.0F)));
            this.gearOpenAnims.put(module.getName(), gOpen);
            int gearColor = ColorUtil.overCol(
                  rgba(255, 255, 255, (120.0F + 105.0F * gHov) * fadeAlpha),
                  ThemeManager.accentSoft(235.0F * fadeAlpha), gOpen);
            FontsLV.WVISUAL.msdf(GEAR_GLYPH, gearX, gearY, 7.0F, gearColor);
         }
         if (appearing) {
            dc.getMatrices().popMatrix();
         }
      }
      Scissor.disable();
   }

   public List<Module> getModules(LvCategory category) {
      if (category == null) {
         return Collections.emptyList();
      }
      Category targetCat = switch (category) {
         case VISUALS -> Category.VISUALS;
         case HUD -> Category.HUD;
         case UTILITIES -> Category.UTILITIES;
         default -> null;
      };
      if (targetCat == null) {
         return Collections.emptyList();
      }
      List<Module> all = new ArrayList<>(Client.get().moduleManager().get(targetCat));
      all.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
      return all;
   }

   public float[] cardRect(List<Module> list, int index, float x, float y, float w) {
      if (list != null && index >= 0 && index < list.size()) {
         float cw = colW(w);
         float col0X = x + 5.0F;
         float col1X = col0X + cw + 5.0F;
         float cx = index % 2 == 0 ? col0X : col1X;
         float cy = y + 5.0F + this.cardTopInColumn(list, index, cw) - this.scroll;
         float ch = this.baseCardHeight(list.get(index), cw);
         return new float[]{cx, cy, cw, ch};
      }
      return null;
   }

   public void scroll(float amount, float maxScrollH) {
      float max = Math.max(0.0F, this.contentH - maxScrollH);
      this.scrollTarget = Math.max(0.0F, Math.min(max, this.scrollTarget - (float)amount * 16.0F));
   }

   public boolean scrollbarGrab(float x, float y) {
      return this.scrollBar.tryGrab(x, y);
   }

   public void scrollbarRelease() {
      this.scrollBar.release();
   }

   public boolean hitSettingsIcon(float mx, float my, float cx, float cy, float cw, Module module) {
      if (module.getSettings().isEmpty()) {
         return false;
      }
      float gearW = FontsLV.WVISUAL.msdfWidth(GEAR_GLYPH, 7.0F);
      float checkX = cx + cw - CHECK_SIZE - TOGGLE_RIGHT_PAD;
      float gearX = checkX - GEAR_GAP - gearW;
      return mx >= gearX - 3.5F && mx <= gearX + gearW + 3.5F && my >= cy + 3.5F && my <= cy + 18.0F;
   }

   public void finishTransition() {
      this.transitioning = false;
      this.fadingOut = Collections.emptyList();
      this.fadeOutTime = 0.0F;
      this.scroll = 0.0F;
      this.scrollTarget = 0.0F;
      this.lastRendered = null;
   }

   public void focusModule(String name) {
      this.focusName = name;
      this.focusUntilMs = System.currentTimeMillis() + 5000L;
      this.focusScrollPending = true;
   }

   private List<String> descLines(Module module, float w) {
      String key = module.getName() + "|" + (int)w;
      List<String> cached = this.descLinesCache.get(key);
      if (cached != null) return cached;
      String desc = module.getDesc() != null ? module.getDesc() : "";
      List<String> lines = new ArrayList<>();
      if (!desc.isEmpty()) {
         float maxLineW = w - 16.0F - DESC_RIGHT_PAD;
         StringBuilder sb = new StringBuilder();
         for (String word : desc.split(" ")) {
            String test = sb.length() > 0 ? sb + " " + word : word;
            if (FontsLV.MONTSERRAT_MEDIUM.width(test, DESC_SIZE) > maxLineW && sb.length() > 0) {
               lines.add(sb.toString());
               sb = new StringBuilder(word);
            } else {
               if (sb.length() > 0) sb.append(' ');
               sb.append(word);
            }
         }
         if (sb.length() > 0) lines.add(sb.toString());
      }
      this.descLinesCache.put(key, lines);
      return lines;
   }

   public float baseCardHeight(Module module, float w) {
      String key = module.getName() + "|" + (int)w;
      Float cached = this.baseHeightCache.get(key);
      if (cached != null) return cached;
      List<String> lines = this.descLines(module, w);
      float h = lines.isEmpty() ? NO_DESC_H : DESC_TOP + lines.size() * DESC_LINE_H + DESC_BOTTOM_PAD;
      this.baseHeightCache.put(key, h);
      return h;
   }

   private void renderDesc(Module module, float x, float y, float w, float alpha, float enableT) {
      List<String> lines = this.descLines(module, w);
      if (!lines.isEmpty()) {
         int color = rgba(255, 255, 255, (78.0F + 27.0F * enableT) * alpha);
         float ly = y + DESC_TOP;
         for (String line : lines) {
            FontsLV.MONTSERRAT_MEDIUM.draw(line, x + 8.0F, ly, DESC_SIZE, color);
            ly += DESC_LINE_H;
         }
      }
   }

   private float cardTopInColumn(List<Module> list, int index, float w) {
      float top = 0.0F;
      for (int i = index % 2; i < index; i += 2) {
         top += this.baseCardHeight(list.get(i), w) + CARD_GAP;
      }
      return top;
   }

   private float totalContentH(List<Module> list, float w) {
      float evenH = 0.0F;
      float oddH = 0.0F;
      for (int i = 0; i < list.size(); i++) {
         float ch = this.baseCardHeight(list.get(i), w) + CARD_GAP;
         if ((i & 1) == 0) evenH += ch;
         else oddH += ch;
      }
      float maxH = Math.max(evenH, oddH);
      if (maxH > 0.0F) maxH -= CARD_GAP;
      return 10.0F + maxH;
   }

   private static void drawModuleCheck(float x, float y, float enableT, float alpha, boolean hover) {
      Draw.rect(x, y, CHECK_SIZE, CHECK_SIZE, rgba(17, 17, 18, 91.0F * alpha), CHECK_RADIUS);
      if (enableT < 0.996F) {
         float fade = alpha * (1.0F - enableT);
         float r1 = 1.2750001F;
          Draw.outline(x + r1, y + r1, CHECK_SIZE - r1 * 2.0F, CHECK_SIZE - r1 * 2.0F,
                CHECK_RING_THICKNESS, ThemeManager.accent((hover ? 80.0F : 35.0F) * fade), 2.5500002F);
          float r2 = 2.5500002F;
          Draw.outline(x + r2, y + r2, CHECK_SIZE - r2 * 2.0F, CHECK_SIZE - r2 * 2.0F,
                CHECK_RING_THICKNESS, ThemeManager.accent((hover ? 45.0F : 20.0F) * fade), 2.125F);
      }
      if (enableT > 0.004F) {
         float fillAlpha = 255.0F * enableT * alpha;
         int cA = ThemeManager.gradientA(fillAlpha);
         int cB = checkDarker(ThemeManager.gradientB(fillAlpha), 100);
         Draw.rect(x, y, CHECK_SIZE, CHECK_SIZE, cA, CHECK_RADIUS, CHECK_RADIUS, cB, cB);
         float inset = 2.125F;
         float inner = CHECK_SIZE - inset * 2.0F;
         Draw.rect(x + inset, y + inset, inner, inner, rgba(255, 255, 255, 255.0F * enableT * alpha), inner * 0.5F);
      }
   }

   private static int checkDarker(int color, int amount) {
      int a = color >>> 24 & 0xFF;
      int r = Math.max(0, (color >> 16 & 0xFF) - amount);
      int g = Math.max(0, (color >> 8 & 0xFF) - amount);
      int b = Math.max(0, (color & 0xFF) - amount);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static int rgba(int r, int g, int b, float a) {
      int ai = Math.max(0, Math.min(255, Math.round(a)));
      return ai <= 0 ? 0 : new Color(r, g, b, ai).getRGB();
   }

   private static float contentEdgeInset() {
      float radius = 12.0F;
      float inner = radius - 1.0F;
      float edge = radius - (float) Math.sqrt(Math.max(0.0F, radius * radius - inner * inner));
      return Math.max(0.0F, edge + 1.5F - 3.0F);
   }

   private static float colW(float w) {
      return (w - 5.0F - 10.0F) * 0.5F;
   }

   private static int rowCount(int n) {
      return (n + 1) / 2;
   }

   private static int modulesSignature(List<Module> list) {
      int n = list.size();
      if (!list.isEmpty()) {
         n = n * 31 + list.get(0).getName().hashCode();
         n = n * 31 + list.get(list.size() - 1).getName().hashCode();
      }
      return n;
   }

   private static int indexOfModule(List<Module> list, String name) {
      for (int i = 0; i < list.size(); i++) {
         if (list.get(i).getName().equals(name)) return i;
      }
      return -1;
   }

   private static Decelerate createAnim(int ms) {
      Decelerate d = new Decelerate();
      d.setMs(ms);
      d.setValue(1.0);
      d.setDirection(Direction.BACKWARDS);
      d.counter.setTime(System.currentTimeMillis() - 10000L);
      return d;
   }

   private void startRowAnims(int count) {
      this.rowAppearAnims.clear();
      this.appearFadeMs = this.quickAppear ? QUICK_ROW_FADE_MS : ROW_FADE_MS;
      this.quickAppear = false;
      this.appearBaseMs = System.currentTimeMillis();
      this.appearInitialFrame = true;
   }

   private boolean hasUnfinishedAppear() {
      for (Decelerate d : this.rowAppearAnims.values()) {
         if (d.getOutput().floatValue() < 0.999F) return true;
      }
      return false;
   }

   private float rowAppear(int row) {
      Decelerate d = this.rowAppearAnims.get(row);
      if (d == null) {
         long t = this.appearInitialFrame ? this.appearBaseMs : System.currentTimeMillis();
         d = new Decelerate();
         d.setMs(this.appearFadeMs);
         d.setValue(1.0);
         d.counter.setTime(t);
         this.rowAppearAnims.put(row, d);
      }
      return Math.max(0.0F, Math.min(1.0F, d.getOutput().floatValue()));
   }
}

package ru.decide.ui.lv;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import ru.decide.Client;
import ru.decide.module.api.Module;
import ru.decide.utils.render.gif.GifRenderer;
import ru.decide.module.impl.display.ClickGui;
import ru.decide.ui.fonts.FontsLV;
import ru.decide.ui.lv.settings.LvBindSetting;
import ru.decide.ui.lv.settings.LvTextSetting;
import ru.decide.ui.lv.settings.SettingWidget;
import ru.decide.ui.theme.AccentGradient;
import ru.decide.ui.theme.ClientAccent;
import ru.decide.ui.theme.ThemeManager;
import ru.decide.ui.util.Position;
import ru.decide.ui.util.anim.Decelerate;
import ru.decide.ui.util.anim.Direction;
import ru.decide.ui.util.anim.GuiMotionAnimation;
import ru.decide.utils.annotation.IMinecraft;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.other.GuiSounds;
import ru.decide.utils.render.Draw;
import ru.decide.utils.render.Render2D;
import ru.decide.utils.render.RenderUtil;
import ru.decide.utils.render.ScreenBlur;

public class LvGui extends Screen implements IMinecraft {
   private static final String[] EVENT_SUBS = new String[]{"Discord", "Telegram"};
   private static final String[] EVENT_SUB_IMAGES = new String[]{"decide:images/discord.png", "decide:images/telegram.png"};
   private static final float PANEL_W = 430.0F;
   private static final float PANEL_H = 290.0F;
   private static final float SIDEBAR_W = 110.0F;
   private static final float CONTENT_X_OFF = 117.0F;
   private static final float CONTENT_INSET = 122.0F;
   private static final float CONTENT_HEIGHT = 280.0F;
   private static final float CONTENT_Y_OFFSET = 5.0F;
   private static final float CAT_COL_TOP = 34.0F;
   private static final float CAT_HEADER_H = 20.0F;
   private static final float CAT_SUB_GAP = 4.0F;
   private static final float CAT_SUB_ROW_H = 18.0F;
   private static final float CAT_OTHERS_GAP = 12.0F;
   private static final float CAT_EVENTS_GAP = 3.0F;
   private static final float CAT_OTHER_ROW_H = 19.0F;
   private static final float CATEGORY_FADE_SEC = 0.15F;
   private static final LvCategory[] CATEGORIES = new LvCategory[]{LvCategory.VISUALS, LvCategory.HUD, LvCategory.UTILITIES, LvCategory.EVENTS, LvCategory.THEMES};
   private static final String RU_LAYOUT = "\u0439\u0446\u0443\u043a\u0435\u043d\u0433\u0448\u0449\u0437\u0445\u044a\u0444\u044b\u0432\u0430\u043f\u0440\u043e\u043b\u0434\u0436\u044d\u044f\u0447\u0441\u043c\u0438\u0442\u044c\u0431\u044e\u0451";
   private static final String EN_LAYOUT = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`";
   public static final LvGui INSTANCE = new LvGui();
   private final LvModuleList moduleList = new LvModuleList();
   private final LvSearchField search = new LvSearchField();
   private final LvBindPopup bindPopup = new LvBindPopup();
   private final LvSettingsPopup settingsPopup = new LvSettingsPopup();
   private final LvThemesRenderer themesRenderer = new LvThemesRenderer();
   private final LvEventsRenderer eventsRenderer = new LvEventsRenderer();
   private final GuiMotionAnimation screenAnim = new GuiMotionAnimation();
   private final Map<LvCategory, Decelerate> categoryAnims = new EnumMap<>(LvCategory.class);
   private final Decelerate modulesHeaderAnim = createAnim(200);
   private final Decelerate eventsHeaderAnim = createAnim(200);
   private final Decelerate placeholderAnim = createAnim(200);
   private final Decelerate subTextAnim = createAnim(300);
   private final float[] eventsSubT = new float[EVENT_SUBS.length];
   private int eventsSub = 0;
   private float themesRowT = 1.0F;
   private LvCategory targetCategory = null;
   private LvCategory contentCategory = null;
   private String oldSubText = "\u041d\u0435 \u0432\u044b\u0431\u0440\u0430\u043d\u043e";
   private String newSubText = "\u041d\u0435 \u0432\u044b\u0431\u0440\u0430\u043d\u043e";
   private boolean subTextAnimDone = true;
   private float categoryT = 1.0F;
   private long lastNs = System.nanoTime();
   private float scaleFix = 1.0F;
   private boolean soundPlayed;
   double lastMouseX;
   double lastMouseY;

   private LvGui() {
      super(Text.literal("LvGui"));
   }

   private static float clamp(float f, float f2, float f3) {
      return Math.max(f2, Math.min(f3, f));
   }

   private static int color(int n, int n2, int n3, int n4, float f) {
      int n5 = Math.max(0, Math.min(255, Math.round(n4 * f)));
      return n5 <= 0 ? 0 : n5 << 24 | n << 16 | n2 << 8 | n3;
   }

   private static Decelerate createAnim(int n) {
      Decelerate decelerate = new Decelerate().setMs(n).setValue(1.0);
      decelerate.setDirection(Direction.BACKWARDS);
      decelerate.counter.setTime(System.currentTimeMillis() - 10000L);
      return decelerate;
   }

   private static String layoutNormalize(String string) {
      StringBuilder stringBuilder = new StringBuilder(string.length());
      for (int i = 0; i < string.length(); i++) {
         char c = string.charAt(i);
         int n = RU_LAYOUT.indexOf(c);
         stringBuilder.append(n >= 0 ? EN_LAYOUT.charAt(n) : c);
      }
      return stringBuilder.toString();
   }

   private Decelerate getCategoryAnim(LvCategory category) {
      return this.categoryAnims.computeIfAbsent(category, c -> createAnim(200));
   }

   private boolean isModuleView() {
      return this.contentCategory != null && this.contentCategory != LvCategory.THEMES && this.contentCategory != LvCategory.EVENTS;
   }

   private static boolean ctrlHeld() {
      long l = IMinecraft.mc.getWindow().getHandle();
      return org.lwjgl.glfw.GLFW.glfwGetKey(l, 341) == 1 || org.lwjgl.glfw.GLFW.glfwGetKey(l, 345) == 1;
   }

   private List<Module> filteredModules(LvCategory category) {
      String string = this.search.getText().trim().toLowerCase(Locale.ROOT);
      if (string.isEmpty()) {
         return this.moduleList.getModules(category);
      } else {
         String string2 = layoutNormalize(string);
         String string3 = string.replace(" ", "");
         String string4 = string2.replace(" ", "");
         ArrayList<Module> arrayList = new ArrayList<>();
         for (Module module : Client.get().moduleManager().values()) {
            String string5 = module.getName().toLowerCase(Locale.ROOT);
            String string6 = string5.replace(" ", "");
            if (string5.contains(string) || string5.contains(string2) || string6.contains(string3) || string6.contains(string4)) {
               arrayList.add(module);
            }
         }
         return arrayList;
      }
   }

   private Module moduleAtCursor() {
      if (!this.isModuleView()) {
         return null;
      } else {
         float f = PANEL_W;
         float f2 = Position.screenWidth() / 2.0F - f / 2.0F;
         float f3 = Position.screenHeight() / 2.0F - PANEL_H / 2.0F;
         float f4 = Position.mouseX();
         float f5 = Position.mouseY();
         float f6 = f2 + CONTENT_X_OFF;
         float f7 = f3 + 5.0F + 30.0F;
         float f8 = f - CONTENT_INSET;
         float f9 = 250.0F;
         if (f4 >= f6 && f4 <= f6 + f8 && f5 >= f7 && f5 <= f7 + f9) {
            List<Module> list = this.filteredModules(this.contentCategory);
            for (int i = 0; i < list.size(); i++) {
               float[] rect = this.moduleList.cardRect(list, i, f6, f7, f8);
               if (rect != null && f4 >= rect[0] && f4 <= rect[0] + rect[2] && f5 >= rect[1] && f5 <= rect[1] + rect[3]) {
                  return list.get(i);
               }
            }
            return null;
         } else {
            return null;
         }
      }
   }

   private static int categoryIndex(LvCategory category) {
      for (int i = 0; i < CATEGORIES.length; i++) {
         if (CATEGORIES[i] == category) {
            return i;
         }
      }
      return 0;
   }

   private LvCategory categoryButtonAt(float f, float f2, float f3, float f4) {
      float f5 = f + 5.0F;
      float f6 = SIDEBAR_W;
      if (f3 >= f5 + 4.0F && f3 <= f5 + f6 - 4.0F) {
         float f7 = f2 + 2.0F;
         float f8 = f7 + CAT_COL_TOP + CAT_HEADER_H + CAT_SUB_GAP;
         for (int i = 0; i < 4; i++) {
            float f9 = f8 + i * CAT_SUB_ROW_H;
            if (f4 >= f9 && f4 <= f9 + CAT_SUB_ROW_H) {
               return CATEGORIES[i];
            }
         }
         float f10 = f8 + 4.0F * CAT_SUB_ROW_H + CAT_EVENTS_GAP + CAT_HEADER_H + CAT_SUB_GAP + 2.0F * CAT_SUB_ROW_H + CAT_OTHERS_GAP;
         if (this.themesRowT > 0.5F && f4 >= f10 && f4 <= f10 + CAT_OTHER_ROW_H) {
            return LvCategory.THEMES;
         }
      }
      return null;
   }

   private int eventsSubAt(float f, float f2, float f3, float f4) {
      float f5 = f + 5.0F;
      float f6 = SIDEBAR_W;
      if (f3 >= f5 + 4.0F && f3 <= f5 + f6 - 4.0F) {
         float f7 = f2 + 2.0F;
         float f8 = f7 + CAT_COL_TOP + CAT_HEADER_H + CAT_SUB_GAP + 4.0F * CAT_SUB_ROW_H + CAT_EVENTS_GAP + CAT_HEADER_H + CAT_SUB_GAP;
         for (int i = 0; i < EVENT_SUBS.length; i++) {
            float f9 = f8 + i * CAT_SUB_ROW_H;
            if (f4 >= f9 && f4 <= f9 + CAT_SUB_ROW_H) {
               return i;
            }
         }
      }
      return -1;
   }

   private void renderCategoryPanel(float f, float f2, float f3, float f4) {
      float f9 = f;
      float f10 = SIDEBAR_W;
      float f11 = f + 9.0F;
      float f12 = f2 + 3.0F;
      float f13 = 26.0F;
      LvRectUtil.drawPanelBg(f9, f12, f10, f13, 12.0F, 0.0F, 0.0F, 0.0F, f4);
      float f14 = f12 + f13 * 0.5F;
      String string = "x";
      float f15 = 11.7F;
      float f16 = FontsLV.DECIDE.width(string, f15);
        String string1 = "DecideVisuals";
       float f17 = 14.3F;
       float f18 = FontsLV.SMALL_PIXEL.width(string1, f17);
       float f19 = f9 + (f10 - (f16 + 7.8F + f18)) * 0.5F;
       AccentGradient.msdfIcon("decidevisuals", string, f19, f14 - f15 * 0.5F + 0.5F, f15, 225.0F * f4, 0.1F);
       FontsLV.SMALL_PIXEL.draw(string1, f19 + f16 + 7.8F, f14 - f17 * 0.5F + 0.5F, f17, color(255, 255, 255, 255, f4));
      float f20 = f2 + CAT_COL_TOP;
      float f21 = f20 + 10.0F;
      float f22 = this.modulesHeaderAnim.getOutput().floatValue();
      if (f22 > 0.004F) {
         Draw.rect(f9 + 4.0F, f20 + 4.5F, f10 - 8.0F, 13.333F, color(255, 255, 255, Math.round(16.0F * f22), f4), 5.0F);
      }
      String string2 = "h";
      float f23 = FontsLV.DECIDE.width(string2, 8.0F);
      AccentGradient.msdfIcon("decidevisuals", string2, f11, f21 - 4.0F + 1.5F, 8.0F, (200.0F + 55.0F * f22) * f4, 0.15F);
      FontsLV.MONTSERRAT_MEDIUM.draw("Modules", f11 + f23 + 6.0F, f21 - 4.0F + 0.5F, 8.0F, color(255, 255, 255, Math.round(215.0F + 40.0F * f22), f4));
      float f24 = f2 + CAT_COL_TOP + CAT_HEADER_H + CAT_SUB_GAP;
      float f25 = f11 + 3.0F;
      float f26 = f25 + 9.0F;
      float f27 = f24 + 9.0F;
      float f28 = f24 + 4.0F * CAT_SUB_ROW_H + 9.0F;
      Draw.rect(f25, f27, 1.0F, f28 - f27, color(255, 255, 255, 36, f4));

      for (int i = 0; i < 4; i++) {
         LvCategory category = CATEGORIES[i];
         float f29 = f24 + i * CAT_SUB_ROW_H;
         float f30 = f29 + 9.0F;
         float f31 = this.getCategoryAnim(category).getOutput().floatValue();
         int n2 = Math.min(255, 140 + Math.round(f31 * 115.0F));
         int n3 = color(255, 255, 255, n2, f4);
         String string3 = String.valueOf(category.getIcon());
         float f32 = FontsLV.DECIDE.width(string3, 7.0F);
         float f33 = (float) i / 2.0F;
         if (f31 > 0.01F) {
            float f34 = (f32 + 5.0F + FontsLV.MONTSERRAT_MEDIUM.width(category.getDisplayName(), 7.0F)) * f31;
            AccentGradient.fillHorizontal(f26, f30 + 8.5F - 2.0F, f34, 0.75F, 0.625F, 88.0F * f31 * f4);
         }
         AccentGradient.msdfIcon("decidevisuals", string3, f26, f30 - 3.5F + 1.5F - 2.0F, 7.0F, n2 * f4, f33);
         FontsLV.MONTSERRAT_MEDIUM.draw(category.getDisplayName(), f26 + f32 + 5.0F, f30 - 3.5F + 0.5F - 2.0F, 7.0F, n3);
         String string4 = String.valueOf(this.moduleList.getModules(category).size());
         float f35 = FontsLV.MONTSERRAT_MEDIUM.width(string4, 5.5F);
         FontsLV.MONTSERRAT_MEDIUM.draw(string4, f9 + f10 - 10.0F - f35, f30 - 2.75F + 0.5F - 2.0F, 5.5F, color(255, 255, 255, Math.round(80.0F + 80.0F * f31), f4));
      }

      float f36 = f24 + 4.0F * CAT_SUB_ROW_H + CAT_EVENTS_GAP;
      float f37 = f36 + 10.0F;
      float f38 = this.eventsHeaderAnim.getOutput().floatValue();
      if (f38 > 0.004F) {
         Draw.rect(f9 + 4.0F, f36 + 4.5F, f10 - 8.0F, 13.333F, color(255, 255, 255, Math.round(16.0F * f38), f4), 5.0F);
      }
      String string5 = "e";
      float f39 = FontsLV.DECIDE.width(string5, 8.0F);
      AccentGradient.msdfIcon("decidevisuals", string5, f11, f37 - 4.0F + 1.5F, 8.0F, (200.0F + 55.0F * f38) * f4, 0.6F);
      FontsLV.MONTSERRAT_MEDIUM.draw("Information", f11 + f39 + 6.0F, f37 - 4.0F + 0.5F, 8.0F, color(255, 255, 255, Math.round(215.0F + 40.0F * f38), f4));
      float f40 = f36 + CAT_HEADER_H + CAT_SUB_GAP;
      float f41 = f40 + 9.0F;
      float f42 = f40 + (EVENT_SUBS.length - 1) * CAT_SUB_ROW_H + 9.0F;
      Draw.rect(f25, f41, 1.0F, f42 - f41, color(255, 255, 255, 36, f4));

      for (int i = 0; i < EVENT_SUBS.length; i++) {
         float f43 = f40 + i * CAT_SUB_ROW_H;
         float f44 = f43 + 9.0F;
         float f45 = this.eventsSubT[i];
         int n4 = Math.min(255, 140 + Math.round(f45 * 115.0F));
         int n5 = color(255, 255, 255, n4, f4);
         float f46 = 7.0F;
         if (f45 > 0.01F) {
            float f47 = (f46 + 5.0F + FontsLV.MONTSERRAT_MEDIUM.width(EVENT_SUBS[i], 7.0F)) * f45;
          AccentGradient.fillHorizontal(f26, f44 + 8.5F - 2.0F, f47, 0.75F, 0.625F, 88.0F * f45 * f4);
          }
          Draw.texture(Identifier.of(EVENT_SUB_IMAGES[i]), f26, f44 - 3.5F + 1.5F - 2.0F, f46, f46, n5);
          FontsLV.MONTSERRAT_MEDIUM.draw(EVENT_SUBS[i], f26 + f46 + 5.0F, f44 - 3.5F + 0.5F - 2.0F, f46, n5);
      }

      float f48 = f40 + EVENT_SUBS.length * CAT_SUB_ROW_H + CAT_OTHERS_GAP;
      if (this.themesRowT > 0.01F) {
         float f49 = f48 + CAT_OTHER_ROW_H * 0.5F;
         float f50 = this.getCategoryAnim(LvCategory.THEMES).getOutput().floatValue();
         int n6 = Math.round(Math.min(255.0F, (140.0F + f50 * 115.0F)) * this.themesRowT);
         String string6 = String.valueOf(LvCategory.THEMES.getIcon());
         float f51 = FontsLV.DECIDE.width(string6, 7.5F);
         AccentGradient.msdfIcon("decidevisuals", string6, f11, f49 - 3.75F + 1.5F - 2.0F, 7.5F, n6 * f4, 0.85F);
         FontsLV.MONTSERRAT_MEDIUM.draw("Themes", f11 + f51 + 6.0F, f49 - 3.5F + 0.5F - 2.0F, 7.0F, color(255, 255, 255, n6, f4));
      }
   }

   private void renderModuleHeader(DrawContext m, float f, float f2, float f3, float f4, float dt) {
      float f5 = f + CONTENT_X_OFF;
      float f6 = f2 + 5.0F;
      float f7 = f3 - CONTENT_INSET;
      LvRectUtil.drawPanelBg(f5, f6, f7, 26.0F, 0.0F, 12.0F, 0.0F, 0.0F, f4);
      float f9 = f5 + f7 - 6.0F - 18.0F;
      float f10 = f6 + (26.0F - 18.0F) * 0.5F;
      this.renderDiscordAvatar(f4, f9, f10, 18.0F);
      String string2 = mc.getSession().getUsername();
      String string3 = "";
      float f12 = FontsLV.MONTSERRAT_MEDIUM.width(string2, 6.5F);
      float f12b = FontsLV.MONTSERRAT_MEDIUM.width(string3, 5.0F);
      float f13 = f10 + (18.0F - (6.5F + 1.0F + 5.0F)) * 0.5F;
      float f14 = f9 - 5.0F;
      FontsLV.MONTSERRAT_MEDIUM.draw(string2, f14 - f12, f13, 6.5F, color(255, 255, 255, 230, f4));
      FontsLV.MONTSERRAT_MEDIUM.draw(string3, f14 - f12b, f13 + 6.5F + 1.0F, 5.0F, ClientAccent.accentSoft(195.0F * f4));
      float f15 = f5 + 10.0F;
      float f16 = Math.max(60.0F, f14 - Math.max(f12, f12b) - 10.0F - f15);
      float f17 = f6 + (26.0F - 14.0F) * 0.5F;
      this.search.render(m, f15, f17, f16, 14.0F, f4, Position.mouseX(), Position.mouseY(), dt);
   }

    private void renderNoResults(float f, float f2, float f3, float f4) {
      float f5 = f + CONTENT_X_OFF;
      float f6 = f3 - CONTENT_INSET;
      float f7 = f2 + 5.0F + 30.0F;
      float f8 = 250.0F;
      String string = "\u041d\u0438\u0447\u0435\u0433\u043e \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u043e";
      float f9 = 6.5F;
      float f10 = FontsLV.MONTSERRAT_MEDIUM.width(string, f9);
      FontsLV.MONTSERRAT_MEDIUM.draw(string, f5 + (f6 - f10) * 0.5F, f7 + f8 * 0.5F - 3.0F, f9, color(255, 255, 255, 110, f4));
   }

   private void renderNoCategoryPlaceholder(float f, float f2, float f3, float f4) {
      float f5 = f4 * this.placeholderAnim.getOutput().floatValue();
      if (f5 <= 0.01F) {
         return;
      }
      float f6 = f + CONTENT_X_OFF;
      float f7 = f2 + CONTENT_Y_OFFSET;
      float f8 = f3 - CONTENT_INSET;
      LvRectUtil.drawPanelBg(f6, f7, f8, CONTENT_HEIGHT, 0.0F, 12.0F, 12.0F, 0.0F, f5);
      float gifSize = 60.0F;
      float gifX = f6 + (f8 - gifSize) * 0.5F;
      float gifY = f7 + CONTENT_HEIGHT * 0.5F - gifSize * 0.5F - 10.0F;
      GifRenderer.draw(gifX, gifY, gifSize, gifSize, 8.0F, "decide:gif/kity.gif", f5);
      String string2 = "\u041e\u0442\u043a\u0440\u043e\u0439\u0442\u0435 \u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u044e \u0447\u0442\u043e\u0431\u044b \u043d\u0430\u0447\u0430\u0442\u044c";
      float f11 = 6.0F;
      float f12 = FontsLV.MONTSERRAT_MEDIUM.width(string2, f11);
      FontsLV.MONTSERRAT_MEDIUM.draw(string2, f6 + (f8 - f12) * 0.5F, gifY + gifSize + 8.0F, f11, color(255, 255, 255, 100, f5));
   }

   private void renderDiscordAvatar(float f4, float f, float f2, float f3) {
      if (LvDiscordAvatar.texture() == null) {
         Draw.rect(f, f2, f3, f3, ThemeManager.rgba(0, 90.0F * f4), f3 * 0.5F);
         Draw.outline(f, f2, f3, f3, 0.8F, ThemeManager.rgba(16777215, 30.0F * f4), f3 * 0.5F);
      }
   }

   private void renderPanel(DrawContext m) {
      float f3 = this.screenAnim.scale();
      float f4 = this.screenAnim.alpha();
      float f5 = f3;
      float f6 = f4;
      float f7 = PANEL_W;
      float f8 = PANEL_H;
      float f9 = Position.screenWidth() / 2.0F - f7 / 2.0F;
      float f10 = Position.screenHeight() / 2.0F - f8 / 2.0F;
       Draw.rect(-5.0F, -5.0F, Position.screenWidth() + 10.0F, Position.screenHeight() + 10.0F, color(0, 0, 0, 60, f4));
      LvRectUtil.drawClientRect(f9, f10, f7, f8, 12.0F, f6);
      LvRectUtil.drawPanelBg(f9 + 5.0F, f10 + 5.0F, SIDEBAR_W, f8 - 10.0F, 12.0F, 0.0F, 0.0F, 12.0F, f6);
      LvRectUtil.drawPanelBg(f9 + CONTENT_X_OFF, f10 + CONTENT_Y_OFFSET, f7 - CONTENT_INSET, CONTENT_HEIGHT, 0.0F, 12.0F, 12.0F, 0.0F, f6);
      long l = System.nanoTime();
      float f11 = Math.min(0.1F, (float) (l - this.lastNs) / 1.0E9F);
      this.lastNs = l;
      this.renderCategoryPanel(f9 + 5.0F, f10 + 2.0F, f8, f6);
      this.updateCategoryCrossfade(f11);
      this.updateEventsSubAnim(f11);
      this.themesRowT = Math.min(1.0F, this.themesRowT + f11 / CATEGORY_FADE_SEC);
      float f12 = f6 * this.categoryT;
      float f13 = this.categoryT;
      boolean bl = !this.bindPopup.isVisible() && !this.settingsPopup.isVisible();
      this.moduleList.setAppearComposite(bl);
      this.themesRenderer.setAppearComposite(bl);
      if (f12 > 0.01F && this.contentCategory == LvCategory.EVENTS) {
         this.eventsRenderer.render(m, f9, f10, f7, f12, f11);
      } else if (f12 > 0.01F && this.contentCategory == LvCategory.THEMES) {
         this.themesRenderer.render(m, f9, f10, f7, f12, f13, f11);
      } else if (f12 > 0.01F && this.contentCategory != null) {
         List<Module> list = this.filteredModules(this.contentCategory);
         this.moduleList.render(m, f9, f10, f7, f12, f13, f11, this.contentCategory, list);
         if (list.isEmpty() && this.search.hasText()) {
            this.renderNoResults(f9, f10, f7, f12);
         }
      }
      if (this.contentCategory == null && this.targetCategory == null) {
         this.renderNoCategoryPlaceholder(f9, f10, f7, f6);
      }
       float fh = this.modulesHeaderAnim.getOutput().floatValue();
      if (this.isModuleView() && f6 > 0.01F && fh > 0.004F) {
         this.renderModuleHeader(m, f9, f10, f7, f6 * fh, f11);
      }
      if (this.settingsPopup.isVisible()) {
         this.settingsPopup.render(m, f6);
      }
      if (this.bindPopup.isVisible()) {
         this.bindPopup.render(m, f6);
      }
   }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
       this.scaleFix = 2.0F / mc.getWindow().getScaleFactor();
       mouseX = (int) (mouseX / this.scaleFix);
       mouseY = (int) (mouseY / this.scaleFix);
       this.lastMouseX = mouseX;
       this.lastMouseY = mouseY;
       this.screenAnim.updateFrame();
      if (this.screenAnim.isCloseFinished() && mc.currentScreen == this) {
         this.screenAnim.snapClosed();
         mc.setScreen(null);
         return;
      }
      this.renderOverlay(context);
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

      public void renderOverlay(DrawContext context) {
         if (context != null) {
            context.getMatrices().pushMatrix();
         }
         Render2D.beginOverlay();
         float f6 = this.screenAnim.alpha();
         int screenWidth = (int) (mc.getWindow().getScaledWidth() / this.scaleFix);
         int screenHeight = (int) (mc.getWindow().getScaledHeight() / this.scaleFix);
         ScreenBlur.capture(2);
         RenderUtil.Blur.blur(0, 0, screenWidth, screenHeight, f6, ColorUtil.getColor(0, f6 * 0.2F));
         this.renderPanel(context);
         Render2D.endOverlay();
         if (context != null) {
            context.getMatrices().popMatrix();
         }
      }

    @Override
    protected void init() {
       this.screenAnim.startOpening();
      this.moduleList.warmup();
      this.targetCategory = null;
      this.contentCategory = null;
      this.categoryT = 0.0F;
      this.placeholderAnim.setDirection(Direction.FORWARDS);
      this.modulesHeaderAnim.setDirection(Direction.BACKWARDS);
      this.subTextAnim.setDirection(Direction.FORWARDS);
      this.subTextAnimDone = false;
      for (LvCategory category4 : LvCategory.values()) {
         this.getCategoryAnim(category4).setDirection(Direction.BACKWARDS);
      }
      if (!this.soundPlayed) {
         GuiSounds.open();
         this.soundPlayed = true;
      }
   }

    private void onClose() {
       this.releaseAllDrags();
       this.settingsPopup.close();
       this.search.blur();
       GuiSounds.close();
       if (mc.currentScreen == this) {
          mc.setScreen(null);
       }
    }

   @Override
   public void close() {
      this.onClose();
   }

   private void releaseAllDrags() {
      this.settingsPopup.releaseDrags();
      this.bindPopup.releaseDrag();
      this.moduleList.scrollbarRelease();
      this.eventsRenderer.scrollbarRelease();
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return false;
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   public void removed() {
      this.releaseAllDrags();
      this.settingsPopup.close();
      this.search.blur();
      this.soundPlayed = false;
      super.removed();
   }

   @Override
   public boolean mouseClicked(Click click, boolean doubled) {
      if (!this.screenAnim.canInteract()) {
         return true;
      } else {
         float f = PANEL_W;
         float f2 = PANEL_H;
         float f3 = Position.screenWidth() / 2.0F - f / 2.0F;
         float f4 = Position.screenHeight() / 2.0F - f2 / 2.0F;
         float f5 = Position.mouseX();
         float f6 = Position.mouseY();
         int button = click.button();
         if (button == 2) {
            if (this.settingsPopup.isOpen() && this.settingsPopup.middleClick(f5, f6)) {
               return true;
            } else {
               Module module2 = this.moduleAtCursor();
               if (module2 != null) {
                  this.bindPopup.open(module2, f5, f6);
                  return true;
               }
            }
         }
         if (this.bindPopup.isOpen() && this.bindPopup.mouseBind(button)) {
            return true;
         } else if (this.bindPopup.isOpen() && button == 0) {
            this.bindPopup.click(f5, f6);
            return true;
         } else if (this.settingsPopup.isOpen() && button == 0) {
             if (this.settingsPopup.contains(f5, f6)) {
                 this.settingsPopup.click(f5, f6);
                 return true;
             } else {
                 this.settingsPopup.close();
                 return true;
             }
          } else if (this.settingsPopup.isOpen() && button == 1 && this.settingsPopup.contains(f5, f6)) {
             return true;
          } else if (this.settingsPopup.isOpen() && button == 1) {
             this.settingsPopup.close();
             return true;
         } else if (this.isModuleView() && this.search.mouseClicked(f5, f6, button)) {
            return true;
         } else {
            if (this.contentCategory == LvCategory.EVENTS && button == 0) {
               if (this.eventsRenderer.scrollbarGrab(f5, f6)) {
                  return true;
               }
               if (this.eventsRenderer.click(f5, f6)) {
                  return true;
               }
            }
            if (this.contentCategory == LvCategory.THEMES && button == 0 && this.themesRenderer.click(f3, f4, f, f5, f6)) {
                return true;
            }
            if (button == 0 || button == 1) {
                  boolean bl = this.isModuleView();
                  if (bl && this.moduleList.scrollbarGrab(f5, f6)) {
                     return true;
                  }
                   float f7 = f3 + CONTENT_X_OFF;
                   float f8 = f4 + 5.0F + 30.0F;
                   float f9 = f - CONTENT_INSET;
                   float f10 = 250.0F;
                  if (bl) {
                     List<Module> list = this.filteredModules(this.contentCategory);
                     if (f5 >= f7 && f5 <= f7 + f9 && f6 >= f8 && f6 <= f8 + f10) {
                        for (int i = 0; i < list.size(); i++) {
                           Module module = list.get(i);
                           float[] rect = this.moduleList.cardRect(list, i, f7, f8, f9);
                           if (rect != null) {
                              float f11 = rect[0];
                              float f12 = rect[1];
                              float f13 = rect[2];
                              float f14 = rect[3];
                              if (f5 >= f11 && f5 <= f11 + f13 && f6 >= f12 && f6 <= f12 + f14) {
                                 if (button == 1) {
                                    if (!module.getSettings().isEmpty()) {
                                       boolean bl2 = this.settingsPopup.toggle(module, f5, f6, f7, f8, f9, f10);
                                       GuiSounds.expand(bl2);
                                    }
                                    return true;
                                 }
                                 if (ctrlHeld() && this.search.hasText()) {
                                    this.moduleList.focusModule(module.getName());
                                    LvCategory lvCategory = LvCategory.getCategoryForModule(module.getCategory());
                                    if (this.targetCategory != lvCategory) {
                                       GuiSounds.category(categoryIndex(lvCategory), CATEGORIES.length);
                                       this.selectCategory(lvCategory);
                                    } else {
                                       this.search.setText("");
                                       this.search.blur();
                                    }
                                    return true;
                                 }
                                 if (this.moduleList.hitSettingsIcon(f5, f6, f11, f12, f13, module)) {
                                    boolean bl3 = this.settingsPopup.toggle(module, f5, f6, f7, f8, f9, f10);
                                    GuiSounds.expand(bl3);
                                    return true;
                                 }
                                 if (!(module instanceof ClickGui)) {
                                    module.toggle();
                                 }
                                 return true;
                              }
                           }
                        }
                     }
                  }
                  if (button == 1) {
                     return true;
                  }
               }
               if (button != 0) {
                  return super.mouseClicked(click, doubled);
               } else {
                  int n = this.eventsSubAt(f3, f4, f5, f6);
                  if (n >= 0) {
                     this.selectEventsSub(n);
                     return true;
                  }
                  LvCategory category = this.categoryButtonAt(f3, f4, f5, f6);
                  if (category != null) {
                     if (category != this.targetCategory) {
                        GuiSounds.category(categoryIndex(category), CATEGORIES.length);
                     }
                     this.selectCategory(category);
                     return true;
                  } else {
                     return super.mouseClicked(click, doubled);
                  }
               }
         }
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (!this.screenAnim.canInteract()) {
         return true;
      } else if (this.bindPopup.isOpen()) {
         this.bindPopup.close();
         return true;
      } else {
         float f = PANEL_W;
         float f2 = Position.screenWidth() / 2.0F - f / 2.0F;
         float f3 = Position.screenHeight() / 2.0F - PANEL_H / 2.0F;
         float f4 = f2 + CONTENT_X_OFF;
         float f5 = f3 + 5.0F;
         float f6 = f - CONTENT_INSET;
         float f7 = CONTENT_HEIGHT;
         double d = Position.mouseX();
         double d2 = Position.mouseY();
         if (this.settingsPopup.isOpen() && this.settingsPopup.scroll((float) d, (float) d2, verticalAmount)) {
            return true;
         } else if (d >= f4 && d <= f4 + f6 && d2 >= f5 && d2 <= f5 + f7) {
            if (this.contentCategory == LvCategory.EVENTS) {
               this.eventsRenderer.scroll((float) verticalAmount, f7);
            } else if (this.contentCategory == LvCategory.THEMES) {
                this.themesRenderer.scroll((float) verticalAmount, f7);
            } else {
               this.settingsPopup.close();
               this.moduleList.scroll((float) verticalAmount, f7 - 30.0F);
            }
            return true;
         } else {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
         }
      }
   }

   @Override
   public boolean mouseReleased(Click click) {
      if (!this.screenAnim.canInteract()) {
         return true;
      } else {
         if (click.button() == 0) {
            this.settingsPopup.releaseDrags();
            this.bindPopup.releaseDrag();
         }
         this.moduleList.scrollbarRelease();
         this.eventsRenderer.scrollbarRelease();
         this.search.mouseReleased(click.button());
         return super.mouseReleased(click);
      }
   }

   @Override
   public boolean keyPressed(KeyInput input) {
      if (!this.screenAnim.canInteract()) {
         return true;
      } else if (this.bindPopup.isOpen()) {
         if (this.bindPopup.keyPressed(input.key())) {
            return true;
         } else {
            if (input.key() == 256) {
               this.bindPopup.close();
            }
            return true;
         }
      } else {
         for (SettingWidget settingWidget : this.settingsPopup.widgets()) {
            if (settingWidget instanceof LvBindSetting lvBindSetting && lvBindSetting.isListening()) {
               int n = input.key();
               lvBindSetting.setKey((n == 256 || n == 261) ? -1 : n);
               lvBindSetting.setListening(false);
               return true;
            }
         }
         if (this.settingsPopup.isOpen()) {
            for (SettingWidget settingWidget : this.settingsPopup.widgets()) {
               if (settingWidget instanceof LvTextSetting lvTextSetting && lvTextSetting.isFocused() && lvTextSetting.typeKey(input.key())) {
                  return true;
               }
            }
         }
         if (input.key() == 256 && this.settingsPopup.hasOpenOverlay()) {
            this.settingsPopup.closeOverlays();
            return true;
         } else if (input.key() == 256 && this.settingsPopup.isOpen()) {
            this.settingsPopup.close();
            return true;
         } else if (this.search.isTyping() && this.search.keyPressed(input)) {
            return true;
         } else {
            ClickGui clickGui = Client.get().moduleManager().get(ClickGui.class);
            int n = clickGui != null ? clickGui.getKey() : 344;
            if (input.key() == 256 || input.key() == n) {
               this.onClose();
               return true;
            } else {
               return super.keyPressed(input);
            }
         }
      }
   }

   @Override
   public boolean charTyped(CharInput input) {
      if (!this.screenAnim.canInteract()) {
         return true;
      } else if (this.bindPopup.isOpen()) {
         return true;
      } else {
         if (this.settingsPopup.isOpen()) {
            for (SettingWidget settingWidget : this.settingsPopup.widgets()) {
               if (settingWidget instanceof LvTextSetting lvTextSetting && lvTextSetting.isFocused()) {
                  lvTextSetting.typeChar(input.codepoint());
                  return true;
               }
            }
         }
         if (this.search.isTyping() && this.search.charTyped(input)) {
            return true;
         } else {
            return super.charTyped(input);
         }
      }
   }

private void selectCategory(LvCategory category) {
       this.settingsPopup.close();
       LvCategory newTarget = (category == this.targetCategory) ? null : category;
       if (newTarget != this.targetCategory) {
          this.search.setText("");
          this.search.blur();
          this.targetCategory = newTarget;
          this.oldSubText = this.newSubText;
          this.newSubText = newTarget == null ? "\u041d\u0435 \u0432\u044b\u0431\u0440\u0430\u043d\u043e" : newTarget.getDisplayName();
          this.subTextAnim.setDirection(Direction.FORWARDS);
          this.subTextAnim.counter.resetCounter();
          this.subTextAnimDone = false;
          for (LvCategory category4 : LvCategory.values()) {
             this.getCategoryAnim(category4).setDirection(category4 == this.targetCategory ? Direction.FORWARDS : Direction.BACKWARDS);
          }
          this.modulesHeaderAnim.setDirection(isMainCategory(this.targetCategory) ? Direction.FORWARDS : Direction.BACKWARDS);
          this.eventsHeaderAnim.setDirection(this.targetCategory == LvCategory.EVENTS ? Direction.FORWARDS : Direction.BACKWARDS);
          if (this.targetCategory == null) {
             this.placeholderAnim.setDirection(Direction.FORWARDS);
             this.placeholderAnim.counter.resetCounter();
          } else {
             this.placeholderAnim.setDirection(Direction.BACKWARDS);
          }
       }
    }

   private static boolean isMainCategory(LvCategory category) {
      return category == LvCategory.VISUALS || category == LvCategory.HUD || category == LvCategory.UTILITIES;
   }

   private static void openBrowser(String url) {
      new Thread(() -> {
         try {
            Desktop.getDesktop().browse(URI.create(url));
         } catch (Throwable e) {
            try {
               Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", url});
            } catch (Throwable ignored) {
            }
         }
      }, "DecideVisuals-OpenLink").start();
   }

   private void selectEventsSub(int n) {
      if (n < 0 || n >= EVENT_SUBS.length) {
         return;
      }
      if (n == 0) {
         openBrowser("https://dsc.gg/decidevisuals");
      } else {
         openBrowser("https://t.me/decidevisuals");
      }
      GuiSounds.category(n, EVENT_SUBS.length);
   }

   private void swapContentCategory(LvCategory category) {
        this.moduleList.finishTransition();
        this.themesRenderer.finishTransition();
        this.eventsRenderer.finishTransition();
        this.contentCategory = category;
       if (category == LvCategory.THEMES) {
          this.themesRenderer.open(false);
       }
       if (category == LvCategory.EVENTS) {
          this.eventsRenderer.open();
          if (this.eventsSub == 0) {
             this.eventsRenderer.showEvents();
          } else {
             this.eventsRenderer.showMines();
          }
        }
   }

   private void updateEventsSubAnim(float f) {
      for (int i = 0; i < this.eventsSubT.length; i++) {
         boolean bl = this.eventsSub == i;
         this.eventsSubT[i] = bl ? Math.min(1.0F, this.eventsSubT[i] + f / CATEGORY_FADE_SEC) : Math.max(0.0F, this.eventsSubT[i] - f / CATEGORY_FADE_SEC);
      }
   }

   private void updateCategoryCrossfade(float f) {
      if (this.targetCategory == this.contentCategory) {
         if (this.contentCategory != null && this.categoryT < 1.0F) {
            this.categoryT = Math.min(1.0F, this.categoryT + f / CATEGORY_FADE_SEC);
         }
      } else if (this.categoryT > 0.0F) {
         this.categoryT = Math.max(0.0F, this.categoryT - f / CATEGORY_FADE_SEC);
         if (this.categoryT <= 0.0F) {
            this.categoryT = 0.0F;
            this.swapContentCategory(this.targetCategory);
         }
      } else {
         this.swapContentCategory(this.targetCategory);
         this.categoryT = 0.0F;
      }
   }
}
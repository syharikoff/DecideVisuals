package ru.white.ui.lv;

import java.util.ArrayList;
import java.util.Locale;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import ru.white.Client;
import ru.white.module.api.Module;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.AccentGradient;
import ru.white.ui.theme.ThemeManager;
import ru.white.ui.util.Position;
import ru.white.ui.util.anim.Decelerate;
import ru.white.ui.util.anim.Direction;
import ru.white.ui.util.anim.GuiMotionAnimation;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.Draw;
import ru.white.utils.render.Render2D;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.ScreenBlur;
import net.minecraft.util.Identifier;

/**
 * Точная копия lastvisuals UI.java.
 */
public class GlassGui extends net.minecraft.client.gui.screen.Screen implements IMinecraft {

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
    private static final String[] EVENT_SUBS = {"Discord", "Telegram"};
    private static final String[] EVENT_SUB_IMAGES = {"wvisual:images/discord.png", "wvisual:images/telegram.png"};
    private static final LvCategory[] CATEGORIES = {LvCategory.VISUALS, LvCategory.HUD, LvCategory.UTILITIES};

    public static final GlassGui INSTANCE = new GlassGui();
    private final LvModuleList moduleList = new LvModuleList();
    private final LvSearchField search = new LvSearchField();
    private final LvBindPopup bindPopup = new LvBindPopup();
    private final LvSettingsPopup settingsPopup = new LvSettingsPopup();
    private final LvThemesRenderer themesRenderer = new LvThemesRenderer();
    private final LvEventsRenderer eventsRenderer = new LvEventsRenderer();
    private final GuiMotionAnimation screenAnim = new GuiMotionAnimation();
    private final java.util.Map<LvCategory, Decelerate> categoryAnims = new java.util.EnumMap<>(LvCategory.class);

    private float categoryT = 1.0F;
    private float themesRowT = 1.0F;
    private long lastNs = System.nanoTime();
    private LvCategory targetCategory = null;
    private LvCategory contentCategory = null;
    private boolean soundPlayed;
    private float scaleFix = 1.0F;

    private GlassGui() {
        super(Text.literal("GlassGui"));
    }

    // ── helpers ──────────────────────────────────────────────────────

    private static Decelerate createAnim(int ms) {
        Decelerate d = new Decelerate().setMs(ms).setValue(1.0);
        d.setDirection(Direction.BACKWARDS);
        d.counter.setTime(System.currentTimeMillis() - 10000L);
        return d;
    }

    private static int rgba(int r, int g, int b, float a) {
        int a2 = Math.max(0, Math.min(255, Math.round(a)));
        return a2 <= 0 ? 0 : a2 << 24 | r << 16 | g << 8 | b;
    }

    private static int rgba(int r, int g, int b, int a, float f) {
        int a2 = Math.max(0, Math.min(255, Math.round(a * f)));
        return a2 <= 0 ? 0 : a2 << 24 | r << 16 | g << 8 | b;
    }

    private Decelerate getCategoryAnim(LvCategory c) {
        return categoryAnims.computeIfAbsent(c, k -> createAnim(200));
    }

    private boolean isModuleView() {
        return contentCategory != null && contentCategory != LvCategory.THEMES && contentCategory != LvCategory.EVENTS;
    }

    private java.util.List<Module> filteredModules(LvCategory category) {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return moduleList.getModules(category);
        ArrayList<Module> result = new ArrayList<>();
        for (Module m : Client.get().moduleManager().values()) {
            if (m.getName().toLowerCase(Locale.ROOT).contains(q)) result.add(m);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Render — копия LvGui.render + renderOverlay
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.scaleFix = 2.0F / mc.getWindow().getScaleFactor();
        mouseX = (int) (mouseX / this.scaleFix);
        mouseY = (int) (mouseY / this.scaleFix);
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
        float alpha = this.screenAnim.alpha();
        int screenWidth = (int) (mc.getWindow().getScaledWidth() / this.scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / this.scaleFix);
        ScreenBlur.capture(2);
        RenderUtil.Blur.blur(0, 0, screenWidth, screenHeight, alpha, ColorUtil.getColor(0, alpha * 0.2F));
        this.renderPanel(context);
        Render2D.endOverlay();
        if (context != null) {
            context.getMatrices().popMatrix();
        }
    }

    private void renderPanel(DrawContext ctx) {
        float alpha = this.screenAnim.alpha();
        if (alpha <= 0.01F) return;

        float sw = Position.screenWidth();
        float sh = Position.screenHeight();
        float px = sw / 2.0F - PANEL_W / 2.0F;
        float py = sh / 2.0F - PANEL_H / 2.0F;

        long now = System.nanoTime();
        float dt = Math.min(0.1F, (float) (now - this.lastNs) / 1.0E9F);
        this.lastNs = now;

        Draw.rect(-5.0F, -5.0F, sw + 10.0F, sh + 10.0F, rgba(0, 0, 0, 60, alpha));
        LvRectUtil.drawClientRect(px, py, PANEL_W, PANEL_H, 12.0F, alpha);
        LvRectUtil.drawPanelBg(px + 5.0F, py + 5.0F, SIDEBAR_W, PANEL_H - 10.0F, 12.0F, 0.0F, 0.0F, 12.0F, alpha);
        LvRectUtil.drawPanelBg(px + CONTENT_X_OFF, py + CONTENT_Y_OFFSET, PANEL_W - CONTENT_INSET, CONTENT_HEIGHT, 0.0F, 12.0F, 12.0F, 0.0F, alpha);

        this.renderSidebar(px + 5.0F, py + 2.0F, PANEL_H, alpha);
        this.themesRowT = Math.min(1.0F, this.themesRowT + dt / CATEGORY_FADE_SEC);

        float contentAlpha = alpha * this.categoryT;
        boolean canRender = !this.bindPopup.isVisible() && !this.settingsPopup.isVisible();
        this.moduleList.setAppearComposite(canRender);
        this.themesRenderer.setAppearComposite(canRender);

        if (contentAlpha > 0.01F && this.contentCategory == LvCategory.EVENTS) {
            this.eventsRenderer.render(ctx, px, py, PANEL_W, contentAlpha, dt);
        } else if (contentAlpha > 0.01F && this.contentCategory == LvCategory.THEMES) {
            this.themesRenderer.render(ctx, px, py, PANEL_W, contentAlpha, this.categoryT, dt);
        } else if (contentAlpha > 0.01F && this.contentCategory != null) {
            java.util.List<Module> list = this.filteredModules(this.contentCategory);
            this.moduleList.render(ctx, px, py, PANEL_W, contentAlpha, this.categoryT, dt, this.contentCategory, list);
            if (list.isEmpty() && this.search.hasText()) {
                this.renderNoResults(px, py, PANEL_W, contentAlpha);
            }
        }

        if (this.isModuleView() && alpha > 0.01F) {
            this.renderModuleHeader(ctx, px, py, PANEL_W, alpha);
        }

        if (this.settingsPopup.isVisible()) this.settingsPopup.render(ctx, alpha);
        if (this.bindPopup.isVisible()) this.bindPopup.render(ctx, alpha);
    }

    private void renderSidebar(float x, float y, float h, float alpha) {
        float midY = y + 13.0F;
        String icon = "x";
        float iconSize = 11.7F;
        float iconW = FontsLV.WVISUAL.width(icon, iconSize);
        String title = "DecideVisuals";
        float titleSize = 14.3F;
        float titleW = FontsLV.SMALL_PIXEL.width(title, titleSize);
        float tx = x + (SIDEBAR_W - (iconW + 7.8F + titleW)) * 0.5F;
        AccentGradient.msdfIcon("decidevisuals", icon, tx, midY - iconSize * 0.5F + 0.5F, iconSize, 225.0F * alpha, 0.1F);
        FontsLV.SMALL_PIXEL.draw(title, tx + iconW + 7.8F, midY - titleSize * 0.5F + 0.5F, titleSize, rgba(255, 255, 255, 255, alpha));

        float catY = y + CAT_COL_TOP;
        String modIcon = "h";
        float modIconW = FontsLV.WVISUAL.width(modIcon, 8.0F);
        AccentGradient.msdfIcon("decidevisuals", modIcon, x + 9.0F, catY + 10.0F - 4.0F + 1.5F, 8.0F, (200.0F + 55.0F) * alpha, 0.15F);
        FontsLV.MONTSERRAT_MEDIUM.draw("Modules", x + 9.0F + modIconW + 6.0F, catY + 10.0F - 4.0F + 0.5F, 8.0F, rgba(255, 255, 255, 255, alpha));

        float subY = catY + CAT_HEADER_H + CAT_SUB_GAP;
        float lineX = x + 12.0F;
        float lineTop = subY + 9.0F;
        float lineBot = subY + 2.0F * CAT_SUB_ROW_H + 9.0F;
        Draw.rect(lineX, lineTop, 1.0F, lineBot - lineTop, rgba(255, 255, 255, 36, alpha));

        for (int i = 0; i < CATEGORIES.length; i++) {
            LvCategory cat = CATEGORIES[i];
            float rowY = subY + i * CAT_SUB_ROW_H + 9.0F;
            float f31 = getCategoryAnim(cat).getOutput().floatValue();
            int n2 = Math.min(255, 140 + Math.round(f31 * 115.0F));
            String catIcon = String.valueOf(cat.getIcon());
            float ciW = FontsLV.WVISUAL.width(catIcon, 7.0F);
            if (f31 > 0.01F) {
                float fW = (ciW + 5.0F + FontsLV.MONTSERRAT_MEDIUM.width(cat.getDisplayName(), 7.0F)) * f31;
                AccentGradient.fillHorizontal(lineX + 3.0F, rowY + 8.5F - 2.0F, fW, 0.75F, 0.625F, 88.0F * f31 * alpha);
            }
            AccentGradient.msdfIcon("decidevisuals", catIcon, lineX + 3.0F, rowY - 3.5F + 1.5F - 2.0F, 7.0F, n2 * alpha, (float) i / 2.0F);
            FontsLV.MONTSERRAT_MEDIUM.draw(cat.getDisplayName(), lineX + 3.0F + ciW + 5.0F, rowY - 3.5F + 0.5F - 2.0F, 7.0F, rgba(255, 255, 255, n2, alpha));
            String count = String.valueOf(moduleList.getModules(cat).size());
            float countW = FontsLV.MONTSERRAT_MEDIUM.width(count, 5.5F);
            FontsLV.MONTSERRAT_MEDIUM.draw(count, x + SIDEBAR_W - 10.0F - countW, rowY - 2.75F + 0.5F - 2.0F, 5.5F, rgba(255, 255, 255, Math.round(80.0F + 80.0F * f31), alpha));
        }

        float infoY = subY + 3.0F * CAT_SUB_ROW_H + CAT_EVENTS_GAP + 10.0F;
        String infoIcon = "e";
        float infoIconW = FontsLV.WVISUAL.width(infoIcon, 8.0F);
        AccentGradient.msdfIcon("decidevisuals", infoIcon, x + 9.0F, infoY - 4.0F + 1.5F, 8.0F, (200.0F + 55.0F) * alpha, 0.6F);
        FontsLV.MONTSERRAT_MEDIUM.draw("Information", x + 9.0F + infoIconW + 6.0F, infoY - 4.0F + 0.5F, 8.0F, rgba(255, 255, 255, 255, alpha));

        float subStartY = infoY + CAT_HEADER_H + CAT_SUB_GAP;
        float subLineTop = subStartY + 9.0F;
        float subLineBot = subStartY + (EVENT_SUBS.length - 1) * CAT_SUB_ROW_H + 9.0F;
        Draw.rect(lineX, subLineTop, 1.0F, subLineBot - subLineTop, rgba(255, 255, 255, 36, alpha));

        for (int i = 0; i < EVENT_SUBS.length; i++) {
            float rowY = subStartY + i * CAT_SUB_ROW_H + 9.0F;
            float iconSz = 7.0F;
            float n2 = 200.0F * alpha;
            Draw.texture(Identifier.of(EVENT_SUB_IMAGES[i]), lineX + 3.0F, rowY - 3.5F + 1.5F - 2.0F, iconSz, iconSz, rgba(255, 255, 255, Math.round(n2)));
            FontsLV.MONTSERRAT_MEDIUM.draw(EVENT_SUBS[i], lineX + 3.0F + iconSz + 5.0F, rowY - 3.5F + 0.5F - 2.0F, iconSz, rgba(255, 255, 255, Math.round(n2)));
        }

        if (this.themesRowT > 0.01F) {
            float themesY = subStartY + EVENT_SUBS.length * CAT_SUB_ROW_H + CAT_OTHERS_GAP + CAT_OTHER_ROW_H * 0.5F;
            float f50 = getCategoryAnim(LvCategory.THEMES).getOutput().floatValue();
            int n6 = Math.round(Math.min(255.0F, (140.0F + f50 * 115.0F)) * this.themesRowT);
            String tIcon = String.valueOf(LvCategory.THEMES.getIcon());
            float tIconW = FontsLV.WVISUAL.width(tIcon, 7.5F);
            AccentGradient.msdfIcon("decidevisuals", tIcon, x + 9.0F, themesY - 3.75F + 1.5F - 2.0F, 7.5F, n6 * alpha, 0.85F);
            FontsLV.MONTSERRAT_MEDIUM.draw("Themes", x + 9.0F + tIconW + 6.0F, themesY - 3.5F + 0.5F - 2.0F, 7.0F, rgba(255, 255, 255, n6, alpha));
        }
    }

    private void renderModuleHeader(DrawContext ctx, float px, float py, float pw, float alpha) {
        float cx = px + CONTENT_X_OFF;
        float cy = py + 5.0F;
        float cw = pw - CONTENT_INSET;
        LvRectUtil.drawPanelBg(cx, cy, cw, 26.0F, 0.0F, 12.0F, 0.0F, 0.0F, alpha);
        float avX = cx + cw - 6.0F - 18.0F;
        float avY = cy + (26.0F - 18.0F) * 0.5F;
        Draw.rect(avX, avY, 18.0F, 18.0F, ThemeManager.rgba(0, 90.0F * alpha), 9.0F);
        Draw.outline(avX, avY, 18.0F, 18.0F, 0.8F, ThemeManager.rgba(16777215, 30.0F * alpha), 9.0F);
        String name = mc.getSession().getUsername();
        float nameW = FontsLV.MONTSERRAT_MEDIUM.width(name, 6.5F);
        FontsLV.MONTSERRAT_MEDIUM.draw(name, avX - 5.0F - nameW, avY + 5.0F, 6.5F, rgba(255, 255, 255, 230, alpha));
        float searchX = cx + 10.0F;
        float searchW = Math.max(60.0F, avX - Math.max(nameW, 0) - 10.0F - searchX);
        float searchY = cy + (26.0F - 14.0F) * 0.5F;
        search.render(ctx, searchX, searchY, searchW, 14.0F, alpha, Position.mouseX(), Position.mouseY(), 0.016F);
    }

    private void renderNoResults(float px, float py, float pw, float alpha) {
        float cx = px + CONTENT_X_OFF;
        float cw = pw - CONTENT_INSET;
        String text = "\u041d\u0438\u0447\u0435\u0433\u043e \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u043e";
        float tw = FontsLV.MONTSERRAT_MEDIUM.width(text, 6.5F);
        FontsLV.MONTSERRAT_MEDIUM.draw(text, cx + (cw - tw) * 0.5F, py + 5.0F + 30.0F + 125.0F - 3.0F, 6.5F, rgba(255, 255, 255, 110, alpha));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        this.screenAnim.startOpening();
        this.moduleList.warmup();
        this.targetCategory = LvCategory.VISUALS;
        this.contentCategory = LvCategory.VISUALS;
        this.categoryT = 1.0F;
        if (!this.soundPlayed) {
            ru.white.utils.other.GuiSounds.open();
            this.soundPlayed = true;
        }
    }

    @Override
    public void close() {
        this.moduleList.scrollbarRelease();
        this.eventsRenderer.scrollbarRelease();
        this.settingsPopup.close();
        this.search.blur();
        this.bindPopup.releaseDrag();
        ru.white.utils.other.GuiSounds.close();
        if (mc.currentScreen == this) {
            mc.setScreen(null);
        }
    }

    @Override
    public void removed() {
        this.soundPlayed = false;
        super.removed();
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float contentH = PANEL_H - 30.0F;
        if (this.isModuleView()) {
            this.moduleList.scroll((float) verticalAmount, contentH);
        } else if (this.contentCategory == LvCategory.EVENTS) {
            this.eventsRenderer.scroll((float) verticalAmount, contentH);
        }
        return true;
    }
}

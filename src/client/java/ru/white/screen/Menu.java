package ru.white.screen;

import net.minecraft.client.sound.Sound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import ru.white.Client;

import ru.white.cosmetics.CosmeticCategory;
import ru.white.cosmetics.CosmeticItem;
import ru.white.cosmetics.CosmeticManager;
import ru.white.manager.Theme;
import ru.white.module.impl.display.ClickGui;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.MultiBooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.api.settings.impl.StringSetting;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import ru.white.screen.editor.OverlayEditor;
import ru.white.screen.editor.OverlayEditors;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.animation.satoshi.Direction;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.colors.ColorFormatting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.Keyboard;
import ru.white.utils.math.MathUtil;
import ru.white.utils.other.GuiMusicPlayer;
import ru.white.utils.other.GuiSounds;
import ru.white.utils.render.*;
import ru.white.utils.render.gif.GifRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Menu extends Screen implements IMinecraft {

    /** Общий масштаб меню: множитель для шрифтов, иконок и всех размеров. */
    public static float S = 1.0F;

    public static boolean returnToGlassAfterEditor = false;

    public Menu() {
        super(Text.literal("MenuNight"));
    }

    boolean exit = false;
    private boolean use3DAnim = false;

    public Animation glomalAnim = new Animation();

    private final HandsEditor handsEditor = HandsEditor.getInstance();

    private float scaleFix = 1F;

    double lastMouseX;
    double lastMouseY;

    public static ru.white.manager.Theme selectedTheme;
    public static ru.white.manager.Theme preSelectedTheme;
    public static ru.white.manager.Theme[] themes;
    public static ru.white.utils.animation.satoshi.Animation animation14 = new EaseInOutQuad(300, 1);
    public static ru.white.utils.animation.satoshi.Animation animCategoryReset = new EaseInOutQuad(300, 1);
    public static ru.white.utils.animation.satoshi.Animation animUserInfo = new EaseInOutQuad(300, 1);

    public static ru.white.utils.animation.satoshi.Animation animation1 = new EaseInOutQuad(300, 1);
    public static ru.white.utils.animation.satoshi.Animation animation2 = new EaseInOutQuad(300, 1);
    public static ru.white.utils.animation.satoshi.Animation animation3 = new EaseInOutQuad(300, 1);
    public static ru.white.utils.animation.satoshi.Animation animation4 = new EaseInOutQuad(300, 1);

    public Module select = null;
    public Category active = Category.VISUALS;

    private float scrollTarget = 0;
    private float scrollAnim = 0;
    private float maxScroll = 0;

    private float settingScrollTarget = 0;
    private float settingScrollAnim = 0;
    private float settingMaxScroll = 0;

    private SliderSetting draggingSlider = null;
    private BindSetting activeBind = null;

    /** Модуль, которому сейчас назначают клавишу прямо из списка. */
    private Module bindingModule = null;

    private StringSetting activeString = null;
    private String stringBuffer = "";

    private ColorSetting draggingColor = null;
    private int draggingColorBar = 0; // 0 - hue, 1 - saturation, 2 - brightness

    public static boolean searchActive = false;
    /** Запрос живёт между открытиями меню. */
    private static String searchQuery = "";
    private long searchTypeTime = System.currentTimeMillis();

    private final ru.white.utils.animation.satoshi.Animation animSearchFocus = new EaseInOutQuad(300, 1, Direction.BACKWARDS);
    private final ru.white.utils.animation.satoshi.Animation animSearchText = new EaseInOutQuad(300, 1, Direction.BACKWARDS);
    private final ru.white.utils.animation.satoshi.Animation animSearchEmpty = new EaseInOutQuad(300, 1, Direction.BACKWARDS);

    private static final int SEARCH_LIMIT = 24;

    private final Set<String> expandedModes = new HashSet<>();

    // Config UI
    private String configInput = "";
    private boolean configInputFocused = false;
    private float configScroll = 0;
    private float configScrollTarget = 0;
    private final java.util.List<String> configCache = new java.util.ArrayList<>();
    private final java.util.List<float[]> configRowRects = new java.util.ArrayList<>();
    private final java.util.List<float[]> configDelRects = new java.util.ArrayList<>();

    // Marker UI
    private String markerNameInput = "";
    private String markerXInput = "";
    private String markerYInput = "";
    private String markerZInput = "";
    private int markerInputFocused = -1; // -1=none, 0=name, 1=x, 2=y, 3=z
    private float markerScroll = 0;
    private float markerScrollTarget = 0;
    private final java.util.List<String[]> markerCache = new java.util.ArrayList<>(); // {name, x, y, z}
    private final java.util.List<float[]> markerRowRects = new java.util.ArrayList<>();
    private final java.util.List<float[]> markerDelRects = new java.util.ArrayList<>();

    // Cosmetic UI
    private CosmeticCategory activeCosmetic = CosmeticCategory.WINGS;
    private float cosmeticsScroll = 0;
    private float cosmeticsScrollAnim = 0;
    private float cosmeticsScrollTarget = 0;
    private float cosmeticsMaxScroll = 0;

    private static class CosmeticClickTarget {
        final int id;
        final float cardX, cardY, cardW, cardH;
        final float btnX, btnY, btnW, btnH;
        CosmeticClickTarget(int id, float cardX, float cardY, float cardW, float cardH, float btnX, float btnY, float btnW, float btnH) {
            this.id = id;
            this.cardX = cardX; this.cardY = cardY; this.cardW = cardW; this.cardH = cardH;
            this.btnX = btnX; this.btnY = btnY; this.btnW = btnW; this.btnH = btnH;
        }
    }
    private final java.util.List<CosmeticClickTarget> cosmeticsItemButtons = new java.util.ArrayList<>();

    private final HashMap<String, ru.white.utils.animation.satoshi.Animation> chipAnims = new HashMap<>();

    private ru.white.utils.animation.satoshi.Animation chipAnim(String key) {
        return chipAnims.computeIfAbsent(key, k -> new EaseInOutQuad(300, 1));
    }

    private ru.white.utils.animation.satoshi.Animation expandAnim(String key) {
        return chipAnims.computeIfAbsent("expand:" + key, k -> new EaseInOutQuad(300, 1));
    }

    // ——— фоновые эффекты ———
    private final GrayscalePipeline grayscalePipeline = new GrayscalePipeline();
    private final ClickGuiDotsPipeline dotsPipeline = new ClickGuiDotsPipeline();
    private final ScanLinesPipeline scanLinesPipeline = new ScanLinesPipeline();
    private final MenuRaysPipeline raysPipeline = new MenuRaysPipeline();
    private final HalftoneDotsPipeline halftonePipeline = new HalftoneDotsPipeline();

    private boolean effect(String name) {
        ClickGui gui = Client.get().moduleManager().get(ClickGui.class);
        return gui != null && gui.effect.getValue(name);
    }

    private static final class GuiParticle {
        final float x, y;
        final float maxRadius;
        final float lifeMs;
        final float drift;
        final long born = System.currentTimeMillis();

        GuiParticle(float x, float y) {
            this.x = x;
            this.y = y;
            this.maxRadius = 10F + (float) Math.random() * 7F;
            this.lifeMs = 1400F + (float) Math.random() * 700F;
            this.drift = 4F + (float) Math.random() * 6F;
        }

        float progress() {
            return (System.currentTimeMillis() - born) / lifeMs;
        }
    }

    private final java.util.List<GuiParticle> particles = new java.util.ArrayList<>();
    private long lastParticle;
    private long particleDelay = 90;

    private void spawnParticle(int screenWidth, int screenHeight) {
        if (System.currentTimeMillis() - lastParticle < particleDelay) return;
        lastParticle = System.currentTimeMillis();
        particleDelay = 70 + (long) (Math.random() * 90);
        if (particles.size() > 60) return;
        particles.add(new GuiParticle((float) (Math.random() * screenWidth), (float) (Math.random() * screenHeight)));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = MathHelper.clamp((value - edge0) / (edge1 - edge0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    private void renderParticles(float globalAnim) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            GuiParticle p = particles.get(i);
            float t = p.progress();
            if (t >= 1F) { particles.remove(i); continue; }
            float grow = 1F - (1F - t) * (1F - t) * (1F - t);
            float radius = p.maxRadius * grow * S;
            if (radius < 0.4F) continue;
            float fade = smoothstep(0F, 0.18F, t) * (1F - smoothstep(0.45F, 1F, t));
            float alpha = fade * globalAnim * 0.75F;
            if (alpha < 0.004F) continue;
            float thickness = 1.1F + 2.6F * (1F - grow);
            float py = p.y - p.drift * grow * S;
            RenderUtil.Render2D.outline(p.x - radius, py - radius, radius * 2, radius * 2, thickness, ColorUtil.replAlpha(ColorUtil.client(), alpha), radius);
        }
    }

    private void drawScanLines(int screenWidth, int screenHeight, float globalAnim) {
        if (globalAnim <= 0.01F) return;
        scanLinesPipeline.draw(screenWidth, screenHeight, globalAnim * 0.18F, ColorUtil.getColor(255), 4F * S, 1F, 50F, 3000F, 100F);
    }

    private final HashMap<String, Float> descHeights = new HashMap<>();

    private float descHeight(Font font, String desc) {
        return descHeights.computeIfAbsent(desc + "_" + S, d -> font.getWrappedHeight(desc, 125 * S, 6 * S));
    }

    private final HashMap<String, float[]> smoothVals = new HashMap<>();

    private float smooth(String key, float target) {
        float[] v = smoothVals.computeIfAbsent(key, k -> new float[]{target});
        v[0] += (target - v[0]) * 0.2F;
        return v[0];
    }

    private final java.util.IdentityHashMap<Setting<?>, ru.white.utils.animation.satoshi.Animation> visAnims = new java.util.IdentityHashMap<>();

    private float visAnim(Module f, Setting<?> setting) {
        ru.white.utils.animation.satoshi.Animation a = visAnims.computeIfAbsent(setting, k -> new EaseInOutQuad(300, 1));
        a.setDirection(setting.getVisible().get() ? Direction.FORWARDS : Direction.BACKWARDS);
        return a.getOutput();
    }

    private float chipsHeight(Iterable<String> values, float width) {
        Font font = Fonts.sf_regular;
        float px = 0, py = 0;
        for (String val : values) {
            float tw = font.getWidth(val, 6 * S) + 8 * S;
            if (px + tw > width && px > 0) { px = 0; py += 12 * S; }
            px += tw + 3 * S;
        }
        return py + 10 * S;
    }

    private final java.util.IdentityHashMap<Setting<?>, float[]> chipsHeights = new java.util.IdentityHashMap<>();

    private float chipsHeight(Setting<?> key, Iterable<String> values, float width) {
        float[] cached = chipsHeights.get(key);
        if (cached != null && cached[0] == width) return cached[1];
        float height = chipsHeight(values, width);
        chipsHeights.put(key, new float[]{width, height});
        return height;
    }

    private float modeChipsHeight(ModeSetting s, float width) {
        return chipsHeight(s, s.values, width);
    }

    private float multiChipsHeight(MultiBooleanSetting s, float width) {
        float[] cached = chipsHeights.get(s);
        if (cached != null && cached[0] == width) return cached[1];
        return chipsHeight(s, multiNames(s), width);
    }

    private String query() { return searchQuery.trim().toLowerCase(); }
    private boolean searching() { return !query().isEmpty(); }

    private boolean isNoSearchCategory() {
        return active == Category.CONFIGS || active == Category.MARKERS || active == Category.COSMETICS;
    }

    private boolean moduleVisible(Module f) {
        if (isNoSearchCategory()) return false;
        String q = query();
        if (q.isEmpty()) return f.getCategory() == active;
        return f.getName().toLowerCase().contains(q) || f.getBigName().toLowerCase().contains(q) || f.getDesc().toLowerCase().contains(q) || f.getCategory().getName().toLowerCase().contains(q);
    }

    private int searchResults() {
        int count = 0;
        for (Module f : Client.get().moduleManager().values()) if (moduleVisible(f)) count++;
        return count;
    }

    private void searchChanged() {
        searchTypeTime = System.currentTimeMillis();
        scrollTarget = 0;
    }

    private void clearSearch() {
        if (searchQuery.isEmpty()) return;
        searchQuery = "";
        searchChanged();
    }

    private java.util.List<String> multiNames(MultiBooleanSetting s) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (BooleanSetting b : s.getValues()) names.add(b.getName());
        return names;
    }

    @Override
    protected void init() {
        exit = false;
        use3DAnim = false;
        GuiCloseAnimationShatter.cancel();
        searchActive = false;
        searchTypeTime = System.currentTimeMillis();
        bindingModule = null;
        GuiSounds.open();
        GuiMusicPlayer.start(0.15F);
        glomalAnim.run(1, 0.25F, Easings.SINE_OUT);
        selectedTheme = Client.get().guiManager().getCurrentTheme();
        preSelectedTheme = Client.get().guiManager().getCurrentTheme();
        themes = ru.white.manager.Theme.values();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    private GifTexture gif;


    private void loadGif() {
        if (gif != null) return;
        try {
            var res = mc.getResourceManager().getResource(Identifier.of("client", "textures/gui.gif"));
            if (res.isPresent()) {
                try (InputStream in = res.get().getInputStream()) {
                    gif = new GifTexture(in);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        closeCheck();
        glomalAnim.update();
        mouseX = (int) (mouseX / scaleFix);
        mouseY = (int) (mouseY / scaleFix);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public void renderOverlay(DrawContext context, RenderTickCounter tickCounter) {
        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        scaleFix = targetScale / currentScale;

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        float globalAnim = glomalAnim.get();

        if (context != null) context.getMatrices().pushMatrix();

        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) {
            float editorMouseX = (float) (mc.mouse.getScaledX(mc.getWindow()) / scaleFix);
            float editorMouseY = (float) (mc.mouse.getScaledY(mc.getWindow()) / scaleFix);
            editor.render(screenWidth, screenHeight, editorMouseX, editorMouseY, globalAnim);
            Render2D.endOverlay();
            if (context != null) context.getMatrices().popMatrix();
            return;
        }

        if (effect("Серый фон")) grayscalePipeline.draw(globalAnim);
        ScreenBlur.capture(1);
        if (effect("Размывать фон")) {
            RenderUtil.Blur.blur(0, 0, screenWidth, screenHeight, globalAnim, 0, ColorUtil.getColor(0, 0));
        }
        if (effect("Затемнять фон")) {
            RenderUtil.Images.texture(Identifier.of("client","textures/frame/background.png"), 0, 0, screenWidth, screenHeight, ColorUtil.multAlpha(ColorUtil.client(), globalAnim));
        }

        float shaderMouseX = (float) (mc.mouse.getScaledX(mc.getWindow()) / scaleFix);
        float shaderMouseY = (float) (mc.mouse.getScaledY(mc.getWindow()) / scaleFix);

        if (effect("Шейдер")) {
            int rayBase = ColorUtil.client();
            raysPipeline.draw(globalAnim, ColorUtil.replAlpha(ColorUtil.multDark(rayBase, 0.5F), 0.9F * globalAnim), ColorUtil.replAlpha(rayBase, 0.9F * globalAnim), 0.1F, 0.08F, 0.26F);
        }

        if (effect("Точки")) {
            halftonePipeline.draw(screenWidth, screenHeight, shaderMouseX, shaderMouseY, globalAnim * 0.1F, ColorUtil.getColor(255), 6, 0.7F, 3, 100);
        }

        if (effect("Скан линии")) drawScanLines(screenWidth, screenHeight, globalAnim);

        if (effect("Свечение")) {
            int glowColor = ColorUtil.replAlpha(ColorUtil.client(), globalAnim * 0.5F);
            RenderUtil.Images.texture(Identifier.of("client","textures/effects/top_glow.png"), 0, -30 + 30 * globalAnim, screenWidth, screenHeight, glowColor);

            if (!exit && globalAnim > 0.01F && globalAnim < 0.99F) {
                float pulse = (globalAnim > 0.5F ? 1F - globalAnim : globalAnim) * 2F;
                float scale = screenWidth / 1.75F * globalAnim;
                float cx = screenWidth / 2F;
                float cy = screenHeight / 2F;
                RenderUtil.Render2D.rect(cx - scale, cy - scale, scale * 2, scale * 2, ColorUtil.getColor(255, 0.2F * pulse), scale);
                RenderUtil.Render2D.glow(cx - scale, cy - scale, scale * 2, scale * 2, ColorUtil.replAlpha(ColorUtil.client(), 0.15F * pulse), scale, 12, 1);
            }
        }

        if (!particles.isEmpty()) {
            particles.clear();
        }

        ScreenBlur.capture();

        S = Client.get().moduleManager().get(ClickGui.class).size.getValue();

        float w = 420 * S;
        float h = 280 * S;
        float x = screenWidth / 2F - w / 2;
        float y = screenHeight / 2F - h / 2 + (exit ? 60 * S - 60 * S * globalAnim : -60 * S + 60 * S * globalAnim);

        Font draw = Fonts.sf_regular;
        Font regular = Fonts.sf_regular;
        Font icons = Fonts.icon;
        Font guiicon = Fonts.gui;

        ScreenBlur.capture();

        float ht = 20 * S;
        float dotsW = (themes.length - 1) * 14 * S + 8 * S;
        float barW = dotsW + 12 * S;
        float xtd = x + (w - dotsW) / 2;
        float barX = xtd - 6 * S;
        float yt = y + h + 5 * S;

        RenderUtil.Render2D.glow(barX, yt, barW, ht - 0.5F * S, ColorUtil.getColor(0, 0.15F * globalAnim), 7.5F * S, 15, 1);
        RenderUtil.Blur.blur(barX, yt, barW, ht, globalAnim, 7.5F * S, ColorUtil.multAlpha(ColorUtil.multDark(ColorUtil.background(), 0.6F), globalAnim));
        RenderUtil.Images.texture(Identifier.of("client","textures/frame/rectthemegui.png"), barX, yt, barW, ht, ColorUtil.multAlpha(ColorUtil.client(), globalAnim));

        float ytd = yt + 6F * S;

        for (Theme theme : themes) {
            theme.animation.setDirection(theme == selectedTheme ? Direction.FORWARDS : Direction.BACKWARDS);
            float anPC = theme.animation.getOutput();

            RenderUtil.Render2D.rect(xtd + 0.75F * S * anPC, ytd + 0.75F * S * anPC, 8 * S - 1.5F * S * anPC, 8 * S - 1.5F * S * anPC, ColorUtil.replAlpha(theme.getClient(), (0.5F + 0.5F * anPC) * globalAnim), 8 * S);
            RenderUtil.Render2D.outline(xtd - 0.5F * S * anPC, ytd - 0.5F * S * anPC, 8 * S + 1 * S * anPC, 8 * S + 1 * S * anPC, 0.25F * S, ColorUtil.replAlpha(theme.getClient(), (1.0F * anPC) * globalAnim), 8 * S);
            RenderUtil.Render2D.glow(xtd + 0.75F * S * anPC, ytd + 0.75F * S * anPC, 8 * S - 1.5F * S * anPC, 8 * S - 1.5F * S * anPC, ColorUtil.replAlpha(theme.getClient(), (0.1F * anPC) * globalAnim), 8 * S, 7, 1);

            xtd += 14 * S;
        }

        RenderUtil.Render2D.glow(x, y, w, h - 0.5F * S, ColorUtil.getColor(0, 0.15F * globalAnim), 8 * S, 15, 1);
        RenderUtil.Blur.blur(x, y, w, h, globalAnim, 8 * S, ColorUtil.multAlpha(ColorUtil.multDark(ColorUtil.background(), 0.6F), globalAnim));
        RenderUtil.Images.texture(Identifier.of("client","textures/frame/rectgui.png"), x, y, w, h, ColorUtil.multAlpha(ColorUtil.client(), globalAnim));

        RenderUtil.Render2D.glow(x + 6 * S, y + 6 * S, 30 * S, h - 12 * S, ColorUtil.getColor(0, 0.04F * globalAnim), 6 * S, 8, 1);
        RenderUtil.Render2D.rect(x + 6 * S, y + 6 * S, 30 * S, h - 12 * S, ColorUtil.getColor(0, 0.15F * globalAnim), 6 * S);

        float xPanelMini = x + 6 * S;
        float yPanelMini = y + 6 * S;

        boolean isLogoHov = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xPanelMini + (30 * S) / 2 - (12 * S) / 2, yPanelMini + 7 * S, 12 * S, 12 * S);
        animUserInfo.setDirection(isLogoHov ? Direction.FORWARDS : Direction.BACKWARDS);

        float animL = animUserInfo.getOutput();
        float xAnimADd2 = 16 * S - 16 * S * animL;
        String sgff = "DecideVisuals" + ColorFormatting.getColor(ColorUtil.replAlpha(ColorUtil.client(), globalAnim * animL)) + " 5.0";

        RenderUtil.Render2D.glow(xPanelMini - draw.getWidth(sgff, 8 * S) - 4 * S + xAnimADd2, yPanelMini + 7 * S, 7 * S + draw.getWidth(sgff, 8 * S), 14 * S, ColorUtil.multAlpha(ColorUtil.getColor(0), globalAnim * animL * 0.1F), 4 * S, 6, 1);
        RenderUtil.Blur.blur(xPanelMini - draw.getWidth(sgff, 8 * S) - 4 * S + xAnimADd2, yPanelMini + 7 * S, 7 * S + draw.getWidth(sgff, 8 * S), 14 * S, globalAnim * animL, 4 * S, ColorUtil.multAlpha(ColorUtil.background(), globalAnim * animL * 0.2F));
        draw.draw(sgff, xPanelMini - draw.getWidth(sgff, 8 * S) + xAnimADd2, yPanelMini + 9 * S, 8 * S, ColorUtil.getColor(200, globalAnim * animL));

        RenderUtil.Render2D.glow(xPanelMini + (30 * S) / 2 - (16 * S) / 2 + 5.5F * S, yPanelMini + 10 * S, 0.1F * S, 0.1F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * animL * 0.15F), 10 * S, 12, 1);
        RenderUtil.Images.texture(Identifier.of("client", "textures/frame/logo.png"), xPanelMini + (30 * S) / 2 - (16 * S) / 2, yPanelMini + 2 * S, 16 * S, 16 * S, ColorUtil.replAlpha(ColorUtil.getClientColor(1), globalAnim));

        float csgddd = 0;
        for (Category category : Category.values()) {
            csgddd += 20 * S;
        }

        float cy = yPanelMini + 28 * S;

        for (Category category : Category.values()) {
            category.alphaS.setDirection(active == category ? Direction.FORWARDS : Direction.BACKWARDS);
            float anim = category.alphaS.getOutput();
            ru.white.utils.animation.satoshi.Animation animUF = category.alphaS2;

            boolean isHv = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xPanelMini + (30 * S) / 2 - (16 * S) / 2, cy, 16 * S, 16 * S);
            animUF.setDirection(isHv ? Direction.FORWARDS : Direction.BACKWARDS);
            float animL2 = animUF.getOutput();

            String name = category.getIcon();
            RenderUtil.Render2D.outline(xPanelMini + (30 * S) / 2 - (16 * S) / 2 - 1 * S * anim + 1 * S, cy - 1 * S * anim + 1 * S, 16 * S + 2 * S * anim - 2 * S, 16 * S + 2 * S * anim - 2 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * anim), 5 * S);
            RenderUtil.Render2D.glow(xPanelMini + (30 * S) / 2 - (12 * S) / 2 + 5.8F * S, cy + 7.8F * S, 0.1F * S, 0.1F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * animL2 * 0.15F * anim), 8 * S, 9, 1);
            if (category == Category.MARKERS) {
                RenderUtil.Images.texture(Identifier.of("client", "textures/hud/marker.png"), xPanelMini + (30 * S) / 2 - (8 * S) / 2, cy + 4 * S, 8 * S, 8 * S, ColorUtil.multAlpha(ColorUtil.overCol(ColorUtil.getColor(255, 0.15F * globalAnim + 0.5F * animL2), ColorUtil.client(), anim), globalAnim));
            } else {
                Fonts.wvisual_2.drawCentered(name, xPanelMini + (30 * S) / 2, cy + 4.5F * S, 8 * S, ColorUtil.multAlpha(ColorUtil.overCol(ColorUtil.getColor(255, 0.15F * globalAnim + 0.5F * animL2), ColorUtil.client(), anim), globalAnim));
            }

            String sgff2 = category.getName();
            float xAnimADd = 16 * S - 16 * S * animL2;
            RenderUtil.Render2D.glow(xPanelMini - draw.getWidth(sgff2, 8 * S) - 4 * S + xAnimADd, cy + 7 * S - 6 * S, 7 * S + draw.getWidth(sgff2, 8 * S), 14 * S, ColorUtil.multAlpha(ColorUtil.getColor(0), globalAnim * animL2 * 0.1F), 4 * S, 6, 1);
            RenderUtil.Blur.blur(xPanelMini - draw.getWidth(sgff2, 8 * S) - 4 * S + xAnimADd, cy + 7 * S - 6 * S, 7 * S + draw.getWidth(sgff2, 8 * S), 14 * S, globalAnim * animL2, 4 * S, ColorUtil.multAlpha(ColorUtil.background(), globalAnim * animL2 * 0.2F));
            draw.draw(sgff2, xPanelMini - draw.getWidth(sgff2, 8 * S) + xAnimADd, cy + 9 * S - 6 * S, 8 * S, ColorUtil.getColor(200, globalAnim * animL2));

            cy += 20 * S;
        }

        float xps = x + 6 * S + 36 * S;
        float yps = y + 6 * S;

        boolean searchHover = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xps, yps, 140 * S, 20 * S);
        animation3.setDirection(searchHover || searchActive ? Direction.FORWARDS : Direction.BACKWARDS);
        animSearchFocus.setDirection(searchActive ? Direction.FORWARDS : Direction.BACKWARDS);
        animSearchText.setDirection(!searchQuery.isEmpty() ? Direction.FORWARDS : Direction.BACKWARDS);

        float hvs = animation3.getOutput();
        float focus = animSearchFocus.getOutput();
        float typed = animSearchText.getOutput();

        RenderUtil.Render2D.glow(xps, yps, 140 * S, 20 * S, ColorUtil.getColor(0, 0.04F * globalAnim), 6 * S, 8, 1);
        RenderUtil.Render2D.rect(xps, yps, 140 * S, 20 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.15F * globalAnim), ColorUtil.getColor(25, 0.3F * globalAnim), Math.max(hvs, focus)), 6 * S);
        RenderUtil.Render2D.outline(xps, yps, 140 * S, 20 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * focus), 6 * S);

        guiicon.draw("g", xps + 7 * S, yps + 7 * S, 6 * S, ColorUtil.overCol(ColorUtil.getColor(255, globalAnim * (0.3F + 0.5F * hvs)), ColorUtil.replAlpha(ColorUtil.client(), globalAnim * (0.3F + 0.7F * hvs)), hvs));

        float xSearchText = xps + 6.5F * S + 11 * S;
        float wSearchText = 140 * S - (xSearchText - xps) - 8 * S;

        float hidePlaceholder = Math.max(typed, focus);
        if (hidePlaceholder < 0.99F) {
            String placeholderText = isNoSearchCategory() ? "\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0440\u0430\u0437\u0434\u0435\u043b\u044b \u0432\u044b\u0448\u0435" : "Search";
            regular.draw(placeholderText, xSearchText, yps + 5.5F * S + 4 * S * hidePlaceholder, 7 * S, ColorUtil.getColor(255, globalAnim * (1 - hidePlaceholder) * (0.3F + 0.5F * hvs)));
        }
        if (typed > 0.01F) {
            regular.drawFadingTextReverse(searchQuery, xSearchText, yps + 5.5F * S - 4 * S * (1 - typed), wSearchText, ColorUtil.getColor(255, globalAnim * typed * (0.5F + 0.5F * Math.max(hvs, focus))), 7 * S);
        }

        boolean justTyped = System.currentTimeMillis() - searchTypeTime < 500;
        float caretTarget = focus * (justTyped || (System.currentTimeMillis() / 500) % 2 == 0 ? 1F : 0F);
        float caret = smooth("search:caret", caretTarget);
        float caretX = smooth("search:caretx", xSearchText + Math.min(regular.getWidth(searchQuery, 7 * S), wSearchText) + 1.5F * S);

        RenderUtil.Render2D.rect(caretX, yps + 6.5F * S, 0.6F * S, 6.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * caret), 0.3F * S);

        float catPanX = x + 6 * S + 36 * S + 6 * S + 140 * S;
        float catPanW = w + (6 * S - 36 * S - 6 * S - 140 * S - 12 * S - 6 * S);

        RenderUtil.Render2D.glow(catPanX, y + 6 * S, catPanW, 20 * S, ColorUtil.getColor(0, 0.04F * globalAnim), 6 * S, 8, 1);
        RenderUtil.Render2D.rect(catPanX, y + 6 * S, catPanW, 20 * S, ColorUtil.getColor(0, 0.15F * globalAnim), 6 * S);

        String catName = active.getIcon();
        Fonts.wvisual_2.draw(catName, catPanX + 8 * S, y + 12.7F * S + 5 * S - 5 * S * animCategoryReset.getOutput(), 7 * S, ColorUtil.multAlpha(ColorUtil.client(), globalAnim * animCategoryReset.getOutput()));
        draw.draw(active.getName(), catPanX + 20 * S, y + 11.8F * S + 5 * S - 5 * S * animCategoryReset.getOutput(), 7 * S, ColorUtil.getColor(200, globalAnim * animCategoryReset.getOutput()));

        int found = searchResults();
        String foundText = found + (found == 1 ? " result" : " results");
        float xHeader = catPanX;
        float wHeader = catPanW;
        draw.draw(foundText, xHeader + wHeader - 8 * S - draw.getWidth(foundText, 6.5F * S), y + 12.2F * S + 5 * S - 5 * S * typed, 6.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * typed));

        // у разделов со своим UI (Конфиги/Метки/Косметика) свой фон, иначе он ложится
        // вторым слоем и левая часть панели выглядит темнее остальной
        if (!isCustomPanelCategory()) {
            RenderUtil.Render2D.glow(x + 6 * S + 36 * S, y + 6 * S + 26 * S, 140 * S, h - 12 * S - 26 * S, ColorUtil.getColor(0, 0.04F * globalAnim), 7 * S, 8, 1);
            RenderUtil.Render2D.rect(x + 6 * S + 36 * S, y + 6 * S + 26 * S, 140 * S, h - 12 * S - 26 * S, ColorUtil.getColor(0, 0.15F * globalAnim), 7 * S);

            Scissor.enable(x + 6 * S + 36 * S, y + 6 * S + 26 * S, 140 * S, h - 12 * S - 26 * S, 2);
        }

        scrollAnim += (scrollTarget - scrollAnim) * 0.2F;

        float xModule = x + 6 * S + 36 * S + 5 * S;
        float yModule = y + 6 * S + 30 * S - scrollAnim;

        float crs = animCategoryReset.getOutput();
        float listTop = y + 6 * S + 26 * S;
        float listBottom = listTop + (h - 12 * S - 26 * S);

        // список модулей рисуем только там, где он вообще нужен; у своих разделов своя панель
        boolean drawModuleList = !isCustomPanelCategory();

        for (Module f : Client.get().moduleManager().values()) {
            f.getAnimation14().setDirection(moduleVisible(f) ? Direction.FORWARDS : Direction.BACKWARDS);
            float canim1 = f.getAnimation14().getOutput();

            if (canim1 > 0 && drawModuleList) {
                float descH = descHeight(draw, f.getDesc());
                float moduleH = Math.max(20 * S, 16 * S + descH + 4 * S);

                f.getAnimation16().setDirection(f.isEnabled() ? Direction.FORWARDS : Direction.BACKWARDS);
                float moduleEnable = f.getAnimation16().getOutput();

                boolean onScreen = yModule + moduleH >= listTop && yModule <= listBottom;
                boolean isHover = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xModule, yModule, 130 * S, moduleH);

                f.getAnimation12().setDirection(isHover ? Direction.FORWARDS : Direction.BACKWARDS);
                float hanim = f.getAnimation12().getOutput();
                f.animation1.setDirection(f == select ? Direction.FORWARDS : Direction.BACKWARDS);
                float selectAnim = f.animation1.getOutput();

                if (!onScreen) {
                    yModule += (moduleH + 5 * S) * canim1;
                    continue;
                }

                RenderUtil.Render2D.rect(xModule, yModule, 130 * S, moduleH, ColorUtil.overCol(ColorUtil.getColor(0, 0.15F * globalAnim * canim1), ColorUtil.getColor(25, 0.3F * globalAnim * canim1), hanim), 5 * S);
                RenderUtil.Render2D.outline(xModule - 1 * S * moduleEnable + 1 * S, yModule - 1 * S * moduleEnable + 1 * S, 130 * S + 2 * S * moduleEnable - 2 * S, moduleH + 2 * S * moduleEnable - 2 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * canim1 * moduleEnable * (1.0F - 0.3F * hanim)), 5 * S);

                RenderUtil.Render2D.rect(xModule + 130 * S - 14 * S - 5 * S, yModule + 5F * S, 14 * S, 8 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.2F * globalAnim * canim1), ColorUtil.replAlpha(ColorUtil.client(), globalAnim * canim1 * (1.0F - 0.3F * hanim)), moduleEnable), 4 * S);
                RenderUtil.Render2D.rect(xModule + 130 * S - 14 * S - 5 * S + 1.5F * S + 5.5F * S * moduleEnable, yModule + 5F * S + 1.25F * S, 5.5F * S, 5.5F * S, ColorUtil.getColor(255, globalAnim * (0.3F + 0.7F * moduleEnable) * canim1), 4 * S);

                boolean bindingNow = bindingModule == f;
                ru.white.utils.animation.satoshi.Animation bindAct = chipAnim(f.getName() + ":modbind");
                bindAct.setDirection(bindingNow ? Direction.FORWARDS : Direction.BACKWARDS);
                float bindActive = bindAct.getOutput();

                String keyName = bindingNow ? "..." : (f.getKey() == -1 ? "n/a" : Keyboard.keyName(f.getKey()).replace("NONE", "n/a"));
                float keyW = smooth(f.getName() + ":modbindw", draw.getWidth(keyName, 6 * S) + 9 * S);
                float keyX = xModule + 130 * S - 14 * S - 5 * S - 5 * S - keyW;
                float keyY = yModule + 4F * S;

                boolean keyHover = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, keyX, keyY, keyW, 10 * S);
                ru.white.utils.animation.satoshi.Animation bindHov = chipAnim(f.getName() + ":modbindhov");
                bindHov.setDirection(keyHover ? Direction.FORWARDS : Direction.BACKWARDS);
                float bindHover = bindHov.getOutput();

                float pulse = bindingNow ? 0.55F + 0.45F * (float) Math.sin((System.currentTimeMillis() % 1000L) / 1000F * Math.PI * 2F) : 0F;
                float bindAccent = Math.max(bindHover * 0.5F, bindActive);

                RenderUtil.Render2D.rect(keyX, keyY, keyW, 10 * S, ColorUtil.overCol(ColorUtil.getColor(0, (0.12F + 0.13F * bindHover) * globalAnim * canim1), ColorUtil.replAlpha(ColorUtil.client(), (0.15F + 0.2F * pulse) * globalAnim * canim1), bindActive), 3 * S);
                RenderUtil.Render2D.outline(keyX, keyY, keyW, 10 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * canim1 * bindAccent * (bindingNow ? 0.4F + 0.6F * pulse : 1F)), 3 * S);
                RenderUtil.Render2D.glow(keyX, keyY, keyW, 10 * S, ColorUtil.replAlpha(ColorUtil.client(), 0.12F * globalAnim * canim1 * bindActive * pulse), 3 * S, 6, 1);

                regular.drawCentered(keyName, keyX + keyW / 2, keyY + 1.5F * S, 6 * S, ColorUtil.replAlpha(ColorUtil.overCol(ColorUtil.getColor(255), ColorUtil.client(), Math.max(bindActive, moduleEnable)), globalAnim * canim1 * (0.35F + 0.35F * moduleEnable + 0.3F * Math.max(bindHover, bindActive))));

                draw.draw(f.getBigName(), xModule + 5 * S, yModule + 5 * S, 7 * S, ColorUtil.getColor(255, globalAnim * canim1 * (0.2F + 0.6F * moduleEnable + 0.2F * hanim)));
                draw.drawWrappedText(f.getDesc(), xModule + 5 * S, yModule + 4 * S + 12 * S, 125 * S, ColorUtil.getColor(255, globalAnim * canim1 * (0.1F + 0.5F * moduleEnable + 0.2F * hanim)), 6 * S);

                RenderUtil.Render2D.rect(xModule, yModule, 130 * S, moduleH, ColorUtil.replAlpha(ColorUtil.client(), 0.3F * globalAnim * selectAnim * (0.2F + 0.5F * moduleEnable) * canim1), 5 * S);

                yModule += (moduleH + 5 * S) * canim1;
            }
        }

        animSearchEmpty.setDirection(!drawModuleList ? Direction.BACKWARDS
                : (searching() && found == 0 ? Direction.FORWARDS : Direction.BACKWARDS));
        float emptyAnim = animSearchEmpty.getOutput();

        if (emptyAnim > 0.01F) {
            float xEmpty = x + 6 * S + 36 * S + 70 * S;
            float yEmpty = y + 6 * S + 26 * S + (h - 12 * S - 26 * S) / 2 - 8 * S + 6 * S - 6 * S * emptyAnim;
            guiicon.drawCentered("g", xEmpty, yEmpty, 8 * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * emptyAnim * 0.4F));
            draw.drawCentered("Ничего не найдено", xEmpty, yEmpty + 14 * S, 7 * S, ColorUtil.getColor(255, globalAnim * emptyAnim * 0.5F));
        }

        float contentHeight = yModule + scrollAnim - (y + 6 * S + 30 * S);
        float viewHeight = h - 12 * S - 26 * S - 4 * S;
        maxScroll = Math.max(0, contentHeight - viewHeight);
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;

        Scissor.reset();

        if (active == Category.CONFIGS || active == Category.MARKERS) {
            renderConfigOrMarkerUi(x, y, w, h, globalAnim, draw, regular, guiicon);
        } else if (active == Category.COSMETICS) {
            renderCosmeticsUi(context, x, y, w, h, globalAnim, draw, regular);
        }

        if (!isCustomPanelCategory()) {

        float xSetBase = x + 6 * S + 36 * S + 140 * S + 6 * S;
        float wSetBase = w - (6 * S + 36 * S + 6 * S + 140 * S + 6 * S);
        float ySetBase = y + 6 * S + 26 * S;
        float hSetBase = h - 12 * S - 26 * S;

        RenderUtil.Render2D.glow(xSetBase, ySetBase, wSetBase, hSetBase, ColorUtil.getColor(0, 0.04F * globalAnim), 7 * S, 8, 1);
        RenderUtil.Render2D.rect(xSetBase, ySetBase, wSetBase, hSetBase, ColorUtil.getColor(0, 0.15F * globalAnim), 7 * S);

        Scissor.enable(xSetBase, ySetBase, wSetBase, hSetBase, 2);

        float xSetting = xSetBase;
        float ySetting = ySetBase;
        float wSetting = wSetBase;
        float hSetting = hSetBase;

        animation2.setDirection(select != null ? Direction.FORWARDS : Direction.BACKWARDS);

        settingScrollAnim += (settingScrollTarget - settingScrollAnim) * 0.2F;

        float xST = xSetting + 10 * S;
        float yST = ySetting + 10 * S - settingScrollAnim;
        float wST = wSetting - 20 * S;
        float setTop = ySetting;
        float setBottom = ySetting + hSetting;

        for (Module f : Client.get().moduleManager().values()) {
            f.animation3.setDirection(f == select ? Direction.FORWARDS : Direction.BACKWARDS);
            float fa = f.animation3.getOutput();

            if (fa > 0) {
                for (Setting setting : f.getSettings()) {

                    if (setting instanceof SliderSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            float percent = MathHelper.clamp((s.getValue() - s.min) / (s.max - s.min), 0, 1);
                            s.getAnimation().update();
                            s.getAnimation().run(percent, 0.06F, Easings.LINEAR);

                            boolean onScreen = yST + 25 * S >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, 24 * S);
                            s.animation.setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.animation.getOutput();
                            float track = wST - 12 * S;

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, 25 * S, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, 25 * S, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, 25 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);
                                draw.draw(s.getName(), xST + 6 * S, yST + 5 * S, 6.5F * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)));

                                String val = String.valueOf((Math.round(s.getValue() * 100.0) / 100.0));
                                draw.draw(val, xST + wST - 6 * S - draw.getWidth(val, 6 * S), yST + 5.25F * S, 6 * S, ColorUtil.replAlpha(ColorUtil.client(), (globalAnim * sa) * (0.6F + 0.4F * hover)));

                                RenderUtil.Render2D.rect(xST + 6 * S, yST + 16.5F * S, track, 3 * S, ColorUtil.getColor(0, (globalAnim * sa) * (0.15F)), 1.5F * S);
                                RenderUtil.Render2D.rect(xST + 6 * S, yST + 16.5F * S, track * s.getAnimation().get(), 3 * S, ColorUtil.replAlpha(ColorUtil.client(), (globalAnim * sa) * (0.5F + 0.5F * hover)), 1.5F * S);
                                RenderUtil.Render2D.rect(xST + 6 * S + track * s.getAnimation().get() - 3 * S, yST + 15 * S, 6 * S, 6 * S, ColorUtil.getColor((int) (200 + 55 * hover), (globalAnim * sa)), 6 * S);
                                RenderUtil.Render2D.rect(xST + 6 * S + track * s.getAnimation().get() - 2 * S, yST + 16 * S, 4 * S, 4 * S, ColorUtil.replAlpha(ColorUtil.client(), (globalAnim * sa) * (0.7F + 0.3F * hover)), 6 * S);
                            }
                            if (draggingSlider == s) {
                                float perc = MathUtil.clamp(((float) lastMouseX - (xST + 6 * S)) / track, 0, 1);
                                float newVal = MathUtil.clamp(MathUtil.round(s.min + (s.max - s.min) * perc, s.increment), s.min, s.max);
                                if (s.getValue() != newVal) { s.set(newVal); GuiSounds.sliderTick(perc); }
                            }
                            yST += 30 * S * fa * vis;
                        }
                    }

                    if (setting instanceof ModeSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            boolean expanded = expandedModes.contains(s.getName());
                            ru.white.utils.animation.satoshi.Animation ea = expandAnim(f.getName() + ":" + s.getName());
                            ea.setDirection(expanded ? Direction.FORWARDS : Direction.BACKWARDS);
                            float expandT = ea.getOutput();

                            float chipsH = modeChipsHeight(s, wST - 12 * S) + 4 * S;
                            float hMode = 15 * S + chipsH * expandT;
                            boolean onScreen = yST + hMode >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, hMode);
                            s.animation.setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.animation.getOutput();

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, hMode, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, hMode, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, hMode, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);

                                String current = s.getValue();
                                String arrow = expanded ? " \u25B2" : " \u25BC";
                                draw.drawFadingText(s.getName(), xST + 6 * S, yST + 4.5F * S, wST - draw.getWidth(current + arrow, 6 * S) - 16 * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)), 6.5F * S);
                                draw.draw(current + arrow, xST + wST - 6 * S - draw.getWidth(current + arrow, 6 * S), yST + 4.75F * S, 6 * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * (0.6F + 0.4F * hover)));

                                if (expandT > 0.01F) {
                                    float chipMaxW = wST - 12 * S;
                                    float px = 0, py = 0;
                                    for (String val : s.values) {
                                        float tw = draw.getWidth(val, 6 * S) + 8 * S;
                                        if (px + tw > chipMaxW && px > 0) { px = 0; py += 12 * S; }
                                        float cx = xST + 6 * S + px;
                                        float cyy = yST + 15 * S + py;
                                        ru.white.utils.animation.satoshi.Animation chip = chipAnim(f.getName() + ":" + s.getName() + ":" + val);
                                        chip.setDirection(val.equals(current) ? Direction.FORWARDS : Direction.BACKWARDS);
                                        float sel = chip.getOutput();

                                        RenderUtil.Render2D.rect(cx, cyy, tw, 10 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.25F * globalAnim * sa * expandT), ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * (0.5F + 0.5F * hover) * expandT), sel), 3 * S);
                                        regular.drawCentered(val, cx + tw / 2, cyy + 1.5F * S, 6 * S, ColorUtil.getColor(255, globalAnim * sa * (0.35F + 0.65F * sel) * (0.7F + 0.3F * hover) * expandT));
                                        px += tw + 3 * S;
                                    }
                                }
                            }
                            yST += (hMode + 5 * S) * fa * vis;
                        }
                    }

                    if (setting instanceof MultiBooleanSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            float hMulti = 15 * S + multiChipsHeight(s, wST - 12 * S) + 4 * S;
                            boolean onScreen = yST + hMulti >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, hMulti);
                            s.getAnimation1().setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.getAnimation1().getOutput();

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, hMulti, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, hMulti, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, hMulti, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);

                                int total = s.getValues().size();
                                int selectedCount = 0;
                                for (BooleanSetting b : s.getValues()) if (b.getValue()) selectedCount++;
                                String counter = selectedCount + " / " + total;

                                draw.drawFadingText(s.getName(), xST + 6 * S, yST + 4.5F * S, wST - draw.getWidth(counter, 6 * S) - 16 * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)), 6.5F * S);
                                draw.draw(counter, xST + wST - 6 * S - draw.getWidth(counter, 6 * S), yST + 4.75F * S, 6 * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * (0.6F + 0.4F * hover)));

                                float chipMaxW = wST - 12 * S;
                                float px = 0, py = 0;
                                for (BooleanSetting b : s.getValues()) {
                                    float tw = draw.getWidth(b.getName(), 6 * S) + 8 * S;
                                    if (px + tw > chipMaxW && px > 0) { px = 0; py += 12 * S; }
                                    float cx = xST + 6 * S + px;
                                    float cyy = yST + 15 * S + py;
                                    ru.white.utils.animation.satoshi.Animation chip = chipAnim(f.getName() + ":" + s.getName() + ":" + b.getName());
                                    chip.setDirection(b.getValue() ? Direction.FORWARDS : Direction.BACKWARDS);
                                    float sel = chip.getOutput();

                                    RenderUtil.Render2D.rect(cx, cyy, tw, 10 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.25F * globalAnim * sa), ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * (0.5F + 0.5F * hover)), sel), 3 * S);
                                    regular.drawCentered(b.getName(), cx + tw / 2, cyy + 1.5F * S, 6 * S, ColorUtil.getColor(255, globalAnim * sa * (0.35F + 0.65F * sel) * (0.7F + 0.3F * hover)));
                                    px += tw + 3 * S;
                                }
                            }
                            yST += (hMulti + 5 * S) * fa * vis;
                        }
                    }

                    if (setting instanceof BooleanSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            boolean onScreen = yST + 16 * S >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, 16 * S);
                            s.animation.setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.animation.getOutput();
                            s.animation2.setDirection(s.getValue() ? Direction.FORWARDS : Direction.BACKWARDS);
                            float sanimation2 = s.animation2.getOutput();

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, 16 * S, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, 16 * S, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, 16 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);

                                draw.draw(s.getName(), xST + 6 * S, yST + 4 * S, 6.5F * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)));
                                RenderUtil.Render2D.rect(xST + wST - 6 * S - 12 * S, yST + 4.5F * S, 12 * S, 7 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.2F * globalAnim * sa), ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * (0.5F + 0.5F * hover)), sanimation2), 3.5F * S);
                                RenderUtil.Render2D.rect(xST + wST - 6 * S - 12 * S + 1.25F * S + 4.5F * S * sanimation2, yST + 5.75F * S, 4.5F * S, 4.5F * S, ColorUtil.getColor(255, globalAnim * sa * (0.2F + 0.4F * sanimation2 + 0.4F * hover)), 2.5F * S);
                            }
                            yST += 21 * S * fa * vis;
                        }
                    }

                    if (setting instanceof ButtonSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            boolean onScreen = yST + 16 * S >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, 16 * S);
                            ru.white.utils.animation.satoshi.Animation btn = chipAnim(f.getName() + ":" + s.getName() + ":btn");
                            btn.setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = btn.getOutput();

                            s.pressAnim.update();
                            float press = s.pressAnim.get();
                            float accent = Math.max(hover, press);

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, 16 * S, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, 16 * S, ColorUtil.overCol(ColorUtil.getColor(40, 0.15F * globalAnim * sa), ColorUtil.replAlpha(ColorUtil.client(), 0.25F * globalAnim * sa), accent), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, 16 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * accent), 5 * S);
                                RenderUtil.Render2D.glow(xST, yST, wST, 16 * S, ColorUtil.replAlpha(ColorUtil.client(), 0.15F * globalAnim * sa * press), 5 * S, 8, 1);
                                draw.drawCentered(s.getName(), xST + wST / 2, yST + 4 * S, 6.5F * S, ColorUtil.overCol(ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)), ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa), press));
                            }
                            yST += 21 * S * fa * vis;
                        }
                    }

                    if (setting instanceof BindSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            boolean onScreen = yST + 16 * S >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, 16 * S);
                            s.animation.setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.animation.getOutput();

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, 16 * S, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, 16 * S, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, 16 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);

                                boolean binding = activeBind == s;
                                ru.white.utils.animation.satoshi.Animation bindAct = chipAnim(f.getName() + ":" + s.getName() + ":act");
                                bindAct.setDirection(binding ? Direction.FORWARDS : Direction.BACKWARDS);
                                float act = bindAct.getOutput();

                                String keyName = binding ? "..." : (s.get() == -1 ? "n/a" : Keyboard.keyName(s.get()));
                                float bw = smooth(f.getName() + ":" + s.getName() + ":bw", draw.getWidth(keyName, 6 * S) + 10 * S);

                                draw.drawFadingText(s.getName(), xST + 6 * S, yST + 4 * S, wST - bw - 16 * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)), 6.5F * S);
                                RenderUtil.Render2D.rect(xST + wST - 6 * S - bw, yST + 3 * S, bw, 10 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.25F * globalAnim * sa), ColorUtil.replAlpha(ColorUtil.client(), 0.15F * globalAnim * sa), act), 3 * S);
                                RenderUtil.Render2D.outline(xST + wST - 6 * S - bw, yST + 3 * S, bw, 10 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * act), 3 * S);
                                RenderUtil.Render2D.glow(xST + wST - 6 * S - bw, yST + 3 * S, bw, 10 * S, ColorUtil.replAlpha(ColorUtil.client(), 0.12F * globalAnim * sa * act), 3 * S, 6, 1);
                                regular.drawCentered(keyName, xST + wST - 6 * S - bw / 2, yST + 4.5F * S, 6 * S, ColorUtil.replAlpha(ColorUtil.overCol(ColorUtil.getColor(255), ColorUtil.client(), act), globalAnim * sa * (0.45F + 0.55F * Math.max(act, hover))));
                            }
                            yST += 21 * S * fa * vis;
                        }
                    }

                    if (setting instanceof StringSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            boolean onScreen = yST + 16 * S >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, 16 * S);
                            s.getAnimation1().setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.getAnimation1().getOutput();

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, 16 * S, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, 16 * S, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, 16 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);

                                boolean editing = activeString == s;
                                ru.white.utils.animation.satoshi.Animation editAct = chipAnim(f.getName() + ":" + s.getName() + ":act");
                                editAct.setDirection(editing ? Direction.FORWARDS : Direction.BACKWARDS);
                                float act = editAct.getOutput();

                                String shown = editing ? stringBuffer + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "") : s.getValue();
                                if (shown.isEmpty()) shown = "...";

                                float bw = smooth(f.getName() + ":" + s.getName() + ":bw", Math.min(draw.getWidth(shown, 6 * S) + 10 * S, wST * 0.55F));

                                draw.drawFadingText(s.getName(), xST + 6 * S, yST + 4 * S, wST - bw - 16 * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)), 6.5F * S);
                                RenderUtil.Render2D.rect(xST + wST - 6 * S - bw, yST + 3 * S, bw, 10 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.25F * globalAnim * sa), ColorUtil.replAlpha(ColorUtil.client(), 0.15F * globalAnim * sa), act), 3 * S);
                                RenderUtil.Render2D.outline(xST + wST - 6 * S - bw, yST + 3 * S, bw, 10 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * act), 3 * S);
                                RenderUtil.Render2D.glow(xST + wST - 6 * S - bw, yST + 3 * S, bw, 10 * S, ColorUtil.replAlpha(ColorUtil.client(), 0.12F * globalAnim * sa * act), 3 * S, 6, 1);
                                regular.drawFadingText(shown, xST + wST - 6 * S - bw + 5 * S, yST + 4.5F * S, bw - 8 * S, ColorUtil.getColor(255, globalAnim * sa * (0.45F + 0.55F * Math.max(act, hover))), 6 * S);
                            }
                            yST += 21 * S * fa * vis;
                        }
                    }

                    if (setting instanceof ColorSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            s.pickerAnim.setDirection(s.pickerOpen ? Direction.FORWARDS : Direction.BACKWARDS);
                            float open = s.pickerAnim.getOutput();
                            float hCol = 16 * S + open * 35 * S;

                            boolean onScreen = yST + hCol >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, hCol);
                            s.getAnimation1().setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.getAnimation1().getOutput();

                            float bx = xST + 6 * S;
                            float bwd = wST - 12 * S;
                            int col = s.getValue();
                            String kb = f.getName() + ":" + s.getName();

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, hCol, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, hCol, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, hCol, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);

                                draw.drawFadingText(s.getName(), xST + 6 * S, yST + 4 * S, wST - 34 * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)), 6.5F * S);

                                int pr = (int) smooth(kb + ":pr", (col >> 16) & 0xFF);
                                int pg = (int) smooth(kb + ":pg", (col >> 8) & 0xFF);
                                int pb = (int) smooth(kb + ":pb", col & 0xFF);

                                float pcx = xST + wST - 6 * S - 9 * S;
                                float pcy = yST + 4 * S;

                                RenderUtil.Render2D.rect(pcx, pcy, 8 * S, 8 * S, ColorUtil.getColor(pr, pg, pb, globalAnim * sa), 8 * S);
                                RenderUtil.Render2D.outline(pcx - 0.75F * S, pcy - 0.75F * S, 9.5F * S, 9.5F * S, 0.25F * S, ColorUtil.getColor(pr, pg, pb, globalAnim * sa * (0.4F + 0.6F * hover)), 8 * S);
                                RenderUtil.Render2D.glow(pcx, pcy, 8 * S, 8 * S, ColorUtil.getColor(pr, pg, pb, 0.1F * globalAnim * sa), 8 * S, 7, 1);

                                if (open > 0.01F) {
                                    float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
                                    float shh = smooth(kb + ":h", hsb[0]);
                                    float shs = smooth(kb + ":s", hsb[1]);
                                    float shb = smooth(kb + ":b", hsb[2]);
                                    float[] smoothHsb = {shh, shs, shb};

                                    int segs = 16;
                                    float seg = bwd / segs;

                                    for (int bar = 0; bar < 3; bar++) {
                                        float by = yST + 18 * S + bar * 12 * S;

                                        for (int i = 0; i < segs; i++) {
                                            float t0 = (float) i / segs;
                                            float t1 = (float) (i + 1) / segs;
                                            int c0 = switch (bar) { case 0 -> java.awt.Color.HSBtoRGB(t0, 1F, 1F); case 1 -> java.awt.Color.HSBtoRGB(shh, t0, shb); default -> java.awt.Color.HSBtoRGB(shh, shs, t0); };
                                            int c1 = switch (bar) { case 0 -> java.awt.Color.HSBtoRGB(t1, 1F, 1F); case 1 -> java.awt.Color.HSBtoRGB(shh, t1, shb); default -> java.awt.Color.HSBtoRGB(shh, shs, t1); };
                                            int a0 = ColorUtil.replAlpha(c0, globalAnim * sa * open);
                                            int a1 = ColorUtil.replAlpha(c1, globalAnim * sa * open);

                                            RenderUtil.Render2D.gradientRect(bx + i * seg, by, seg + (i == segs - 1 ? 0 : 0.5F * S), 4 * S, new int[]{a0, a1, a1, a0}, i == 0 ? 2 * S : 0, i == segs - 1 ? 2 * S : 0, i == segs - 1 ? 2 * S : 0, i == 0 ? 2 * S : 0);
                                        }

                                        float kx = bx + bwd * smoothHsb[bar];
                                        int kc = switch (bar) { case 0 -> java.awt.Color.HSBtoRGB(shh, 1F, 1F); default -> java.awt.Color.HSBtoRGB(shh, shs, shb); };
                                        RenderUtil.Render2D.rect(kx - 3 * S, by - 1 * S, 6 * S, 6 * S, ColorUtil.getColor(255, globalAnim * sa * open), 6 * S);
                                        RenderUtil.Render2D.rect(kx - 2 * S, by, 4 * S, 4 * S, ColorUtil.replAlpha(kc, globalAnim * sa * open), 6 * S);
                                    }
                                }
                            }

                            if (open > 0.01F && draggingColor == s) {
                                float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
                                float t = MathUtil.clamp(((float) lastMouseX - bx) / bwd, 0, 1);
                                float[] nh = {hsb[0], hsb[1], hsb[2]};
                                nh[draggingColorBar] = t;
                                int rgb = java.awt.Color.HSBtoRGB(nh[0], nh[1], nh[2]);
                                int newCol = (col & 0xFF000000) | (rgb & 0x00FFFFFF);
                                if (newCol != col) { s.set(newCol); GuiSounds.colorTick(t); }
                            }
                            yST += (hCol + 5 * S) * fa * vis;
                        }
                    }
                }
            }
        }

        float contentHS = yST + settingScrollAnim - (ySetting + 10 * S);
        settingMaxScroll = Math.max(0, contentHS - (hSetting - 14 * S));
        if (settingScrollTarget > settingMaxScroll) settingScrollTarget = settingMaxScroll;

        Scissor.disable();
        } // end if not CONFIGS/MARKERS
        Render2D.endOverlay();

        if (context != null) context.getMatrices().popMatrix();
    }

    public void openHandsEditor() { beforeEditorOpen(); handsEditor.open(); GuiSounds.editor(); }
    public void openPreviewEditor(Module target) { beforeEditorOpen(); PreviewEditor.getInstance().open(target); GuiSounds.editor(); }
    public void openCrosshairEditor() { beforeEditorOpen(); CrosshairEditor.getInstance().open(); GuiSounds.editor(); }
    private void beforeEditorOpen() { exit = false; glomalAnim.run(1, 0.2F, Easings.QUAD_OUT); }
    @Override public void removed() {
        OverlayEditors.closeAll();
        returnToGlassAfterEditor = false;
        super.removed();
    }
    private void closeCheck() {
        if (returnToGlassAfterEditor && OverlayEditors.active() == null) {
            returnToGlassAfterEditor = false;
            OverlayEditors.closeAll();
            mc.setScreen(ru.white.ui.lv.LvGui.INSTANCE);
            return;
        }
        if (exit && glomalAnim.isFinished()) { close(); GuiMusicPlayer.stop(); exit = false; }
    }

    private void closeMenuToWorld() {
        exit = false;
        mc.setScreen(null);
        GuiMusicPlayer.stop();
    }

    // ── Configs & Markers UI ─────────────────────────────────────────────────

    private boolean isCosmeticsCategory() { return active == Category.COSMETICS; }
    private boolean isConfigCategory() { return active == Category.CONFIGS; }
    private boolean isMarkerCategory() { return active == Category.MARKERS; }
    private boolean isSpecialCategory() { return isCosmeticsCategory() || isConfigCategory() || isMarkerCategory(); }
    /** Разделы, у которых свой UI вместо списка модулей и настроек. */
    private boolean isCustomPanelCategory() { return isSpecialCategory(); }

    private float cfgInputX, cfgInputY, cfgInputW, cfgInputH;
    private float cfgAddX, cfgAddY, cfgAddW, cfgAddH;
    private float cfgListX, cfgListY, cfgListW, cfgListH;

    private float mkNameX, mkNameY, mkNameW, mkNameH;
    private float mkXInputX, mkXInputY, mkXInputW, mkXInputH;
    private float mkYInputX, mkYInputY, mkYInputW, mkYInputH;
    private float mkZInputX, mkZInputY, mkZInputW, mkZInputH;
    private float mkAddX, mkAddY, mkAddW, mkAddH;
    private float mkListX, mkListY, mkListW, mkListH;

    private void renderConfigOrMarkerUi(float x, float y, float w, float h, float globalAnim, Font draw, Font regular, Font guiicon) {
        Font font = Fonts.sf_regular;

        float xLeft = x + 6 * S + 36 * S;
        float yTop = y + 6 * S + 26 * S;
        float wLeft = 140 * S;
        float hArea = h - 12 * S - 26 * S;

        float xRight = x + 6 * S + 36 * S + 140 * S + 6 * S;
        float wRight = w - (6 * S + 36 * S + 6 * S + 140 * S + 6 * S);

        RenderUtil.Render2D.glow(xLeft, yTop, wLeft, hArea, ColorUtil.getColor(0, 0.04F * globalAnim), 7 * S, 8, 1);
        RenderUtil.Render2D.rect(xLeft, yTop, wLeft, hArea, ColorUtil.getColor(0, 0.15F * globalAnim), 7 * S);

        RenderUtil.Render2D.glow(xRight, yTop, wRight, hArea, ColorUtil.getColor(0, 0.04F * globalAnim), 7 * S, 8, 1);
        RenderUtil.Render2D.rect(xRight, yTop, wRight, hArea, ColorUtil.getColor(0, 0.15F * globalAnim), 7 * S);

        if (isConfigCategory()) {
            renderConfigPanel(xLeft, yTop, wLeft, hArea, globalAnim, draw, regular, font);
            renderConfigList(xRight, yTop, wRight, hArea, globalAnim, draw, regular, font);
        } else {
            renderMarkerPanel(xLeft, yTop, wLeft, hArea, globalAnim, draw, regular, font);
            renderMarkerList(xRight, yTop, wRight, hArea, globalAnim, draw, regular, font);
        }
    }

    private void drawInputField(float ix, float iy, float iw, float ih, String text, boolean focused, String placeholder, float globalAnim, Font font) {
        float focusAnim = focused ? 1F : 0F;
        int bg = ColorUtil.getColor(40, 0.15F * globalAnim);
        int border = ColorUtil.replAlpha(ColorUtil.client(), globalAnim * 0.3F * focusAnim);
        RenderUtil.Render2D.rect(ix, iy, iw, ih, bg, 4 * S);
        if (focused) RenderUtil.Render2D.outline(ix, iy, iw, ih, 0.5F * S, border, 4 * S);
        String shown;
        if (text.isEmpty() && !focused) {
            shown = placeholder;
            font.draw(shown, ix + 6 * S, iy + (ih - 6 * S) / 2F, 6 * S, ColorUtil.getColor(140, globalAnim * 0.6F));
        } else {
            boolean blink = focused && (System.currentTimeMillis() / 450L) % 2L == 0L;
            shown = text + (blink ? "_" : "");
            font.draw(shown, ix + 6 * S, iy + (ih - 6 * S) / 2F, 6 * S, ColorUtil.getColor(200, globalAnim));
        }
    }

    private void drawSmallButton(float bx, float by, float bw, float bh, String label, float globalAnim, Font font, boolean hovered) {
        float hp = hovered ? 1F : 0F;
        int bg = ColorUtil.overCol(ColorUtil.getColor(40, 0.15F * globalAnim), ColorUtil.replAlpha(ColorUtil.client(), 0.2F * globalAnim), hp);
        RenderUtil.Render2D.rect(bx, by, bw, bh, bg, 4 * S);
        font.drawCentered(label, bx + bw / 2F, by + (bh - 6 * S) / 2F, 6 * S, ColorUtil.getColor(200, globalAnim * (0.6F + 0.4F * hp)));
    }

    // ── Config Panel ─────────────────────────────────────────────────────────

    private void renderConfigPanel(float xLeft, float yTop, float wLeft, float hArea, float globalAnim, Font draw, Font regular, Font font) {
        float pad = 8 * S;
        float inputH = 18 * S;
        float btnH = 18 * S;
        float gap = 5 * S;

        cfgInputX = xLeft + pad;
        cfgInputY = yTop + pad;
        cfgInputW = wLeft - pad * 2;
        cfgInputH = inputH;

        cfgAddX = xLeft + pad;
        cfgAddY = cfgInputY + cfgInputH + gap;
        cfgAddW = wLeft - pad * 2;
        cfgAddH = btnH;

        drawInputField(cfgInputX, cfgInputY, cfgInputW, cfgInputH, configInput, configInputFocused, "Имя конфига", globalAnim, font);

        boolean addHov = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, cfgAddX, cfgAddY, cfgAddW, cfgAddH);
        drawSmallButton(cfgAddX, cfgAddY, cfgAddW, cfgAddH, "+ Добавить", globalAnim, font, addHov);
    }

    private void renderConfigList(float xRight, float yTop, float wRight, float hArea, float globalAnim, Font draw, Font regular, Font font) {
        float pad = 8 * S;
        float rowH = 20 * S;
        float rowGap = 3 * S;

        cfgListX = xRight + pad;
        cfgListY = yTop + pad;
        cfgListW = wRight - pad * 2;
        cfgListH = hArea - pad * 2;

        configCache.clear();
        configCache.addAll(Client.get().configManager().listConfigs());

        float contentH = configCache.size() * (rowH + rowGap);
        float maxScroll = Math.max(0, contentH - cfgListH);
        configScrollTarget = MathUtil.clamp(configScrollTarget, 0, maxScroll);
        configScroll += (configScrollTarget - configScroll) * 0.2F;

        configRowRects.clear();
        configDelRects.clear();

        Scissor.enable(cfgListX, cfgListY, cfgListW, cfgListH, 2);

        if (configCache.isEmpty()) {
            font.drawCentered("Нет конфигов", cfgListX + cfgListW / 2F, cfgListY + cfgListH / 2F - 4 * S, 7 * S, ColorUtil.getColor(255, globalAnim * 0.15F));
        }

        float ry = cfgListY - configScroll;
        for (int i = 0; i < configCache.size(); i++) {
            String name = configCache.get(i);
            float rh = rowH;
            boolean onScreen = ry + rh >= cfgListY && ry <= cfgListY + cfgListH;
            boolean hov = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, cfgListX, ry, cfgListW, rh);
            float ha = hov ? 1F : 0F;

            if (onScreen) {
                int rowBg = ColorUtil.overCol(ColorUtil.getColor(0, 0.12F * globalAnim), ColorUtil.getColor(25, 0.25F * globalAnim), ha);
                RenderUtil.Render2D.rect(cfgListX, ry, cfgListW, rh, rowBg, 4 * S);

                boolean isAuto = name.equals("auto");
                String displayName = isAuto ? name + " (авто)" : name;
                draw.draw(displayName, cfgListX + 8 * S, ry + (rh - 7 * S) / 2F, 7 * S, ColorUtil.getColor(255, globalAnim * (0.5F + 0.3F * ha)));

                float delSize = 12 * S;
                float delX = cfgListX + cfgListW - delSize - 4 * S;
                float delY = ry + (rh - delSize) / 2F;
                boolean delHov = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, delX, delY, delSize, delSize);
                float dha = delHov ? 1F : 0F;
                int delBg = ColorUtil.overCol(ColorUtil.getColor(0, 0.15F * globalAnim), ColorUtil.getColor(125, 50, 50, 0.3F * globalAnim), dha);
                RenderUtil.Render2D.rect(delX, delY, delSize, delSize, delBg, 3 * S);
                Fonts.icon.drawCentered("W", delX + delSize / 2F, delY + delSize / 2F - 3.5F, 7 * S, ColorUtil.getColor(255, globalAnim * (0.4F + 0.4F * dha)));

                configRowRects.add(new float[]{cfgListX, ry, cfgListW - delSize - 12 * S, rh});
                configDelRects.add(new float[]{delX, delY, delSize, delSize});
            }
            ry += rh + rowGap;
        }

        Scissor.disable();
    }

    // ── Marker Panel ─────────────────────────────────────────────────────────

    private void renderMarkerPanel(float xLeft, float yTop, float wLeft, float hArea, float globalAnim, Font draw, Font regular, Font font) {
        float pad = 8 * S;
        float inputH = 16 * S;
        float gap = 4 * S;
        float btnH = 18 * S;
        float labelH = 8 * S;

        float labelY = yTop + pad;
        font.draw("Название", xLeft + pad, labelY, 5.5F, ColorUtil.getColor(200, globalAnim * 0.5F));
        mkNameX = xLeft + pad;
        mkNameY = labelY + labelH;
        mkNameW = wLeft - pad * 2;
        mkNameH = inputH;

        float coordRowY = mkNameY + mkNameH + gap + 2 * S;
        font.draw("X", xLeft + pad, coordRowY, 5.5F, ColorUtil.getColor(200, globalAnim * 0.5F));
        font.draw("Y", xLeft + pad + (wLeft - pad * 2) / 3F + gap / 2F, coordRowY, 5.5F, ColorUtil.getColor(200, globalAnim * 0.5F));
        font.draw("Z", xLeft + pad + 2 * ((wLeft - pad * 2) / 3F + gap / 2F), coordRowY, 5.5F, ColorUtil.getColor(200, globalAnim * 0.5F));

        float coordInputY = coordRowY + labelH;
        float coordW = (wLeft - pad * 2 - gap * 2) / 3F;

        mkXInputX = xLeft + pad;
        mkXInputY = coordInputY;
        mkXInputW = coordW;
        mkXInputH = inputH;

        mkYInputX = xLeft + pad + coordW + gap;
        mkYInputY = coordInputY;
        mkYInputW = coordW;
        mkYInputH = inputH;

        mkZInputX = xLeft + pad + 2 * (coordW + gap);
        mkZInputY = coordInputY;
        mkZInputW = coordW;
        mkZInputH = inputH;

        mkAddX = xLeft + pad;
        mkAddY = coordInputY + inputH + gap + 4 * S;
        mkAddW = wLeft - pad * 2;
        mkAddH = btnH;

        drawInputField(mkNameX, mkNameY, mkNameW, mkNameH, markerNameInput, markerInputFocused == 0, "Имя метки", globalAnim, font);
        drawInputField(mkXInputX, mkXInputY, mkXInputW, mkXInputH, markerXInput, markerInputFocused == 1, "0", globalAnim, font);
        drawInputField(mkYInputX, mkYInputY, mkYInputW, mkYInputH, markerYInput, markerInputFocused == 2, "0", globalAnim, font);
        drawInputField(mkZInputX, mkZInputY, mkZInputW, mkZInputH, markerZInput, markerInputFocused == 3, "0", globalAnim, font);

        boolean addHov = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, mkAddX, mkAddY, mkAddW, mkAddH);
        drawSmallButton(mkAddX, mkAddY, mkAddW, mkAddH, "+ Добавить", globalAnim, font, addHov);
    }

    private void renderMarkerList(float xRight, float yTop, float wRight, float hArea, float globalAnim, Font draw, Font regular, Font font) {
        float pad = 8 * S;
        float rowH = 20 * S;
        float rowGap = 3 * S;

        mkListX = xRight + pad;
        mkListY = yTop + pad;
        mkListW = wRight - pad * 2;
        mkListH = hArea - pad * 2;

        markerCache.clear();
        ru.white.command.impl.WayCommand wc = ru.white.command.impl.WayCommand.get();
        if (wc != null) {
            for (ru.white.command.impl.WayCommand.Waypoint wp : wc.getWaypoints()) {
                markerCache.add(new String[]{wp.name(), String.valueOf((int) wp.pos().x), String.valueOf((int) wp.pos().y), String.valueOf((int) wp.pos().z)});
            }
        }

        float contentH = markerCache.size() * (rowH + rowGap);
        float maxScroll = Math.max(0, contentH - mkListH);
        markerScrollTarget = MathUtil.clamp(markerScrollTarget, 0, maxScroll);
        markerScroll += (markerScrollTarget - markerScroll) * 0.2F;

        markerRowRects.clear();
        markerDelRects.clear();

        Scissor.enable(mkListX, mkListY, mkListW, mkListH, 2);

        if (markerCache.isEmpty()) {
            font.drawCentered("Нет меток", mkListX + mkListW / 2F, mkListY + mkListH / 2F - 4 * S, 7 * S, ColorUtil.getColor(255, globalAnim * 0.15F));
        }

        float ry = mkListY - markerScroll;
        for (int i = 0; i < markerCache.size(); i++) {
            String[] m = markerCache.get(i);
            float rh = rowH;
            boolean onScreen = ry + rh >= mkListY && ry <= mkListY + mkListH;
            boolean hov = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, mkListX, ry, mkListW, rh);
            float ha = hov ? 1F : 0F;

            if (onScreen) {
                int rowBg = ColorUtil.overCol(ColorUtil.getColor(0, 0.12F * globalAnim), ColorUtil.getColor(25, 0.25F * globalAnim), ha);
                RenderUtil.Render2D.rect(mkListX, ry, mkListW, rh, rowBg, 4 * S);

                String label = m[0] + " " + m[1] + " " + m[2] + " " + m[3];
                draw.draw(label, mkListX + 8 * S, ry + (rh - 7 * S) / 2F, 6.5F, ColorUtil.getColor(255, globalAnim * (0.5F + 0.3F * ha)));

                float delSize = 12 * S;
                float delX = mkListX + mkListW - delSize - 4 * S;
                float delY = ry + (rh - delSize) / 2F;
                boolean delHov = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, delX, delY, delSize, delSize);
                float dha = delHov ? 1F : 0F;
                int delBg = ColorUtil.overCol(ColorUtil.getColor(0, 0.15F * globalAnim), ColorUtil.getColor(125, 50, 50, 0.3F * globalAnim), dha);
                RenderUtil.Render2D.rect(delX, delY, delSize, delSize, delBg, 3 * S);
                Fonts.icon.drawCentered("W", delX + delSize / 2F, delY + delSize / 2F - 3.5F, 7 * S, ColorUtil.getColor(255, globalAnim * (0.4F + 0.4F * dha)));

                markerRowRects.add(new float[]{mkListX, ry, mkListW - delSize - 12 * S, rh});
                markerDelRects.add(new float[]{delX, delY, delSize, delSize});
            }
            ry += rh + rowGap;
        }

        Scissor.disable();
    }

    // ── Cosmetics UI ─────────────────────────────────────────────────────────

    private void renderCosmeticsUi(DrawContext context, float x, float y, float w, float h, float globalAnim, Font draw, Font regular) {
        float xLeft = x + 6 * S + 36 * S;
        float yTop = y + 6 * S + 26 * S;
        float wLeft = 140 * S;
        float hArea = h - 12 * S - 26 * S;

        float xRight = x + 6 * S + 36 * S + 140 * S + 6 * S;
        float wRight = w - (6 * S + 36 * S + 6 * S + 140 * S + 6 * S);

        renderCosmeticsSubTabs(xLeft, yTop, wLeft, hArea, globalAnim, draw, regular);
        renderCosmeticsGrid(context, xRight, yTop, wRight, hArea, globalAnim, draw, regular);
    }

    private void renderCosmeticsSubTabs(float xLeft, float yTop, float wLeft, float hArea, float globalAnim, Font draw, Font regular) {
        RenderUtil.Render2D.glow(xLeft, yTop, wLeft, hArea, ColorUtil.getColor(0, 0.04F * globalAnim), 7 * S, 8, 1);
        RenderUtil.Render2D.rect(xLeft, yTop, wLeft, hArea, ColorUtil.getColor(0, 0.15F * globalAnim), 7 * S);

        regular.draw("Категории", xLeft + 10 * S, yTop + 8 * S, 6.5F * S, ColorUtil.getColor(160, 0.6F * globalAnim));
        RenderUtil.Render2D.rect(xLeft + 8 * S, yTop + 18 * S, wLeft - 16 * S, 0.5F * S, ColorUtil.getColor(255, 0.05F * globalAnim), 0.25F * S);

        float ySubTab = yTop + 22 * S;
        float subTabH = 26 * S;
        float gap = 4 * S;
        float pad = 6 * S;
        float tabW = wLeft - pad * 2;
        float tabX = xLeft + pad;

        for (CosmeticCategory cat : CosmeticCategory.values()) {
            boolean isSel = (activeCosmetic == cat);
            boolean isHov = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, tabX, ySubTab, tabW, subTabH);

            cat.selectAnim.setDirection(isSel ? Direction.FORWARDS : Direction.BACKWARDS);
            cat.hoverAnim.setDirection(isHov ? Direction.FORWARDS : Direction.BACKWARDS);
            float selA = cat.selectAnim.getOutput();
            float hovA = cat.hoverAnim.getOutput();

            RenderUtil.Render2D.glow(tabX, ySubTab, tabW, subTabH, ColorUtil.getColor(0, 0.04F * globalAnim * (0.5F + 0.5F * selA)), 5 * S, 6, 1);
            int bgCol = ColorUtil.overCol(ColorUtil.getColor(35, 0.15F * globalAnim), ColorUtil.getColor(55, 0.3F * globalAnim), Math.max(hovA, selA));
            RenderUtil.Render2D.rect(tabX, ySubTab, tabW, subTabH, bgCol, 5 * S);

            if (selA > 0.01F || hovA > 0.01F) {
                RenderUtil.Render2D.outline(tabX, ySubTab, tabW, subTabH, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * (0.8F * selA + 0.3F * hovA)), 5 * S);
            }

            draw.draw(cat.getName(), tabX + 8 * S, ySubTab + 9 * S, 7 * S, ColorUtil.overCol(ColorUtil.getColor(200, 0.8F * globalAnim), ColorUtil.client(), selA));

            String countStr = String.valueOf(cat.getCount());
            float countW = regular.getWidth(countStr, 6 * S) + 8 * S;
            float countH = 12 * S;
            float countX = tabX + tabW - 6 * S - countW;
            float countY = ySubTab + (subTabH - countH) / 2F;
            int badgeBg = ColorUtil.overCol(ColorUtil.getColor(0, 0.25F * globalAnim), ColorUtil.multAlpha(ColorUtil.client(), 0.25F * globalAnim), selA);
            RenderUtil.Render2D.rect(countX, countY, countW, countH, badgeBg, 3 * S);
            regular.drawCentered(countStr, countX + countW / 2F, countY + 3 * S, 6 * S, ColorUtil.overCol(ColorUtil.getColor(160, 0.7F * globalAnim), ColorUtil.client(), selA));

            ySubTab += subTabH + gap;
        }
    }

    private void renderCosmeticsGrid(DrawContext context, float xRight, float yTop, float wRight, float hArea, float globalAnim, Font draw, Font regular) {
        RenderUtil.Render2D.glow(xRight, yTop, wRight, hArea, ColorUtil.getColor(0, 0.04F * globalAnim), 7 * S, 8, 1);
        RenderUtil.Render2D.rect(xRight, yTop, wRight, hArea, ColorUtil.getColor(0, 0.15F * globalAnim), 7 * S);

        draw.draw(activeCosmetic.getName(), xRight + 10 * S, yTop + 8 * S, 7.5F * S, ColorUtil.getColor(230, globalAnim));
        int equippedId = CosmeticManager.get().getEquipped(activeCosmetic);
        String statusText = equippedId > 0 ? "Надето: #" + equippedId : "Не надето";
        regular.draw(statusText, xRight + wRight - 10 * S - regular.getWidth(statusText, 6.5F * S), yTop + 9 * S, 6.5F * S, equippedId > 0 ? ColorUtil.replAlpha(ColorUtil.client(), globalAnim) : ColorUtil.getColor(140, 0.6F * globalAnim));

        RenderUtil.Render2D.rect(xRight + 8 * S, yTop + 20 * S, wRight - 16 * S, 0.5F * S, ColorUtil.getColor(255, 0.05F * globalAnim), 0.25F * S);

        float yGrid = yTop + 24 * S;
        float hGrid = hArea - 28 * S;

        Scissor.enable(xRight + 2 * S, yGrid, wRight - 4 * S, hGrid, 2);

        cosmeticsScrollAnim += (cosmeticsScrollTarget - cosmeticsScrollAnim) * 0.2F;

        float pad = 8 * S;
        float gap = 8 * S;
        float cardW = (wRight - pad * 2 - gap) / 2F;
        float previewH = 62 * S;
        float btnH = 15 * S;
        float cardH = previewH + 34 * S;

        int totalItems = activeCosmetic.getCount();
        cosmeticsItemButtons.clear();

        for (int id = 1; id <= totalItems; id++) {
            int col = (id - 1) % 2;
            int row = (id - 1) / 2;
            float cardX = xRight + pad + col * (cardW + gap);
            float cardY = yGrid + pad + row * (cardH + gap) - cosmeticsScrollAnim;

            boolean onScreen = (cardY + cardH >= yGrid && cardY <= yGrid + hGrid);
            boolean isEquipped = CosmeticManager.get().isEquipped(activeCosmetic, id);
            boolean isCardHovered = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, cardX, cardY, cardW, cardH);

            float btnW = cardW - 10 * S;
            float btnX = cardX + 5 * S;
            float btnY = cardY + cardH - btnH - 5 * S;
            boolean isBtnHovered = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, btnX, btnY, btnW, btnH);

            if (onScreen) {
                cosmeticsItemButtons.add(new CosmeticClickTarget(id, cardX, cardY, cardW, cardH, btnX, btnY, btnW, btnH));

                RenderUtil.Render2D.glow(cardX, cardY, cardW, cardH, ColorUtil.getColor(0, 0.05F * globalAnim), 6 * S, 6, 1);
                RenderUtil.Render2D.rect(cardX, cardY, cardW, cardH, ColorUtil.getColor(28, 0.22F * globalAnim), 6 * S);
                RenderUtil.Render2D.outline(cardX, cardY, cardW, cardH, 0.5F * S, isEquipped ? ColorUtil.replAlpha(ColorUtil.client(), globalAnim * 0.85F) : ColorUtil.getColor(255, (isCardHovered ? 0.2F : 0.06F) * globalAnim), 6 * S);

                CosmeticItem item = CosmeticManager.get().getItem(activeCosmetic, id);
                String itemName = item != null ? item.getName() : activeCosmetic.getName() + " #" + id;

                // Прямоугольник предпросмотра
                float prevPad = 4 * S;
                float prevX = cardX + prevPad;
                float prevY = cardY + prevPad;
                float prevW = cardW - prevPad * 2;
                RenderUtil.Render2D.rect(prevX, prevY, prevW, previewH, ColorUtil.getColor(12, 0.95F * globalAnim), 4 * S);

                // Текстурное/PNG-превью косметики отключено.
                RenderUtil.Render2D.outline(prevX, prevY, prevW, previewH, 0.5F * S, ColorUtil.getColor(40, 0.4F * globalAnim), 4 * S);

                float maxLabelW = cardW - 12 * S;
                String displayLabel = itemName;
                if (draw.getWidth(displayLabel, 6.2F * S) > maxLabelW) {
                    while (displayLabel.length() > 3 && draw.getWidth(displayLabel + "...", 6.2F * S) > maxLabelW) {
                        displayLabel = displayLabel.substring(0, displayLabel.length() - 1);
                    }
                    displayLabel += "...";
                }
                draw.draw(displayLabel, cardX + 6 * S, cardY + previewH + 6.5F * S, 6.2F * S, ColorUtil.getColor(220, globalAnim));

                if (isEquipped) {
                    RenderUtil.Render2D.glow(btnX, btnY, btnW, btnH, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * 0.2F), 4 * S, 5, 1);
                    RenderUtil.Render2D.rect(btnX, btnY, btnW, btnH, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * (isBtnHovered ? 0.4F : 0.28F)), 4 * S);
                    RenderUtil.Render2D.outline(btnX, btnY, btnW, btnH, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * 0.85F), 4 * S);
                    draw.drawCentered("Надето", btnX + btnW / 2F, btnY + 4 * S, 6 * S, ColorUtil.getColor(255, globalAnim));
                } else {
                    int btnBg = ColorUtil.overCol(ColorUtil.getColor(42, 0.2F * globalAnim), ColorUtil.getColor(65, 0.35F * globalAnim), isBtnHovered ? 1F : 0F);
                    RenderUtil.Render2D.rect(btnX, btnY, btnW, btnH, btnBg, 4 * S);
                    RenderUtil.Render2D.outline(btnX, btnY, btnW, btnH, 0.5F * S, isBtnHovered ? ColorUtil.replAlpha(ColorUtil.client(), globalAnim * 0.4F) : ColorUtil.getColor(70, 0.15F * globalAnim), 4 * S);
                    draw.drawCentered("Использовать", btnX + btnW / 2F, btnY + 4 * S, 6 * S, ColorUtil.getColor(190, globalAnim * (isBtnHovered ? 1.0F : 0.7F)));
                }
            }
        }

        int totalRows = (totalItems + 1) / 2;
        float totalContentH = pad * 2 + totalRows * (cardH + gap);
        cosmeticsMaxScroll = Math.max(0, totalContentH - hGrid);
        if (cosmeticsScrollTarget > cosmeticsMaxScroll) cosmeticsScrollTarget = cosmeticsMaxScroll;

        Scissor.reset();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mouseX = (int) (click.x() / scaleFix);
        float mouseY = (int) (click.y() / scaleFix);

        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) { return editor.mouseClicked(mouseX, mouseY, click.button()); }

        if (bindingModule != null) { bindingModule = null; GuiSounds.bindReset(); return true; }

        if (activeBind != null) {
            if (activeBind.allowMouse && click.button() != 0 && click.button() != 1) { activeBind.set(click.button()); GuiSounds.bindSet(); }
            else { GuiSounds.bindReset(); }
            activeBind = null; return true;
        }

        if (activeString != null) { activeString.set(stringBuffer); activeString = null; GuiSounds.editCommit(); }

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        float w = 420 * S;
        float h = 280 * S;
        float x = screenWidth / 2F - w / 2;
        float y = screenHeight / 2F - h / 2;

        float ht = 20 * S;
        float dotsW = (themes.length - 1) * 14 * S + 8 * S;
        float xtd = x + (w - dotsW) / 2;
        float yt = y + h + 5 * S;

        float xps = x + 6 * S + 36 * S;
        float yps = y + 6 * S;

        if (MathUtil.isHovered(mouseX, mouseY, xps, yps, 140 * S, 20 * S)) {
            if (!isNoSearchCategory()) {
                if (click.button() == 1) { clearSearch(); searchActive = false; GuiSounds.searchClear(); }
                else if (click.button() == 0) { if (!searchActive) GuiSounds.editStart(); searchActive = true; searchTypeTime = System.currentTimeMillis(); }
            }
            return true;
        }

        searchActive = false;

        float ytd = yt + 6F * S;
        int themeIndex = 0;

        for (Theme theme : themes) {
            if (MathUtil.isHovered(mouseX, mouseY, xtd, ytd, 8 * S, 8 * S) && click.button() == 0) {
                animation14.reset(); preSelectedTheme = selectedTheme; selectedTheme = theme;
                Client.get().guiManager().setGuiTheme(theme); GuiSounds.theme(themeIndex, themes.length);
            }
            xtd += 14 * S; themeIndex++;
        }

        float xPanelMini = x + 6 * S;
        float yPanelMini = y + 6 * S;
        float csgddd = 0;
        for (Category category : Category.values()) { csgddd += 20 * S; }
        float cy = yPanelMini + 28 * S;
        int catIndex = 0;

        for (Category category : Category.values()) {
            if (MathUtil.isHovered(mouseX, mouseY, xPanelMini + (30 * S) / 2 - (16 * S) / 2, cy, 16 * S, 16 * S) && click.button() == 0 && (active != category || searching())) {
                clearSearch();
                if (active != category) { active = category; select = null; settingScrollTarget = 0; settingScrollAnim = 0; configInputFocused = false; markerInputFocused = -1; cosmeticsScrollTarget = 0; cosmeticsScrollAnim = 0; animCategoryReset.reset(); }
                scrollTarget = 0; GuiSounds.category(catIndex, Category.values().length);
            }
            cy += 20 * S; catIndex++;
        }

        if (isSpecialCategory() && click.button() == 0) {
            if (isCosmeticsCategory()) {
                float xLeft = x + 6 * S + 36 * S;
                float yTop = y + 6 * S + 26 * S;
                float wLeft = 140 * S;
                float subTabH = 26 * S;
                float gap = 4 * S;
                float pad = 6 * S;
                float tabW = wLeft - pad * 2;
                float tabX = xLeft + pad;
                float ySubTab = yTop + 22 * S;

                for (CosmeticCategory cat : CosmeticCategory.values()) {
                    if (MathUtil.isHovered(mouseX, mouseY, tabX, ySubTab, tabW, subTabH)) {
                        if (activeCosmetic != cat) {
                            activeCosmetic = cat;
                            cosmeticsScrollTarget = 0;
                            cosmeticsScrollAnim = 0;
                            GuiSounds.category(cat.ordinal(), CosmeticCategory.values().length);
                        }
                        return true;
                    }
                    ySubTab += subTabH + gap;
                }

                for (CosmeticClickTarget target : cosmeticsItemButtons) {
                    if (MathUtil.isHovered(mouseX, mouseY, target.cardX, target.cardY, target.cardW, target.cardH)
                            || MathUtil.isHovered(mouseX, mouseY, target.btnX, target.btnY, target.btnW, target.btnH)) {
                        CosmeticManager.get().toggleEquip(activeCosmetic, target.id);
                        if (CosmeticManager.get().isEquipped(activeCosmetic, target.id)) {
                            GuiSounds.bindSet();
                        } else {
                            GuiSounds.bindReset();
                        }
                        return true;
                    }
                }
                return true;
            } else if (isConfigCategory()) {
                if (MathUtil.isHovered(mouseX, mouseY, cfgInputX, cfgInputY, cfgInputW, cfgInputH)) {
                    configInputFocused = true; return true;
                }
                if (MathUtil.isHovered(mouseX, mouseY, cfgAddX, cfgAddY, cfgAddW, cfgAddH)) {
                    if (!configInput.trim().isEmpty()) {
                        Client.get().configManager().save(configInput.trim());
                        configInput = ""; configInputFocused = false;
                        GuiSounds.bindSet();
                    }
                    return true;
                }
                for (int i = 0; i < configDelRects.size(); i++) {
                    float[] r = configDelRects.get(i);
                    if (MathUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                        Client.get().configManager().delete(configCache.get(i));
                        GuiSounds.bindReset(); return true;
                    }
                }
                for (int i = 0; i < configRowRects.size(); i++) {
                    float[] r = configRowRects.get(i);
                    if (MathUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                        Client.get().configManager().load(configCache.get(i));
                        GuiSounds.bindSet(); return true;
                    }
                }
                configInputFocused = false;
            } else {
                int[] focusMap = {0, 1, 2, 3};
                float[][] inputs = {{mkNameX, mkNameY, mkNameW, mkNameH}, {mkXInputX, mkXInputY, mkXInputW, mkXInputH}, {mkYInputX, mkYInputY, mkYInputW, mkYInputH}, {mkZInputX, mkZInputY, mkZInputW, mkZInputH}};
                boolean hit = false;
                for (int i = 0; i < 4; i++) {
                    if (MathUtil.isHovered(mouseX, mouseY, inputs[i][0], inputs[i][1], inputs[i][2], inputs[i][3])) {
                        markerInputFocused = i; hit = true; break;
                    }
                }
                if (!hit) markerInputFocused = -1;
                if (MathUtil.isHovered(mouseX, mouseY, mkAddX, mkAddY, mkAddW, mkAddH)) {
                    String name = markerNameInput.trim();
                    if (!name.isEmpty()) {
                        double mx2 = markerXInput.isEmpty() ? (mc.player != null ? mc.player.getX() : 0) : Double.parseDouble(markerXInput);
                        double my2 = markerYInput.isEmpty() ? (mc.player != null ? mc.player.getY() : 0) : Double.parseDouble(markerYInput);
                        double mz2 = markerZInput.isEmpty() ? (mc.player != null ? mc.player.getZ() : 0) : Double.parseDouble(markerZInput);
                        ru.white.command.impl.WayCommand wc = ru.white.command.impl.WayCommand.get();
                        if (wc != null) { wc.addWaypoint(name, mx2, my2, mz2); }
                        markerNameInput = ""; markerXInput = ""; markerYInput = ""; markerZInput = "";
                        markerInputFocused = -1;
                        GuiSounds.bindSet();
                    }
                    return true;
                }
                for (int i = 0; i < markerDelRects.size(); i++) {
                    float[] r = markerDelRects.get(i);
                    if (MathUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                        ru.white.command.impl.WayCommand wc = ru.white.command.impl.WayCommand.get();
                        if (wc != null) { wc.removeWaypoint(markerCache.get(i)[0]); }
                        GuiSounds.bindReset(); return true;
                    }
                }
            }
            return true;
        }
        Font draw = Fonts.sf_regular;
        float xModule = x + 6 * S + 36 * S + 5 * S;
        float yModule = y + 6 * S + 30 * S - scrollAnim;

        boolean insidePanel = MathUtil.isHovered(mouseX, mouseY, x + 6 * S + 36 * S, y + 6 * S + 26 * S, 140 * S, h - 12 * S - 26 * S);

        for (Module f : Client.get().moduleManager().values()) {
            float canim1 = f.getAnimation14().getOutput();
            if (canim1 > 0) {
                float descH = descHeight(draw, f.getDesc()) ;
                float moduleH = Math.max(20 * S, 16 * S + descH + 4 * S);

                if (insidePanel && canim1 > 0.5F && MathUtil.isHovered(mouseX, mouseY, xModule, yModule, 130 * S, moduleH)) {
                    String keyName = bindingModule == f ? "..." : (f.getKey() == -1 ? "n/a" : Keyboard.keyName(f.getKey()).replace("NONE", "n/a"));
                    float keyW = draw.getWidth(keyName, 6 * S) + 9 * S;
                    float keyX = xModule + 130 * S - 14 * S - 5 * S - 5 * S - keyW;

                    if (MathUtil.isHovered(mouseX, mouseY, keyX - 2 * S, yModule + 2F * S, keyW + 4 * S, 14 * S) && click.button() == 0) {
                        bindingModule = f; GuiSounds.bindStart(); return true;
                    }
                    if (click.button() == 2) { bindingModule = f; GuiSounds.bindStart(); return true; }
                    if (click.button() == 0) { f.setEnabled(!f.isEnabled()); }
                    if (click.button() == 1) { select = (f == select) ? null : f; settingScrollTarget = 0; settingScrollAnim = 0; GuiSounds.expand(select == f); }
                }
                yModule += (moduleH + 5 * S) * canim1;
            }
        }

        float xSetting = x + 6 * S + 36 * S + 140 * S + 6 * S;
        float ySetting = y + 6 * S + 26 * S;
        float wSetting = w - (6 * S + 36 * S + 6 * S + 140 * S + 6 * S);
        float hSetting = h - 12 * S - 26 * S;

        if (MathUtil.isHovered(mouseX, mouseY, xSetting, ySetting, wSetting, hSetting) && select != null) {
            float xST = xSetting + 10 * S;
            float yST = ySetting + 10 * S - settingScrollAnim;
            float wST = wSetting - 20 * S;

            for (Setting setting : select.getSettings()) {
                if (setting instanceof SliderSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        if (s.getVisible().get() && click.button() == 0 && MathUtil.isHovered(mouseX, mouseY, xST + 3 * S, yST + 11 * S, wST - 6 * S, 13 * S)) {
                            draggingSlider = s; GuiSounds.sliderGrab();
                        }
                        yST += 30 * S * vis;
                    }
                }
                if (setting instanceof ModeSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        if (s.getVisible().get() && click.button() == 0) {
                            if (MathUtil.isHovered(mouseX, mouseY, xST, yST, wST, 15 * S)) {
                                if (expandedModes.contains(s.getName())) {
                                    expandedModes.remove(s.getName());
                                } else {
                                    expandedModes.add(s.getName());
                                }
                            } else if (expandedModes.contains(s.getName())) {
                                float chipMaxW = wST - 12 * S; float px = 0, py = 0; int chipIndex = 0;
                                for (String val : s.values) {
                                    float tw = draw.getWidth(val, 6 * S) + 8 * S;
                                    if (px + tw > chipMaxW && px > 0) { px = 0; py += 12 * S; }
                                    if (MathUtil.isHovered(mouseX, mouseY, xST + 6 * S + px, yST + 15 * S + py, tw, 10 * S)) {
                                        if (!val.equals(s.getValue())) GuiSounds.chip(chipIndex, s.values.size()); s.set(val);
                                    }
                                    px += tw + 3 * S; chipIndex++;
                                }
                            }
                        }
                        boolean expanded = expandedModes.contains(s.getName());
                        ru.white.utils.animation.satoshi.Animation ea = expandAnim(select.getName() + ":" + s.getName());
                        ea.setDirection(expanded ? Direction.FORWARDS : Direction.BACKWARDS);
                        float expandT = ea.getOutput();
                        float chipsH = modeChipsHeight(s, wST - 12 * S) + 4 * S;
                        float hMode = 15 * S + chipsH * expandT;
                        yST += (hMode + 5 * S) * vis;
                    }
                }
                if (setting instanceof MultiBooleanSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        float hMulti = 15 * S + multiChipsHeight(s, wST - 12 * S) + 4 * S;
                        if (s.getVisible().get() && click.button() == 0) {
                            float chipMaxW = wST - 12 * S; float px = 0, py = 0;
                            for (BooleanSetting b : s.getValues()) {
                                float tw = draw.getWidth(b.getName(), 6 * S) + 8 * S;
                                if (px + tw > chipMaxW && px > 0) { px = 0; py += 12 * S; }
                                if (MathUtil.isHovered(mouseX, mouseY, xST + 6 * S + px, yST + 15 * S + py, tw, 10 * S)) { b.set(!b.getValue()); GuiSounds.chipMulti(b.getValue()); }
                                px += tw + 3 * S;
                            }
                        }
                        yST += (hMulti + 5 * S) * vis;
                    }
                }
                if (setting instanceof BooleanSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        if (s.getVisible().get() && click.button() == 0 && MathUtil.isHovered(mouseX, mouseY, xST, yST, wST, 16 * S)) { s.set(!s.getValue()); GuiSounds.toggle(s.getValue()); }
                        yST += 21 * S * vis;
                    }
                }
                if (setting instanceof ButtonSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        if (s.getVisible().get() && click.button() == 0 && MathUtil.isHovered(mouseX, mouseY, xST, yST, wST, 16 * S)) { s.press(); GuiSounds.button(); }
                        yST += 21 * S * vis;
                    }
                }
                if (setting instanceof BindSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        if (s.getVisible().get() && click.button() == 0) {
                            String keyName = s.get() == -1 ? "n/a" : Keyboard.keyName(s.get());
                            float bw = draw.getWidth(keyName, 6 * S) + 10 * S;
                            if (MathUtil.isHovered(mouseX, mouseY, xST + wST - 6 * S - bw - 2 * S, yST + 1 * S, bw + 4 * S, 14 * S)) { activeBind = s; GuiSounds.bindStart(); }
                        }
                        yST += 21 * S * vis;
                    }
                }
                if (setting instanceof StringSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        if (s.getVisible().get() && click.button() == 0) {
                            String shown = s.getValue().isEmpty() ? "..." : s.getValue();
                            float bw = Math.min(draw.getWidth(shown, 6 * S) + 10 * S, wST * 0.55F);
                            if (MathUtil.isHovered(mouseX, mouseY, xST + wST - 6 * S - bw - 2 * S, yST + 1 * S, bw + 4 * S, 14 * S)) { activeString = s; stringBuffer = s.getValue(); GuiSounds.editStart(); }
                        }
                        yST += 21 * S * vis;
                    }
                }
                if (setting instanceof ColorSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        float open = s.pickerAnim.getOutput();
                        float hCol = 16 * S + open * 35 * S;
                        if (s.getVisible().get() && click.button() == 0) {
                            if (MathUtil.isHovered(mouseX, mouseY, xST, yST, wST, 16 * S)) { s.pickerOpen = !s.pickerOpen; GuiSounds.picker(s.pickerOpen); }
                            else if (s.pickerOpen) {
                                for (int bar = 0; bar < 3; bar++) {
                                    float by = yST + 18 * S + bar * 12 * S;
                                    if (MathUtil.isHovered(mouseX, mouseY, xST + 6 * S, by - 3 * S, wST - 12 * S, 10 * S)) { draggingColor = s; draggingColorBar = bar; GuiSounds.sliderGrab(); }
                                }
                            }
                        }
                        yST += (hCol + 5 * S) * vis;
                    }
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) { return editor.mouseDragged((float) (deltaX / scaleFix), (float) (deltaY / scaleFix)); }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) { return editor.mouseReleased(click.button()); }
        if (draggingSlider != null || draggingColor != null) GuiSounds.sliderRelease();
        draggingSlider = null; draggingColor = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) { if (key == 256) editor.saveAndExit(); return true; }

        if (bindingModule != null) {
            boolean reset = key == 256 || key == 261;
            bindingModule.setKey(reset ? -1 : key); bindingModule = null;
            if (reset) GuiSounds.bindReset(); else GuiSounds.bindSet(); return true;
        }

        if (activeBind != null) {
            boolean reset = key == 256 || key == 261;
            activeBind.set(reset ? -1 : key); activeBind = null;
            if (reset) GuiSounds.bindReset(); else GuiSounds.bindSet(); return true;
        }

        if (searchActive) {
            if (isNoSearchCategory()) { searchActive = false; }
            else if (key == 256) { if (!searchQuery.isEmpty()) clearSearch(); else searchActive = false; GuiSounds.searchClear(); }
            else if (key == 257 || key == 335) { searchActive = false; GuiSounds.editCommit(); }
            else if (key == 259 && !searchQuery.isEmpty()) { searchQuery = searchQuery.substring(0, searchQuery.length() - 1); searchChanged(); GuiSounds.erase(); }
            return true;
        }

        if (activeString != null) {
            if (key == 257 || key == 335) { activeString.set(stringBuffer); activeString = null; GuiSounds.editCommit(); }
            else if (key == 256) { activeString = null; GuiSounds.editCancel(); }
            else if (key == 259 && !stringBuffer.isEmpty()) { stringBuffer = stringBuffer.substring(0, stringBuffer.length() - 1); GuiSounds.erase(); }
            return true;
        }

        if (isSpecialCategory()) {
            if (configInputFocused || markerInputFocused >= 0) {
                if (key == 256 || key == 257 || key == 335) {
                    configInputFocused = false;
                    markerInputFocused = -1;
                    return true;
                }
                if (key == 259) {
                    if (configInputFocused && !configInput.isEmpty()) {
                        configInput = configInput.substring(0, configInput.length() - 1);
                    } else if (markerInputFocused == 0 && !markerNameInput.isEmpty()) {
                        markerNameInput = markerNameInput.substring(0, markerNameInput.length() - 1);
                    } else if (markerInputFocused == 1 && !markerXInput.isEmpty()) {
                        markerXInput = markerXInput.substring(0, markerXInput.length() - 1);
                    } else if (markerInputFocused == 2 && !markerYInput.isEmpty()) {
                        markerYInput = markerYInput.substring(0, markerYInput.length() - 1);
                    } else if (markerInputFocused == 3 && !markerZInput.isEmpty()) {
                        markerZInput = markerZInput.substring(0, markerZInput.length() - 1);
                    }
                    GuiSounds.erase();
                    return true;
                }
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchActive) {
            if (!isNoSearchCategory() && input.isValidChar() && searchQuery.length() < SEARCH_LIMIT) { searchQuery += input.asString(); searchChanged(); GuiSounds.type(); }
            return true;
        }
        if (activeString != null) {
            if (input.isValidChar()) {
                String str = input.asString();
                if (!activeString.isOnlyNumber() || str.matches("[0-9.,-]+")) { stringBuffer += str; GuiSounds.type(); }
            }
            return true;
        }

        if (isSpecialCategory()) {
            if (configInputFocused && input.isValidChar() && configInput.length() < 24) {
                String s = input.asString();
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ') configInput += c;
                }
                GuiSounds.type(); return true;
            }
            if (markerInputFocused >= 0 && input.isValidChar()) {
                String s = input.asString();
                int maxLen = markerInputFocused == 0 ? 24 : 10;
                String current = markerInputFocused == 0 ? markerNameInput : markerInputFocused == 1 ? markerXInput : markerInputFocused == 2 ? markerYInput : markerZInput;
                if (current.length() < maxLen) {
                    for (int i = 0; i < s.length(); i++) {
                        char c = s.charAt(i);
                        boolean ok = markerInputFocused == 0 ? (Character.isLetterOrDigit(c) || c == '_' || c == ' ') : (Character.isDigit(c) || c == '-' || c == '.');
                        if (ok) {
                            if (markerInputFocused == 0) markerNameInput += c;
                            else if (markerInputFocused == 1) markerXInput += c;
                            else if (markerInputFocused == 2) markerYInput += c;
                            else markerZInput += c;
                        }
                    }
                    GuiSounds.type();
                }
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseScrolled(double mouseXRaw, double mouseYRaw, double horizontalAmount, double verticalAmount) {
        float mouseX = (float) (mouseXRaw / scaleFix);
        float mouseY = (float) (mouseYRaw / scaleFix);
        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) { return editor.mouseScrolled(mouseX, mouseY, verticalAmount); }

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        float w = 420 * S;
        float h = 280 * S;
        float x = screenWidth / 2F - w / 2;
        float y = screenHeight / 2F - h / 2;

        if (MathUtil.isHovered(mouseX, mouseY, x + 6 * S + 36 * S, y + 6 * S + 26 * S, 140 * S, h - 12 * S - 26 * S)) {
            if (isSpecialCategory()) return true;
            float before = scrollTarget;
            scrollTarget = MathUtil.clamp((float) (scrollTarget - verticalAmount * 25 * S), 0, maxScroll);
            if (scrollTarget != before) GuiSounds.scroll(); return true;
        }

        if (MathUtil.isHovered(mouseX, mouseY, x + 6 * S + 36 * S, y + 6 * S + 26 * S, w - (6 * S + 36 * S + 6 * S), h - 12 * S - 26 * S)) {
            if (isSpecialCategory()) {
                if (isCosmeticsCategory()) {
                    float before = cosmeticsScrollTarget;
                    cosmeticsScrollTarget = MathUtil.clamp((float) (cosmeticsScrollTarget - verticalAmount * 25 * S), 0, cosmeticsMaxScroll);
                    if (cosmeticsScrollTarget != before) GuiSounds.scroll();
                } else if (isConfigCategory()) {
                    float before = configScrollTarget;
                    configScrollTarget = MathUtil.clamp((float) (configScrollTarget - verticalAmount * 25 * S), 0, Float.MAX_VALUE);
                    if (configScrollTarget != before) GuiSounds.scroll();
                } else {
                    float before = markerScrollTarget;
                    markerScrollTarget = MathUtil.clamp((float) (markerScrollTarget - verticalAmount * 25 * S), 0, Float.MAX_VALUE);
                    if (markerScrollTarget != before) GuiSounds.scroll();
                }
                return true;
            }
            float before = settingScrollTarget;
            settingScrollTarget = MathUtil.clamp((float) (settingScrollTarget - verticalAmount * 25 * S), 0, settingMaxScroll);
            if (settingScrollTarget != before) GuiSounds.scroll(); return true;
        }

        return super.mouseScrolled(mouseXRaw, mouseYRaw, horizontalAmount, verticalAmount);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() {
        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) {
            editor.saveAndExit();
            if (returnToGlassAfterEditor) {
                returnToGlassAfterEditor = false;
                close();
                mc.setScreen(ru.white.ui.lv.LvGui.INSTANCE);
            }
            return false;
        }
        if (!exit) {
ClickGui clickGui = Client.get().moduleManager().get(ClickGui.class);
        use3DAnim = clickGui != null && clickGui.closeAnimation.is("3D");
        if (use3DAnim) {
            GuiCloseAnimation3D.start();
            GuiSounds.close();
            return false;
        }
        if (clickGui != null && clickGui.closeAnimation.is("Shatter")) {
            if (GuiCloseAnimationShatter.start()) {
                GuiSounds.close();
                GuiMusicPlayer.stop();
                return false;
            }
        }
            glomalAnim.run(0, 0.3F, Easings.SINE_IN);
            exit = true;
            GuiSounds.close();
        }
        return false;
    }
}

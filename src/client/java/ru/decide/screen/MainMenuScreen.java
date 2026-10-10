package ru.decide.screen;

import ru.decide.utils.animation.Animation;
import ru.decide.utils.animation.Easings;
import ru.decide.utils.annotation.IMinecraft;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.math.MathUtil;
import ru.decide.utils.render.*;
import ru.decide.utils.render.font.Font;
import ru.decide.utils.render.font.Fonts;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainMenuScreen extends Screen implements IMinecraft {

    public MainMenuScreen() {
        super(Text.literal("MainMenuScreen"));
    }

    public Animation alpha = new Animation();
    private final List<MenuButton> buttons = new ArrayList<>();
    private MenuButton multiplayerButton;
    private MenuButton singleplayerButton;
    private MenuButton settingsButton;
    private MenuButton accountsButton;
    private MenuButton modsButton;
    private MenuButton exitButton;

    boolean exit = false;
    private float scaleFix = 1F;
    double lastMouseX;
    double lastMouseY;
    private final MainMenuShaderRenderer menuShader = new MainMenuShaderRenderer();
    private boolean exitSlideDragging = false;
    private float exitSlideProgress = 0F;
    private float exitSlideTarget = 0F;
    private boolean exitTriggered = false;
    private float exitFade = 1F;
    private float exitFadeTarget = 1F;

    // язык: маленькая кнопка-переключатель РУС/АНГ в углу экрана
    private float langBtnX, langBtnY, langBtnW, langBtnH;
    private final Animation langHover = new Animation();

    @Override
    protected void init() {
        exit = false;
        alpha.set(0);
        alpha.run(1, 0.5F, Easings.BACK_OUT);

        // применяем сохранённый активный аккаунт при первом показе меню
        ru.decide.alt.AltManager.get().bootstrap();

        buildButtons();
    }

    private void buildButtons() {
        buttons.clear();

        singleplayerButton = new MenuButton("Одиночная игра", "Singleplayer",
                "Играть в кампанию и оффлайн-миры", "Play the campaign and offline worlds",
                () -> mc.setScreen(new SelectWorldScreen(this)));
        multiplayerButton = new MenuButton("Сетевая игра", "Multiplayer",
                "Подключиться к мультиплеер-серверам", "Join and play on multiplayer servers",
                () -> mc.setScreen(new MultiplayerScreen(this)));

        buttons.add(multiplayerButton);
        buttons.add(singleplayerButton);

        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            modsButton = new MenuButton("Моды", "Mods",
                    "Управление установленными модификациями", "Manage your installed modifications", () -> {
                try {
                    Class<?> modMenuClass = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
                    Screen modsScreen = (Screen) modMenuClass.getConstructor(Screen.class).newInstance(this);
                    mc.setScreen(modsScreen);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            buttons.add(modsButton);
        } else {
            modsButton = null;
        }

        accountsButton = new MenuButton("Аккаунты", "Accounts",
                "Переключение и управление аккаунтами", "Switch and manage your accounts",
                () -> mc.setScreen(new AltManagerScreen(this)));
        settingsButton = new MenuButton("Настройки", "Settings",
                "Настройки игры и видео", "Adjust game and video options",
                () -> mc.setScreen(new OptionsScreen(this, mc.options)));
        exitButton = new MenuButton("Выход", "Exit",
                "Закрыть игру и выйти на рабочий стол", "Close the game and return to desktop",
                true, () -> mc.scheduleStop());

        buttons.add(settingsButton);
        buttons.add(accountsButton);
        buttons.add(exitButton);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        closeCheck();
        mouseX = (int) (mouseX / scaleFix);
        mouseY = (int) (mouseY / scaleFix);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        alpha.update();

        float slideSpeed = 0.12F;
        exitSlideProgress += (exitSlideTarget - exitSlideProgress) * slideSpeed;
        if (exitSlideTarget == 1F && exitSlideProgress > 0.99F) {
            exitSlideProgress = 1F;
        }

        if (exitSlideProgress >= 1F && !exitTriggered) {
            exitTriggered = true;
            exitFadeTarget = 0F;
        }

        exitFade += (exitFadeTarget - exitFade) * 0.08F;
        if (exitTriggered && exitFade < 0.02F) {
            exitFade = 0F;
            mc.scheduleStop();
        }

        renderOverlay(context);
    }

    public void renderOverlay(DrawContext context) {
        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        scaleFix = targetScale / currentScale;

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        if (context != null) context.getMatrices().pushMatrix();
        Render2D.beginOverlay();

        float alphaVal = this.alpha.get() * exitFade;


        float parallaxStrength = 0.07F; // Сила смещения
        float offsetX = (screenWidth / 2F - (float) lastMouseX) * parallaxStrength;
        float offsetY = (screenHeight / 2F - (float) lastMouseY) * parallaxStrength;


        float bgScale = 1.1F;
        float bgW = screenWidth * bgScale;
        float bgH = screenHeight * bgScale;
        float bgX = (screenWidth - bgW) / 2F + offsetX;
        float bgY = (screenHeight - bgH) / 2F + offsetY;

        RenderUtil.Images.texture(Identifier.of("decide","textures/frame/menu.png"), bgX, bgY, bgW, bgH, ColorUtil.getColor(255, alphaVal));

        RenderUtil.Render2D.rect(0, 0, screenWidth, screenHeight, ColorUtil.replAlpha(ColorUtil.getColor(0), 0.20F * alphaVal));

        ScreenBlur.capture(2);

        RenderUtil.Images.texture(Identifier.of("decide", "textures/frame/logo.png"), screenWidth / 2F - 24, screenHeight * 0.30F + 30 - 30 * alphaVal - 24, 48, 48, ColorUtil.getColor(255, alphaVal));

        Fonts.sf_regular.drawCentered("DecideVisuals", screenWidth / 2,
                screenHeight * 0.34F + 30 - 30 * alphaVal, 12, ColorUtil.getColor(255, alphaVal));

        for (MenuButton b : buttons) b.update(lastMouseX, lastMouseY);
        drawMenuButtons(screenWidth, screenHeight, alphaVal);


        Render2D.endOverlay();
        if (context != null) context.getMatrices().popMatrix();
    }

    private void drawMenuButtons(int screenWidth, int screenHeight, float alphaVal) {
        updateExitSlideProgress();

        float gap = 4;
        float panelWidth = Math.min(220, screenWidth - 36F);
        float cardWidth = (panelWidth - gap) / 2F;
        float cardHeight = 25;
        float totalCardsWidth = cardWidth * 2F + gap;

        float smallGap = 4;
        int smallCount = modsButton == null ? 2 : 3;
        float smallWidth = (totalCardsWidth - smallGap * (smallCount - 1)) / smallCount;
        float smallHeight = 25;

        float exitWidth = Math.min(150F, totalCardsWidth * 0.65F);
        float exitHeight = 25;

        // --- ВЫЧИСЛЕНИЕ ВЫСОТЫ БЛОКА ДЛЯ ОТЦЕНТРОВКИ ---
        float totalBlockHeight = cardHeight + gap + smallHeight + gap + exitHeight;

        float startX = screenWidth / 2F - totalCardsWidth / 2F;
        float startY = screenHeight / 2F - totalBlockHeight / 2F;
        float appearY = (1F - alphaVal) * 34F;

        float currentY = startY + appearY;

        // 1 Ряд: Основные кнопки
        multiplayerButton.drawHero(startX, currentY, cardWidth, cardHeight, alphaVal, 0);
        singleplayerButton.drawHero(startX + cardWidth + gap, currentY, cardWidth, cardHeight, alphaVal, 1);

        currentY += cardHeight + gap;

        // 2 Ряд: Мелкие кнопки
        float smallX = startX;
        settingsButton.drawCompact(smallX, currentY, smallWidth, smallHeight, alphaVal, "l");
        smallX += smallWidth + smallGap;
        accountsButton.drawCompact(smallX, currentY, smallWidth, smallHeight, alphaVal, "m");
        if (modsButton != null) {
            smallX += smallWidth + smallGap;
            modsButton.drawCompact(smallX, currentY, smallWidth, smallHeight, alphaVal, "n");
        }

        currentY += smallHeight + gap;

        // 3 Ряд: Кнопка выхода
        float exitX = screenWidth / 2F - exitWidth / 2F;
        exitButton.drawExit(exitX, currentY, exitWidth, exitHeight, alphaVal, exitSlideProgress, exitSlideDragging);
    }

    private void updateExitSlideProgress() {
        if (!exitSlideDragging || exitButton == null) return;

        float trackX = exitButton.getLastX() + 5F;
        float trackW = Math.max(1F, exitButton.getLastW() - 34F);
        exitSlideProgress = Math.max(0F, Math.min(1F, ((float) lastMouseX - trackX) / trackW));
    }

    private void drawLanguageButton(int screenWidth, float alphaVal) {
        float w = 25, h = 15;
        float bx = screenWidth - w - 12, by = 12;
        langBtnX = bx; langBtnY = by; langBtnW = w; langBtnH = h;

        boolean hov = ru.decide.utils.math.MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, bx, by, w, h);
        langHover.update();
        langHover.run(hov ? 1 : 0, 0.18F, Easings.QUAD_OUT);
        float hp = langHover.get();

        int accent = ColorUtil.getColor(125, 145, 244);
        RenderUtil.Blur.blur(bx, by, w, h, 1, 5, ColorUtil.getColor(20, alphaVal * (0.3F + 0.1F * hp)));

        int textColor = ColorUtil.replAlpha(
                ColorUtil.interpolateColor(ColorUtil.getColor(170, 170, 170, 1F), accent, hp), alphaVal);
        Fonts.sf_regular.drawCentered(ru.decide.lang.Lang.tag(), bx + w / 2F, by + h / 2F - 3.8F, 6, textColor);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (exitTriggered) return true;
        float mouseX = (float) (click.x() / scaleFix);
        float mouseY = (float) (click.y() / scaleFix);
        int button = click.button();

        if (button == 0) {
            if (ru.decide.utils.math.MathUtil.isHovered(mouseX, mouseY, langBtnX, langBtnY, langBtnW, langBtnH)) {
                ru.decide.lang.Lang.toggle();
                return true;
            }
            for (MenuButton btn : buttons) {
                if (btn.isHovered()) {
                    if (btn == exitButton) {
                        exitSlideDragging = true;
                        updateExitSlideProgress();
                        return true;
                    }
                    btn.onClick();
                    return true;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (exitSlideDragging) {
            lastMouseX = click.x() / scaleFix;
            lastMouseY = click.y() / scaleFix;
            updateExitSlideProgress();
            return true;
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (exitSlideDragging) {
            lastMouseX = click.x() / scaleFix;
            lastMouseY = click.y() / scaleFix;
            updateExitSlideProgress();
            exitSlideDragging = false;

            if (exitSlideProgress >= 0.90F) {
                exitSlideTarget = 1F;
            } else {
                exitSlideTarget = 0F;
            }
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() {
        if (!exit) {
            exit = false;
        }
        return false;
    }

    private void closeCheck() {
    }

    private static class MenuButton {
        private final String textRu, textEn;
        private final String descRu, descEn;
        private final Runnable action;
        private final boolean danger;
        private final Animation hoverAnim = new Animation();
        private boolean hovered;

        private float lastX, lastY, lastW, lastH;

        public MenuButton(String textRu, String textEn, String descRu, String descEn, Runnable action) {
            this(textRu, textEn, descRu, descEn, false, action);
        }

        public MenuButton(String textRu, String textEn, String descRu, String descEn, boolean danger, Runnable action) {
            this.textRu = textRu;
            this.textEn = textEn;
            this.descRu = descRu;
            this.descEn = descEn;
            this.danger = danger;
            this.action = action;
            this.hoverAnim.set(0);
        }

        private String text() { return textRu; }
        private String description() { return ru.decide.lang.Lang.pick(descRu, descEn); }

        public float getWidth() {
            Font font = Fonts.sf_regular;
            return Math.max(font.getWidth(text(), 8), font.getWidth(description(), 6)) + 34;
        }

        public float getHoverValue() {
            return hoverAnim.get();
        }

        public void update(double mouseX, double mouseY) {
            hovered = MathUtil.isHovered((float) mouseX, (float) mouseY, lastX, lastY, lastW, lastH);
            hoverAnim.update();
            hoverAnim.run(hovered ? 1 : 0, 0.18F, Easings.QUAD_OUT);
        }

        private void rememberBounds(float x, float y, float width, float height) {
            lastX = x;
            lastY = y;
            lastW = width;
            lastH = height;
        }

        public float getLastX() {
            return lastX;
        }

        public float getLastW() {
            return lastW;
        }

        public void drawHero(float x, float y, float width, float height, float globalAlpha, int variant) {
            rememberBounds(x, y, width, height);

            float hp = hoverAnim.get();
            float headerHeight = height;
            int accent = ColorUtil.overCol(ColorUtil.getColor(125, 125, 125), ColorUtil.client(), hp);

            RenderUtil.Blur.blur(x, y, width, headerHeight, 1, 6,
                    ColorUtil.getColor(12, globalAlpha * 0.35F));

            int textColor = ColorUtil.replAlpha(
                    ColorUtil.interpolateColor(ColorUtil.getColor(255, 1F), ColorUtil.getColor(255, 1F), hp),
                    globalAlpha * (0.3F + hp * 0.7F));

            Fonts.sf_regular.draw(text(), x + 12F, y + 7.8F, 7, textColor);
            Fonts.icon.drawCentered(variant == 0 ? "j" : "k", x + width - 15, y + 9.25F, 6,
                    ColorUtil.replAlpha(accent, globalAlpha * (0.3F + hp * 0.7F)));
        }

        public void drawCompact(float x, float y, float width, float height, float globalAlpha, String icon) {
            rememberBounds(x, y, width, height);

            float hp = hoverAnim.get();
            float radius = 6F;
            int accent = ColorUtil.overCol(ColorUtil.getColor(255), ColorUtil.client(), hp);

            RenderUtil.Blur.blur(x, y, width, height, 1, radius, ColorUtil.getColor(12, globalAlpha * 0.35F));

            int textColor = ColorUtil.replAlpha(
                    ColorUtil.getColor(255),
                    globalAlpha * (0.3F + hp * 0.7F));

            Fonts.sf_regular.draw(text(), x + 12F, y + 7.8F, 7, textColor);
            Fonts.icon.drawCentered(icon, x + width - 15, y + 9.25F, 6,
                    ColorUtil.replAlpha(accent, globalAlpha * (0.2F + hp * 0.8F)));
        }

        public void drawExit(float x, float y, float width, float height, float globalAlpha, float slideProgress, boolean dragging) {
            rememberBounds(x, y, width, height);

            float hp = hoverAnim.get();
            float radius = 6F;
            int accent = ColorUtil.getColor(255, dragging ? 145 : 255, dragging ? 145 : 255);
            int fill = ColorUtil.getColor(20, globalAlpha * 0.35F);
            float progress = Math.max(0F, Math.min(1F, slideProgress));
            float knobSize = height - 8F;
            float knobX = x + 5F + (width - knobSize - 10F) * progress;

            RenderUtil.Blur.blur(x, y, width, height, 1, radius, fill);

            RenderUtil.Render2D.rect(knobX, y + 4F, knobSize, knobSize,
                    ColorUtil.getColor(255, 255, 255, globalAlpha * (0.02F + hp * 0.03F + (dragging ? 0.02F : 0F))), 4);
            Fonts.icon.drawCentered("i", knobX + knobSize / 2F, y + height / 2F - 3, 7,
                    ColorUtil.replAlpha(accent, globalAlpha * (0.2F + hp * 0.2F + progress * 0.2f)));
            Fonts.sf_regular.drawCentered(ru.decide.lang.Lang.pick("Сдвиньте, чтобы выйти", "Сдвиньте, чтобы выйти"), x + width / 2F + 8F,
                    y + height / 2F - 4.4F, 7,
                    ColorUtil.getColor(255, globalAlpha * (0.1F + hp * 0.2F - progress * 0.16F)));
        }

        public void draw(float x, float y, float width, float height, float globalAlpha) {
            float hp = hoverAnim.get();
            float radius = 6;
            int accent = ColorUtil.getColor(125, 145, 244);

            rememberBounds(x, y, width, height);

            int fill = danger ? ColorUtil.overCol(ColorUtil.getColor(12,
                    globalAlpha * (0.3F - 0.2F * hp)), ColorUtil.getColor(
                    45, 0, 0, globalAlpha * (0.3F)), hp) :
                    ColorUtil.getColor(12, globalAlpha * (0.3F - 0.2F * hp));
            RenderUtil.Blur.blur(x, y, width, height, 1, radius, fill);

            Font font = Fonts.sf_regular;

            int titleTarget = danger ? ColorUtil.getColor(255, 125, 125) : accent;
            int titleColor = ColorUtil.replAlpha(
                    ColorUtil.interpolateColor(ColorUtil.getColor(160, 160, 160, 1F), titleTarget, hp), globalAlpha);

            String text = text();
            float titleX = x + width / 2F - font.getWidth(text, 8) / 2F;
            font.draw(text, titleX, y + 12, 8, titleColor);
        }

        public boolean isHovered() {
            return hovered;
        }

        public void onClick() {
            if (action != null) {
                action.run();
            }
        }
    }

}
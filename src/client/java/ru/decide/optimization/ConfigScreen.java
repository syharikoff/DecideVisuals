package ru.decide.optimization;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private static final int MIN_HZ = 30;
    private static final int HZ_RANGE = 210;
    private static final double UNLIMITED_THRESHOLD = 0.999;

    private final Screen parent;
    private final ModConfig config;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Achrone Optimization"));
        this.parent = parent;
        this.config = ModConfig.getInstance();
    }

    @Override
    protected void init() {
        this.clearChildren();
        int centerX = this.width / 2;
        int startY = this.height / 4;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 25;

        Text initialHzText = this.config.isUnlimitedFps()
                ? Text.literal("Монитор: Unlimited")
                : Text.literal("Монитор: " + this.config.targetMonitorHz + " Hz");
        double initialHzValue = this.config.isUnlimitedFps()
                ? 1.0
                : ((double) this.config.targetMonitorHz - MIN_HZ) / HZ_RANGE;
        this.hzSlider = new SliderWidget(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight, initialHzText, initialHzValue) {
            @Override
            protected void updateMessage() {
                if (this.value >= UNLIMITED_THRESHOLD) {
                    this.setMessage(Text.literal("Монитор: Unlimited"));
                } else {
                    int hz = (int) (MIN_HZ + this.value * HZ_RANGE);
                    this.setMessage(Text.literal("Монитор: " + hz + " Hz"));
                }
            }

            @Override
            protected void applyValue() {
                if (this.value >= UNLIMITED_THRESHOLD) {
                    ConfigScreen.this.config.setUnlimitedFps(true);
                } else {
                    int hz = (int) (MIN_HZ + this.value * HZ_RANGE);
                    ConfigScreen.this.config.setTargetMonitorHz(hz);
                }
                ConfigScreen.this.applyConfigToLimiter();
            }
        };
        this.addDrawableChild(this.hzSlider);

        this.enableButton = CyclingButtonWidget.onOffBuilder(Text.literal("Включён"), Text.literal("Выключен"), this.config.enableRenderLimit)
                .build(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight, Text.literal("Ограничение рендера"), (button, enabled) -> {
                    this.config.setEnableRenderLimit(enabled);
                    this.applyConfigToLimiter();
                });
        this.addDrawableChild(this.enableButton);

        this.autoDetectButton = CyclingButtonWidget.onOffBuilder(Text.literal("Авто: Вкл"), Text.literal("Авто: Выкл"), this.config.autoDetectMonitorHz)
                .build(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight, Text.literal("Автоопределение Hz"), (button, autoDetect) -> {
                    this.config.setAutoDetectMonitorHz(autoDetect);
                    if (autoDetect) {
                        int detectedHz = RenderLimiter.detectMonitorHz();
                        this.config.setTargetMonitorHz(detectedHz);
                        this.applyConfigToLimiter();
                        this.init();
                    }
                });
        this.addDrawableChild(this.autoDetectButton);

        this.minFpsSlider = new SliderWidget(centerX - buttonWidth / 2, startY + spacing * 3, buttonWidth, buttonHeight,
                Text.literal("Мин. FPS: " + this.config.minFpsLimit), ((double) this.config.minFpsLimit - 15.0) / 105.0) {
            @Override
            protected void updateMessage() {
                int minFps = (int) (15.0 + this.value * 105.0);
                this.setMessage(Text.literal("Мин. FPS: " + minFps));
            }

            @Override
            protected void applyValue() {
                int minFps = (int) (15.0 + this.value * 105.0);
                ConfigScreen.this.config.setMinFpsLimit(minFps);
                ConfigScreen.this.applyConfigToLimiter();
            }
        };
        this.addDrawableChild(this.minFpsSlider);

        this.fpsIndicatorButton = CyclingButtonWidget.onOffBuilder(Text.literal("Показ"), Text.literal("Скрыть"), this.config.showFpsIndicator)
                .build(centerX - buttonWidth / 2, startY + spacing * 4, buttonWidth, buttonHeight, Text.literal("Индикатор FPS"), (button, show) -> this.config.setShowFpsIndicator(show));
        this.addDrawableChild(this.fpsIndicatorButton);

        this.lowEndModeButton = CyclingButtonWidget.onOffBuilder(Text.literal("Вкл"), Text.literal("Выкл"), this.config.lowEndMode)
                .build(centerX - buttonWidth / 2, startY + spacing * 5, buttonWidth, buttonHeight, Text.literal("Low-End режим"), (button, lowEnd) -> {
                    this.config.setLowEndMode(lowEnd);
                    this.applyConfigToLimiter();
                });
        this.addDrawableChild(this.lowEndModeButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Сбросить настройки"), button -> {
            this.config.resetToDefaults();
            this.applyConfigToLimiter();
            this.init();
        }).dimensions(centerX - buttonWidth / 2, startY + spacing * 6, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), button -> this.close())
                .dimensions(centerX - buttonWidth / 2, startY + spacing * 7, buttonWidth, buttonHeight).build());
    }

    private SliderWidget hzSlider;
    private CyclingButtonWidget<Boolean> enableButton;
    private CyclingButtonWidget<Boolean> autoDetectButton;
    private CyclingButtonWidget<Boolean> fpsIndicatorButton;
    private SliderWidget minFpsSlider;
    private CyclingButtonWidget<Boolean> lowEndModeButton;

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        String configuredLimit = this.config.isUnlimitedFps() ? "Unlimited" : this.config.targetMonitorHz + " Hz";
        String info = String.format("Current: %s, %s", configuredLimit, this.config.enableRenderLimit ? "Enabled" : "Disabled");
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(info), this.width / 2, this.height - 30, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    private void applyConfigToLimiter() {
        RenderManager.getInstance().updateSettings(this.config.getEffectiveHz(), this.config.enableRenderLimit);
    }
}
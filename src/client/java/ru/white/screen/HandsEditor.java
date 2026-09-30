package ru.white.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import ru.white.Client;
import ru.white.lang.Lang;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.impl.render.GlassHands;
import ru.white.module.impl.render.Hands;
import ru.white.module.impl.render.SwingAnimation;
import ru.white.screen.editor.EditorButton;
import ru.white.screen.editor.EditorWidgets;
import ru.white.screen.editor.OverlayEditor;
import ru.white.screen.editor.Rect;
import ru.white.screen.editor.SettingsPanel;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.render.GlassHandsRenderer;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Fonts;

import static ru.white.screen.editor.EditorTheme.PANEL_W;
import static ru.white.screen.editor.EditorTheme.ROW_H;

/**
 * Полноэкранный редактор положения рук. Рисуется внутри Menu, полностью перехватывает ввод
 * и оформлен тем же языком, что и ClickGUI: блюр-панели, строки с ховером, чипсы режимов.
 */
public final class HandsEditor implements OverlayEditor {

    private static final HandsEditor INSTANCE = new HandsEditor();

    private static final long BOUNDS_TTL_MS = 250L;
    private static final long AUTO_SWING_INTERVAL_MS = 500L;

    private static final ItemStack RIGHT_PREVIEW_STACK = new ItemStack(Items.NETHERITE_SWORD);
    private static final ItemStack LEFT_PREVIEW_STACK = new ItemStack(Items.TOTEM_OF_UNDYING);

    private static final float[][] HAND_SAMPLE_POINTS = {
            {-0.55F, -0.55F, -0.35F}, {-0.55F, -0.55F, 0.35F},
            {-0.55F, 0.55F, -0.35F}, {-0.55F, 0.55F, 0.35F},
            {0.55F, -0.55F, -0.35F}, {0.55F, -0.55F, 0.35F},
            {0.55F, 0.55F, -0.35F}, {0.55F, 0.55F, 0.35F}
    };

    private final HandBounds leftBounds = new HandBounds();
    private final HandBounds rightBounds = new HandBounds();

    private final Animation visibility = new Animation();
    private final Animation leftHover = new Animation();
    private final Animation rightHover = new Animation();

    private final EditorButton saveButton = new EditorButton("Выйти и сохранить", true);
    private final EditorButton resetAllButton = new EditorButton("Сбросить обе руки", false);
    private final EditorButton resetLeftButton = new EditorButton("Сбросить левую", false);
    private final EditorButton resetRightButton = new EditorButton("Сбросить правую", false);

    private boolean active;
    private Arm hoveredArm;
    private Arm draggingArm;
    private int draggingButton = -1;
    private boolean autoSwingPreview;
    private long nextAutoSwingAt;

    private float dragStartMouseX;
    private float dragStartMouseY;
    private float dragStartValueX;
    private float dragStartValueY;

    private float screenWidth;
    private float screenHeight;

    private Rect autoSwingBounds = Rect.EMPTY;

    private final EditorWidgets widgets = new EditorWidgets();
    private final SettingsPanel swingPanel = new SettingsPanel();
    private final SettingsPanel glassPanel = new SettingsPanel();

    private HandsEditor() {
    }

    public static HandsEditor getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public ItemStack previewStackFor(Arm arm) {
        return arm == Arm.RIGHT ? RIGHT_PREVIEW_STACK : LEFT_PREVIEW_STACK;
    }

    public void open() {
        if (active) return;

        active = true;
        hoveredArm = null;
        draggingArm = null;
        draggingButton = -1;
        autoSwingPreview = false;
        nextAutoSwingAt = 0L;
        swingPanel.reset();
        glassPanel.reset();
        closePickers();
        leftBounds.invalidate();
        rightBounds.invalidate();
        visibility.set(0);
        visibility.run(1, 0.24F, Easings.EXPO_OUT);

        Hands hands = Hands.get();
        if (hands != null) hands.setEditorActive(true);
    }

    @Override
    public void saveAndExit() {
        if (!active) return;
        if (Client.get() != null && Client.get().configManager() != null) {
            Client.get().configManager().autoSave();
        }
        deactivate();
    }

    @Override
    public void closeFromMenuRemoval() {
        if (!active) return;
        if (Client.get() != null && Client.get().configManager() != null) {
            Client.get().configManager().autoSave();
        }
        deactivate();
    }

    private void deactivate() {
        active = false;
        hoveredArm = null;
        draggingArm = null;
        draggingButton = -1;
        swingPanel.releaseDrag();
        glassPanel.releaseDrag();
        autoSwingPreview = false;
        nextAutoSwingAt = 0L;
        closePickers();

        Hands hands = Hands.get();
        if (hands != null) hands.setEditorActive(false);

        restoreGlassHandsRenderer();
    }

    private void closePickers() {
        GlassHands glass = GlassHands.getInstance();
        if (glass != null) glass.tintColor.pickerOpen = false;

        swingPanel.closePickers();
        glassPanel.closePickers();
    }

    // ───────────────────────────── рендер ─────────────────────────────

    @Override
    public void render(float width, float height, float mouseX, float mouseY, float parentAlpha) {
        if (!active) return;

        screenWidth = width;
        screenHeight = height;
        widgets.setMouse(mouseX, mouseY);

        layoutButtons(width, height);
        swingPanel.layout(SwingAnimation.get(), "Swing Animation", width - PANEL_W - 16F, 14F, width, height, widgets);
        glassPanel.layout(GlassHands.getInstance(), "GlassHands", 16F, 14F, width, height, widgets);
        updateAutoSwingPreview();

        visibility.update();
        float alpha = visibility.get() * parentAlpha;
        hoveredArm = draggingArm != null ? draggingArm : findHoveredArm(mouseX, mouseY);

        updateHover(leftHover, hoveredArm == Arm.LEFT);
        updateHover(rightHover, hoveredArm == Arm.RIGHT);
        saveButton.update(mouseX, mouseY);
        resetAllButton.update(mouseX, mouseY);
        resetLeftButton.update(mouseX, mouseY);
        resetRightButton.update(mouseX, mouseY);

        RenderUtil.Render2D.rect(0, 0, width, height, ColorUtil.getColor(0, 0.30F * alpha));
        // затемнение ложится поверх рук — возвращаем их пиксели по маске GlassHands
        Client.get().render2D().flushAll();
        GlassHandsRenderer.getInstance().restoreHandsAfterOverlay();

        drawHeader(width, alpha);
        widgets.drawButton(resetLeftButton, alpha);
        widgets.drawButton(resetRightButton, alpha);
        widgets.drawButton(resetAllButton, alpha);
        widgets.drawButton(saveButton, alpha);
        widgets.drawToggleRow(autoSwingBounds, Lang.tr("Авто-взмах правой руки"), autoSwingPreview,
                "editor:autoswing", alpha);

        swingPanel.draw(widgets, alpha);
        glassPanel.draw(widgets, alpha);
    }

    private void drawHeader(float width, float alpha) {
        Client.get().render2D().flushAll();

        Fonts.sf_regular.drawCentered(Lang.tr("Редактор рук"), width / 2F, 16F, 8F,
                ColorUtil.getColor(240, alpha));
        Fonts.sf_regular.drawCentered(Lang.tr("Наведите курсор на руку"), width / 2F, 29F, 6F,
                ColorUtil.getColor(255, alpha * 0.45F));
        Fonts.sf_regular.drawCentered(
                Lang.tr("ЛКМ / ПКМ + движение — переместить") + "   •   " + Lang.tr("Колесо — изменить размер"),
                width / 2F, 39F, 5.5F, ColorUtil.getColor(255, alpha * 0.3F));
    }

    // ───────────────────────────── раскладка ─────────────────────────────

    private void layoutButtons(float width, float height) {
        float buttonH = 18F;
        float sideW = 100F;
        float saveW = 120F;
        float resetAllW = 112F;
        float bottomY = height - 30F;

        saveButton.set(width / 2F - saveW / 2F, bottomY, saveW, buttonH);
        resetAllButton.set(width / 2F - resetAllW / 2F, bottomY - 25F, resetAllW, buttonH);
        resetLeftButton.set(20F, bottomY, sideW, buttonH);
        resetRightButton.set(width - 20F - sideW, bottomY, sideW, buttonH);
        autoSwingBounds = new Rect(width / 2F - 80F, bottomY - 52F, 160F, ROW_H);
    }

    // ───────────────────────────── ввод ─────────────────────────────

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!active) return false;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (glassPanel.handleClick(mouseX, mouseY, button)
                    || swingPanel.handleClick(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (autoSwingBounds.contains(mouseX, mouseY)) {
                autoSwingPreview = !autoSwingPreview;
                nextAutoSwingAt = 0L;
                return true;
            }

            Hands hands = Hands.get();
            if (saveButton.contains(mouseX, mouseY)) {
                saveButton.press();
                saveAndExit();
            } else if (hands != null && resetAllButton.contains(mouseX, mouseY)) {
                resetAllButton.press();
                hands.resetAll();
            } else if (hands != null && resetLeftButton.contains(mouseX, mouseY)) {
                resetLeftButton.press();
                hands.resetLeft();
            } else if (hands != null && resetRightButton.contains(mouseX, mouseY)) {
                resetRightButton.press();
                hands.resetRight();
            } else {
                Arm arm = findDragArm(mouseX, mouseY);
                if (arm != null) beginDrag(arm, mouseX, mouseY, button);
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (isOverControls(mouseX, mouseY)) return true;
            Arm arm = findDragArm(mouseX, mouseY);
            if (arm != null) beginDrag(arm, mouseX, mouseY, button);
            return true;
        }

        return true;
    }

    @Override
    public boolean mouseDragged(float deltaX, float deltaY) {
        return active;
    }

    @Override
    public boolean mouseReleased(int button) {
        if (!active) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            swingPanel.releaseDrag();
            glassPanel.releaseDrag();
        }
        if (button == draggingButton) {
            draggingArm = null;
            draggingButton = -1;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(float mouseX, float mouseY, double verticalAmount) {
        if (!active) return false;

        // над панелями колесо крутит их содержимое, а не масштаб руки
        if (glassPanel.handleScroll(mouseX, mouseY, verticalAmount)
                || swingPanel.handleScroll(mouseX, mouseY, verticalAmount)) {
            return true;
        }

        if (autoSwingBounds.contains(mouseX, mouseY)) return true;

        Arm arm = findHoveredArm(mouseX, mouseY);
        Hands hands = Hands.get();
        if (arm == null || hands == null || verticalAmount == 0) return true;

        SliderSetting scale = arm == Arm.RIGHT ? hands.rScale : hands.lScale;
        float direction = verticalAmount > 0 ? 1.0F : -1.0F;
        float oldScale = scale.getValue();
        float newScale = clampAndRound(oldScale + direction * scale.increment, scale);
        if (Math.abs(newScale - oldScale) < 0.0001F) return true;

        Rect rendered = boundsFor(arm).freshRect();
        Rect anchor = rendered != null ? rendered : controlRect(arm);
        float ratio = newScale / oldScale;
        float shiftX = (1F - ratio) * (mouseX - anchor.centerX());
        float shiftY = (1F - ratio) * (mouseY - anchor.centerY());

        SliderSetting xSetting = arm == Arm.RIGHT ? hands.rx : hands.lx;
        SliderSetting ySetting = arm == Arm.RIGHT ? hands.ry : hands.ly;
        float pixelsPerUnit = dragPixelsPerUnit();
        xSetting.set(MathHelper.clamp(xSetting.getValue() + shiftX / pixelsPerUnit,
                xSetting.min, xSetting.max));
        ySetting.set(MathHelper.clamp(ySetting.getValue() - shiftY / pixelsPerUnit,
                ySetting.min, ySetting.max));
        scale.set(newScale);
        return true;
    }

    /**
     * Сырой ввод из MouseMixin. Тянуть руку можно любой кнопкой, но контролы всегда в приоритете.
     */
    @Override
    public void rawMouseButton(float mouseX, float mouseY, int button, boolean pressed) {
        if (!active) return;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        if (!pressed) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                swingPanel.releaseDrag();
                glassPanel.releaseDrag();
            }
            if (button == draggingButton) {
                draggingArm = null;
                draggingButton = -1;
            }
            return;
        }

        if (isOverControls(mouseX, mouseY)) return;

        Arm arm = findDragArm(mouseX, mouseY);
        if (arm != null) beginDrag(arm, mouseX, mouseY, button);
    }

    @Override
    public void rawMouseMoved(float mouseX, float mouseY) {
        if (glassPanel.rawMouseMoved(mouseX, mouseY)) return;
        if (swingPanel.rawMouseMoved(mouseX, mouseY)) return;
        applyDrag(mouseX, mouseY);
    }

    private void beginDrag(Arm arm, float mouseX, float mouseY, int button) {
        Hands hands = Hands.get();
        if (hands == null) return;

        draggingArm = arm;
        draggingButton = button;
        dragStartMouseX = mouseX;
        dragStartMouseY = mouseY;
        dragStartValueX = arm == Arm.RIGHT ? hands.rx.getValue() : hands.lx.getValue();
        dragStartValueY = arm == Arm.RIGHT ? hands.ry.getValue() : hands.ly.getValue();
    }

    private void applyDrag(float mouseX, float mouseY) {
        if (!active || draggingArm == null) return;
        Hands hands = Hands.get();
        if (hands == null) return;

        float sensitivity = 1F / dragPixelsPerUnit();
        float valueX = dragStartValueX + (mouseX - dragStartMouseX) * sensitivity;
        float valueY = dragStartValueY - (mouseY - dragStartMouseY) * sensitivity;
        SliderSetting xSetting = draggingArm == Arm.RIGHT ? hands.rx : hands.lx;
        SliderSetting ySetting = draggingArm == Arm.RIGHT ? hands.ry : hands.ly;
        // курсор не квантуем шагом 0.05 — плавность движения руки важнее круглых значений
        xSetting.set(MathHelper.clamp(valueX, xSetting.min, xSetting.max));
        ySetting.set(MathHelper.clamp(valueY, ySetting.min, ySetting.max));
    }

    private Arm findDragArm(float mouseX, float mouseY) {
        Arm arm = findHoveredArm(mouseX, mouseY);
        if (arm == null && mouseY >= screenHeight * 0.32F) {
            arm = mouseX < screenWidth / 2F ? Arm.LEFT : Arm.RIGHT;
        }
        return arm;
    }

    private float clampAndRound(float value, SliderSetting setting) {
        return MathHelper.clamp(MathUtil.round(value, setting.increment), setting.min, setting.max);
    }

    public boolean isAutoSwingPreview() {
        return active && autoSwingPreview;
    }

    private void updateAutoSwingPreview() {
        if (!autoSwingPreview) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.handSwinging) return;
        long now = System.currentTimeMillis();
        if (now < nextAutoSwingAt) return;

        Hand rightHand = client.player.getMainArm() == Arm.RIGHT ? Hand.MAIN_HAND : Hand.OFF_HAND;
        // двухаргументный метод LivingEntity шлёт только локальную анимацию, без пакета
        client.player.swingHand(rightHand, false);
        nextAutoSwingAt = now + AUTO_SWING_INTERVAL_MS;
    }

    private Arm findHoveredArm(float mouseX, float mouseY) {
        if (isOverControls(mouseX, mouseY) || screenWidth <= 0 || screenHeight <= 0) {
            return null;
        }

        Rect left = controlRect(Arm.LEFT);
        Rect right = controlRect(Arm.RIGHT);
        boolean overLeft = left.contains(mouseX, mouseY);
        boolean overRight = right.contains(mouseX, mouseY);

        if (overLeft && overRight) {
            float leftDistance = left.distanceSquaredToCenter(mouseX, mouseY);
            float rightDistance = right.distanceSquaredToCenter(mouseX, mouseY);
            return leftDistance <= rightDistance ? Arm.LEFT : Arm.RIGHT;
        }
        if (overLeft) return Arm.LEFT;
        if (overRight) return Arm.RIGHT;
        // большие невидимые зоны — чтобы рука хваталась даже когда анимация увела предмет
        if (mouseY >= screenHeight * 0.32F) {
            return mouseX < screenWidth / 2F ? Arm.LEFT : Arm.RIGHT;
        }
        return null;
    }

    /**
     * Стабильная прозрачная «ручка» руки. Она ходит по тем же X/Y и с той же чувствительностью,
     * что и перетаскивание, поэтому не зависит от проекции геометрии предмета.
     */
    private Rect controlRect(Arm arm) {
        Hands hands = Hands.get();
        float valueX = 0F;
        float valueY = 0F;
        float scale = 1F;
        if (hands != null) {
            valueX = arm == Arm.RIGHT ? hands.rx.getValue() : hands.lx.getValue();
            valueY = arm == Arm.RIGHT ? hands.ry.getValue() : hands.ly.getValue();
            scale = arm == Arm.RIGHT ? hands.rScale.getValue() : hands.lScale.getValue();
        }

        float pixelsPerUnit = dragPixelsPerUnit();
        float baseX = screenWidth * (arm == Arm.LEFT ? 0.16F : 0.84F);
        float baseY = screenHeight * 0.78F;
        float baseSize = MathHelper.clamp(Math.min(screenWidth, screenHeight) * 0.38F, 150F, 240F);
        float size = MathHelper.clamp(baseSize * (0.88F + scale * 0.12F), 145F, 275F);

        float centerX = baseX + valueX * pixelsPerUnit;
        float centerY = baseY - valueY * pixelsPerUnit;
        centerX = MathHelper.clamp(centerX, size / 2F + 8F, screenWidth - size / 2F - 8F);
        centerY = MathHelper.clamp(centerY, size / 2F + 52F, screenHeight - size / 2F - 54F);
        return new Rect(centerX - size / 2F, centerY - size / 2F, size, size);
    }

    private float dragPixelsPerUnit() {
        // проекция руки от первого лица зависит от вертикального FOV, поэтому масштаб берём от высоты
        return Math.max(1F, screenHeight / 1.05F);
    }

    private boolean isOverControls(float mouseX, float mouseY) {
        return saveButton.contains(mouseX, mouseY)
                || resetAllButton.contains(mouseX, mouseY)
                || resetLeftButton.contains(mouseX, mouseY)
                || resetRightButton.contains(mouseX, mouseY)
                || autoSwingBounds.contains(mouseX, mouseY)
                || swingPanel.bounds().contains(mouseX, mouseY)
                || glassPanel.bounds().contains(mouseX, mouseY);
    }

    private void updateHover(Animation animation, boolean hovered) {
        animation.update();
        animation.run(hovered ? 1F : 0F, 0.18F, Easings.QUAD_OUT, true);
    }

    // ───────────────────────────── проекция и захват ─────────────────────────────

    /**
     * Вызывается из HeldItemRendererMixin после применения всех трансформаций руки.
     */
    public void updateHandBounds(Arm arm, MatrixStack matrices) {
        if (!active || matrices == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.getWindow() == null) return;

        Matrix4f position = new Matrix4f(matrices.peek().getPositionMatrix());
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        // ванильные руки рисуются своей проекцией в 70°, независимо от мирового FOV
        Matrix4f projection = client.gameRenderer.getBasicProjectionMatrix(70F);
        float width = client.getWindow().getFramebufferWidth() / 2F;
        float height = client.getWindow().getFramebufferHeight() / 2F;

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        int projected = 0;

        Vector4f point = new Vector4f();
        for (float[] sample : HAND_SAMPLE_POINTS) {
            point.set(sample[0], sample[1], sample[2], 1F);
            point.mul(position);
            point.mul(modelView);
            point.mul(projection);
            if (point.w() <= 0.001F || !Float.isFinite(point.w())) continue;

            float ndcX = point.x() / point.w();
            float ndcY = point.y() / point.w();
            if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) continue;

            float sx = (ndcX * 0.5F + 0.5F) * width;
            float sy = (0.5F - ndcY * 0.5F) * height;
            minX = Math.min(minX, sx);
            minY = Math.min(minY, sy);
            maxX = Math.max(maxX, sx);
            maxY = Math.max(maxY, sy);
            projected++;
        }

        if (projected < 2) return;

        float centerX = (minX + maxX) / 2F;
        float centerY = (minY + maxY) / 2F;
        float boundsWidth = MathHelper.clamp(maxX - minX + 34F, 76F, 260F);
        float boundsHeight = MathHelper.clamp(maxY - minY + 42F, 90F, 280F);
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY)) return;

        boundsFor(arm).set(centerX, centerY, boundsWidth, boundsHeight);
    }

    public void captureBeforeHands() {
        if (!active) return;
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        renderer.setKeepHandsResult(true);

        GlassHands glassHands = GlassHands.getInstance();
        if (glassHands != null && glassHands.isEnabled()) {
            renderer.setEnabled(true);
            glassHands.applyRendererSettings();
        } else {
            configureEditorOutline(renderer);
        }
        renderer.captureSceneBeforeHands();
    }

    public void captureAfterHands() {
        if (!active) return;
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        renderer.captureSceneAfterHands();
        renderer.renderGlassEffect();
    }

    private void configureEditorOutline(GlassHandsRenderer renderer) {
        renderer.setEnabled(true);
        renderer.setBlurEnabled(false);
        renderer.setSaturation(1F);
        renderer.setReflect(false);
        renderer.setTintColor(0x00000000);
        renderer.setTintIntensity(0F);
        renderer.setIceIntensity(0F);
        renderer.setSmokeAmount(0F);
        renderer.setOutlineEnabled(true);
        renderer.setOutlineGlowStrength(hoveredArm == null ? 12F : 20F);
        renderer.setOutlineWidth(1);
        renderer.setOutlineGlowMode(2);
        renderer.setOutlineFillColor(0x00000000);
        renderer.setOutlineColor(ColorUtil.getClientColor1(1));
        renderer.setShimmerEnabled(true);
        renderer.setShimmerWidth(0.045F);
        renderer.setShimmerPeriodSec(2.8F);
    }

    private void restoreGlassHandsRenderer() {
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        renderer.setKeepHandsResult(false);

        GlassHands glassHands = GlassHands.getInstance();
        if (glassHands != null && glassHands.isEnabled()) {
            renderer.setEnabled(true);
            glassHands.applyRendererSettings();
        } else {
            renderer.setEnabled(false);
        }
    }

    private HandBounds boundsFor(Arm arm) {
        return arm == Arm.RIGHT ? rightBounds : leftBounds;
    }

    // ───────────────────────────── модель ─────────────────────────────

    private static final class HandBounds {
        private float centerX;
        private float centerY;
        private float width;
        private float height;
        private long updatedAt;

        void set(float centerX, float centerY, float width, float height) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.width = width;
            this.height = height;
            this.updatedAt = System.currentTimeMillis();
        }

        void invalidate() {
            updatedAt = 0L;
        }

        Rect freshRect() {
            boolean fresh = updatedAt > 0L && System.currentTimeMillis() - updatedAt <= BOUNDS_TTL_MS;
            return fresh
                    ? new Rect(centerX - width / 2F, centerY - height / 2F, width, height)
                    : null;
        }
    }
}

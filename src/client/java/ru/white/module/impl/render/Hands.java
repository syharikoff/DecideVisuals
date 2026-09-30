package ru.white.module.impl.render;

import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.module.api.settings.impl.DelimiterSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.screen.HandsEditor;
import ru.white.screen.Menu;
import ru.white.utils.other.GuiSounds;
import ru.white.utils.other.Instance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;

@ModuleInfo(
        name = "Hands",
        desc = "Положение и размер рук от первого лица",
        category = Category.VISUALS
)
public class Hands extends Module {

    public static Hands get() {
        return Instance.get(Hands.class);
    }

    private boolean editorActive;
    private boolean restoreSwingAnimation;
    private boolean restoreGlassHands;


    public ButtonSetting openEditor = new ButtonSetting(this, "Редактор рук", () -> {
        if (mc.currentScreen instanceof Menu menu) {
            menu.openHandsEditor();
        } else {
            GuiSounds.editor();
            Menu.returnToGlassAfterEditor = true;
            MinecraftClient.getInstance().setScreen(null);
            MinecraftClient.getInstance().send(() -> {
                Menu menu = new Menu();
                MinecraftClient.getInstance().setScreen(menu);
                MinecraftClient.getInstance().send(menu::openHandsEditor);
            });
        }
    });

    // ===== правая рука =====
    public DelimiterSetting rightTitle = new DelimiterSetting(this, "Правая рука");
    public SliderSetting rx = new SliderSetting(this, "Правая X", 0.0F, -2.0F, 2.0F, 0.05F);
    public SliderSetting ry = new SliderSetting(this, "Правая Y", 0.0F, -2.0F, 2.0F, 0.05F);
    public SliderSetting rz = new SliderSetting(this, "Правая Z", 0.0F, -2.0F, 2.0F, 0.05F);
    public SliderSetting rScale = new SliderSetting(this, "Правая размер", 1.0F, 0.1F, 3.0F, 0.05F);
    public ButtonSetting resetRightButton = new ButtonSetting(this, "Сбросить правую", this::resetRight);

    // ===== левая рука =====
    public DelimiterSetting leftTitle = new DelimiterSetting(this, "Левая рука");
    public SliderSetting lx = new SliderSetting(this, "Левая X", 0.0F, -2.0F, 2.0F, 0.05F);
    public SliderSetting ly = new SliderSetting(this, "Левая Y", 0.0F, -2.0F, 2.0F, 0.05F);
    public SliderSetting lz = new SliderSetting(this, "Левая Z", 0.0F, -2.0F, 2.0F, 0.05F);
    public SliderSetting lScale = new SliderSetting(this, "Левая размер", 1.0F, 0.1F, 3.0F, 0.05F);
    public ButtonSetting resetLeftButton = new ButtonSetting(this, "Сбросить левую", this::resetLeft);

    // ===== общий сброс =====
    public ButtonSetting resetAllButton = new ButtonSetting(this, "Сбросить всё", this::resetAll);



    /**
     * Анимация свинга и стеклянные руки перетирают ту же матрицу, поэтому на время работы
     * модуля они выключаются, а при возврате включаются обратно — но только если были включены.
     */
    @Override
    protected void onEnable() {
        SwingAnimation swingAnimation = SwingAnimation.get();
        if (restoreSwingAnimation && swingAnimation != null && !swingAnimation.isEnabled()) {
            swingAnimation.setEnabled(true, false);
        }

        GlassHands glassHands = GlassHands.getInstance();
        if (restoreGlassHands && glassHands != null && !glassHands.isEnabled()) {
            glassHands.setEnabled(true, false);
        }
    }

    @Override
    protected void onDisable() {
        SwingAnimation swingAnimation = SwingAnimation.get();
        restoreSwingAnimation = swingAnimation != null && swingAnimation.isEnabled();
        if (swingAnimation != null && swingAnimation.isEnabled()) {
            swingAnimation.setEnabled(false, false);
        }

        GlassHands glassHands = GlassHands.getInstance();
        restoreGlassHands = glassHands != null && glassHands.isEnabled();
        if (glassHands != null && glassHands.isEnabled()) {
            glassHands.setEnabled(false, false);
        }
    }

    /**
     * Позиция ставится до ванильных поворотов конкретной ветки рендера — так X/Y остаются
     * в экранных координатах и курсор в редакторе не едет в обратную сторону.
     */
    public void applyHandTranslation(MatrixStack matrices, Arm arm) {
        if (!shouldApplyTransforms()) return;
        float x = arm == Arm.RIGHT ? rx.getValue() : lx.getValue();
        float y = arm == Arm.RIGHT ? ry.getValue() : ly.getValue();
        float z = arm == Arm.RIGHT ? rz.getValue() : lz.getValue();
        matrices.translate(x, y, z);
    }

    /**
     * Размер применяется прямо на геометрии модели, чтобы не умножать сдвиги свинга
     * и не менять траекторию анимации.
     */
    public void applyHandScale(MatrixStack matrices, Arm arm) {
        if (!shouldApplyTransforms()) return;
        float s = arm == Arm.RIGHT ? rScale.getValue() : lScale.getValue();
        matrices.scale(s, s, s);
    }

    /**
     * У ванильной пустой руки база на ~0.08 ниже и дальше в сторону, чем у якоря предмета —
     * это выравнивает их между собой.
     */
    public void applyEmptyHandAlignment(MatrixStack matrices, Arm arm) {
        if (!shouldApplyTransforms()) return;
        matrices.translate(arm == Arm.RIGHT ? -0.08F : 0.08F, 0.08F, 0F);
    }

    /** Трансформы нужны и когда модуль выключен, но открыт редактор рук. */
    public boolean shouldApplyTransforms() {
        return isEnabled() || editorActive;
    }

    public boolean isEditorActive() {
        return editorActive;
    }

    public void setEditorActive(boolean editorActive) {
        this.editorActive = editorActive;
    }

    public void resetRight() {
        rx.set(0.0F);
        ry.set(0.0F);
        rz.set(0.0F);
        rScale.set(1.0F);
    }

    public void resetLeft() {
        lx.set(0.0F);
        ly.set(0.0F);
        lz.set(0.0F);
        lScale.set(1.0F);
    }

    public void resetAll() {
        resetRight();
        resetLeft();
    }
}

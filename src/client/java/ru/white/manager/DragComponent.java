package ru.white.manager;

import ru.white.Client;
import ru.white.manager.event_impl.MousePressEvent;
import ru.white.manager.event_impl.MouseReleaseEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.Component;
import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;
import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.display.InterFace;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Fonts;
import ru.white.utils.taskript.Script;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class DragComponent extends Component implements IMinecraft {

    private DragSetting selected;
    private final Vector2f offset = new Vector2f();
    private final List<Module> modules = new CopyOnWriteArrayList<>();
    public static List<Line> lines = new CopyOnWriteArrayList<>();
    private final Script script = new Script();

    // размер ячейки сетки и радиус «прилипания» (в координатах HUD)
    private static final float GRID = 8F;
    private static final float SNAP = 5F;

    // направляющие, которые надо отрисовать в этом кадре (X — вертикальные, Y — горизонтальные)
    private final List<Float> guidesV = new CopyOnWriteArrayList<>();
    private final List<Float> guidesH = new CopyOnWriteArrayList<>();

    // плавное появление/скрытие сетки при перетаскивании
    private final Animation gridAnim = new Animation();
    private boolean deleteWasPressed = false;

    // анимации направляющих: позиция плавно переезжает, прозрачность затухает
    private final Animation guideVAlpha = new Animation();
    private final Animation guideHAlpha = new Animation();
    private final Animation guideVPos = new Animation();
    private final Animation guideHPos = new Animation();

    public void post(Matrix3x2f matrices) {
        script.update();

        Window window = mc.getWindow();

        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        float scaleFix = targetScale / currentScale;

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);



        boolean inChat = mc.currentScreen instanceof ChatScreen;
        boolean holdingShift = InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean shouldRender = inChat || (mc.currentScreen == null && holdingShift);
        if (!shouldRender) selected = null;

        initModules();
        handleAnimation();

        // сетка плавно появляется только пока тащишь элемент
        boolean dragging = shouldRender && selected != null && selected.active;
        gridAnim.update();
        gridAnim.run(dragging ? 1 : 0, 0.25, Easings.SINE_OUT, true);

        if (gridAnim.get() > 0.01F) {
            drawGrid(screenWidth, screenHeight, gridAnim.get());
        }

        guidesV.clear();
        guidesH.clear();

        // зажатый ALT — свободное перемещение без сетки и прилипания
        boolean freeMove = InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_LEFT_ALT)
                || InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);

        if (shouldRender && InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_DELETE)) {
            if (!deleteWasPressed) {
                deleteWasPressed = true;
                float delMouseX = (float) (mc.mouse.getX() / 2);
                float delMouseY = (float) (mc.mouse.getY() / 2);
                for (final Module module : modules) {
                    for (final Setting<?> setting : module.getSettings()) {
                        if (setting instanceof final DragSetting ds) {
                            if (!ds.active) continue;
                            if (isHover(delMouseX, delMouseY, ds.position.x, ds.position.y, ds.size.x, ds.size.y)) {
                                toggleElementOff(ds);
                            }
                        }
                    }
                }
            }
        } else {
            deleteWasPressed = false;
        }

        if (selected != null && selected.active) {
            float mouseX = (float) (mc.mouse.getX() /  2);
            float mouseY = (float) (mc.mouse.getY() /  2);

            float posX = mouseX + offset.x;
            float posY = mouseY + offset.y;

            if (!selected.lockX) {
                selected.targetPosition.x = posX;
            }
            selected.targetPosition.y = posY;

            lines.clear();

            // выравнивание по другим элементам + по сетке
            if (!freeMove) {
                applySnapping();
            }

            handleDrags(screenWidth, screenHeight);
        }

        // обновление анимаций направляющих
        updateGuideAnimations();

        // отрисовка направляющих поверх сетки
        drawGuides(screenWidth, screenHeight, gridAnim.get());
    }

    private void updateGuideAnimations() {
        boolean hasV = !guidesV.isEmpty();
        boolean hasH = !guidesH.isEmpty();

        guideVAlpha.update();
        guideHAlpha.update();
        guideVPos.update();
        guideHPos.update();

        if (hasV) {
            float target = guidesV.get(0);
            // если линия была скрыта — появляемся сразу на месте, без переезда через экран
            if (guideVAlpha.get() <= 0.01F) guideVPos.set(target);
            else guideVPos.run(target, 0.15, Easings.BACK_OUT, true);
        }
        if (hasH) {
            float target = guidesH.get(0);
            if (guideHAlpha.get() <= 0.01F) guideHPos.set(target);
            else guideHPos.run(target, 0.15, Easings.BACK_OUT, true);
        }

        guideVAlpha.run(hasV ? 1 : 0, 0.2, Easings.SINE_OUT, true);
        guideHAlpha.run(hasH ? 1 : 0, 0.2, Easings.SINE_OUT, true);
    }

    private void drawGrid(int width, int height, float alpha) {
        int color = ColorUtil.getColor(255, 255, 255, (int) (16 * alpha));
        int colorMajor = ColorUtil.getColor(255, 255, 255, (int) (34 * alpha));

        int i = 0;
        for (float x = 0; x <= width; x += GRID, i++) {
            RenderUtil.Render2D.rect(x, 0, 0.5F, height, i % 5 == 0 ? colorMajor : color);
        }
        i = 0;
        for (float y = 0; y <= height; y += GRID, i++) {
            RenderUtil.Render2D.rect(0, y, width, 0.5F, i % 5 == 0 ? colorMajor : color);
        }

        // центральные оси экрана
        int center = ColorUtil.getColor(255, 120, 170, (int) (70 * alpha));
        RenderUtil.Render2D.rect(width / 2F, 0, 0.6F, height, center);
        RenderUtil.Render2D.rect(0, height / 2F, width, 0.6F, center);
    }

    private void drawGuides(int width, int height, float alpha) {
        if (alpha <= 0.01F) return;

        float aV = guideVAlpha.get() * alpha;
        float aH = guideHAlpha.get() * alpha;

        if (aV > 0.01F) {
            int color = ColorUtil.getColor(255, 120, 200, (int) (200 * aV));
            RenderUtil.Render2D.rect(guideVPos.get() - 0.3F, 0, 0.6F, height, color);
        }
        if (aH > 0.01F) {
            int color = ColorUtil.getColor(255, 120, 200, (int) (200 * aH));
            RenderUtil.Render2D.rect(0, guideHPos.get() - 0.3F, width, 0.6F, color);
        }
    }

    // прилипание выделенного элемента к краям/центрам других элементов; если нет — к сетке
    private void applySnapping() {
        float sx = selected.targetPosition.x;
        float sy = selected.targetPosition.y;
        float sw = selected.size.x;
        float sh = selected.size.y;

        // три опорные точки выделенного элемента по каждой оси: начало, центр, конец
        float[] sXs = { sx, sx + sw / 2F, sx + sw };
        float[] sYs = { sy, sy + sh / 2F, sy + sh };

        Float bestX = null;
        float bestXDist = SNAP;
        float guideX = 0;

        Float bestY = null;
        float bestYDist = SNAP;
        float guideY = 0;

        for (Module module : modules) {
            for (Setting<?> setting : module.getSettings()) {
                if (!(setting instanceof DragSetting o)) continue;
                if (o == selected || !o.active) continue;

                float[] oXs = { o.position.x, o.position.x + o.size.x / 2F, o.position.x + o.size.x };
                float[] oYs = { o.position.y, o.position.y + o.size.y / 2F, o.position.y + o.size.y };

                for (float oe : oXs) {
                    for (float se : sXs) {
                        float d = Math.abs(se - oe);
                        if (d < bestXDist) {
                            bestXDist = d;
                            bestX = sx + (oe - se);
                            guideX = oe;
                        }
                    }
                }
                for (float oe : oYs) {
                    for (float se : sYs) {
                        float d = Math.abs(se - oe);
                        if (d < bestYDist) {
                            bestYDist = d;
                            bestY = sy + (oe - se);
                            guideY = oe;
                        }
                    }
                }
            }
        }

        if (!selected.lockX) {
            if (bestX != null) {
                selected.targetPosition.x = bestX;
                guidesV.add(guideX);
            } else {
                selected.targetPosition.x = Math.round(sx / GRID) * GRID;
            }
        }

        if (bestY != null) {
            selected.targetPosition.y = bestY;
            guidesH.add(guideY);
        } else {
            selected.targetPosition.y = Math.round(sy / GRID) * GRID;
        }
    }

    private void handleDrags(int width, int height) {
        for (Module module : modules) {
            for (Setting<?> setting : module.getSettings()) {
                if (!(setting instanceof DragSetting drag)) continue;
                if (!drag.active) continue;

                drag.position.x = (float) drag.animationX.getValue();
                drag.position.y = (float) drag.animationY.getValue();

                drag.position.x = Math.max(0, Math.min(width - drag.size.x, drag.position.x));
                drag.position.y = Math.max(0, Math.min(height - drag.size.y, drag.position.y));

                drag.targetPosition.x = Math.max(0, Math.min(width - drag.size.x, drag.targetPosition.x));
                drag.targetPosition.y = Math.max(0, Math.min(height - drag.size.y, drag.targetPosition.y));
            }
        }
    }

    private void handleSnaps(MatrixStack matrices, int color) {
        double closest;

        for (Line snap : lines) {
            switch (snap.direction) {
                case VERTICAL -> {
                    closest = Double.MAX_VALUE;
                    for (float y = -selected.size.y; y <= 0; y += selected.size.y / 2F) {
                        if ((y == -selected.size.y / 2F && !snap.center)
                                || (y == -selected.size.y && !snap.left)
                                || (y == 0 && !snap.right)) continue;

                        double dist = Math.abs(selected.targetPosition.y - (snap.position + y));
                        if (dist < snap.distance && dist < closest) {
                            closest = dist;
                            selected.targetPosition.y = snap.position + y;
                            // RectUtil.drawRect(matrices, 0, snap.position - 0.5F, scaled().x, 1F, color);
                        }
                    }
                }
                case HORIZONTAL -> {
                    closest = Double.MAX_VALUE;
                    for (float x = -selected.size.x; x <= 0; x += selected.size.x / 2F) {
                        if ((x == -selected.size.x / 2F && !snap.center)
                                || (x == -selected.size.x && !snap.left)
                                || (x == 0 && !snap.right)) continue;

                        float dist = Math.abs(selected.targetPosition.x - (snap.position + x));
                        if (dist < snap.distance && dist < closest) {
                            closest = dist;
                            selected.targetPosition.x = snap.position + x;
                            // RectUtil.drawRect(matrices, snap.position - 0.5F, 0, 1F, scaled().y, color);
                        }
                    }
                }
            }
        }
    }

    // Vector2f scaled() {
    //     return ScaleMath.getMouse(MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight());
    // }
//
    // private void initSnaps(int width, int height) {
    //     float edge = 5F;
    //     float dist = 5F;
//
//
//
    // }

    private void handleAnimation() {
        modules.forEach(m -> m.getSettings().forEach(s -> {
            if (s instanceof DragSetting drag) {
                drag.animationX.update();
                drag.animationY.update();
                if (script.isFinished()) {
                    drag.animationX.run(drag.targetPosition.x, 0.06,true);
                    drag.animationY.run(drag.targetPosition.y, 0.06,true);
                }
            }
        }));
    }

    private void initModules() {
        modules.clear();
        Client.get().moduleManager().values().stream()
                .filter(m -> m.isEnabled() && m.getSettings().stream().anyMatch(s -> s instanceof DragSetting))
                .forEach(modules::add);
    }


    @EventHandler
    public void onEvent(MousePressEvent event) {
        if (event.getKey() != 0) {
            return;
        }
        boolean inChat = event.getScreen() instanceof ChatScreen;
        boolean holdingShift = InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean canDrag = inChat || (event.getScreen() == null && holdingShift);
        if (!canDrag) return;
        for (final Module module : modules) {
            for (final Setting<?> setting : module.getSettings()) {
                if (setting instanceof final DragSetting dragSetting) {
                    if (!dragSetting.active) continue;
                    final Vector2f position = dragSetting.position;
                    final Vector2f scale = dragSetting.size;

                    final Vector2f mouse = getMouse((float) event.getMouseX(), (float) event.getMouseY());
                    final double mouseX = mouse.x;
                    final double mouseY = mouse.y;
                    if (!dragSetting.active) return;
                    if (!dragSetting.structure && isHover(mouseX, mouseY, position.x, position.y, scale.x, scale.y)) {
                        selected = dragSetting;
                        offset.set(position.x - mouseX, position.y - mouseY);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEvent(MouseReleaseEvent event) {
        if (selected != null) {
            script.cleanup()
                    .addTickStep(0, () -> {
                        selected.targetPosition.set(selected.position);
                        selected = null;
                    }, () -> selected != null && selected.animationX.isFinished() && selected.animationY.isFinished());
        }
    }

    public static boolean isHover(double mouseX, double mouseY,
                                  double x, double y,
                                  double width, double height) {
        return mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height;
    }

    public Vector2f getMouse(double mouseX, double mouseY) {
        return new Vector2f((float) (mouseX * mc.getWindow().getScaleFactor() / 2), (float) (mouseY * mc.getWindow().getScaleFactor() / 2));
    }

    private static final Map<String, String> DRAG_TO_ELEMENT = Map.of(
            "WaterMark", "Water mark",
            "Information", "Information",
            "Key Binds", "Key Binds",
            "Potions", "Potions",
            "Cooldowns", "Cooldowns",
            "Music Player", "Music Player",
            "Notifications", "Notifications",
            "Target Hud", "Target Hud",
            "Armor Hud", "Armor Hud"
    );

    private void toggleElementOff(DragSetting ds) {
        String elementName = DRAG_TO_ELEMENT.get(ds.getName());
        if (elementName != null) {
            var setting = InterFace.getInstance().element.get(elementName);
            if (setting != null && setting.getValue()) {
                setting.set(false);
            }
        }
    }


    @AllArgsConstructor
    public static class Line {
        public float position, distance;
        public Direction direction;
        public boolean center, right, left;
    }

    public enum Direction {
        VERTICAL,
        HORIZONTAL
    }
}

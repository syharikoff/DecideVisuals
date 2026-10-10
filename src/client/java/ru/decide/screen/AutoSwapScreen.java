package ru.decide.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import static org.lwjgl.glfw.GLFW.glfwGetKey;
import org.lwjgl.glfw.GLFW;
import ru.decide.module.impl.utils.AutoSwapModule;
import ru.decide.utils.annotation.IMinecraft;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.render.Draw;
import ru.decide.utils.render.RenderUtil;
import ru.decide.utils.render.ScreenBlur;

public final class AutoSwapScreen extends Screen implements IMinecraft {
    private static final float RADIUS = 72F;
    private static final float DEAD_ZONE = 40F;
    private static final float SLOT = 40F;

    private final AutoSwapModule module;
    private final int heldKey;
    private final int slotCount;
    private final boolean[] available;
    private final ru.decide.utils.animation.satoshi.EaseInOutQuad[] hoverAnims;

    public AutoSwapScreen(AutoSwapModule module, int heldKey) {
        super(Text.literal("Auto Swap"));
        this.module = module;
        this.heldKey = heldKey;
        this.slotCount = module.getSlotCount();
        this.available = new boolean[slotCount];
        this.hoverAnims = new ru.decide.utils.animation.satoshi.EaseInOutQuad[slotCount];
        for (int i = 0; i < slotCount; i++) {
            hoverAnims[i] = new ru.decide.utils.animation.satoshi.EaseInOutQuad(200, 1);
        }
        refresh();
    }

    public boolean belongsTo(AutoSwapModule other) {
        return module == other;
    }

    private void refresh() {
        for (int i = 0; i < slotCount; i++) available[i] = module.available(i);
    }

    @Override
    protected void init() {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void tick() {
        if (client == null || client.player == null || !module.isEnabled() || !module.mode.is("Мульти")) close();
        if (client != null && client.options != null) {
            client.options.forwardKey.setPressed(glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS);
            client.options.backKey.setPressed(glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS);
            client.options.leftKey.setPressed(glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS);
            client.options.rightKey.setPressed(glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS);
            client.options.jumpKey.setPressed(glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS);
            client.options.sprintKey.setPressed(glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS);
        }
    }

    private int sector(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        if (Math.hypot(dx, dy) <= DEAD_ZONE) return -1;
        double angle = (Math.toDegrees(Math.atan2(-dx, dy)) + 180) % 360;
        int index = (int) Math.floor(angle / (360.0 / slotCount));
        return index >= slotCount ? 0 : index;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (client == null || client.player == null) return;

        float alpha = 1F;

        Draw.rect(0, 0, width, height, ColorUtil.getColor(0, 0.45F * alpha));
        ScreenBlur.capture(1);

        float size = RADIUS * 2 + SLOT + 56;
        float px = width / 2F - size / 2F;
        float py = height / 2F - size / 2F;
        float half = size / 2F;

        Draw.glow(px, py, size, size, ColorUtil.replAlpha(ColorUtil.client(), 0.20F * alpha),
                half, 14, 1);
        Draw.glass(px, py, size, size, alpha, half, half, half, half,
                ColorUtil.multAlpha(ColorUtil.multDark(ColorUtil.background(), 0.6F), alpha));
        Draw.glassOutline(px, py, size, size, 0.5F, half, alpha, 0.8F);

        int selected = sector(mouseX, mouseY);

        for (int i = 0; i < slotCount; i++) {
            double angle = (i + 0.5) * Math.PI * 2 / slotCount;
            float x = (float) (width / 2.0 + Math.sin(angle) * RADIUS);
            float y = (float) (height / 2.0 - Math.cos(angle) * RADIUS);

            boolean hovered = inside(mouseX, mouseY, x - SLOT / 2, y - SLOT / 2, SLOT, SLOT) || selected == i;
            hoverAnims(i).setDirection(hovered ? ru.decide.utils.animation.satoshi.Direction.FORWARDS
                    : ru.decide.utils.animation.satoshi.Direction.BACKWARDS);
            float sel = hoverAnims(i).getOutput();

            ItemStack item = module.savedItems().get(i);
            boolean missing = !item.isEmpty() && !available[i];

            RenderUtil.Render2D.rect(x - SLOT / 2, y - SLOT / 2, SLOT, SLOT, ColorUtil.overCol(
                    ColorUtil.getColor(40, 0.5F * alpha),
                    ColorUtil.replAlpha(ColorUtil.client(), 0.55F * alpha), sel), 9);
            RenderUtil.Render2D.outline(x - SLOT / 2, y - SLOT / 2, SLOT, SLOT, 0.5F,
                    missing ? ColorUtil.getColor(235, 70, 70, alpha * 0.8F)
                            : ColorUtil.replAlpha(ColorUtil.client(), alpha * Math.max(hovered ? 0.9F : 0.25F, sel)),
                    9);

            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x, y);
            matrices.scale(2F, 2F);
            if (item.isEmpty()) {
                matrices.popMatrix();
                context.drawCenteredTextWithShadow(client.textRenderer, "+", (int) x, (int) y - 4,
                        ColorUtil.getColor(255, 0.7F * alpha));
            } else {
                context.drawItem(item, -8, -8);
                matrices.popMatrix();
                if (hovered) {
                    String name = item.getName().getString();
                    if (client.textRenderer.getWidth(name) <= size - 30) {
                        context.drawCenteredTextWithShadow(client.textRenderer, name, width / 2,
                                (int) (py + size - 18), ColorUtil.getColor(220, alpha));
                    }
                }
            }
        }

        context.drawCenteredTextWithShadow(client.textRenderer,
                "Отпусти клавишу — свап · ПКМ по пустому — выбрать предмет · Esc — выход",
                width / 2, height - 24, ColorUtil.getColor(170, 0.8F * alpha));
    }

    private ru.decide.utils.animation.satoshi.Animation hoverAnims(int i) {
        return hoverAnims[i];
    }

    private boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void finishSelection() {
        if (client == null || client.currentScreen != this) return;
        int index = sector(client.mouse.getX() * width / client.getWindow().getWidth(),
                client.mouse.getY() * height / client.getWindow().getHeight());
        close();
        if (index >= 0 && index < slotCount && available[index]) module.select(index);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        if (input.key() == heldKey) {
            finishSelection();
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == heldKey) finishSelection();
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (client == null || client.player == null) return true;
        int index = sector(click.x(), click.y());
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && index >= 0) {
            if (module.savedItems().get(index).isEmpty()) {
                close();
                module.startPick(index);
            } else {
                module.removeAt(index);
                refresh();
            }
        } else if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && index >= 0 && available[index]) {
            finishSelection();
        }
        return true;
    }

    @Override
    public void close() {
        if (client != null && client.currentScreen == this) client.setScreen(null);
    }
}

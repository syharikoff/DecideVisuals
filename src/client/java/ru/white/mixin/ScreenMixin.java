package ru.white.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Fonts;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void decidevisuals$renderStyledButtons(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;

        for (var child : self.children()) {
            if (child instanceof PressableWidget widget && widget.visible) {
                float x = widget.getX();
                float y = widget.getY();
                float w = widget.getWidth();
                float h = widget.getHeight();

                boolean hovered = MathUtil.isHovered(mouseX, mouseY, x, y, w, h);
                float hp = hovered ? 1f : 0f;
                float activeAlpha = widget.active ? 1f : 0.35f;

                RenderUtil.Render2D.rect(x, y, w, h,
                        ColorUtil.getColor(0, activeAlpha * (0.7f + hp * 0.1f)), 6);

                RenderUtil.Render2D.outline(x, y, w, h, 0.5f,
                        ColorUtil.replAlpha(ColorUtil.getColor(255),
                                activeAlpha * (0.03f + hp * 0.12f)), 6);

                String text = widget.getMessage().getString();
                int textColor = ColorUtil.replAlpha(
                        ColorUtil.getColor(255),
                        activeAlpha * (0.5f + hp * 0.5f));
                Fonts.sf_regular.drawCentered(text, x + w / 2f, y + h / 2f - 3.8f, 7, textColor);
            }
        }
    }
}

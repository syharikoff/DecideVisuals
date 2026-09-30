package ru.white.mixin;

import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import ru.white.Client;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow
    protected TextFieldWidget chatField;

    @Unique private List<String> decidevisuals$suggestions = Collections.emptyList();
    @Unique private int decidevisuals$selected = 0;

    @Unique private int decidevisuals$boxX, decidevisuals$boxY, decidevisuals$boxW, decidevisuals$boxCount;
    @Unique private static final int DECIDEVISUALS_LINE_H = 12;

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void interceptMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (message.isEmpty()) return;

        char prefix = Client.get().commandManager().getPrefix();
        if (message.charAt(0) == prefix) {
            ci.cancel();
            Client.get().commandManager().handleMessage(message);
            return;
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void decidevisuals$renderSuggestions(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        decidevisuals$boxCount = 0;

        String text = chatField.getText();
        char prefix = Client.get().commandManager().getPrefix();

        if (text.isEmpty() || text.charAt(0) != prefix) {
            decidevisuals$suggestions = Collections.emptyList();
            return;
        }

        decidevisuals$suggestions = Client.get().commandManager().getSuggestions(text);
        if (decidevisuals$suggestions.isEmpty()) return;

        if (decidevisuals$selected >= decidevisuals$suggestions.size()) decidevisuals$selected = 0;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int count = Math.min(decidevisuals$suggestions.size(), 10);

        int width = 0;
        for (int i = 0; i < count; i++) {
            width = Math.max(width, tr.getWidth(decidevisuals$suggestions.get(i)));
        }
        width += 6;

        int boxH = count * DECIDEVISUALS_LINE_H;
        int x = chatField.getX() - 2;
        int y = chatField.getY() - boxH - 1;

        decidevisuals$boxX = x;
        decidevisuals$boxY = y;
        decidevisuals$boxW = width;
        decidevisuals$boxCount = count;

        context.fill(x, y, x + width, y + boxH, 0xE6000000);
        context.fill(x, y, x + width, y + 1, 0x40FFFFFF);

        for (int i = 0; i < count; i++) {
            int ly = y + i * DECIDEVISUALS_LINE_H;
            boolean sel = i == decidevisuals$selected;
            if (sel) context.fill(x, ly, x + width, ly + DECIDEVISUALS_LINE_H, 0x55FFFFFF);
            context.drawText(tr, decidevisuals$suggestions.get(i), x + 3, ly + 2,
                    sel ? 0xFFFFFF55 : 0xFFBBBBBB, false);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void decidevisuals$keyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (decidevisuals$suggestions.isEmpty()) return;
        int n = decidevisuals$suggestions.size();

        switch (input.getKeycode()) {
            case GLFW.GLFW_KEY_TAB -> {
                decidevisuals$apply(decidevisuals$suggestions.get(Math.min(decidevisuals$selected, n - 1)));
                cir.setReturnValue(true);
            }
            case GLFW.GLFW_KEY_UP -> {
                decidevisuals$selected = (decidevisuals$selected - 1 + n) % n;
                cir.setReturnValue(true);
            }
            case GLFW.GLFW_KEY_DOWN -> {
                decidevisuals$selected = (decidevisuals$selected + 1) % n;
                cir.setReturnValue(true);
            }
            default -> {}
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void decidevisuals$mouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {

        int button = click.button();

        double mouseX = click.x();
        double mouseY = click.y();

        if (decidevisuals$suggestions.isEmpty() || decidevisuals$boxCount == 0 || button != 0) return;

        if (mouseX >= decidevisuals$boxX && mouseX <= decidevisuals$boxX + decidevisuals$boxW
                && mouseY >= decidevisuals$boxY && mouseY <= decidevisuals$boxY + decidevisuals$boxCount * DECIDEVISUALS_LINE_H) {
            int idx = (int) ((mouseY - decidevisuals$boxY) / DECIDEVISUALS_LINE_H);
            if (idx >= 0 && idx < decidevisuals$boxCount && idx < decidevisuals$suggestions.size()) {
                decidevisuals$apply(decidevisuals$suggestions.get(idx));
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private void decidevisuals$apply(String suggestion) {
        chatField.setText(suggestion);
        chatField.setCursorToEnd(false);
        decidevisuals$selected = 0;
    }
}

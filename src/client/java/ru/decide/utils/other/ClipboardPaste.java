package ru.decide.utils.other;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Вставка из буфера обмена (Ctrl+V) для текстовых полей клиента.
 * Пользовательский ввод: ник игрока, поиск, названия пресетов/алтов и т.п.
 */
public final class ClipboardPaste {

    private ClipboardPaste() {
    }

    /** Зажат ли Ctrl (любой). */
    public static boolean ctrlDown() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        long handle = mc.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    /** Это сочетание Ctrl+V? */
    public static boolean pasteCombo(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_V && ctrlDown();
    }

    /** Содержимое буфера одной строкой (переносы -> пробел), обрезанное до maxLength. */
    public static String content(int maxLength) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.keyboard == null) return "";

        String clip = mc.keyboard.getClipboard();
        if (clip == null || clip.isEmpty()) return "";

        String clean = clip.replace("\r", " ").replace("\n", " ").trim();
        if (clean.isEmpty()) return "";

        if (maxLength > 0 && clean.length() > maxLength) {
            clean = clean.substring(0, maxLength);
        }
        return clean;
    }

    /** Дописать содержимое буфера в текущее значение поля с учётом лимита длины. */
    public static String append(String current, int maxLength) {
        String paste = content(maxLength);
        if (paste.isEmpty()) return current;

        String base = current == null ? "" : current;
        if (maxLength > 0 && base.length() >= maxLength) return base;

        int room = maxLength > 0 ? maxLength - base.length() : paste.length();
        return base + paste.substring(0, Math.min(room, paste.length()));
    }
}
package ru.white.utils.notification;

import ru.white.utils.animation.Animation;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class NotificationManager {

    public enum Type {
        MODULE, EFFECT, INFO, WARNING
    }

    public static class Entry {
        public final String text;
        public final Type type;
        public final Animation anim = new Animation();
        public boolean removing = false;
        private final long created = System.currentTimeMillis();
        private final long duration;
        public final String iconGlyph;
        public final Identifier effectTexture;
        public final ItemStack itemStack;

        Entry(String text, Type type, String iconGlyph, Identifier effectTexture, ItemStack itemStack, long duration) {
            this.text = text;
            this.type = type;
            this.iconGlyph = iconGlyph;
            this.effectTexture = effectTexture;
            this.itemStack = itemStack;
            this.duration = duration;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - created >= duration;
        }
    }

    private static final List<Entry> entries = new ArrayList<>();

    public static void send(String text) {
        add(text, Type.INFO, null, null, null, 3000);
    }

    public static void send(String text, Type type) {
        add(text, type, null, null, null, 3000);
    }

    public static void send(String text, Type type, long ms) {
        add(text, type, null, null, null, ms);
    }

    /** MODULE — гляф категории ("e","i","n","l","u") */
    public static void send(String text, Type type, String glyph) {
        add(text, type, glyph, null, null, 3000);
    }

    public static void send(String text, Type type, String glyph, long ms) {
        add(text, type, glyph, null, null, ms);
    }

    /** EFFECT — текстура эффекта */
    public static void send(String text, Type type, Identifier texture) {
        add(text, type, null, texture, null, 3000);
    }

    public static void send(String text, Type type, Identifier texture, long ms) {
        add(text, type, null, texture, null, ms);
    }

    /** Иконка — предмет ItemStack */
    public static void send(String text, Type type, ItemStack item) {
        add(text, type, null, null, item, 3000);
    }

    public static void send(String text, Type type, ItemStack item, long ms) {
        add(text, type, null, null, item, ms);
    }

    private static void add(String text, Type type, String glyph, Identifier texture, ItemStack item, long ms) {
        synchronized (entries) {
            entries.add(new Entry(text, type, glyph, texture, item, ms));
        }
    }

    public static List<Entry> entries() {
        return entries;
    }
}

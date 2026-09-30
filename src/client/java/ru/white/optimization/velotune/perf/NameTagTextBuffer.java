/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.minecraft.PlayerEntityRenderState
 *  net.minecraft.Text
 *  net.minecraft.TextRenderer
 *  net.minecraft.OrderedText
 */
package ru.white.optimization.velotune.perf;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.text.Text;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;

@Environment(value=EnvType.CLIENT)
public final class NameTagTextBuffer {
    private static final int MAX_PLAYERS = 4096;
    private static final int MAX_WIDTHS = 4096;
    private static final Map<Integer, TextEntry> PLAYER_TEXT = new HashMap<Integer, TextEntry>();
    private static final Map<WidthKey, WidthEntry> WIDTHS = new LinkedHashMap<WidthKey, WidthEntry>(256, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<WidthKey, WidthEntry> eldest) {
            return this.size() > 4096;
        }
    };

    private NameTagTextBuffer() {
    }

    public static void apply(PlayerEntityRenderState state, int updatesPerSecond) {
        if (state.id == 0) {
            return;
        }
        if (PLAYER_TEXT.size() > 4096) {
            PLAYER_TEXT.clear();
        }
        long now = System.nanoTime();
        TextEntry entry = PLAYER_TEXT.computeIfAbsent(state.id, ignored -> new TextEntry());
        if (TextRefreshThrottle.shouldRefresh(entry.initialized, now - entry.capturedNanos, updatesPerSecond)) {
            entry.displayName = state.displayName;
            entry.playerName = state.playerName;
            entry.capturedNanos = now;
            entry.initialized = true;
        } else {
            state.displayName = entry.displayName;
            state.playerName = entry.playerName;
        }
    }

    public static int width(TextRenderer renderer, OrderedText text, int updatesPerSecond) {
        long now = System.nanoTime();
        WidthKey key = new WidthKey(renderer, text);
        WidthEntry entry = WIDTHS.get(key);
        if (entry == null || TextRefreshThrottle.shouldRefresh(true, now - entry.capturedNanos, updatesPerSecond)) {
            int width = renderer.getWidth(text);
            WIDTHS.put(key, new WidthEntry(width, now));
            return width;
        }
        return entry.width;
    }

    @Environment(value=EnvType.CLIENT)
    private static final class TextEntry {
        private Text displayName;
        private Text playerName;
        private long capturedNanos;
        private boolean initialized;

        private TextEntry() {
        }
    }

    @Environment(value=EnvType.CLIENT)
    private record WidthKey(TextRenderer renderer, OrderedText text) {
    }

    @Environment(value=EnvType.CLIENT)
    private record WidthEntry(int width, long capturedNanos) {
    }
}

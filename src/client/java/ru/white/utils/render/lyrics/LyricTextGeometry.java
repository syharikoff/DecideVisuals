package ru.white.utils.render.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import ru.white.utils.media.MediaLog;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Раскладка MSDF-текста для 3D-рендера (порт {@code MsdfTextGeometry} из Kimiko).
 *
 * Отличается от 2D-пути клиента ({@link ru.white.utils.render.font.FontAtlas}) двумя
 * вещами, которые здесь принципиальны:
 * <ul>
 *   <li>базовая линия берётся из {@code metrics.ascender} самого шрифта, а не из
 *       константы 0.95 — иначе текст прыгает по вертикали между шрифтами;</li>
 *   <li>применяется {@code kerning} из json.</li>
 * </ul>
 *
 * Атлас грузится лениво и кэшируется; GpuTextureView берётся из TextureManager, чтобы
 * он совпадал с тем, что уже загружено 2D-рендером (одна текстура на оба пути).
 */
public final class LyricTextGeometry {

    private static final Map<String, MsdfFont> FONTS = new HashMap<>();
    private static final Map<String, GpuTextureView> VIEWS = new HashMap<>();

    private LyricTextGeometry() {}

    /** Раскладывает текст в локальные координаты (Y вверх, начало — базовая линия). */
    public static Layout layout(String fontName, String text, float size) {
        MsdfFont font = MsdfFont.get(fontName);
        if (font == null || text == null || text.isEmpty() || size <= 0.0f) {
            return Layout.EMPTY;
        }
        GpuTextureView atlas = atlasView(font);
        if (atlas == null) {
            return Layout.EMPTY;
        }

        List<Quad> quads = new ArrayList<>(text.length());
        float baseline = font.ascent(size);
        float penX = 0.0f;
        int previous = -1;
        int index = 0;

        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint == '\n') {
                continue;
            }

            Glyph glyph = font.glyph(codePoint);
            if (previous != -1) {
                penX += font.kerning(previous, codePoint) * size;
            }
            if (glyph != null && glyph.drawable()) {
                quads.add(new Quad(
                        penX + glyph.planeLeft * size, baseline - glyph.planeTop * size,
                        penX + glyph.planeRight * size, baseline - glyph.planeBottom * size,
                        glyph.u0, glyph.v0, glyph.u1, glyph.v1, codePoint));
            }
            penX += (glyph == null ? font.missingAdvance() : glyph.advance) * size;
            previous = codePoint;
        }
        return new Layout(List.copyOf(quads), font.width(text, size), font.lineHeight(size), atlas);
    }

    public static float width(String fontName, String text, float size) {
        MsdfFont font = MsdfFont.get(fontName);
        return font == null || text == null || text.isEmpty() ? 0.0f : font.width(text, size);
    }

    /**
     * GpuTextureView атласа.
     *
     * Текстуру грузим сами и регистрируем под отдельным id. Причина: 2D-путь
     * ({@code FontPipeline}) регистрирует {@code client:fonts/<name>.png} только когда этот
     * шрифт реально нарисован в интерфейсе, а Manasco в UI не используется. Плюс
     * {@code TextureManager.getTexture} для неизвестного id не возвращает null, а молча
     * создаёт missing-texture — так что порядок «сначала загрузить, потом взять view»
     * здесь принципиален.
     */
    private static synchronized GpuTextureView atlasView(MsdfFont font) {
        GpuTextureView cached = VIEWS.get(font.viewId().toString());
        if (cached != null && !cached.isClosed()) {
            return cached;
        }
        // Текстура перезагрузилась (F3+T) — прошлые view уже невалидны
        VIEWS.entrySet().removeIf(e -> e.getValue().isClosed());
        VIEWS.clear();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        if (!font.ensureAtlas(client)) {
            return null;
        }

        AbstractTexture texture = client.getTextureManager().getTexture(font.viewId());
        if (texture == null) {
            return null;
        }
        GpuTextureView view = texture.getGlTextureView();
        if (view == null) {
            return null;
        }
        VIEWS.put(font.viewId().toString(), view);
        return view;
    }

    /** Сбрасывает кэш view — после перезагрузки ресурсов. */
    public static synchronized void invalidate() {
        VIEWS.clear();
        for (MsdfFont font : FONTS.values()) {
            font.markAtlasStale();
        }
    }

    // ------------------------------------------------------------------ glyph

    private static final class Glyph {
        final float planeLeft, planeRight, planeTop, planeBottom;
        final float advance;
        final float u0, v0, u1, v1;
        final boolean drawable;

        Glyph(float planeLeft, float planeRight, float planeTop, float planeBottom,
              float advance, float u0, float v0, float u1, float v1, boolean drawable) {
            this.planeLeft = planeLeft;
            this.planeRight = planeRight;
            this.planeTop = planeTop;
            this.planeBottom = planeBottom;
            this.advance = advance;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.drawable = drawable;
        }

        boolean drawable() {
            return drawable;
        }
    }

    // ------------------------------------------------------------------- font

    private static final class MsdfFont {

        private final String key;
        private final Identifier jsonId;
        private final Identifier textureId;
        private final Map<Integer, Glyph> glyphs = new HashMap<>();
        private final Map<Long, Float> kerning = new HashMap<>();

        private float emSize = 1.0f;
        private float ascender = 0.8f;
        private float lineHeightRatio = 1.2f;
        private float fontSize = 32.0f;
        private float atlasWidth = 512.0f;
        private float atlasHeight = 512.0f;
        private boolean yOriginBottom = true;
        private boolean uploaded;
        private int skipped;

        private MsdfFont(String key, Identifier jsonId, Identifier textureId) {
            this.key = key;
            this.jsonId = jsonId;
            this.textureId = textureId;
        }

        static synchronized MsdfFont get(String name) {
            if (name == null) {
                return null;
            }
            MsdfFont font = FONTS.get(name);
            if (font != null) {
                return font;
            }
            Identifier jsonId = Identifier.of("client", "fonts/" + name + ".json");
            Identifier textureId = Identifier.of("client", "fonts/" + name + ".png");
            font = new MsdfFont(name, jsonId, textureId);
            if (!font.load()) {
                return null;
            }
            FONTS.put(name, font);
            return font;
        }

        private boolean load() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return false;
            }
            try {
                Optional<Resource> resource = client.getResourceManager().getResource(jsonId);
                if (resource.isEmpty()) {
                    MediaLog.note("font", "нет метрик: " + jsonId);
                    return false;
                }
                try (InputStream in = resource.get().getInputStream();
                     InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    parse(JsonParser.parseReader(reader).getAsJsonObject());
                }
                if (glyphs.isEmpty()) {
                    MediaLog.note("font", "в " + jsonId + " нет глифов");
                    return false;
                }
                MediaLog.note("font", key + ": " + glyphs.size() + " глифов, kerning="
                        + kerning.size() + ", ascender=" + ascender + ", атлас "
                        + atlasWidth + "x" + atlasHeight);
                return true;
            } catch (Exception e) {
                MediaLog.note("font", "не читается " + jsonId + ": " + e);
                return false;
            }
        }

        /**
         * Загружает PNG атласа и регистрирует его в TextureManager под собственным id.
         * Отдельный id, а не {@code client:fonts/<name>.png}, намеренно: тот же ресурс
         * может уже быть зарегистрирован 2D-путем, и подмена текстуры на лету сорвала бы
         * отрисовку текущего кадра. Своя копия стоит 350–460 КБ на шрифт и не конфликтует.
         */
        private synchronized boolean ensureAtlas(MinecraftClient client) {
            if (uploaded) {
                return true;
            }
            try {
                Optional<Resource> resource = client.getResourceManager().getResource(textureId);
                if (resource.isEmpty()) {
                    return false;
                }
                NativeImage image;
                try (InputStream in = resource.get().getInputStream()) {
                    image = NativeImage.read(in);
                }
                if (image == null) {
                    return false;
                }
                NativeImageBackedTexture texture =
                        new NativeImageBackedTexture(() -> viewId().toString(), image);
                texture.upload();
                client.getTextureManager().registerTexture(viewId(), texture);
                uploaded = true;
                MediaLog.note("font", key + ": атлас " + image.getWidth() + "x" + image.getHeight()
                        + " загружен как " + viewId());
                return true;
            } catch (Exception e) {
                MediaLog.note("font", "не читается атлас " + textureId + ": " + e);
                return false;
            }
        }

        void markAtlasStale() {
            uploaded = false;
        }

        private Identifier viewId() {
            return Identifier.of("client", "lyrics/font/" + key);
        }

        private void parse(JsonObject root) {
            if (root.has("atlas")) {
                JsonObject atlas = root.getAsJsonObject("atlas");
                atlasWidth = floatOf(atlas, "width", 512f);
                atlasHeight = floatOf(atlas, "height", 512f);
                fontSize = floatOf(atlas, "size", 32f);
                if (atlas.has("yOrigin")) {
                    yOriginBottom = atlas.get("yOrigin").getAsString().equalsIgnoreCase("bottom");
                }
            }
            if (root.has("metrics")) {
                JsonObject metrics = root.getAsJsonObject("metrics");
                emSize = floatOf(metrics, "emSize", 1.0f);
                ascender = floatOf(metrics, "ascender", 0.8f);
                lineHeightRatio = floatOf(metrics, "lineHeight", 1.2f);
            }
            if (root.has("kerning")) {
                JsonArray kerningArray = root.getAsJsonArray("kerning");
                for (JsonElement element : kerningArray) {
                    JsonObject pair = element.getAsJsonObject();
                    // Формат msdfgen: unicode1/unicode2/advance.
                    // Формат FontForge и sf-атласов Kimiko: left/right/value.
                    // Путать их нельзя — исключение здесь роняет загрузку всего шрифта,
                    // и глифы просто перестают рисоваться.
                    int left = pair.has("unicode1")
                            ? pair.get("unicode1").getAsInt()
                            : pair.get("left").getAsInt();
                    int right = pair.has("unicode2")
                            ? pair.get("unicode2").getAsInt()
                            : pair.get("right").getAsInt();
                    float amount = pair.has("advance")
                            ? pair.get("advance").getAsFloat()
                            : floatOf(pair, "value", 0f);
                    kerning.put(pairKey(left, right), amount);
                }
            }
            if (root.has("glyphs")) {
                for (JsonElement element : root.getAsJsonArray("glyphs")) {
                    try {
                        parseGlyph(element.getAsJsonObject());
                    } catch (Exception e) {
                        // Один битый глиф не должен ронять весь шрифт: иначе ошибка в
                        // формате json тихо гасит всю фичу.
                        skipped++;
                    }
                }
                if (skipped > 0) {
                    MediaLog.note("font", key + ": пропущено битых глифов — " + skipped);
                }
            }
        }

        private void parseGlyph(JsonObject g) {
            int unicode;
            if (g.has("unicode")) {
                unicode = g.get("unicode").getAsInt();
            } else if (g.has("char")) {
                String s = g.get("char").getAsString();
                if (s.isEmpty()) {
                    return;
                }
                unicode = s.codePointAt(0);
            } else if (g.has("id")) {
                unicode = g.get("id").getAsInt();
            } else {
                return;
            }

            float advance = g.has("advance") ? g.get("advance").getAsFloat() : 0.0f;

            float left = 0f, bottom = 0f, right = 0f, top = 0f;
            if (g.has("planeBounds")) {
                JsonObject plane = g.getAsJsonObject("planeBounds");
                left = floatOf(plane, "left", 0f);
                bottom = floatOf(plane, "bottom", 0f);
                right = floatOf(plane, "right", 0f);
                top = floatOf(plane, "top", 0f);
            }

            float u0 = 0f, v0 = 0f, u1 = 0f, v1 = 0f;
            boolean drawable = false;
            if (g.has("atlasBounds")) {
                JsonObject bounds = g.getAsJsonObject("atlasBounds");
                float x0 = floatOf(bounds, "left", 0f);
                float y0 = floatOf(bounds, "bottom", 0f);
                float x1 = floatOf(bounds, "right", 0f);
                float y1 = floatOf(bounds, "top", 0f);
                if (x1 > x0 && y1 > y0) {
                    u0 = x0 / atlasWidth;
                    u1 = x1 / atlasWidth;
                    // Метрики json считаются снизу вверх, а строка текстуры — сверху вниз
                    if (yOriginBottom) {
                        v0 = 1.0f - y1 / atlasHeight;
                        v1 = 1.0f - y0 / atlasHeight;
                    } else {
                        v0 = y0 / atlasHeight;
                        v1 = y1 / atlasHeight;
                    }
                    drawable = true;
                }
            }

            glyphs.put(unicode, new Glyph(left, right, top, bottom, advance, u0, v0, u1, v1, drawable));
        }

        float ascent(float size) {
            return ascender * size;
        }

        float lineHeight(float size) {
            return lineHeightRatio * size;
        }

        float missingAdvance() {
            Glyph fallback = glyphs.get((int) '?');
            return fallback != null ? fallback.advance : emSize * 0.5f;
        }

        Glyph glyph(int codePoint) {
            Glyph glyph = glyphs.get(codePoint);
            if (glyph != null) {
                return glyph;
            }
            // Кириллица/латиница есть не везде — подменяем '?', иначе глиф молча пропадёт
            return codePoint < 0x0400 ? glyphs.get((int) '?') : null;
        }

        float kerning(int left, int right) {
            Float value = kerning.get(pairKey(left, right));
            return value == null ? 0.0f : value;
        }

        float width(String text, float size) {
            float width = 0.0f;
            int previous = -1;
            int index = 0;
            while (index < text.length()) {
                int codePoint = text.codePointAt(index);
                index += Character.charCount(codePoint);
                if (codePoint == '\n') {
                    continue;
                }
                if (previous != -1) {
                    width += kerning(previous, codePoint) * size;
                }
                Glyph glyph = glyph(codePoint);
                width += (glyph == null ? missingAdvance() : glyph.advance) * size;
                previous = codePoint;
            }
            return width;
        }

        Identifier atlasTexture() {
            return textureId;
        }

        String name() {
            return key;
        }

        private static long pairKey(int left, int right) {
            return ((long) left << 32) | (right & 0xFFFFFFFFL);
        }

        private static float floatOf(JsonObject object, String field, float fallback) {
            return object.has(field) ? object.get(field).getAsFloat() : fallback;
        }
    }

    // ----------------------------------------------------------------- layout

    /** Четырёхугольник глифа в локальных координатах + UV в атласе. */
    public record Quad(float x0, float y0, float x1, float y1,
                       float u0, float v0, float u1, float v1, int codePoint) {}

    public record Layout(List<Quad> quads, float width, float height, GpuTextureView atlas) {

        public static final Layout EMPTY = new Layout(List.of(), 0.0f, 0.0f, null);

        public boolean empty() {
            return quads.isEmpty() || atlas == null || height <= 0.0f;
        }
    }
}

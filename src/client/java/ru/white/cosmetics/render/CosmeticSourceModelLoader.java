package ru.white.cosmetics.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.white.cosmetics.CosmeticCategory;
import ru.white.cosmetics.model.GeoModel;
import ru.white.cosmetics.model.GeoModelParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmeticSourceModelLoader {
    private static final Map<String, CosmeticSourceModel> CACHE = new ConcurrentHashMap<>();

    public static CosmeticSourceModel get(CosmeticCategory category, int index) {
        String key = category.name() + "_" + index;
        return CACHE.computeIfAbsent(key, k -> load(category, index));
    }

    private static CosmeticSourceModel load(CosmeticCategory category, int index) {
        int rawId = CosmeticModelLoader.getRawId(category, index);

        try (InputStream in = CosmeticSourceModelLoader.class.getClassLoader()
                .getResourceAsStream(CosmeticModelLoader.getModelPath(category, index))) {
            if (in == null) return null;

            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject modelObj = root.has("model") && root.get("model").isJsonObject() ? root.getAsJsonObject("model") : root;
            GeoModel geo = GeoModelParser.parse(modelObj.toString());
            if (geo == null) return null;

            String name = root.has("name") ? root.get("name").getAsString() : (category.getName() + " #" + index);
            int pos = root.has("pos") ? root.get("pos").getAsInt() : 1;
            float scale = root.has("scale") ? root.get("scale").getAsFloat() : 1.0F;
            float x = root.has("x") ? root.get("x").getAsFloat() : 0.0F;
            float y = root.has("y") ? root.get("y").getAsFloat() : 0.0F;
            float z = root.has("z") ? root.get("z").getAsFloat() : 0.0F;
            float yaw = root.has("yaw") ? root.get("yaw").getAsFloat() : 0.0F;
            float pitch = root.has("pitch") ? root.get("pitch").getAsFloat() : 0.0F;
            float roll = root.has("roll") ? root.get("roll").getAsFloat() : 0.0F;
            int[] frames = parseTextureAnimation(root);

            return new CosmeticSourceModel(rawId, name, pos, scale, x, y, z, yaw, pitch, roll, geo,
                    parseAnimations(root), frames[0], frames[1], frames[2], frames[3]);
        } catch (Exception e) {
            return null;
        }
    }

    /** Возвращает {frameCount, frameHeight, totalHeight, frameTime}; нули — если анимации текстуры нет. */
    private static int[] parseTextureAnimation(JsonObject root) {
        if (!root.has("textureAnimation") || !root.get("textureAnimation").isJsonObject()) {
            return new int[]{0, 0, 0, 0};
        }
        JsonObject anim = root.getAsJsonObject("textureAnimation");
        int count = anim.has("frameCount") ? anim.get("frameCount").getAsInt() : 0;
        int height = anim.has("frameHeight") ? anim.get("frameHeight").getAsInt() : 0;
        int total = anim.has("totalHeight") ? anim.get("totalHeight").getAsInt() : 0;
        int time = anim.has("frameTime") ? anim.get("frameTime").getAsInt() : 3;
        if (count <= 1 || height <= 0 || total <= height) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{count, height, total, Math.max(1, time)};
    }

    private static Map<String, CosmeticSourceModel.AnimationData> parseAnimations(JsonObject root) {
        Map<String, CosmeticSourceModel.AnimationData> result = new LinkedHashMap<>();
        if (!root.has("animation") || !root.get("animation").isJsonObject()) return result;

        JsonObject animRoot = root.getAsJsonObject("animation");
        if (!animRoot.has("animations") || !animRoot.get("animations").isJsonObject()) return result;

        JsonObject animations = animRoot.getAsJsonObject("animations");
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                JsonObject animation = entry.getValue().getAsJsonObject();
                boolean loop = !animation.has("loop") || animation.get("loop").getAsBoolean();
                float length = animation.has("animation_length") ? animation.get("animation_length").getAsFloat() : 1.0F;
                Map<String, CosmeticSourceModel.BoneAnimationData> bones = new LinkedHashMap<>();

                if (animation.has("bones") && animation.get("bones").isJsonObject()) {
                    JsonObject boneObj = animation.getAsJsonObject("bones");
                    for (Map.Entry<String, JsonElement> bEntry : boneObj.entrySet()) {
                        if (bEntry.getValue().isJsonObject()) {
                            JsonObject b = bEntry.getValue().getAsJsonObject();
                            bones.put(
                                    bEntry.getKey(),
                                    new CosmeticSourceModel.BoneAnimationData(
                                            parseChannel(b.get("rotation")),
                                            parseChannel(b.get("position")),
                                            parseChannel(b.get("scale"))
                                    )
                            );
                        }
                    }
                }

                result.put(entry.getKey(), new CosmeticSourceModel.AnimationData(entry.getKey(), loop, length, bones));
            }
        }

        return result;
    }

    private static CosmeticSourceModel.Channel parseChannel(JsonElement element) {
        CosmeticSourceModel.Channel channel = new CosmeticSourceModel.Channel();
        if (element == null || element.isJsonNull()) return channel;

        if (element.isJsonArray() || element.isJsonPrimitive()) {
            channel.constant = vector(element, new float[]{0.0F, 0.0F, 0.0F});
            return channel;
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                try {
                    float timestamp = Float.parseFloat(e.getKey());
                    channel.keys.put(timestamp, keyVector(e.getValue(), new float[]{0.0F, 0.0F, 0.0F}));
                } catch (Exception ignored) {}
            }
        }

        return channel;
    }

    private static float[] keyVector(JsonElement value, float[] fallback) {
        if (value != null && value.isJsonObject()) {
            JsonObject o = value.getAsJsonObject();
            if (o.has("post")) return vector(o.get("post"), fallback);
            if (o.has("pre")) return vector(o.get("pre"), fallback);
            if (o.has("vector")) return vector(o.get("vector"), fallback);
        }
        return vector(value, fallback);
    }

    private static float[] vector(JsonElement element, float[] fallback) {
        if (element == null || element.isJsonNull()) return fallback.clone();
        if (element.isJsonPrimitive()) {
            try {
                float n = element.getAsFloat();
                return new float[]{n, n, n};
            } catch (Exception e) {
                return fallback.clone();
            }
        }
        if (!element.isJsonArray()) return fallback.clone();
        JsonArray arr = element.getAsJsonArray();
        float[] result = fallback.clone();
        for (int i = 0; i < Math.min(3, arr.size()); i++) {
            try {
                result[i] = arr.get(i).getAsFloat();
            } catch (Exception ignored) {}
        }
        return result;
    }
}

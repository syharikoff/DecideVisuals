package ru.white.cosmetics.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import ru.white.cosmetics.CosmeticCategory;
import ru.white.cosmetics.model.GeoModel;
import ru.white.cosmetics.model.GeoModelParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmeticModelLoader {
    private static final Map<String, CosmeticModelData> CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, CosmeticCapeData> CAPES = new ConcurrentHashMap<>();
    private static final int[] WINGS_IDS = {25, 61, 62, 63, 64, 65, 82, 86, 91, 99, 123, 124, 136};
    private static final int[] CAPE_IDS = {23, 58, 59, 60, 76, 77, 79, 83, 87, 100, 101, 102, 122, 130, 131};
    private static final int[] HAT_IDS = {48, 49, 50, 53, 57, 80, 85, 88, 92, 96, 97, 98, 125};
    private static final int[] CLOTHES_IDS = {36, 37, 38, 39, 40, 41, 42, 43, 44, 95, 137};
    private static final int[] PET_IDS = {45, 46, 47, 81, 84, 90, 93, 126, 132, 133, 134, 135};
    private static final int[] GRAFFITI_IDS = {127, 128, 129, 138, 139, 140, 141, 142, 143, 144, 145, 146, 148, 149, 150, 151, 152, 153, 154, 155};

    public static int getRawId(CosmeticCategory category, int index) {
        int idx = index - 1;
        int[] table = idsFor(category);
        if (table == null) return index;
        return idx >= 0 && idx < table.length ? table[idx] : index;
    }

    private static int[] idsFor(CosmeticCategory category) {
        switch (category) {
            case WINGS: return WINGS_IDS;
            case CAPES: return CAPE_IDS;
            case HATS: return HAT_IDS;
            case CLOTHES: return CLOTHES_IDS;
            case PETS: return PET_IDS;
            case GRAFFITI: return GRAFFITI_IDS;
            default: return null;
        }
    }

    public static String getModelFolder(CosmeticCategory category) {
        switch (category) {
            case WINGS: return "wings";
            case CAPES: return "cape";
            case HATS: return "hat";
            case CLOTHES: return "bodywear";
            case PETS: return "pet";
            case GRAFFITI: return "graffiti";
            default: return "wings";
        }
    }

    public static String getModelPath(CosmeticCategory category, int index) {
        return "assets/pulsecosmetics/cosmetics/" + getModelFolder(category) + "/" + getRawId(category, index) + "/model.json";
    }

    /** Текстура косметики. У плащей это всегда ассет в pulsecosmetics, а не превью из меню. */
    public static Identifier getTextureId(CosmeticCategory category, int index) {
        return Identifier.of("pulsecosmetics",
                "cosmetics/" + getModelFolder(category) + "/" + getRawId(category, index) + "/texture.png");
    }

    public static CosmeticModelData get(CosmeticCategory category, int index) {
        String key = category.name() + "_" + index;
        return CACHE.computeIfAbsent(key, k -> load(category, index));
    }

    /** Плащ: геометрии нет, нужны только текстура, её размер и анимация кадров. */
    public static CosmeticCapeData getCape(int index) {
        return CAPES.computeIfAbsent(index, CosmeticModelLoader::loadCape);
    }

    /** Прогрев плаща: описание плюс границы текстуры (их вычисляет рендер, а декодировать 2048x1024 на первом кадре нельзя). */
    public static void warmCape(int index) {
        CosmeticCapeData cape = getCape(index);
        if (cape != null) {
            CosmeticTextureInfo.contentBounds(cape.getTexture());
        }
    }

    private static CosmeticModelData load(CosmeticCategory category, int index) {
        int rawId = getRawId(category, index);
        Identifier textureId = getTextureId(category, index);

        try (InputStream in = CosmeticModelLoader.class.getClassLoader().getResourceAsStream(getModelPath(category, index))) {
            if (in == null) {
                return null;
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject modelObj = root.has("model") && root.get("model").isJsonObject() ? root.getAsJsonObject("model") : root;

            GeoModel geo = GeoModelParser.parse(modelObj.toString());
            if (geo == null) {
                return null;
            }

            String name = root.has("name") ? root.get("name").getAsString() : (category.getName() + " #" + index);
            CosmeticModelData data = new CosmeticModelData(rawId, name, geo, textureId);

            if (root.has("scale")) data.setScale(root.get("scale").getAsFloat());
            if (root.has("x")) data.setX(root.get("x").getAsFloat());
            if (root.has("y")) data.setY(root.get("y").getAsFloat());
            if (root.has("z")) data.setZ(root.get("z").getAsFloat());
            if (root.has("yaw")) data.setYaw(root.get("yaw").getAsFloat());
            if (root.has("pitch")) data.setPitch(root.get("pitch").getAsFloat());
            if (root.has("roll")) data.setRoll(root.get("roll").getAsFloat());
            if (root.has("previewScale")) data.setPreviewScale(root.get("previewScale").getAsFloat());
            if (root.has("previewY")) data.setPreviewY(root.get("previewY").getAsFloat());

            return data;
        } catch (Exception e) {
            return null;
        }
    }

    private static CosmeticCapeData loadCape(int index) {
        int rawId = getRawId(CosmeticCategory.CAPES, index);
        Identifier texture = getTextureId(CosmeticCategory.CAPES, index);
        int[] size = CosmeticTextureInfo.size(texture);

        int frameCount = 0;
        int frameWidth = 0;
        int frameHeight = 0;
        int frameTime = 3;
        String name = "Плащ #" + index;

        // model.json у плаща опционален: у части плащей его нет вовсе
        try (InputStream in = CosmeticModelLoader.class.getClassLoader()
                .getResourceAsStream(getModelPath(CosmeticCategory.CAPES, index))) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("name")) {
                    name = root.get("name").getAsString();
                }
                if (root.has("capeAnimation") && root.get("capeAnimation").isJsonObject()) {
                    JsonObject anim = root.getAsJsonObject("capeAnimation");
                    if (anim.has("frameCount")) frameCount = anim.get("frameCount").getAsInt();
                    if (anim.has("frameWidth")) frameWidth = anim.get("frameWidth").getAsInt();
                    if (anim.has("frameHeight")) frameHeight = anim.get("frameHeight").getAsInt();
                    if (anim.has("frameTime")) frameTime = anim.get("frameTime").getAsInt();
                }
            }
        } catch (Exception ignored) {
        }

        if (frameCount <= 1 || frameHeight <= 0) {
            frameCount = 0;
            frameHeight = 0;
            frameWidth = 0;
        }

        return new CosmeticCapeData(rawId, name, texture, size[0], size[1], frameCount, frameWidth, frameHeight,
                Math.max(1, frameTime));
    }
}

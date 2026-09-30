package ru.white.utils.render.killeffect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BBModel {
    public final String modelId;
    public final String name;
    public final float width;
    public final float height;
    public final List<Texture> textures;
    public final List<Cube> cubes;
    public final List<Bone> roots;
    public final List<BBAnimation> animations;

    private BBModel(String modelId, String name, float width, float height,
                    List<Texture> textures, List<Cube> cubes,
                    List<Bone> roots, List<BBAnimation> animations) {
        this.modelId = modelId;
        this.name = name;
        this.width = width;
        this.height = height;
        this.textures = textures;
        this.cubes = cubes;
        this.roots = roots;
        this.animations = animations;
    }

    public static BBModel load(String modelId, String namespace, String folder) {
        String resource = "/assets/" + namespace + "/" + folder + "/" + modelId + ".bbmodel";
        try (InputStream stream = BBModel.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing model resource: " + resource);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return BBModel.parse(root, modelId, namespace, folder);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load bbmodel " + modelId, e);
        }
    }

    public static BBModel parse(JsonObject root, String modelId, String namespace, String folder) {
        String name = text(root, "name", modelId);
        JsonObject resolution = root.getAsJsonObject("resolution");
        float resWidth = number(resolution, "width", 16.0f);
        float resHeight = number(resolution, "height", 16.0f);
        String textureFolder = folder.startsWith("models/") ? folder.substring("models/".length()) : folder;

        ArrayList<Texture> textures = new ArrayList<>();
        JsonArray textureEntries = root.getAsJsonArray("textures");
        if (textureEntries != null) {
            int index = 0;
            for (JsonElement element : textureEntries) {
                JsonObject t = element.getAsJsonObject();
                float w = number(t, "width", resWidth);
                float h = number(t, "height", resHeight);
                float uvW = number(t, "uv_width", w);
                float uvH = number(t, "uv_height", h);
                Identifier fileId = Identifier.of(namespace, "textures/" + textureFolder + "/" + modelId + "_" + index + ".png");
                textures.add(new Texture(fileId, text(t, "name", ""), text(t, "path", fileId.toString()), w, h, uvW, uvH));
                index++;
            }
        }
        if (textures.isEmpty()) {
            textures.add(new Texture(Identifier.of(namespace, "textures/" + textureFolder + "/" + modelId + ".png"), "", "", resWidth, resHeight, resWidth, resHeight));
        }

        HashMap<String, Cube> cubeMap = new HashMap<>();
        ArrayList<Cube> cubes = new ArrayList<>();
        JsonArray elements = root.getAsJsonArray("elements");
        if (elements != null) {
            for (JsonElement element : elements) {
                JsonObject o = element.getAsJsonObject();
                if (!"cube".equals(text(o, "type", "cube"))) continue;
                Cube cube = parseCube(o);
                cubeMap.put(cube.uuid(), cube);
                cubes.add(cube);
            }
        }

        ArrayList<Bone> roots = new ArrayList<>();
        JsonArray outliner = root.getAsJsonArray("outliner");
        if (outliner != null) {
            for (JsonElement element : outliner) {
                if (!element.isJsonObject()) continue;
                roots.add(parseBone(element.getAsJsonObject(), cubeMap));
            }
        }

        ArrayList<String> referenced = new ArrayList<>();
        for (Bone b : roots) collectReferenced(b, referenced);

        ArrayList<Cube> loose = new ArrayList<>();
        for (Cube cube : cubes) {
            if (!referenced.contains(cube.uuid())) loose.add(cube);
        }
        if (!loose.isEmpty()) {
            roots.add(new Bone("loose", "loose", Vec.ZERO, Vec.ZERO, loose, List.of(), true));
        }

        ArrayList<BBAnimation> animations = new ArrayList<>();
        JsonArray animationEntries = root.getAsJsonArray("animations");
        if (animationEntries != null) {
            for (JsonElement element : animationEntries) {
                animations.add(BBAnimation.parse(element.getAsJsonObject()));
            }
        }
        return new BBModel(modelId, name, resWidth, resHeight, textures, cubes, roots, animations);
    }

    private static void collectReferenced(Bone bone, List<String> out) {
        for (Cube cube : bone.cubes()) out.add(cube.uuid());
        for (Bone child : bone.children()) collectReferenced(child, out);
    }

    private static Cube parseCube(JsonObject o) {
        Vec from = vector(o.getAsJsonArray("from"));
        Vec to = vector(o.getAsJsonArray("to"));
        Vec origin = o.has("origin") ? vector(o.getAsJsonArray("origin")) : Vec.ZERO;
        Vec rotation = o.has("rotation") ? negXY(vector(o.getAsJsonArray("rotation"))) : Vec.ZERO;
        ArrayList<Face> faces = new ArrayList<>();
        JsonObject faceObject = o.getAsJsonObject("faces");
        if (faceObject != null) {
            for (Map.Entry<String, JsonElement> entry : faceObject.entrySet()) {
                JsonArray uv;
                JsonObject face = entry.getValue().getAsJsonObject();
                if (!face.has("texture") || face.get("texture").isJsonNull()) continue;
                if ((uv = face.getAsJsonArray("uv")) == null || uv.size() < 4) continue;
                faces.add(new Face(entry.getKey(), uv.get(0).getAsFloat(), uv.get(1).getAsFloat(),
                        uv.get(2).getAsFloat(), uv.get(3).getAsFloat(),
                        (int) number(face, "rotation", 0.0f), face.get("texture").getAsInt()));
            }
        }
        return new Cube(text(o, "uuid", ""), text(o, "name", ""), from, to, origin, rotation, faces);
    }

    private static Bone parseBone(JsonObject source, Map<String, Cube> cubeMap) {
        ArrayList<Cube> ownCubes = new ArrayList<>();
        ArrayList<Bone> children = new ArrayList<>();
        String name = text(source, "name", "bone");
        boolean visible = !source.has("visibility") || source.get("visibility").getAsBoolean();
        JsonArray childElements = source.getAsJsonArray("children");
        if (childElements != null) {
            for (JsonElement child : childElements) {
                if (child.isJsonPrimitive()) {
                    Cube cube = cubeMap.get(child.getAsString());
                    if (cube != null) ownCubes.add(cube);
                } else if (child.isJsonObject()) {
                    children.add(parseBone(child.getAsJsonObject(), cubeMap));
                }
            }
        }
        return new Bone(text(source, "uuid", ""), name,
                vector(source.getAsJsonArray("origin")),
                source.has("rotation") ? negXY(vector(source.getAsJsonArray("rotation"))) : Vec.ZERO,
                ownCubes, children, visible);
    }

    private static Vec negXY(Vec v) {
        return new Vec(-v.x(), -v.y(), v.z());
    }

    private static Vec vector(JsonArray array) {
        if (array == null || array.size() < 3) return Vec.ZERO;
        return new Vec(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    private static float number(JsonObject o, String key, float fallback) {
        if (o == null || !o.has(key)) return fallback;
        try { return o.get(key).getAsFloat(); } catch (RuntimeException ignored) { return fallback; }
    }

    private static String text(JsonObject o, String key, String fallback) {
        return o != null && o.has(key) ? o.get(key).getAsString() : fallback;
    }

    public record Texture(Identifier id, String name, String path, float width, float height, float uvWidth, float uvHeight) {}
    public record Cube(String uuid, String name, Vec from, Vec to, Vec origin, Vec rotation, List<Face> faces) {
        public float width() { return Math.abs(to.x() - from.x()); }
        public float height() { return Math.abs(to.y() - from.y()); }
        public float depth() { return Math.abs(to.z() - from.z()); }
    }
    public record Bone(String uuid, String name, Vec origin, Vec rotation, List<Cube> cubes, List<Bone> children, boolean visible) {}
    public record Vec(float x, float y, float z) {
        public static final Vec ZERO = new Vec(0, 0, 0);
        public static final Vec ONE = new Vec(1, 1, 1);
        public Vec add(Vec o) { return new Vec(x + o.x, y + o.y, z + o.z); }
        public Vec sub(Vec o) { return new Vec(x - o.x, y - o.y, z - o.z); }
        public static Vec lerp(Vec a, Vec b, float t) {
            return new Vec(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
        }
    }
    public record Face(String direction, float u1, float v1, float u2, float v2, int rotation, int texture) {}
}

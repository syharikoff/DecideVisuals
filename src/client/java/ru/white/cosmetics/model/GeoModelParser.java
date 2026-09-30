package ru.white.cosmetics.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;

public class GeoModelParser {
    public static GeoModel parse(String jsonString) {
        try {
            JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
            return parseModel(root);
        } catch (Exception e) {
            System.err.println("[NightixCosmetics] Failed to parse model: " + e);
            return null;
        }
    }

    public static GeoModel parseModel(JsonObject root) {
        JsonArray geomArray = root.getAsJsonArray("minecraft:geometry");
        if (geomArray == null || geomArray.isEmpty()) {
            return null;
        }
        JsonObject geom = geomArray.get(0).getAsJsonObject();
        JsonObject desc = geom.getAsJsonObject("description");
        int texW = desc != null && desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
        int texH = desc != null && desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;

        GeoModel model = new GeoModel();
        model.setTextureWidth(texW);
        model.setTextureHeight(texH);

        JsonArray bonesArray = geom.getAsJsonArray("bones");
        if (bonesArray != null) {
            HashMap<String, GeoBone> boneMap = new HashMap<>();
            for (JsonElement elem : bonesArray) {
                JsonObject boneObj = elem.getAsJsonObject();
                GeoBone bone = parseBone(boneObj, texW, texH);
                boneMap.put(bone.getName(), bone);
            }

            for (JsonElement elem : bonesArray) {
                JsonObject boneObj = elem.getAsJsonObject();
                String name = boneObj.get("name").getAsString();
                GeoBone bone = boneMap.get(name);
                if (boneObj.has("parent")) {
                    String parentName = boneObj.get("parent").getAsString();
                    GeoBone parent = boneMap.get(parentName);
                    if (parent != null) {
                        parent.getChildBones().add(bone);
                        bone.setParent(parent);
                    }
                } else {
                    model.getTopLevelBones().add(bone);
                }
            }
        }

        return model;
    }

    private static GeoBone parseBone(JsonObject obj, int texW, int texH) {
        String name = obj.get("name").getAsString();
        GeoBone bone = new GeoBone(name);

        if (obj.has("pivot")) {
            JsonArray p = obj.getAsJsonArray("pivot");
            bone.setRotationPointX(-p.get(0).getAsFloat());
            bone.setRotationPointY(p.get(1).getAsFloat());
            bone.setRotationPointZ(p.get(2).getAsFloat());
        }

        if (obj.has("rotation")) {
            JsonArray r = obj.getAsJsonArray("rotation");
            bone.setRotateX((float) Math.toRadians(-r.get(0).getAsFloat()));
            bone.setRotateY((float) Math.toRadians(-r.get(1).getAsFloat()));
            bone.setRotateZ((float) Math.toRadians(r.get(2).getAsFloat()));
        }

        if (obj.has("cubes")) {
            for (JsonElement c : obj.getAsJsonArray("cubes")) {
                JsonObject cObj = c.getAsJsonObject();
                GeoCube cube = parseCube(cObj, texW, texH);
                bone.getChildCubes().add(cube);
            }
        }

        return bone;
    }

    private static GeoCube parseCube(JsonObject obj, int texW, int texH) {
        float[] origin = parseFloatArray(obj, "origin", new float[]{0.0F, 0.0F, 0.0F});
        float[] size = parseFloatArray(obj, "size", new float[]{1.0F, 1.0F, 1.0F});
        float[] pivot = parseFloatArray(obj, "pivot", origin.clone());
        float[] rotation = parseFloatArray(obj, "rotation", new float[]{0.0F, 0.0F, 0.0F});
        float inflate = obj.has("inflate") ? obj.get("inflate").getAsFloat() : 0.0F;
        boolean mirror = obj.has("mirror") && obj.get("mirror").getAsBoolean();

        GeoCube cube = new GeoCube(size[0], size[1], size[2]);
        cube.setPivot(new Vec3F(-pivot[0], pivot[1], pivot[2]));
        cube.setRotation(new Vec3F(
                (float) Math.toRadians(-rotation[0]),
                (float) Math.toRadians(-rotation[1]),
                (float) Math.toRadians(rotation[2])
        ));
        cube.setInflate(inflate);
        cube.setMirror(mirror);

        buildCubeQuads(cube, origin, size, inflate, mirror, obj, texW, texH);
        return cube;
    }

    private static void buildCubeQuads(GeoCube cube, float[] origin, float[] size, float inflate, boolean mirror, JsonObject obj, int texW, int texH) {
        float ox = origin[0] - inflate;
        float oy = origin[1] - inflate;
        float oz = origin[2] - inflate;
        float sx = size[0] + inflate * 2.0F;
        float sy = size[1] + inflate * 2.0F;
        float sz = size[2] + inflate * 2.0F;

        float u = 0.0F;
        float v = 0.0F;
        boolean perFace = false;
        JsonObject uvObj = null;

        if (obj.has("uv")) {
            JsonElement uvElem = obj.get("uv");
            if (uvElem.isJsonArray()) {
                JsonArray arr = uvElem.getAsJsonArray();
                u = arr.get(0).getAsFloat();
                v = arr.get(1).getAsFloat();
            } else if (uvElem.isJsonObject()) {
                perFace = true;
                uvObj = uvElem.getAsJsonObject();
            }
        }

        float minX = -(ox + sx) / 16.0F;
        float minY = oy / 16.0F;
        float minZ = oz / 16.0F;
        float maxX = -ox / 16.0F;
        float maxY = (oy + sy) / 16.0F;
        float maxZ = (oz + sz) / 16.0F;

        if (perFace && uvObj != null) {
            cube.getQuads()[0] = buildQuadPerFace(uvObj, "west", minX, minY, minZ, minX, maxY, maxZ, -1.0F, 0.0F, 0.0F, texW, texH);
            cube.getQuads()[1] = buildQuadPerFace(uvObj, "east", maxX, minY, minZ, maxX, maxY, maxZ, 1.0F, 0.0F, 0.0F, texW, texH);
            cube.getQuads()[2] = buildQuadPerFace(uvObj, "down", minX, minY, minZ, maxX, minY, maxZ, 0.0F, -1.0F, 0.0F, texW, texH);
            cube.getQuads()[3] = buildQuadPerFace(uvObj, "up", minX, maxY, minZ, maxX, maxY, maxZ, 0.0F, 1.0F, 0.0F, texW, texH);
            cube.getQuads()[4] = buildQuadPerFace(uvObj, "north", minX, minY, minZ, maxX, maxY, minZ, 0.0F, 0.0F, -1.0F, texW, texH);
            cube.getQuads()[5] = buildQuadPerFace(uvObj, "south", minX, minY, maxZ, maxX, maxY, maxZ, 0.0F, 0.0F, 1.0F, texW, texH);
        } else {
            cube.getQuads()[0] = buildQuadBox(minX, minY, minZ, minX, maxY, maxZ, -1.0F, 0.0F, 0.0F, u, v, sz, sy, sx, texW, texH, "west");
            cube.getQuads()[1] = buildQuadBox(maxX, minY, minZ, maxX, maxY, maxZ, 1.0F, 0.0F, 0.0F, u, v, sz, sy, sx, texW, texH, "east");
            cube.getQuads()[2] = buildQuadBox(minX, minY, minZ, maxX, minY, maxZ, 0.0F, -1.0F, 0.0F, u, v, sz, sy, sx, texW, texH, "down");
            cube.getQuads()[3] = buildQuadBox(minX, maxY, minZ, maxX, maxY, maxZ, 0.0F, 1.0F, 0.0F, u, v, sz, sy, sx, texW, texH, "up");
            cube.getQuads()[4] = buildQuadBox(minX, minY, minZ, maxX, maxY, minZ, 0.0F, 0.0F, -1.0F, u, v, sz, sy, sx, texW, texH, "north");
            cube.getQuads()[5] = buildQuadBox(minX, minY, maxZ, maxX, maxY, maxZ, 0.0F, 0.0F, 1.0F, u, v, sz, sy, sx, texW, texH, "south");
        }
    }

    private static GeoQuad buildQuadPerFace(JsonObject obj, String face, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float nx, float ny, float nz, int texW, int texH) {
        if (!obj.has(face)) return null;
        JsonObject faceObj = obj.getAsJsonObject(face);
        JsonArray uvArr = faceObj.getAsJsonArray("uv");
        JsonArray sizeArr = faceObj.getAsJsonArray("uv_size");
        float u = uvArr.get(0).getAsFloat() / texW;
        float v = uvArr.get(1).getAsFloat() / texH;
        float su = sizeArr.get(0).getAsFloat() / texW;
        float sv = sizeArr.get(1).getAsFloat() / texH;
        GeoVertex[] verts = buildFaceVertices(face, minX, minY, minZ, maxX, maxY, maxZ, u, v, su, sv);
        return new GeoQuad(verts, nx, ny, nz);
    }

    private static GeoQuad buildQuadBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float nx, float ny, float nz, float u, float v, float sz, float sy, float sx, float texW, float texH, String face) {
        float faceU, faceV, faceW, faceH;
        switch (face) {
            case "north":
                faceU = (u + sz + sx) / texW;
                faceV = (v + sz) / texH;
                faceW = sx / texW;
                faceH = sy / texH;
                break;
            case "south":
                faceU = (u + sz + sx + sz) / texW;
                faceV = (v + sz) / texH;
                faceW = sx / texW;
                faceH = sy / texH;
                break;
            case "east":
                faceU = u / texW;
                faceV = (v + sz) / texH;
                faceW = sz / texW;
                faceH = sy / texH;
                break;
            case "west":
                faceU = (u + sz + sx) / texW;
                faceV = (v + sz) / texH;
                faceW = sz / texW;
                faceH = sy / texH;
                break;
            case "up":
                faceU = (u + sz) / texW;
                faceV = v / texH;
                faceW = sx / texW;
                faceH = sz / texH;
                break;
            case "down":
                faceU = (u + sz + sx) / texW;
                faceV = v / texH;
                faceW = sx / texW;
                faceH = sz / texH;
                break;
            default:
                faceU = 0; faceV = 0; faceW = 0; faceH = 0;
        }

        GeoVertex[] verts = buildFaceVertices(face, minX, minY, minZ, maxX, maxY, maxZ, faceU, faceV, faceW, faceH);
        return new GeoQuad(verts, nx, ny, nz);
    }

    private static GeoVertex[] buildFaceVertices(String face, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float u, float v, float su, float sv) {
        GeoVertex[] verts = new GeoVertex[4];
        float maxU = u + su;
        float maxV = v + sv;

        switch (face) {
            case "north":
                verts[0] = new GeoVertex(maxX, maxY, minZ, u, v);
                verts[1] = new GeoVertex(minX, maxY, minZ, maxU, v);
                verts[2] = new GeoVertex(minX, minY, minZ, maxU, maxV);
                verts[3] = new GeoVertex(maxX, minY, minZ, u, maxV);
                break;
            case "south":
                verts[0] = new GeoVertex(minX, maxY, maxZ, u, v);
                verts[1] = new GeoVertex(maxX, maxY, maxZ, maxU, v);
                verts[2] = new GeoVertex(maxX, minY, maxZ, maxU, maxV);
                verts[3] = new GeoVertex(minX, minY, maxZ, u, maxV);
                break;
            case "east":
                verts[0] = new GeoVertex(maxX, maxY, maxZ, u, v);
                verts[1] = new GeoVertex(maxX, maxY, minZ, maxU, v);
                verts[2] = new GeoVertex(maxX, minY, minZ, maxU, maxV);
                verts[3] = new GeoVertex(maxX, minY, maxZ, u, maxV);
                break;
            case "west":
                verts[0] = new GeoVertex(minX, maxY, minZ, u, v);
                verts[1] = new GeoVertex(minX, maxY, maxZ, maxU, v);
                verts[2] = new GeoVertex(minX, minY, maxZ, maxU, maxV);
                verts[3] = new GeoVertex(minX, minY, minZ, u, maxV);
                break;
            case "up":
                verts[0] = new GeoVertex(minX, maxY, minZ, u, v);
                verts[1] = new GeoVertex(minX, maxY, maxZ, u, maxV);
                verts[2] = new GeoVertex(maxX, maxY, maxZ, maxU, maxV);
                verts[3] = new GeoVertex(maxX, maxY, minZ, maxU, v);
                break;
            case "down":
                verts[0] = new GeoVertex(maxX, minY, minZ, u, v);
                verts[1] = new GeoVertex(maxX, minY, maxZ, u, maxV);
                verts[2] = new GeoVertex(minX, minY, maxZ, maxU, maxV);
                verts[3] = new GeoVertex(minX, minY, minZ, maxU, v);
                break;
        }

        return verts;
    }

    private static float[] parseFloatArray(JsonObject obj, String name, float[] def) {
        if (!obj.has(name)) return def;
        JsonArray arr = obj.getAsJsonArray(name);
        float[] res = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            res[i] = arr.get(i).getAsFloat();
        }
        return res;
    }
}

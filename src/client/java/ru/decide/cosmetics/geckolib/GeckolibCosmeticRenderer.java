/*
 * Decompiled with CFR 0.152.
 */
package ru.decide.cosmetics.geckolib;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ru.decide.cosmetics.CosmeticManager;
import ru.decide.cosmetics.geckolib.GeckoRenderHelper;
import ru.decide.cosmetics.geckolib.GeckolibModelParser;
import ru.decide.cosmetics.geo.GeoBone;
import ru.decide.cosmetics.geo.GeoCube;
import ru.decide.cosmetics.geo.GeoModel;
import ru.decide.cosmetics.geo.GeoQuad;
import ru.decide.cosmetics.geo.GeoVertex;
import ru.decide.cosmetics.model.CosmeticModel;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Environment(value=EnvType.CLIENT)
public class GeckolibCosmeticRenderer {
    private static GeckolibCosmeticRenderer instance;
    private final Map<Integer, GeoModel> modelCache = new ConcurrentHashMap<Integer, GeoModel>();
    private final Map<Integer, CosmeticAnimationData> animationCache = new ConcurrentHashMap<Integer, CosmeticAnimationData>();
    private final Set<Integer> noAnimationSet = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Long> animationStartTime = new ConcurrentHashMap<Integer, Long>();
    private final Map<Integer, Map<String, float[]>> initialBoneTransforms = new ConcurrentHashMap<Integer, Map<String, float[]>>();
    private final GeckolibModelParser modelParser = new GeckolibModelParser();

    public static GeckolibCosmeticRenderer getInstance() {
        if (instance == null) {
            instance = new GeckolibCosmeticRenderer();
        }
        return instance;
    }

    public void renderCosmetic(CosmeticModel cosmetic, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        GeoModel model;
        if (cosmetic != null && cosmetic.getTextureId() != null && (model = this.getOrParseModel(cosmetic)) != null) {
            this.renderParsed(cosmetic, model, matrices, RenderLayers.entityCutoutNoCull(cosmetic.getTextureId()), consumers, light);
        }
    }

    /**
     * То же, но со своим слоем рендера: в GUI нужен слой без depth-test,
     * иначе глубина мира закрывает превью.
     */
    public void renderCosmetic(CosmeticModel cosmetic, MatrixStack matrices, VertexConsumerProvider consumers,
                               int light, RenderLayer layer) {
        GeoModel model;
        if (cosmetic != null && cosmetic.getTextureId() != null && (model = this.getOrParseModel(cosmetic)) != null) {
            this.renderParsed(cosmetic, model, matrices, layer, consumers, light);
        }
    }
    /**
     * Применяет анимацию модели (или сбрасывает в исходную позу).
     * Нужна превью в GUI, где геометрия считается на CPU, без VertexConsumer.
     */
    public void applyPose(CosmeticModel cosmetic, GeoModel model) {
        CosmeticAnimationData anim = this.getOrParseAnimation(cosmetic);
        if (anim != null) {
            this.applyAnimations(model, anim, cosmetic.getId());
        } else {
            this.resetToInitialPose(model, cosmetic.getId());
        }
    }


    private void renderParsed(CosmeticModel cosmetic, GeoModel model, MatrixStack matrices, RenderLayer layer,
                              VertexConsumerProvider consumers, int light) {
        CosmeticAnimationData anim = this.getOrParseAnimation(cosmetic);
        if (anim != null) {
            this.applyAnimations(model, anim, cosmetic.getId());
        } else {
            this.resetToInitialPose(model, cosmetic.getId());
        }
        VertexConsumer buffer = consumers.getBuffer(layer);
        for (GeoBone bone : model.topLevelBones) {
            this.renderBone(bone, matrices, buffer, light, OverlayTexture.DEFAULT_UV, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    public void renderCosmetic(CosmeticModel cosmetic, MatrixStack matrices, VertexConsumer vertexConsumer, int light) {
        GeoModel model;
        if (cosmetic != null && cosmetic.getTextureId() != null && vertexConsumer != null && (model = this.getOrParseModel(cosmetic)) != null) {
            CosmeticAnimationData anim = this.getOrParseAnimation(cosmetic);
            if (anim != null) {
                this.applyAnimations(model, anim, cosmetic.getId());
            } else {
                this.resetToInitialPose(model, cosmetic.getId());
            }
            for (GeoBone bone : model.topLevelBones) {
                this.renderBone(bone, matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1.0f, 1.0f, 1.0f, 1.0f);
            }
        }
    }

    public void renderCapeCuboid(MatrixStack matrices, VertexConsumer buffer, int light, Identifier texture) {
        Matrix4f mat = matrices.peek().getPositionMatrix();
        int r = 255;
        int g = 255;
        int b = 255;
        int a = 255;
        int ov = OverlayTexture.DEFAULT_UV;
        CosmeticManager.CapeAnimationInfo anim = CosmeticManager.getInstance().getCapeAnimation(texture);
        int frame = anim.getCurrentFrame();
        float uLeft0 = anim.getU(0.0f);
        float uLeft1 = anim.getU(1.0f);
        float uBack0 = anim.getU(1.0f);
        float uBack1 = anim.getU(11.0f);
        float uRight0 = anim.getU(11.0f);
        float uRight1 = anim.getU(12.0f);
        float uFront0 = anim.getU(12.0f);
        float uFront1 = anim.getU(22.0f);
        float uTop0 = anim.getU(1.0f);
        float uTop1 = anim.getU(11.0f);
        float uBot0 = anim.getU(11.0f);
        float uBot1 = anim.getU(21.0f);
        float vTop0 = anim.getV(0.0f, frame);
        float vTop1 = anim.getV(1.0f, frame);
        float vBody0 = anim.getV(1.0f, frame);
        float vBody1 = anim.getV(17.0f, frame);
        buffer.vertex(mat, -5.0f, -8.0f, 0.5f).color(r, g, b, a).texture(uFront0, vBody1).overlay(ov).light(light).normal(0.0f, 0.0f, 1.0f);
        buffer.vertex(mat, 5.0f, -8.0f, 0.5f).color(r, g, b, a).texture(uFront1, vBody1).overlay(ov).light(light).normal(0.0f, 0.0f, 1.0f);
        buffer.vertex(mat, 5.0f, 8.0f, 0.5f).color(r, g, b, a).texture(uFront1, vBody0).overlay(ov).light(light).normal(0.0f, 0.0f, 1.0f);
        buffer.vertex(mat, -5.0f, 8.0f, 0.5f).color(r, g, b, a).texture(uFront0, vBody0).overlay(ov).light(light).normal(0.0f, 0.0f, 1.0f);
        buffer.vertex(mat, 5.0f, -8.0f, -0.5f).color(r, g, b, a).texture(uBack0, vBody1).overlay(ov).light(light).normal(0.0f, 0.0f, -1.0f);
        buffer.vertex(mat, -5.0f, -8.0f, -0.5f).color(r, g, b, a).texture(uBack1, vBody1).overlay(ov).light(light).normal(0.0f, 0.0f, -1.0f);
        buffer.vertex(mat, -5.0f, 8.0f, -0.5f).color(r, g, b, a).texture(uBack1, vBody0).overlay(ov).light(light).normal(0.0f, 0.0f, -1.0f);
        buffer.vertex(mat, 5.0f, 8.0f, -0.5f).color(r, g, b, a).texture(uBack0, vBody0).overlay(ov).light(light).normal(0.0f, 0.0f, -1.0f);
        buffer.vertex(mat, -5.0f, -8.0f, -0.5f).color(r, g, b, a).texture(uRight0, vBody1).overlay(ov).light(light).normal(-1.0f, 0.0f, 0.0f);
        buffer.vertex(mat, -5.0f, -8.0f, 0.5f).color(r, g, b, a).texture(uRight1, vBody1).overlay(ov).light(light).normal(-1.0f, 0.0f, 0.0f);
        buffer.vertex(mat, -5.0f, 8.0f, 0.5f).color(r, g, b, a).texture(uRight1, vBody0).overlay(ov).light(light).normal(-1.0f, 0.0f, 0.0f);
        buffer.vertex(mat, -5.0f, 8.0f, -0.5f).color(r, g, b, a).texture(uRight0, vBody0).overlay(ov).light(light).normal(-1.0f, 0.0f, 0.0f);
        buffer.vertex(mat, 5.0f, -8.0f, 0.5f).color(r, g, b, a).texture(uLeft0, vBody1).overlay(ov).light(light).normal(1.0f, 0.0f, 0.0f);
        buffer.vertex(mat, 5.0f, -8.0f, -0.5f).color(r, g, b, a).texture(uLeft1, vBody1).overlay(ov).light(light).normal(1.0f, 0.0f, 0.0f);
        buffer.vertex(mat, 5.0f, 8.0f, -0.5f).color(r, g, b, a).texture(uLeft1, vBody0).overlay(ov).light(light).normal(1.0f, 0.0f, 0.0f);
        buffer.vertex(mat, 5.0f, 8.0f, 0.5f).color(r, g, b, a).texture(uLeft0, vBody0).overlay(ov).light(light).normal(1.0f, 0.0f, 0.0f);
        buffer.vertex(mat, -5.0f, 8.0f, 0.5f).color(r, g, b, a).texture(uTop0, vTop1).overlay(ov).light(light).normal(0.0f, 1.0f, 0.0f);
        buffer.vertex(mat, 5.0f, 8.0f, 0.5f).color(r, g, b, a).texture(uTop1, vTop1).overlay(ov).light(light).normal(0.0f, 1.0f, 0.0f);
        buffer.vertex(mat, 5.0f, 8.0f, -0.5f).color(r, g, b, a).texture(uTop1, vTop0).overlay(ov).light(light).normal(0.0f, 1.0f, 0.0f);
        buffer.vertex(mat, -5.0f, 8.0f, -0.5f).color(r, g, b, a).texture(uTop0, vTop0).overlay(ov).light(light).normal(0.0f, 1.0f, 0.0f);
        buffer.vertex(mat, -5.0f, -8.0f, -0.5f).color(r, g, b, a).texture(uTop1, vTop0).overlay(ov).light(light).normal(0.0f, -1.0f, 0.0f);
        buffer.vertex(mat, 5.0f, -8.0f, -0.5f).color(r, g, b, a).texture(uBot1, vTop0).overlay(ov).light(light).normal(0.0f, -1.0f, 0.0f);
        buffer.vertex(mat, 5.0f, -8.0f, 0.5f).color(r, g, b, a).texture(uBot1, vTop1).overlay(ov).light(light).normal(0.0f, -1.0f, 0.0f);
        buffer.vertex(mat, -5.0f, -8.0f, 0.5f).color(r, g, b, a).texture(uTop1, vTop1).overlay(ov).light(light).normal(0.0f, -1.0f, 0.0f);
    }

    public GeoModel getOrParseModel(CosmeticModel cosmetic) {
        int id = cosmetic.getId();
        if (this.modelCache.containsKey(id)) {
            return this.modelCache.get(id);
        }
        GeoModel model = this.modelParser.parseModel(cosmetic);
        if (model != null) {
            this.modelCache.put(id, model);
            this.saveInitialBoneTransforms(id, model);
        }
        return model;
    }

    private void saveInitialBoneTransforms(int id, GeoModel model) {
        HashMap<String, float[]> map = new HashMap<String, float[]>();
        for (GeoBone bone : model.topLevelBones) {
            this.saveBonesRecursive(bone, map);
        }
        this.initialBoneTransforms.put(id, map);
    }

    private void saveBonesRecursive(GeoBone bone, Map<String, float[]> map) {
        map.put(bone.name, new float[]{bone.getRotationX(), bone.getRotationY(), bone.getRotationZ(), bone.getPositionX(), bone.getPositionY(), bone.getPositionZ(), bone.getScaleX(), bone.getScaleY(), bone.getScaleZ()});
        for (GeoBone child : bone.childBones) {
            this.saveBonesRecursive(child, map);
        }
    }

    private void resetToInitialPose(GeoModel model, int id) {
        Map<String, float[]> initial = this.initialBoneTransforms.get(id);
        if (initial != null) {
            for (GeoBone bone : model.topLevelBones) {
                this.resetBoneRecursive(bone, initial);
            }
        }
    }

    private void renderBone(GeoBone bone, MatrixStack matrices, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
        if (!bone.isHidden) {
            matrices.push();
            GeckoRenderHelper.translate(bone, matrices);
            GeckoRenderHelper.moveToPivot(bone, matrices);
            GeckoRenderHelper.rotate(bone, matrices);
            GeckoRenderHelper.scale(bone, matrices);
            GeckoRenderHelper.moveBackFromPivot(bone, matrices);
            for (GeoCube cube : bone.childCubes) {
                this.renderCube(cube, matrices, buffer, light, overlay, r, g, b, a);
            }
            for (GeoBone child : bone.childBones) {
                this.renderBone(child, matrices, buffer, light, overlay, r, g, b, a);
            }
            matrices.pop();
        }
    }

    private void renderCube(GeoCube cube, MatrixStack matrices, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
        matrices.push();
        GeckoRenderHelper.moveToPivot(cube, matrices);
        GeckoRenderHelper.rotate(cube, matrices);
        GeckoRenderHelper.moveBackFromPivot(cube, matrices);
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();
        Matrix3f normMatrix = matrices.peek().getNormalMatrix();
        for (GeoQuad quad : cube.quads) {
            if (quad == null) continue;
            Vector3f normal = new Vector3f(quad.normal.getX(), quad.normal.getY(), quad.normal.getZ());
            normMatrix.transform(normal);
            float nx = normal.x();
            float ny = normal.y();
            float nz = normal.z();
            if ((cube.size.getY() == 0.0f || cube.size.getZ() == 0.0f) && nx < 0.0f) {
                nx = -nx;
            }
            if ((cube.size.getX() == 0.0f || cube.size.getZ() == 0.0f) && ny < 0.0f) {
                ny = -ny;
            }
            if ((cube.size.getX() == 0.0f || cube.size.getY() == 0.0f) && nz < 0.0f) {
                nz = -nz;
            }
            int red = (int)(r * 255.0f);
            int green = (int)(g * 255.0f);
            int blue = (int)(b * 255.0f);
            int alpha = (int)(a * 255.0f);
            for (GeoVertex vertex : quad.vertices) {
                buffer.vertex(posMatrix, vertex.position.getX(), vertex.position.getY(), vertex.position.getZ()).color(red, green, blue, alpha).texture(vertex.textureU, vertex.textureV).overlay(overlay).light(light).normal(nx, ny, nz);
            }
        }
        matrices.pop();
    }

    private CosmeticAnimationData getOrParseAnimation(CosmeticModel cosmetic) {
        int id = cosmetic.getId();
        if (this.animationCache.containsKey(id)) {
            return this.animationCache.get(id);
        }
        if (this.noAnimationSet.contains(id)) {
            return null;
        }
        JsonObject animJson = cosmetic.getAnimationJson();
        if (animJson == null) {
            this.noAnimationSet.add(id);
            return null;
        }
        try {
            CosmeticAnimationData data = this.parseAnimationData(animJson);
            if (data != null) {
                this.animationCache.put(id, data);
            } else {
                this.noAnimationSet.add(id);
            }
            return data;
        }
        catch (Exception e) {
            this.noAnimationSet.add(id);
            return null;
        }
    }

    private CosmeticAnimationData parseAnimationData(JsonObject root) {
        CosmeticAnimationData data = new CosmeticAnimationData();
        if (!root.has("animations")) {
            return null;
        }
        JsonObject anims = root.getAsJsonObject("animations");
        Iterator it = anims.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry entry = (Map.Entry)it.next();
            String animName = (String)entry.getKey();
            JsonObject animObj = ((JsonElement)entry.getValue()).getAsJsonObject();
            data.animationName = animName;
            data.loop = !animObj.has("loop") || animObj.get("loop").getAsBoolean();
            float f = data.length = animObj.has("animation_length") ? animObj.get("animation_length").getAsFloat() : 1.0f;
            if (data.length <= 0.0f) {
                data.length = 1.0f;
            }
            if (animObj.has("bones")) {
                JsonObject bones = animObj.getAsJsonObject("bones");
                for (Map.Entry bEntry : bones.entrySet()) {
                    String boneName = (String)bEntry.getKey();
                    JsonObject boneAnim = ((JsonElement)bEntry.getValue()).getAsJsonObject();
                    BoneAnimationData bData = new BoneAnimationData();
                    if (boneAnim.has("rotation")) {
                        bData.rotationKeyframes = this.parseKeyframes(boneAnim.get("rotation"));
                    }
                    if (boneAnim.has("position")) {
                        bData.positionKeyframes = this.parseKeyframes(boneAnim.get("position"));
                    }
                    if (boneAnim.has("scale")) {
                        bData.scaleKeyframes = this.parseKeyframes(boneAnim.get("scale"));
                    }
                    data.boneAnimations.put(boneName, bData);
                }
            }
        }
        return data;
    }

    private Map<Float, float[]> parseKeyframes(JsonElement elem) {
        HashMap<Float, float[]> map = new HashMap<Float, float[]>();
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            for (Map.Entry entry : obj.entrySet()) {
                try {
                    float time = Float.parseFloat((String)entry.getKey());
                    JsonElement val = (JsonElement)entry.getValue();
                    float[] vec = new float[3];
                    if (val.isJsonObject()) {
                        JsonObject vObj = val.getAsJsonObject();
                        if (vObj.has("vector")) {
                            JsonArray arr = vObj.getAsJsonArray("vector");
                            vec[0] = arr.get(0).getAsFloat();
                            vec[1] = arr.get(1).getAsFloat();
                            vec[2] = arr.get(2).getAsFloat();
                        }
                    } else if (val.isJsonArray()) {
                        JsonArray arr = val.getAsJsonArray();
                        vec[0] = arr.get(0).getAsFloat();
                        vec[1] = arr.get(1).getAsFloat();
                        vec[2] = arr.get(2).getAsFloat();
                    }
                    map.put(Float.valueOf(time), vec);
                }
                catch (NumberFormatException numberFormatException) {}
            }
        }
        return map;
    }

    private void applyAnimations(GeoModel model, CosmeticAnimationData animData, int id) {
        Map<String, float[]> initial = this.initialBoneTransforms.get(id);
        if (initial != null) {
            long start = this.animationStartTime.computeIfAbsent(id, k -> System.currentTimeMillis());
            float elapsed = (float)(System.currentTimeMillis() - start) / 1000.0f;
            float animLen = animData.length > 0.0f ? animData.length : 1.0f;
            float time = animData.loop ? elapsed % animLen : Math.min(elapsed, animLen);
            for (GeoBone geoBone : model.topLevelBones) {
                this.resetBoneRecursive(geoBone, initial);
            }
            for (Map.Entry entry : animData.boneAnimations.entrySet()) {
                String boneName = (String)entry.getKey();
                BoneAnimationData bData = (BoneAnimationData)entry.getValue();
                GeoBone bone = this.findBone(model, boneName);
                if (bone == null) continue;
                float[] init = initial.get(boneName);
                if (init == null) {
                    init = new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f};
                }
                if (!bData.rotationKeyframes.isEmpty()) {
                    float[] rot = this.interpolateKeyframes(bData.rotationKeyframes, time);
                    bone.setRotationX(init[0] + (float)Math.toRadians(-rot[0]));
                    bone.setRotationY(init[1] + (float)Math.toRadians(-rot[1]));
                    bone.setRotationZ(init[2] + (float)Math.toRadians(rot[2]));
                }
                if (!bData.positionKeyframes.isEmpty()) {
                    float[] pos = this.interpolateKeyframes(bData.positionKeyframes, time);
                    bone.setPositionX(init[3] + pos[0]);
                    bone.setPositionY(init[4] + pos[1]);
                    bone.setPositionZ(init[5] + pos[2]);
                }
                if (bData.scaleKeyframes.isEmpty()) continue;
                float[] sc = this.interpolateKeyframes(bData.scaleKeyframes, time);
                bone.setScaleX(init[6] * sc[0]);
                bone.setScaleY(init[7] * sc[1]);
                bone.setScaleZ(init[8] * sc[2]);
            }
        }
    }

    private void resetBoneRecursive(GeoBone bone, Map<String, float[]> initial) {
        float[] vals = initial.get(bone.name);
        if (vals != null) {
            bone.setRotationX(vals[0]);
            bone.setRotationY(vals[1]);
            bone.setRotationZ(vals[2]);
            bone.setPositionX(vals[3]);
            bone.setPositionY(vals[4]);
            bone.setPositionZ(vals[5]);
            bone.setScaleX(vals[6]);
            bone.setScaleY(vals[7]);
            bone.setScaleZ(vals[8]);
        }
        for (GeoBone child : bone.childBones) {
            this.resetBoneRecursive(child, initial);
        }
    }

    private GeoBone findBone(GeoModel model, String name) {
        for (GeoBone bone : model.topLevelBones) {
            GeoBone found = this.findBoneRecursive(bone, name);
            if (found == null) continue;
            return found;
        }
        return null;
    }

    private GeoBone findBoneRecursive(GeoBone bone, String name) {
        if (bone.name.equals(name)) {
            return bone;
        }
        for (GeoBone child : bone.childBones) {
            GeoBone found = this.findBoneRecursive(child, name);
            if (found == null) continue;
            return found;
        }
        return null;
    }

    private float[] interpolateKeyframes(Map<Float, float[]> keyframes, float time) {
        if (keyframes.isEmpty()) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        Float t1 = null;
        Float t2 = null;
        float[] v1 = null;
        float[] v2 = null;
        Float minT = null;
        Float maxT = null;
        float[] minV = null;
        float[] maxV = null;
        for (Map.Entry<Float, float[]> entry : keyframes.entrySet()) {
            float t = entry.getKey().floatValue();
            if (minT == null || t < minT.floatValue()) {
                minT = Float.valueOf(t);
                minV = entry.getValue();
            }
            if (maxT == null || t > maxT.floatValue()) {
                maxT = Float.valueOf(t);
                maxV = entry.getValue();
            }
            if (t <= time && (t1 == null || t > t1.floatValue())) {
                t1 = Float.valueOf(t);
                v1 = entry.getValue();
            }
            if (!(t >= time) || t2 != null && !(t < t2.floatValue())) continue;
            t2 = Float.valueOf(t);
            v2 = entry.getValue();
        }
        if (v1 == null && v2 == null) {
            float[] fArray;
            if (minV != null) {
                fArray = minV;
            } else {
                float[] fArray2 = new float[3];
                fArray2[0] = 0.0f;
                fArray2[1] = 0.0f;
                fArray = fArray2;
                fArray2[2] = 0.0f;
            }
            return fArray;
        }
        if (v1 == null) {
            v1 = maxV;
            t1 = Float.valueOf(0.0f);
        }
        if (v2 == null) {
            v2 = minV;
            t2 = Float.valueOf(maxT != null && maxT.floatValue() > 0.0f ? maxT.floatValue() : 1.0f);
        }
        if (t1.equals(t2) || t2.floatValue() - t1.floatValue() == 0.0f) {
            return v1;
        }
        float factor = Math.max(0.0f, Math.min(1.0f, (time - t1.floatValue()) / (t2.floatValue() - t1.floatValue())));
        return new float[]{v1[0] + factor * (v2[0] - v1[0]), v1[1] + factor * (v2[1] - v1[1]), v1[2] + factor * (v2[2] - v1[2])};
    }

    @Environment(value=EnvType.CLIENT)
    private static class CosmeticAnimationData {
        String animationName;
        boolean loop;
        float length;
        Map<String, BoneAnimationData> boneAnimations = new HashMap<String, BoneAnimationData>();

        private CosmeticAnimationData() {
        }
    }

    @Environment(value=EnvType.CLIENT)
    private static class BoneAnimationData {
        Map<Float, float[]> rotationKeyframes = new HashMap<Float, float[]>();
        Map<Float, float[]> positionKeyframes = new HashMap<Float, float[]>();
        Map<Float, float[]> scaleKeyframes = new HashMap<Float, float[]>();

        private BoneAnimationData() {
        }
    }
}


package ru.white.cosmetics.render;

import lombok.Getter;
import ru.white.cosmetics.model.GeoBone;
import ru.white.cosmetics.model.GeoModel;

import java.util.*;

@Getter
public class CosmeticSourceModel {
    final int id;
    final String name;
    final int position;
    final float scale;
    final float x;
    final float y;
    final float z;
    final float yaw;
    final float pitch;
    final float roll;
    final GeoModel model;
    final Map<String, AnimationData> animations;
    final Map<String, float[]> initialBoneTransforms = new HashMap<>();
    final int frameCount;
    final int frameHeight;
    final int totalHeight;
    final int frameTime;

    public CosmeticSourceModel(int id, String name, int position, float scale, float x, float y, float z, float yaw, float pitch, float roll, GeoModel model, Map<String, AnimationData> animations) {
        this(id, name, position, scale, x, y, z, yaw, pitch, roll, model, animations, 0, 0, 0, 0);
    }

    public CosmeticSourceModel(int id, String name, int position, float scale, float x, float y, float z, float yaw, float pitch, float roll, GeoModel model, Map<String, AnimationData> animations, int frameCount, int frameHeight, int totalHeight, int frameTime) {
        this.id = id;
        this.name = name;
        this.position = position;
        this.scale = scale;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.model = model;
        this.animations = animations != null ? animations : Collections.emptyMap();
        this.frameCount = frameCount;
        this.frameHeight = frameHeight;
        this.totalHeight = totalHeight;
        this.frameTime = frameTime;

        if (model != null) {
            for (GeoBone bone : model.getTopLevelBones()) {
                this.saveBoneRecursive(bone);
            }
        }
    }

    /**
     * Сдвиг V для кадров анимированной текстуры.
     * Модели пачканы под полный лист (texture_height = totalHeight), поэтому достаточно
     * прибавить смещение начала текущего кадра — UV остаются внутри своего кадра.
     */
    public float frameVOffset(float timeSeconds) {
        if (this.frameCount <= 1 || this.frameHeight <= 0 || this.totalHeight <= this.frameHeight) {
            return 0.0F;
        }
        float frameSeconds = Math.max(1, this.frameTime) / 20.0F;
        int frame = (int) (timeSeconds / frameSeconds) % this.frameCount;
        return (float) (frame * this.frameHeight) / this.totalHeight;
    }

    private void saveBoneRecursive(GeoBone bone) {
        this.initialBoneTransforms.put(
                bone.getName(),
                new float[]{
                        bone.getRotateX(),
                        bone.getRotateY(),
                        bone.getRotateZ(),
                        bone.getPositionX(),
                        bone.getPositionY(),
                        bone.getPositionZ(),
                        bone.getScaleX(),
                        bone.getScaleY(),
                        bone.getScaleZ()
                }
        );

        for (GeoBone child : bone.getChildBones()) {
            this.saveBoneRecursive(child);
        }
    }

    public void resetBones() {
        if (this.model != null) {
            for (GeoBone bone : this.model.getTopLevelBones()) {
                this.resetBoneRecursive(bone);
            }
        }
    }

    private void resetBoneRecursive(GeoBone bone) {
        float[] base = this.initialBoneTransforms.get(bone.getName());
        if (base != null) {
            bone.setRotateX(base[0]);
            bone.setRotateY(base[1]);
            bone.setRotateZ(base[2]);
            bone.setPositionX(base[3]);
            bone.setPositionY(base[4]);
            bone.setPositionZ(base[5]);
            bone.setScaleX(base[6]);
            bone.setScaleY(base[7]);
            bone.setScaleZ(base[8]);
        }

        for (GeoBone child : bone.getChildBones()) {
            this.resetBoneRecursive(child);
        }
    }

    public GeoBone findBone(String name) {
        return this.model != null ? this.model.getBone(name).orElse(null) : null;
    }

    public String chooseAnimation(boolean gliding, boolean swimming, boolean sneaking, boolean moving) {
        if (this.animations.isEmpty()) {
            return null;
        }

        if (gliding) {
            String s = this.find("elytra", "flying", "fly");
            if (s != null) return s;
        }

        if (swimming) {
            if (moving) {
                String s = this.findAll("moving", "water");
                if (s != null) return s;
            }
            String s = this.find("swimming", "swim");
            if (s != null) return s;
            s = this.findAll("idle", "water");
            if (s != null) return s;
        }

        if (sneaking) {
            if (moving) {
                String s = this.findAll("moving", "sneak");
                if (s != null) return s;
            }
            String s = this.findAll("idle", "sneak");
            if (s != null) return s;
            s = this.find("sneak", "crouch");
            if (s != null) return s;
        }

        if (moving) {
            String s = this.find("walking", "walk", "moving", "move", "run");
            if (s != null && !s.toLowerCase(Locale.ROOT).contains("water") && !s.toLowerCase(Locale.ROOT).contains("sneak")) {
                return s;
            }
        }

        String idle = this.findExactOrContains("idle");
        if (idle != null && !idle.toLowerCase(Locale.ROOT).contains("water") && !idle.toLowerCase(Locale.ROOT).contains("sneak")) {
            return idle;
        }

        String main = this.findExactOrContains("main");
        if (main != null && !main.toLowerCase(Locale.ROOT).contains("water") && !main.toLowerCase(Locale.ROOT).contains("sneak")) {
            return main;
        }

        for (String name : this.animations.keySet()) {
            String l = name.toLowerCase(Locale.ROOT);
            if (!l.contains("gui") && !l.contains("preview")) {
                return name;
            }
        }

        return this.animations.keySet().iterator().next();
    }

    private String find(String... needles) {
        for (String name : this.animations.keySet()) {
            String l = name.toLowerCase(Locale.ROOT);
            for (String needle : needles) {
                if (l.contains(needle)) return name;
            }
        }
        return null;
    }

    private String findAll(String... needles) {
        for (String name : this.animations.keySet()) {
            String l = name.toLowerCase(Locale.ROOT);
            boolean ok = true;
            for (String needle : needles) {
                ok &= l.contains(needle);
            }
            if (ok) return name;
        }
        return null;
    }

    private String findExactOrContains(String needle) {
        for (String name : this.animations.keySet()) {
            if (name.toLowerCase(Locale.ROOT).equals(needle)) {
                return name;
            }
        }
        return this.find(needle);
    }

    public static final class AnimationData {
        public final String name;
        public final boolean loop;
        public final float length;
        public final Map<String, BoneAnimationData> bones;

        public AnimationData(String name, boolean loop, float length, Map<String, BoneAnimationData> bones) {
            this.name = name;
            this.loop = loop;
            this.length = Math.max(0.001F, length);
            this.bones = bones != null ? bones : Collections.emptyMap();
        }
    }

    public static final class BoneAnimationData {
        public final Channel rotation;
        public final Channel position;
        public final Channel scale;

        public BoneAnimationData(Channel rotation, Channel position, Channel scale) {
            this.rotation = rotation;
            this.position = position;
            this.scale = scale;
        }
    }

    public static final class Channel {
        public final NavigableMap<Float, float[]> keys = new TreeMap<>();
        public float[] constant;

        public boolean isEmpty() {
            return this.constant == null && this.keys.isEmpty();
        }

        public float[] sample(float time, float[] fallback) {
            if (this.constant != null) {
                return this.constant;
            } else if (this.keys.isEmpty()) {
                return fallback;
            } else if (this.keys.size() == 1) {
                return this.keys.firstEntry().getValue();
            } else {
                Map.Entry<Float, float[]> first = this.keys.firstEntry();
                Map.Entry<Float, float[]> last = this.keys.lastEntry();
                if (time <= first.getKey()) {
                    return first.getValue();
                } else if (time >= last.getKey()) {
                    return last.getValue();
                } else {
                    Map.Entry<Float, float[]> a = this.keys.floorEntry(time);
                    Map.Entry<Float, float[]> b = this.keys.ceilingEntry(time);
                    if (a == null) return first.getValue();
                    if (b == null) return last.getValue();
                    if (Objects.equals(a.getKey(), b.getKey())) return a.getValue();

                    float q = (time - a.getKey()) / (b.getKey() - a.getKey());
                    q = Math.max(0.0F, Math.min(1.0F, q));
                    float[] av = a.getValue();
                    float[] bv = b.getValue();
                    return new float[]{
                            av[0] + q * (bv[0] - av[0]),
                            av[1] + q * (bv[1] - av[1]),
                            av[2] + q * (bv[2] - av[2])
                    };
                }
            }
        }
    }
}

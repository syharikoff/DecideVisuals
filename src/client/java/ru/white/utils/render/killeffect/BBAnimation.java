package ru.white.utils.render.killeffect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BBAnimation {
    private final Map<String, List<Keyframe>> tracks;
    public final String name;
    public final float length;
    public final String loop;

    private BBAnimation(String name, float length, String loop, Map<String, List<Keyframe>> tracks) {
        this.name = name;
        this.length = length;
        this.loop = loop;
        this.tracks = tracks;
    }

    public static BBAnimation parse(JsonObject source) {
        String name = has(source, "name") ? source.get("name").getAsString() : "animation";
        float length = number(source, "length", 1.0f);
        String loop = has(source, "loop") ? source.get("loop").getAsString() : "once";
        HashMap<String, List<Keyframe>> tracks = new HashMap<>();
        JsonObject animators = source.getAsJsonObject("animators");
        if (animators != null) {
            for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
                JsonArray keyframes = entry.getValue().getAsJsonObject().getAsJsonArray("keyframes");
                if (keyframes == null) continue;
                ArrayList<Keyframe> parsed = new ArrayList<>();
                for (JsonElement element : keyframes) {
                    JsonObject k = element.getAsJsonObject();
                    JsonArray points = k.getAsJsonArray("data_points");
                    if (points == null || points.isEmpty()) continue;
                    parsed.add(new Keyframe(
                            has(k, "channel") ? k.get("channel").getAsString() : "rotation",
                            k.get("time").getAsFloat(),
                            point(points.get(0).getAsJsonObject()),
                            has(k, "interpolation") ? k.get("interpolation").getAsString() : "linear"));
                }
                parsed.sort(Comparator.comparingDouble(Keyframe::time));
                tracks.put(entry.getKey(), List.copyOf(parsed));
            }
        }
        return new BBAnimation(name, length, loop, tracks);
    }

    public Transform sample(String boneUuid, float seconds) {
        List<Keyframe> keyframes = tracks.get(boneUuid);
        if (keyframes == null) keyframes = tracks.get("bone/" + boneUuid);
        if (keyframes == null || keyframes.isEmpty()) return Transform.IDENTITY;

        float time = "once".equals(loop)
                ? Math.min(seconds, length > 0 ? length : seconds)
                : (length <= 0 ? 0 : seconds % length);

        BBModel.Vec pos = sampleChannel(keyframes, "position", time, BBModel.Vec.ZERO);
        BBModel.Vec rot = sampleChannel(keyframes, "rotation", time, BBModel.Vec.ZERO);
        BBModel.Vec scale = sampleChannel(keyframes, "scale", time, BBModel.Vec.ONE);
        return new Transform(new BBModel.Vec(-pos.x(), pos.y(), pos.z()),
                new BBModel.Vec(-rot.x(), -rot.y(), rot.z()), scale);
    }

    private static BBModel.Vec sampleChannel(List<Keyframe> all, String channel, float time, BBModel.Vec fallback) {
        List<Keyframe> frames = filter(all, channel);
        if (frames.isEmpty()) return fallback;
        if (frames.size() == 1) return frames.get(0).value();
        if (time >= frames.get(frames.size() - 1).time()) return frames.get(frames.size() - 1).value();
        if (time <= frames.get(0).time()) return frames.get(0).value();

        Keyframe previous = frames.get(0);
        Keyframe next = frames.get(1);
        for (int i = 0; i < frames.size() - 1; i++) {
            Keyframe a = frames.get(i);
            Keyframe b = frames.get(i + 1);
            if (time >= a.time() && time <= b.time()) {
                previous = a;
                next = b;
                break;
            }
        }

        float previousT = previous.time();
        float nextT = next.time();
        switch (previous.interpolation()) {
            case "step": return previous.value();
            case "catmullrom": {
                BBModel.Vec p0 = catmullP0(frames, previous);
                BBModel.Vec p1 = previous.value();
                BBModel.Vec p2 = next.value();
                BBModel.Vec p3 = catmullP3(frames, next);
                float t = safeRatio(time, previousT, nextT);
                return catmullRom(p0, p1, p2, p3, t);
            }
            case "bezier": {
                float t = safeRatio(time, previousT, nextT);
                BBModel.Vec p0 = previous.value();
                BBModel.Vec p2 = next.value();
                BBModel.Vec handle = BBModel.Vec.lerp(p0, p2, 0.33f);
                BBModel.Vec handle2 = BBModel.Vec.lerp(p0, p2, 0.67f);
                return cubic(p0, handle, handle2, p2, t);
            }
        }
        float t = safeRatio(time, previousT, nextT);
        return BBModel.Vec.lerp(previous.value(), next.value(), clamp01(t));
    }

    private static List<Keyframe> filter(List<Keyframe> all, String channel) {
        ArrayList<Keyframe> out = new ArrayList<>();
        for (Keyframe k : all) {
            if (k.channel().equals(channel)) out.add(k);
        }
        return out;
    }

    private static BBModel.Vec catmullP0(List<Keyframe> frames, Keyframe a) {
        int i = frames.indexOf(a);
        return i > 0 ? frames.get(i - 1).value() : a.value();
    }

    private static BBModel.Vec catmullP3(List<Keyframe> frames, Keyframe b) {
        int i = frames.indexOf(b);
        return i + 1 < frames.size() ? frames.get(i + 1).value() : b.value();
    }

    private static BBModel.Vec catmullRom(BBModel.Vec p0, BBModel.Vec p1, BBModel.Vec p2, BBModel.Vec p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        return new BBModel.Vec(
                0.5f * (2*p1.x() + (-p0.x()+p2.x())*t + (2*p0.x()-5*p1.x()+4*p2.x()-p3.x())*t2 + (-p0.x()+3*p1.x()-3*p2.x()+p3.x())*t3),
                0.5f * (2*p1.y() + (-p0.y()+p2.y())*t + (2*p0.y()-5*p1.y()+4*p2.y()-p3.y())*t2 + (-p0.y()+3*p1.y()-3*p2.y()+p3.y())*t3),
                0.5f * (2*p1.z() + (-p0.z()+p2.z())*t + (2*p0.z()-5*p1.z()+4*p2.z()-p3.z())*t2 + (-p0.z()+3*p1.z()-3*p2.z()+p3.z())*t3));
    }

    private static BBModel.Vec cubic(BBModel.Vec p0, BBModel.Vec p1, BBModel.Vec p2, BBModel.Vec p3, float t) {
        float u = 1 - t, u2 = u*u, u3 = u2*u, t2 = t*t, t3 = t2*t;
        return new BBModel.Vec(
                u3*p0.x() + 3*u2*t*p1.x() + 3*u*t2*p2.x() + t3*p3.x(),
                u3*p0.y() + 3*u2*t*p1.y() + 3*u*t2*p2.y() + t3*p3.y(),
                u3*p0.z() + 3*u2*t*p1.z() + 3*u*t2*p2.z() + t3*p3.z());
    }

    private static float safeRatio(float time, float a, float b) {
        return b == a ? 0 : (time - a) / (b - a);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static BBModel.Vec point(JsonObject source) {
        return new BBModel.Vec(number(source, "x", 0), number(source, "y", 0), number(source, "z", 0));
    }

    private static float number(JsonObject o, String key, float fallback) {
        if (o == null || !o.has(key)) return fallback;
        try { return o.get(key).getAsFloat(); } catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean has(JsonObject o, String key) {
        return o != null && o.has(key);
    }

    private record Keyframe(String channel, float time, BBModel.Vec value, String interpolation) {}

    public static final class Transform {
        public final BBModel.Vec position;
        public final BBModel.Vec rotation;
        public final BBModel.Vec scale;
        public static final Transform IDENTITY = new Transform(BBModel.Vec.ZERO, BBModel.Vec.ZERO, BBModel.Vec.ONE);

        public Transform(BBModel.Vec position, BBModel.Vec rotation, BBModel.Vec scale) {
            this.position = position;
            this.rotation = rotation;
            this.scale = scale;
        }
    }
}

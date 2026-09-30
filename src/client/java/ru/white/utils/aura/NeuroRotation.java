package ru.white.utils.aura;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * «Нейро-ротация»: маленькая MLP-сеть, которая обучается на ротации живого игрока
 * и потом повторяет её манеру доводки.
 *
 * Запись: каждый тик с валидной целью сохраняется сэмпл
 *   вход  (8): ошибка по yaw/pitch до цели, дистанция, скорость цели,
 *              боковое движение цели относительно взгляда, onGround,
 *              прошлый довод по yaw, кулдаун удара (тайминг наводки под удар)
 *   выход (2): фактический довод игрока мышью за этот тик (dYaw, dPitch)
 *
 * Сеть 8→24→16→2 (tanh, линейный выход), обучение — SGD с импульсом по MSE.
 * Веса сохраняются в JSON рядом с конфигами.
 */
public class NeuroRotation {

    public static final int IN = 8, H1 = 24, H2 = 16, OUT = 2;

    /** Нормировка входов/выходов. */
    public static final float YAW_ERR_NORM = 180F, PITCH_ERR_NORM = 90F,
            DIST_NORM = 6F, SPEED_NORM = 0.3F, DELTA_YAW_NORM = 20F, DELTA_PITCH_NORM = 10F;

    /** Минимум сэмплов для обучения (~100 секунд записи). */
    public static final int MIN_SAMPLES = 300;

    private static final int MAX_SAMPLES = 40_000;
    private static final Path MODEL_PATH = Path.of("C:/wvisual/client1_21_11/neuro_rotation.json");
    private static final Path SAMPLES_PATH = Path.of("C:/wvisual/client1_21_11/neuro_samples.bin");
    private static final Gson GSON = new GsonBuilder().create();

    private final float[][] w1 = new float[H1][IN];
    private final float[] b1 = new float[H1];
    private final float[][] w2 = new float[H2][H1];
    private final float[] b2 = new float[H2];
    private final float[][] w3 = new float[OUT][H2];
    private final float[] b3 = new float[OUT];

    private final List<float[]> samples = new ArrayList<>(); // [IN входов, OUT выходов]
    private volatile boolean trained = false;
    private volatile boolean training = false;

    public NeuroRotation() {
        reinit();
        load();
        loadSamples(); // буфер записи тоже переживает перезапуск
    }

    /** Полная переинициализация весов (свежая сеть). */
    private void reinit() {
        Random r = new Random();
        initLayer(w1, b1, r);
        initLayer(w2, b2, r);
        initLayer(w3, b3, r);
    }

    /** true, если в весах NaN/Inf — сеть «сгорела» и непригодна. */
    private boolean hasInvalidWeights() {
        return invalid(w1) || invalid(b1) || invalid(w2) || invalid(b2) || invalid(w3) || invalid(b3);
    }

    private static boolean invalid(float[][] m) {
        for (float[] row : m) if (invalid(row)) return true;
        return false;
    }

    private static boolean invalid(float[] v) {
        for (float f : v) if (Float.isNaN(f) || Float.isInfinite(f)) return true;
        return false;
    }

    private static void initLayer(float[][] w, float[] b, Random r) {
        float scale = (float) Math.sqrt(2.0 / w[0].length); // Xavier/He
        for (float[] row : w)
            for (int i = 0; i < row.length; i++)
                row[i] = (float) (r.nextGaussian() * scale);
        java.util.Arrays.fill(b, 0F);
    }

    public boolean isTrained() {
        return trained;
    }

    public boolean isTraining() {
        return training;
    }

    public int getSampleCount() {
        synchronized (samples) {
            return samples.size();
        }
    }

    public boolean hasMemory() {
        synchronized (samples) {
            return !samples.isEmpty();
        }
    }

    public void clearSamples() {
        synchronized (samples) {
            samples.clear();
        }
        try {
            Files.deleteIfExists(SAMPLES_PATH);
        } catch (Exception ignored) {
        }
    }

    // ── запись ───────────────────────────────────────────────────────────────

    /** Сэмпл одного тика: features уже нормированы, deltas — сырые градусы за тик. */
    public void record(float[] features, float dYaw, float dPitch) {
        if (features.length != IN) return;
        if (invalid(features) || Float.isNaN(dYaw) || Float.isNaN(dPitch)
                || Float.isInfinite(dYaw) || Float.isInfinite(dPitch)) return;
        if (Math.abs(dYaw) > 120F || Math.abs(dPitch) > 90F) return;

        float[] s = new float[IN + OUT];
        System.arraycopy(features, 0, s, 0, IN);
        s[IN] = clamp(dYaw / DELTA_YAW_NORM, -1.5F, 1.5F);
        s[IN + 1] = clamp(dPitch / DELTA_PITCH_NORM, -1.5F, 1.5F);

        synchronized (samples) {
            if (samples.size() >= MAX_SAMPLES) samples.remove(0);
            samples.add(s);
        }
    }

    // ── инференс ─────────────────────────────────────────────────────────────

    /** Предсказанный довод за тик: [dYaw, dPitch] в градусах. */
    public float[] predict(float[] features) {
        float[] remembered = predictFromMemory(features);
        if (remembered != null) return remembered;

        float[] h1 = new float[H1];
        float[] h2 = new float[H2];
        float[] out = new float[OUT];
        forward(features, h1, h2, out);
        // повреждённая сеть не должна ломать камеру NaN-ами
        if (Float.isNaN(out[0]) || Float.isNaN(out[1])
                || Float.isInfinite(out[0]) || Float.isInfinite(out[1])) {
            return new float[]{0F, 0F};
        }
        return new float[]{
                clamp(out[0] * DELTA_YAW_NORM, -30F, 30F),
                clamp(out[1] * DELTA_PITCH_NORM, -18F, 18F)
        };
    }

    private float[] predictFromMemory(float[] features) {
        if (features.length != IN) return null;

        List<float[]> data;
        synchronized (samples) {
            if (samples.isEmpty()) return null;
            data = new ArrayList<>(samples);
        }

        int k = Math.min(5, data.size());
        float[] bestDist = new float[k];
        float[] bestYaw = new float[k];
        float[] bestPitch = new float[k];
        java.util.Arrays.fill(bestDist, Float.MAX_VALUE);

        for (float[] sample : data) {
            float dist = sampleDistance(features, sample);
            for (int i = 0; i < k; i++) {
                if (dist >= bestDist[i]) continue;
                for (int j = k - 1; j > i; j--) {
                    bestDist[j] = bestDist[j - 1];
                    bestYaw[j] = bestYaw[j - 1];
                    bestPitch[j] = bestPitch[j - 1];
                }
                bestDist[i] = dist;
                bestYaw[i] = sample[IN] * DELTA_YAW_NORM;
                bestPitch[i] = sample[IN + 1] * DELTA_PITCH_NORM;
                break;
            }
        }

        float yaw = 0F;
        float pitch = 0F;
        float weightSum = 0F;
        for (int i = 0; i < k; i++) {
            if (bestDist[i] == Float.MAX_VALUE) continue;
            float weight = 1F / (0.0001F + bestDist[i] * bestDist[i]);
            yaw += bestYaw[i] * weight;
            pitch += bestPitch[i] * weight;
            weightSum += weight;
        }
        if (weightSum <= 0F) return null;

        return new float[]{
                clamp(yaw / weightSum, -30F, 30F),
                clamp(pitch / weightSum, -18F, 18F)
        };
    }

    private static float sampleDistance(float[] features, float[] sample) {
        float dist = 0F;
        for (int i = 0; i < IN; i++) {
            float weight = switch (i) {
                case 0 -> 4.0F;
                case 1 -> 3.0F;
                case 6 -> 1.7F;
                case 7 -> 1.4F;
                default -> 1.0F;
            };
            float d = features[i] - sample[i];
            dist += d * d * weight;
        }
        return dist;
    }

    private void forward(float[] in, float[] h1, float[] h2, float[] out) {
        for (int i = 0; i < H1; i++) {
            float sum = b1[i];
            for (int j = 0; j < IN; j++) sum += w1[i][j] * in[j];
            h1[i] = (float) Math.tanh(sum);
        }
        for (int i = 0; i < H2; i++) {
            float sum = b2[i];
            for (int j = 0; j < H1; j++) sum += w2[i][j] * h1[j];
            h2[i] = (float) Math.tanh(sum);
        }
        for (int i = 0; i < OUT; i++) {
            float sum = b3[i];
            for (int j = 0; j < H2; j++) sum += w3[i][j] * h2[j];
            out[i] = sum; // линейный выход
        }
    }

    // ── обучение ─────────────────────────────────────────────────────────────

    /**
     * Обучение в фоне; onDone(успех, кол-во сэмплов) дёргается из потока обучения.
     * Возвращает false, если предыдущее обучение ещё не закончилось.
     */
    public boolean trainAsync(java.util.function.BiConsumer<Boolean, Integer> onDone) {
        if (training) return false;
        List<float[]> data;
        synchronized (samples) {
            data = new ArrayList<>(samples);
        }
        training = true;
        Thread t = new Thread(() -> {
            boolean ok = false;
            try {
                // прогресс записи сохраняем всегда — даже если данных мало,
                // после перезахода буфер продолжит копиться, а не начнётся с нуля
                saveSamples(data);

                if (data.size() >= MIN_SAMPLES) {
                    // стартуем не со «сгоревших» весов
                    if (hasInvalidWeights()) reinit();

                    train(data);

                    if (hasInvalidWeights()) {
                        // обучение разошлось — сбрасываем сеть, не сохраняем мусор
                        reinit();
                        trained = false;
                    } else {
                        trained = true;
                        save();
                        ok = true;
                    }
                }
            } catch (Throwable t2) {
                // любое падение обучения не должно оставлять сеть в полумёртвом виде
                reinit();
                trained = false;
            } finally {
                training = false;
            }
            if (onDone != null) onDone.accept(ok, data.size());
        }, "neuro-rotation-train");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private void train(List<float[]> data) {
        int epochs = 60;
        float lr = 0.004F;
        float momentum = 0.85F;
        float clip = 3F; // ограничение градиентов — против разлёта в NaN

        // импульс
        float[][] vw1 = new float[H1][IN]; float[] vb1 = new float[H1];
        float[][] vw2 = new float[H2][H1]; float[] vb2 = new float[H2];
        float[][] vw3 = new float[OUT][H2]; float[] vb3 = new float[OUT];

        float[] h1 = new float[H1], h2 = new float[H2], out = new float[OUT];
        float[] dOut = new float[OUT], dH2 = new float[H2], dH1 = new float[H1];

        Random rnd = new Random();
        List<float[]> shuffled = new ArrayList<>(data);

        for (int epoch = 0; epoch < epochs; epoch++) {
            Collections.shuffle(shuffled, rnd);
            float curLr = lr * (1F - (float) epoch / epochs * 0.9F); // затухание

            for (float[] s : shuffled) {
                forward(s, h1, h2, out); // первые IN элементов — вход

                for (int i = 0; i < OUT; i++)
                    dOut[i] = clamp(out[i] - s[IN + i], -clip, clip); // dMSE/dOut

                // выходной слой
                for (int i = 0; i < OUT; i++) {
                    for (int j = 0; j < H2; j++) {
                        vw3[i][j] = momentum * vw3[i][j] - curLr * dOut[i] * h2[j];
                        w3[i][j] += vw3[i][j];
                    }
                    vb3[i] = momentum * vb3[i] - curLr * dOut[i];
                    b3[i] += vb3[i];
                }

                // второй скрытый
                for (int j = 0; j < H2; j++) {
                    float g = 0;
                    for (int i = 0; i < OUT; i++) g += dOut[i] * w3[i][j];
                    dH2[j] = clamp(g * (1F - h2[j] * h2[j]), -clip, clip); // tanh'
                }
                for (int i = 0; i < H2; i++) {
                    for (int j = 0; j < H1; j++) {
                        vw2[i][j] = momentum * vw2[i][j] - curLr * dH2[i] * h1[j];
                        w2[i][j] += vw2[i][j];
                    }
                    vb2[i] = momentum * vb2[i] - curLr * dH2[i];
                    b2[i] += vb2[i];
                }

                // первый скрытый
                for (int j = 0; j < H1; j++) {
                    float g = 0;
                    for (int i = 0; i < H2; i++) g += dH2[i] * w2[i][j];
                    dH1[j] = clamp(g * (1F - h1[j] * h1[j]), -clip, clip);
                }
                for (int i = 0; i < H1; i++) {
                    for (int j = 0; j < IN; j++) {
                        vw1[i][j] = momentum * vw1[i][j] - curLr * dH1[i] * s[j];
                        w1[i][j] += vw1[i][j];
                    }
                    vb1[i] = momentum * vb1[i] - curLr * dH1[i];
                    b1[i] += vb1[i];
                }
            }
        }
    }

    // ── персистентность ──────────────────────────────────────────────────────

    /** Сэмплы на диск: int ширина строки, int кол-во, потом float-ы подряд. */
    private void saveSamples(List<float[]> data) {
        try {
            Files.createDirectories(SAMPLES_PATH.getParent());
            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(SAMPLES_PATH)))) {
                out.writeInt(IN + OUT);
                out.writeInt(data.size());
                for (float[] s : data)
                    for (int i = 0; i < IN + OUT; i++) out.writeFloat(s[i]);
            }
        } catch (Exception ignored) {
        }
    }

    private void loadSamples() {
        if (!Files.exists(SAMPLES_PATH)) return;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(SAMPLES_PATH)))) {
            int width = in.readInt();
            if (width != IN + OUT) return; // формат старой версии сети — пропускаем
            int count = Math.min(in.readInt(), MAX_SAMPLES);
            synchronized (samples) {
                samples.clear();
                for (int c = 0; c < count; c++) {
                    float[] s = new float[IN + OUT];
                    for (int i = 0; i < s.length; i++) s[i] = in.readFloat();
                    samples.add(s);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void save() {
        if (hasInvalidWeights()) return; // мусор на диск не пишем
        try {
            Files.createDirectories(MODEL_PATH.getParent());
            JsonObject root = new JsonObject();
            root.add("w1", matrixToJson(w1));
            root.add("b1", vectorToJson(b1));
            root.add("w2", matrixToJson(w2));
            root.add("b2", vectorToJson(b2));
            root.add("w3", matrixToJson(w3));
            root.add("b3", vectorToJson(b3));
            try (Writer w = Files.newBufferedWriter(MODEL_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
        } catch (Exception ignored) {
        }
    }

    public void load() {
        if (!Files.exists(MODEL_PATH)) return;
        try (Reader r = Files.newBufferedReader(MODEL_PATH, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            jsonToMatrix(root.getAsJsonArray("w1"), w1);
            jsonToVector(root.getAsJsonArray("b1"), b1);
            jsonToMatrix(root.getAsJsonArray("w2"), w2);
            jsonToVector(root.getAsJsonArray("b2"), b2);
            jsonToMatrix(root.getAsJsonArray("w3"), w3);
            jsonToVector(root.getAsJsonArray("b3"), b3);
            if (hasInvalidWeights()) {
                reinit();
                return;
            }
            trained = true;
        } catch (Exception e) {
            reinit(); // файл битый/старого формата — начинаем с чистой сети
        }
    }

    private static JsonArray matrixToJson(float[][] m) {
        JsonArray arr = new JsonArray();
        for (float[] row : m) arr.add(vectorToJson(row));
        return arr;
    }

    private static JsonArray vectorToJson(float[] v) {
        JsonArray arr = new JsonArray();
        for (float f : v) arr.add(f);
        return arr;
    }

    private static void jsonToMatrix(JsonArray arr, float[][] m) {
        for (int i = 0; i < m.length; i++)
            jsonToVector(arr.get(i).getAsJsonArray(), m[i]);
    }

    private static void jsonToVector(JsonArray arr, float[] v) {
        for (int i = 0; i < v.length; i++)
            v[i] = arr.get(i).getAsFloat();
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }
}

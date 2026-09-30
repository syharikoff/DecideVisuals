package ru.white.manager.neuro;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Именованная нейро-модель: сеть + метаданные. Сохраняется в
 * C:/wvisual/client1_21_11/neuro/&lt;name&gt;.neuro
 */
public class NeuroModel {

    public static final Path DIR = Path.of("C:/wvisual/client1_21_11/neuro");
    private static final Path CURRENT_FILE = DIR.resolve("current.txt");

    public static final int INPUTS = 14;
    public static final int HIDDEN = 20;
    public static final int OUTPUTS = 13;
    private static final int MAX_MEMORY = 60_000;

    public static final float MAX_YAW_SPEED = 180F;
    public static final float MAX_PITCH_SPEED = 120F;
    public static final float MAX_PEAK_SPEED = 90F;
    public static final float MAX_YAW_SHAKE = 8F;
    public static final float MAX_PITCH_SHAKE = 5F;
    public static final float MAX_YAW_LEAD = 35F;
    public static final float MAX_PITCH_LEAD = 22F;
    public static final float MAX_YAW_ACCEL = 35F;
    public static final float MAX_PITCH_ACCEL = 22F;

    public final String name;
    public NeuroNet net;
    public int samples;
    public double loss = -1;
    private final List<double[]> memoryX = new ArrayList<>();
    private final List<double[]> memoryY = new ArrayList<>();
    private int replayIndex = -1;

    // "Штрихи" — сырая текстура руки: {jitterYaw, jitterPitch, скорость в этот момент}.
    // Пишутся подряд, реплеятся последовательно — сохраняется ритм, а не отдельные точки.
    public static final int STROKE_SIZE = 3;
    private static final int MAX_STROKES = 50_000;
    private final List<float[]> strokes = new ArrayList<>();
    private int strokeIndex = -1;

    // Траектории — полная копия движения: {yawErr, pitchErr, dYaw, dPitch, тиков с удара}.
    // Состояние (где цель относительно прицела, давно ли был удар) -> что сделала мышь.
    // Строка с sinceAttack = -1 означает разрыв записи (реплей через него не проходит).
    public static final int TRAJ_SIZE = 5;
    private static final int MAX_TRAJ = 60_000;
    private final List<float[]> traj = new ArrayList<>();
    private int trajIndex = -1;

    public NeuroModel(String name) {
        this.name = name;
        this.net = new NeuroNet(INPUTS, HIDDEN, OUTPUTS);
    }

    /** Предсказывает [yawSpeed, pitchSpeed] по ошибке наведения (в градусах). */
    public float[] predict(float yawErr, float pitchErr) {
        Prediction prediction = predictDetailed(yawErr, pitchErr);
        return new float[]{prediction.yawSpeed, prediction.pitchSpeed};
    }

    public Prediction predictDetailed(float yawErr, float pitchErr) {
        double[] x = defaultFeatures(yawErr, pitchErr);
        return predictDetailed(x);
    }

    public Prediction predictDetailed(double[] x) {
        Prediction network = predictFromNetwork(x);
        Prediction remembered = predictFromMemory(x);
        if (remembered != null) return remembered.withSpeed(network.yawSpeed, network.pitchSpeed);

        return network;
    }

    private Prediction predictFromNetwork(double[] x) {
        double[] o = net.forward(x);
        float yawSpeed = (float) (o[0] * MAX_YAW_SPEED);
        float pitchSpeed = (float) (o[1] * MAX_PITCH_SPEED);
        if (o.length < OUTPUTS) {
            return new Prediction(Math.max(yawSpeed, 12F), Math.max(pitchSpeed, 10F),
                    0.5F, 0.5F, 0.5F, 0F, 0F, 0F, 0F, 0F, 0F, 1F, 0F, false);
        }
        return new Prediction(
                Math.max(yawSpeed, 12F),
                Math.max(pitchSpeed, 10F),
                clamp01((float) o[2]),
                clamp01((float) o[3]),
                clamp01((float) o[4]),
                unitToSigned((float) o[5]) * MAX_YAW_SHAKE,
                unitToSigned((float) o[6]) * MAX_PITCH_SHAKE,
                unitToSigned((float) o[7]) * MAX_YAW_LEAD,
                unitToSigned((float) o[8]) * MAX_PITCH_LEAD,
                unitToSigned((float) o[9]) * MAX_YAW_ACCEL,
                unitToSigned((float) o[10]) * MAX_PITCH_ACCEL,
                clamp01((float) o[11]),
                clamp01((float) o[12]),
                false
        );
    }

    public void remember(double[][] x, double[][] y) {
        for (int i = 0; i < x.length && i < y.length; i++) {
            if (x[i].length != INPUTS || y[i].length != OUTPUTS) continue;
            if (memoryX.size() >= MAX_MEMORY) {
                memoryX.remove(0);
                memoryY.remove(0);
            }
            memoryX.add(x[i].clone());
            memoryY.add(y[i].clone());
        }
    }

    private Prediction predictFromMemory(double[] x) {
        if (memoryX.isEmpty() || x.length != INPUTS) return null;

        if (replayIndex >= 0 && replayIndex < memoryX.size()) {
            double sequenceDist = sampleDistance(x, memoryX.get(replayIndex));
            if (sequenceDist < 8.0D) {
                return predictionFromOutput(memoryY.get(replayIndex++));
            }
        }

        double bestDist = Double.MAX_VALUE;
        double[] best = null;
        int bestIndex = -1;
        for (int i = 0; i < memoryX.size(); i++) {
            double dist = sampleDistance(x, memoryX.get(i));
            if (dist < bestDist) {
                bestDist = dist;
                best = memoryY.get(i);
                bestIndex = i;
            }
        }
        replayIndex = bestIndex >= 0 ? bestIndex + 1 : -1;
        return best == null ? null : predictionFromOutput(best);
    }

    private static double sampleDistance(double[] a, double[] b) {
        double dist = 0.0;
        for (int i = 0; i < INPUTS; i++) {
            double weight = switch (i) {
                case 0, 1, 2, 3 -> 3.0;
                case 4, 5, 6, 7 -> 2.4;
                case 8, 9, 10 -> 1.8;
                case 12, 13 -> 1.5;
                default -> 1.0;
            };
            double d = a[i] - b[i];
            dist += d * d * weight;
        }
        return dist;
    }

    private static Prediction predictionFromOutput(double[] o) {
        return new Prediction(
                Math.max((float) o[0] * MAX_YAW_SPEED, 12F),
                Math.max((float) o[1] * MAX_PITCH_SPEED, 10F),
                clamp01((float) o[2]),
                clamp01((float) o[3]),
                clamp01((float) o[4]),
                unitToSigned((float) o[5]) * MAX_YAW_SHAKE,
                unitToSigned((float) o[6]) * MAX_PITCH_SHAKE,
                unitToSigned((float) o[7]) * MAX_YAW_LEAD,
                unitToSigned((float) o[8]) * MAX_PITCH_LEAD,
                unitToSigned((float) o[9]) * MAX_YAW_ACCEL,
                unitToSigned((float) o[10]) * MAX_PITCH_ACCEL,
                o.length > 11 ? clamp01((float) o[11]) : 1F,
                o.length > 12 ? clamp01((float) o[12]) : 0F,
                true
        );
    }

    public static double[] defaultFeatures(float yawErr, float pitchErr) {
        return features(yawErr, pitchErr, 0F, 0F, 0F, 0F, 0.5F, 0.5F, 0.5F, 0F, 1F, 0F);
    }

    public static double[] features(float yawErr, float pitchErr,
                                    float appliedYaw, float appliedPitch,
                                    float prevAppliedYaw, float prevAppliedPitch,
                                    float hitX, float hitY, float hitZ,
                                    float distance,
                                    float prevSmoothness, float prevPeak) {
        return new double[]{
                Math.min(Math.abs(yawErr) / 180.0, 1.0),
                Math.min(Math.abs(pitchErr) / 90.0, 1.0),
                signedToUnit(yawErr, 180F),
                signedToUnit(pitchErr, 90F),
                signedToUnit(appliedYaw, MAX_YAW_LEAD),
                signedToUnit(appliedPitch, MAX_PITCH_LEAD),
                signedToUnit(appliedYaw - prevAppliedYaw, MAX_YAW_ACCEL),
                signedToUnit(appliedPitch - prevAppliedPitch, MAX_PITCH_ACCEL),
                clamp01(hitX),
                clamp01(hitY),
                clamp01(hitZ),
                Math.min(Math.max(distance, 0F) / 8.0, 1.0),
                clamp01(prevSmoothness),
                clamp01(prevPeak)
        };
    }

    public void addStrokes(List<float[]> recorded) {
        for (float[] s : recorded) {
            if (s.length != STROKE_SIZE) continue;
            if (strokes.size() >= MAX_STROKES) strokes.remove(0);
            strokes.add(s.clone());
        }
    }

    public int strokeCount() {
        return strokes.size();
    }

    /**
     * Следующий кусочек записанной текстуры руки {jitterYaw, jitterPitch} под текущую скорость.
     * Держит указатель и идёт по записи последовательно, пока скорость контекста совпадает —
     * так воспроизводится настоящий ритм дёрганий, а не случайный шум.
     */
    public float[] nextJitter(float speed) {
        if (strokes.isEmpty()) return new float[]{0F, 0F};

        if (strokeIndex >= 0 && strokeIndex < strokes.size()) {
            float[] s = strokes.get(strokeIndex);
            if (Math.abs(s[2] - speed) <= speedTolerance(speed)) {
                strokeIndex++;
                return new float[]{s[0], s[1]};
            }
        }

        int best = 0;
        float bestDist = Float.MAX_VALUE;
        int start = java.util.concurrent.ThreadLocalRandom.current().nextInt(strokes.size());
        for (int i = 0; i < strokes.size(); i++) {
            int idx = (start + i) % strokes.size();
            float dist = Math.abs(strokes.get(idx)[2] - speed);
            if (dist < bestDist) {
                bestDist = dist;
                best = idx;
            }
            if (dist <= speedTolerance(speed) * 0.25F) {
                best = idx;
                break;
            }
        }
        strokeIndex = best + 1;
        float[] s = strokes.get(best);
        return new float[]{s[0], s[1]};
    }

    private static float speedTolerance(float speed) {
        return Math.max(3F, speed * 0.6F);
    }

    public void addTrajectory(List<float[]> recorded) {
        for (float[] s : recorded) {
            if (s.length != TRAJ_SIZE) continue;
            if (traj.size() >= MAX_TRAJ) traj.remove(0);
            traj.add(s.clone());
        }
    }

    public int trajCount() {
        return traj.size();
    }

    /**
     * Полная копия движения: следующий записанный сдвиг камеры {dYaw, dPitch}
     * под текущую ситуацию (ошибка до цели + давно ли был удар).
     * Идёт по записи последовательно, пока ситуация совпадает — воспроизводится
     * весь жест целиком: подлёт к хитбоксу, отвод после удара, доводка.
     * null = подходящей записи нет, звать фолбэк.
     */
    public float[] nextMove(float yawErr, float pitchErr, float sinceAttack) {
        if (traj.isEmpty()) return null;

        if (trajIndex >= 0 && trajIndex < traj.size()) {
            float[] s = traj.get(trajIndex);
            if (s[4] >= 0 && trajDistance(s, yawErr, pitchErr, sinceAttack) <= followTolerance(yawErr, pitchErr)) {
                trajIndex++;
                return scaledMove(s, yawErr, pitchErr);
            }
        }

        int best = -1;
        float bestDist = Float.MAX_VALUE;
        int start = java.util.concurrent.ThreadLocalRandom.current().nextInt(traj.size());
        for (int i = 0; i < traj.size(); i++) {
            int idx = (start + i) % traj.size();
            float[] s = traj.get(idx);
            if (s[4] < 0) continue;
            float dist = trajDistance(s, yawErr, pitchErr, sinceAttack);
            if (dist < bestDist) {
                bestDist = dist;
                best = idx;
            }
            if (dist <= followTolerance(yawErr, pitchErr) * 0.2F) {
                best = idx;
                break;
            }
        }
        if (best < 0 || bestDist > followTolerance(yawErr, pitchErr) * 4F) {
            trajIndex = -1;
            return null;
        }
        trajIndex = best + 1;
        return scaledMove(traj.get(best), yawErr, pitchErr);
    }

    /** Масштабирует записанный сдвиг под текущую ошибку, чтобы жест сходился к цели. */
    private static float[] scaledMove(float[] s, float yawErr, float pitchErr) {
        float recErr = (float) Math.hypot(s[0], s[1]);
        float curErr = (float) Math.hypot(yawErr, pitchErr);
        float scale = recErr < 1F ? 1F : clamp(curErr / recErr, 0.5F, 2.0F);
        return new float[]{s[2] * scale, s[3] * scale};
    }

    private static float trajDistance(float[] s, float yawErr, float pitchErr, float sinceAttack) {
        float dYaw = s[0] - yawErr;
        float dPitch = s[1] - pitchErr;
        float dAtk = (Math.min(s[4], 40F) - Math.min(sinceAttack, 40F)) * 1.2F;
        return dYaw * dYaw + dPitch * dPitch + dAtk * dAtk;
    }

    private static float followTolerance(float yawErr, float pitchErr) {
        float err = (float) Math.hypot(yawErr, pitchErr);
        float tol = 6F + err * 0.35F;
        return tol * tol;
    }

    public void ensureModernShape() {
        if (net.in != INPUTS || net.out != OUTPUTS) {
            net = new NeuroNet(INPUTS, HIDDEN, OUTPUTS);
            samples = 0;
            loss = -1;
            memoryX.clear();
            memoryY.clear();
        }
    }

    public static double signedToUnit(float value, float maxAbs) {
        if (maxAbs <= 0F) return 0.5;
        return (clamp(value / maxAbs, -1F, 1F) + 1F) * 0.5F;
    }

    private static float unitToSigned(float value) {
        return clamp01(value) * 2F - 1F;
    }

    private static float clamp01(float value) {
        return clamp(value, 0F, 1F);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static class Prediction {
        public final float yawSpeed;
        public final float pitchSpeed;
        public final float hitX;
        public final float hitY;
        public final float hitZ;
        public final float yawShake;
        public final float pitchShake;
        public final float yawLead;
        public final float pitchLead;
        public final float yawAccel;
        public final float pitchAccel;
        // плавность движения внутри тика: 1 = чистая дуга, 0 = дёрганое туда-сюда
        public final float smoothness;
        // пиковый рывок мыши внутри тика, 0..1 от MAX_PEAK_SPEED
        public final float peak;
        public final boolean remembered;

        public Prediction(float yawSpeed, float pitchSpeed, float hitX, float hitY, float hitZ,
                          float yawShake, float pitchShake,
                          float yawLead, float pitchLead,
                          float yawAccel, float pitchAccel) {
            this(yawSpeed, pitchSpeed, hitX, hitY, hitZ, yawShake, pitchShake,
                    yawLead, pitchLead, yawAccel, pitchAccel, 1F, 0F, false);
        }

        public Prediction(float yawSpeed, float pitchSpeed, float hitX, float hitY, float hitZ,
                          float yawShake, float pitchShake,
                          float yawLead, float pitchLead,
                          float yawAccel, float pitchAccel,
                          float smoothness, float peak, boolean remembered) {
            this.yawSpeed = yawSpeed;
            this.pitchSpeed = pitchSpeed;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
            this.yawShake = yawShake;
            this.pitchShake = pitchShake;
            this.yawLead = yawLead;
            this.pitchLead = pitchLead;
            this.yawAccel = yawAccel;
            this.pitchAccel = pitchAccel;
            this.smoothness = smoothness;
            this.peak = peak;
            this.remembered = remembered;
        }

        public Prediction withSpeed(float yawSpeed, float pitchSpeed) {
            return new Prediction(yawSpeed, pitchSpeed, hitX, hitY, hitZ,
                    yawShake, pitchShake, yawLead, pitchLead, yawAccel, pitchAccel,
                    smoothness, peak, remembered);
        }
    }

    public void save() {
        try {
            Files.createDirectories(DIR);
            String body = samples + " " + loss + "\n" + net.serialize() + serializeMemory() + serializeStrokes() + serializeTrajectory();
            Files.writeString(file(), body, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private String serializeStrokes() {
        StringBuilder sb = new StringBuilder();
        sb.append("STROKES ").append(strokes.size()).append('\n');
        for (float[] s : strokes) {
            sb.append(s[0]).append(' ').append(s[1]).append(' ').append(s[2]).append('\n');
        }
        return sb.toString();
    }

    private String serializeTrajectory() {
        StringBuilder sb = new StringBuilder();
        sb.append("TRAJ ").append(traj.size()).append('\n');
        for (float[] s : traj) {
            sb.append(s[0]).append(' ').append(s[1]).append(' ').append(s[2]).append(' ')
                    .append(s[3]).append(' ').append(s[4]).append('\n');
        }
        return sb.toString();
    }

    private String serializeMemory() {
        StringBuilder sb = new StringBuilder();
        sb.append("MEMORY ").append(memoryX.size()).append('\n');
        for (int row = 0; row < memoryX.size(); row++) {
            for (double v : memoryX.get(row)) sb.append(v).append(' ');
            sb.append("| ");
            for (double v : memoryY.get(row)) sb.append(v).append(' ');
            sb.append('\n');
        }
        return sb.toString();
    }

    public Path file() {
        return DIR.resolve(name + ".neuro");
    }

    public static void saveCurrentName(String name) {
        try {
            Files.createDirectories(DIR);
            if (name == null || name.isBlank()) {
                Files.deleteIfExists(CURRENT_FILE);
            } else {
                Files.writeString(CURRENT_FILE, name, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}
    }

    public static String loadCurrentName() {
        try {
            if (!Files.exists(CURRENT_FILE)) return null;
            String name = Files.readString(CURRENT_FILE, StandardCharsets.UTF_8).trim();
            return name.isEmpty() ? null : name;
        } catch (Exception e) {
            return null;
        }
    }

    public static NeuroModel load(String name) {
        try {
            Path p = DIR.resolve(name + ".neuro");
            if (!Files.exists(p)) return null;
            String data = Files.readString(p, StandardCharsets.UTF_8);
            int nl = data.indexOf('\n');
            String meta = data.substring(0, nl).trim();
            String[] m = meta.split("\\s+");
            NeuroModel model = new NeuroModel(name);
            model.samples = Integer.parseInt(m[0]);
            model.loss = Double.parseDouble(m[1]);
            model.net = NeuroNet.deserialize(data.substring(nl + 1));
            model.loadMemory(data.substring(nl + 1));
            model.loadStrokes(data.substring(nl + 1));
            model.loadTrajectory(data.substring(nl + 1));
            return model;
        } catch (Exception e) {
            return null;
        }
    }

    private void loadMemory(String data) {
        String[] lines = data.split("\n");
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("MEMORY ")) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) return;

        memoryX.clear();
        memoryY.clear();
        for (int i = start; i < lines.length && memoryX.size() < MAX_MEMORY; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("STROKES ")) break;
            int sep = line.indexOf('|');
            if (sep < 0) continue;
            double[] x = parseVector(line.substring(0, sep), INPUTS);
            double[] y = parseVector(line.substring(sep + 1), OUTPUTS);
            if (x == null || y == null) continue;
            memoryX.add(x);
            memoryY.add(y);
        }
    }

    private void loadStrokes(String data) {
        String[] lines = data.split("\n");
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("STROKES ")) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) return;

        strokes.clear();
        for (int i = start; i < lines.length && strokes.size() < MAX_STROKES; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("TRAJ ")) break;
            String[] parts = line.split("\\s+");
            if (parts.length < STROKE_SIZE) continue;
            try {
                strokes.add(new float[]{
                        Float.parseFloat(parts[0]),
                        Float.parseFloat(parts[1]),
                        Float.parseFloat(parts[2])
                });
            } catch (Exception ignored) {}
        }
    }

    private void loadTrajectory(String data) {
        String[] lines = data.split("\n");
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("TRAJ ")) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) return;

        traj.clear();
        for (int i = start; i < lines.length && traj.size() < MAX_TRAJ; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            if (parts.length < TRAJ_SIZE) continue;
            try {
                traj.add(new float[]{
                        Float.parseFloat(parts[0]),
                        Float.parseFloat(parts[1]),
                        Float.parseFloat(parts[2]),
                        Float.parseFloat(parts[3]),
                        Float.parseFloat(parts[4])
                });
            } catch (Exception ignored) {}
        }
    }

    private static double[] parseVector(String text, int expected) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < expected) return null;
        double[] values = new double[expected];
        try {
            for (int i = 0; i < expected; i++) values[i] = Double.parseDouble(parts[i]);
            return values;
        } catch (Exception e) {
            return null;
        }
    }
}

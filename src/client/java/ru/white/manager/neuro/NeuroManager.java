package ru.white.manager.neuro;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.white.Client;
import ru.white.manager.event_impl.AttackEvent;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.Rotation;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.math.ChatUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NeuroManager implements IMinecraft {

    private static NeuroManager instance;

    public static NeuroManager get() {
        if (instance == null) {
            instance = new NeuroManager();
            Client.eventHandler().subscribe(instance);
        }
        return instance;
    }

    private final Map<String, NeuroModel> models = new LinkedHashMap<>();
    private NeuroModel current;

    private boolean recording;
    private OtherClientPlayerEntity dummy;
    private Vec3d dummyCenter;
    private boolean spawnedDummy;
    private final List<double[]> recX = new ArrayList<>();
    private final List<double[]> recY = new ArrayList<>();
    private float lastYaw, lastPitch;
    private float prevAppliedYaw, prevAppliedPitch;
    private float lastHitX = 0.5F, lastHitY = 0.5F, lastHitZ = 0.5F;
    private long trainStart;

    // Покадровое накопление между тиками: длина пути, пиковый рывок, число кадров.
    // Ловит суб-тиковый джитер мыши, который на 20 Гц тиках теряется.
    private float frameLastYaw, frameLastPitch;
    private boolean frameInit;
    private float framePath;
    private float framePeak;
    private int frameCount;

    // Плавность/пик предыдущего тика (идут во вход сети) и EMA-сглаженные скорости
    private float prevSmoothness = 1F, prevPeak = 0F;
    private float emaYawSpeed, emaPitchSpeed;

    // Сырая текстура руки: дёрганая составляющая движения (отклонение от плавного хода)
    // пишется на каждом тике независимо от хитбокса — фулл запись того, как ведётся мышь
    private final List<float[]> recStrokes = new ArrayList<>();
    private float emaSignedYaw, emaSignedPitch;

    // Траектории: полная последовательность "ситуация -> движение мыши" с контекстом ударов.
    // Отсюда плейбек копирует подлёт к хитбоксу и отвод камеры после удара один-в-один
    private final List<float[]> recTraj = new ArrayList<>();
    private int attackTicksAgo = 999;
    private boolean trajGapPending;

    private NeuroManager() {
        loadModels();
    }

    public boolean isRecording() {
        return recording;
    }

    public NeuroModel getCurrent() {
        return current;
    }

    public java.util.Collection<NeuroModel> all() {
        return models.values();
    }

    public boolean create(String name) {
        if (models.containsKey(name.toLowerCase())) return false;
        NeuroModel m = new NeuroModel(name);
        models.put(name.toLowerCase(), m);
        current = m;
        m.save();
        NeuroModel.saveCurrentName(name);
        return true;
    }

    public boolean select(String name) {
        NeuroModel m = models.get(name.toLowerCase());
        if (m == null) m = NeuroModel.load(name);
        if (m == null) return false;
        models.put(name.toLowerCase(), m);
        current = m;
        NeuroModel.saveCurrentName(m.name);
        return true;
    }

    public boolean delete(String name) {
        NeuroModel m = models.remove(name.toLowerCase());
        try {
            java.nio.file.Files.deleteIfExists(NeuroModel.DIR.resolve(name + ".neuro"));
        } catch (Exception ignored) {}
        if (m != null && current == m) {
            current = models.values().stream()
                    .max(java.util.Comparator.comparingInt(model -> model.samples))
                    .orElse(null);
            NeuroModel.saveCurrentName(current == null ? null : current.name);
        }
        return m != null;
    }

    private void loadModels() {
        try {
            if (!java.nio.file.Files.isDirectory(NeuroModel.DIR)) return;
            try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(NeuroModel.DIR)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".neuro"))
                        .forEach(path -> {
                            String file = path.getFileName().toString();
                            String name = file.substring(0, file.length() - ".neuro".length());
                            NeuroModel model = NeuroModel.load(name);
                            if (model != null) models.put(name.toLowerCase(), model);
                        });
            }

            String selected = NeuroModel.loadCurrentName();
            if (selected != null) current = models.get(selected.toLowerCase());
            if (current == null) {
                current = models.values().stream()
                        .max(java.util.Comparator.comparingInt(model -> model.samples))
                        .orElse(null);
            }
        } catch (Exception ignored) {}
    }

    public void startTrain() {
        if (current == null) {
            ChatUtils.addChatMessage("§cСначала создай/выбери модель: §f.neuro create <имя>");
            return;
        }
        if (mc.player == null || mc.world == null) return;
        if (recording) {
            ChatUtils.addChatMessage("§7Тренировка уже идет. §f.neuro stop §7чтобы закончить");
            return;
        }

        spawnDummy();

        recX.clear();
        recY.clear();
        lastYaw = Rotation.cameraYaw();
        lastPitch = Rotation.cameraPitch();
        prevAppliedYaw = 0F;
        prevAppliedPitch = 0F;
        lastHitX = 0.5F;
        lastHitY = 0.5F;
        lastHitZ = 0.5F;
        frameInit = false;
        framePath = 0F;
        framePeak = 0F;
        frameCount = 0;
        prevSmoothness = 1F;
        prevPeak = 0F;
        emaYawSpeed = 0F;
        emaPitchSpeed = 0F;
        recStrokes.clear();
        emaSignedYaw = 0F;
        emaSignedPitch = 0F;
        recTraj.clear();
        attackTicksAgo = 999;
        trajGapPending = false;
        trainStart = System.currentTimeMillis();
        recording = true;

        ChatUtils.addChatMessage("§a[Neuro] §7Тренировка модели §f" + current.name + "§7 началась: веди прицелом по хитбоксу. §f.neuro stop§7 для завершения");
    }

    public void stopTrain() {
        if (!recording) {
            ChatUtils.addChatMessage("§7Тренировка не запущена");
            return;
        }
        recording = false;
        removeDummy();

        int n = recX.size();
        if (current == null || n < 10) {
            ChatUtils.addChatMessage("§cМало данных (" + n + "), модель не обучена. Поводи прицелом подольше");
            return;
        }

        current.ensureModernShape();
        double[][] X = recX.toArray(new double[0][]);
        double[][] Y = recY.toArray(new double[0][]);
        double loss = current.net.train(X, Y, 650, 0.18);
        current.remember(X, Y);
        current.addStrokes(recStrokes);
        current.addTrajectory(recTraj);
        current.samples += n;
        current.loss = loss;
        current.save();

        ChatUtils.addChatMessage(String.format("§a[Neuro] §7Модель §f%s§7 обучена: §f%d§7 образцов, §f%d§7 штрихов, §f%d§7 тиков траектории, ошибка §f%.4f",
                current.name, n, current.strokeCount(), current.trajCount(), loss));
    }

    private void spawnDummy() {
        double rad = Math.toRadians(Rotation.cameraYaw());
        double dx = -Math.sin(rad) * 3.5;
        double dz = Math.cos(rad) * 3.5;
        dummyCenter = new Vec3d(mc.player.getX() + dx, mc.player.getY(), mc.player.getZ() + dz);
        spawnedDummy = true;

        GameProfile profile = new GameProfile(
                UUID.fromString("32d0a964-137a-2c03-7cc9-df6700000001"), "NEURO_DUMMY");
        dummy = new OtherClientPlayerEntity(mc.world, profile);
        dummy.refreshPositionAndAngles(dummyCenter.x, dummyCenter.y, dummyCenter.z, 0, 0);
        dummy.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        dummy.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        dummy.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        dummy.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        dummy.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
        dummy.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        dummy.setHealth(20F);
        dummy.setGlowing(true);
        mc.world.addEntity(dummy);
    }

    private void removeDummy() {
        if (dummy != null && spawnedDummy) {
            dummy.discard();
        }
        dummy = null;
        spawnedDummy = false;
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (recording) {
            attackTicksAgo = 0;
        }
    }

    @EventHandler
    public void onDisplay(EventDisplay e) {
        if (!recording || mc.player == null) return;

        float yaw = Rotation.cameraYaw();
        float pitch = Rotation.cameraPitch();
        if (!frameInit) {
            frameLastYaw = yaw;
            frameLastPitch = pitch;
            frameInit = true;
            return;
        }

        float dYaw = MathHelper.wrapDegrees(yaw - frameLastYaw);
        float dPitch = pitch - frameLastPitch;
        float step = (float) Math.hypot(dYaw, dPitch);
        framePath += step;
        framePeak = Math.max(framePeak, step);
        frameCount++;
        frameLastYaw = yaw;
        frameLastPitch = pitch;
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (!recording || mc.player == null || mc.world == null || dummy == null || dummyCenter == null) return;

        // экран открыт или окно не в фокусе — мышь не управляет камерой,
        // такие тики в датасет не пишем, только сбрасываем состояние
        if (mc.currentScreen != null || !mc.isWindowFocused()) {
            lastYaw = Rotation.cameraYaw();
            lastPitch = Rotation.cameraPitch();
            prevAppliedYaw = 0F;
            prevAppliedPitch = 0F;
            framePath = 0F;
            framePeak = 0F;
            frameCount = 0;
            frameInit = false;
            emaSignedYaw = 0F;
            emaSignedPitch = 0F;
            trajGapPending = true;
            return;
        }

        long now = System.currentTimeMillis() - trainStart;
        double ang = now / 650.0;
        double offX = Math.cos(ang) * 1.8;
        double offZ = Math.sin(ang * 0.8) * 1.4;
        double offY = Math.sin(now / 500.0) * 0.7;
        if (spawnedDummy) {
            dummy.refreshPositionAndAngles(dummyCenter.x + offX, dummyCenter.y + offY, dummyCenter.z + offZ, 0, 0);
        }
        dummy.setHealth(20F);

        float curYaw = Rotation.cameraYaw();
        float curPitch = Rotation.cameraPitch();
        Vec3d centerAim = dummy.getBoundingBox().getCenter().subtract(mc.player.getEyePos());
        float centerYaw = yawTo(centerAim);
        float centerPitch = pitchTo(centerAim);
        Vec3d hitPoint = currentHitPoint(dummy, curYaw, curPitch);
        float[] hitNorm = normalizeHitPoint(dummy.getBoundingBox(), hitPoint);
        lastHitX = hitNorm[0];
        lastHitY = hitNorm[1];
        lastHitZ = hitNorm[2];

        Vec3d pointAim = hitPoint.subtract(mc.player.getEyePos());
        float pointYaw = yawTo(pointAim);
        float pointPitch = pitchTo(pointAim);
        float yawErr = MathHelper.wrapDegrees(centerYaw - lastYaw);
        float pitchErr = centerPitch - lastPitch;
        float pointYawErr = MathHelper.wrapDegrees(pointYaw - lastYaw);
        float pointPitchErr = pointPitch - lastPitch;
        float appliedYaw = MathHelper.wrapDegrees(curYaw - lastYaw);
        float appliedPitch = curPitch - lastPitch;
        float yawShake = MathHelper.wrapDegrees(curYaw - pointYaw);
        float pitchShake = curPitch - pointPitch;
        float distance = (float) mc.player.getEyePos().distanceTo(hitPoint);

        // EMA-сглаживание скоростей: сеть учит "крейсерскую" скорость руки,
        // а не шумную дельту одного тика
        emaYawSpeed += (Math.abs(appliedYaw) - emaYawSpeed) * 0.45F;
        emaPitchSpeed += (Math.abs(appliedPitch) - emaPitchSpeed) * 0.45F;

        // фулл-запись почерка мыши: дёрганая составляющая = движение минус его плавный тренд.
        // Пишется всегда, независимо от того, куда смотрит прицел относительно хитбокса
        float strokeJitterYaw = appliedYaw - emaSignedYaw;
        float strokeJitterPitch = appliedPitch - emaSignedPitch;
        emaSignedYaw += (appliedYaw - emaSignedYaw) * 0.35F;
        emaSignedPitch += (appliedPitch - emaSignedPitch) * 0.35F;
        recStrokes.add(new float[]{
                strokeJitterYaw,
                strokeJitterPitch,
                (float) Math.hypot(emaSignedYaw, emaSignedPitch)
        });

        // траектория: ошибка до цели В НАЧАЛЕ тика -> что мышь сделала за этот тик,
        // плюс сколько тиков прошло с удара (так запоминается и подлёт, и отвод после удара)
        attackTicksAgo = Math.min(attackTicksAgo + 1, 999);
        if (trajGapPending) {
            recTraj.add(new float[]{0F, 0F, 0F, 0F, -1F});
            trajGapPending = false;
        }
        recTraj.add(new float[]{
                yawErr,
                pitchErr,
                appliedYaw,
                appliedPitch,
                Math.min(attackTicksAgo, 60)
        });

        // плавность: отношение чистого смещения к длине покадрового пути за тик.
        // 1 = мышь шла ровной дугой, ближе к 0 = дёргалась туда-сюда (джитер)
        float displacement = (float) Math.hypot(appliedYaw, appliedPitch);
        float smoothness = framePath < 0.02F ? 1F : MathHelper.clamp(displacement / framePath, 0F, 1F);
        // пиковый рывок: самый резкий покадровый скачок, приведённый к скорости за тик
        float peak = frameCount <= 0 ? 0F
                : MathHelper.clamp(framePeak * frameCount / NeuroModel.MAX_PEAK_SPEED, 0F, 1F);
        framePath = 0F;
        framePeak = 0F;
        frameCount = 0;

        recX.add(NeuroModel.features(
                yawErr, pitchErr,
                appliedYaw, appliedPitch,
                prevAppliedYaw, prevAppliedPitch,
                lastHitX, lastHitY, lastHitZ,
                distance,
                prevSmoothness, prevPeak
        ));
        recY.add(new double[]{
                MathHelper.clamp(emaYawSpeed, 0F, NeuroModel.MAX_YAW_SPEED) / NeuroModel.MAX_YAW_SPEED,
                MathHelper.clamp(emaPitchSpeed, 0F, NeuroModel.MAX_PITCH_SPEED) / NeuroModel.MAX_PITCH_SPEED,
                lastHitX,
                lastHitY,
                lastHitZ,
                NeuroModel.signedToUnit(yawShake, NeuroModel.MAX_YAW_SHAKE),
                NeuroModel.signedToUnit(pitchShake, NeuroModel.MAX_PITCH_SHAKE),
                NeuroModel.signedToUnit(appliedYaw, NeuroModel.MAX_YAW_LEAD),
                NeuroModel.signedToUnit(appliedPitch, NeuroModel.MAX_PITCH_LEAD),
                NeuroModel.signedToUnit(appliedYaw - prevAppliedYaw, NeuroModel.MAX_YAW_ACCEL),
                NeuroModel.signedToUnit(appliedPitch - prevAppliedPitch, NeuroModel.MAX_PITCH_ACCEL),
                smoothness,
                peak
        });

        prevSmoothness = smoothness;
        prevPeak = peak;
        prevAppliedYaw = appliedYaw;
        prevAppliedPitch = appliedPitch;
        lastYaw = curYaw;
        lastPitch = curPitch;
    }

    private Vec3d currentHitPoint(OtherClientPlayerEntity target, float yaw, float pitch) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(rotationVector(yaw, pitch).multiply(8.0D));
        java.util.Optional<Vec3d> hit = target.getBoundingBox().raycast(eye, end);
        if (hit.isPresent()) return hit.get();

        Box box = target.getBoundingBox();
        return new Vec3d(
                MathHelper.lerp(lastHitX, box.minX, box.maxX),
                MathHelper.lerp(lastHitY, box.minY, box.maxY),
                MathHelper.lerp(lastHitZ, box.minZ, box.maxZ)
        );
    }

    private static float[] normalizeHitPoint(Box box, Vec3d point) {
        return new float[]{
                normalize((float) ((point.x - box.minX) / Math.max(0.001D, box.maxX - box.minX))),
                normalize((float) ((point.y - box.minY) / Math.max(0.001D, box.maxY - box.minY))),
                normalize((float) ((point.z - box.minZ) / Math.max(0.001D, box.maxZ - box.minZ)))
        };
    }

    private static float normalize(float value) {
        return MathHelper.clamp(value, 0F, 1F);
    }

    private static Vec3d rotationVector(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        return new Vec3d(-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
    }

    private static float usefulMovement(float applied, float error) {
        if (Math.abs(applied) < 0.001F || Math.abs(error) < 0.001F) return 0F;
        return Math.signum(applied) == Math.signum(error) ? Math.abs(applied) : 0F;
    }

    private static float yawTo(Vec3d aim) {
        return (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
    }

    private static float pitchTo(Vec3d aim) {
        return (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z))), -90F, 90F);
    }

    public float[] predict(float yawErr, float pitchErr) {
        if (current == null || current.samples <= 0) {
            return new float[]{90F, 90F};
        }
        return current.predict(yawErr, pitchErr);
    }

    public NeuroModel.Prediction predictDetailed(float yawErr, float pitchErr) {
        if (current == null || current.samples <= 0) {
            return new NeuroModel.Prediction(90F, 90F, 0.5F, 0.5F, 0.5F, 0F, 0F, 0F, 0F, 0F, 0F, 1F, 0F, false);
        }
        return current.predictDetailed(yawErr, pitchErr);
    }

    public NeuroModel.Prediction predictDetailed(double[] features) {
        if (current == null || current.samples <= 0) {
            return new NeuroModel.Prediction(90F, 90F, 0.5F, 0.5F, 0.5F, 0F, 0F, 0F, 0F, 0F, 0F, 1F, 0F, false);
        }
        return current.predictDetailed(features);
    }

    /** Полная копия движения: записанный сдвиг камеры под текущую ситуацию, null если записи нет. */
    public float[] nextMove(float yawErr, float pitchErr, float sinceAttack) {
        if (current == null) return null;
        return current.nextMove(yawErr, pitchErr, sinceAttack);
    }

    /** Следующий кусочек записанного почерка руки {jitterYaw, jitterPitch} под текущую скорость. */
    public float[] nextJitter(float speed) {
        if (current == null) return new float[]{0F, 0F};
        return current.nextJitter(speed);
    }

    public boolean hasTrainedModel() {
        return current != null && current.samples > 0;
    }
}

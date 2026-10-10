package ru.decide.module.impl.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.decide.utils.render.ModelBoxCapture;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Снимки модели сущностей «в опасности» (получили урон / низкое HP / только что атакованы).
 *
 * Нужны для агрессивного детекта: на серверах без анимации смерти сущность просто
 * исчезает. Тогда берём последний снимок и разваливаем его — эффект срабатывает
 * даже без единого подтверждения смерти.
 */
public final class ModelCollapseSnapshots {

    private static final long CAPTURE_INTERVAL_MS = 100L;
    private static final long IDLE_INTERVAL_MS = 400L;
    private static final long SNAPSHOT_TTL_MS = 2500L;
    private static final long SNAPSHOT_USE_MS = 1200L;
    private static final long ATTACK_MEMORY_MS = 2000L;
    private static final int MAX_TRACKED = 8;
    private static final int MAX_CAPTURES_PER_FRAME = 2;
    private static final double SNAPSHOT_RANGE = 32.0;
    private static final float DANGER_HEALTH = 0.6f;

    private static final Map<Integer, Snapshot> snapshots = new HashMap<>();
    private static final Map<Integer, Long> lastCapture = new HashMap<>();
    private static final Map<Integer, Long> attacked = new HashMap<>();

    private ModelCollapseSnapshots() {
    }

    public static void noteAttack(LivingEntity entity) {
        attacked.put(entity.getId(), System.currentTimeMillis());
    }

    public static void clear() {
        snapshots.clear();
        lastCapture.clear();
        attacked.clear();
    }

    public static void tick(float partialTick, double maxDistance, Predicate<LivingEntity> filter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld level = client.world;
        if (level == null) return;

        ClientPlayerEntity player = client.player;
        if (player == null) return;

        long now = System.currentTimeMillis();
        snapshots.values().removeIf(s -> now - s.takenMs() > SNAPSHOT_TTL_MS);
        lastCapture.values().removeIf(at -> now - at > SNAPSHOT_TTL_MS);
        attacked.values().removeIf(at -> now - at > ATTACK_MEMORY_MS);

        double range = Math.min(maxDistance, SNAPSHOT_RANGE);
        double maxSq = range * range;

        int taken = 0;
        for (Entity entity : level.getEntities()) {
            if (taken >= MAX_CAPTURES_PER_FRAME) break;
            if (!(entity instanceof LivingEntity living) || entity == player) continue;
            if (living.isRemoved() || !living.isAlive()) continue;
            if (entity.squaredDistanceTo(player) > maxSq) continue;
            if (!filter.test(living)) continue;

            boolean danger = inDanger(living, now);
            if (!danger && snapshots.size() >= MAX_TRACKED && !snapshots.containsKey(living.getId())) continue;

            Long last = lastCapture.get(living.getId());
            if (last != null && now - last < (danger ? CAPTURE_INTERVAL_MS : IDLE_INTERVAL_MS)) continue;
            lastCapture.put(living.getId(), now);

            List<ModelBoxCapture.Box> boxes = ModelBoxCapture.capture(living, partialTick, false);
            if (boxes.isEmpty()) continue;

            Vec3d motion = living.getVelocity();
            snapshots.put(living.getId(), new Snapshot(boxes,
                    MathHelper.lerp(partialTick, entity.lastRenderX, living.getX()),
                    MathHelper.lerp(partialTick, entity.lastRenderY, living.getY()),
                    MathHelper.lerp(partialTick, entity.lastRenderZ, living.getZ()),
                    (float) motion.x, (float) motion.y, (float) motion.z,
                    living.getHeight(), now, danger));
            taken++;
        }
    }

    /** Вызывает handler для сущностей, которые только что исчезли; handler возвращает false — снимок не тратим. */
    public static void collectVanished(long maxAgeMs, BiPredicate<Integer, Snapshot> handler) {
        if (snapshots.isEmpty()) return;

        ClientWorld level = MinecraftClient.getInstance().world;
        if (level == null) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Snapshot>> it = snapshots.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Snapshot> entry = it.next();
            Snapshot snapshot = entry.getValue();

            if (now - snapshot.takenMs() > maxAgeMs || !snapshot.danger()) continue;

            int id = entry.getKey();
            Entity entity = level.getEntityById(id);
            boolean gone = entity == null || entity.isRemoved()
                    || (entity instanceof LivingEntity living && !living.isAlive());
            if (!gone) continue;

            if (!handler.test(id, snapshot)) continue;
            it.remove();
            lastCapture.remove(id);
        }
    }

    public static Snapshot take(int entityId) {
        Snapshot snapshot = snapshots.remove(entityId);
        if (snapshot == null) return null;

        lastCapture.remove(entityId);
        if (System.currentTimeMillis() - snapshot.takenMs() > SNAPSHOT_USE_MS) return null;
        return snapshot;
    }

    private static boolean inDanger(LivingEntity entity, long now) {
        if (entity.hurtTime > 0) return true;

        float max = entity.getMaxHealth();
        if (max > 0.0f && entity.getHealth() <= max * DANGER_HEALTH) return true;

        Long hit = attacked.get(entity.getId());
        return hit != null && now - hit <= ATTACK_MEMORY_MS;
    }

    public record Snapshot(List<ModelBoxCapture.Box> boxes,
                           double x, double y, double z,
                           float motionX, float motionY, float motionZ,
                           float height, long takenMs, boolean danger) {
    }
}

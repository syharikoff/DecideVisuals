package ru.white.module.impl.render;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.white.manager.event_impl.CameraPositionEvent;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.DelimiterSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.other.Instance;
import ru.white.utils.render.ExplosionWavePipeline;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Порт Kimiko ExplosionWave — ударная волна при взрыве: преломление мира
 * по фронту волны, хроматическая аберрация, вспышка и тряска камеры.
 *
 * Волны ловятся по пакетам: ExplosionS2CPacket (ТНТ/кристаллы),
 * звуку взрыва и частицам. Взрывы рядом с кристаллом end-crystal
 * отфильтровываются — там волна от перехода комнаты, а не от взрыва.
 */
@ModuleInfo(
        name = "Explosion Wave",
        desc = "Ударная волна, преломление и тряска при взрыве",
        category = Category.VISUALS
)
public final class ExplosionWave extends Module {

    private static final Logger LOGGER = LoggerFactory.getLogger("client/ExplosionWave");

    public static ExplosionWave getInstance() {
        return Instance.get(ExplosionWave.class);
    }

    private static final int MAX_WAVES = 8;    private static final int WAVE_STRIDE = 12;
    private static final int WAVE_OFFSET = 24;
    private static final double BLAST_DEDUP_SQ = 12.25;
    private static final double CRYSTAL_PROXIMITY_SQ = 16.0;

    // ---------- Основное ----------
    public final DelimiterSetting generalSeparator = new DelimiterSetting(this, "Основное");
    public final SliderSetting waveTime = new SliderSetting(this, "Время волны", 2000F, 600F, 3000F, 50F);
    public final SliderSetting waveRadius = new SliderSetting(this, "Радиус волны", 15F, 5F, 30F, 0.5F);
    public final SliderSetting maxDistance = new SliderSetting(this, "Дистанция", 50F, 10F, 96F, 1F);

    // ---------- Искажение ----------
    public final DelimiterSetting distortSeparator = new DelimiterSetting(this, "Искажение");
    public final SliderSetting distortStrength = new SliderSetting(this, "Сила искажения", 3.0F, 0.2F, 3.0F, 0.1F);
    public final SliderSetting waveThickness = new SliderSetting(this, "Толщина", 25F, 10F, 60F, 1F);
    public final BooleanSetting chromatic = new BooleanSetting(this, "Хроматика", true);
    public final SliderSetting chromaticStrength = new SliderSetting(this, "Сила хроматики", 1.5F, 0.2F, 3.0F, 0.1F)
            .setVisible(() -> chromatic.getValue());

    // ---------- Вспышка ----------
    public final DelimiterSetting flashSeparator = new DelimiterSetting(this, "Вспышка");
    public final BooleanSetting flash = new BooleanSetting(this, "Вспышка", true);
    public final SliderSetting flashStrength = new SliderSetting(this, "Сила вспышки", 50F, 10F, 100F, 1F)
            .setVisible(() -> flash.getValue());

    // ---------- Тряска ----------
    public final DelimiterSetting shakeSeparator = new DelimiterSetting(this, "Тряска");
    public final BooleanSetting shake = new BooleanSetting(this, "Тряска экрана", true);
    public final SliderSetting shakeStrength = new SliderSetting(this, "Сила тряски", 1.5F, 0.2F, 3.0F, 0.1F)
            .setVisible(() -> shake.getValue());
    public final SliderSetting shakeTime = new SliderSetting(this, "Время тряски", 1000F, 200F, 1500F, 50F)
            .setVisible(() -> shake.getValue());

    private final Queue<PendingBlast> pending = new ArrayDeque<>();
    private final List<Wave> waves = new ArrayList<>();
    private final List<RecentBlast> recentBlasts = new ArrayList<>();
    private final Map<Integer, RecentBlast> crystalTracks = new HashMap<>();

    private final Matrix4f invViewProj = new Matrix4f();
    private final Matrix4f viewProj = new Matrix4f();
    private final Vector4f projScratch = new Vector4f();
    private final float[] uniformScratch = new float[ExplosionWavePipeline.UNIFORM_FLOATS];

    private long enabledAtMillis;
    private float shakeAmp;
    private long shakeUntil;

    @Override
    public void setup() {
        super.setup();
        // fail-fast: регистрируем пайплайн на старте, чтобы сбой был виден в логе сразу
        if (ExplosionWavePipeline.deviceReady() && !ExplosionWavePipeline.validate()) {
            LOGGER.warn("[ExplosionWave] pipeline init failed, waves will not render");
        }
    }

    @Override
    protected void onEnable() {
        enabledAtMillis = System.currentTimeMillis();
        // повторная проверка: девайс на старте мог быть не готов
        if (ExplosionWavePipeline.deviceReady() && !ExplosionWavePipeline.validate()) {
            LOGGER.warn("[ExplosionWave] pipeline unavailable, waves will not render");
        }
    }

    /** Плавное появление эффектов после включения. */
    public float visualAlpha() {
        if (!isEnabled()) return 0.0F;
        long elapsed = System.currentTimeMillis() - enabledAtMillis;
        if (elapsed >= 1000L) return 1.0F;
        return Math.max(0.0F, elapsed / 1000.0F);
    }

    @Override
    protected void onDisable() {
        pending.clear();
        waves.clear();
        recentBlasts.clear();
        crystalTracks.clear();
        shakeAmp = 0.0F;
        shakeUntil = 0L;
        ExplosionWavePipeline.clear();
    }

    // ================= Отслеживание взрывов =================

    @EventHandler
    public void onPacket(EventPacket event) {
        if (event.isSend()) return;
        Packet<?> packet = event.getPacket();

        if (packet instanceof ExplosionS2CPacket explosion) {
            if (!isWindBurst(explosion)) {
                pending.add(new PendingBlast(explosion.center(), explosion.radius()));
            }
            return;
        }

        if (packet instanceof PlaySoundS2CPacket sound
                && sound.getSound() != null
                && sound.getSound().value() == SoundEvents.ENTITY_GENERIC_EXPLODE.value()) {
            pending.add(new PendingBlast(new Vec3d(sound.getX(), sound.getY(), sound.getZ()), 4.0F));
            return;
        }

        if (packet instanceof ParticleS2CPacket particles) {
            ParticleEffect params = particles.getParameters();
            if (params == null) return;
            ParticleType type = params.getType();
            if (type == ParticleTypes.EXPLOSION_EMITTER) {
                pending.add(new PendingBlast(new Vec3d(particles.getX(), particles.getY(), particles.getZ()), 4.0F));
            } else if (type == ParticleTypes.EXPLOSION) {
                pending.add(new PendingBlast(new Vec3d(particles.getX(), particles.getY(), particles.getZ()), 2.0F));
            }
        }
    }

    private static boolean isWindBurst(ExplosionS2CPacket packet) {
        ParticleEffect effect = packet.explosionParticle();
        if (effect == null) return false;
        ParticleType type = effect.getType();
        return type == ParticleTypes.GUST
                || type == ParticleTypes.SMALL_GUST
                || type == ParticleTypes.GUST_EMITTER_SMALL
                || type == ParticleTypes.GUST_EMITTER_LARGE;
    }

    @EventHandler
    public void onRender(EventRender3D event) {
        trackCrystals();
        drainPending();

        float life = Math.max(1.0F, waveTime.getValue());
        for (int i = waves.size() - 1; i >= 0; i--) {
            Wave wave = waves.get(i);
            if (wave.progress(life) < 1.0F && isFiniteAndSafe(wave.pos)) continue;
            waves.remove(i);
        }
    }

    private void trackCrystals() {
        ClientWorld level = mc.world;
        if (level == null) return;

        long now = System.currentTimeMillis();
        for (Entity entity : level.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            Vec3d pos = entity.getEntityPos();
            if (!isFiniteAndSafe(pos)) continue;
            crystalTracks.put(crystal.getId(), new RecentBlast(pos, now));
        }
        crystalTracks.values().removeIf(track -> now - track.time > 2000L);
    }

    /** Рядом с end-crystal идёт переход комнаты — такую волну не рисуем. */
    private boolean nearCrystal(Vec3d center) {
        for (RecentBlast track : crystalTracks.values()) {
            if (track.pos.squaredDistanceTo(center) < CRYSTAL_PROXIMITY_SQ) return true;
        }
        return false;
    }

    private void drainPending() {
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        recentBlasts.removeIf(recent -> now - recent.time > 400L);

        PendingBlast blast;
        while ((blast = pending.poll()) != null) {
            if (!isFiniteAndSafe(blast.center)) continue;

            float maxDist = maxDistance.getValue();
            double dist = mc.player.getEntityPos().distanceTo(blast.center);
            if (dist > maxDist) continue;

            boolean duplicate = false;
            for (RecentBlast recent : recentBlasts) {
                if (recent.pos.squaredDistanceTo(blast.center) < BLAST_DEDUP_SQ) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate || nearCrystal(blast.center)) continue;

            recentBlasts.add(new RecentBlast(blast.center, now));

            float falloff = (float) Math.pow(1.0 - dist / maxDist, 1.5);
            float power = Math.max(Math.min(blast.power / 4.0F, 2.5F), 0.6F);

            if (waves.size() >= MAX_WAVES) waves.remove(0);
            waves.add(new Wave(blast.center, power, falloff));

            if (!shake.getValue()) continue;
            float amp = 3.5F * shakeStrength.getValue() * falloff * power;
            if (amp <= 0.05F) continue;
            shakeAmp = Math.max(shakeAmp, amp);
            shakeUntil = Math.max(shakeUntil, now + shakeTime.getValue().longValue());
        }
    }

    // ================= Тряска камеры =================

    @EventHandler
    public void onCameraPosition(CameraPositionEvent event) {
        if (!isEnabled() || !shake.getValue() || shakeAmp <= 0.01F) return;

        long now = System.currentTimeMillis();
        if (now >= shakeUntil) {
            shakeAmp = 0.0F;
            return;
        }

        Vec3d pos = event.getPos();
        if (pos == null) return;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        float a = shakeAmp;
        event.setPos(new Vec3d(
                pos.x + (rnd.nextFloat() - 0.5F) * a,
                pos.y + (rnd.nextFloat() - 0.5F) * a,
                pos.z + (rnd.nextFloat() - 0.5F) * a));
    }

    // ================= Пост-обработка =================

    /**
     * Вызывается из WorldRendererMixin в конце WorldRenderer.render.
     * Волны строятся в экранном пространстве — нужны матрицы кадра.
     */
    public void renderWaves(Framebuffer renderTarget, Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        if (!isEnabled() || renderTarget == null || projectionMatrix == null || positionMatrix == null) return;
        if (waves.isEmpty()) return;
        if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return;
        if (renderTarget.textureWidth <= 0 || renderTarget.textureHeight <= 0) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();

        float life = Math.max(1.0F, waveTime.getValue());
        float maxRadiusSetting = waveRadius.getValue();
        float baseAmp = 0.022F * distortStrength.getValue() * visualAlpha();
        float aspect = (float) renderTarget.textureWidth / Math.max(1, renderTarget.textureHeight);

        this.invViewProj.set(projectionMatrix).mul(positionMatrix).invert();
        this.viewProj.set(projectionMatrix).mul(positionMatrix);

        float[] data = this.uniformScratch;
        int count = 0;
        float globalFlash = 0.0F;

        for (Wave wave : waves) {
            if (count >= MAX_WAVES) break;

            float progress = wave.progress(life);
            if (progress < 0.0F || progress >= 1.0F) continue;

            float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
            float maxRadius = maxRadiusSetting * wave.power;
            float radius = Math.max(eased * maxRadius, 0.05F);
            float thickness = Math.max(0.3F, maxRadius * waveThickness.getValue() / 100.0F);

            float fadeIn = MathHelper.clamp(progress / 0.05F, 0.0F, 1.0F);
            float fadeOut = MathHelper.clamp((1.0F - progress) / 0.4F, 0.0F, 1.0F);
            float env = fadeIn * fadeOut * wave.falloff;
            if (env <= 0.001F) continue;

            float waveFlash = flash.getValue()
                    ? Math.max(0.0F, 1.0F - progress / 0.12F) * wave.falloff
                    : 0.0F;
            globalFlash = Math.max(globalFlash, waveFlash * flashStrength.getValue() / 100.0F);

            float cx = (float) (wave.pos.x - cam.x);
            float cy = (float) (wave.pos.y - cam.y);
            float cz = (float) (wave.pos.z - cam.z);

            this.projScratch.set(cx, cy, cz, 1.0F);
            this.viewProj.transform(this.projScratch);

            float sx = 0.0F;
            float sy = 0.0F;
            float valid = 0.0F;
            if (this.projScratch.w > 0.001F) {
                sx = this.projScratch.x / this.projScratch.w * 0.5F + 0.5F;
                sy = this.projScratch.y / this.projScratch.w * 0.5F + 0.5F;
                valid = 1.0F;
            }

            int base = WAVE_OFFSET + count * WAVE_STRIDE;
            data[base] = cx;
            data[base + 1] = cy;
            data[base + 2] = cz;
            data[base + 3] = radius;
            data[base + 4] = thickness;
            data[base + 5] = baseAmp * env;
            data[base + 6] = env;
            data[base + 7] = waveFlash;
            data[base + 8] = sx;
            data[base + 9] = sy;
            data[base + 10] = valid;
            data[base + 11] = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            count++;
        }

        if (count == 0) return;

        data[0] = count;
        data[1] = aspect;
        data[2] = chromatic.getValue() ? 0.35F * chromaticStrength.getValue() : 0.0F;
        data[3] = globalFlash;
        data[4] = 0.35F;
        data[5] = 0.0F;
        data[6] = 0.0F;
        data[7] = 0.0F;
        this.invViewProj.get(data, 8);

        ExplosionWavePipeline.apply(renderTarget, data);
    }

    private static boolean isFiniteAndSafe(Vec3d pos) {
        return isFiniteAndSafe(pos.x) && isFiniteAndSafe(pos.y) && isFiniteAndSafe(pos.z);
    }

    private static boolean isFiniteAndSafe(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 3.0E7;
    }

    // ================= Данные =================

    private static final class PendingBlast {
        private final Vec3d center;
        private final float power;

        private PendingBlast(Vec3d center, float power) {
            this.center = center;
            this.power = power;
        }
    }

    private static final class Wave {
        private final Vec3d pos;
        private final float power;
        private final float falloff;
        private final long time;

        private Wave(Vec3d pos, float power, float falloff) {
            this.pos = pos;
            this.power = power;
            this.falloff = falloff;
            this.time = System.currentTimeMillis();
        }

        private float progress(float life) {
            return (System.currentTimeMillis() - time) / life;
        }
    }

    private static final class RecentBlast {
        private final Vec3d pos;
        private final long time;

        private RecentBlast(Vec3d pos, long time) {
            this.pos = pos;
            this.time = time;
        }
    }
}

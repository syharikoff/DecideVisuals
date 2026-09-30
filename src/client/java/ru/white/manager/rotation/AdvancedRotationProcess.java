package ru.white.manager.rotation;

import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.utils.aura.GCDUtil;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

import static net.minecraft.util.math.MathHelper.wrapDegrees;

public class AdvancedRotationProcess extends Component {

    public static RotationTask currentTask = RotationTask.IDLE;
    public static int  currentPriority;
    public static int  currentTimeout;
    public static int  idleTicks;
    public static Rotation targetRotation;

    public static float currentYawSpeed;
    public static float currentPitchSpeed;
    public static float currentYawReturnSpeed;
    public static float currentPitchReturnSpeed;

    // тряска при прицеливании — прямое значение (волна, рандом — на усмотрение вызывающего)
    public static float shakeYaw;
    public static float shakePitch;
    // тряска при возврате — случайный шум с амплитудой, затухает за shakeDuration тиков
    public static float resetShakeAmplitudeYaw;
    public static float resetShakeAmplitudePitch;
    public static int   shakeDuration;
    private static int  resetShakeTicks;

    private static final Random RNG = new Random();


    // ───────────────────────────────────────────────────────────────────────────

    public static boolean isRotating() {
        return !currentTask.equals(RotationTask.IDLE);
    }

    @EventHandler
    public void onEvent(EventTick event) {
        if (currentTask.equals(RotationTask.AIM) && idleTicks > currentTimeout) {
            currentTask = RotationTask.RESET;
            resetShakeTicks = 0;
        }

        if (currentTask.equals(RotationTask.RESET)) {
            resetShakeTicks++;
            doResetRotation();
        }

        idleTicks++;
    }

    private void doResetRotation() {
        Rotation target = new Rotation(FreeLookUtil.freeYaw, FreeLookUtil.freePitch);

        float sYaw = 0f, sPitch = 0f;
        if (shakeDuration > 0 && resetShakeTicks <= shakeDuration) {
            // свежий рандом каждый тик, амплитуда затухает линейно
            float fade = 1f - (float) resetShakeTicks / shakeDuration;
            sYaw   = (RNG.nextFloat() * 2f - 1f) * resetShakeAmplitudeYaw   * fade;
            sPitch = (RNG.nextFloat() * 2f - 1f) * resetShakeAmplitudePitch * fade;
        }

        boolean reached = updateRotation(target, currentYawReturnSpeed, currentPitchReturnSpeed, sYaw, sPitch);

        if (reached && (shakeDuration <= 0 || resetShakeTicks > shakeDuration)) {
            stopRotation();
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public static void update(AdvancedRotationConfig cfg) {
        if (currentPriority > cfg.priority) return;

        if (currentTask.equals(RotationTask.IDLE) && !cfg.clientRotation) {
            FreeLookUtil.active = true;
        }

        currentYawSpeed         = cfg.yawSpeed;
        currentPitchSpeed       = cfg.pitchSpeed;
        currentYawReturnSpeed   = cfg.yawReturnSpeed;
        currentPitchReturnSpeed = cfg.pitchReturnSpeed;
        currentTimeout          = cfg.timeout;
        currentPriority         = cfg.priority;
        shakeYaw                    = cfg.shakeYaw;
        shakePitch                  = cfg.shakePitch;
        resetShakeAmplitudeYaw      = cfg.resetShakeAmplitudeYaw;
        resetShakeAmplitudePitch    = cfg.resetShakeAmplitudePitch;
        shakeDuration               = cfg.shakeDuration;
        currentTask             = RotationTask.AIM;
        targetRotation          = cfg.target;

        // при прицеливании — полная тряска без затухания
        updateRotation(cfg.target, cfg.yawSpeed, cfg.pitchSpeed, cfg.shakeYaw, cfg.shakePitch);
    }

    /** Простой вариант без тряски, как в оригинале */
    public static void update(Rotation target, float turnSpeed, float returnSpeed, int timeout, int priority) {
        update(new AdvancedRotationConfig(target)
                .speed(turnSpeed, returnSpeed)
                .timeout(timeout)
                .priority(priority));
    }

    /** Полный вариант, совместимый с оригинальной сигнатурой */
    public static void update(Rotation target, float yawSpeed, float pitchSpeed,
                              float yawReturnSpeed, float pitchReturnSpeed,
                              int timeout, int priority, boolean clientRotation) {
        update(new AdvancedRotationConfig(target)
                .speed(yawSpeed, pitchSpeed, yawReturnSpeed, pitchReturnSpeed)
                .timeout(timeout)
                .priority(priority)
                .clientRotation(clientRotation));
    }

    public static void resetParentTimeout() {
        currentTimeout = 0;
        currentTask    = RotationTask.IDLE;
        currentPriority = 0;
        FreeLookUtil.setActive(false);
    }

    // ── Core ───────────────────────────────────────────────────────────────────

    static boolean updateRotation(Rotation targetRot, float yawSpeed, float pitchSpeed,
                                   float sYaw, float sPitch) {
        if (mc.player == null) return false;

        Rotation current = new Rotation(mc.player);

        float yawDelta   = wrapDegrees(targetRot.yaw - current.yaw);
        float pitchDelta = targetRot.pitch - current.pitch;

        float clampedYaw   = Math.min(Math.abs(yawDelta),   yawSpeed);
        float clampedPitch = Math.min(Math.abs(pitchDelta), pitchSpeed);

        float moveYaw   = GCDUtil.getSensitivity(MathHelper.clamp(yawDelta,   -clampedYaw,   clampedYaw))
                          + sYaw;
        float movePitch = GCDUtil.getSensitivity(MathHelper.clamp(pitchDelta, -clampedPitch, clampedPitch))
                          + sPitch;

        mc.player.setYaw(mc.player.headYaw += moveYaw);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + movePitch, -90f, 90f));

        idleTicks = 0;
        return new Rotation(mc.player).getDelta(targetRot) < 1f;
    }

    public void stopRotation() {
        currentTask     = RotationTask.IDLE;
        currentPriority = 0;
        FreeLookUtil.setActive(false);
    }

    // ── Task ───────────────────────────────────────────────────────────────────

    public enum RotationTask { AIM, RESET, IDLE }

    // ── Config builder ─────────────────────────────────────────────────────────

    public static class AdvancedRotationConfig {
        public Rotation target;
        public float yawSpeed         = 180f;
        public float pitchSpeed       = 180f;
        public float yawReturnSpeed   = 180f;
        public float pitchReturnSpeed = 180f;
        // прицеливание: прямое значение (передаёшь сам — волна, константа и т.д.)
        public float shakeYaw                = 0f;
        public float shakePitch              = 0f;
        // возврат: амплитуда случайного шума, затухает за shakeDuration тиков
        public float resetShakeAmplitudeYaw  = 0f;
        public float resetShakeAmplitudePitch= 0f;
        public int   shakeDuration           = 0;
        public int   timeout          = 1;
        public int   priority         = 0;
        public boolean clientRotation = false;

        public AdvancedRotationConfig(Rotation target) {
            this.target = target;
        }

        /** Одна скорость для прицеливания и возврата */
        public AdvancedRotationConfig speed(float turnSpeed, float returnSpeed) {
            this.yawSpeed         = turnSpeed;
            this.pitchSpeed       = turnSpeed;
            this.yawReturnSpeed   = returnSpeed;
            this.pitchReturnSpeed = returnSpeed;
            return this;
        }

        /** Раздельные скорости по осям */
        public AdvancedRotationConfig speed(float yawSpeed, float pitchSpeed,
                                             float yawReturnSpeed, float pitchReturnSpeed) {
            this.yawSpeed         = yawSpeed;
            this.pitchSpeed       = pitchSpeed;
            this.yawReturnSpeed   = yawReturnSpeed;
            this.pitchReturnSpeed = pitchReturnSpeed;
            return this;
        }

        /**
         * Тряска при прицеливании — прямое значение каждый тик.
         * Передаёшь сам (волна, рандом, константа — что угодно).
         */
        public AdvancedRotationConfig shake(float yaw, float pitch) {
            this.shakeYaw   = yaw;
            this.shakePitch = pitch;
            return this;
        }

        /**
         * Тряска при возврате к камере — случайный шум с амплитудой,
         * затухает линейно за durationTicks тиков.
         */
        public AdvancedRotationConfig resetShake(float amplitudeYaw, float amplitudePitch, int durationTicks) {
            this.resetShakeAmplitudeYaw   = amplitudeYaw;
            this.resetShakeAmplitudePitch = amplitudePitch;
            this.shakeDuration            = durationTicks;
            return this;
        }

        public AdvancedRotationConfig timeout(int ticks) {
            this.timeout = ticks;
            return this;
        }

        public AdvancedRotationConfig priority(int p) {
            this.priority = p;
            return this;
        }

        public AdvancedRotationConfig clientRotation(boolean v) {
            this.clientRotation = v;
            return this;
        }
    }
}

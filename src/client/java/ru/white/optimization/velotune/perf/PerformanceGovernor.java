/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 */
package ru.white.optimization.velotune.perf;

import ru.white.optimization.velotune.VeloTuneManager;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class PerformanceGovernor {
    private static final long MAX_VALID_FRAME_NANOS = 250000000L;
    private volatile int pressureLevel;
    private volatile long frameIndex;
    private volatile double averageFrameMillis = 16.67;
    private VeloTuneConfig config = new VeloTuneConfig();
    private long frameStartedNanos;
    private int slowFrames;
    private int fastFrames;
    private int cooldownFrames;

    public void configure(VeloTuneConfig config) {
        this.config = config;
        this.pressureLevel = Math.min(this.pressureLevel, config.adaptive.maxPressureLevel);
        this.averageFrameMillis = 1000.0 / (double)config.adaptive.targetFps;
    }

    public void beginFrame() {
        this.frameStartedNanos = System.nanoTime();
    }

    public void endFrame() {
        long started = this.frameStartedNanos;
        if (started == 0L) {
            return;
        }
        this.recordFrameNanos(System.nanoTime() - started);
    }

    void recordFrameNanos(long elapsedNanos) {
        double targetMillis;
        ++this.frameIndex;
        if (elapsedNanos <= 0L || elapsedNanos > 250000000L) {
            return;
        }
        double sampleMillis = (double)elapsedNanos / 1000000.0;
        this.averageFrameMillis += (sampleMillis - this.averageFrameMillis) * 0.08;
        if (!this.config.enabled || !this.config.adaptive.enabled) {
            this.pressureLevel = 0;
            return;
        }
        if (this.cooldownFrames > 0) {
            --this.cooldownFrames;
        }
        if (this.averageFrameMillis > (targetMillis = 1000.0 / (double)this.config.adaptive.targetFps) * 1.07) {
            ++this.slowFrames;
            this.fastFrames = 0;
            if (this.slowFrames >= this.config.adaptive.slowFramesBeforeIncrease && this.cooldownFrames == 0) {
                this.setPressureLevel(Math.min(this.config.adaptive.maxPressureLevel, this.pressureLevel + 1));
                this.slowFrames = 0;
            }
        } else if (this.averageFrameMillis < targetMillis * 0.9) {
            ++this.fastFrames;
            this.slowFrames = 0;
            if (this.fastFrames >= this.config.adaptive.fastFramesBeforeRecovery && this.cooldownFrames == 0) {
                this.setPressureLevel(Math.max(0, this.pressureLevel - 1));
                this.fastFrames = 0;
            }
        } else {
            this.slowFrames = Math.max(0, this.slowFrames - 1);
            this.fastFrames = Math.max(0, this.fastFrames - 1);
        }
        if (this.config.logging.logMetrics && this.config.logging.metricsIntervalFrames > 0 && this.frameIndex % (long)this.config.logging.metricsIntervalFrames == 0L) {
            VeloTuneManager.LOGGER.info("VeloTune metrics: avg={} ms, pressure={}, {}", new Object[]{String.format(Locale.ROOT, "%.2f", this.averageFrameMillis), this.pressureLevel, Metrics.snapshotAndReset()});
        }
    }

    private void setPressureLevel(int next) {
        if (next == this.pressureLevel) {
            return;
        }
        this.pressureLevel = next;
        this.cooldownFrames = this.config.adaptive.levelChangeCooldownFrames;
        VeloTuneManager.LOGGER.debug("Adaptive pressure changed to level {} at {} ms average frame time", (Object)next, (Object)String.format(Locale.ROOT, "%.2f", this.averageFrameMillis));
    }

    public int pressureLevel() {
        return this.pressureLevel;
    }

    public long frameIndex() {
        return this.frameIndex;
    }

    public double averageFrameMillis() {
        return this.averageFrameMillis;
    }
}

/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 */
package ru.white.optimization.velotune.perf;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class VeloTuneConfig {
    private static final int EXTREME_OPTIONS_VERSION = 2;
    public boolean enabled = true;
    public Integer extremeOptionsVersion;
    public Adaptive adaptive = new Adaptive();
    public Players players = new Players();
    public Particles particles = new Particles();
    public Extreme extreme = new Extreme();
    public Logging logging = new Logging();

    public void sanitize() {
        if (this.adaptive == null) {
            this.adaptive = new Adaptive();
        }
        if (this.players == null) {
            this.players = new Players();
        }
        if (this.particles == null) {
            this.particles = new Particles();
        }
        if (this.extreme == null) {
            this.extreme = new Extreme();
        }
        if (this.logging == null) {
            this.logging = new Logging();
        }
        if (this.extremeOptionsVersion == null || this.extremeOptionsVersion < 2) {
            this.disableOptionalExtremeFeatures();
            this.extremeOptionsVersion = 2;
        }
        this.adaptive.targetFps = VeloTuneConfig.clamp(this.adaptive.targetFps, 30, 360);
        this.adaptive.maxPressureLevel = VeloTuneConfig.clamp(this.adaptive.maxPressureLevel, 0, 3);
        this.adaptive.slowFramesBeforeIncrease = VeloTuneConfig.clamp(this.adaptive.slowFramesBeforeIncrease, 4, 240);
        this.adaptive.fastFramesBeforeRecovery = VeloTuneConfig.clamp(this.adaptive.fastFramesBeforeRecovery, 30, 2400);
        this.adaptive.levelChangeCooldownFrames = VeloTuneConfig.clamp(this.adaptive.levelChangeCooldownFrames, 10, 1200);
        this.players.nearPoseDistance = VeloTuneConfig.clamp(this.players.nearPoseDistance, 8.0, 128.0);
        this.players.midPoseDistance = VeloTuneConfig.clamp(this.players.midPoseDistance, this.players.nearPoseDistance, 192.0);
        this.players.farPoseDistance = VeloTuneConfig.clamp(this.players.farPoseDistance, this.players.midPoseDistance, 256.0);
        this.players.featureDistance = VeloTuneConfig.clamp(this.players.featureDistance, 24.0, 256.0);
        this.players.labelDistance = VeloTuneConfig.clamp(this.players.labelDistance, 16.0, 256.0);
        this.players.emergencyCullDistance = VeloTuneConfig.clamp(this.players.emergencyCullDistance, 64.0, 512.0);
        this.players.maxCachedPlayers = VeloTuneConfig.clamp(this.players.maxCachedPlayers, 32, 4096);
        this.particles.spawnBudgetPerFrame = VeloTuneConfig.clamp(this.particles.spawnBudgetPerFrame, 64, 16384);
        this.particles.nearDistance = VeloTuneConfig.clamp(this.particles.nearDistance, 8.0, 96.0);
        this.particles.hardDistance = VeloTuneConfig.clamp(this.particles.hardDistance, this.particles.nearDistance, 512.0);
        this.extreme.verticalCullDepth = VeloTuneConfig.clamp(this.extreme.verticalCullDepth, 0.0, 256.0);
        this.extreme.playerRenderDistance = VeloTuneConfig.clamp(this.extreme.playerRenderDistance, 8.0, 256.0);
        this.extreme.entityRenderDistance = VeloTuneConfig.clamp(this.extreme.entityRenderDistance, 8.0, 256.0);
        this.extreme.blockEntityRenderDistance = VeloTuneConfig.clamp(this.extreme.blockEntityRenderDistance, 8.0, 256.0);
        this.extreme.particleRenderDistance = VeloTuneConfig.clamp(this.extreme.particleRenderDistance, 8.0, 128.0);
        this.extreme.maxRenderedPlayers = VeloTuneConfig.clamp(this.extreme.maxRenderedPlayers, 1, 128);
        this.extreme.maxRenderedEntities = VeloTuneConfig.clamp(this.extreme.maxRenderedEntities, 1, 2048);
        this.extreme.playerPoseInterval = VeloTuneConfig.clamp(this.extreme.playerPoseInterval, 1, 120);
        this.extreme.playerPoseUpdatesPerSecond = VeloTuneConfig.clamp(this.extreme.playerPoseUpdatesPerSecond, 1, 60);
        this.extreme.nameTagTextUpdatesPerSecond = VeloTuneConfig.clamp(this.extreme.nameTagTextUpdatesPerSecond, 1, 240);
        this.extreme.hudUpdatesPerSecond = VeloTuneConfig.clamp(this.extreme.hudUpdatesPerSecond, 1, 240);
        this.extreme.mouseUpdatesPerSecond = VeloTuneConfig.clamp(this.extreme.mouseUpdatesPerSecond, 0, 360);
        this.extreme.particleBudgetPerFrame = VeloTuneConfig.clamp(this.extreme.particleBudgetPerFrame, 16, 2048);
    }

    private void disableOptionalExtremeFeatures() {
        this.extreme.hidePlayerFeatures = false;
        this.extreme.simplifyEquipmentRendering = false;
        this.extreme.nameTagTextBuffer = false;
        this.extreme.hudBuffer = false;
        this.extreme.rawInputBuffer = false;
        this.extreme.disableGlowingOutlines = false;
        this.extreme.disableEntityShadows = false;
        this.extreme.disableClouds = false;
        this.extreme.disableWeather = false;
        this.extreme.cullChunkSections = false;
        this.extreme.cullBlockEntities = false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    @Environment(value=EnvType.CLIENT)
    public static final class Adaptive {
        public boolean enabled = true;
        public int targetFps = 360;
        public int maxPressureLevel = 3;
        public int slowFramesBeforeIncrease = 18;
        public int fastFramesBeforeRecovery = 240;
        public int levelChangeCooldownFrames = 90;
    }

    @Environment(value=EnvType.CLIENT)
    public static final class Players {
        public boolean poseCache = true;
        public double nearPoseDistance = 24.0;
        public double midPoseDistance = 48.0;
        public double farPoseDistance = 96.0;
        public double featureDistance = 96.0;
        public double labelDistance = 64.0;
        public boolean emergencyCullDistantPlayers = true;
        public double emergencyCullDistance = 256.0;
        public int maxCachedPlayers = 1024;
    }

    @Environment(value=EnvType.CLIENT)
    public static final class Particles {
        public boolean enabled = true;
        public int spawnBudgetPerFrame = 1536;
        public double nearDistance = 32.0;
        public double hardDistance = 160.0;
    }

    @Environment(value=EnvType.CLIENT)
    public static final class Extreme {
        public boolean enabled = false;
        public double verticalCullDepth = 20.0;
        public double playerRenderDistance = 64.0;
        public double entityRenderDistance = 48.0;
        public double blockEntityRenderDistance = 12.0;
        public double particleRenderDistance = 12.0;
        public int maxRenderedPlayers = 128;
        public int maxRenderedEntities = 128;
        public int playerPoseInterval = 1;
        public int playerPoseUpdatesPerSecond = 60;
        public int particleBudgetPerFrame = 32;
        public boolean hidePlayerFeatures = false;
        public boolean preservePlayerEquipment = true;
        public boolean simplifyEquipmentRendering = false;
        public boolean hidePlayerLabels = false;
        public boolean preservePlayerLabels = true;
        public boolean nameTagTextBuffer = false;
        public int nameTagTextUpdatesPerSecond = 5;
        public boolean hudBuffer = false;
        public int hudUpdatesPerSecond = 20;
        public boolean rawInputBuffer = false;
        public int mouseUpdatesPerSecond = 240;
        public boolean disableGlowingOutlines = false;
        public boolean disableEntityShadows = false;
        public boolean disableClouds = false;
        public boolean disableWeather = false;
        public boolean cullChunkSections = false;
        public boolean cullBlockEntities = false;
    }

    @Environment(value=EnvType.CLIENT)
    public static final class Logging {
        public boolean logMetrics = false;
        public int metricsIntervalFrames = 1200;
    }
}

package ru.decide.module.impl.utils;

import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.module.api.settings.impl.DelimiterSetting;
import ru.decide.module.api.settings.impl.SliderSetting;
import ru.decide.optimization.velotune.VeloTuneManager;
import ru.decide.optimization.velotune.perf.VeloTuneConfig;
import ru.decide.optimization.velotune.perf.ConfigManager;

@ModuleInfo(
        name = "VeloTune",
        category = Category.UTILITIES,
        desc = "Оптимизация рендера: LOD сущностей, пропуск частиц, кэширование поз, HUD-буфер",
        key = -1
)
public class VeloTuneModule extends Module {

    // ===== Основные =====
    public BooleanSetting enabled = new BooleanSetting(this, "Включить VeloTune", true)
            .onAction(this::syncToConfig);

    public SliderSetting targetFps = new SliderSetting(this, "Целевой FPS", 360f, 30f, 360f, 10f)
            .onAction(this::syncToConfig);

    public SliderSetting maxPressure = new SliderSetting(this, "Макс. давление", 3f, 0f, 3f, 1f)
            .onAction(this::syncToConfig);

    // ===== Экстрим =====
    public DelimiterSetting extremeHeader = new DelimiterSetting(this, "Экстрим режим");

    public BooleanSetting extremeEnabled = new BooleanSetting(this, "Экстрим включён", false)
            .onAction(this::syncToConfig);

    public SliderSetting entityDistance = new SliderSetting(this, "Дистанция сущностей", 48f, 8f, 256f, 1f)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting playerDistance = new SliderSetting(this, "Дистанция игроков", 64f, 8f, 256f, 1f)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting blockEntityDistance = new SliderSetting(this, "Дистанция блок-сущностей", 12f, 8f, 256f, 1f)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting verticalCull = new SliderSetting(this, "Вертикальная отсечка", 20f, 0f, 256f, 1f)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting particleDistance = new SliderSetting(this, "Дистанция частиц", 12f, 8f, 128f, 1f)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting maxPlayers = new SliderSetting(this, "Макс. игроков", 128f, 1f, 128f, 1f)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting maxEntities = new SliderSetting(this, "Макс. сущностей", 128f, 1f, 2048f, 1f)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    // ===== Игроки =====
    public DelimiterSetting playersHeader = new DelimiterSetting(this, "Игроки");

    public BooleanSetting poseCache = new BooleanSetting(this, "Кэш поз", true)
            .onAction(this::syncToConfig);

    public SliderSetting nearPose = new SliderSetting(this, "Ближняя дистанция поз", 24f, 8f, 128f, 1f)
            .setVisible(() -> poseCache.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting midPose = new SliderSetting(this, "Средняя дистанция поз", 48f, 8f, 192f, 1f)
            .setVisible(() -> poseCache.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting farPose = new SliderSetting(this, "Дальняя дистанция поз", 96f, 8f, 256f, 1f)
            .setVisible(() -> poseCache.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting featureDistance = new SliderSetting(this, "Дистанция фич", 96f, 24f, 256f, 1f)
            .onAction(this::syncToConfig);

    public SliderSetting labelDistance = new SliderSetting(this, "Дистанция табличек", 64f, 16f, 256f, 1f)
            .onAction(this::syncToConfig);

    public BooleanSetting emergencyCull = new BooleanSetting(this, "Экстр. отсечение игроков", true)
            .onAction(this::syncToConfig);

    public SliderSetting emergencyDistance = new SliderSetting(this, "Экстр. дистанция", 256f, 64f, 512f, 1f)
            .setVisible(() -> emergencyCull.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting maxCachedPlayers = new SliderSetting(this, "Макс. кэшированных", 1024f, 32f, 4096f, 32f)
            .onAction(this::syncToConfig);

    // ===== Частицы =====
    public DelimiterSetting particlesHeader = new DelimiterSetting(this, "Частицы");

    public BooleanSetting particlesEnabled = new BooleanSetting(this, "Ограничение частиц", true)
            .onAction(this::syncToConfig);

    public SliderSetting particleBudget = new SliderSetting(this, "Бюджет частиц/кадр", 1536f, 64f, 16384f, 64f)
            .setVisible(() -> particlesEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting nearParticleDist = new SliderSetting(this, "Ближняя дист. частиц", 32f, 8f, 96f, 1f)
            .setVisible(() -> particlesEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting hardParticleDist = new SliderSetting(this, "Дальняя дист. частиц", 160f, 8f, 512f, 1f)
            .setVisible(() -> particlesEnabled.getValue())
            .onAction(this::syncToConfig);

    // ===== Экстрим: оптимизации рендера =====
    public DelimiterSetting renderHeader = new DelimiterSetting(this, "Оптимизации рендера");

    public BooleanSetting hideFeatures = new BooleanSetting(this, "Прячать фичи игроков", false)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public BooleanSetting preserveEquipment = new BooleanSetting(this, "Сохр. экипировку", true)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public BooleanSetting simplifyEquip = new BooleanSetting(this, "Упростить экипировку", false)
            .setVisible(() -> extremeEnabled.getValue() && !preserveEquipment.getValue())
            .onAction(this::syncToConfig);

    public BooleanSetting hideLabels = new BooleanSetting(this, "Прячать таблички", false)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public BooleanSetting preserveLabels = new BooleanSetting(this, "Сохр. таблички", true)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public BooleanSetting disableOutlines = new BooleanSetting(this, "Откл. свечение", false)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public BooleanSetting disableShadows = new BooleanSetting(this, "Откл. тени", false)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public BooleanSetting disableClouds = new BooleanSetting(this, "Откл. облака", false)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public BooleanSetting disableWeather = new BooleanSetting(this, "Откл. погоду", false)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public BooleanSetting cullChunks = new BooleanSetting(this, "Отсекать чанки", false)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public BooleanSetting cullBlockEntities = new BooleanSetting(this, "Отсекать блок-сущности", false)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    // ===== HUD и ввод =====
    public DelimiterSetting hudHeader = new DelimiterSetting(this, "HUD и ввод");

    public BooleanSetting nameTagBuffer = new BooleanSetting(this, "Буфер табличек", false)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting nameTagHz = new SliderSetting(this, "Частота табличек (Гц)", 5f, 1f, 240f, 1f)
            .setVisible(() -> extremeEnabled.getValue() && nameTagBuffer.getValue())
            .onAction(this::syncToConfig);

    public BooleanSetting hudBuffer = new BooleanSetting(this, "Буфер HUD", false)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting hudHz = new SliderSetting(this, "Частота HUD (Гц)", 20f, 1f, 240f, 1f)
            .setVisible(() -> extremeEnabled.getValue() && hudBuffer.getValue())
            .onAction(this::syncToConfig);

    public BooleanSetting rawInput = new BooleanSetting(this, "Raw Input буфер", false)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting mouseHz = new SliderSetting(this, "Частота мыши (Гц)", 240f, 0f, 360f, 10f)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting poseHz = new SliderSetting(this, "Частота поз (Гц)", 60f, 1f, 120f, 1f)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting poseInterval = new SliderSetting(this, "Интервал поз (кадры)", 1f, 1f, 120f, 1f)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    public SliderSetting particleBudgetExtreme = new SliderSetting(this, "Бюджет частиц экстр.", 32f, 16f, 2048f, 16f)
            .setVisible(() -> extremeEnabled.getValue())
            .onAction(this::syncToConfig);

    @Override
    protected void onEnable() {
        syncToConfig();
        VeloTuneManager.saveConfig();
    }

    @Override
    protected void onDisable() {
        VeloTuneConfig config = VeloTuneManager.config();
        config.enabled = false;
        VeloTuneManager.saveConfig();
    }

    public void syncToConfig() {
        VeloTuneConfig config = VeloTuneManager.config();

        // Основные
        config.enabled = enabled.getValue();
        config.adaptive.targetFps = targetFps.getValue().intValue();
        config.adaptive.maxPressureLevel = maxPressure.getValue().intValue();

        // Экстрим
        config.extreme.enabled = extremeEnabled.getValue();
        config.extreme.entityRenderDistance = entityDistance.getValue();
        config.extreme.playerRenderDistance = playerDistance.getValue();
        config.extreme.blockEntityRenderDistance = blockEntityDistance.getValue();
        config.extreme.verticalCullDepth = verticalCull.getValue();
        config.extreme.particleRenderDistance = particleDistance.getValue();
        config.extreme.maxRenderedPlayers = maxPlayers.getValue().intValue();
        config.extreme.maxRenderedEntities = maxEntities.getValue().intValue();

        // Игроки
        config.players.poseCache = poseCache.getValue();
        config.players.nearPoseDistance = nearPose.getValue();
        config.players.midPoseDistance = midPose.getValue();
        config.players.farPoseDistance = farPose.getValue();
        config.players.featureDistance = featureDistance.getValue();
        config.players.labelDistance = labelDistance.getValue();
        config.players.emergencyCullDistantPlayers = emergencyCull.getValue();
        config.players.emergencyCullDistance = emergencyDistance.getValue();
        config.players.maxCachedPlayers = maxCachedPlayers.getValue().intValue();

        // Частицы
        config.particles.enabled = particlesEnabled.getValue();
        config.particles.spawnBudgetPerFrame = particleBudget.getValue().intValue();
        config.particles.nearDistance = nearParticleDist.getValue();
        config.particles.hardDistance = hardParticleDist.getValue();

        // Рендер
        config.extreme.hidePlayerFeatures = hideFeatures.getValue();
        config.extreme.preservePlayerEquipment = preserveEquipment.getValue();
        config.extreme.simplifyEquipmentRendering = simplifyEquip.getValue();
        config.extreme.hidePlayerLabels = hideLabels.getValue();
        config.extreme.preservePlayerLabels = preserveLabels.getValue();
        config.extreme.disableGlowingOutlines = disableOutlines.getValue();
        config.extreme.disableEntityShadows = disableShadows.getValue();
        config.extreme.disableClouds = disableClouds.getValue();
        config.extreme.disableWeather = disableWeather.getValue();
        config.extreme.cullChunkSections = cullChunks.getValue();
        config.extreme.cullBlockEntities = cullBlockEntities.getValue();

        // HUD и ввод
        config.extreme.nameTagTextBuffer = nameTagBuffer.getValue();
        config.extreme.nameTagTextUpdatesPerSecond = nameTagHz.getValue().intValue();
        config.extreme.hudBuffer = hudBuffer.getValue();
        config.extreme.hudUpdatesPerSecond = hudHz.getValue().intValue();
        config.extreme.rawInputBuffer = rawInput.getValue();
        config.extreme.mouseUpdatesPerSecond = mouseHz.getValue().intValue();
        config.extreme.playerPoseUpdatesPerSecond = poseHz.getValue().intValue();
        config.extreme.playerPoseInterval = poseInterval.getValue().intValue();
        config.extreme.particleBudgetPerFrame = particleBudgetExtreme.getValue().intValue();

        VeloTuneManager.saveConfig();
    }

    public void loadFromConfig() {
        VeloTuneConfig config = VeloTuneManager.config();

        enabled.set(config.enabled);
        targetFps.set((float) config.adaptive.targetFps);
        maxPressure.set((float) config.adaptive.maxPressureLevel);

        extremeEnabled.set(config.extreme.enabled);
        entityDistance.set((float) config.extreme.entityRenderDistance);
        playerDistance.set((float) config.extreme.playerRenderDistance);
        blockEntityDistance.set((float) config.extreme.blockEntityRenderDistance);
        verticalCull.set((float) config.extreme.verticalCullDepth);
        particleDistance.set((float) config.extreme.particleRenderDistance);
        maxPlayers.set((float) config.extreme.maxRenderedPlayers);
        maxEntities.set((float) config.extreme.maxRenderedEntities);

        poseCache.set(config.players.poseCache);
        nearPose.set((float) config.players.nearPoseDistance);
        midPose.set((float) config.players.midPoseDistance);
        farPose.set((float) config.players.farPoseDistance);
        featureDistance.set((float) config.players.featureDistance);
        labelDistance.set((float) config.players.labelDistance);
        emergencyCull.set(config.players.emergencyCullDistantPlayers);
        emergencyDistance.set((float) config.players.emergencyCullDistance);
        maxCachedPlayers.set((float) config.players.maxCachedPlayers);

        particlesEnabled.set(config.particles.enabled);
        particleBudget.set((float) config.particles.spawnBudgetPerFrame);
        nearParticleDist.set((float) config.particles.nearDistance);
        hardParticleDist.set((float) config.particles.hardDistance);

        hideFeatures.set(config.extreme.hidePlayerFeatures);
        preserveEquipment.set(config.extreme.preservePlayerEquipment);
        simplifyEquip.set(config.extreme.simplifyEquipmentRendering);
        hideLabels.set(config.extreme.hidePlayerLabels);
        preserveLabels.set(config.extreme.preservePlayerLabels);
        disableOutlines.set(config.extreme.disableGlowingOutlines);
        disableShadows.set(config.extreme.disableEntityShadows);
        disableClouds.set(config.extreme.disableClouds);
        disableWeather.set(config.extreme.disableWeather);
        cullChunks.set(config.extreme.cullChunkSections);
        cullBlockEntities.set(config.extreme.cullBlockEntities);

        nameTagBuffer.set(config.extreme.nameTagTextBuffer);
        nameTagHz.set((float) config.extreme.nameTagTextUpdatesPerSecond);
        hudBuffer.set(config.extreme.hudBuffer);
        hudHz.set((float) config.extreme.hudUpdatesPerSecond);
        rawInput.set(config.extreme.rawInputBuffer);
        mouseHz.set((float) config.extreme.mouseUpdatesPerSecond);
        poseHz.set((float) config.extreme.playerPoseUpdatesPerSecond);
        poseInterval.set((float) config.extreme.playerPoseInterval);
        particleBudgetExtreme.set((float) config.extreme.particleBudgetPerFrame);
    }
}

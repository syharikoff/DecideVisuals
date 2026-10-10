package ru.decide.module.impl.render;

import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.Vec3d;
import ru.decide.manager.event_impl.EventRender3D;
import ru.decide.manager.event_impl.EventTick;
import ru.decide.manager.event_impl.WorldLoadEvent;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.module.api.settings.impl.ModeSetting;
import ru.decide.module.api.settings.impl.MultiBooleanSetting;
import ru.decide.module.api.settings.impl.SliderSetting;

/**
 * Оптимизация отрисовки: пресеты качества (Низкий / Средний / Ультра),
 * ограничение дальности прорисовки, отсечение сущностей, блок-сущностей
 * и частиц по расстоянию, отключение блюра/травы/погоды и подстройка
 * качества блюра под текущий FPS.
 */
@ModuleInfo(
        name = "Optimizer",
        desc = "Оптимизация отрисовки",
        category = Category.UTILITIES
)
public class Optimizer extends Module {

    private static final int SAMPLES = 60;

    /** Квадраты дистанций частиц по уровням: 48 / 32 / 22 блока. */
    private static final double[] PARTICLE_DIST_SQ = {2304.0, 1024.0, 484.0};
    /** Дистанции блок-сущностей: 48 / 36 / 24 блока. */
    private static final double[] BLOCK_ENTITY_DIST_SQ = {2304.0, 1296.0, 576.0};
    /** Потолок дистанции сущностей: без лимита / 51 / 29 блоков. */
    private static final double[] ENTITY_TIER_CAP = {-1.0, 51.0, 29.0};
    /** Потолок дальности прорисовки: без лимита / 12 / 8 чанков. */
    private static final int[] RENDER_DISTANCE_CAP = {0, 12, 8};

    private static Optimizer instance;

    public final MultiBooleanSetting options = new MultiBooleanSetting(this, "Опции",
            new BooleanSetting("Синхронизация кадров", true),
            new BooleanSetting("Без размытия", false),
            new BooleanSetting("Без травы", false),
            new BooleanSetting("Без частиц", false),
            new BooleanSetting("Без погоды", false),
            new BooleanSetting("Отсечение сущностей", false),
            new BooleanSetting("Окклюзия сущностей", true),
            new BooleanSetting("Ограничение дальности", true));

    public final SliderSetting entityDistance = new SliderSetting(this, "Дистанция сущностей", 48.0F, 16.0F, 128.0F, 1.0F);
    public final SliderSetting blockEntityDistance = new SliderSetting(this, "Дистанция блок-сущностей", 32.0F, 8.0F, 96.0F, 1.0F);
    public final ModeSetting tier = new ModeSetting(this, "Уровень", "Низкий", "Средний", "Ультра");

    private final long[] frameTimes = new long[SAMPLES];
    private int frameIndex;
    private int frameCount;
    private int currentFps = 60;
    private float qualityLevel = 1.0F;

    private int savedViewDistance = -1;
    private int appliedCap = -1;
    private int lastTier = -1;
    private boolean lastLimit;
    private int lastSeenDist = -1;

    public Optimizer() {
        instance = this;
    }

    public static Optimizer getInstance() {
        return ru.decide.utils.other.Instance.get(Optimizer.class);
    }

    // ───────────────────────────── статические хуки для миксинов ─────────────────────────────

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    public int tierIndex() {
        if (tier.is("Ультра")) return 2;
        return tier.is("Средний") ? 1 : 0;
    }

    private boolean option(String name) {
        return options.getValue(name);
    }

    public static boolean isNoBlur() {
        return active() && instance.option("Без размытия");
    }

    public static boolean isNoGrass() {
        return active() && instance.option("Без травы");
    }

    public static boolean isNoParticles() {
        return active() && instance.option("Без частиц");
    }

    public static boolean isNoWeather() {
        return active() && instance.option("Без погоды");
    }

    public static boolean occludeEntities() {
        return active() && instance.option("Окклюзия сущностей");
    }

    public static int occludeTier() {
        return instance == null ? 0 : instance.tierIndex();
    }

    /** На «Ультра» облака не рисуются. */
    public static boolean cullClouds() {
        return active() && instance.tierIndex() == 2;
    }

    /** Дистанция отсечения сущностей; -1 — не отсекаем. */
    public static float entityCullDist() {
        if (active() && instance.option("Отсечение сущностей")) {
            double cap = ENTITY_TIER_CAP[instance.tierIndex()];
            double distance = instance.entityDistance.getValue();
            return (float) (cap > 0.0 ? Math.min(distance, cap) : distance);
        }
        return -1.0F;
    }

    /** Дистанция отсечения блок-сущностей; -1 — не отсекаем. */
    public static float blockEntityCullDist() {
        if (!active()) return -1.0F;

        double cap = Math.sqrt(BLOCK_ENTITY_DIST_SQ[instance.tierIndex()]);
        if (instance.option("Отсечение сущностей")) {
            cap = Math.min(cap, instance.blockEntityDistance.getValue());
        }
        return (float) cap;
    }

    /** Пропускать ли частицу по её мировой позиции. */
    public static boolean allowParticle(net.minecraft.client.particle.Particle particle) {
        if (!active()) return true;
        if (instance.option("Без частиц")) return false;

        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) return true;

        Vec3d cam = client.gameRenderer.getCamera().getCameraPos();
        if (cam == null) return true;

        var box = particle.getBoundingBox();
        double x = (box.minX + box.maxX) * 0.5;
        double y = (box.minY + box.maxY) * 0.5;
        double z = (box.minZ + box.maxZ) * 0.5;

        double dx = x - cam.x;
        double dy = y - cam.y;
        double dz = z - cam.z;
        return dx * dx + dy * dy + dz * dz <= PARTICLE_DIST_SQ[instance.tierIndex()];
    }

    /** Шагов kawase-блюра: меньше qualityLevel — меньше проходов. */
    public static int blurSteps() {
        if (!active()) return 5;
        return Math.max(2, Math.round(2.0F + 3.0F * instance.qualityLevel));
    }

    public static float blurOffset() {
        if (!active()) return 1.0F;
        return 1.0F + 1.5F * (1.0F - instance.qualityLevel);
    }

    public static boolean fastGradient() {
        return active() && instance.qualityLevel < 0.7F;
    }

    public static boolean flatBackdrop() {
        return active() && instance.qualityLevel < 0.5F;
    }

    public static int estimatedFps() {
        return instance == null ? 60 : instance.currentFps;
    }

    public static float quality() {
        return active() ? instance.qualityLevel : 1.0F;
    }

    // ───────────────────────────── жизненный цикл ─────────────────────────────

    @Override
    protected void onEnable() {
        lastTier = -1;
        lastLimit = false;
        lastSeenDist = -1;
        enforceRenderDistance();
    }

    @Override
    protected void onDisable() {
        restoreRenderDistance();
        lastTier = -1;
        lastLimit = false;
        lastSeenDist = -1;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        restoreRenderDistance();
        lastTier = -1;
        lastLimit = false;
        lastSeenDist = -1;
    }

    @EventHandler
    public void onTick(EventTick e) {
        enforceRenderDistance();
    }

    @EventHandler
    public void onRender3D(EventRender3D e) {
        sampleFrame();
    }

    /** Сэмплирование кадров: из среднего FPS выводится уровень качества. */
    public static void sampleFrame() {
        Optimizer self = instance;
        if (self == null || !self.isEnabled() || !self.option("Синхронизация кадров")) return;

        long now = System.nanoTime();
        self.frameTimes[self.frameIndex] = now;
        self.frameIndex = (self.frameIndex + 1) % SAMPLES;
        if (self.frameCount < SAMPLES) {
            self.frameCount++;
        }

        if (self.frameCount < 2) {
            self.currentFps = 60;
            self.qualityLevel = 1.0F;
            return;
        }

        int newest = (self.frameIndex - 1 + SAMPLES) % SAMPLES;
        int oldest = self.frameCount < SAMPLES ? 0 : self.frameIndex;
        long delta = self.frameTimes[newest] - self.frameTimes[oldest];
        if (delta > 0L) {
            self.currentFps = (int) Math.round((self.frameCount - 1) * 1.0E9 / delta);
        }

        self.qualityLevel = self.currentFps >= 55 ? 1.0F
                : (self.currentFps >= 35 ? 0.7F
                : (self.currentFps >= 20 ? 0.45F : 0.25F));
    }

    // ───────────────────────────── дальность прорисовки ─────────────────────────────

    private void enforceRenderDistance() {
        GameOptions options = net.minecraft.client.MinecraftClient.getInstance().options;
        if (options == null) return;

        int current = options.getViewDistance().getValue();
        int currentTier = tierIndex();
        boolean limit = option("Ограничение дальности");

        if (currentTier == lastTier && limit == lastLimit && current == lastSeenDist) return;

        lastTier = currentTier;
        lastLimit = limit;

        int cap = RENDER_DISTANCE_CAP[currentTier];
        if (!limit || cap <= 0) {
            restoreRenderDistance();
            lastSeenDist = options.getViewDistance().getValue();
        } else if (current > cap) {
            if (savedViewDistance < 0) {
                savedViewDistance = current;
            }
            appliedCap = cap;
            options.getViewDistance().setValue(cap);
            lastSeenDist = cap;
        } else {
            lastSeenDist = current;
        }
    }

    private void restoreRenderDistance() {
        if (savedViewDistance < 0) return;

        try {
            GameOptions options = net.minecraft.client.MinecraftClient.getInstance().options;
            if (options != null) {
                int current = options.getViewDistance().getValue();
                // восстанавливаем только если значение всё ещё наше (пользователь не менял вручную)
                if (appliedCap < 0 || current == appliedCap) {
                    options.getViewDistance().setValue(savedViewDistance);
                }
            }
        } catch (Exception ignored) {
        }

        savedViewDistance = -1;
        appliedCap = -1;
    }
}
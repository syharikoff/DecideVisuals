package ru.decide.module.impl.render;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import ru.decide.Client;
import ru.decide.manager.event_impl.AttackEvent;
import ru.decide.manager.event_impl.EventRender3D;
import ru.decide.manager.event_impl.EventTick;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.module.api.settings.impl.DelimiterSetting;
import ru.decide.module.api.settings.impl.ModeSetting;
import ru.decide.module.api.settings.impl.MultiBooleanSetting;
import ru.decide.module.api.settings.impl.SliderSetting;
import ru.decide.utils.other.Instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Портированный Kimiko ModelCollapse: модель умершей сущности разваливается
 * на текстурированные кубики с физикой и коллизией.
 *
 * Смерть ловится двумя путями:
 *  - напрямую из миксина (onDeath / setHealth) — подтверждённая смерть;
 *  - по исчезновению сущности в опасности — для серверов без анимации смерти.
 */
@ModuleInfo(
        name = "Model Collapse",
        desc = "Разрывает модель убитой сущности на кубики с физикой и коллизией",
        category = Category.VISUALS
)
public final class ModelCollapse extends Module {

    public static ModelCollapse getInstance() {
        return Instance.get(ModelCollapse.class);
    }

    private static final String MODE_BOTTOM_UP = "Снизу вверх";
    private static final String MODE_TOP_DOWN = "Сверху вниз";
    private static final String TARGET_PLAYERS = "Игроки";
    private static final String TARGET_FRIENDS = "Друзья";
    private static final String TARGET_MOBS = "Мобы";
    private static final String TARGET_ANIMALS = "Животные";

    private static final int MAX_PENDING = 8;
    private static final long HIDE_MS = 5000L;
    private static final long COLLAPSE_MEMORY_MS = 5000L;
    private static final long PROVISIONAL_MS = 700L;
    private static final long VANISH_SNAPSHOT_MS = 700L;
    private static final double UNHIDE_MOVE_SQ = 6.25;

    private final ModelCollapseSimulation simulation = new ModelCollapseSimulation();
    private final List<LivingEntity> pending = new ArrayList<>();
    private final Map<Integer, HiddenEntry> hidden = new HashMap<>();
    private final Map<Integer, Long> recentlyCollapsed = new HashMap<>();
    private final Map<Integer, Long> provisional = new HashMap<>();

    public final DelimiterSetting mainSeparator = new DelimiterSetting(this, "Основное");
    public final ModeSetting breakMode = new ModeSetting(this, "Раскол", MODE_BOTTOM_UP, MODE_TOP_DOWN);
    public final SliderSetting waveTime = new SliderSetting(this, "Волна", 350F, 100F, 1000F, 25F);
    public final SliderSetting cubeSize = new SliderSetting(this, "Размер кубиков", 0.055F, 0.03F, 0.12F, 0.005F);
    public final SliderSetting impulse = new SliderSetting(this, "Разлёт", 1.0F, 0.0F, 3.0F, 0.05F);
    public final SliderSetting bounce = new SliderSetting(this, "Отскок", 35F, 0.0F, 80F, 5F);
    public final SliderSetting lifeTime = new SliderSetting(this, "Время жизни", 6.0F, 1.0F, 15.0F, 0.5F);
    public final SliderSetting maxDistance = new SliderSetting(this, "Дальность", 64F, 16F, 128F, 8F);
    public final BooleanSetting hideModel = new BooleanSetting(this, "Скрывать модель", true);
    public final BooleanSetting aggressiveDetect = new BooleanSetting(this, "Агрессивный детект", true);

    public final DelimiterSetting targetsSeparator = new DelimiterSetting(this, "Цели");
    private final BooleanSetting targetPlayers = new BooleanSetting(TARGET_PLAYERS, true);
    private final BooleanSetting targetFriends = new BooleanSetting(TARGET_FRIENDS, true);
    private final BooleanSetting targetMobs = new BooleanSetting(TARGET_MOBS, true);
    private final BooleanSetting targetAnimals = new BooleanSetting(TARGET_ANIMALS, true);
    public final MultiBooleanSetting targets = new MultiBooleanSetting(this, "Цели",
            targetPlayers, targetFriends, targetMobs, targetAnimals);

    // ================= Публичные хуки для миксинов =================

    /** Вызывается из LivingEntityMixin при подтверждённой смерти. */
    public static void notifyEntityDied(LivingEntity victim) {
        ModelCollapse module = getInstanceIfReady();
        if (module == null || !module.isEnabled() || victim == null) return;
        module.handleDeath(victim);
    }

    /** Вызывается из EntityRendererMixin: спрятать оригинальную модель, пока идут осколки. */
    public static boolean shouldHideEntity(LivingEntity entity) {
        ModelCollapse module = getInstanceIfReady();
        if (module == null || !module.isEnabled()) return false;

        HiddenEntry entry = module.hidden.get(entity.getId());
        if (entry == null) return false;
        if (System.currentTimeMillis() >= entry.until) return false;

        // сущность успела уйти со своей death-позиции — значит это не наш килл
        if (!entity.isRemoved() && entity.squaredDistanceTo(entry.x, entry.y, entry.z) > UNHIDE_MOVE_SQ) {
            module.hidden.remove(entity.getId());
            return false;
        }
        return true;
    }

    private static ModelCollapse getInstanceIfReady() {
        try {
            return getInstance();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // ================= Логика =================

    @EventHandler
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) resetState();
    }

    @EventHandler
    public void onRender(EventRender3D event) {
        if (mc.player == null || mc.world == null) {
            resetState();
            return;
        }

        long now = System.currentTimeMillis();
        float partialTick = MathHelper.clamp(event.getTickDelta(), 0.0f, 1.0f);

        // подтверждённые смерти: снимаем заморозку, если сущность ещё жива, иначе строим кучу
        if (!pending.isEmpty()) {
            Iterator<LivingEntity> it = pending.iterator();
            while (it.hasNext()) {
                LivingEntity entity = it.next();
                provisional.remove(entity.getId());

                if (simulation.release(entity.getId())) continue;
                if (simulation.spawn(entity, partialTick, settings())) continue;

                hidden.remove(entity.getId());
            }
            pending.clear();
        }

        // замороженные снапшоты: сущность не появилась — отпускаем, появилась — отменяем
        if (!provisional.isEmpty()) {
            List<Integer> stale = new ArrayList<>();
            for (Map.Entry<Integer, Long> entry : provisional.entrySet()) {
                if (now - entry.getValue() > PROVISIONAL_MS) stale.add(entry.getKey());
            }
            for (int id : stale) {
                provisional.remove(id);
                ClientWorld world = mc.world;
                if (world.getEntityById(id) == null) {
                    simulation.release(id);
                    recentlyCollapsed.put(id, now);
                    continue;
                }
                simulation.dropFrozen(id);
                hidden.remove(id);
            }
        }

        double distance = maxDistance.getValue();

        if (aggressiveDetect.getValue()) {
            ModelCollapseSnapshots.tick(partialTick, distance, this::matchesTarget);
            ModelCollapseSnapshots.collectVanished(VANISH_SNAPSHOT_MS, this::spawnFromVanished);
        }

        hidden.values().removeIf(entry -> entry.until < now);
        recentlyCollapsed.values().removeIf(at -> now - at > COLLAPSE_MEMORY_MS);

        if (!simulation.isIdle()) simulation.renderAndStep(event, settings());
    }

    private boolean spawnFromVanished(int id, ModelCollapseSnapshots.Snapshot snapshot) {
        if (recentlyCollapsed.containsKey(id) || provisional.containsKey(id)) return false;

        ClientPlayerEntity player = mc.player;
        if (player == null) return false;

        double distance = maxDistance.getValue();
        if (player.squaredDistanceTo(snapshot.x(), snapshot.y(), snapshot.z()) > distance * distance) return false;
        if (!simulation.spawnFrozen(id, snapshot, settings())) return false;

        provisional.put(id, System.currentTimeMillis());
        if (hideModel.getValue()) {
            hidden.put(id, new HiddenEntry(System.currentTimeMillis() + HIDE_MS,
                    snapshot.x(), snapshot.y(), snapshot.z()));
        }
        return true;
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (!isEnabled()) return;

        Entity target = event.getTarget();
        if (target instanceof LivingEntity living && target != mc.player) {
            ModelCollapseSnapshots.noteAttack(living);
        }
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    private void resetState() {
        pending.clear();
        hidden.clear();
        recentlyCollapsed.clear();
        provisional.clear();
        ModelCollapseSnapshots.clear();
        simulation.clear();
    }

    private ModelCollapseSimulation.Settings settings() {
        return new ModelCollapseSimulation.Settings(
                cubeSize.getValue(),
                impulse.getValue(),
                Math.round(lifeTime.getValue() * 1000.0f),
                bounce.getValue() / 100.0f,
                Math.round(waveTime.getValue()),
                breakMode.is(MODE_TOP_DOWN));
    }

    private void handleDeath(LivingEntity victim) {
        ClientPlayerEntity player = mc.player;
        ClientWorld level = mc.world;
        if (player == null || level == null) return;
        if (Objects.equals(victim, player)) return;
        if (!Objects.equals(victim.getEntityWorld(), level)) return;

        if (recentlyCollapsed.containsKey(victim.getId())) return;

        double distance = maxDistance.getValue();
        if (victim.squaredDistanceTo(player) > distance * distance) return;
        if (!matchesTarget(victim)) return;
        if (pending.size() >= MAX_PENDING) return;

        recentlyCollapsed.put(victim.getId(), System.currentTimeMillis());
        pending.add(victim);

        if (hideModel.getValue()) {
            hidden.put(victim.getId(), new HiddenEntry(System.currentTimeMillis() + HIDE_MS,
                    victim.getX(), victim.getY(), victim.getZ()));
        }
    }

    private boolean matchesTarget(LivingEntity entity) {
        if (entity instanceof PlayerEntity) {
            var friends = Client.get().friendManager();
            if (friends != null && friends.isFriend(entity.getName().getString())) {
                return targets.getValue(TARGET_FRIENDS);
            }
            return targets.getValue(TARGET_PLAYERS);
        }
        if (entity instanceof AnimalEntity) return targets.getValue(TARGET_ANIMALS);
        if (entity instanceof MobEntity) return targets.getValue(TARGET_MOBS);
        return false;
    }

    private static final class HiddenEntry {
        final long until;
        final double x, y, z;

        HiddenEntry(long until, double x, double y, double z) {
            this.until = until;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}

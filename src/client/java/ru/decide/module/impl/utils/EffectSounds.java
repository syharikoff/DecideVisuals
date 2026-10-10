package ru.decide.module.impl.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.decide.config.ConfigManager;
import ru.decide.manager.event_impl.AttackEvent;
import ru.decide.manager.event_impl.EventPacket;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.module.api.settings.impl.ModeSetting;
import ru.decide.module.api.settings.impl.SliderSetting;

/**
 * Звуковые эффекты клиента: приветствие на старте, звук за убийство
 * (игрока или моба) и звук за снос тотема.
 * <p>
 * Все звуки лежат как обычные звуковые события ассета {@code decide}
 * (см. {@code assets/decide/sounds.json}), поэтому проигрываются через
 * {@link MinecraftClient#getSoundManager()} и не блокируют рендер.
 * <p>
 * <b>Важно про файлы:</b> Minecraft 1.21 декодирует только Ogg Vorbis.
 * Если положить в ogg-контейнер Opus (так часто делают конвертеры),
 * файл попадёт в jar, реестр его найдёт, но звука не будет - поэтому все
 * треки перекодированы в Vorbis.
 * <p>
 * При смене значения звука проигрывается тестовый трек. Но
 * {@link Setting#set} выполняет действие лишь в игре, а меню настроек
 * доступно и в главном меню, поэтому проигрывание дополнительно
* проверяется по тику.
 */
@ModuleInfo(
        name = "Effect Sounds",
        desc = "Создаёт звук при убийстве игрока либо моба",
        category = Category.UTILITIES
)
public final class EffectSounds extends Module {

    private static final Logger LOGGER = LoggerFactory.getLogger("DecideVisuals:EffectSounds");

    /** EntityStatusS2CPacket: 3 = смерть, 35 = срабатывание тотема. */
    private static final byte STATUS_DEATH = 3;
    private static final byte STATUS_TOTEM = 35;

    /** Сколько живёт цель атаки, после чего её убийство уже не считается нашим. */
    private static final long KILL_WINDOW_MS = 10_000L;

    /**
     * Сколько миллисекунд после нашего звука глушим звуки игры.
     * За короткое окно успевает прийти ванильный дубль (тотем / смерть моба).
     */
    private static final long SUPPRESS_MS = 120L;

    /**
     * Сколько миллисекунд эффект считается «занятым».
     * Гонять второй звук поверх первого нельзя - получится каша.
     * Окно защищает и от дублей: один и тот же пакет смерти иногда приходит дважды.
     */
    private static final long BUSY_MS = 400L;

    private static final String OFF = "Выключен";

    // Названия в настройках - ровно как файлы в папке Soundsfre.
    // Сами файлы переименованы в effect_*.ogg: путь ресурса с кириллицей не работает.
    private static final String KILL_1 = "В колени пидорас ебучий";
    private static final String KILL_2 = "Опа проебал";
    private static final String TOTEM_1 = "Бля минус тал";
    private static final String TOTEM_2 = "Я тебя монгнууу";

    private static final Identifier SOUND_KILL_1 = Identifier.of("decide", "effect.kill_1");
    private static final Identifier SOUND_KILL_2 = Identifier.of("decide", "effect.kill_2");
    private static final Identifier SOUND_TOTEM_1 = Identifier.of("decide", "effect.totem_1");
    private static final Identifier SOUND_TOTEM_2 = Identifier.of("decide", "effect.totem_2");
    private static final Identifier SOUND_GREETING = Identifier.of("decide", "effect.greeting");

    // ── Настройки ────────────────────────────────────────────────────────────

    public final BooleanSetting greeting = new BooleanSetting(this, "Приветствие при запуске", true)
            .onAction(() -> requestTest(SOUND_GREETING, 1.0F));
    public final SliderSetting greetingVolume = new SliderSetting(this, "Громкость приветствия", 100.0F, 0.0F, 100.0F, 5.0F)
            .setVisible(() -> greeting.getValue());

    public final ModeSetting killSound = new ModeSetting(this, "Звук за убийство", KILL_1, KILL_2, OFF)
            .onAction(this::playSelectedKillSound);
    public final ModeSetting killTarget = new ModeSetting(this, "Звук после кого", "Игрока", "Моба", "Любого");
    public final SliderSetting killVolume = new SliderSetting(this, "Громкость убийства", 100.0F, 0.0F, 100.0F, 5.0F);

    public final ModeSetting totemSound = new ModeSetting(this, "Звук после тотема", TOTEM_1, TOTEM_2, OFF)
            .onAction(this::playSelectedTotemSound);
    public final SliderSetting totemVolume = new SliderSetting(this, "Громкость тотема", 100.0F, 0.0F, 100.0F, 5.0F);

    // ── Состояние ────────────────────────────────────────────────────────────

    /** Последняя атакованная цель - по ней определяем, наше ли это убийство. */
    private Entity lastTarget;
    private long lastAttackTime;

    /** Звук, который не удалось проиграть сразу (менеджер/реестр ещё не готов). */
    private Identifier pendingTestSound;
    private float pendingTestVolume = 1.0F;

    /** Приветствие проигрывается один раз, когда звуки доступны. */
    private boolean greetingPending;

    /** Предупреждение о ненайденном звуке пишем один раз, а не каждый тик. */
    private boolean warnedMissing;

    /** До какого момента (мс) глушим звуки игры, чтобы не было дубля. */
    private static long suppressUntilMs;

    /** До какого момента (мс) наш эффект ещё «занят» - новые звуки не стартуют. */
    private static long busyUntilMs;

    /** Сейчас проигрывается наш звук - сами себя глушить нельзя. */
    private static boolean PLAYING_OWN_SOUND;

    /** Наш эффект ещё играет: второй звук поверх первого запускать нельзя. */
    private static boolean isBusy() {
        return System.currentTimeMillis() < busyUntilMs;
    }

    public EffectSounds() {
        greetingPending = greeting.getValue();
    }

    @Override
    protected void onEnable() {
        pendingTestSound = null;
        lastTarget = null;
        lastAttackTime = 0L;
    }

    // ── Приветствие и тестовые звуки ─────────────────────────────────────────

    /**
     * Приветствие после запуска клиента. Ждём, пока звуки ассета загрузятся,
     * иначе звук не воспроизведётся.
     */
    public void playGreetingOnStart() {
        greetingPending = greeting.getValue();
    }

    private void playSelectedKillSound() {
        if (skipTest()) return;
        String selected = killSound.getValue();
        Identifier sound = KILL_1.equals(selected) ? SOUND_KILL_1
                : KILL_2.equals(selected) ? SOUND_KILL_2
                : null;
        requestTest(sound, killVolume.getValue() / 100.0F);
    }

    private void playSelectedTotemSound() {
        if (skipTest()) return;
        String selected = totemSound.getValue();
        Identifier sound = TOTEM_1.equals(selected) ? SOUND_TOTEM_1
                : TOTEM_2.equals(selected) ? SOUND_TOTEM_2
                : null;
        requestTest(sound, totemVolume.getValue() / 100.0F);
    }

    /**
     * Тест не проигрываем, если значение сменил не игрок:
     * при загрузке/сохранении конфига настройки восстанавливаются через
     * {@code Setting.set()}, и без проверки клиент сам себе играл звуки.
     */
    private static boolean skipTest() {
        return ConfigManager.isLoadingConfig();
    }

    private void requestTest(Identifier sound, float volume) {
        if (sound == null) {
            pendingTestSound = null;
            return;
        }
        // Звук уже не должен звучать - новый не запускаем
        if (isBusy()) {
            return;
        }
        if (tryPlay(sound, volume)) {
            pendingTestSound = null;
        } else {
            // звуки ещё не загружены (например, настройка менялась в главном меню)
            pendingTestSound = sound;
            pendingTestVolume = volume;
        }
    }

    /**
     * Тик для приветствия и отложенных тестовых звуков.
     * <p>
     * Вызывается из {@code Client} через Fabric {@code ClientTickEvents}, а не
     * через наш {@code EventTick}: тот публикуется только когда есть world и
     * player, а приветствие нужно в главном меню, и настройки звука там тоже
     * меняются. Модуль при этом может быть выключен.
     */
    public void tickUiSounds() {
        if (greetingPending) {
            float volume = greetingVolume.getValue() / 100.0F;
            if (tryPlay(SOUND_GREETING, volume)) {
                greetingPending = false;
                LOGGER.info("[effect-sounds] приветствие сыграно (громкость {})", fmtVolume(volume));
            }
        }
        if (pendingTestSound != null && tryPlay(pendingTestSound, pendingTestVolume)) {
            pendingTestSound = null;
        }
    }

    /**
     * Проигрывает звук из нашего ассета.
     *
     * @return true, если звук отдан менеджеру; false, если звуки ещё не загружены
     */
    private boolean tryPlay(Identifier sound, float volume) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSoundManager() == null) {
            return false;
        }
        // get() отдаёт null, пока sounds.json ассета не обработан.
        // Ждём следующего тика вместо того, чтобы сыпать ошибками в лог.
        if (client.getSoundManager().get(sound) == null) {
            if (!warnedMissing) {
                warnedMissing = true;
                LOGGER.warn("[effect-sounds] звук {} не найден в реестре - проверь sounds.json", sound);
            }
            return false;
        }
        playCustom(sound, volume);
        return true;
    }

    private static void playCustom(Identifier sound, float volume) {
        play(SoundEvent.of(sound), volume);
    }

    private static SoundEvent identifier(Identifier sound) {
        return SoundEvent.of(sound);
    }

    private static void play(SoundEvent sound, float volume) {
        if (sound == null || volume <= 0.0F) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSoundManager() == null) return;
        // Гасим дубликат: игра сама проиграет item.totem.use / звук смерти моба.
        suppressUntilMs = System.currentTimeMillis() + SUPPRESS_MS;
        // Держим эффект «занятым», чтобы два звука не легли друг на друга
        busyUntilMs = System.currentTimeMillis() + BUSY_MS;
        try {
            // Помечаем, что сейчас играем именно мы: иначе SoundManagerMixin
            // (гасящий дубликаты) проглотил бы и наш собственный звук.
            PLAYING_OWN_SOUND = true;
            // ui(event, pitch, volume) - звук без привязки к позиции
            client.getSoundManager().play(PositionedSoundInstance.ui(sound, 1.0F, volume));
        } catch (Throwable t) {
            LOGGER.warn("[effect-sounds] не удалось проиграть {}", sound, t);
        } finally {
            PLAYING_OWN_SOUND = false;
        }
    }

    /**
     * Сейчас проигрывается наш звук эффекта, а значит звуки игры надо заглушить.
     * Вызывается из {@code SoundManagerMixin}.
     * <p>
     * Окно короткое: ванильный тотем/смерть приходят в том же тике, что и наш
     * пакет. Слишком длинное окно съело бы обычные звуки мира.
     */
    public static boolean isSuppressingGameSounds() {
        return !PLAYING_OWN_SOUND && System.currentTimeMillis() < suppressUntilMs;
    }

    // ── Убийства ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onAttack(AttackEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Entity target = event.getTarget();
        if (target == null || target == client.player) return;

        lastTarget = target;
        lastAttackTime = System.currentTimeMillis();
    }

    @EventHandler
    public void onPacket(EventPacket event) {
        if (event.isSend() || !(event.getPacket() instanceof EntityStatusS2CPacket packet)) {
            return;
        }

        byte status = packet.getStatus();
        if (status == STATUS_TOTEM) {
            onTotem(packet);
        } else if (status == STATUS_DEATH) {
            onDeath(packet);
        }
    }

    private void onTotem(EntityStatusS2CPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Entity entity = packet.getEntity(client.world);
        // Реагируем только на свой тотем: чужой тотем - не наше событие
        if (entity != client.player) return;
        if (isBusy()) return;

        float volume = totemVolume.getValue() / 100.0F;
        String selected = totemSound.getValue();
        Identifier sound = TOTEM_1.equals(selected) ? SOUND_TOTEM_1
                : TOTEM_2.equals(selected) ? SOUND_TOTEM_2
                : null;
        if (sound == null) return;
        playCustom(sound, volume);
        LOGGER.info("[effect-sounds] тотем: {} (громкость {})", sound, fmtVolume(volume));
    }

    private void onDeath(EntityStatusS2CPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        String selected = killSound.getValue();
        if (OFF.equals(selected)) return;

        Entity entity = packet.getEntity(client.world);
        if (entity == null || entity == client.player) return;
        if (!matchesKillTarget(entity)) return;
        if (isBusy()) return;
        // Убили мы цель недавно? Иначе это чужое убийство в поле зрения
        if (lastTarget == null
                || !lastTarget.getUuid().equals(entity.getUuid())
                || System.currentTimeMillis() - lastAttackTime > KILL_WINDOW_MS) {
            return;
        }

        lastTarget = null;
        Identifier sound = KILL_1.equals(selected) ? SOUND_KILL_1 : SOUND_KILL_2;
        float volume = killVolume.getValue() / 100.0F;
        playCustom(sound, volume);
        LOGGER.info("[effect-sounds] убийство: {} (громкость {})", sound, fmtVolume(volume));
    }

    private boolean matchesKillTarget(Entity entity) {
        String target = killTarget.getValue();
        if ("Любого".equals(target)) return true;
        // "Игрока" / "Моба": игроки - PlayerEntity, всё остальное - мобы
        boolean isPlayer = entity instanceof PlayerEntity;
        return "Игрока".equals(target) == isPlayer;
    }

    private static String fmtVolume(float volume) {
        return String.format(java.util.Locale.ROOT, "%.2f", volume);
    }
}

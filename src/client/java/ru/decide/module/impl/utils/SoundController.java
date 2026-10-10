package ru.decide.module.impl.utils;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.module.api.settings.impl.SliderSetting;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Управление громкостью отдельных звуков игры: у каждой группы свой
 * выключатель и ползунок 0–100%. Влияет на звук в момент его воспроизведения,
 * поэтому работает и для звуков мира, и для звуков с задержкой.
 */
@ModuleInfo(
        name = "Sound Controller",
        desc = "Громкость отдельных звуков",
        category = Category.UTILITIES
)
public class SoundController extends Module {

    private static final Logger LOGGER = LoggerFactory.getLogger("DecideVisuals:SoundController");
    private static SoundController instance;

    /**
     * Ключи — полные {@link Identifier}, а не строки из sounds.json: у звука
     * {@code Identifier.toString()} всегда с неймспейсом
     * ({@code minecraft:weather.rain}), и со строкой без него группа
     * никогда не совпадала.
     */
    private final Map<Identifier, Group> bindings = new LinkedHashMap<>();
    private final Set<String> seen = new HashSet<>();

    public final BooleanSetting firework = toggle("Фейерверк");
    public final SliderSetting fireworkVolume = volume("Громкость фейерверка", firework);

    public final BooleanSetting expBottleThrow = toggle("Бросок пузырька опыта");
    public final SliderSetting expBottleThrowVolume = volume("Громкость броска", expBottleThrow);

    public final BooleanSetting expBottleBreak = toggle("Разбивание пузырька");
    public final SliderSetting expBottleBreakVolume = volume("Громкость разбивания", expBottleBreak);

    public final BooleanSetting expPickup = toggle("Подбор опыта");
    public final SliderSetting expPickupVolume = volume("Громкость подбора", expPickup);

    public final BooleanSetting trident = toggle("Трезубец");
    public final SliderSetting tridentVolume = volume("Громкость трезубца", trident);

    public final BooleanSetting fishingRod = toggle("Удочка");
    public final SliderSetting fishingRodVolume = volume("Громкость удочки", fishingRod);

    public final BooleanSetting hit = toggle("Звук удара");
    public final SliderSetting hitVolume = volume("Громкость удара", hit);

    public final BooleanSetting crit = toggle("Звук крита");
    public final SliderSetting critVolume = volume("Громкость крита", crit);

    public final BooleanSetting splash = toggle("Сплеш");
    public final SliderSetting splashVolume = volume("Громкость сплеша", splash);

    public final BooleanSetting funtimeCrit = toggle("Крит FunTime");
    public final SliderSetting funtimeCritVolume = volume("Громкость FunTime", funtimeCrit);

    // ── добавлено сверх оригинала ──

    public final BooleanSetting totem = toggle("Тотем");
    public final SliderSetting totemVolume = volume("Громкость тотема", totem);

    public final BooleanSetting explosion = toggle("Взрыв");
    public final SliderSetting explosionVolume = volume("Громкость взрыва", explosion);

    public final BooleanSetting enderPearl = toggle("Эндерперл");
    public final SliderSetting enderPearlVolume = volume("Громкость эндерперла", enderPearl);

    public final BooleanSetting snowball = toggle("Снежок");
    public final SliderSetting snowballVolume = volume("Громкость снежка", snowball);

    public final BooleanSetting crossbow = toggle("Арбалет");
    public final SliderSetting crossbowVolume = volume("Громкость арбалета", crossbow);

    public final BooleanSetting armor = toggle("Броня");
    public final SliderSetting armorVolume = volume("Громкость брони", armor);

    public final BooleanSetting fireCharge = toggle("Огненный заряд");
    public final SliderSetting fireChargeVolume = volume("Громкость огненного заряда", fireCharge);

    public final BooleanSetting thunder = toggle("Гром");
    public final SliderSetting thunderVolume = volume("Громкость грома", thunder);

    // ── добавлено по запросу ──

    public final BooleanSetting fireworkBlast = toggle("Взрыв фейерверка");
    public final SliderSetting fireworkBlastVolume = volume("Громкость взрыва фейерверка", fireworkBlast);

    public final BooleanSetting tnt = toggle("ТНТ");
    public final SliderSetting tntVolume = volume("Громкость ТНТ", tnt);

    public final BooleanSetting anvil = toggle("Наковальня");
    public final SliderSetting anvilVolume = volume("Громкость наковальни", anvil);

    public final BooleanSetting potions = toggle("Зелья");
    public final SliderSetting potionsVolume = volume("Громкость зелий", potions);

    public final BooleanSetting beacon = toggle("Маяк");
    public final SliderSetting beaconVolume = volume("Громкость маяка", beacon);

    public final BooleanSetting mobsAmbient = toggle("Фон мобов");
    public final SliderSetting mobsAmbientVolume = volume("Громкость фона мобов", mobsAmbient);

    public final BooleanSetting boss = toggle("Боссы");
    public final SliderSetting bossVolume = volume("Громкость боссов", boss);

    public final BooleanSetting rain = toggle("Дождь");
    public final SliderSetting rainVolume = volume("Громкость дождя", rain);

    public SoundController() {
        instance = this;

        bind("entity.firework_rocket.launch", firework, fireworkVolume);
        bind("entity.experience_bottle.throw", expBottleThrow, expBottleThrowVolume);
        bind("entity.splash_potion.break", expBottleBreak, expBottleBreakVolume);
        bind("entity.experience_orb.pickup", expPickup, expPickupVolume);
        bind("item.trident.return", trident, tridentVolume);
        bind("item.trident.hit_ground", trident, tridentVolume);
        bind("block.beacon.deactivate", trident, tridentVolume);
        bind("entity.fishing_bobber.retrieve", fishingRod, fishingRodVolume);
        bind("entity.player.attack.sweep", hit, hitVolume);
        bind("entity.player.attack.strong", hit, hitVolume);
        bind("entity.player.attack.weak", hit, hitVolume);
        bind("entity.generic.splash", splash, splashVolume);
        bind("entity.player.splash", splash, splashVolume);
        bind("entity.player.attack.crit", crit, critVolume);
        bind("entity.player.levelup", funtimeCrit, funtimeCritVolume);

        bind("item.totem.use", totem, totemVolume);
        bind("entity.generic.explode", explosion, explosionVolume);
        bind("block.end_portal.spawn", explosion, explosionVolume);
        bind("entity.ender_pearl.throw", enderPearl, enderPearlVolume);
        bind("entity.snowball.throw", snowball, snowballVolume);
        bind("item.crossbow.shoot", crossbow, crossbowVolume);
        bind("entity.arrow.shoot", crossbow, crossbowVolume);
        bind("item.armor.equip_generic", armor, armorVolume);
        bind("item.armor.equip_chain", armor, armorVolume);
        bind("item.armor.equip_iron", armor, armorVolume);
        bind("item.armor.equip_leather", armor, armorVolume);
        bind("item.armor.equip_gold", armor, armorVolume);
        bind("item.armor.equip_diamond", armor, armorVolume);
        bind("item.armor.equip_netherite", armor, armorVolume);
        bind("item.armor.equip_turtle", armor, armorVolume);
        bind("item.armor.equip_elytra", armor, armorVolume);
        bind("item.firecharge.use", fireCharge, fireChargeVolume);
        bind("entity.lightning_bolt.thunder", thunder, thunderVolume);

        // ── добавлено по запросу ──
        bind("entity.firework_rocket.blast", fireworkBlast, fireworkBlastVolume);
        bind("entity.firework_rocket.blast_far", fireworkBlast, fireworkBlastVolume);
        bind("entity.firework_rocket.large_blast", fireworkBlast, fireworkBlastVolume);
        bind("entity.firework_rocket.large_blast_far", fireworkBlast, fireworkBlastVolume);
        bind("entity.firework_rocket.twinkle", fireworkBlast, fireworkBlastVolume);

        bind("entity.tnt.primed", tnt, tntVolume);

        bind("block.anvil.land", anvil, anvilVolume);
        bind("block.anvil.use", anvil, anvilVolume);
        bind("block.anvil.hit", anvil, anvilVolume);

        bind("entity.splash_potion.throw", potions, potionsVolume);
        bind("block.brewing_stand.brew", potions, potionsVolume);

        bind("block.beacon.activate", beacon, beaconVolume);
        bind("block.beacon.ambient", beacon, beaconVolume);
        bind("block.beacon.power_select", beacon, beaconVolume);

        bind("entity.zombie.ambient", mobsAmbient, mobsAmbientVolume);
        bind("entity.skeleton.ambient", mobsAmbient, mobsAmbientVolume);
        bind("entity.spider.ambient", mobsAmbient, mobsAmbientVolume);
        bind("entity.cow.ambient", mobsAmbient, mobsAmbientVolume);
        bind("entity.chicken.ambient", mobsAmbient, mobsAmbientVolume);
        bind("entity.pig.ambient", mobsAmbient, mobsAmbientVolume);
        bind("entity.slime.squish", mobsAmbient, mobsAmbientVolume);
        bind("entity.blaze.ambient", mobsAmbient, mobsAmbientVolume);

        bind("entity.ender_dragon.growl", boss, bossVolume);
        bind("entity.ender_dragon.flap", boss, bossVolume);
        bind("entity.ender_dragon.shoot", boss, bossVolume);
        bind("entity.ender_dragon.hurt", boss, bossVolume);
        bind("entity.wither.shoot", boss, bossVolume);
        bind("entity.wither.break_block", boss, bossVolume);
        bind("entity.wither.hurt", boss, bossVolume);

        bind("weather.rain", rain, rainVolume);
        bind("weather.rain.above", rain, rainVolume);
    }

    public static SoundController getInstance() {
        return instance;
    }

    /** Коэффициент громкости для звукового события; 1.0 — без изменений. */
    public static float scaleFor(SoundInstance sound) {
        if (sound == null) return 1.0F;

        var id = sound.getId();
        if (id == null) return 1.0F;

        SoundController self = instance;
        if (self == null || !self.isEnabled()) return 1.0F;

        Group group = self.bindings.get(id);
        if (group == null || !group.enabled().getValue()) return 1.0F;

        float value = group.volume().getValue();
        float scale = Math.max(0.0F, Math.min(100.0F, value)) / 100.0F;

        if (self.seen.add(id.toString())) {
            LOGGER.info("[SoundController] {} -> x{}", id, scale);
        }
        return scale;
    }

    /**
     * Подменяет звук обёрткой с приглушённой громкостью (для SoundManagerMixin).
     * Уже обёрнутый звук возвращается как есть - иначе повторный вызов play
     * из миксина обернул бы его снова и ушёл в рекурсию.
     */
    public static SoundInstance scale(SoundInstance sound) {
        if (sound instanceof ScaledSoundInstance) return sound;
        return ScaledSoundInstance.of(sound, scaleFor(sound));
    }

    private BooleanSetting toggle(String name) {
        return new BooleanSetting(this, name, false);
    }

    private SliderSetting volume(String name, BooleanSetting owner) {
        return new SliderSetting(this, name, 50.0F, 0.0F, 100.0F, 1.0F)
                .setVisible(owner::getValue);
    }

    private void bind(String path, BooleanSetting enabled, SliderSetting volume) {
        this.bindings.put(Identifier.ofVanilla(path), new Group(enabled, volume));
    }

    private record Group(BooleanSetting enabled, SliderSetting volume) {
    }
}
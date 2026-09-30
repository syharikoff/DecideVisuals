package ru.white.module.impl.render.swordreplacer;

import java.util.ArrayList;
import java.util.List;

public final class WeaponCatalog {
    public static final String VANILLA_MODEL = "Vanilla";
    public static final String DEFAULT_MODEL = "Katana";

    private static final List<String> CUSTOM_MODELS = List.of(
        "Abominable Blade", "Abominable Great Saber", "Abominable Scythe",
        "Acidic Cleaver", "Amethyst Shuriken", "Ancient Royal Great Sword",
        "Aquatic Sacred Blade", "Arcanethyst", "Ashura's Blade",
        "Awakened Lichblade", "Blood Edge", "Bloody Death", "Bramblethorn",
        "Brimstone Claymore", "Carian Knight's Sword", "Chrono Blade",
        "Corrupted Mythic Blade", "Creation Splitter", "Crescent Rose",
        "Cyber Katana", "Cyber Mantis Blade", "Cyber Sword",
        "Cybernetic Chainsaw Blade", "Cybernetic Katana", "Cybernetic Knife",
        "Dainsleif", "Dark Blade", "Dark Cleaver",
        "Death Knight's Dagger", "Death Knight's Sword",
        "Demigod's Unholy Blade", "Demigod's Unholy Halberd",
        "Demon Lord's Great Axe", "Demon Lord's Sword",
        "Demonic Blade", "Demonic Cleaver",
        "Divine Axe Rhitta", "Divine Justice", "Divine Punisher", "Divine Reaper",
        "Dragon Slaying blade", "Edge Of The Astral Plane",
        "Emberblade", "Enigma", "Epic Sword", "Estoc",
        "Fallen God's Spear", "Fallen God's Sword",
        "Floral Longsword", "Floral Sabre", "Forest Guardian's Glaive",
        "Frost Axe", "Frost Blade", "Frost Scythe",
        "Hearthflame", "Hero Sword", "Holy Moonlight Sword",
        "Hornet's Needle", "Icewhisper", "Jade Halberd",
        "Katana", "Legendary Sword", "Longsword",
        "Magi Scythe", "Masamune", "Mjolnir",
        "Molten Blade", "Molten Sword", "Muramasa",
        "Mystical Spellblade", "Mythic Blade", "Ocean's Rage",
        "Partisan", "Pharaoh's Treasure", "Pheonix Grace",
        "Plague Longsword", "Power Fuse Hammer", "Power Fuse Sword",
        "Requiem of the Ninth Abyss", "Ribbon Cleaver", "Righteous Relic",
        "Rivers Of Blood", "Royal Chakram", "Royal Rapier",
        "Sabre", "Scissor Blade", "Sculk Cleaver",
        "Sculk Scythe", "Sculk Sword", "Sentinel's Will",
        "Silverine Blade", "Soul Claws", "Soul Collector",
        "Soul Devourer", "Soul Edge", "Soul Harvester",
        "Soul Stealer", "Soulrender", "Star's Edge",
        "Steel Sword", "Stop Sign", "Storm Bringer",
        "Storm's Edge", "Sunbreak", "Tengen's Blade",
        "Terra Blade", "Thousand Demon Daggers", "Thunder Bringer",
        "Thunderbrand", "True Excalibur", "Vampiric Needle",
        "Wakizashi", "Watcher Claymore", "Watching Warglaive",
        "Waxweaver", "Whisperwind", "Wickpiercer",
        "Wraith Scythe", "Yoru"
    );

    private static final List<String> ALL_MODELS = createAllModels();

    public static List<String> allModels() { return ALL_MODELS; }
    public static List<String> customModels() { return CUSTOM_MODELS; }
    public static boolean contains(String name) {
        return name != null && ALL_MODELS.contains(name);
    }

    private static List<String> createAllModels() {
        List<String> list = new ArrayList<>(CUSTOM_MODELS.size() + 1);
        list.add(VANILLA_MODEL);
        list.addAll(CUSTOM_MODELS);
        return List.copyOf(list);
    }
}

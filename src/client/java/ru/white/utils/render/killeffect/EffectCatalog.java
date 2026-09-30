package ru.white.utils.render.killeffect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EffectCatalog {
    private static final Map<String, Effect> EFFECTS = new LinkedHashMap<>();

    private EffectCatalog() {}

    private static int loaded = 0;
    private static int failed = 0;

    public static int loadedCount() { return loaded; }
    public static int failedCount() { return failed; }

    public static void register(String id, String modelId, String namespace, float scale, double duration) {
        try {
            BBModel model = BBModel.load(modelId, namespace, "models/effects");
            EFFECTS.put(id, new Effect(id, modelId, namespace, scale, duration, model));
            loaded++;
        } catch (Exception e) {
            failed++;
        }
    }

    public static Effect get(String id) {
        return EFFECTS.get(id);
    }

    public static List<Effect> all() {
        return List.copyOf(EFFECTS.values());
    }

    public static List<String> allIds() {
        return List.copyOf(EFFECTS.keySet());
    }

    public static void registerAll() {
        String ns = "wvisual";
        int loaded = 0;
        int failed = 0;
        register("shark_attack", "shark_attack", ns, 1.0f, 3.0);
        register("ice_shatter", "ice_shatter", ns, 1.0f, 3.0);
        register("glass_shatter", "glass_shatter", ns, 1.0f, 3.0);
        register("rust_decay", "rust_decay", ns, 1.0f, 3.0);
        register("quicksand", "quicksand", ns, 1.0f, 3.0);
        register("knockout_ko", "knockout_ko", ns, 1.0f, 3.0);
        register("bubble_burst", "bubble_burst", ns, 1.0f, 3.0);
        register("spectral_fade", "spectral_fade", ns, 1.0f, 3.0);
        register("energy_dissipation", "energy_dissipation", ns, 1.0f, 3.0);
        register("stone_crumble", "stone_crumble", ns, 1.0f, 3.0);
        register("sand_dissolve", "sand_dissolve", ns, 1.0f, 3.0);
        register("water_evaporation", "water_evaporation", ns, 1.0f, 3.0);
        register("sound_wave_disperse", "sound_wave_disperse", ns, 1.0f, 3.0);
        register("digital_disintegration", "digital_disintegration", ns, 1.0f, 3.0);
        register("tentacle_grasp", "tentacle_grasp", ns, 1.0f, 3.0);
        register("plantfood_feasting", "plantfood_feasting", ns, 1.0f, 3.0);
        register("tertis_smash", "tertis_smash", ns, 1.0f, 3.0);
        register("light_absorption", "light_absorption", ns, 1.0f, 3.0);
        register("astral_projection", "astral_projection", ns, 1.0f, 3.0);
        register("arcade_gameover", "arcade_gameover", ns, 1.0f, 3.0);
        register("angelic_bless", "angelic_bless", ns, 1.0f, 3.0);
        register("liquid_meltdown", "liquid_meltdown", ns, 1.0f, 3.0);
        register("hellfire_burn", "hellfire_burn", ns, 1.0f, 3.0);
        register("imposter_instinct", "imposter_instinct", ns, 1.0f, 3.0);
        register("colorful_explosion", "colorful_explosion", ns, 1.0f, 3.0);
        register("feather_scatter", "feather_scatter", ns, 1.0f, 3.0);
        register("nature_reclaim", "nature_reclaim", ns, 1.0f, 3.0);
        register("dissolve_into_ash", "dissolve_into_ash", ns, 1.0f, 3.0);
        register("hologram_flicker_out", "hologram_flicker_out", ns, 1.0f, 3.0);
        register("kfx_abstracted", "kfx_abstracted", ns, 1.0f, 3.0);
        register("kfx_acidic_corrosion", "kfx_acidic_corrosion", ns, 1.0f, 3.0);
        register("kfx_clockwork_disassembly", "kfx_clockwork_disassembly", ns, 1.0f, 3.0);
        register("kfx_frost_infection", "kfx_frost_infection", ns, 1.0f, 3.0);
        register("kfx_graffiti_spray", "kfx_graffiti_spray", ns, 1.0f, 3.0);
        register("kfx_ink_blots", "kfx_ink_blots", ns, 1.0f, 3.0);
        register("kfx_magnetic_resonance", "kfx_magnetic_resonance", ns, 1.0f, 3.0);
        register("kfx_origami_fold", "kfx_origami_fold", ns, 1.0f, 3.0);
        register("kfx_pure_form", "kfx_pure_form", ns, 1.0f, 3.0);
    }

    public record Effect(String id, String modelId, String namespace, float scale, double duration, BBModel model) {}
}

package ru.white.module.impl.display;

import net.minecraft.client.MinecraftClient;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.other.Instance;
import ru.white.utils.other.SoundUtil;

@ModuleInfo(
        name = "Sounds",
        desc = "Звуки включения/выключения модулей",
        category = Category.HUD
)
public final class Sounds extends Module {

    public static Sounds get() {
        return Instance.get(Sounds.class);
    }

    private static final String S1 = "Звук 1";
    private static final String S2 = "Звук 2";
    private static final String S3 = "Звук 3";
    private static final String S4 = "Звук 4";
    private static final String S5 = "Звук 5";
    private static final String S6 = "Звук 6";
    private static final String S7 = "Звук 7";
    private static final String S8 = "Звук 8";
    private static final String S9 = "Звук 9";
    private static final String S10 = "Звук 10";

    public final BooleanSetting toggleSounds = new BooleanSetting(this, "Звуки toggle", true);
    public final ModeSetting soundPack = new ModeSetting(this, "Пак звуков", S1,
            S1, S2, S3, S4, S5, S6, S7, S8, S9, S10);
    public final SliderSetting volume = new SliderSetting(this, "Громкость", 0.5F, 0.1F, 1.0F, 0.1F);

    @Override
    protected void onEnable() {
        playSelfSound(true);
    }

    @Override
    protected void onDisable() {
        playSelfSound(false);
    }

    private void playSelfSound(boolean enable) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        playSound(enable);
    }

    public boolean isToggleSoundsEnabled() {
        return isEnabled() && toggleSounds.getValue();
    }

    public void playToggleSound(boolean enable) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!isToggleSoundsEnabled()) return;

        playSound(enable);
    }

    private void playSound(boolean enable) {
        float vol = volume.getValue();
        String dir = "toggle/";
        String suffix = enable ? "_enable" : "_disable";

        if (soundPack.is(S1)) {
            SoundUtil.playSound_wav("tone" + suffix, vol);
        } else if (soundPack.is(S2)) {
            SoundUtil.playSound_wav("notify" + suffix, vol);
        } else if (soundPack.is(S3)) {
            SoundUtil.playSound_wav("akrien" + suffix, vol);
        } else if (soundPack.is(S4)) {
            SoundUtil.playSound_wav(dir + "smooth_" + (enable ? "on" : "off"), vol);
        } else if (soundPack.is(S5)) {
            SoundUtil.playSound_wav(dir + "celestial_" + (enable ? "on" : "off"), vol);
        } else if (soundPack.is(S6)) {
            SoundUtil.playSound_wav(dir + "blop_" + (enable ? "on" : "off"), vol);
        } else if (soundPack.is(S7)) {
            SoundUtil.playSound_wav(dir + "module_" + (enable ? "enable" : "disable") + "_5", vol);
        } else if (soundPack.is(S8)) {
            SoundUtil.playSound_wav(dir + "module_" + (enable ? "enable" : "disable") + "_6", vol);
        } else if (soundPack.is(S9)) {
            SoundUtil.playSound_wav(dir + "module_" + (enable ? "enable" : "disable") + "_7", vol);
        } else {
            SoundUtil.playSound_wav(dir + (enable ? "on" : "off"), vol);
        }
    }
}

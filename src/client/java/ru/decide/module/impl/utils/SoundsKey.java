package ru.decide.module.impl.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.SliderSetting;
import ru.decide.utils.other.Instance;

/**
 * Sounds key - звук при нажатии клавиш и кликов мыши.
 * <p>
 * Перенос CreamyKeys 1.21.X. Сами звуки взяты из него (наборы
 * {@code cherrymx_black_pbt} для клавиш и {@code cherrymx_blue_abs_2} для мыши -
 * ровно те, что в моде стоят по умолчанию), но 40 файлов набора заведены как
 * одно звуковое событие: Minecraft сам выбирает случайный файл из списка,
 * поэтому каждый следующий нажим звучит чуть иначе - как в оригинале.
 * <p>
 * Хуки нажатий - в миксинах {@code KeyboardMixinKeys}/{@code MouseMixinClick},
 * они же следят, чтобы звук не играл при прокрутке и перетаскивании.
 */
@ModuleInfo(
        name = "Sounds key",
        desc = "SMR звук клавиш",
        category = Category.UTILITIES
)
public final class SoundsKey extends Module {

    private static final Identifier SOUND_KEY = Identifier.of("decide", "key.press");
    private static final Identifier SOUND_MOUSE = Identifier.of("decide", "key.mouse");

    public final SliderSetting volume = new SliderSetting(this, "Громкость", 50.0F, 0.0F, 100.0F, 10.0F);

    public static SoundsKey getInstance() {
        return Instance.get(SoundsKey.class);
    }

    /** Звук клавиши. */
    public static void playKey() {
        play(SOUND_KEY);
    }

    /** Звук клика мыши. */
    public static void playMouse() {
        play(SOUND_MOUSE);
    }

    private static void play(Identifier sound) {
        SoundsKey module = getInstance();
        if (module == null || !module.isEnabled()) return;

        float v = module.volume.getValue() / 100.0F;
        if (v <= 0.0F) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSoundManager() == null) return;
        try {
            client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvent.of(sound), 1.0F, v));
        } catch (Throwable ignored) {
            // звук не критичен: если менеджер занят - просто пропускаем щелчок
        }
    }
}

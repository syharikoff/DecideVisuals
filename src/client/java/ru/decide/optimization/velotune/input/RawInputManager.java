package ru.decide.optimization.velotune.input;

import ru.decide.optimization.velotune.input.WindowsRawInputBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class RawInputManager {
    private static final WindowsRawInputBuffer BUFFER = new WindowsRawInputBuffer();

    private RawInputManager() {
    }

    public static WindowsRawInputBuffer buffer() {
        return BUFFER;
    }
}

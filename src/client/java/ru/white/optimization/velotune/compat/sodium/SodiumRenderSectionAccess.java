package ru.white.optimization.velotune.compat.sodium;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public interface SodiumRenderSectionAccess {
    public int velotune$getOriginY();
}

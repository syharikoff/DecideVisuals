package ru.white.mixin;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** К Loot View: нужен исходный NativeImage и размеры спрайта, чтобы снять силуэт модели предмета. */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor("image")
    NativeImage nightix$image();

    @Accessor("width")
    int nightix$width();

    @Accessor("height")
    int nightix$height();
}

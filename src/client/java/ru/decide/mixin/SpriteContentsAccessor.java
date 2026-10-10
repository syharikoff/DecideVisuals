package ru.decide.mixin;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** К Loot View: нужен исходный NativeImage и размеры спрайта, чтобы снять силуэт модели предмета. */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor("image")
    NativeImage decide$image();

    @Accessor("width")
    int decide$width();

    @Accessor("height")
    int decide$height();
}

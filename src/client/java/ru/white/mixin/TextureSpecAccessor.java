package ru.white.mixin;

import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** RenderSetup.TextureSpec — package-private record, доступ только через targets. */
@Mixin(targets = "net.minecraft.client.render.RenderSetup$TextureSpec")
public interface TextureSpecAccessor {
    @Accessor("location")
    Identifier nightix$location();
}

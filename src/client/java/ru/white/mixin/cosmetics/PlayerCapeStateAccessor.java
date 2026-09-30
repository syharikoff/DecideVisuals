package ru.white.mixin.cosmetics;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntityRenderState.class)
public interface PlayerCapeStateAccessor {
    @Accessor("field_53536")
    float nightix$capeFlap();

    @Accessor("field_53537")
    float nightix$capeLean();

    @Accessor("field_53538")
    float nightix$capeLean2();
}

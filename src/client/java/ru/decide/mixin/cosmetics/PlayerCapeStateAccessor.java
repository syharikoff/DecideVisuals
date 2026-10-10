package ru.decide.mixin.cosmetics;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntityRenderState.class)
public interface PlayerCapeStateAccessor {
    @Accessor("field_53536")
    float decide$capeFlap();

    @Accessor("field_53537")
    float decide$capeLean();

    @Accessor("field_53538")
    float decide$capeLean2();
}

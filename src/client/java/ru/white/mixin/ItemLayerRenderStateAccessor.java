package ru.white.mixin;

import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRenderState.LayerRenderState.class)
public interface ItemLayerRenderStateAccessor {
    @Accessor("tints")
    int[] nightix$tints();

    /** Спец-рендереры (посох, зачарованная книга) квадов не дают — Loot View их пропускает. */
    @Accessor("specialModelType")
    SpecialModelRenderer<?> nightix$specialModelType();
}

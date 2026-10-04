package ru.white.mixin;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.white.module.impl.render.WastedDeath;

/**
 * Wasted: во время кинематографичного облёта камера уезжает далеко от игрока,
 * и smart-culling начинает выкидывать чанки — в кадре появляются дыры.
 *
 * priority = 1100 обязателен: Sodium целится в тот же updateCamera с priority 1000,
 * и при одинаковом приоритете Mixin отказывается мержить инъекции.
 */
@Mixin(value = WorldRenderer.class, priority = 1100)
public abstract class WastedCullingMixin {

    @ModifyArg(
            method = "updateCamera",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;updateSectionOcclusionGraph(ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Ljava/util/List;Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;)V"),
            index = 0)
    private boolean nightix$wastedDisableSmartCull(boolean smartCull) {
        return smartCull && !WastedDeath.isRunning();
    }
}

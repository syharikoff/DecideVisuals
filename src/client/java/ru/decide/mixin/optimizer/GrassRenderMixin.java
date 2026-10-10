package ru.decide.mixin.optimizer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.decide.module.impl.render.Optimizer;

import java.util.List;

/**
 * Optimizer → «Без травы»: не рисуем траву, папоротник и морскую траву.
 * Блок отбрасывается до отрисовки модели, поэтому не тратимся на геометрию.
 */
@Mixin(BlockRenderManager.class)
public abstract class GrassRenderMixin {

    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void decide$skipGrass(BlockState state, BlockPos pos, BlockRenderView world,
                                  MatrixStack matrices, VertexConsumer vertices, boolean hasBlockEntity,
                                  List<BlockModelPart> parts, CallbackInfo ci) {
        if (!Optimizer.isNoGrass()) return;

        Block block = state.getBlock();
        if (block == Blocks.SHORT_GRASS
                || block == Blocks.TALL_GRASS
                || block == Blocks.FERN
                || block == Blocks.LARGE_FERN
                || block == Blocks.DEAD_BUSH
                || block == Blocks.SEAGRASS
                || block == Blocks.TALL_SEAGRASS) {
            ci.cancel();
        }
    }
}
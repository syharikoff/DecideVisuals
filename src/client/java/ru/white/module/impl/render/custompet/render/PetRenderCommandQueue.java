package ru.white.module.impl.render.custompet.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.font.TextRenderer;
import org.joml.Quaternionf;

public class PetRenderCommandQueue implements OrderedRenderCommandQueue {

    private final VertexConsumerProvider.Immediate immediate;

    public PetRenderCommandQueue(VertexConsumerProvider.Immediate immediate) {
        this.immediate = immediate;
    }

    @Override
    public RenderCommandQueue getBatchingQueue(int batchingIndex) {
        return this;
    }

    @Override
    public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, Custom custom) {
        custom.render(matrices.peek(), this.immediate.getBuffer(renderLayer));
    }

    @Override
    public void submitBlock(MatrixStack matrices, BlockState blockState, int light, int overlay, int color) {
        CustomPetRendererBridge.renderBlockAsEntity(blockState, matrices, this.immediate, light, overlay);
    }

    @Override
    public void submitShadowPieces(MatrixStack matrices, float shadowRadius, java.util.List<EntityRenderState.ShadowPiece> shadowPieces) {
    }

    @Override
    public void submitLabel(MatrixStack matrices, Vec3d pos, int color, Text text, boolean background, int verticalOffset, double depthTestStart, CameraRenderState cameraState) {
    }

    @Override
    public void submitText(MatrixStack matrices, float x, float y, OrderedText text, boolean background, TextRenderer.TextLayerType layerType, int color, int light, int backgroundColor, int backgroundOverflow) {
    }

    @Override
    public void submitFire(MatrixStack matrices, EntityRenderState renderState, Quaternionf rotation) {
    }

    @Override
    public void submitLeash(MatrixStack matrices, EntityRenderState.LeashData leashData) {
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S renderState, MatrixStack matrices, RenderLayer renderLayer, int light, int overlay, int color, Sprite sprite, int modelIndex, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlayCommand) {
    }

    @Override
    public void submitModelPart(ModelPart modelPart, MatrixStack matrices, RenderLayer renderLayer, int light, int overlay, Sprite sprite, boolean isEmissive, boolean zOffset, int color, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlayCommand, int modelIndex) {
    }

    @Override
    public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState movingBlockRenderState) {
    }

    @Override
    public void submitBlockStateModel(MatrixStack matrices, RenderLayer renderLayer, BlockStateModel blockStateModel, float x, float y, float z, int light, int overlay, int color) {
    }

    @Override
    public void submitItem(MatrixStack matrices, ItemDisplayContext displayContext, int modelIndex, int light, int overlay, int[] tints, java.util.List<BakedQuad> quads, RenderLayer renderLayer, ItemRenderState.Glint glint) {
    }

    @Override
    public void submitCustom(LayeredCustom layeredCustom) {
    }
}
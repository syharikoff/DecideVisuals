/*
 * Decompiled with CFR 0.152.
 */
package ru.decide.cosmetics.render;

import ru.decide.cosmetics.geckolib.GeckolibCosmeticRenderer;
import ru.decide.cosmetics.geo.GeoModel;
import ru.decide.cosmetics.model.CosmeticModel;
import ru.decide.cosmetics.model.ModelPosition;
import ru.decide.cosmetics.render.RenderStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;

@Environment(value=EnvType.CLIENT)
public class CosmeticRenderer {
    private static CosmeticRenderer instance;
    private final GeckolibCosmeticRenderer geckolibRenderer = GeckolibCosmeticRenderer.getInstance();
    private final RenderStack stack = new RenderStack();

    public static CosmeticRenderer getInstance() {
        if (instance == null) {
            instance = new CosmeticRenderer();
        }
        return instance;
    }

    public void renderCosmetic(CosmeticModel cosmetic, AbstractClientPlayerEntity player, MatrixStack matrices, VertexConsumerProvider consumers, int light, PlayerEntityModel playerModel, float tickDelta) {
        if (cosmetic == null || cosmetic.getTextureId() == null || playerModel == null) {
            return;
        }
        if (this.isPet(cosmetic)) {
            this.renderPet(cosmetic, matrices, consumers, null, light, playerModel);
            return;
        }
        this.stack.update(matrices);
        this.stack.push();
        float yOffset = this.transformToPosition(cosmetic, playerModel, matrices);
        this.stack.rotateZDegrees(180.0f);
        this.stack.translate(cosmetic.getX(), cosmetic.getY() + yOffset, cosmetic.getZ());
        this.stack.rotateYDegrees(cosmetic.getYaw());
        this.stack.rotateXDegrees(cosmetic.getPitch());
        this.stack.rotateZDegrees(cosmetic.getRoll());
        this.stack.scale(cosmetic.getScale(), cosmetic.getScale(), cosmetic.getScale());
        this.geckolibRenderer.renderCosmetic(cosmetic, matrices, consumers, light);
        this.stack.pop();
    }

    public void renderCosmetic(CosmeticModel cosmetic, AbstractClientPlayerEntity player, MatrixStack matrices, VertexConsumer vertexConsumer, int light, PlayerEntityModel playerModel, float tickDelta) {
        if (cosmetic == null || cosmetic.getTextureId() == null || vertexConsumer == null || playerModel == null) {
            return;
        }
        if (this.isPet(cosmetic)) {
            this.renderPet(cosmetic, matrices, null, vertexConsumer, light, playerModel);
            return;
        }
        this.stack.update(matrices);
        this.stack.push();
        float yOffset = this.transformToPosition(cosmetic, playerModel, matrices);
        this.stack.rotateZDegrees(180.0f);
        this.stack.translate(cosmetic.getX(), cosmetic.getY() + yOffset, cosmetic.getZ());
        this.stack.rotateYDegrees(cosmetic.getYaw());
        this.stack.rotateXDegrees(cosmetic.getPitch());
        this.stack.rotateZDegrees(cosmetic.getRoll());
        this.stack.scale(cosmetic.getScale(), cosmetic.getScale(), cosmetic.getScale());
        this.geckolibRenderer.renderCosmetic(cosmetic, matrices, vertexConsumer, light);
        this.stack.pop();
    }

    private boolean isPet(CosmeticModel cosmetic) {
        if (cosmetic == null) {
            return false;
        }
        if ("pet".equalsIgnoreCase(cosmetic.getType())) {
            return true;
        }
        if (cosmetic.getCategory() == 2) {
            return true;
        }
        String lower = cosmetic.getName().toLowerCase();
        return lower.contains("pet") || lower.contains("bee") || lower.contains("radish");
    }

    private void renderPet(CosmeticModel cosmetic, MatrixStack matrices, VertexConsumerProvider consumers, VertexConsumer vertexConsumer, int light, PlayerEntityModel playerModel) {
        this.stack.update(matrices);
        this.stack.push();
        this.transformToModelPart(playerModel.body, matrices);
        this.stack.rotateZDegrees(180.0f);
        GeoModel geoModel = this.geckolibRenderer.getOrParseModel(cosmetic);
        if (geoModel != null) {
            geoModel.computeBounds();
            this.stack.translate(-geoModel.centerX, -geoModel.centerY, -geoModel.centerZ);
        }
        float bob = (float)Math.sin((double)(System.currentTimeMillis() % 2400L) / 2400.0 * Math.PI * 2.0) * 0.035f;
        this.stack.translate(0.48f, 0.6f + bob, 0.08f);
        this.stack.rotateYDegrees(cosmetic.getYaw());
        this.stack.rotateXDegrees(cosmetic.getPitch());
        this.stack.rotateZDegrees(cosmetic.getRoll());
        this.stack.scale(cosmetic.getScale(), cosmetic.getScale(), cosmetic.getScale());
        if (consumers != null) {
            this.geckolibRenderer.renderCosmetic(cosmetic, matrices, consumers, light);
        } else if (vertexConsumer != null) {
            this.geckolibRenderer.renderCosmetic(cosmetic, matrices, vertexConsumer, light);
        }
        this.stack.pop();
    }

    private boolean isAnimalHat(CosmeticModel cosmetic) {
        if (cosmetic == null || cosmetic.getName() == null) {
            return false;
        }
        String lower = cosmetic.getName().toLowerCase();
        return lower.contains("frog") || lower.contains("chicken") || lower.contains("camel") || lower.contains("sheep") || lower.contains("armadillo");
    }

    private float transformToPosition(CosmeticModel cosmetic, PlayerEntityModel playerModel, MatrixStack matrices) {
        float yOffset = 0.0f;
        if (playerModel == null) {
            return yOffset;
        }
        ModelPosition pos = cosmetic.getPosition();
        switch (pos) {
            case HEAD: {
                this.transformToModelPart(playerModel.head, matrices);
                if (this.isAnimalHat(cosmetic)) {
                    yOffset = 0.26f;
                    break;
                }
                yOffset = 0.04f;
                break;
            }
            case ABOVE_HEAD: {
                this.transformToModelPart(playerModel.head, matrices);
                yOffset = 0.38f;
                break;
            }
            case BODY: {
                this.transformToModelPart(playerModel.body, matrices);
                yOffset = -0.3f;
                break;
            }
            case RIGHT_ARM: {
                this.transformToModelPart(playerModel.rightArm, matrices);
                yOffset = -0.25f;
                break;
            }
            case LEFT_ARM: {
                this.transformToModelPart(playerModel.leftArm, matrices);
                yOffset = -0.25f;
                break;
            }
            case RIGHT_LEG: {
                this.transformToModelPart(playerModel.rightLeg, matrices);
                yOffset = -0.35f;
                break;
            }
            case LEFT_LEG: {
                this.transformToModelPart(playerModel.leftLeg, matrices);
                yOffset = -0.35f;
                break;
            }
        }
        return yOffset;
    }

    private void transformToModelPart(ModelPart part, MatrixStack matrices) {
        if (part != null) {
            part.applyTransform(matrices);
        }
    }
}


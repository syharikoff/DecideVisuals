package ru.white.module.impl.render.custompet.render;

import net.minecraft.block.Blocks;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import ru.white.module.impl.render.custompet.CustomPetVariant;
import ru.white.module.impl.render.custompet.entity.CustomPetEntity;
import ru.white.module.impl.render.custompet.model.CustomPetModel;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

public class CustomPetRenderer extends GeoEntityRenderer<CustomPetEntity, CustomPetRenderState> {
    private static final float LILY_PAD_SCALE = 1.35F;
    private static final float LILY_PAD_Y_OFFSET = -0.008F;
    private static final float OWL_SCALE = 0.55F;
    private static final String[] OWL_EFFECT_BONES = new String[]{
            "trick_star", "stumble_star_left", "stumble_star_right", "celebrate_confetti_1", "celebrate_confetti_2", "celebrate_confetti_3", "signature"
    };
    private static final String[] OWL_GROUND_ROPE_BONES = new String[]{"rope_spin", "left_handle", "right_handle"};
    private static final String[] OWL_FLIGHT_ROPE_BONES = new String[]{"fly_rope_left", "fly_rope_right"};
    private static final String[] CONDITIONAL_BONES = new String[]{
            "body_default",
            "body_merchant",
            "leaf",
            "gardener_hat",
            "watering_can",
            "sourcerer_hat",
            "accessories",
            "fishing_rod",
            "fishing_rod_2",
            "fishing_rod_3",
            "guitar",
            "flute",
            "bongo",
            "bass",
            "umbrella",
            "umbrella2",
            "umbrella3",
            "fisherman_umbrella",
            "fisherman_umbrella2",
            "fisherman_umbrella3",
            "pride"
    };

    public CustomPetRenderer(EntityRendererFactory.Context context) {
        super(context, new CustomPetModel());
        this.shadowRadius = 0.35F;
    }

    @Override
    public void render(CustomPetRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        super.render(state, matrices, queue, cameraState);
        if (Boolean.TRUE.equals(state.getOrDefaultGeckolibData(CustomPetModel.AIRBORNE, false))) {
            if (!Boolean.TRUE.equals(state.getOrDefaultGeckolibData(CustomPetModel.OWL, false))) {
                String string = state.getOrDefaultGeckolibData(CustomPetModel.VARIANT, CustomPetVariant.NITWIT.name());
                CustomPetVariant customPetVariant = CustomPetVariant.fromSerializedName(string);
                if (!customPetVariant.isRobot()) {
                    matrices.push();
                    matrices.translate(-0.675F, LILY_PAD_Y_OFFSET, -0.675F);
                    matrices.scale(LILY_PAD_SCALE, 1.0F, LILY_PAD_SCALE);
                    queue.submitBlock(matrices, Blocks.LILY_PAD.getDefaultState(), state.light, OverlayTexture.DEFAULT_UV, state.outlineColor);
                    matrices.pop();
                }
            }
        }
    }

    @Override
    public RenderLayer getRenderType(CustomPetRenderState state, Identifier texture) {
        return RenderLayers.entityCutout(texture);
    }

    @Override
    public float getMotionAnimThreshold(CustomPetEntity customPetEntity) {
        return 5.0E-4F;
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<CustomPetRenderState> renderPassInfo, float widthScale, float heightScale) {
        if (Boolean.TRUE.equals(renderPassInfo.getOrDefaultGeckolibData(CustomPetModel.OWL, false))) {
            widthScale *= OWL_SCALE;
            heightScale *= OWL_SCALE;
        }

        super.scaleModelForRender(renderPassInfo, widthScale, heightScale);
    }

    private static void adjustOwlBones(BoneSnapshots boneSnapshots, boolean bl) {
        for (String string : OWL_EFFECT_BONES) {
            boneSnapshots.ifPresent(string, boneSnapshot -> boneSnapshot.skipRender(true).skipChildrenRender(true));
        }

        for (String string : OWL_GROUND_ROPE_BONES) {
            boneSnapshots.ifPresent(string, boneSnapshot -> boneSnapshot.skipRender(bl).skipChildrenRender(bl));
        }

        for (String string : OWL_FLIGHT_ROPE_BONES) {
            boneSnapshots.ifPresent(string, boneSnapshot -> boneSnapshot.skipRender(!bl).skipChildrenRender(!bl));
        }
    }

    private static boolean isBoneVisible(String string, CustomPetVariant customPetVariant, boolean bl, boolean bl2) {
        return switch (string) {
            case "body_merchant" -> customPetVariant.usesMerchantBody();
            case "body_default" -> !customPetVariant.usesMerchantBody();
            case "leaf" -> customPetVariant.usesMerchantLeaf() && !bl;
            case "gardener_hat", "watering_can" -> customPetVariant.usesGardenerGear();
            case "sourcerer_hat" -> customPetVariant.usesSorcererHat();
            case "accessories", "fishing_rod", "fishing_rod_2", "fishing_rod_3" -> customPetVariant.usesFishermanGear();
            case "umbrella2" -> bl2 && !customPetVariant.usesFishermanGear();
            case "fisherman_umbrella2" -> bl2 && customPetVariant.usesFishermanGear();
            case "umbrella3" -> !bl2 && bl && !customPetVariant.usesFishermanGear();
            case "fisherman_umbrella3" -> !bl2 && bl && customPetVariant.usesFishermanGear();
            default -> false;
        };
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<CustomPetRenderState> renderPassInfo, BoneSnapshots boneSnapshots) {
        boolean bl = Boolean.TRUE.equals(renderPassInfo.getOrDefaultGeckolibData(CustomPetModel.AIRBORNE, false));
        if (Boolean.TRUE.equals(renderPassInfo.getOrDefaultGeckolibData(CustomPetModel.OWL, false))) {
            adjustOwlBones(boneSnapshots, bl);
        } else {
            String string = renderPassInfo.getOrDefaultGeckolibData(CustomPetModel.VARIANT, CustomPetVariant.NITWIT.name());
            CustomPetVariant customPetVariant = CustomPetVariant.fromSerializedName(string);
            boolean bl2 = Boolean.TRUE.equals(renderPassInfo.getOrDefaultGeckolibData(CustomPetModel.UMBRELLA, false));

            for (String string2 : CONDITIONAL_BONES) {
                boolean bl3 = !isBoneVisible(string2, customPetVariant, bl2, bl);
                boneSnapshots.ifPresent(string2, boneSnapshot -> boneSnapshot.skipRender(bl3).skipChildrenRender(bl3));
            }
        }
    }

    @Override
    public CustomPetRenderState createRenderState(CustomPetEntity customPetEntity, Void relatedObject) {
        return new CustomPetRenderState();
    }

    @Override
    public CustomPetRenderState fillRenderState(CustomPetEntity animatable, Void relatedObject, CustomPetRenderState renderState, float partialTick) {
        renderState.age = animatable.getAnimationAge() + partialTick;
        CustomPetRenderState result = super.fillRenderState(animatable, relatedObject, renderState, partialTick);
        renderState.addGeckolibData(DataTickets.ENTITY_YAW, renderState.bodyYaw);
        renderState.addGeckolibData(DataTickets.ENTITY_BODY_YAW, renderState.bodyYaw);
        return result;
    }
}
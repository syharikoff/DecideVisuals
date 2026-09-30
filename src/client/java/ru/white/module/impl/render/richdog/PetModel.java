package ru.white.module.impl.render.richdog;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class PetModel {

    private final ModelPart head;
    private final ModelPart neck;
    private final ModelPart body;
    private final ModelPart frontLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart leftBackLeg;
    private final ModelPart rightBackLeg;
    private final ModelPart tail;

    public PetModel() {
        ModelPart root = bakeRoot();
        this.head = root.getChild("head");
        this.neck = root.getChild("neck");
        this.body = root.getChild("body");
        this.frontLeftLeg = root.getChild("front_left_leg");
        this.frontRightLeg = root.getChild("front_right_leg");
        this.leftBackLeg = root.getChild("left_back_leg");
        this.rightBackLeg = root.getChild("right_back_leg");
        this.tail = root.getChild("tail");
    }

    private static ModelPart bakeRoot() {
        ModelData mesh = new ModelData();
        ModelPartData parts = mesh.getRoot();

        ModelPartData head = parts.addChild("head",
                ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-3.0F, -3.0F, -4.0F, 6.0F, 6.0F, 4.0F)
                        .uv(21, 0).cuboid(-1.5F, 0.0F, -7.0F, 3.0F, 3.0F, 3.0F),
                ModelTransform.origin(0.0F, 10.5F, -6.8F));

        head.addChild("left_ear",
                ModelPartBuilder.create()
                        .uv(32, 4).cuboid(0.0F, -5.0F, -1.5F, 1.0F, 3.0F, 3.0F)
                        .uv(34, 1).cuboid(0.0F, -5.5F, -0.75F, 1.0F, 1.0F, 1.5F),
                ModelTransform.origin(3.0F, 3.0F, -2.0F));

        head.addChild("right_ear",
                ModelPartBuilder.create()
                        .uv(32, 4).cuboid(-1.0F, -5.0F, -1.5F, 1.0F, 3.0F, 3.0F)
                        .uv(34, 1).cuboid(-1.0F, -5.5F, -0.75F, 1.0F, 1.0F, 1.5F),
                ModelTransform.origin(-3.0F, 3.0F, -2.0F));

        parts.addChild("neck",
                ModelPartBuilder.create()
                        .uv(15, 7).cuboid(-2.95F, -1.0F, -4.0F, 5.9F, 5.0F, 6.0F),
                ModelTransform.of(0.0F, 10.5F, -5.0F, -25.0F * MathHelper.RADIANS_PER_DEGREE, 0.0F, 0.0F));

        ModelPartData body = parts.addChild("body",
                ModelPartBuilder.create(),
                ModelTransform.origin(0.0F, 13.5F, -5.0F));

        body.addChild("chest",
                ModelPartBuilder.create()
                        .uv(32, 13).cuboid(-4.0F, -3.5F, -3.0F, 8.0F, 7.0F, 6.0F),
                ModelTransform.origin(0.0F, 0.0F, 3.0F));

        body.addChild("back",
                ModelPartBuilder.create()
                        .uv(3, 19).cuboid(-3.0F, -3.0F, -0.5F, 6.0F, 6.0F, 11.0F),
                ModelTransform.origin(0.0F, -0.5F, 5.5F));

        parts.addChild("front_left_leg",
                ModelPartBuilder.create()
                        .uv(42, 0).cuboid(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                ModelTransform.origin(1.5F, 16.0F, -3.0F));

        parts.addChild("front_right_leg",
                ModelPartBuilder.create().mirrored()
                        .uv(42, 0).cuboid(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                ModelTransform.origin(-1.5F, 16.0F, -3.0F));

        parts.addChild("left_back_leg",
                ModelPartBuilder.create()
                        .uv(52, 0).cuboid(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                ModelTransform.origin(1.5F, 16.0F, 9.0F));

        parts.addChild("right_back_leg",
                ModelPartBuilder.create().mirrored()
                        .uv(52, 0).cuboid(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                ModelTransform.origin(-1.5F, 16.0F, 9.0F));

        parts.addChild("tail",
                ModelPartBuilder.create()
                        .uv(2, 12).cuboid(-1.0F, 2.0F, -1.0F, 2.0F, 8.0F, 2.0F),
                ModelTransform.of(0.0F, 9.0F, 10.0F, 22.5F * MathHelper.RADIANS_PER_DEGREE, 0.0F, 0.0F));

        return TexturedModelData.of(mesh, 60, 36).createModel();
    }

    public void setupAnim(float ageInTicks, PetBrain brain) {
        head.yaw = brain.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        head.pitch = brain.getPitch() * MathHelper.RADIANS_PER_DEGREE;

        float swing = brain.limbSwing * 0.6662F;
        float amount = brain.limbSwingAmount;
        frontLeftLeg.pitch = MathHelper.cos(swing) * 1.4F * amount;
        frontRightLeg.pitch = MathHelper.cos(swing + MathHelper.PI) * 1.4F * amount;
        leftBackLeg.pitch = MathHelper.cos(swing + MathHelper.PI) * 1.4F * amount;
        rightBackLeg.pitch = MathHelper.cos(swing) * 1.4F * amount;

        if (brain.isLay()) {
            frontLeftLeg.pitch = (float) Math.toRadians(-90);
            frontRightLeg.pitch = (float) Math.toRadians(-90);
            leftBackLeg.pitch = (float) Math.toRadians(90);
            rightBackLeg.pitch = (float) Math.toRadians(90);

            frontLeftLeg.yaw = (float) Math.toRadians(-22);
            frontRightLeg.yaw = (float) Math.toRadians(22);
            leftBackLeg.yaw = (float) Math.toRadians(22);
            rightBackLeg.yaw = (float) Math.toRadians(-22);
        } else {
            frontLeftLeg.yaw = frontRightLeg.yaw = leftBackLeg.yaw = rightBackLeg.yaw = 0.0F;
        }

        tail.pitch = (float) Math.toRadians(brain.isLay() ? 45 : 22);
        tail.roll = (float) (Math.toRadians(-22.5F) + 22.5F * MathHelper.RADIANS_PER_DEGREE + MathHelper.cos(ageInTicks * 0.15F) * 0.3F);
    }

    public void render(MatrixStack pose, VertexConsumer consumer, int packedLight, int packedOverlay, PetBrain brain) {
        pose.push();
        pose.translate(0.0F, 1.2F - (brain.isLay() ? 0.3F : 0.0F), 0.0F);
        pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));
        pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(brain.getBody()));

        head.render(pose, consumer, packedLight, packedOverlay);
        neck.render(pose, consumer, packedLight, packedOverlay);
        body.render(pose, consumer, packedLight, packedOverlay);
        frontLeftLeg.render(pose, consumer, packedLight, packedOverlay);
        frontRightLeg.render(pose, consumer, packedLight, packedOverlay);
        leftBackLeg.render(pose, consumer, packedLight, packedOverlay);
        rightBackLeg.render(pose, consumer, packedLight, packedOverlay);
        tail.render(pose, consumer, packedLight, packedOverlay);

        pose.pop();
    }
}

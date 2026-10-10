package ru.decide.mixin;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.*;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.decide.module.impl.render.HoldMyItems;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import java.util.Random;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererHmiMixin {
    @Shadow
    protected abstract void renderArm(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Arm arm);

    @Shadow
    protected abstract void renderArmHoldingItem(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, float equipProgress, float swingProgress, Arm arm);

    @Shadow
    protected abstract void renderMapInOneHand(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, float equipProgress, Arm arm, float swingProgress, ItemStack item);

    @Shadow
    protected abstract void renderMapInBothHands(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, float pitch, float equipProgress, float swingProgress);

    @Shadow
    protected abstract void applyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress);

    @Shadow
    protected abstract void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress);

    @Shadow
    public abstract void renderItem(net.minecraft.entity.LivingEntity entity, ItemStack item, ItemDisplayContext renderMode, MatrixStack matrices, OrderedRenderCommandQueue queue, int light);

    @Unique
    private boolean hmi$repPower = false;
    @Unique
    private float hmi$prevAge = 0.0F;
    @Unique
    private double hmi$previousRotation = 0.0;
    @Unique
    private float hmi$swingAngleY = 0.0F;
    @Unique
    private float hmi$swingAngleX = 0.0F;
    @Unique
    private float hmi$swingVelocityY = 0.0F;
    @Unique
    private float hmi$swingVelocityX = 0.0F;
    @Unique
    private float hmi$swingVelocityZ = 0.0F;
    @Unique
    private float hmi$vertAngleY = 0.0F;
    @Unique
    private float hmi$vertVelocityY = 0.0F;
    @Unique
    private float hmi$vertVelocityYSlime = 0.0F;
    @Unique
    private float hmi$vertAngleYSlime = 0.0F;
    @Unique
    private float hmi$riptideCounter = 0.0F;
    @Unique
    private float hmi$netherCounter = 0.0F;
    @Unique
    private float hmi$fallCounter = 0.0F;
    @Unique
    private float hmi$inWaterCounter = 0.0F;
    @Unique
    private float hmi$inspect = 0.0F;
    @Unique
    private float hmi$tilt = 0.0F;
    @Unique
    private float hmi$freezeCounter = 0.0F;
    @Unique
    private float hmi$clCount = 0.0F;
    @Unique
    private float hmi$crawlCount = 0.0F;
    @Unique
    private float hmi$directionalCrawlCount = 0.0F;
    @Unique
    private float hmi$climbCount = 0.0F;
    @Unique
    private float hmi$mouseHolding = 1.0F;
    @Unique
    private boolean hmi$isSwinging = false;
    @Unique
    private float hmi$swingProgress = 0.0F;
    @Unique
    private boolean hmi$isForward = false;
    @Unique
    private boolean hmi$isAttacking = false;
    @Unique
    private boolean hmi$left = false;

    @Shadow
    private ItemStack mainHand;

    @Shadow
    @Final
    private MinecraftClient client;

    @Unique
    private float hmiEaseInOutBack(float x) {
        float c1 = 1.70158F;
        float c2 = c1 * 1.525F;
        return (float) ((double) x < 0.5
                ? Math.pow(2.0 * x, 2.0) * ((c2 + 1.0F) * 2.0F * x - c2) / 2.0
                : (Math.pow(2.0 * x - 2.0, 2.0) * ((c2 + 1.0F) * (x * 2.0F - 2.0F) + c2) + 2.0) / 2.0);
    }

    @Unique
    private float hmiGetAttackDamage(ItemStack stack) {
        AttributeModifiersComponent modifiers = stack.getComponents().get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return 0.0F;
        }
        float totalDamage = 0.0F;
        for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().value() == EntityAttributes.ATTACK_DAMAGE.value()) {
                totalDamage += (float) entry.modifier().value();
            }
        }
        return totalDamage;
    }

    @Unique
    private boolean hmi$isCrawling(AbstractClientPlayerEntity player) {
        return player.getPose() == EntityPose.SWIMMING && !player.isInSwimmingPose();
    }

    @Unique
    private boolean hmiIsSharpAnimation(HoldMyItems config) {
        return config != null && config.isSharpAnimation();
    }

    @Unique
    private void hmiAltSwing(MatrixStack matrices, Arm arm, float swingProgress, ItemStack item) {
        int i = arm == Arm.RIGHT ? 1 : -1;
        float f = MathHelper.sin(swingProgress * 3.14F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0F + f * 0.0F)));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * -45.0F));
    }

    public void hmiRenderCustomFirstPersonItem(
            AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, Arm arm, float sideFactor,
            float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
            OrderedRenderCommandQueue queue, int light
    ) {
        HoldMyItems hmi = HoldMyItems.getInstance();
        if (hmi != null && hmi.isEnabled()) {
            boolean isUsingSpyglass = player.isUsingItem() && player.getActiveItem().isOf(Items.SPYGLASS);
            if (!isUsingSpyglass) {
                HoldMyItems config = HoldMyItems.getInstance();
                float yaw = player.getYaw();
                double radians = Math.toRadians((double) yaw);
                double forwardX = -Math.sin(radians);
                double forwardZ = Math.cos(radians);
                Vec3d horizontalVelocity = player.getVelocity();
                double dotProduct = horizontalVelocity.x * forwardX + horizontalVelocity.z * forwardZ;
                double crossProduct = horizontalVelocity.x * forwardZ - horizontalVelocity.z * forwardX;
                float al;
                if (player.getPitch() != 0.0F) {
                    al = 90.0F / player.getPitch() / 10.0F;
                } else {
                    al = 1.0F;
                }

                if (al > 1.0F) {
                    al = 1.0F;
                }

                if (al < 0.0F) {
                    al = 1.0F;
                }

                boolean bl = hand == Hand.MAIN_HAND;
                matrices.push();
                matrices.push();

                if (bl) {
                    matrices.translate(config.mainHandX.getValue(), config.mainHandY.getValue(), config.mainHandZ.getValue());
                } else {
                    matrices.translate(config.offHandX.getValue(), config.offHandY.getValue(), config.offHandZ.getValue());
                }

                double tt = (1.0 / Math.max(MinecraftClient.getInstance().getCurrentFps(), 1)) * 30.0;
                float smoothness = MathHelper.clamp(config.smoothness.getValue(), 0.35F, 2.5F);
                float hmiProgress = (float) Math.pow(MathHelper.clamp(swingProgress, 0.0F, 1.0F), smoothness);
                float swing_rot = (double) hmiProgress < 0.6 ? MathHelper.sin(MathHelper.clamp(hmiProgress, 0.0F, 0.12506F) * 12.56F) : MathHelper.sin(MathHelper.clamp(hmiProgress, 0.62532F, 0.75038F) * 12.56F);
                float swing = MathHelper.sin(hmiProgress * 3.14F);
                swing = hmiEaseInOutBack(swing);
                boolean sharpSword = item.isIn(ItemTags.SWORDS) && hmiIsSharpAnimation(config);
                if ((item.isOf(Items.EXPERIENCE_BOTTLE) || item.isOf(Items.WIND_CHARGE) || item.isOf(Items.EGG) || item.isOf(Items.ENDER_EYE) || item.isOf(Items.SNOWBALL) || item.getItem() instanceof SplashPotionItem || item.getItem() instanceof LingeringPotionItem) && player.getOffHandStack().isEmpty() && item.getUseAction() != UseAction.TRIDENT && !item.isOf(Items.FIRE_CHARGE) && !player.isInSwimmingPose() && !hmi$isCrawling(player) && !player.isClimbing()) {
                    if (player.getMainArm() == Arm.LEFT) {
                        bl = !bl;
                    }

                    matrices.push();
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-25.0F * sideFactor));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(25.0F * sideFactor * swing));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F * swing));
                    matrices.translate(-0.15 * (double) sideFactor, 0.1, 0.1);
                    matrices.translate((double) 0.0F, -0.55 * (double) swing, 0.4 * (double) swing * (double) 3.14F);
                    renderArmHoldingItem(matrices, queue, light, equipProgress, 0.0F, arm.getOpposite());
                    matrices.pop();
                }

                if (this.client.options.attackKey.isPressed() && !this.hmi$isAttacking && (double) swingProgress == (double) 0.0F) {
                    this.hmi$left = !this.hmi$left;
                }

                if (!item.isEmpty()) {
                    if (player.getMainArm() == Arm.LEFT) {
                        bl = !bl;
                    }

                    if ((this.hmi$left || item.isIn(ItemTags.AXES) || item.getUseAction() == UseAction.TRIDENT || item.getUseAction() == UseAction.BLOCK) && !item.isIn(ItemTags.SHOVELS)) {
                        if (sharpSword) {
                            matrices.translate(0.1 * (double) sideFactor * (double) swing_rot, 0.1 * (double) swing_rot, (double) -0.5F * (double) swing);
                            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-30.0F * swing_rot));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20.0F * swing_rot * sideFactor));
                            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(40.0F * swing));
                        } else if (!item.isIn(ItemTags.SWORDS) && !item.isIn(ItemTags.AXES)) {
                            if (item.getUseAction() == UseAction.TRIDENT) {
                                matrices.translate((double) 0.0F, (double) 0.0F, 0.45 * (double) swing_rot);
                                matrices.translate((double) -0.25F * (double) sideFactor * (double) swing, -0.35 * (double) swing_rot, -0.6 * (double) swing);
                                matrices.translate((double) 0.0F, 0.1 * (double) swing, (double) 0.0F);
                                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(15.0F * swing_rot * sideFactor));
                                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(30.0F * swing_rot * sideFactor));
                            } else if (item.isIn(ConventionalItemTags.TOOLS) && item.getUseAction() != UseAction.BLOCK && !item.isIn(ItemTags.SHOVELS)) {
                                matrices.translate(0.1 * (double) sideFactor * (double) swing_rot, 0.1 * (double) swing_rot, (double) -0.5F * (double) swing);
                                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-30.0F * swing_rot));
                                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20.0F * swing_rot * sideFactor));
                                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(40.0F * swing));
                            } else if (item.getUseAction() != UseAction.BLOCK) {
                                matrices.translate(0.1 * (double) sideFactor * (double) swing_rot, 0.1 * (double) swing_rot, -0.1 * (double) swing);
                                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-30.0F * swing_rot));
                                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-10.0F * swing_rot * sideFactor));
                                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(40.0F * swing));
                                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(10.0F * swing * sideFactor));
                            } else {
                                matrices.translate(0.1 * (double) sideFactor * (double) swing_rot, 0.1 * (double) swing_rot, -0.2 * (double) swing);
                                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-10.0F * swing_rot));
                                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-10.0F * swing_rot * sideFactor));
                                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(20.0F * swing));
                            }
                        } else {
                            matrices.translate(0.8 * (double) sideFactor * (double) swing_rot, 0.3 * (double) swing_rot, (double) -0.5F * (double) swing);
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(15.0F * swing_rot * sideFactor));
                            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-20.0F * swing_rot));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-70.0F * swing_rot * sideFactor));
                            if (item.isIn(ItemTags.SWORDS)) {
                                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(40.0F * swing));
                            } else {
                                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(30.0F * swing));
                            }
                        }
                    } else if (!item.isIn(ItemTags.SHOVELS)) {
                        if (sharpSword) {
                            matrices.translate(0.1 * (double) sideFactor * (double) swing_rot, 0.1 * (double) swing_rot, (double) -0.5F * (double) swing);
                            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-30.0F * swing_rot));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20.0F * swing_rot * sideFactor));
                            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(40.0F * swing));
                        } else if (item.isIn(ItemTags.SWORDS)) {
                            matrices.translate(0.1 * (double) sideFactor * (double) swing_rot, 0.1 * (double) swing_rot, (double) -0.5F * (double) swing);
                            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-30.0F * swing_rot));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20.0F * swing_rot * sideFactor));
                            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(40.0F * swing));
                        } else if (item.isIn(ConventionalItemTags.TOOLS) && !item.isIn(ItemTags.SHOVELS)) {
                            matrices.translate(0.1 * (double) sideFactor * (double) swing_rot, 0.1 * (double) swing_rot, (double) -0.5F * (double) swing);
                            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-30.0F * swing_rot));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20.0F * swing_rot * sideFactor));
                            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(40.0F * swing));
                        } else {
                            matrices.translate(0.1 * (double) sideFactor * (double) swing_rot, 0.1 * (double) swing_rot, -0.1 * (double) swing);
                            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-30.0F * swing_rot));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-10.0F * swing_rot * sideFactor));
                            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(40.0F * swing));
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(10.0F * swing * sideFactor));
                        }
                    } else if (item.isIn(ItemTags.SHOVELS)) {
                        matrices.translate((double) 0.0F, 0.15 * (double) swing_rot, (double) -0.25F * (double) swing_rot);
                        matrices.translate((double) 0.0F, (double) 0.0F, -0.2 * (double) swing);
                        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(15.0F * swing_rot));
                        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-35.0F * swing_rot));
                        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F * swing));
                    }
                } else if (Block.getBlockFromItem(item.getItem()) != Blocks.AIR && (!item.isIn(ConventionalItemTags.TOOLS) || item.isIn(ItemTags.TRIMMABLE_ARMOR) || item.isIn(ItemTags.BOOKSHELF_BOOKS) || item.getUseAction() == UseAction.EAT || !item.isEnchantable()) && item.getUseAction() != UseAction.BOW && item.getUseAction() != UseAction.SPYGLASS && this.hmiGetAttackDamage(item) == 0.0F && item.getUseAction() != UseAction.BLOCK && !item.isOf(Items.WARPED_FUNGUS_ON_A_STICK) && !item.isOf(Items.CARROT_ON_A_STICK) && !item.isOf(Items.FISHING_ROD) && !item.isOf(Items.SHEARS)) {
                    swingProgress = (float) ((double) swingProgress * 1.2);
                    if (swingProgress > 1.0F) {
                        swingProgress = 0.0F;
                    }
                } else if (!item.isIn(ItemTags.SHOVELS)) {
                    swingProgress = (float) ((double) swingProgress * (double) 1.5F);
                    if (swingProgress > 1.0F) {
                        swingProgress = 0.0F;
                    }
                }

                if (player.getVelocity().length() >= 0.08) {
                    this.hmi$crawlCount = (float) ((double) this.hmi$crawlCount + 0.1 * player.getVelocity().length() * (double) 2.0F * tt);
                    this.hmi$directionalCrawlCount = (float) ((double) this.hmi$directionalCrawlCount + 0.1 * dotProduct * (double) 4.0F * tt);
                    this.hmi$directionalCrawlCount = (float) ((double) this.hmi$directionalCrawlCount + (dotProduct > (double) 0.0F ? 0.1 * Math.abs(crossProduct) * (double) 4.0F * tt : 0.1 * Math.abs(crossProduct) * (double) -1.0F * (double) 4.0F * tt));
                }

                if (player.getVelocity().y > (double) 0.0F) {
                    this.hmi$climbCount = (float) ((double) this.hmi$climbCount + 0.1 * tt);
                }

                if (player.getVelocity().y < (double) 0.0F) {
                    this.hmi$climbCount = (float) ((double) this.hmi$climbCount - 0.1 * tt);
                }

                if ((hmi$isCrawling(player) && config.climbAndCrawl.getValue() || player.isClimbing() && !player.isOnGround() && Math.abs(player.getVelocity().y) > (double) 0.0F && config.climbAndCrawl.getValue()) && !player.isUsingItem() && swingProgress == 0.0F) {
                    this.hmi$clCount = (float) ((double) this.hmi$clCount + 0.1 * tt);
                    if (this.hmi$clCount > 1.0F) {
                        this.hmi$clCount = 1.0F;
                    }

                    if (!item.isOf(Items.LANTERN) && !item.isOf(Items.SOUL_LANTERN)) {
                        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-20.0F * this.hmi$clCount));
                    }
                } else {
                    this.hmi$clCount = (float) ((double) this.hmi$clCount * Math.pow((double) 0.88F, tt));
                }

                if (swingProgress == 0.0F) {
                    matrices.translate(bl ? player.getPitch() / 650.0F * this.hmi$clCount * -1.0F : player.getPitch() / 650.0F * this.hmi$clCount, 0.0F, 0.0F);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(player.getPitch() * this.hmi$clCount));
                }

                if (!item.isOf(Items.LANTERN) && !item.isOf(Items.SOUL_LANTERN)) {
                    matrices.translate(0.0F, 0.0F, player.getPitch() / 120.0F * this.hmi$clCount);
                } else if (swingProgress == 0.0F) {
                    matrices.translate(0.0F, 0.0F, player.getPitch() / 80.0F * this.hmi$clCount);
                }

                if (player.isClimbing() && config.climbAndCrawl.getValue() && !player.isOnGround() && !item.isOf(Items.LANTERN) && !item.isOf(Items.SOUL_LANTERN) && !player.isUsingItem()) {
                    matrices.translate((double) 0.0F, 0.1, -0.2);
                }

                if ((player.isTouchingWater()) && !player.isInSwimmingPose() && !player.isSubmergedIn(FluidTags.WATER)) {
                    this.hmi$inWaterCounter = (float) ((double) this.hmi$inWaterCounter + 0.1 * tt);
                    if (this.hmi$inWaterCounter >= 1.0F) {
                        this.hmi$inWaterCounter = 1.0F;
                    }
                } else {
                    this.hmi$inWaterCounter = (float) ((double) this.hmi$inWaterCounter * Math.pow((double) 0.88F, tt));
                }

                if (false && (double) player.getFreezingScale() > 0.1) {
                    this.hmi$freezeCounter = (float) ((double) this.hmi$freezeCounter + 0.1 * tt);
                } else {
                    this.hmi$freezeCounter = (float) ((double) this.hmi$freezeCounter * Math.pow((double) 0.88F, tt));
                }

                matrices.translate((double) 0.0F, 0.02 * (double) this.hmi$inWaterCounter, (double) 0.0F);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(8.0F * sideFactor * this.hmi$inWaterCounter));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(0.3F * MathHelper.sin(this.hmi$freezeCounter * 5.0F)));
                if (player.getVelocity().y < -0.85 && item.isOf(Items.MACE) && player.getMainHandStack() == item) {
                    this.hmi$fallCounter = (float) ((double) this.hmi$fallCounter + 0.1 * tt);
                    if (this.hmi$fallCounter >= 1.0F) {
                        this.hmi$fallCounter = 1.0F;
                    }
                } else {
                    this.hmi$fallCounter = (float) ((double) this.hmi$fallCounter * Math.pow((double) 0.88F, tt));
                }

                if (bl) {
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(45.0F * this.hmi$fallCounter));
                    matrices.translate((double) 0.0F, -0.2 * (double) this.hmi$fallCounter, (double) 0.0F);
                }

                this.hmi$vertAngleY = (float) ((double) this.hmi$vertAngleY + player.getVelocity().y * (double) 0.015F * tt);
                this.hmi$vertAngleY = (float) ((double) this.hmi$vertAngleY - (double) (0.1F * this.hmi$vertAngleY) * tt);
                this.hmi$vertAngleY = (float) ((double) this.hmi$vertAngleY * Math.pow((double) 0.88F, tt));
                this.hmi$vertVelocityYSlime = (float) ((double) this.hmi$vertVelocityYSlime + player.getVelocity().y * (double) 0.015F * tt);
                this.hmi$vertVelocityYSlime = (float) ((double) this.hmi$vertVelocityYSlime - (double) (0.1F * this.hmi$vertAngleYSlime) * tt);
                this.hmi$vertVelocityYSlime = (float) ((double) this.hmi$vertVelocityYSlime * Math.pow((double) 0.88F, tt));
                this.hmi$vertAngleYSlime = (float) ((double) this.hmi$vertAngleYSlime + (double) this.hmi$vertVelocityYSlime * tt);
                matrices.translate(0.0F, this.hmi$vertAngleY * -1.0F, 0.0F);
                matrices.translate((double) 0.0F, Math.sin((double) player.age * 0.1) * 0.007 * (double) sideFactor, (double) 0.0F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(0.15F * MathHelper.sin((float) player.age * 0.15F) * sideFactor));
                if (!item.isEmpty() || hmi$isCrawling(player) || player.isClimbing() && !player.isOnGround() || player.isInSwimmingPose()) {
                    if (player.getMainArm() == Arm.LEFT) {
                        bl = !bl;
                    }

                    if (item.getUseAction() == UseAction.BLOCK) {
                        matrices.translate(0.0F, 0.0F, 0.0F);
                    } else {
                        matrices.translate((double) 0.0F, -0.1, 0.1);
                    }
                }

                if (item.isOf(Items.LANTERN) || item.isOf(Items.SOUL_LANTERN) || item.isIn(ItemTags.HANGING_SIGNS)) {
                    matrices.translate((double) 0.0F, 0.1, (double) 0.0F);
                    if (player.isInSwimmingPose()) {
                        matrices.translate((double) 0.0F, -0.1, 0.1);
                    }
                }

                if (player.isInSwimmingPose() && swingProgress == 0.0F && config.swimmingAnimation.getValue()) {
                    double distance = (double) this.hmi$crawlCount;
                    double swingAmplitude = (double) 1.5F;
                    double frequency = (double) 2.0F;
                    double s = distance * frequency;
                    double handRotation = Math.sin(s) * swingAmplitude;
                    double smoothRotation = handRotation * 0.8 + this.hmi$previousRotation * 0.2;
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (bl ? smoothRotation : -smoothRotation)));
                    matrices.translate((double) 0.0F, (double) 0.0F, smoothRotation * (double) 0.2F);
                    double k = (double) (this.hmi$crawlCount * 2.0F);
                    double a = Math.cos(k);
                    double b = a;
                    if (a <= (double) 0.0F) {
                        b = a * (double) 0.5F;
                    }

                    matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) (bl ? b * (double) 30.0F : b * (double) 30.0F * (double) -1.0F)));
                    matrices.translate((double) 0.0F, (double) 0.0F, a * (double) 0.2F);
                    if (item.isEmpty() && !bl && !player.isInvisible()) {
                        matrices.translate((double) (1.0F * sideFactor), (double) 0.0F - (double) equipProgress * 0.3, 0.3);
                        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * sideFactor));
                        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-40.0F * sideFactor));
                        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F));
                        this.hmiAltSwing(matrices, arm, swingProgress, item);
                        float c = MathHelper.sin(equipProgress * 3.14F);
                        matrices.scale(0.9F, 0.9F, 0.9F);
                        renderArmHoldingItem(matrices, queue, light, 0.0F, 0.0F, arm);
                    }

                    this.hmi$previousRotation = smoothRotation;
                }

                if ((player.isClimbing() && !player.isOnGround() || hmi$isCrawling(player) && swingProgress == 0.0F) && !player.isUsingItem()) {
                    double s = (double) this.hmi$climbCount;
                    float v = (float) player.getVelocity().y;
                    float a = MathHelper.cos((float) s * 2.0F);
                    if (player.isClimbing()) {
                        if (!item.isOf(Items.LANTERN) && !item.isOf(Items.SOUL_LANTERN)) {
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(20.0F * a * sideFactor));
                        } else {
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(1.0F * a * sideFactor));
                        }
                    }

                    if (hmi$isCrawling(player) && !player.isUsingItem() && swingProgress == 0.0F) {
                        float crawlProgress = MathHelper.sin(this.hmi$directionalCrawlCount * 4.0F * this.hmi$mouseHolding);
                        float upAndDown = MathHelper.cos(this.hmi$directionalCrawlCount * 4.0F * this.hmi$mouseHolding);
                        if (item.isOf(Items.LANTERN) || item.isOf(Items.SOUL_LANTERN)) {
                            crawlProgress *= 0.14F;
                            upAndDown *= 0.14F;
                        }

                        matrices.translate(0.2 * (double) crawlProgress, 0.3 * (double) crawlProgress * (double) sideFactor, -0.2 * (double) crawlProgress * (double) sideFactor * (double) al);
                        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(25.0F * crawlProgress));
                        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.clamp(20.0F * upAndDown * sideFactor, 0.0F, 20.0F)));
                    }

                    if (item.isEmpty() && !bl && !player.isInvisible() && (!player.isOnGround() && player.isClimbing() || hmi$isCrawling(player))) {
                        matrices.translate((double) (1.0F * sideFactor), (double) 0.0F - (double) equipProgress * 0.3, 0.3);
                        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * sideFactor));
                        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-40.0F * sideFactor));
                        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F));
                        this.hmiAltSwing(matrices, arm, swingProgress, item);
                        matrices.scale(0.9F, 0.9F, 0.9F);
                        renderArmHoldingItem(matrices, queue, light, 0.0F, 0.0F, arm);
                    }
                }

                if (item.isEmpty()) {
                    if (bl && !player.isInvisible()) {
                        if ((player.isOnGround() || !player.isClimbing()) && !player.isInSwimmingPose() && !hmi$isCrawling(player)) {
                            if (player.getMainArm() == Arm.LEFT) {
                                bl = !bl;
                            }

                            matrices.translate((double) 0.0F, 0.2 * (double) swing_rot, 0.15 * (double) swing_rot);
                            matrices.translate(0.1 * (double) sideFactor * (double) swing, 0.15 * (double) swing, -0.45 * (double) swing);
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35.0F * swing * sideFactor));
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30.0F * swing));
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-10.0F * swing_rot * sideFactor));
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(10.0F * swing_rot));
                            renderArmHoldingItem(matrices, queue, light, 0.0F, 0.0F, arm);
                        } else {
                            matrices.translate((double) (1.0F * sideFactor), (double) 0.0F - (double) equipProgress * 0.3, 0.3);
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * sideFactor));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-40.0F * sideFactor));
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F));
                            this.hmiAltSwing(matrices, arm, swingProgress, item);
                            float c = MathHelper.sin(equipProgress * 3.14F);
                            matrices.scale(0.9F, 0.9F, 0.9F);
                            renderArmHoldingItem(matrices, queue, light, 0.0F, 0.0F, arm);
                        }
                    } else if (!bl && !player.isInvisible()) {
                        matrices.translate((double) (1.0F * sideFactor), (double) 0.0F - (double) equipProgress * 0.3, 0.3);
                        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * sideFactor));
                        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-40.0F * sideFactor));
                        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F));
                        this.hmiAltSwing(matrices, arm, swingProgress, item);
                        float c = MathHelper.sin(equipProgress * 3.14F);
                        matrices.scale(0.9F, 0.9F, 0.9F);
                        renderArmHoldingItem(matrices, queue, light, 0.0F, 0.0F, arm);
                    }
                } else if (item.contains(DataComponentTypes.MAP_ID)) {
                    if (bl && this.mainHand.isEmpty()) {
                        matrices.translate((double) 0.0F, 0.1, (double) 0.0F);
                        renderMapInBothHands(matrices, queue, light, pitch, equipProgress, swingProgress);
                    } else {
                        matrices.translate(bl ? -0.1 : 0.1, 0.1, (double) 0.0F);
                        renderMapInOneHand(matrices, queue, light, equipProgress, arm, swingProgress, item);
                    }
                } else if (item.getUseAction() == UseAction.CROSSBOW) {
                    matrices.push();
                    boolean bl2 = CrossbowItem.isCharged(item);
                    boolean bl3 = arm == Arm.RIGHT;
                    int i = bl3 ? 1 : -1;
                    if (player.isUsingItem() && player.getItemUseTimeLeft() > 0 && player.getActiveHand() == hand) {
                        applyEquipOffset(matrices, arm, equipProgress);
                        matrices.translate((float) i * -0.4785682F, -0.24387F, 0.05731531F);
                        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-11.935F));
                        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * 65.3F));
                        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * 9.785F));
                        float f = (float) item.getMaxUseTime(player) - ((float) player.getItemUseTimeLeft() - tickDelta + 1.0F);
                        float g = f / (float) CrossbowItem.getPullTime(item, player);
                        if (g > 1.0F) {
                            g = 1.0F;
                        }

                        if (g > 0.1F) {
                            float h = MathHelper.sin((f - 0.1F) * 1.3F);
                            float j = g - 0.1F;
                            float k = h * j;
                            matrices.translate(k * 0.0F, k * 0.004F, k * 0.0F);
                        }

                        matrices.translate(g * 0.0F, g * 0.0F, g * 0.04F);
                        matrices.scale(1.0F, 1.0F, 1.0F);
                        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) i * 45.0F));
                    } else {
                        applySwingOffset(matrices, arm, swingProgress);
                        if (bl2 && swingProgress < 0.001F && bl) {
                            matrices.translate((float) i * -0.341864F, 0.0F, 0.0F);
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * 10.0F));
                        }
                    }

                    matrices.translate(0.0F, 0.0F, -1.0F);
                    matrices.translate(-0.45 * (double) i, 0.45, 1.7);
                    matrices.translate((double) (1.0F * sideFactor), (double) 0.0F - (double) equipProgress * 0.3, 0.3);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * sideFactor));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-40.0F * sideFactor));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F));
                    this.hmiAltSwing(matrices, arm, swingProgress, item);
                    float c = MathHelper.sin(equipProgress * 3.14F);
                    matrices.scale(0.9F, 0.9F, 0.9F);
                    renderArmHoldingItem(matrices, queue, light, 0.0F, 0.0F, arm);
                    matrices.translate((double) -0.25F * (double) i, (double) 1.25F, 0.05);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (-90 * i)));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(77.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (85 * i)));
                    matrices.scale(1.2F, 1.2F, 1.2F);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10.0F));
                    matrices.translate((double) 0.0F, -0.15, 0.15);
                    renderItem(player, item, bl3 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, matrices, queue, light);
                    matrices.pop();
                    if (player.isUsingItem() && player.getItemUseTimeLeft() > 0 && player.getActiveHand() == hand) {
                        float f = (float) item.getMaxUseTime(player) - ((float) player.getItemUseTimeLeft() - tickDelta + 1.0F);
                        float g = f / (float) CrossbowItem.getPullTime(item, player);
                        if (g > 1.0F) {
                            g = 1.0F;
                        }

                        if (g > 0.1F) {
                            float h = MathHelper.sin((f - 0.1F) * 1.3F);
                            float j = g - 0.1F;
                            float k = h * j;
                            matrices.translate(k * 0.0F, k * 0.004F, k * 0.0F);
                        }

                        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((double) g <= 0.2 ? 75.0F * g * 5.0F * (float) i : (float) (75 * i)));
                        matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(10.0F * g * 1.5F));
                        matrices.translate(-0.37 * (double) i, (double) 0.0F, 0.6);
                        matrices.translate(0.15 * (double) g * (double) i, (double) 0.0F, (double) 0.0F);
                        renderArmHoldingItem(matrices, queue, light, equipProgress, swingProgress, arm.getOpposite());
                    }
                } else {
                    boolean bl2 = arm == Arm.RIGHT;
                    int l = bl2 ? 1 : -1;
                    if (player.isUsingItem() && player.getItemUseTimeLeft() > 0 && player.getActiveHand() == hand) {
                        switch (item.getUseAction()) {
                            case NONE -> {
                                applyEquipOffset(matrices, arm, equipProgress);
                            }
                            case EAT, DRINK -> {
                                float u = (float) item.getMaxUseTime(player) - ((float) player.getItemUseTimeLeft() - tickDelta + 1.0F);
                                float y = u / 5.0F;
                                if (y > 1.0F) {
                                    y = 1.0F;
                                }

                                float q = MathHelper.sin(u / 2.0F * 3.14F);
                                q /= 10.0F;
                                matrices.translate((double) (1 * l), 0.1, 0.3);
                                matrices.translate(0.2 * (double) l * (double) y, -0.7 * (double) y, -0.2 * (double) y);
                                matrices.translate((double) 0.0F, -0.2 * (double) q, -0.2 * (double) q);
                                matrices.translate((double) 0.0F, 0.1 * (double) hmiEaseInOutBack(MathHelper.sin(y * 3.14F)), (double) 0.0F);
                                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (45 * l)));
                                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (-40 * l)));
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F));
                                this.hmiAltSwing(matrices, arm, swingProgress, item);
                                float c = MathHelper.sin(equipProgress * 3.14F);
                                matrices.scale(0.9F, 0.9F, 0.9F);
                                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * y * (float) l));
                                renderArmHoldingItem(matrices, queue, light, 0.0F, swingProgress, arm);
                            }
                            case BLOCK -> {
                                float k = (float) item.getMaxUseTime(player) - ((float) player.getItemUseTimeLeft() - tickDelta + 1.0F);
                                float s = k / 4.0F;
                                float s2 = k / 6.0F;
                                if (s > 1.0F) {
                                    s = 1.0F;
                                }

                                if (s2 > 1.0F) {
                                    s2 = 1.0F;
                                }

                                matrices.translate((double) 0.0F, -0.2, (double) 0.0F);
                                matrices.translate((double) (1 * l), (double) 0.0F, 0.3);
                                matrices.translate(0.7 * (double) s * (double) l, (double) 0.0F, -1.3 * (double) s);
                                matrices.translate(-0.2 * (double) l * (double) s2, (double) 0.0F, (double) 0.0F);
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) ((double) 10.0F * Math.sin((double) s2 * 3.14))));
                                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(70.0F * s * (float) l));
                                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (45 * l)));
                                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (-40 * l)));
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F));
                                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (5 * l) * s));
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10.0F * s));
                                matrices.translate((double) 0.0F, (double) 0.0F, -0.2 * (double) s);
                                this.hmiAltSwing(matrices, arm, swingProgress, item);
                                matrices.scale(0.9F, 0.9F, 0.9F);
                                renderArmHoldingItem(matrices, queue, light, 0.0F, swingProgress, arm);
                                matrices.translate(0.35 * (double) l, -0.13, -0.12);
                                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(10.0F * (float) l));
                                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(10.0F * (float) l));
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(0.0F));
                                matrices.translate(-0.2 * (double) l, -0.04, 0.15);
                                matrices.scale(1.0F, 1.0F, 1.0F);
                            }
                            case BOW -> {
                                matrices.push();
                                if (player.getMainArm() == Arm.LEFT) {
                                    bl = !bl;
                                }

                                float m1 = (float) item.getMaxUseTime(player) - ((float) player.getItemUseTimeLeft() - tickDelta + 1.0F);
                                float f1 = m1 / 20.0F;
                                float f = (f1 * f1 + f1 * 2.0F) / 3.0F;
                                if (f1 > 1.0F) {
                                    f1 = 1.0F;
                                }

                                if (f1 > 0.1F) {
                                    float g1 = MathHelper.sin((m1 - 0.1F) * 1.3F);
                                    float j1 = g1 * f1;
                                    matrices.translate(j1 * 0.0F, j1 * 0.004F, j1 * 0.0F);
                                }

                                matrices.translate(bl ? -0.1 : 0.1, (double) 0.0F, (double) f1 * 0.15);
                                renderArmHoldingItem(matrices, queue, light, equipProgress, swingProgress, arm);
                                matrices.pop();
                                matrices.translate(bl ? (double) -0.5F : (double) 0.5F, -0.45, 0.1);
                                matrices.multiply(RotationAxis.POSITIVE_X.rotation(0.3F));
                                if (bl) {
                                    matrices.multiply(RotationAxis.NEGATIVE_Z.rotation(-0.3F));
                                    matrices.multiply(RotationAxis.NEGATIVE_Y.rotation(1.0F));
                                } else {
                                    matrices.multiply(RotationAxis.POSITIVE_Z.rotation(-0.3F));
                                    matrices.multiply(RotationAxis.POSITIVE_Y.rotation(1.0F));
                                }

                                renderArmHoldingItem(matrices, queue, light, equipProgress, swingProgress, arm.getOpposite());
                                if (bl) {
                                    matrices.multiply(RotationAxis.NEGATIVE_Y.rotation(2.5F));
                                } else {
                                    matrices.multiply(RotationAxis.POSITIVE_Y.rotation(2.5F));
                                }

                                matrices.translate(bl ? -0.65 : 0.65, -0.35, 0.27);
                                if (f1 > 1.0F) {
                                    f1 = 1.0F;
                                }

                                matrices.pop();
                                if (config.mb3DCompat.getValue()) {
                                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (10 * l)));
                                }

                                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(75.0F));
                                matrices.multiply(RotationAxis.NEGATIVE_Z.rotationDegrees((float) (-15 * l)));
                                matrices.translate(0.8 * (double) l, (double) (0.0F - equipProgress * 0.3F), -0.1);
                                if (f > 0.1F) {
                                    float g1 = MathHelper.sin((m1 - 0.1F) * 1.3F);
                                    float h1 = f1 - 0.1F;
                                    float j1 = g1 * h1;
                                    matrices.translate(j1 * 0.0F, j1 * 0.004F, j1 * 0.0F);
                                }

                                matrices.push();
                            }
                            case TRIDENT -> {
                                if (player.getOffHandStack().isEmpty() && !hmi$isCrawling(player) && !player.isInSwimmingPose() && !player.isClimbing()) {
                                    matrices.push();
                                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (-25 * l)));
                                    matrices.translate(-0.15 * (double) l, 0.1, 0.1);
                                    renderArmHoldingItem(matrices, queue, light, equipProgress, swingProgress, arm.getOpposite());
                                    matrices.pop();
                                }

                                float m = (float) item.getMaxUseTime(player) - ((float) player.getItemUseTimeLeft() - tickDelta + 1.0F);
                                float f = m / 10.0F;
                                if (f > 1.0F) {
                                    f = 1.0F;
                                }

                                if (f > 0.1F) {
                                    float g = MathHelper.sin((m - 0.1F) * 1.3F);
                                    float h = f - 0.1F;
                                    float j = g * h;
                                    matrices.translate(j * 0.0F, j * 0.004F, j * 0.0F);
                                }

                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(45.0F));
                                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (25 * l)));
                                matrices.translate(0.2 * (double) l, (double) 0.0F, 0.8);
                                renderArmHoldingItem(matrices, queue, light, equipProgress, swingProgress, arm);
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(135.0F));
                                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (-65 * l)));
                                matrices.translate((double) (0.65F * (float) l), (double) -1.0F, -0.6);
                            }
                            case BRUSH -> {
                                float f5 = (float) (player.getItemUseTimeLeft() % 10);
                                float g5 = f5 - tickDelta + 1.0F;
                                float h5 = 1.0F - g5 / 10.0F;
                                float n = -15.0F + 75.0F * MathHelper.cos(h5 * 2.0F * (float) Math.PI);
                                float z = (float) item.getMaxUseTime(player) - ((float) player.getItemUseTimeLeft() - tickDelta + 1.0F);
                                float x = z / 4.0F;
                                if (x > 1.0F) {
                                    x = 1.0F;
                                }

                                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (25 * l) * x));
                                matrices.translate((double) (0.3F * (float) l * x), 0.3 * (double) x, 0.1 * (double) x);
                                if (x == 1.0F) {
                                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(n / 20.0F));
                                }

                                renderArmHoldingItem(matrices, queue, light, equipProgress, swingProgress, arm);
                            }
                            case BUNDLE -> {
                                matrices.translate((double) (1 * l), (double) 0.0F - (double) equipProgress * 0.3, 0.3);
                                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (45 * l)));
                                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (-40 * l)));
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F));
                                this.hmiAltSwing(matrices, arm, swingProgress, item);
                                matrices.scale(0.9F, 0.9F, 0.9F);
                                renderArmHoldingItem(matrices, queue, light, 0.0F, 0.0F, arm);
                            }
                            default -> {
                            }
                        }
                    } else if (player.isUsingRiptide() && item.getUseAction() == UseAction.TRIDENT) {
                        this.hmi$riptideCounter = (float) ((double) this.hmi$riptideCounter + 0.15 * tt);
                        float m = (float) item.getMaxUseTime(player) - ((float) player.getItemUseTimeLeft() - tickDelta + 1.0F);
                        float f = m / 10.0F;
                        if (f > 1.0F) {
                            f = 1.0F;
                        }

                        if (f > 0.1F) {
                            float g = MathHelper.sin((m - 0.1F) * 1.3F);
                            float h = f - 0.1F;
                            float j = g * h;
                            matrices.translate(j * 0.0F, j * 0.004F, j * 0.0F);
                        }

                        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(45.0F - this.hmi$riptideCounter * 2.0F));
                        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (25 * l)));
                        matrices.translate(0.2 * (double) l, (double) 0.0F, (double) 0.75F);
                        matrices.translate((double) 0.0F, (double) 0.0F, 0.01 * (double) MathHelper.sin(this.hmi$riptideCounter * 6.28F));
                        renderArmHoldingItem(matrices, queue, light, equipProgress, swingProgress, arm);
                        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(135.0F));
                        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (-65 * l)));
                        matrices.translate((double) (0.65F * (float) l), (double) -1.0F, -0.6);
                    } else {
                        this.hmi$riptideCounter = 0.0F;
                        if (!item.isOf(Items.LANTERN) && !item.isOf(Items.SOUL_LANTERN) && !item.isIn(ItemTags.HANGING_SIGNS)) {
                            if (item.getUseAction() == UseAction.BLOCK) {
                                matrices.translate((double) 0.0F, -0.2, (double) 0.0F);
                            }
                        } else {
                            matrices.translate(0.1 * (double) l, (double) 0.0F, -0.1);
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(10.0F));
                        }

                        matrices.translate((double) (1 * l), (double) 0.0F - (double) equipProgress * 0.3, 0.3);
                        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (45 * l)));
                        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (-40 * l)));
                        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F));
                        this.hmiAltSwing(matrices, arm, swingProgress, item);
                        matrices.scale(0.9F, 0.9F, 0.9F);
                        renderArmHoldingItem(matrices, queue, light, 0.0F, 0.0F, arm);
                    }

                    matrices.translate(-0.3 * (double) l, 0.65, -0.1);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (-65 * l)));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(10.0F));
                    if (item.isIn(ItemTags.WOOL_CARPETS)) {
                        matrices.translate(0.2 * (double) l, -0.1, (double) 0.0F);
                    }

                    if (Block.getBlockFromItem(item.getItem()) != Blocks.AIR && item.getUseAction() != UseAction.EAT && !item.isIn(ConventionalItemTags.BUCKETS)) {
                        if (item.getName().getString().toLowerCase().contains("torch")) {
                            matrices.scale(1.5F, 1.5F, 1.5F);
                            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) (25 * l)));
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(5.0F));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (75 * l)));
                            matrices.translate(0.2 * (double) l, 0.2, 0.05);
                        } else if ((item.isOf(Items.STRING) || item.isOf(Items.REDSTONE) || item.isOf(Items.LEVER) || item.isOf(Items.TRIPWIRE_HOOK) || Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(ConventionalBlockTags.GLASS_PANES) || Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.RAILS) || Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.CLIMBABLE) || item.isIn(ItemTags.DOORS)) && !Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.LEAVES) && !Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.COMBINATION_STEP_SOUND_BLOCKS) && !Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.BANNERS)) {
                            matrices.translate((double) 0.0F, (double) 0.0F, -0.1);
                            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) (5 * l)));
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(15.0F));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (75 * l)));
                        } else if (!item.isOf(Items.LANTERN) && !item.isOf(Items.SOUL_LANTERN) && !item.isIn(ItemTags.HANGING_SIGNS)) {
                            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) (25 * l)));
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(5.0F));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (75 * l)));
                            matrices.translate(0.2 * (double) l, 0.2, 0.05);
                            if (Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.BANNERS)) {
                                matrices.translate(-0.2 * (double) l, (double) 0.0F, (double) 0.0F);
                                matrices.scale(1.1F, 1.1F, 1.1F);
                            }
                        } else {
                            float yawDelta = player.getYaw() - player.headYaw;
                            float pitchDelta = player.getPitch() - player.getPitch();
                            this.hmi$swingVelocityY += yawDelta * 0.015F * (float) tt;
                            this.hmi$swingVelocityY += swingProgress * 2.0F * (float) tt;
                            this.hmi$swingVelocityX += pitchDelta * 0.015F * (float) tt;
                            this.hmi$swingVelocityY -= 0.1F * this.hmi$swingAngleY * (float) tt;
                            this.hmi$swingVelocityX -= 0.1F * this.hmi$swingAngleX * (float) tt;
                            this.hmi$swingVelocityY = (float) ((double) this.hmi$swingVelocityY * Math.pow((double) 0.88F, tt));
                            this.hmi$swingVelocityX = (float) ((double) this.hmi$swingVelocityX * Math.pow((double) 0.88F, tt));
                            this.hmi$swingAngleY += this.hmi$swingVelocityY * (float) tt;
                            this.hmi$swingAngleX += this.hmi$swingVelocityX * (float) tt;
                            double currentSpeed = player.getVelocity().length();
                            this.hmi$swingVelocityZ = (float) ((double) this.hmi$swingVelocityZ + (bl ? (currentSpeed * (double) -1.0F * (double) 15.0F - (double) this.hmi$swingVelocityZ) * (double) 0.1F * tt : (currentSpeed * (double) 15.0F - (double) this.hmi$swingVelocityZ) * (double) 0.1F * tt));
                            if ((currentSpeed > 0.09 && player.isOnGround() || player.isInSwimmingPose() || player.isClimbing() && !player.isOnGround()) && this.client.options.getBobView().getValue()) {
                                Random random = new Random();
                                boolean randomBoolean = random.nextBoolean();
                                this.hmi$swingVelocityY += (float) (randomBoolean ? (double) -5.5F * currentSpeed * tt : (double) 5.5F * currentSpeed * tt);
                            }

                            matrices.translate((double) 0.0F, (double) 0.0F, -0.1);
                            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) (35 * l) + this.hmi$swingAngleY));
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(15.0F + this.hmi$swingAngleX));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (75 * l) + this.hmi$swingVelocityZ));
                            if (item.isIn(ItemTags.HANGING_SIGNS)) {
                                matrices.translate((double) 0.0F, -0.1, (double) 0.0F);
                                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (-45 * l)));
                            }

                            matrices.translate(0.3 * (double) l, -0.35, (double) 0.0F);
                            matrices.translate((double) 0.0F, (double) 0.0F, 0.1);
                            matrices.scale(1.5F, 1.5F, 1.5F);
                        }
                    } else {
                        if ((!item.isIn(ConventionalItemTags.TOOLS) || item.isIn(ItemTags.TRIMMABLE_ARMOR) || item.isIn(ItemTags.BOOKSHELF_BOOKS) || item.getUseAction() == UseAction.EAT || !item.isEnchantable()) && item.getUseAction() != UseAction.BOW && item.getUseAction() != UseAction.SPYGLASS && this.hmiGetAttackDamage(item) == 0.0F && item.getUseAction() != UseAction.BLOCK && !item.isOf(Items.WARPED_FUNGUS_ON_A_STICK) && !item.isOf(Items.CARROT_ON_A_STICK) && !item.isOf(Items.FISHING_ROD) && !item.isOf(Items.SHEARS) && !item.isIn(ItemTags.HOES) && !config.mb3DCompat.getValue()) {
                            if (item.getUseAction() == UseAction.BRUSH) {
                                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(25.0F));
                                matrices.translate(bl ? (double) 0.0F : 0.35, bl ? (double) 0.0F : (double) 0.25F, bl ? (double) 0.0F : 0.37);
                                if (!bl) {
                                    matrices.scale(0.75F, 0.75F, 0.75F);
                                }

                                matrices.multiply(RotationAxis.NEGATIVE_Z.rotationDegrees((float) (-75 * l)));
                                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(35.0F));
                                matrices.translate(bl ? -0.05 : 0.85, bl ? (double) 0.0F : 0.05, bl ? 0.08 : -0.2);
                            } else {
                                matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) (5 * l)));
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(15.0F));
                                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (75 * l)));
                                matrices.translate((double) 0.0F, -0.05, -0.1);
                                matrices.scale(0.7F, 0.7F, 0.7F);
                            }

                            if (item.isOf(Items.FEATHER) || item.isOf(Items.SLIME_BALL) || item.isOf(Items.PUFFERFISH)) {
                                this.hmi$vertVelocityYSlime = (float) ((double) this.hmi$vertVelocityYSlime + (double) swingProgress * 0.03 * (float) tt);
                                if ((player.getVelocity().length() > 0.09 && player.isOnGround() || player.isInSwimmingPose() || hmi$isCrawling(player) || player.isClimbing() && !player.isOnGround()) && this.client.options.getBobView().getValue()) {
                                    Random random = new Random();
                                    boolean randomBoolean = random.nextBoolean();
                                    this.hmi$vertVelocityYSlime += (float) (-0.05 * player.getVelocity().length() * (float) tt);
                                }

                                matrices.scale(1.0F, 1.0F + this.hmi$vertAngleYSlime * -2.0F, 1.0F);
                            }
                        } else if (item.getUseAction() == UseAction.BLOCK && item.getUseAction() != UseAction.TRIDENT) {
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (160 * l)));
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (-60 * l)));
                            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-70.0F));
                            matrices.scale(0.75F, 0.75F, 0.75F);
                            matrices.translate(0.15 * (double) l, bl ? 0.35 : 0.45, bl ? -0.15 : -0.1);
                            matrices.translate(0.17 * (double) l, (double) 0.0F, 0.3);
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (-90 * l)));
                        } else if (item.getUseAction() == UseAction.TRIDENT) {
                            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) (75 * l)));
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (45 * l)));
                            matrices.translate(-0.3F * (float) l, 0.0F, 0.0F);
                        } else {
                            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float) (75 * l)));
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(70.0F));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (45 * l)));
                        }

                        if (item.getUseAction() != UseAction.BLOCK) {
                            matrices.scale(1.2F, 1.2F, 1.2F);
                        }

                        if (item.getUseAction() == UseAction.BOW && !player.isUsingItem()) {
                            matrices.translate(-0.1 * (double) l, -0.2, (double) 0.0F);
                        }

                        if (item.isOf(Items.MACE)) {
                            if (config.mb3DCompat.getValue()) {
                                matrices.translate(-0.08, 0.17, (double) 0.0F);
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(40.0F));
                            }

                            matrices.translate(0.1 * (double) l, (double) 0.0F, (double) 0.0F);
                            matrices.scale(0.9F, 0.9F, 0.9F);
                        }
                    }

                    if (item.getItem() instanceof BlockItem && (!item.isIn(ConventionalItemTags.BUCKETS) && item.getUseAction() != UseAction.EAT && !item.isIn(ItemTags.BANNERS) && !item.isOf(Items.STRING) && !item.isOf(Items.REDSTONE) && !item.isOf(Items.LEVER) && !item.isOf(Items.TRIPWIRE_HOOK) && !Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(ConventionalBlockTags.GLASS_PANES) && !Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.RAILS) && !Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.CLIMBABLE) && !item.isIn(ItemTags.DOORS) || Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.LEAVES)) && !Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.COMBINATION_STEP_SOUND_BLOCKS)) {
                        BlockItem blockItem = (BlockItem) item.getItem();
                        BlockRenderManager blockRenderDispatcher = MinecraftClient.getInstance().getBlockRenderManager();
                        matrices.push();
                        if (!bl2) {
                            matrices.translate(-0.4F, 0.0F, 0.0F);
                        }

                        matrices.scale(0.4F, 0.4F, 0.4F);
                        matrices.translate(-0.9 * (double) l, -0.45, (double) -0.5F);
                        if (Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.BUTTONS)) {
                            matrices.translate(0.2 * (double) l, -0.15, -0.2);
                        }

                        if (Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.PRESSURE_PLATES)) {
                            matrices.translate((double) 0.0F, 0.1, (double) 0.0F);
                        }

                        if (item.isOf(Items.SLIME_BLOCK) || item.isOf(Items.HONEY_BLOCK) || Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.FLOWERS) || Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.LEAVES) || Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.SAPLINGS) || Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.SWORD_EFFICIENT)) {
                            this.hmi$vertVelocityYSlime = (float) ((double) this.hmi$vertVelocityYSlime + (double) swingProgress * 0.03 * (float) tt);
                            if ((player.getVelocity().length() > 0.09 && player.isOnGround() || player.isInSwimmingPose() || hmi$isCrawling(player) || player.isClimbing() && !player.isOnGround()) && this.client.options.getBobView().getValue()) {
                                Random random = new Random();
                                boolean randomBoolean = random.nextBoolean();
                                this.hmi$vertVelocityYSlime += (float) (-0.05 * player.getVelocity().length() * (float) tt);
                            }

                            matrices.scale(1.0F, 1.0F + this.hmi$vertAngleYSlime * -2.0F, 1.0F);
                        }

                        BlockState blockState = blockItem.getBlock().getDefaultState();
                        if ((float) player.age - this.hmi$prevAge >= 100.0F) {
                            this.hmi$repPower = !this.hmi$repPower;
                            this.hmi$prevAge = (float) player.age;
                        }

                        if (blockItem.getBlock() == Blocks.REPEATER && this.hmi$repPower) {
                            blockState = blockState.with(AbstractRedstoneGateBlock.POWERED, true);
                        }

                        if (blockItem.getBlock() == Blocks.COMPARATOR && this.hmi$repPower) {
                            blockState = blockState.with(AbstractRedstoneGateBlock.POWERED, true);
                        }

                        if (blockItem.getBlock() == Blocks.REDSTONE_TORCH && player.isSubmergedIn(FluidTags.WATER)) {
                            blockState = blockState.with(RedstoneTorchBlock.LIT, false);
                        }

                        if ((blockItem.getBlock() == Blocks.CAMPFIRE || blockItem.getBlock() == Blocks.SOUL_CAMPFIRE) && player.isSubmergedIn(FluidTags.WATER)) {
                            blockState = blockState.with(CampfireBlock.LIT, false);
                        }

                        if (item.isIn(ItemTags.BEDS)) {
                            if (bl) {
                                matrices.translate(0.9, (double) 0.0F, 0.8);
                            }

                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (90 * l)));
                        }

                        blockRenderDispatcher.renderBlockAsEntity(blockState, matrices, MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(), light, OverlayTexture.DEFAULT_UV);

                        Block renderBlock = Block.getBlockFromItem(item.getItem());
                        if (renderBlock instanceof BlockEntityProvider blockEntityProvider) {
                            try {
                                net.minecraft.util.math.BlockPos playerPos = player.getBlockPos();
                                net.minecraft.block.entity.BlockEntity tempBE = blockEntityProvider.createBlockEntity(playerPos, blockState);
                                if (tempBE != null) {
                                    tempBE.setWorld(MinecraftClient.getInstance().world);
                                    tempBE.setCachedState(blockState);
                                    net.minecraft.client.render.block.entity.BlockEntityRenderManager beDispatcher = MinecraftClient.getInstance().getBlockEntityRenderDispatcher();
                                    beDispatcher.configure(MinecraftClient.getInstance().gameRenderer.getCamera());
                                    net.minecraft.client.render.block.entity.state.BlockEntityRenderState beState = beDispatcher.getRenderState(tempBE, MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false), null);
                                    if (beState != null) {
                                        net.minecraft.client.render.state.CameraRenderState cameraState = new net.minecraft.client.render.state.CameraRenderState();
                                        net.minecraft.client.render.Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
                                        cameraState.initialized = true;
                                        cameraState.pos = camera.getCameraPos();
                                        cameraState.blockPos = net.minecraft.util.math.BlockPos.ofFloored(camera.getCameraPos());
                                        cameraState.entityPos = camera.getCameraPos();
                                        cameraState.orientation = camera.getRotation();
                                        beDispatcher.render(beState, matrices, queue, cameraState);
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        matrices.pop();
                    } else {
                        if (item.isIn(ConventionalItemTags.TOOLS) && !item.isIn(ItemTags.TRIMMABLE_ARMOR) && !item.isIn(ItemTags.BOOKSHELF_BOOKS) && item.getUseAction() != UseAction.EAT && item.isEnchantable() || item.getUseAction() == UseAction.BOW || item.getUseAction() == UseAction.SPYGLASS || this.hmiGetAttackDamage(item) != 0.0F || item.getUseAction() == UseAction.BLOCK || item.isOf(Items.WARPED_FUNGUS_ON_A_STICK) || item.isOf(Items.CARROT_ON_A_STICK) || item.isOf(Items.FISHING_ROD) || item.isOf(Items.SHEARS)) {
                            if (item.isIn(ItemTags.SWORDS) && !sharpSword) {
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-60.0F * swing));
                                matrices.translate((double) 0.0F, 0.1 * (double) swing, -0.1 * (double) swing);
                            }

                            if (item.isIn(ItemTags.SHOVELS)) {
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F * swing_rot));
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F * swing));
                            } else if (item.getUseAction() == UseAction.TRIDENT) {
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-40.0F * swing_rot));
                                matrices.translate((double) 0.0F, 0.1 * (double) swing_rot, -0.1 * (double) swing_rot);
                            } else if (item.getUseAction() != UseAction.BLOCK) {
                                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-25.0F * swing));
                                matrices.translate((double) 0.0F, 0.05 * (double) swing, -0.05 * (double) swing);
                            }
                        }

                        if (!item.isOf(Items.NETHER_STAR) && (!item.isOf(Items.END_CRYSTAL) || !config.mb3DCompat.getValue())) {
                            this.hmi$netherCounter = 0.0F;
                        } else {
                            this.hmi$netherCounter = (float) ((double) this.hmi$netherCounter + 0.9 * tt);
                            matrices.translate((double) 0.0F, (double) 0.25F + 0.02 * (double) MathHelper.sin(this.hmi$netherCounter * 0.1F), (double) 0.0F);
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(3.0F * MathHelper.sin(this.hmi$netherCounter * 0.2F)));
                            matrices.scale(1.0F + 0.01F * MathHelper.sin(this.hmi$netherCounter), 1.0F + 0.01F * MathHelper.sin(this.hmi$netherCounter), 1.0F + 0.01F * MathHelper.sin(this.hmi$netherCounter));
                        }

                        if (config.mb3DCompat.getValue()) {
                            if (item.isIn(ItemTags.SWORDS)) {
                                matrices.translate((double) 0.0F, 0.2, (double) 0.0F);
                            }

                            if (item.isOf(Items.FEATHER) || item.isOf(Items.SLIME_BALL) || item.isOf(Items.PUFFERFISH)) {
                                this.hmi$vertVelocityYSlime = (float) ((double) this.hmi$vertVelocityYSlime + (double) swingProgress * 0.03 * (float) tt);
                                if ((player.getVelocity().length() > 0.09 && player.isOnGround() || player.isInSwimmingPose() || hmi$isCrawling(player) || player.isClimbing() && !player.isOnGround()) && this.client.options.getBobView().getValue()) {
                                    Random random = new Random();
                                    boolean randomBoolean = random.nextBoolean();
                                    this.hmi$vertVelocityYSlime += (float) (-0.05 * player.getVelocity().length() * (float) tt);
                                }

                                matrices.scale(1.0F, 1.0F + this.hmi$vertAngleYSlime * -2.0F, 1.0F);
                            }
                        }

                        if (item.isIn(ItemTags.SHOVELS)) {
                            matrices.translate(0.07 * (double) l, (double) 0.0F, 0.05);
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (90 * l)));
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-15.0F));
                        }

                        if (item.isOf(Items.TORCH)) {

                        }

                        renderItem(player, item, bl2 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, matrices, queue, light);
                    }
                }

                matrices.pop();
                matrices.pop();
                this.hmi$isAttacking = this.client.options.attackKey.isPressed();
            }
        }
    }

    @WrapOperation(
        method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
        at = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"),
        require = 0
    )
    private void hmi$handRenderHook(HeldItemRenderer instance, AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Operation<Void> original) {
        HoldMyItems hmi = HoldMyItems.getInstance();
        if (hmi != null && hmi.isEnabled()) {
            Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            float sideFactor = hand == Hand.MAIN_HAND ? 1.0F : -1.0F;
            if (hmi.swapHands.getValue()) {
                arm = arm.getOpposite();
                sideFactor *= -1.0F;
            }
            float hmiSwingProgress = hand == Hand.MAIN_HAND ? swingProgress : 0.0F;
            this.hmiRenderCustomFirstPersonItem(
                    player, tickDelta, pitch, hand, arm, sideFactor,
                    hmiSwingProgress, item, 0.0F, matrices, queue, light
            );
            return;
        }
        original.call(instance, player, tickDelta, pitch, hand, swingProgress, item, equipProgress, matrices, queue, light);
    }
}
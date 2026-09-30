package ru.white.module.impl.render;

import ru.white.manager.event_impl.HandAnimationEvent;
import ru.white.manager.event_impl.SwingDurationEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.animation.Easings;
import ru.white.utils.other.Instance;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

@ModuleInfo(
        name = "Swing Animation",
        category = Category.VISUALS,
        desc = "Анимации взмаха руки"
)
public class SwingAnimation extends Module {

    public static SwingAnimation get() {
        return Instance.get(SwingAnimation.class);
    }

    public ModeSetting swingMode = new ModeSetting(this, "Режим анимации", "Первый", "Второй", "Третий", "Четвёртый", "Пятый", "Шестой");

    public ModeSetting typeSwing = new ModeSetting(this, "Тип", "Первый", "Второй").setVisible(() -> swingMode.is("Третий"));
    public SliderSetting swingPowessss = new SliderSetting(this, "Угол", 90, -180, 180, 1).setVisible(() -> swingMode.is("Третий"));

    public ModeSetting easeSwin = new ModeSetting(this, "Режим интерполяции", "Первый", "Второй", "Третий");

    public BooleanSetting auraOnly = new BooleanSetting(this, "Только с аурой", false);

    public SliderSetting animSpeed = new SliderSetting(this, "Скорость анимации", 1, 0.1F, 4, 0.1F);
    public SliderSetting animSize = new SliderSetting(this, "Сила анимации", 6, 0.1F, 10, 0.1F);

    public static void interactItem(Hand hand) {
        mc.player.swingHand(Hand.MAIN_HAND);
        sendSequencedPacket(id ->
                new PlayerInteractItemC2SPacket(hand, id,
                        mc.player.getYaw(),
                        mc.player.getPitch())
        );
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        mc.interactionManager.sendSequencedPacket(mc.world, packetCreator);
    }

    @EventHandler
    public void onSwingDuration(SwingDurationEvent e) {
        if (!auraCheck()) {
            return;
        }

        Item item = mc.player.getStackInHand(Hand.MAIN_HAND).getItem();

        if (item == Items.AIR) return;

        float speed = animSpeed.getValue();

        e.setAnimation(speed);
        e.cancel();
    }

    @EventHandler
    public void onEvent(HandAnimationEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (!auraCheck()) {
            return;
        }

        boolean rightHand = event.getArm() == Arm.RIGHT;
        ModeSetting mode = swingMode;

        if (event.getArm() != mc.player.getMainArm()) {
            return;
        }

        ItemStack stack = mc.player.getStackInHand(event.getHand());

        MatrixStack matrices = event.getMatrices();

        float swingProgress = event.getSwingProgress();

        int i = rightHand ? 1 : -1;

        float t;
        if (easeSwin.is("Первый")) {
            t = (float) Easings.SINE_OUT.ease(swingProgress);
        } else if (easeSwin.is("Третий")) {
            t = swingProgress;
        } else {
            t = (float) Easings.BACK_OUT.ease(swingProgress);
        }

        float back = t;

        float sin1 = MathHelper.sin(back * back * (float) Math.PI);
        float sin2 = MathHelper.sin(MathHelper.sqrt(back) * (float) Math.PI);

        float sinSmooth = (sin1 * sin2);

        if(easeSwin.is("Третий")) {
            sinSmooth = (float) Math.sin(swingProgress * (Math.PI / 2) * 2);
        }

        float size = animSize.getValue();
        Arm arm = event.getArm();

        switch (mode.getValue()) {
            case "Первый" -> {
                matrices.translate(i * 0.56F, -0.52F, -0.72F);
                float g = sinSmooth;
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g *  -180.0F * size / 10));
                matrices.multiply(RotationAxis.NEGATIVE_Z.rotationDegrees(g *(rightHand ? -60.0F * size / 10 : 60.0F * size / 10)));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(g *(rightHand ? -60.0F * size / 10 : 60.0F * size / 10)));
            }
            case "Второй" -> {
                matrices.translate(i * 0.56F, -0.42F, -0.72F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinSmooth * -(24 * size)));
                matrices.translate(0, -0.1, 0);
            }
            case "Третий" -> {
                matrices.translate(i * 0.7F, -0.35F, -0.9);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * 60));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * -60));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-swingPowessss.getValue() - (typeSwing.is("Первый") ? (size * 24) * sinSmooth : -(size * 24) * sinSmooth))));
            }
            case "Четвёртый" -> {
                matrices.translate(i * (0.2F - 0.1 * sinSmooth * (0.25F * size)), 0, -0.2F - 0.2 * sinSmooth * (0.25F * size));
                applyEquipOffset(matrices, arm, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50f - 4 * sinSmooth * (0.35F * size)));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * (-60f + (0.6F * size * sinSmooth))));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * (110f + 40f * sinSmooth * (0.2F * size))));
            }
            case "Пятый" -> {
                matrices.translate(i * 0.56F, -0.42F, -0.72F);
                // Умножаем на 180, так как размах синуса от -1 до 1 дает общую амплитуду в 360 градусов
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-0 + t * 360));
                matrices.translate(0, -0.1, 0);
            }
            case "Шестой" -> {
                matrices.translate(i * 0.7F, -0.35F, -0.9);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * 65));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * -10));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-90- (size * 24) * sinSmooth)));
            }
        }
        event.cancel();
    }

    public boolean auraCheck() {
        return true;
    }

    public void onEatAnimsHands(MatrixStack matrices, float tickDelta, Arm arm, ItemStack stack, PlayerEntity player) {
        float f = (float) player.getItemUseTimeLeft() - tickDelta + 1.0F;
        float g = f / (float) stack.getMaxUseTime(player);
        if (g < 0.8F) {
            float h = MathHelper.abs(MathHelper.cos(f / 5.0F * (float) Math.PI) * 0.015F);
            matrices.translate(0.0F, h, 0.0F);
        }

        float h = 1.0F - (float) Math.pow((double) g, (double) 27.0F);
        int i = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate(h * 0.6F * (float) i, h * -0.5F, h * 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * h * 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(h * 10.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * h * 30.0F));
    }

    private void applyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress) {
        int i = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate((float) i * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
    }
}

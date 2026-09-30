package ru.white.manager.event_impl;

import ru.white.manager.events.Event;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

public class RenderItemEvent extends Event {
    private final MatrixStack matrix;
    private final Hand hand;
    private final Arm arm;

    public RenderItemEvent(MatrixStack matrix, Hand hand, Arm arm) {
        this.matrix = matrix;
        this.hand = hand;
        this.arm = arm;
    }

    public MatrixStack getMatrix() {
        return matrix;
    }

    public Hand getHand() {
        return hand;
    }

    public Arm getArm() {
        return arm;
    }

    public boolean isRightHand() {
        return arm == Arm.RIGHT;
    }
}

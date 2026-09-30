package ru.white.manager.event_impl;

import ru.white.manager.events.CancellableEvent;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@Getter
@Setter
public class HandAnimationEvent extends CancellableEvent {
    MatrixStack matrices;
    Hand hand;
    float swingProgress;
    float equipProgress;
    Arm arm;

}


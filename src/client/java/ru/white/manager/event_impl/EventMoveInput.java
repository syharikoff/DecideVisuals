package ru.white.manager.event_impl;

import ru.white.manager.events.CancellableEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.PlayerInput;

@Getter
@Setter
@AllArgsConstructor
public class EventMoveInput extends CancellableEvent {
    private PlayerInput input;
    private float forward, strafe;

    public void setDirectionalLow(boolean forward, boolean backward, boolean left, boolean right) {
        input = new PlayerInput(forward, backward, left, right, input.jump(), input.sneak(), input.sprint());
    }
}
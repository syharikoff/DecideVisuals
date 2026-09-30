package ru.white.manager.event_impl;

import ru.white.manager.events.CancellableEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
@AllArgsConstructor
public class MotionEvent extends CancellableEvent {
    private double x, y, z, yaw, pitch;
    private boolean ground;
}

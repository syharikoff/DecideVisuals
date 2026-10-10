package ru.decide.manager.event_impl;

import ru.decide.manager.events.Event;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.math.Vec3d;

@Data
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MoveEvent extends Event {
    Vec3d movement;
}
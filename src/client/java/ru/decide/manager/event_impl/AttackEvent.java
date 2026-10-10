package ru.decide.manager.event_impl;

import ru.decide.manager.events.CancellableEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.Entity;

@Getter
@Setter
@AllArgsConstructor
public class AttackEvent extends CancellableEvent {
    private Entity target;
}
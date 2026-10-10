package ru.decide.manager.event_impl;

import ru.decide.manager.events.CancellableEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UsingItemEvent extends CancellableEvent {
    byte type;
}

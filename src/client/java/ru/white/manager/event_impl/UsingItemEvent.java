package ru.white.manager.event_impl;

import ru.white.manager.events.CancellableEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UsingItemEvent extends CancellableEvent {
    byte type;
}

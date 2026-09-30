package ru.white.manager.event_impl;

import ru.white.manager.events.Event;

/** Событие отпускания клавиши (только в игре, без открытого Screen). */
public class EventKeyRelease extends Event {
    private int key;

    public EventKeyRelease(int key) {
        this.key = key;
    }

    public int getKey() {
        return this.key;
    }

    public void setKey(int key) {
        this.key = key;
    }
}

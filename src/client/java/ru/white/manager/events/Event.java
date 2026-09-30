package ru.white.manager.events;


import ru.white.Client;

public class Event {
    public String getName() {
        return this.getClass().getSimpleName().toLowerCase();
    }

    public void hook() {
        Client.eventHandler().post(this);
    }
}

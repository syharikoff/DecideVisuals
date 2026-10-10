package ru.decide.manager.events;


import ru.decide.Client;

public class Event {
    public String getName() {
        return this.getClass().getSimpleName().toLowerCase();
    }

    public void hook() {
        Client.eventHandler().post(this);
    }
}

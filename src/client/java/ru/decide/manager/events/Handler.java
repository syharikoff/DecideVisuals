package ru.decide.manager.events;


import ru.decide.Client;

public abstract class Handler {
    public Handler() {
        Client.eventHandler().subscribe(this);
    }
}
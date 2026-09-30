package ru.white.manager.events;


import ru.white.Client;

public abstract class Handler {
    public Handler() {
        Client.eventHandler().subscribe(this);
    }
}
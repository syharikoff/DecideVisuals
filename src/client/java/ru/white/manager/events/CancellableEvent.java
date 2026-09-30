package ru.white.manager.events;

import ru.white.manager.events.orbit.ICancellable;

public class CancellableEvent extends Event implements ICancellable {
    private boolean cancelled;

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public void cancel() {
        ICancellable.super.cancel();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}

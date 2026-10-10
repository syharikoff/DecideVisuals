package ru.decide.rpc.callbacks;


import com.sun.jna.Callback;
import ru.decide.rpc.DiscordUser;

public interface ReadyCallback extends Callback {
    void apply(final DiscordUser p0);
}

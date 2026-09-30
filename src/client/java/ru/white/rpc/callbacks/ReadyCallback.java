package ru.white.rpc.callbacks;


import com.sun.jna.Callback;
import ru.white.rpc.DiscordUser;

public interface ReadyCallback extends Callback {
    void apply(final DiscordUser p0);
}

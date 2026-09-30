package ru.white.rpc.callbacks;


import com.sun.jna.Callback;
import ru.white.rpc.DiscordUser;

public interface JoinRequestCallback extends Callback {
    void apply(final DiscordUser p0);
}

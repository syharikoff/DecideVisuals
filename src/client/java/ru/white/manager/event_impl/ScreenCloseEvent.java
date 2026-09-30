package ru.white.manager.event_impl;

import ru.white.manager.events.CancellableEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.gui.screen.Screen;

@Getter
@AllArgsConstructor
public class ScreenCloseEvent extends CancellableEvent {
    private Screen screen;
    private int windowId;

}
package ru.white.manager.event_impl;

import ru.white.manager.events.Event;
import lombok.Getter;
import net.minecraft.client.gui.screen.Screen;

@Getter
public class MouseReleaseEvent extends Event {

    private final double mouseX;
    private final double mouseY;
    private final int button;
    private final Screen screen;

    public MouseReleaseEvent(double mouseX, double mouseY, int button, Screen screen) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.button = button;
        this.screen = screen;
    }
}

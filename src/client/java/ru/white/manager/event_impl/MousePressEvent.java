package ru.white.manager.event_impl;

import ru.white.manager.events.Event;
import net.minecraft.client.gui.screen.Screen;

public class MousePressEvent extends Event {
    private final int button;
    private final int action;
    private final int modifiers;
    private final Screen screen;
    private double mouseX, mouseY;


    public MousePressEvent(int button, int action, int modifiers, Screen screen,double mouseX,double mouseY) {
        this.button = button;
        this.action = action;
        this.modifiers = modifiers;
        this.screen = screen;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }



    public int getKey() {
        return button;
    }
    public double getMouseX() {
        return mouseX;
    }

    public double getMouseY() {
        return mouseY;
    }

    public int getButton() {
        return button;
    }

    public int getAction() {
        return action;
    }

    public int getModifiers() {
        return modifiers;
    }

    public Screen getScreen() {
        return screen;
    }
}


package ru.white.ui.compat.impl;

import ru.white.ui.compat.SoundAPI;
import ru.white.utils.other.GuiSounds;

public final class NightixSoundAPI implements SoundAPI {

    @Override
    public void play(String name) {
        if (name == null) return;
        switch (name.toLowerCase()) {
            case "open" -> GuiSounds.open();
            case "close" -> GuiSounds.close();
            case "click" -> GuiSounds.button();
            case "toggle" -> GuiSounds.toggle(true);
            case "slider_grab" -> GuiSounds.sliderGrab();
            case "slider_tick" -> GuiSounds.sliderTick(0.5F);
            case "slider_release" -> GuiSounds.sliderRelease();
            case "bind_start" -> GuiSounds.bindStart();
            case "bind_set" -> GuiSounds.bindSet();
            case "bind_reset" -> GuiSounds.bindReset();
            case "edit_start" -> GuiSounds.editStart();
            case "edit_commit" -> GuiSounds.editCommit();
            case "edit_cancel" -> GuiSounds.editCancel();
            case "type" -> GuiSounds.type();
            case "erase" -> GuiSounds.erase();
            case "search_clear" -> GuiSounds.searchClear();
            case "scroll" -> GuiSounds.scroll();
            case "expand" -> GuiSounds.expand(true);
            default -> GuiSounds.button();
        }
    }
}

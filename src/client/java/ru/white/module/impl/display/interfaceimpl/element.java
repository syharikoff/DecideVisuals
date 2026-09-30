package ru.white.module.impl.display.interfaceimpl;

import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.display.InterFace;
import ru.white.utils.annotation.IMinecraft;

public interface element extends IMinecraft {

    void onRender(DragSetting dragSetting, InterFace interFace);

}

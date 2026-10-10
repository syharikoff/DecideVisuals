package ru.decide.module.impl.display.interfaceimpl;

import ru.decide.module.api.settings.impl.DragSetting;
import ru.decide.module.impl.display.InterFace;
import ru.decide.utils.annotation.IMinecraft;

public interface element extends IMinecraft {

    void onRender(DragSetting dragSetting, InterFace interFace);

}

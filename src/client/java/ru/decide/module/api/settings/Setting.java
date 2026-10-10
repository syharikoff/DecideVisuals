package ru.decide.module.api.settings;


import ru.decide.utils.animation.Animation;
import ru.decide.utils.animation.satoshi.EaseInOutQuad;
import ru.decide.utils.annotation.IMinecraft;
import lombok.Getter;
import ru.decide.module.api.Module;


import java.util.function.Supplier;

@Getter
public class Setting<Value> implements ISetting, IMinecraft {
    private Runnable onAction;
    private Runnable onSetVisible;
    private Value value;
    @Getter
    public final String name;
    private Supplier<Boolean> visible = () -> true;
    private Module parent;
    private final Animation animation = new Animation();
    private final ru.decide.utils.animation.satoshi.Animation animation1 = new EaseInOutQuad(300, 1);

    public Setting(String name, Value value) {
        this.name = name;
        this.value = value;
    }

    public Setting(Module parent, String name, Value value) {
        this.parent = parent;
        this.name = name;
        this.value = value;
        parent.getSettings().add(this);
    }

    public Setting<?> set(Value value) {
        this.value = value;
        if (mc.world != null && mc.player != null && onAction != null) {
            onAction.run();
        }
        return this;
    }


    @Override
    public Setting<?> setVisible(Supplier<Boolean> value) {
        visible = value;
        if (mc.world != null && mc.player != null && onSetVisible != null) {
            onSetVisible.run();
        }
        return this;
    }

    public Setting<?> onAction(Runnable action) {
        this.onAction = action;
        return this;
    }

    public Setting<?> onSetVisible(Runnable action) {
        this.onSetVisible = action;
        return this;
    }
}
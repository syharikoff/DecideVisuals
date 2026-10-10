package ru.decide.module.api;

import ru.decide.Client;
import ru.decide.module.api.settings.Setting;
import ru.decide.module.impl.display.InterFace;
import ru.decide.module.impl.display.Sounds;
import ru.decide.utils.animation.Animation;
import ru.decide.utils.animation.satoshi.EaseInOutQuad;
import ru.decide.utils.annotation.IMinecraft;


import ru.decide.utils.other.SoundUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Data;
import org.apache.commons.lang3.NotImplementedException;


import java.util.List;


@Data
public abstract class Module implements IMinecraft {
    private final List<Setting<?>> settings = new ObjectArrayList<>();
    private ModuleInfo moduleInfo;
    private String name;
    private String bigName;
    private String desc;
    private Category category;
    private boolean enabled;
    private boolean autoEnabled;
    private boolean allowDisable;
    private boolean hidden;
    private int key;

    public final Animation animation_new = new Animation();
    public final Animation animation_new_1 = new Animation();
    public final Animation toggleAnimation = new Animation();

    public final Animation hoverModule = new Animation();
    public final Animation expandedAnimation = new Animation();
    public final Animation animtoP7 = new Animation();
    public final Animation animtoP8 = new Animation();
    public ru.decide.utils.animation.satoshi.Animation animation = new EaseInOutQuad(300, 1);
    public ru.decide.utils.animation.satoshi.Animation animation1 = new EaseInOutQuad(300, 1);
    public ru.decide.utils.animation.satoshi.Animation animation2 = new EaseInOutQuad(300, 1);
    public ru.decide.utils.animation.satoshi.Animation animation3 = new EaseInOutQuad(300, 1);
    public ru.decide.utils.animation.satoshi.Animation animation12 = new EaseInOutQuad(300, 1);
    public ru.decide.utils.animation.satoshi.Animation animation14 = new EaseInOutQuad(300, 1);
    public ru.decide.utils.animation.satoshi.Animation animation15 = new EaseInOutQuad(300, 1);
    public ru.decide.utils.animation.satoshi.Animation animation16 = new EaseInOutQuad(300, 1);

    public Module() {
        Class<? extends Module> clazz = this.getClass();
        ModuleInfo moduleInfo = clazz.getAnnotation(ModuleInfo.class);

        if (moduleInfo == null) {
            throw new NotImplementedException("@ModuleInfo annotation not found on " + clazz.getSimpleName());
        }

        this.moduleInfo = moduleInfo;
        this.name = moduleInfo.name().trim().replaceAll(" ", "");
        this.bigName =  moduleInfo.name();
        this.desc = moduleInfo.desc();
        this.category = moduleInfo.category();
        this.autoEnabled = moduleInfo.autoEnabled();
        this.allowDisable = moduleInfo.allowDisable();
        this.hidden = moduleInfo.hidden();
        this.key = moduleInfo.key();
        setup();
    }

    public void toggle() {
        setEnabled(!enabled);
    }


    public void setEnabled(final boolean enabled) {
        setEnabled(enabled, true);
    }

    public void setEnabled(final boolean enabled, boolean notification) {
        if (this.enabled == enabled || (!this.allowDisable && !enabled)) {
            return;
        }
        this.enabled = enabled;

        if (enabled) {
            superEnable();
            if(mc.player != null && notification) {
                Sounds sounds = Sounds.get();
                if (sounds != null) {
                    sounds.playToggleSound(true);
                }
            }
        } else {
            superDisable();
            if(mc.player != null && notification) {
                Sounds sounds = Sounds.get();
                if (sounds != null) {
                    sounds.playToggleSound(false);
                }
            }
        }


    }

    private void eventEnable() {
        Client.eventHandler().subscribe(this);
    }

    private void eventDisable() {
        Client.eventHandler().unsubscribe(this);
    }

    private void superEnable() {
        if (mc.player != null) onEnable();

        eventEnable();
    }

    private boolean expanded;

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    private void superDisable() {
        if (mc.player != null) onDisable();

        eventDisable();
    }

    public void setup() {
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }
}
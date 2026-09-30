package ru.white.module.impl.render;


import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import net.minecraft.entity.effect.StatusEffects;

@ModuleInfo(
        name = "Gamma",
        desc = "Полная яркость (фуллбрайт)",
        category = Category.VISUALS
)
public class Gamma extends Module {


    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.worldRenderer != null) mc.worldRenderer.reload();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.worldRenderer != null) mc.worldRenderer.reload();
    }
    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null) return;
        mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
    }
}

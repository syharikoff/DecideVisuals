package ru.white.module.impl.render;

import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.impl.render.swordreplacer.SwordReplacer;
import ru.white.module.impl.render.swordreplacer.WeaponCatalog;

@ModuleInfo(
        name = "Custom Sword",
        desc = "Replace diamond and netherite sword models",
        category = Category.VISUALS
)
public class CustomSword extends Module {

    public BooleanSetting enabled = new BooleanSetting(this, "Enable", true);

    public ModeSetting diamondModel = new ModeSetting(this, "Diamond",
            WeaponCatalog.allModels().toArray(new String[0]));

    public ModeSetting netheriteModel = new ModeSetting(this, "Netherite",
            WeaponCatalog.allModels().toArray(new String[0]));

    @Override
    protected void onEnable() {
        SwordReplacer.setEnabled(true);
        applyModels();
    }

    @Override
    protected void onDisable() {
        SwordReplacer.setEnabled(false);
    }

    @EventHandler
    public void onTick(EventTick e) {
        SwordReplacer.setEnabled(enabled.getValue());
        applyModels();
    }

    private void applyModels() {
        SwordReplacer.setDiamondModel(diamondModel.getValue());
        SwordReplacer.setNetheriteModel(netheriteModel.getValue());
    }
}

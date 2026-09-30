package ru.white.module.impl.player;


import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.utils.other.Instance;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

@ModuleInfo(
        name = "No Delay",
        category = Category.UTILITIES,
        desc = "Убирает задержки"
)
public class NoDelay extends Module {

    public static NoDelay get() {
        return Instance.get(NoDelay.class);
    }

    public BooleanSetting jump = new BooleanSetting(this, "Прыжок", true);
    public BooleanSetting expBottle = new BooleanSetting(this, "Пузырёк опыта", true);

    @EventHandler
    private void onUpdate(EventUpdate event) {
        if (mc.player == null)
            return;

        if (expBottle.getValue() && mc.player.getMainHandStack().getItem() == Items.EXPERIENCE_BOTTLE) {
            mc.player.getItemCooldownManager().remove(Registries.ITEM.getId(Items.EXPERIENCE_BOTTLE));
            resetCooldown();
        }
    }

    private void resetCooldown() {
        try {
            java.lang.reflect.Field f = net.minecraft.client.MinecraftClient.class.getDeclaredField("itemUseCooldown");
            f.setAccessible(true);
            f.setInt(mc, 0);
        } catch (Exception e) {
            try {
                // Try intermediary name if mapped
                java.lang.reflect.Field f = net.minecraft.client.MinecraftClient.class.getDeclaredField("field_1752");
                f.setAccessible(true);
                f.setInt(mc, 0);
            } catch (Exception ex) {
                // Ignore
            }
        }
    }
}

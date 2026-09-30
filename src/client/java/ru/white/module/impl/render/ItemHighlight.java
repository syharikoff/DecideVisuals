package ru.white.module.impl.render;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import ru.white.Client;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.DelimiterSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.inventory.SlotCategory;
import ru.white.utils.inventory.SlotItem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

@ModuleInfo(
        name = "Item Highlight",
        desc = "Подсвечивает фон ячеек для нужных предметов",
        category = Category.VISUALS
)
public class ItemHighlight extends Module {

    private static ItemHighlight instance;

    private final Map<SlotItem, ColorSetting> itemColors = new EnumMap<>(SlotItem.class);
    private final Map<Item, ItemEntry> itemEntries = new LinkedHashMap<>();

    public final SliderSetting opacity = new SliderSetting(this, "Прозрачность", 80F, 0F, 100F, 1F);

    public ItemHighlight() {
        instance = this;

        for (SlotItem slotItem : SlotItem.values()) {
            ColorSetting colorSetting = new ColorSetting(this,
                    slotItem.getDisplayName(),
                    0xFF000000 | slotItem.getDefaultColor());
            itemColors.put(slotItem, colorSetting);
        }

        new DelimiterSetting(this, "Предметы");
        addItem(Items.ENDER_PEARL, "Эндер перл", 0xFF009600);
        addItem(Items.SNOWBALL, "Снежок", 0xFF64C8FF);
        addItem(Items.NETHERITE_SCRAP, "Трапка", 0xFFC8B496);
        addItem(Items.LANTERN, "Светильник", 0xFFFF9600);
        addItem(Items.FIRE_CHARGE, "Взрывная т", 0xFFFF0000);
        addItem(Items.TOTEM_OF_UNDYING, "Тотем", 0xFFFFC800);
        addItem(Items.CROSSBOW, "Арбалет", 0xFF966432);
        addItem(Items.NETHERITE_SWORD, "Незеритовый меч", 0xFF323232);
        addItem(Items.CHORUS_FRUIT, "Хорус", 0xFFC864C8);
        addItem(Items.SUGAR, "Сахар", 0xFFFFFFFF);
        addItem(Items.PHANTOM_MEMBRANE, "Мембрана", 0xFFC8B496);
        addItem(Items.ENDER_EYE, "Око эндера", 0xFF6400C8);
        addItem(Items.DRIED_KELP, "Ламинария", 0xFF649632);
        addItem(Items.EXPERIENCE_BOTTLE, "Пузырек опыта", 0xFF00C864);
        addItem(Items.GOLDEN_APPLE, "Золотое яблоко", 0xFFFFC800);
        addItem(Items.ENCHANTED_GOLDEN_APPLE, "Чар. яблоко", 0xFFFF9600);
    }

    public static ItemHighlight getInstance() {
        ItemHighlight itemHighlight = Client.get().moduleManager().get(ItemHighlight.class);
        return itemHighlight != null ? itemHighlight : instance;
    }

    public int backgroundFor(ItemStack stack, boolean ignored) {
        if (isEnabled() && stack != null && !stack.isEmpty()) {
            int n = potionBackground(stack);
            if (n != 0) {
                return n;
            }
            ItemEntry entry = itemEntries.get(stack.getItem());
            if (entry != null && entry.enabled.getValue()) {
                int rgb = entry.color.getValue() & 0xFFFFFF;
                float alpha = ((entry.color.getValue() >> 24) & 0xFF) / 255F;
                return withOpacity(rgb, alpha);
            }
            return 0;
        }
        return 0;
    }

    public void drawSlotBackground(DrawContext ctx, int x, int y, int argb) {
        if (ctx != null && argb != 0) {
            ctx.fill(x, y, x + 16, y + 16, argb);
        }
    }

    private void addItem(Item item, String displayName, int color) {
        BooleanSetting enabled = new BooleanSetting(this, displayName, true);
        ColorSetting colorSetting = new ColorSetting(this, displayName + " цвет", color)
                .setVisible(enabled::getValue);
        itemEntries.put(item, new ItemEntry(enabled, colorSetting));
    }

    private int potionBackground(ItemStack stack) {
        SlotCategory category = SlotCategory.classify(stack);
        if (category == null) {
            return 0;
        }
        SlotItem slotItem = SlotItem.match(stack, category.getItemGroup());
        if (slotItem == null) {
            return 0;
        }
        ColorSetting colorSetting = itemColors.get(slotItem);
        int rgb;
        float alpha;
        if (colorSetting != null) {
            rgb = colorSetting.getValue() & 0xFFFFFF;
            alpha = ((colorSetting.getValue() >> 24) & 0xFF) / 255F;
        } else {
            rgb = category.getDefaultColor() & 0xFFFFFF;
            alpha = 1.0F;
        }
        return withOpacity(rgb, alpha);
    }

    private int withOpacity(int rgb, float alpha) {
        float percent = Math.max(0.0F, Math.min(1.0F, opacity.getValue() / 100.0F));
        int a = Math.round(255.0F * alpha * percent);
        return a <= 0 ? 0 : (a << 24) | rgb;
    }

    public record ItemEntry(BooleanSetting enabled, ColorSetting color) {
    }
}
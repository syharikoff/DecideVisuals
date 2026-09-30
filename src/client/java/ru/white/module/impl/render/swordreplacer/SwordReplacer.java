package ru.white.module.impl.render.swordreplacer;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SwordReplacer {
    private static final String RENDER_KEY_PREFIX = "wvisual:item_replacer:";

    private static final Map<String, CustomModelDataComponent> MODEL_COMPONENTS = createModelComponents();

    private static volatile String diamondModel = WeaponCatalog.DEFAULT_MODEL;
    private static volatile String netheriteModel = WeaponCatalog.DEFAULT_MODEL;
    private static volatile boolean enabled = false;

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static void setDiamondModel(String model) {
        if (WeaponCatalog.contains(model)) diamondModel = model;
    }
    public static void setNetheriteModel(String model) {
        if (WeaponCatalog.contains(model)) netheriteModel = model;
    }
    public static String getDiamondModel() { return diamondModel; }
    public static String getNetheriteModel() { return netheriteModel; }

    public static String[] allModelNames() {
        return WeaponCatalog.allModels().toArray(new String[0]);
    }

    public static ItemStack getRenderStack(ItemStack stack) {
        if (!enabled || stack == null || stack.isEmpty()) return stack;

        SwordType type = resolveType(stack);
        if (type == null) return stack;

        String modelName = (type == SwordType.DIAMOND) ? diamondModel : netheriteModel;
        if (WeaponCatalog.VANILLA_MODEL.equals(modelName)) return stack;

        CustomModelDataComponent component = MODEL_COMPONENTS.get(modelName);
        if (component == null) return stack;

        CustomModelDataComponent existing = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (existing != null && !existing.strings().isEmpty()
                && component.getString(0).equals(existing.getString(0))) {
            return stack;
        }

        ItemStack copy = stack.copy();
        copy.set(DataComponentTypes.CUSTOM_MODEL_DATA, mergeSelection(existing, component));
        return copy;
    }

    private static SwordType resolveType(ItemStack stack) {
        if (stack.isOf(Items.DIAMOND_SWORD)) return SwordType.DIAMOND;
        if (stack.isOf(Items.NETHERITE_SWORD)) return SwordType.NETHERITE;
        return null;
    }

    private static CustomModelDataComponent mergeSelection(CustomModelDataComponent existing,
                                                            CustomModelDataComponent replacement) {
        if (existing == null) return replacement;

        String replacementKey = replacement.getString(0);
        List<String> newStrings = new ArrayList<>(Math.max(1, existing.strings().size()));
        newStrings.addAll(existing.strings());

        if (newStrings.isEmpty()) {
            newStrings.add(replacementKey);
        } else {
            newStrings.set(0, replacementKey);
        }

        return new CustomModelDataComponent(
                existing.floats(),
                existing.flags(),
                List.copyOf(newStrings),
                existing.colors()
        );
    }

    private static Map<String, CustomModelDataComponent> createModelComponents() {
        Map<String, CustomModelDataComponent> map = new ConcurrentHashMap<>();
        for (String name : WeaponCatalog.customModels()) {
            map.put(name, new CustomModelDataComponent(
                    List.of(), List.of(),
                    List.of(RENDER_KEY_PREFIX + name),
                    List.of()
            ));
        }
        return map;
    }

    public enum SwordType {
        DIAMOND,
        NETHERITE
    }
}

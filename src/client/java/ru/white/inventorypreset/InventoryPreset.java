package ru.white.inventorypreset;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InventoryPreset {
    public static final int INVENTORY_SLOTS = 36;
    public static final int ARMOR_SLOTS = 4;
    public static final int OFFHAND_SLOT = 40;
    public static final int SLOT_COUNT = 41;

    private final String name;
    private final long createdAt;
    private final List<Entry> slots;

    public InventoryPreset(String name, long createdAt, List<Entry> slots) {
        this.name = name;
        this.createdAt = createdAt;
        ArrayList<Entry> normalized = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            Entry entry = i < slots.size() ? slots.get(i) : Entry.EMPTY;
            normalized.add(entry == null ? Entry.EMPTY : entry);
        }
        this.slots = Collections.unmodifiableList(normalized);
    }

    public String name() {
        return name;
    }

    public long createdAt() {
        return createdAt;
    }

    public List<Entry> slots() {
        return slots;
    }

    public Entry slot(int logicalSlot) {
        return logicalSlot >= 0 && logicalSlot < slots.size() ? slots.get(logicalSlot) : Entry.EMPTY;
    }

    public record Entry(String itemId, int count, String displayName, String potionSignature, String potionData) {
        public static final Entry EMPTY = new Entry("", 0, "", "", "");
        public static final String LEGACY_NAME_PREFIX = "legacy-name:";

        public Entry {
            itemId = itemId == null ? "" : itemId;
            count = Math.max(0, count);
            displayName = displayName == null ? "" : displayName;
            potionSignature = potionSignature == null ? "" : potionSignature;
            potionData = potionData == null ? "" : potionData;
        }

        public static Entry fromStack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return EMPTY;
            Identifier id = Registries.ITEM.getId(stack.getItem());
            PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
            String signature;
            if (potionContents != null) {
                signature = "potion:" + potionContents;
            } else if (id.getPath().endsWith("bundle")) {
                signature = "bundle:" + stack.getComponents();
            } else {
                signature = "";
            }
            return new Entry(id.toString(), stack.getCount(), stack.getName().getString(),
                    signature, encodePotion(potionContents));
        }

        /** Компонент зелья целиком — без него превью показывает пустую бутылку без цвета и названия. */
        private static String encodePotion(PotionContentsComponent contents) {
            if (contents == null) return "";
            return PotionContentsComponent.CODEC.encodeStart(JsonOps.INSTANCE, contents)
                    .result()
                    .map(JsonElement::toString)
                    .orElse("");
        }

        private static PotionContentsComponent decodePotion(String json) {
            if (json == null || json.isBlank()) return null;
            try {
                return PotionContentsComponent.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                        .result()
                        .orElse(null);
            } catch (Exception e) {
                return null;
            }
        }

        public boolean isEmpty() {
            return itemId.isBlank() || count <= 0;
        }

        public boolean matches(ItemStack stack) {
            if (isEmpty()) return stack == null || stack.isEmpty();
            if (stack == null || stack.isEmpty() || stack.getCount() < count) return false;
            return sameItem(stack);
        }

        public boolean sameItem(ItemStack stack) {
            if (isEmpty() || stack == null || stack.isEmpty()) return false;
            if (!Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) return false;
            if (potionSignature.isBlank()) return true;
            if (potionSignature.startsWith(LEGACY_NAME_PREFIX)) {
                return potionSignature.substring(LEGACY_NAME_PREFIX.length())
                        .equals(stack.getName().getString());
            }
            if (potionSignature.startsWith("bundle:")) {
                return potionSignature.equals("bundle:" + stack.getComponents());
            }
            Object potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (potionContents == null) return false;
            return potionSignature.equals(potionContents.toString())
                    || potionSignature.equals("potion:" + potionContents);
        }

        public String matchKey() {
            return itemId + '\u0000' + potionSignature;
        }

        public static boolean canHavePotionContents(String itemId) {
            return itemId.equals("minecraft:potion")
                    || itemId.equals("minecraft:splash_potion")
                    || itemId.equals("minecraft:lingering_potion")
                    || itemId.equals("minecraft:tipped_arrow");
        }

        public ItemStack toStack() {
            if (isEmpty()) return ItemStack.EMPTY;
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) return ItemStack.EMPTY;

            ItemStack stack = Registries.ITEM.getOptionalValue(id)
                    .map(item -> new ItemStack(item, count))
                    .orElse(ItemStack.EMPTY);

            if (stack.isEmpty()) return stack;

            // цвет и ванильное имя зелья берутся из компонента, поэтому его надо вернуть
            PotionContentsComponent contents = decodePotion(potionData);
            if (contents != null) stack.set(DataComponentTypes.POTION_CONTENTS, contents);

            // кастомное имя (серверные предметы) компонентами не сохраняется — ставим сохранённое
            if (!displayName.isBlank() && !displayName.equals(stack.getName().getString())) {
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
            }

            return stack;
        }
    }
}

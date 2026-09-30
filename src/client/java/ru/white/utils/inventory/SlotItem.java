package ru.white.utils.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

public enum SlotItem {
    POTION_HLOPUSHKA(
            "Хлопушка",
            16724530,
            List.of(sig(StatusEffects.SLOWNESS, 200, 9), sig(StatusEffects.SPEED, 400, 4), sig(StatusEffects.BLINDNESS, 100, 9), sig(StatusEffects.GLOWING, 3600, 0))
    ),
    POTION_RADIATION(
            "Радиация",
            16724530,
            List.of(
                    sig(StatusEffects.POISON, 1200, 1),
                    sig(StatusEffects.WITHER, 1200, 1),
                    sig(StatusEffects.SLOWNESS, 1800, 2),
                    sig(StatusEffects.HUNGER, 1200, 4),
                    sig(StatusEffects.GLOWING, 2400, 0)
            )
    ),
    POTION_SLEEP(
            "Снотворное",
            16724530,
            List.of(sig(StatusEffects.WEAKNESS, 1800, 1), sig(StatusEffects.MINING_FATIGUE, 200, 1), sig(StatusEffects.WITHER, 1800, 2), sig(StatusEffects.BLINDNESS, 200, 0))
    ),
    POTION_HOLY_WATER(
            "Святая вода",
            3329330,
            List.of(sig(StatusEffects.REGENERATION, 900, 1), sig(StatusEffects.INVISIBILITY, 12000, 1), sig(StatusEffects.INSTANT_HEALTH, 0, 1))
    ),
    POTION_RAGE("Гнева", 3329330, List.of(sig(StatusEffects.STRENGTH, 600, 4), sig(StatusEffects.SLOWNESS, 600, 3))),
    POTION_PALADIN(
            "Палладина",
            3329330,
            List.of(
                    sig(StatusEffects.RESISTANCE, 12000, 0),
                    sig(StatusEffects.FIRE_RESISTANCE, 12000, 0),
                    sig(StatusEffects.HEALTH_BOOST, 1200, 2),
                    sig(StatusEffects.INVISIBILITY, 18000, 0)
            )
    ),
    POTION_ASSASSIN(
            "Ассасина",
            3329330,
            List.of(sig(StatusEffects.STRENGTH, 1200, 3), sig(StatusEffects.SPEED, 6000, 2), sig(StatusEffects.HASTE, 1200, 0), sig(StatusEffects.INSTANT_DAMAGE, 0, 1))
    );

    private final String displayName;
    private final int defaultColor;
    private final List<EffectSignature> effectSignatures;

    SlotItem(String displayName, int defaultColor, List<EffectSignature> effectSignatures) {
        this.displayName = displayName;
        this.defaultColor = defaultColor;
        this.effectSignatures = effectSignatures;
    }

    private static EffectSignature sig(RegistryEntry<StatusEffect> effect, int duration, int amplifier) {
        return new EffectSignature(effect, duration, amplifier);
    }

    public static SlotItem match(ItemStack stack, Group group) {
        if (group == Group.POTION && stack != null && !stack.isEmpty()) {
            if (stack.getItem() != Items.SPLASH_POTION && stack.getItem() != Items.LINGERING_POTION) {
                return null;
            } else if (isPotionSectionButton(stack)) {
                return null;
            } else {
                for (SlotItem slotItem : values()) {
                    if (slotItem.matchesEffects(stack)) {
                        return slotItem;
                    }
                }
                return null;
            }
        } else {
            return null;
        }
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultColor() {
        return defaultColor;
    }

    public Group group() {
        return Group.POTION;
    }

    private static boolean isPotionSectionButton(ItemStack stack) {
        LoreComponent loreComponent = stack.get(DataComponentTypes.LORE);
        if (loreComponent != null && !loreComponent.lines().isEmpty()) {
            for (Text text : loreComponent.lines()) {
                String string = text.getString();
                if (string.contains("Нажмите, чтобы перейти")
                        || string.contains("Раздел открыт")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesEffects(ItemStack stack) {
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }
        ArrayList<StatusEffectInstance> effects = new ArrayList<>();
        for (StatusEffectInstance effect : contents.getEffects()) {
            effects.add(effect);
        }

        if (effects.size() != this.effectSignatures.size()) {
            return false;
        }
        for (EffectSignature signature : this.effectSignatures) {
            boolean found = false;
            for (StatusEffectInstance instance : effects) {
                if (signature.matches(instance)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    public record EffectSignature(RegistryEntry<StatusEffect> effect, int duration, int amplifier) {
        public boolean matches(StatusEffectInstance instance) {
            return instance.getEffectType().equals(this.effect)
                    && instance.getDuration() == this.duration
                    && instance.getAmplifier() == this.amplifier;
        }
    }

    public enum Group {
        POTION;
    }
}
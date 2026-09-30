package ru.white.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {

    @Unique
    private static final Pattern PRICE_PATTERN = Pattern.compile("Цен[аaAАыЫ]?:?\\s*([\\d,\\s\\.]+)", Pattern.CASE_INSENSITIVE);

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void appendUnitPrice(Item.TooltipContext context,
                                 PlayerEntity player,
                                 TooltipType type,
                                 CallbackInfoReturnable<List<Text>> cir) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (!(mc.currentScreen instanceof GenericContainerScreen)) {
                return;
            }

            ItemStack stack = (ItemStack) (Object) this;
            if (stack.isEmpty() || stack.getCount() <= 1) {
                return;
            }

            int totalPrice = parsePriceFromLore(stack);
            if (totalPrice <= 0) {
                return;
            }

            int unitPrice = totalPrice / stack.getCount();
            if (unitPrice <= 0) {
                unitPrice = 1;
            }

            List<Text> tooltip = new ArrayList<>(cir.getReturnValue());

            int priceLineIndex = findPriceLineIndex(tooltip);

            Text unitLine = Text.literal("")
                    .append(Text.literal("$ ").formatted(Formatting.DARK_GREEN))
                    .append(Text.literal("За 1 шт: ").formatted(Formatting.WHITE))
                    .append(Text.literal(formatPrice(unitPrice)).formatted(Formatting.GREEN));

            if (priceLineIndex >= 0 && priceLineIndex + 1 <= tooltip.size()) {
                tooltip.add(priceLineIndex + 1, unitLine);
            } else {
                tooltip.add(unitLine);
            }

            cir.setReturnValue(tooltip);
        } catch (Exception e) {
        }
    }
    @Unique
    private static int parsePriceFromLore(ItemStack stack) {
        LoreComponent loreComp = stack.get(DataComponentTypes.LORE);
        if (loreComp == null) return 0;

        for (Text text : loreComp.lines()) {
            String line = Formatting.strip(text.getString());
            if (line == null) continue;

            Matcher m = PRICE_PATTERN.matcher(line);
            if (m.find()) {
                try {
                    String priceStr = m.group(1).replaceAll("[,\\s\\.]", "");
                    return Integer.parseInt(priceStr);
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }
    @Unique
    private static int findPriceLineIndex(List<Text> tooltip) {
        for (int i = 0; i < tooltip.size(); i++) {
            String line = Formatting.strip(tooltip.get(i).getString());
            if (line == null) continue;

            Matcher m = PRICE_PATTERN.matcher(line);
            if (m.find()) {
                return i;
            }

            String lowerLine = line.toLowerCase();
            if (lowerLine.contains("цена") || lowerLine.contains("price") || lowerLine.contains("цен")) {
                return i;
            }
        }
        return -1;
    }
    @Unique
    private static String formatPrice(int price) {
        String value = String.valueOf(price);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (i > 0 && (value.length() - i) % 3 == 0) {
                builder.append('.');
            }
            builder.append(value.charAt(i));
        }
        return builder.toString();
    }
}
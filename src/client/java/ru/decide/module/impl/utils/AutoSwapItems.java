package ru.decide.module.impl.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class AutoSwapItems {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoSwapItems.class);
    private static final int MAX_SLOTS = 6;
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final ItemStack[] items = new ItemStack[MAX_SLOTS];
    private int count = 3;
    private boolean writable = true;

    public AutoSwapItems() {
        for (int i = 0; i < MAX_SLOTS; i++) items[i] = ItemStack.EMPTY;
    }

    public void setCount(int count) {
        this.count = Math.max(1, Math.min(MAX_SLOTS, count));
    }

    public int getCount() {
        return count;
    }

    public ItemStack get(int index) {
        if (index < 0 || index >= count) return ItemStack.EMPTY;
        return items[index].copy();
    }

    private Path file() {
        return mc.runDirectory.toPath().resolve("nur-swap").resolve("items.json");
    }

    public void load() {
        if (mc.world == null) return;
        for (int i = 0; i < MAX_SLOTS; i++) items[i] = ItemStack.EMPTY;
        writable = true;
        if (!Files.exists(file())) return;
        try (Reader reader = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (int i = 0; i < Math.min(array.size(), MAX_SLOTS); i++) {
                items[i] = array.get(i).isJsonNull() ? ItemStack.EMPTY : ItemStack.CODEC.parse(
                        mc.world.getRegistryManager().getOps(JsonOps.INSTANCE), array.get(i)).getOrThrow();
            }
        } catch (IOException | RuntimeException exception) {
            writable = false;
            LOGGER.warn("Unable to load Auto Swap items", exception);
            report("Не удалось загрузить предметы Auto Swap; файл оставлен без изменений");
        }
    }

    public boolean set(int index, ItemStack stack) {
        if (index < 0 || index >= count || mc.world == null || !writable) return false;
        ItemStack previous = items[index];
        items[index] = stack.copy();
        Path temporary = null;
        try {
            JsonArray array = new JsonArray();
            for (int i = 0; i < count; i++) {
                array.add(items[i].isEmpty() ? JsonNull.INSTANCE : encode(items[i]));
            }
            Path target = file();
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "items-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(array, writer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            items[index] = previous;
            LOGGER.warn("Unable to save Auto Swap items", exception);
            report("Не удалось сохранить предмет Auto Swap");
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    LOGGER.warn("Unable to remove temporary Auto Swap file", exception);
                }
            }
        }
    }

    public Predicate<ItemStack> predicate(int index) {
        if (index < 0 || index >= count || items[index].isEmpty() || mc.world == null) return stack -> false;
        JsonElement expected = encode(stripDamage(items[index].copyWithCount(1)));
        return stack -> !stack.isEmpty() && mc.world != null
                && equivalent(expected, encode(stripDamage(stack.copyWithCount(1))));
    }

    private ItemStack stripDamage(ItemStack stack) {
        stack.remove(net.minecraft.component.DataComponentTypes.DAMAGE);
        return stack;
    }

    private JsonElement encode(ItemStack stack) {
        return ItemStack.CODEC.encodeStart(mc.world.getRegistryManager().getOps(JsonOps.INSTANCE), stack).getOrThrow();
    }

    static boolean equivalent(JsonElement expected, JsonElement actual) {
        if (expected instanceof JsonObject first && actual instanceof JsonObject second) {
            if (!first.keySet().equals(second.keySet())) return false;
            for (String key : first.keySet()) {
                if (!equivalent(first.get(key), second.get(key))) return false;
            }
            return true;
        }
        if (expected instanceof JsonArray first && actual instanceof JsonArray second) {
            if (first.size() != second.size()) return false;
            List<JsonElement> remaining = new ArrayList<>(second.asList());
            for (JsonElement element : first) {
                int match = -1;
                for (int i = 0; i < remaining.size(); i++) {
                    if (equivalent(element, remaining.get(i))) {
                        match = i;
                        break;
                    }
                }
                if (match == -1) return false;
                remaining.remove(match);
            }
            return true;
        }
        return expected != null && expected.equals(actual);
    }

    private void report(String message) {
        if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.literal(message), false);
    }
}

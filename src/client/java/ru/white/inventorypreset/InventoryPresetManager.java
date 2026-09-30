package ru.white.inventorypreset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import ru.white.Client;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.utils.math.ChatUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

public final class InventoryPresetManager {
    private static final Path DIRECTORY = Path.of("C:/wvisual/client1_21_11/inventory-presets");
    private static final Path CURRENT_FILE = DIRECTORY.resolve("current.txt");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final Pattern DECORATIVE_BRACKETS =
            Pattern.compile("^\\s*[\\[({<【〖].*?[\\])}>】〗]\\s*");
    private static final Pattern EDGE_DECORATION =
            Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$");

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Map<String, InventoryPreset> presets = new LinkedHashMap<>();
    private final ArrayDeque<ClickStep> clickSteps = new ArrayDeque<>();
    private final Random random = new Random();
    private String activeName;
    private boolean arranging;
    private int arrangeSyncId = -1;
    private int recoverySlot = -1;
    private int missingScroll;
    private int noMovePasses;
    private long nextClickAt;

    public void init() {
        loadAll();
        Client.eventHandler().subscribe(this);
    }

    public List<InventoryPreset> presets() {
        return presets.values().stream()
                .sorted(Comparator.comparingLong(InventoryPreset::createdAt).reversed())
                .toList();
    }

    public Optional<InventoryPreset> activePreset() {
        return Optional.ofNullable(activeName == null ? null : presets.get(key(activeName)));
    }

    public String activeName() {
        return activeName;
    }

    public boolean isArranging() {
        return arranging;
    }

    public int missingScroll(int total, int visible) {
        missingScroll = Math.max(0, Math.min(missingScroll, Math.max(0, total - visible)));
        return missingScroll;
    }

    public void scrollMissing(int direction, int total, int visible) {
        missingScroll = Math.max(0, Math.min(Math.max(0, total - visible), missingScroll + direction));
    }

    public InventoryPreset saveCurrent(String requestedName) {
        if (mc.player == null) return null;
        String name = normalizeName(requestedName);
        if (name.isBlank()) return null;

        List<InventoryPreset.Entry> slots = new ArrayList<>(InventoryPreset.SLOT_COUNT);
        for (int i = 0; i < InventoryPreset.INVENTORY_SLOTS; i++) {
            slots.add(InventoryPreset.Entry.fromStack(mc.player.getInventory().getStack(i)));
        }
        for (EquipmentSlot equipmentSlot : ARMOR) {
            slots.add(InventoryPreset.Entry.fromStack(mc.player.getEquippedStack(equipmentSlot)));
        }
        slots.add(InventoryPreset.Entry.fromStack(mc.player.getOffHandStack()));

        InventoryPreset preset = new InventoryPreset(name, System.currentTimeMillis(), slots);
        presets.put(key(name), preset);
        save(preset);
        return preset;
    }

    public void activate(InventoryPreset preset) {
        if (preset == null) return;
        cancelArrange(false);
        activeName = preset.name();
        missingScroll = 0;
        saveCurrentName();
    }

    public void deactivate() {
        cancelArrange(false);
        activeName = null;
        missingScroll = 0;
        saveCurrentName();
    }

    public void delete(InventoryPreset preset) {
        if (preset == null) return;
        presets.remove(key(preset.name()));
        if (activeName != null && activeName.equalsIgnoreCase(preset.name())) deactivate();
        try {
            Files.deleteIfExists(fileFor(preset.name()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ItemStack currentStack(int logicalSlot) {
        if (mc.player == null) return ItemStack.EMPTY;
        if (logicalSlot >= 0 && logicalSlot < 36) {
            return mc.player.getInventory().getStack(logicalSlot);
        }
        if (logicalSlot >= 36 && logicalSlot < 40) {
            return mc.player.getEquippedStack(ARMOR[logicalSlot - 36]);
        }
        return logicalSlot == InventoryPreset.OFFHAND_SLOT ? mc.player.getOffHandStack() : ItemStack.EMPTY;
    }

    public SlotState stateFor(int logicalSlot) {
        InventoryPreset preset = activePreset().orElse(null);
        if (preset == null) return SlotState.NONE;
        InventoryPreset.Entry expected = preset.slot(logicalSlot);
        if (expected.isEmpty()) return SlotState.CORRECT;
        if (expected.matches(currentStack(logicalSlot))) return SlotState.CORRECT;
        return missingCounts(preset).containsKey(expected.matchKey()) ? SlotState.MISSING : SlotState.WRONG_SLOT;
    }

    public Map<String, MissingItem> missingItems() {
        InventoryPreset preset = activePreset().orElse(null);
        if (preset == null) return Map.of();
        Map<String, Integer> counts = missingCounts(preset);
        Map<String, MissingItem> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            InventoryPreset.Entry source = preset.slots().stream()
                    .filter(slot -> slot.matchKey().equals(entry.getKey()))
                    .findFirst().orElse(InventoryPreset.Entry.EMPTY);
            result.put(entry.getKey(), new MissingItem(source, entry.getValue()));
        }
        return result;
    }

    public boolean isComplete() {
        InventoryPreset preset = activePreset().orElse(null);
        if (preset == null || mc.player == null) return false;
        for (int i = 0; i < InventoryPreset.SLOT_COUNT; i++) {
            InventoryPreset.Entry expected = preset.slot(i);
            if (!expected.isEmpty() && !expected.matches(currentStack(i))) return false;
        }
        return true;
    }

    public boolean startArrange() {
        if (activePreset().isEmpty() || mc.player == null || !(mc.currentScreen instanceof InventoryScreen)) {
            return false;
        }
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            ChatUtils.addChatMessage("Сначала положите предмет с курсора в слот");
            return false;
        }
        clickSteps.clear();
        arranging = true;
        arrangeSyncId = mc.player.currentScreenHandler.syncId;
        recoverySlot = -1;
        noMovePasses = 0;
        nextClickAt = 0L;
        return true;
    }

    public void cancelArrange(boolean notify) {
        if (arranging && notify) ChatUtils.addChatMessage("Раскладка пресета остановлена");
        recoverCursor();
        arranging = false;
        clickSteps.clear();
        arrangeSyncId = -1;
        recoverySlot = -1;
        noMovePasses = 0;
        nextClickAt = 0L;
    }

    public void searchAuction(MissingItem missing) {
        if (mc.player == null || missing == null) return;
        String query = cleanSearchName(missing.entry().displayName());
        if (query.isBlank()) {
            ItemStack fallback = missing.entry().toStack();
            query = fallback.isEmpty() ? missing.entry().itemId() : fallback.getName().getString();
        }
        query = cleanSearchName(query);
        if (!query.isBlank()) mc.player.networkHandler.sendChatCommand("ah search " + query);
    }

    public static String cleanSearchName(String source) {
        if (source == null) return "";
        String value = Formatting.strip(source);
        if (value == null) return "";
        value = DECORATIVE_BRACKETS.matcher(value).replaceFirst("");
        value = value.replaceAll("\\s+", " ").trim();
        String[] words = value.split(" ");
        if (words.length >= 3 && words[0].equalsIgnoreCase(words[words.length - 1])) {
            value = String.join(" ", java.util.Arrays.copyOfRange(words, 1, words.length - 1));
        }
        value = EDGE_DECORATION.matcher(value.trim()).replaceAll("").trim();
        return value.replaceAll("\\s+", " ");
    }

    public static int logicalToScreenSlot(int logical) {
        if (logical >= 0 && logical < 9) return 36 + logical;
        if (logical >= 9 && logical < 36) return logical;
        if (logical >= 36 && logical < 40) return 5 + (logical - 36);
        return logical == InventoryPreset.OFFHAND_SLOT ? 45 : -1;
    }

    public static int screenToLogicalSlot(int screenSlot) {
        if (screenSlot >= 36 && screenSlot <= 44) return screenSlot - 36;
        if (screenSlot >= 9 && screenSlot <= 35) return screenSlot;
        if (screenSlot >= 5 && screenSlot <= 8) return 36 + (screenSlot - 5);
        return screenSlot == 45 ? InventoryPreset.OFFHAND_SLOT : -1;
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (activePreset().isPresent() && !arranging && mc.player != null
                && mc.player.currentScreenHandler.getCursorStack().isEmpty() && isComplete()) {
            activeName = null;
            saveCurrentName();
        }
        if (!arranging) return;
        if (mc.player == null || mc.interactionManager == null || !(mc.currentScreen instanceof InventoryScreen)
                || mc.player.currentScreenHandler.syncId != arrangeSyncId) {
            cancelArrange(true);
            return;
        }
        if (System.currentTimeMillis() < nextClickAt) return;

        if (clickSteps.isEmpty() && !mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            if (!queueCursorRecovery()) {
                nextClickAt = System.currentTimeMillis() + 150L;
            }
            return;
        }

        if (clickSteps.isEmpty() && !planNextMove()) {
            if (isComplete()) {
                arranging = false;
                ChatUtils.addChatMessage("Пресет полностью разложен");
                deactivate();
                return;
            }
            if (++noMovePasses >= 20) {
                arranging = false;
                ChatUtils.addChatMessage("Нет безопасного перемещения: проверьте количество предметов и свободный слот");
            } else {
                nextClickAt = System.currentTimeMillis() + 100L;
            }
            return;
        }

        ClickStep step = clickSteps.peekFirst();
        if (step == null) return;
        if (step.slot() < 0 || step.slot() >= mc.player.currentScreenHandler.slots.size()) {
            clickSteps.clear();
            nextClickAt = System.currentTimeMillis() + 150L;
            return;
        }
        if (!step.matches(mc.player.currentScreenHandler.getCursorStack(),
                mc.player.currentScreenHandler.getSlot(step.slot()).getStack(),
                step.actionType() == SlotActionType.SWAP
                        ? mc.player.currentScreenHandler.getSlot(logicalToScreenSlot(step.button())).getStack()
                        : ItemStack.EMPTY)) {
            clickSteps.clear();
            if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) queueCursorRecovery();
            nextClickAt = System.currentTimeMillis() + 150L;
            return;
        }
        clickSteps.pollFirst();
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, step.slot(), step.button(),
                step.actionType(), mc.player);
        noMovePasses = 0;
        long baseDelay = step.actionType() == SlotActionType.SWAP || step.button() == 1 ? 35L : 65L;
        nextClickAt = System.currentTimeMillis() + baseDelay + random.nextInt(31);
        if (clickSteps.isEmpty() && mc.player.currentScreenHandler.getCursorStack().isEmpty()) recoverySlot = -1;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        cancelArrange(false);
    }

    private boolean planNextMove() {
        InventoryPreset preset = activePreset().orElse(null);
        if (preset == null) return false;

        for (int target = 36; target < 40; target++) {
            if (planTargetIfNeeded(preset, target)) return true;
        }
        if (planTargetIfNeeded(preset, InventoryPreset.OFFHAND_SLOT)) return true;
        for (int target = 0; target < 9; target++) {
            if (planTargetIfNeeded(preset, target)) return true;
        }
        for (int target = 9; target < 36; target++) {
            if (planTargetIfNeeded(preset, target)) return true;
        }
        return false;
    }

    private boolean planTargetIfNeeded(InventoryPreset preset, int target) {
        InventoryPreset.Entry wanted = preset.slot(target);
        ItemStack targetStack = currentStack(target);
        return !wanted.isEmpty()
                && !wanted.matches(targetStack)
                && planTargetMove(preset, wanted, target, targetStack);
    }

    private boolean planTargetMove(InventoryPreset preset, InventoryPreset.Entry wanted,
                                   int target, ItemStack targetStack) {
        int targetScreen = logicalToScreenSlot(target);
        if (targetScreen < 0) return false;

        if (wanted.sameItem(targetStack)) {
            int source = findSource(preset, wanted, target, false);
            if (source < 0) return false;
            ItemStack sourceStack = currentStack(source);
            int sourceScreen = logicalToScreenSlot(source);
            if (sourceScreen < 0) return false;
            if (!ItemStack.areItemsAndComponentsEqual(sourceStack, targetStack)) {
                return queueWholeMove(source, target, sourceStack, targetStack);
            }
            int needed = wanted.count() - targetStack.getCount();
            if (sourceStack.getCount() > needed) {
                return queuePartialMove(sourceScreen, targetScreen, sourceStack, targetStack, needed);
            }
            recoverySlot = sourceScreen;
            StackSnapshot sourceSnapshot = StackSnapshot.of(sourceStack);
            StackSnapshot targetSnapshot = StackSnapshot.of(targetStack);
            addPickup(sourceScreen, StackSnapshot.EMPTY, sourceSnapshot);
            addPickup(targetScreen, sourceSnapshot, targetSnapshot);
            int remainder = Math.max(0,
                    sourceStack.getCount() + targetStack.getCount() - targetStack.getMaxCount());
            if (remainder > 0) {
                addPickup(sourceScreen, sourceSnapshot.withCount(remainder), StackSnapshot.EMPTY);
            }
            return true;
        }

        int source = findSource(preset, wanted, target, true);
        if (source < 0) source = findSource(preset, wanted, target, false);
        if (source < 0) return false;
        int sourceScreen = logicalToScreenSlot(source);
        if (sourceScreen < 0) return false;
        ItemStack sourceStack = currentStack(source);
        if (targetStack.isEmpty() && sourceStack.getCount() > wanted.count()) {
            return queuePartialMove(sourceScreen, targetScreen, sourceStack, targetStack, wanted.count());
        }
        return queueWholeMove(source, target, sourceStack, targetStack);
    }

    private boolean queueWholeMove(int source, int target, ItemStack sourceStack,
                                   ItemStack targetStack) {
        int sourceScreen = logicalToScreenSlot(source);
        int targetScreen = logicalToScreenSlot(target);
        if (sourceScreen < 0 || targetScreen < 0) return false;

        recoverySlot = -1;
        StackSnapshot sourceSnapshot = StackSnapshot.of(sourceStack);
        StackSnapshot targetSnapshot = StackSnapshot.of(targetStack);
        if (target >= 0 && target < 9) {
            addSwap(sourceScreen, target, sourceSnapshot, targetSnapshot);
            return true;
        }
        if (source >= 0 && source < 9) {
            addSwap(targetScreen, source, targetSnapshot, sourceSnapshot);
            return true;
        }

        int buffer = chooseHotbarBuffer();
        if (buffer < 0) return false;
        int bufferScreen = logicalToScreenSlot(buffer);
        StackSnapshot bufferSnapshot = StackSnapshot.of(currentStack(buffer));
        addSwap(sourceScreen, buffer, sourceSnapshot, bufferSnapshot);
        addSwap(targetScreen, buffer, targetSnapshot, sourceSnapshot);
        addSwap(sourceScreen, buffer, bufferSnapshot, targetSnapshot);
        return true;
    }

    private int chooseHotbarBuffer() {
        for (int logical = 0; logical < 9; logical++) {
            if (currentStack(logical).isEmpty()) return logical;
        }
        return 0;
    }

    private boolean queuePartialMove(int sourceScreen, int targetScreen, ItemStack sourceStack,
                                     ItemStack targetStack, int amount) {
        if (amount <= 0 || sourceStack.isEmpty() || sourceStack.getCount() <= amount) return false;
        if (!targetStack.isEmpty() && !ItemStack.areItemsAndComponentsEqual(sourceStack, targetStack)) return false;
        if (targetStack.getCount() + amount > sourceStack.getMaxCount()) return false;

        recoverySlot = sourceScreen;
        StackSnapshot source = StackSnapshot.of(sourceStack);
        addPickup(sourceScreen, StackSnapshot.EMPTY, source);
        for (int moved = 0; moved < amount; moved++) {
            StackSnapshot cursorBefore = source.withCount(sourceStack.getCount() - moved);
            int targetCount = targetStack.getCount() + moved;
            StackSnapshot targetBefore = targetCount == 0
                    ? StackSnapshot.EMPTY
                    : source.withCount(targetCount);
            addPickup(targetScreen, 1, cursorBefore, targetBefore);
        }
        int remainder = sourceStack.getCount() - amount;
        if (remainder > 0) {
            addPickup(sourceScreen, source.withCount(remainder), StackSnapshot.EMPTY);
        }
        return true;
    }

    private int findSource(InventoryPreset preset, InventoryPreset.Entry wanted,
                           int target, boolean requireEnoughCount) {
        for (int logical = 0; logical < InventoryPreset.SLOT_COUNT; logical++) {
            if (logical == target) continue;
            ItemStack stack = currentStack(logical);
            if (!wanted.sameItem(stack)) continue;
            if (requireEnoughCount && stack.getCount() < wanted.count()) continue;
            InventoryPreset.Entry expectedHere = preset.slot(logical);
            if (!expectedHere.isEmpty() && expectedHere.matches(stack)) continue;
            return logical;
        }
        return -1;
    }

    private void addPickup(int slot, StackSnapshot expectedCursor, StackSnapshot expectedSlot) {
        addPickup(slot, 0, expectedCursor, expectedSlot);
    }

    private void addPickup(int slot, int button, StackSnapshot expectedCursor, StackSnapshot expectedSlot) {
        clickSteps.add(new ClickStep(slot, button, SlotActionType.PICKUP,
                expectedCursor, expectedSlot, StackSnapshot.EMPTY));
    }

    private void addSwap(int slot, int hotbarSlot, StackSnapshot expectedSlot, StackSnapshot expectedHotbar) {
        clickSteps.add(new ClickStep(slot, hotbarSlot, SlotActionType.SWAP,
                StackSnapshot.EMPTY, expectedSlot, expectedHotbar));
    }

    private void recoverCursor() {
        if (mc.player == null || mc.interactionManager == null || !(mc.currentScreen instanceof InventoryScreen)
                || mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        int destination = findCursorDestination(mc.player.currentScreenHandler.getCursorStack());
        if (destination >= 0) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, destination, 0,
                    SlotActionType.PICKUP, mc.player);
        }
    }

    private boolean queueCursorRecovery() {
        if (mc.player == null || mc.player.currentScreenHandler.getCursorStack().isEmpty()) return true;
        ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
        int destination = findCursorDestination(cursor);
        if (destination < 0) return false;
        clickSteps.add(new ClickStep(destination, 0, SlotActionType.PICKUP, StackSnapshot.of(cursor),
                StackSnapshot.of(mc.player.currentScreenHandler.getSlot(destination).getStack()),
                StackSnapshot.EMPTY));
        recoverySlot = -1;
        return true;
    }

    private int findCursorDestination(ItemStack cursor) {
        if (recoverySlot >= 0 && recoverySlot < mc.player.currentScreenHandler.slots.size()
                && canPlaceCursor(recoverySlot, cursor)) {
            return recoverySlot;
        }
        for (int logical = 0; logical < 36; logical++) {
            int screenSlot = logicalToScreenSlot(logical);
            if (canPlaceCursor(screenSlot, cursor)) return screenSlot;
        }
        int offhand = logicalToScreenSlot(InventoryPreset.OFFHAND_SLOT);
        return canPlaceCursor(offhand, cursor) ? offhand : -1;
    }

    private boolean canPlaceCursor(int screenSlot, ItemStack cursor) {
        if (screenSlot < 0 || screenSlot >= mc.player.currentScreenHandler.slots.size()) return false;
        var slot = mc.player.currentScreenHandler.getSlot(screenSlot);
        if (!slot.canInsert(cursor)) return false;
        ItemStack existing = slot.getStack();
        return existing.isEmpty()
                || ItemStack.areItemsAndComponentsEqual(existing, cursor)
                && existing.getCount() + cursor.getCount() <= existing.getMaxCount();
    }

    private Map<String, Integer> missingCounts(InventoryPreset preset) {
        Map<String, Integer> required = new LinkedHashMap<>();
        Map<String, Integer> available = new HashMap<>();
        List<InventoryPreset.Entry> variants = new ArrayList<>();
        for (InventoryPreset.Entry entry : preset.slots()) {
            if (!entry.isEmpty()) {
                required.merge(entry.matchKey(), entry.count(), Integer::sum);
                if (variants.stream().noneMatch(existing -> existing.matchKey().equals(entry.matchKey()))) {
                    variants.add(entry);
                }
            }
        }
        for (int i = 0; i < InventoryPreset.SLOT_COUNT; i++) {
            ItemStack stack = currentStack(i);
            if (!stack.isEmpty()) {
                variants.stream()
                        .filter(variant -> variant.sameItem(stack))
                        .findFirst()
                        .ifPresent(variant -> available.merge(variant.matchKey(), stack.getCount(), Integer::sum));
            }
        }
        if (mc.player != null) {
            ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
            if (!cursor.isEmpty()) {
                variants.stream()
                        .filter(variant -> variant.sameItem(cursor))
                        .findFirst()
                        .ifPresent(variant -> available.merge(variant.matchKey(), cursor.getCount(), Integer::sum));
            }
        }
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int deficit = entry.getValue() - available.getOrDefault(entry.getKey(), 0);
            if (deficit > 0) missing.put(entry.getKey(), deficit);
        }
        return missing;
    }

    private void loadAll() {
        presets.clear();
        try {
            Files.createDirectories(DIRECTORY);
            try (var files = Files.list(DIRECTORY)) {
                files.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .forEach(this::load);
            }
            if (Files.exists(CURRENT_FILE)) {
                String selected = Files.readString(CURRENT_FILE, StandardCharsets.UTF_8).trim();
                if (presets.containsKey(key(selected))) activeName = presets.get(key(selected)).name();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void load(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String name = root.has("name") ? root.get("name").getAsString() : path.getFileName().toString();
            long created = root.has("createdAt") ? root.get("createdAt").getAsLong() : 0L;
            List<InventoryPreset.Entry> slots = new ArrayList<>();
            JsonArray array = root.getAsJsonArray("slots");
            if (array != null) {
                for (JsonElement element : array) {
                    JsonObject object = element.getAsJsonObject();
                    String item = object.has("item") ? object.get("item").getAsString() : "";
                    int count = object.has("count") ? object.get("count").getAsInt() : 0;
                    String displayName = object.has("displayName") ? object.get("displayName").getAsString() : "";
                    String potionSignature = object.has("potionSignature")
                            ? object.get("potionSignature").getAsString() : "";
                    if (potionSignature.isBlank() && InventoryPreset.Entry.canHavePotionContents(item)
                            && !displayName.isBlank()) {
                        potionSignature = InventoryPreset.Entry.LEGACY_NAME_PREFIX + displayName;
                    }
                    String potionData = object.has("potionData")
                            ? object.get("potionData").toString() : "";

                    Identifier id = Identifier.tryParse(item);
                    slots.add(id != null && Registries.ITEM.containsId(id)
                            ? new InventoryPreset.Entry(item, count, displayName, potionSignature, potionData)
                            : InventoryPreset.Entry.EMPTY);
                }
            }
            InventoryPreset preset = new InventoryPreset(name, created, slots);
            presets.put(key(name), preset);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void save(InventoryPreset preset) {
        JsonObject root = new JsonObject();
        root.addProperty("name", preset.name());
        root.addProperty("createdAt", preset.createdAt());
        JsonArray slots = new JsonArray();
        for (InventoryPreset.Entry entry : preset.slots()) {
            JsonObject object = new JsonObject();
            object.addProperty("item", entry.itemId());
            object.addProperty("count", entry.count());
            if (!entry.displayName().isBlank()) object.addProperty("displayName", entry.displayName());
            if (!entry.potionSignature().isBlank()) object.addProperty("potionSignature", entry.potionSignature());
            // компонент зелья кладём деревом, а не строкой — так файл остаётся читаемым
            if (!entry.potionData().isBlank()) object.add("potionData", JsonParser.parseString(entry.potionData()));
            slots.add(object);
        }
        root.add("slots", slots);
        try {
            Files.createDirectories(DIRECTORY);
            try (Writer writer = Files.newBufferedWriter(fileFor(preset.name()), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveCurrentName() {
        try {
            Files.createDirectories(DIRECTORY);
            Files.writeString(CURRENT_FILE, activeName == null ? "" : activeName, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Path fileFor(String name) {
        return DIRECTORY.resolve(normalizeName(name).toLowerCase(Locale.ROOT).replace(' ', '_') + ".json");
    }

    private static String normalizeName(String value) {
        if (value == null) return "";
        String result = value.replaceAll("[\\\\/:*?\"<>|]", "").replaceAll("\\s+", " ").trim();
        return result.length() > 32 ? result.substring(0, 32).trim() : result;
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    public enum SlotState {
        NONE, CORRECT, WRONG_SLOT, MISSING
    }

    public record MissingItem(InventoryPreset.Entry entry, int count) {
        public ItemStack stack() {
            return entry.toStack();
        }
    }

    private record ClickStep(int slot, int button, SlotActionType actionType,
                             StackSnapshot expectedCursor, StackSnapshot expectedSlot,
                             StackSnapshot expectedHotbar) {
        private boolean matches(ItemStack cursor, ItemStack slotStack, ItemStack hotbarStack) {
            return expectedCursor.matches(cursor)
                    && expectedSlot.matches(slotStack)
                    && (actionType != SlotActionType.SWAP || expectedHotbar.matches(hotbarStack));
        }
    }

    private record StackSnapshot(String itemId, int count, String components, boolean empty) {
        private static final StackSnapshot EMPTY = new StackSnapshot("", 0, "", true);

        private static StackSnapshot of(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return EMPTY;
            return new StackSnapshot(Registries.ITEM.getId(stack.getItem()).toString(),
                    stack.getCount(), stack.getComponents().toString(), false);
        }

        private StackSnapshot withCount(int newCount) {
            return empty || newCount <= 0 ? EMPTY : new StackSnapshot(itemId, newCount, components, false);
        }

        private boolean matches(ItemStack stack) {
            if (empty) return stack == null || stack.isEmpty();
            if (stack == null || stack.isEmpty() || stack.getCount() != count) return false;
            return itemId.equals(Registries.ITEM.getId(stack.getItem()).toString())
                    && components.equals(stack.getComponents().toString());
        }
    }
}

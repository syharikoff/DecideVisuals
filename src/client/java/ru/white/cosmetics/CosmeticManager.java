package ru.white.cosmetics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import ru.white.cosmetics.render.CosmeticModelLoader;
import ru.white.cosmetics.render.CosmeticSourceModelLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Getter
public class CosmeticManager {
    private static final CosmeticManager INSTANCE = new CosmeticManager();
    private static final Path FILE = Path.of("C:/wvisual/client1_21_11/cosmetics.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static CosmeticManager get() {
        return INSTANCE;
    }

    private final Map<CosmeticCategory, List<CosmeticItem>> items = new EnumMap<>(CosmeticCategory.class);
    private final Map<CosmeticCategory, Integer> equipped = new EnumMap<>(CosmeticCategory.class);

    public CosmeticManager() {
        for (CosmeticCategory cat : CosmeticCategory.values()) {
            equipped.put(cat, -1);
            items.put(cat, new ArrayList<>());
        }
        initItems();
    }

    private void initItems() {
        // WINGS
        addItem(new CosmeticItem(1, CosmeticCategory.WINGS, "Крылья - Aurum"));
        addItem(new CosmeticItem(2, CosmeticCategory.WINGS, "Крылья - Angel"));
        addItem(new CosmeticItem(3, CosmeticCategory.WINGS, "Крылья - Dragon"));
        addItem(new CosmeticItem(4, CosmeticCategory.WINGS, "Крылья - Diamond"));
        addItem(new CosmeticItem(5, CosmeticCategory.WINGS, "Крылья - Spider"));
        addItem(new CosmeticItem(6, CosmeticCategory.WINGS, "Крылья - Easter"));
        addItem(new CosmeticItem(7, CosmeticCategory.WINGS, "Крылья - Fluger"));
        addItem(new CosmeticItem(8, CosmeticCategory.WINGS, "Трезубец - Броя"));
        addItem(new CosmeticItem(9, CosmeticCategory.WINGS, "Крылья - Аквыч"));
        addItem(new CosmeticItem(10, CosmeticCategory.WINGS, "Крылья - Dark"));
        addItem(new CosmeticItem(11, CosmeticCategory.WINGS, "Крылья - Pulse"));
        addItem(new CosmeticItem(12, CosmeticCategory.WINGS, "Крылья - Ghoul"));
        addItem(new CosmeticItem(13, CosmeticCategory.WINGS, "Крылья - Ягуар"));
        // CAPES
        addItem(new CosmeticItem(1, CosmeticCategory.CAPES, "Плащ PulseVisuals"));
        addItem(new CosmeticItem(2, CosmeticCategory.CAPES, "Плащ - Knife"));
        addItem(new CosmeticItem(3, CosmeticCategory.CAPES, "Плащ - Layout"));
        addItem(new CosmeticItem(4, CosmeticCategory.CAPES, "Плащ - Chrome"));
        addItem(new CosmeticItem(5, CosmeticCategory.CAPES, "Плащ - Fluger"));
        addItem(new CosmeticItem(6, CosmeticCategory.CAPES, "Плащ - Fluger V3"));
        addItem(new CosmeticItem(7, CosmeticCategory.CAPES, "Плащ - Fluger V2"));
        addItem(new CosmeticItem(8, CosmeticCategory.CAPES, "Плащ - Броя"));
        addItem(new CosmeticItem(9, CosmeticCategory.CAPES, "Плащ - Aqua"));
        addItem(new CosmeticItem(10, CosmeticCategory.CAPES, "Плащ - Броя V2"));
        addItem(new CosmeticItem(11, CosmeticCategory.CAPES, "Плащ - Броя V3"));
        addItem(new CosmeticItem(12, CosmeticCategory.CAPES, "Плащ - Аквыч V2"));
        addItem(new CosmeticItem(13, CosmeticCategory.CAPES, "Плащ Pulse"));
        addItem(new CosmeticItem(14, CosmeticCategory.CAPES, "Плащ - Ягуар V2"));
        addItem(new CosmeticItem(15, CosmeticCategory.CAPES, "Плащ - Ягуар"));
        addItem(new CosmeticItem(16, CosmeticCategory.CAPES, "Плащ - Аквыч"));
        addItem(new CosmeticItem(17, CosmeticCategory.CAPES, "Плащ - Moonlight"));
        addItem(new CosmeticItem(18, CosmeticCategory.CAPES, "Плащ - Петушок"));
        addItem(new CosmeticItem(19, CosmeticCategory.CAPES, "Плащ - Phantom"));
        addItem(new CosmeticItem(20, CosmeticCategory.CAPES, "Плащ - Sky"));
        addItem(new CosmeticItem(21, CosmeticCategory.CAPES, "Плащ - Братишкин"));
        addItem(new CosmeticItem(22, CosmeticCategory.CAPES, "Плащ - Гама Дрилл"));
        // HATS
        addItem(new CosmeticItem(1, CosmeticCategory.HATS, "Шапка - Armour"));
        addItem(new CosmeticItem(2, CosmeticCategory.HATS, "Шапка - Capybara"));
        addItem(new CosmeticItem(3, CosmeticCategory.HATS, "Шапка - Chicken"));
        addItem(new CosmeticItem(4, CosmeticCategory.HATS, "Шапка - Pepe"));
        addItem(new CosmeticItem(5, CosmeticCategory.HATS, "Шапка - Sheep"));
        addItem(new CosmeticItem(6, CosmeticCategory.HATS, "Шапка - Fluger"));
        addItem(new CosmeticItem(7, CosmeticCategory.HATS, "Маска - Bro9I"));
        addItem(new CosmeticItem(8, CosmeticCategory.HATS, "Шапка - Аквыч"));
        addItem(new CosmeticItem(9, CosmeticCategory.HATS, "Шапка - Bear"));
        addItem(new CosmeticItem(10, CosmeticCategory.HATS, "Шапка - ПеПе"));
        addItem(new CosmeticItem(11, CosmeticCategory.HATS, "Шапка - Happy"));
        addItem(new CosmeticItem(12, CosmeticCategory.HATS, "Нимб"));
        addItem(new CosmeticItem(13, CosmeticCategory.HATS, "Шапка - Pulse"));
        // CLOTHES
        addItem(new CosmeticItem(1, CosmeticCategory.CLOTHES, "Рюкзак - Louis Vuitton"));
        addItem(new CosmeticItem(2, CosmeticCategory.CLOTHES, "Рюкзак - Gucci"));
        addItem(new CosmeticItem(3, CosmeticCategory.CLOTHES, "Рюкзак - Adidas"));
        addItem(new CosmeticItem(4, CosmeticCategory.CLOTHES, "Рюкзак - Nike"));
        addItem(new CosmeticItem(5, CosmeticCategory.CLOTHES, "Рюкзак - Supreme"));
        addItem(new CosmeticItem(6, CosmeticCategory.CLOTHES, "Рюкзак - Vlone"));
        addItem(new CosmeticItem(7, CosmeticCategory.CLOTHES, "Рюкзак - Bape"));
        addItem(new CosmeticItem(8, CosmeticCategory.CLOTHES, "Рюкзак - Balenciaga"));
        addItem(new CosmeticItem(9, CosmeticCategory.CLOTHES, "Рюкзак - Chrome Hearts"));
        addItem(new CosmeticItem(10, CosmeticCategory.CLOTHES, "Рюкзак - PePe"));
        addItem(new CosmeticItem(11, CosmeticCategory.CLOTHES, "Катана - ila_studio"));
        // PETS
        addItem(new CosmeticItem(1, CosmeticCategory.PETS, "Питомец - Angel"));
        addItem(new CosmeticItem(2, CosmeticCategory.PETS, "Питомец - Demon"));
        addItem(new CosmeticItem(3, CosmeticCategory.PETS, "Питомец - Buddy"));
        addItem(new CosmeticItem(4, CosmeticCategory.PETS, "Питомец - Fluger Door"));
        addItem(new CosmeticItem(5, CosmeticCategory.PETS, "Питомец - Diamond"));
        addItem(new CosmeticItem(6, CosmeticCategory.PETS, "Питомец - Аквыч"));
        addItem(new CosmeticItem(7, CosmeticCategory.PETS, "Питомец - Bee"));
        addItem(new CosmeticItem(8, CosmeticCategory.PETS, "Питомец - 1 Год"));
        addItem(new CosmeticItem(9, CosmeticCategory.PETS, "Питомец - Ягуар"));
        addItem(new CosmeticItem(10, CosmeticCategory.PETS, "Питомец - Cergifff"));
        addItem(new CosmeticItem(11, CosmeticCategory.PETS, "Питомец - Ila_Studio"));
        addItem(new CosmeticItem(12, CosmeticCategory.PETS, "Питомец - Creeper"));
        // GRAFFITI
        addItem(new CosmeticItem(1, CosmeticCategory.GRAFFITI, "Граффити - Петух"));
        addItem(new CosmeticItem(2, CosmeticCategory.GRAFFITI, "Граффити - 67"));
        addItem(new CosmeticItem(3, CosmeticCategory.GRAFFITI, "Граффити - Сова на Скакалке"));
        addItem(new CosmeticItem(4, CosmeticCategory.GRAFFITI, "Граффити - Чиназес"));
        addItem(new CosmeticItem(5, CosmeticCategory.GRAFFITI, "Граффити - Doble R"));
        addItem(new CosmeticItem(6, CosmeticCategory.GRAFFITI, "Граффити - EZ"));
        addItem(new CosmeticItem(7, CosmeticCategory.GRAFFITI, "Граффити - Kawai"));
        addItem(new CosmeticItem(8, CosmeticCategory.GRAFFITI, "Граффити - iба чьотко"));
        addItem(new CosmeticItem(9, CosmeticCategory.GRAFFITI, "Граффити - Homelander"));
        addItem(new CosmeticItem(10, CosmeticCategory.GRAFFITI, "Граффити - Мяу"));
        addItem(new CosmeticItem(11, CosmeticCategory.GRAFFITI, "Граффити - Sigma"));
        addItem(new CosmeticItem(12, CosmeticCategory.GRAFFITI, "Граффити - W"));
        addItem(new CosmeticItem(13, CosmeticCategory.GRAFFITI, "Граффити - ?"));
        addItem(new CosmeticItem(14, CosmeticCategory.GRAFFITI, "Граффити - Меч"));
        addItem(new CosmeticItem(15, CosmeticCategory.GRAFFITI, "Граффити - Корона"));
        addItem(new CosmeticItem(16, CosmeticCategory.GRAFFITI, "Граффити - Призрак"));
        addItem(new CosmeticItem(17, CosmeticCategory.GRAFFITI, "Граффити - New Pulse"));
        addItem(new CosmeticItem(18, CosmeticCategory.GRAFFITI, "Граффити - Old Pulse"));
        addItem(new CosmeticItem(19, CosmeticCategory.GRAFFITI, "Граффити - +РЭП"));
        addItem(new CosmeticItem(20, CosmeticCategory.GRAFFITI, "Граффити - 5 СЕК"));
    }

    private void addItem(CosmeticItem item) {
        items.get(item.getCategory()).add(item);
    }

    public List<CosmeticItem> getItems(CosmeticCategory category) {
        return items.getOrDefault(category, Collections.emptyList());
    }

    public CosmeticItem getItem(CosmeticCategory category, int id) {
        List<CosmeticItem> list = getItems(category);
        for (CosmeticItem item : list) {
            if (item.getId() == id) {
                return item;
            }
        }
        return null;
    }

    public boolean isEquipped(CosmeticCategory category, int id) {
        Integer cur = equipped.get(category);
        return cur != null && cur == id;
    }

    public void toggleEquip(CosmeticCategory category, int id) {
        setEquipped(category, isEquipped(category, id) ? -1 : id);
    }

    public int getEquipped(CosmeticCategory category) {
        return equipped.getOrDefault(category, -1);
    }

    public void setEquipped(CosmeticCategory category, int id) {
        equipped.put(category, id);
        save();
    }

    // ── Сохранение ────────────────────────────────────────────────────────────

    /** Загружает выбор из диска. Вызывается один раз при старте клиента. */
    public void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = new InputStreamReader(new FileInputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (!root.has("equipped") || !root.get("equipped").isJsonObject()) return;

            JsonObject saved = root.getAsJsonObject("equipped");
            for (CosmeticCategory cat : CosmeticCategory.values()) {
                if (!saved.has(cat.name())) continue;
                int id = saved.get(cat.name()).getAsInt();
                // id вне диапазона категории — мусор в конфиге, игнорируем
                equipped.put(cat, id > 0 && id <= cat.getCount() ? id : -1);
            }
        } catch (Exception e) {
            System.err.println("[NightixCosmetics] Failed to load cosmetics: " + e);
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        JsonObject saved = new JsonObject();
        for (Map.Entry<CosmeticCategory, Integer> entry : equipped.entrySet()) {
            saved.addProperty(entry.getKey().name(), entry.getValue());
        }
        root.add("equipped", saved);

        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
        } catch (IOException e) {
            System.err.println("[NightixCosmetics] Failed to save cosmetics: " + e);
        }
    }

    // ── Прогрев моделей ───────────────────────────────────────────────────────

    /**
     * Фоновая загрузка моделей и текстур, чтобы первый кадр с косметикой не дёргался.
     * Работает вне рендер-потока, кэш в лоадере потокобезопасный.
     */
    public void warmUpAsync() {
        for (CosmeticCategory cat : new CosmeticCategory[]{
                CosmeticCategory.WINGS, CosmeticCategory.CAPES,
                CosmeticCategory.HATS, CosmeticCategory.CLOTHES, CosmeticCategory.PETS}) {
            for (int id = 1; id <= cat.getCount(); id++) {
                final CosmeticCategory category = cat;
                final int itemId = id;
                WARMER.execute(() -> {
                    if (category == CosmeticCategory.CAPES) {
                        CosmeticModelLoader.warmCape(itemId);
                    } else {
                        CosmeticSourceModelLoader.get(category, itemId);
                    }
                });
            }
        }
    }

    private static final java.util.concurrent.ExecutorService WARMER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "nightix-cosmetics-warmup");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
}

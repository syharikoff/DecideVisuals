package ru.white.alt;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import ru.white.mixin.MinecraftClientAccessor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Хранилище и применение аккаунтов. Список лежит в alts.json рядом с конфигами,
 * формат аналогичен остальным cfg клиента.
 */
public class AltManager {

    private static final Path FILE = Path.of("C:/wvisual/client1_21_11/alts.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static AltManager instance;

    private final List<Account> accounts = new ArrayList<>();
    private String activeName = null;   // последний применённый аккаунт
    private boolean booted = false;     // одноразовое применение при запуске

    public static AltManager get() {
        if (instance == null) {
            instance = new AltManager();
            instance.load();
        }
        return instance;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public boolean exists(String name) {
        return accounts.stream().anyMatch(a -> a.name.equalsIgnoreCase(name));
    }

    public Account add(String name) {
        name = name == null ? "" : name.trim();
        if (name.isEmpty() || exists(name)) return null;
        Account account = new Account(name);
        accounts.add(account);
        save();
        return account;
    }

    /** Случайный валидный ник, которого ещё нет в списке. */
    public String randomName() {
        return randomName("");
    }

    /**
     * Ник вида {база}+{4..12 случайных букв/цифр}, уникальный и не длиннее 16 символов.
     * База очищается до допустимых символов (буквы/цифры/_).
     */
    public String randomName(String base) {
        base = base == null ? "" : base.trim();
        StringBuilder cleaned = new StringBuilder();
        for (char c : base.toCharArray()) if (Character.isLetterOrDigit(c) || c == '_') cleaned.append(c);
        if (cleaned.length() > 12) cleaned.setLength(12);
        base = cleaned.toString();

        final String pool = "abcdefghijklmnopqrstuvwxyz0123456789";
        java.util.Random r = new java.util.Random();
        for (int attempt = 0; attempt < 60; attempt++) {
            int maxLen = Math.max(1, Math.min(12, 16 - base.length()));
            int minLen = Math.min(4, maxLen);
            int n = minLen + r.nextInt(maxLen - minLen + 1);
            StringBuilder sb = new StringBuilder(base);
            for (int i = 0; i < n; i++) sb.append(pool.charAt(r.nextInt(pool.length())));
            String name = sb.toString();
            if (!name.isEmpty() && name.length() <= 16 && !exists(name)) return name;
        }
        return (base.isEmpty() ? "Player" : base) + r.nextInt(10000);
    }

    public void remove(Account account) {
        if (accounts.remove(account)) save();
    }

    public void toggleFavorite(Account account) {
        if (account == null) return;
        account.favorite = !account.favorite;
        save();
    }

    /** Список с избранными сверху (порядок внутри групп сохраняется). */
    public List<Account> getSorted() {
        List<Account> sorted = new ArrayList<>();
        for (Account a : accounts) if (a.favorite) sorted.add(a);
        for (Account a : accounts) if (!a.favorite) sorted.add(a);
        return sorted;
    }

    /** Применяет аккаунт к текущей сессии Minecraft (оффлайн-логин) и запоминает его как активный. */
    public void login(Account account) {
        if (account == null) return;
        applySession(account);
        activeName = account.name;
        save();
    }

    private void applySession(Account account) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        Session session = new Session(
                account.name,
                account.offlineUuid(),
                "0",                  // accessToken — для оффлайна не используется
                Optional.empty(),     // xuid
                Optional.empty()      // clientId
        );
        ((MinecraftClientAccessor) mc).setSession(session);
    }

    /**
     * Одноразовое применение сохранённого активного аккаунта при запуске.
     * Вызывается, когда клиент уже готов (есть сессия). Если ещё рано — тихо ждёт следующего вызова.
     */
    public void bootstrap() {
        if (booted) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSession() == null) return; // ещё рано — попробуем позже
        booted = true;
        if (activeName == null) return;
        for (Account a : accounts) {
            if (a.name.equalsIgnoreCase(activeName)) {
                applySession(a);
                return;
            }
        }
    }

    /** Сейчас активный ник (из текущей сессии). */
    public String currentName() {
        return MinecraftClient.getInstance().getSession().getUsername();
    }

    // ── persistence ───────────────────────────────────────────────────────────

    public void save() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Account a : accounts) {
            JsonObject o = new JsonObject();
            o.addProperty("name", a.name);
            o.addProperty("type", a.type.name());
            o.addProperty("created", a.created);
            o.addProperty("favorite", a.favorite);
            arr.add(o);
        }
        root.add("accounts", arr);
        if (activeName != null) root.addProperty("active", activeName);

        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        accounts.clear();
        if (!Files.exists(FILE)) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("active")) activeName = root.get("active").getAsString();
            if (!root.has("accounts")) return;
            for (JsonElement el : root.getAsJsonArray("accounts")) {
                JsonObject o = el.getAsJsonObject();
                String name = o.has("name") ? o.get("name").getAsString() : null;
                if (name == null || name.isBlank()) continue;
                Account.Type type = Account.Type.OFFLINE;
                if (o.has("type")) {
                    try { type = Account.Type.valueOf(o.get("type").getAsString()); } catch (IllegalArgumentException ignored) {}
                }
                long created = o.has("created") ? o.get("created").getAsLong() : System.currentTimeMillis();
                Account acc = new Account(name, type, created);
                if (o.has("favorite")) acc.favorite = o.get("favorite").getAsBoolean();
                accounts.add(acc);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

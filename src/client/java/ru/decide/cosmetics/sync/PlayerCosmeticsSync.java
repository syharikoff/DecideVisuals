/*
 * Decompiled with CFR 0.152.
 */
package ru.decide.cosmetics.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.decide.cosmetics.CosmeticManager;
import ru.decide.cosmetics.model.CosmeticModel;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;

@Environment(value=EnvType.CLIENT)
public class PlayerCosmeticsSync {
    /**
     * РЎРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ РєРѕСЃРјРµС‚РёРєРё СЃ Р±СЌРєРµРЅРґРѕРј Lexora: РЅР°СЂСѓР¶Сѓ РѕС‚РїСЂР°РІР»СЏРµС‚СЃСЏ РЅРёРє Рё СЃРїРёСЃРѕРє
     * РЅР°РґРµС‚С‹С… РєРѕСЃРјРµС‚РёРє (СЂР°Р· РІ 15СЃ), РѕС‚ СЃРµСЂРІРµСЂР° Р·Р°Р±РёСЂР°СЋС‚СЃСЏ РєРѕСЃРјРµС‚РёРєРё РґСЂСѓРіРёС… РёРіСЂРѕРєРѕРІ.
     * Р•СЃР»Рё Р±СЌРєРµРЅРґ РЅРµ РЅСѓР¶РµРЅ - РїРѕСЃС‚Р°РІСЊ false, С‚РѕРіРґР° С‡СѓР¶РёРµ РёРіСЂРѕРєРё РїСЂРѕСЃС‚Рѕ РЅРёС‡РµРіРѕ РЅРµ РїРѕРєР°Р¶СѓС‚.
     */
    public static final boolean SYNC_ENABLED = true;

    private static final String API_BASE = "https://lexoravisuals.fun/irc/api/cosmetics";
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "DecideVisual-CosmeticsSync");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, PlayerData> CACHE = new ConcurrentHashMap<String, PlayerData>();
    private static final Set<String> PENDING_FETCH = ConcurrentHashMap.newKeySet();
    private static volatile String currentServer = "";
    private static volatile String lastKnownIgn = "";
    private static volatile long lastHeartbeatTime = 0L;
    private static volatile long lastActivePollTime = 0L;
    private static volatile boolean needInitialSync = true;
    private static final long HEARTBEAT_INTERVAL_MS = 15000L;
    private static final long ACTIVE_POLL_INTERVAL_MS = 2500L;
    private static final long CACHE_TTL_MS = 15000L;
    private static final long NEGATIVE_CACHE_TTL_MS = 5000L;

    public static List<CosmeticModel> getEquippedModels(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return Collections.emptyList();
        }
        String clean = PlayerCosmeticsSync.normalizeName(playerName);
        if (clean.isEmpty()) {
            return Collections.emptyList();
        }
        PlayerData data = CACHE.get(clean);
        long now = System.currentTimeMillis();
        if (data != null) {
            long ttl;
            long l = ttl = data.exists ? 15000L : 5000L;
            if (now - data.timestamp > ttl && !PENDING_FETCH.contains(clean)) {
                PlayerCosmeticsSync.triggerFetch(clean, playerName);
            }
            return data.models;
        }
        if (!PENDING_FETCH.contains(clean)) {
            PlayerCosmeticsSync.triggerFetch(clean, playerName);
        }
        return Collections.emptyList();
    }

    public static Identifier getCapeTexture(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        String clean = PlayerCosmeticsSync.normalizeName(playerName);
        if (clean.isEmpty()) {
            return null;
        }
        PlayerData data = CACHE.get(clean);
        long now = System.currentTimeMillis();
        if (data != null) {
            long ttl;
            long l = ttl = data.exists ? 15000L : 5000L;
            if (now - data.timestamp > ttl && !PENDING_FETCH.contains(clean)) {
                PlayerCosmeticsSync.triggerFetch(clean, playerName);
            }
            return data.capeTexture;
        }
        if (!PENDING_FETCH.contains(clean)) {
            PlayerCosmeticsSync.triggerFetch(clean, playerName);
        }
        return null;
    }

    public static void onClientTick(MinecraftClient client) {
        String ign;
        if (client == null || client.world == null || client.player == null) {
            return;
        }
        String server = PlayerCosmeticsSync.resolveServer(client);
        String string = ign = client.player.getName().getString();
        if (needInitialSync || !server.equals(currentServer) || !ign.equals(lastKnownIgn)) {
            needInitialSync = false;
            currentServer = server;
            lastKnownIgn = ign;
            lastHeartbeatTime = System.currentTimeMillis();
            lastActivePollTime = System.currentTimeMillis();
            CACHE.clear();
            PENDING_FETCH.clear();
            PlayerCosmeticsSync.sendSyncAsync(server, ign);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastHeartbeatTime >= 15000L) {
            lastHeartbeatTime = now;
            PlayerCosmeticsSync.sendSyncAsync(server, ign);
        }
        if (now - lastActivePollTime >= 2500L) {
            lastActivePollTime = now;
            PlayerCosmeticsSync.pollActivePlayersAsync(server);
        }
    }

    public static void onJoinServer(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }
        String server = PlayerCosmeticsSync.resolveServer(client);
        String ign = client.player.getName().getString();
        currentServer = server;
        lastKnownIgn = ign;
        lastHeartbeatTime = System.currentTimeMillis();
        lastActivePollTime = System.currentTimeMillis();
        CACHE.clear();
        PENDING_FETCH.clear();
        PlayerCosmeticsSync.sendSyncAsync(server, ign);
    }

    public static void onDisconnect() {
        needInitialSync = true;
        String srv = currentServer;
        String ign = lastKnownIgn;
        if (!srv.isEmpty() && !ign.isEmpty()) {
            PlayerCosmeticsSync.sendLeaveAsync(srv, ign);
        }
        CACHE.clear();
        PENDING_FETCH.clear();
        currentServer = "";
        lastKnownIgn = "";
    }

    public static void onCosmeticsChanged() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            String ign;
            String server = currentServer.isEmpty() ? PlayerCosmeticsSync.resolveServer(client) : currentServer;
            String string = ign = client.player.getName().getString();
            if (!server.isEmpty() && !ign.isEmpty()) {
                currentServer = server;
                lastKnownIgn = ign;
                PlayerCosmeticsSync.sendSyncAsync(server, ign);
            }
        }
    }

    private static String resolveServer(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null) {
            return PlayerCosmeticsSync.normalizeServer(client.getCurrentServerEntry().address);
        }
        return "singleplayer";
    }

    private static void triggerFetch(String clean, String originalName) {
        if (!SYNC_ENABLED || clean.isEmpty() || currentServer.isEmpty()) {
            return;
        }
        PENDING_FETCH.add(clean);
        EXECUTOR.submit(() -> {
            try {
                PlayerCosmeticsSync.fetchPlayerCosmetics(currentServer, clean, originalName);
            }
            catch (Exception e) {
                CACHE.put(clean, new PlayerData(false, 0L, Collections.emptyList(), null));
            }
            finally {
                PENDING_FETCH.remove(clean);
            }
        });
    }

    private static void fetchPlayerCosmetics(String server, String clean, String originalName) {
        try {
            PlayerData globalData;
            PlayerData data = PlayerCosmeticsSync.queryCosmetics(server, clean, originalName);
            if (data != null && data.exists) {
                CACHE.put(clean, data);
                System.out.println("[Cosmetics Sync] Loaded cosmetics for " + originalName + " from server " + server + " (v" + data.version + ", " + data.models.size() + " models, cape=" + (data.capeTexture != null) + ")");
                return;
            }
            if (!"global".equalsIgnoreCase(server) && (globalData = PlayerCosmeticsSync.queryCosmetics("global", clean, originalName)) != null && globalData.exists) {
                CACHE.put(clean, globalData);
                System.out.println("[Cosmetics Sync] Loaded cosmetics for " + originalName + " from global (v" + globalData.version + ", " + globalData.models.size() + " models, cape=" + (globalData.capeTexture != null) + ")");
                return;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        CACHE.put(clean, new PlayerData(false, 0L, Collections.emptyList(), null));
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private static PlayerData queryCosmetics(String server, String clean, String originalName) {
        try {
            String urlStr = "https://lexoravisuals.fun/irc/api/cosmetics/get?server=" + URLEncoder.encode(server, StandardCharsets.UTF_8) + "&ign=" + URLEncoder.encode(originalName, StandardCharsets.UTF_8);
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "DecideVisual/1.0");
            int code = conn.getResponseCode();
            if (code != 200) return null;
            try (InputStream in = conn.getInputStream();){
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString((String)json).getAsJsonObject();
                if (!obj.has("found")) return null;
                if (!obj.get("found").getAsBoolean()) return null;
                long version = obj.has("version") ? obj.get("version").getAsLong() : 1L;
                ArrayList<CosmeticModel> models = new ArrayList<CosmeticModel>();
                if (obj.has("cosmetics") && obj.get("cosmetics").isJsonObject()) {
                    JsonObject cos = obj.getAsJsonObject("cosmetics");
                    for (Map.Entry entry : cos.entrySet()) {
                        if ("cape".equalsIgnoreCase((String)entry.getKey()) || !((JsonElement)entry.getValue()).isJsonPrimitive()) continue;
                        int modelIdx = ((JsonElement)entry.getValue()).getAsInt();
                        CosmeticModel model = CosmeticManager.getInstance().getModel(modelIdx);
                        if (model == null) continue;
                        models.add(model);
                    }
                }
                Identifier capeTex = null;
                if (obj.has("cape") && !obj.get("cape").isJsonNull()) {
                    int capeIdx = obj.get("cape").getAsInt();
                    capeTex = CosmeticManager.getInstance().getCapeTexture(capeIdx);
                }
                PlayerData playerData = new PlayerData(true, version, models, capeTex);
                return playerData;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return null;
    }

    private static void pollActivePlayersAsync(String server) {
        if (!SYNC_ENABLED) return;
        EXECUTOR.submit(() -> {
            block13: {
                try {
                    String urlStr = "https://lexoravisuals.fun/irc/api/cosmetics/active?server=" + URLEncoder.encode(server, StandardCharsets.UTF_8);
                    URL url = URI.create(urlStr).toURL();
                    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.setRequestProperty("User-Agent", "DecideVisual/1.0");
                    int code = conn.getResponseCode();
                    if (code != 200) break block13;
                    try (InputStream in = conn.getInputStream();){
                        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        JsonObject obj = JsonParser.parseString((String)json).getAsJsonObject();
                        if (!obj.has("active")) break block13;
                        HashSet<String> activeSet = new HashSet<String>();
                        if (obj.get("active").isJsonObject()) {
                            JsonObject activeObj = obj.getAsJsonObject("active");
                            for (Map.Entry entry : activeObj.entrySet()) {
                                String activeClean = PlayerCosmeticsSync.normalizeName((String)entry.getKey());
                                activeSet.add(activeClean);
                                long serverVer = ((JsonElement)entry.getValue()).isJsonPrimitive() ? ((JsonElement)entry.getValue()).getAsLong() : 0L;
                                PlayerData cached = CACHE.get(activeClean);
                                if (cached != null && (cached.version == serverVer || PENDING_FETCH.contains(activeClean))) continue;
                                PlayerCosmeticsSync.triggerFetch(activeClean, (String)entry.getKey());
                            }
                        } else if (obj.get("active").isJsonArray()) {
                            for (JsonElement el : obj.getAsJsonArray("active")) {
                                if (!el.isJsonPrimitive()) continue;
                                String activeName = el.getAsString();
                                String activeClean = PlayerCosmeticsSync.normalizeName(activeName);
                                activeSet.add(activeClean);
                                if (CACHE.containsKey(activeClean) || PENDING_FETCH.contains(activeClean)) continue;
                                PlayerCosmeticsSync.triggerFetch(activeClean, activeName);
                            }
                        }
                    }
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        });
    }

    private static void sendSyncAsync(String server, String ign) {
        if (!SYNC_ENABLED) return;
        EXECUTOR.submit(() -> {
            try {
                CosmeticManager mgr = CosmeticManager.getInstance();
                Map<String, Integer> equipped = mgr.getSelectedByType();
                JsonObject root = new JsonObject();
                root.addProperty("server", server);
                root.addProperty("ign", ign);
                JsonObject cosObj = new JsonObject();
                Integer capeVal = null;
                for (Map.Entry<String, Integer> entry : equipped.entrySet()) {
                    if ("cape".equalsIgnoreCase(entry.getKey())) {
                        if (!mgr.isCustomCapeEnabled()) continue;
                        capeVal = entry.getValue();
                        continue;
                    }
                    cosObj.addProperty(entry.getKey(), (Number)entry.getValue());
                }
                root.add("cosmetics", (JsonElement)cosObj);
                if (capeVal != null) {
                    root.addProperty("cape", capeVal);
                } else {
                    root.add("cape", (JsonElement)JsonNull.INSTANCE);
                }
                PlayerCosmeticsSync.postJson("https://lexoravisuals.fun/irc/api/cosmetics/sync", root.toString());
                if (!"global".equalsIgnoreCase(server)) {
                    JsonObject globalRoot = root.deepCopy();
                    globalRoot.addProperty("server", "global");
                    PlayerCosmeticsSync.postJson("https://lexoravisuals.fun/irc/api/cosmetics/sync", globalRoot.toString());
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        });
    }

    private static void sendLeaveAsync(String server, String ign) {
        if (!SYNC_ENABLED) return;
        EXECUTOR.submit(() -> {
            try {
                JsonObject root = new JsonObject();
                root.addProperty("server", server);
                root.addProperty("ign", ign);
                PlayerCosmeticsSync.postJson("https://lexoravisuals.fun/irc/api/cosmetics/leave", root.toString());
                if (!"global".equalsIgnoreCase(server)) {
                    JsonObject globalRoot = root.deepCopy();
                    globalRoot.addProperty("server", "global");
                    PlayerCosmeticsSync.postJson("https://lexoravisuals.fun/irc/api/cosmetics/leave", globalRoot.toString());
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        });
    }

    private static void postJson(String urlStr, String jsonBody) {
        try {
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", "DecideVisual/1.0");
            byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream();){
                out.write(bytes);
            }
            conn.getResponseCode();
            conn.disconnect();
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public static String normalizeServer(String value) {
        int colonIdx;
        if (value == null || value.isBlank()) {
            return "singleplayer";
        }
        String server = value.trim().toLowerCase();
        int slashIdx = (server = server.replaceFirst("^https?://", "").replaceFirst("^wss?://", "")).indexOf(47);
        if (slashIdx >= 0) {
            server = server.substring(0, slashIdx);
        }
        if ((colonIdx = server.indexOf(58)) >= 0) {
            server = server.substring(0, colonIdx);
        }
        if (server.endsWith(".")) {
            server = server.substring(0, server.length() - 1);
        }
        if (server.contains("funtime") || server.contains("fun-time")) {
            return "funtime";
        }
        if (server.contains("spookytime") || server.contains("spooky")) {
            return "spookytime";
        }
        if (server.contains("dexland")) {
            return "dexland";
        }
        if (server.contains("holyworld") || server.contains("howorld")) {
            return "holyworld";
        }
        if (server.contains("reallyworld")) {
            return "reallyworld";
        }
        if (server.contains("mineblaze")) {
            return "mineblaze";
        }
        if (server.contains("prostocraft")) {
            return "prostocraft";
        }
        if (server.contains("hypixel")) {
            return "hypixel";
        }
        if (server.contains("vimeworld")) {
            return "vimeworld";
        }
        if (server.contains("gommehd")) {
            return "gommehd";
        }
        String[] prefixes = new String[]{"mc.", "play.", "join.", "go.", "connect.", "server.", "bedrock.", "msk.", "msk1.", "msk2.", "ru.", "s1.", "s2.", "s3.", "hub.", "lobby.", "link.", "proxy.", "anarchy.", "grief.", "duels.", "survival.", "bungee.", "eu.", "us."};
        boolean changed = true;
        block0: while (changed) {
            changed = false;
            for (String p : prefixes) {
                if (!server.startsWith(p)) continue;
                server = server.substring(p.length());
                changed = true;
                continue block0;
            }
        }
        String[] parts = server.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2];
        }
        return server.isEmpty() ? "unknown" : server;
    }

    public static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase().replaceAll("[^a-z0-9_\u0400-\u04ff]", "");
    }

    @Environment(value=EnvType.CLIENT)
    public static class PlayerData {
        public final boolean exists;
        public final long timestamp;
        public final long version;
        public final List<CosmeticModel> models;
        public final Identifier capeTexture;

        public PlayerData(boolean exists, long version, List<CosmeticModel> models, Identifier capeTexture) {
            this.exists = exists;
            this.timestamp = System.currentTimeMillis();
            this.version = version;
            this.models = models != null ? Collections.unmodifiableList(models) : Collections.emptyList();
            this.capeTexture = capeTexture;
        }
    }
}


package ru.white.cosmetics;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.white.cosmetics.render.CosmeticModelLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Р“СЂР°С„С„РёС‚Рё вЂ” РЅР°РєР»РµР№РєРё РЅР° РіСЂР°РЅСЏС… Р±Р»РѕРєРѕРІ.
 * <p>
 * РЈРїСЂР°РІР»РµРЅРёРµ: <b>Ctrl + РџРљРњ</b> РїРѕ РіСЂР°РЅРё Р±Р»РѕРєР° СЃС‚Р°РІРёС‚ РЅР°РґРµС‚РѕРµ РіСЂР°С„С„РёС‚Рё, РџРљРњ РїРѕ СѓР¶Рµ СЃРІРѕРµРјСѓ
 * РіСЂР°С„С„РёС‚Рё вЂ” СѓР±РёСЂР°РµС‚. Р‘РµР· Ctrl РѕР±С‹С‡РЅРѕРµ РІР·Р°РёРјРѕРґРµР№СЃС‚РІРёРµ СЃ Р±Р»РѕРєРѕРј РЅРµ Р»РѕРјР°РµС‚СЃСЏ.
 * <p>
 * Р”Р°РЅРЅС‹Рµ Р»РµР¶Р°С‚ Р»РѕРєР°Р»СЊРЅРѕ Рё РїСЂРёРІСЏР·Р°РЅС‹ Рє Р°РґСЂРµСЃСѓ СЃРµСЂРІРµСЂР° Рё РёР·РјРµСЂРµРЅРёСЋ, РїРѕСЌС‚РѕРјСѓ РЅР° РґСЂСѓРіРѕРј
 * СЃРµСЂРІРµСЂРµ С‡СѓР¶РёРµ РЅР°РєР»РµР№РєРё РЅРµ РїРѕСЏРІСЏС‚СЃСЏ.
 */
public final class GraffitiManager {
    private static final Path FILE = Path.of("C:/wvisual/client1_21_11/graffiti.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final double RENDER_DISTANCE_SQ = 9216.0;
    private static final int MAX_RENDERED_PER_FRAME = 256;
    private static final long INTERACTION_COOLDOWN_NANOS = 300_000_000L;

    private static final Map<String, PlacedGraffiti> PLACED = new LinkedHashMap<>();
    private static String lastInteraction = "";
    private static long lastInteractionNanos;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(
                context -> renderWorld(context.matrices(), context.consumers()));
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = new InputStreamReader(new FileInputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
            JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
            synchronized (PLACED) {
                PLACED.clear();
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    try {
                        Direction face = Direction.valueOf(o.get("f").getAsString().toUpperCase(Locale.ROOT));
                        int itemId = o.get("i").getAsInt();
                        if (itemId <= 0) continue;
                        PlacedGraffiti placed = new PlacedGraffiti(
                                o.get("s").getAsString(),
                                o.get("d").getAsString(),
                                new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()),
                                face,
                                itemId);
                        PLACED.put(key(placed.serverKey, placed.dimension, placed.pos, placed.face), placed);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[NightixCosmetics] Failed to load graffiti: " + e);
        }
    }

    public static void save() {
        JsonArray arr = new JsonArray();
        synchronized (PLACED) {
            for (PlacedGraffiti placed : PLACED.values()) {
                JsonObject o = new JsonObject();
                o.addProperty("s", placed.serverKey);
                o.addProperty("d", placed.dimension);
                o.addProperty("x", placed.pos.getX());
                o.addProperty("y", placed.pos.getY());
                o.addProperty("z", placed.pos.getZ());
                o.addProperty("f", placed.face.asString());
                o.addProperty("i", placed.itemId);
                arr.add(o);
            }
        }

        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(arr, w);
            }
        } catch (IOException e) {
            System.err.println("[NightixCosmetics] Failed to save graffiti: " + e);
        }
    }

    // в”Ђв”Ђ РЈСЃС‚Р°РЅРѕРІРєР° в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    /** @return true, РµСЃР»Рё РґРµР№СЃС‚РІРёРµ Р±С‹Р»Рѕ РїРµСЂРµС…РІР°С‡РµРЅРѕ Рё РІР°РЅРёР»СЊРЅРѕРµ РІР·Р°РёРјРѕРґРµР№СЃС‚РІРёРµ РЅСѓР¶РЅРѕ РѕС‚РјРµРЅРёС‚СЊ */
    public static boolean handleUse(MinecraftClient client) {
        int itemId = CosmeticManager.get().getEquipped(CosmeticCategory.GRAFFITI);
        if (itemId <= 0 || client.world == null || client.player == null) {
            return false;
        }
        if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        String serverKey = serverKey(client);
        String dimension = client.world.getRegistryKey().getValue().toString();
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getSide();
        String surfaceKey = key(serverKey, dimension, pos, face);

        long now = System.nanoTime();
        if (surfaceKey.equals(lastInteraction) && now - lastInteractionNanos < INTERACTION_COOLDOWN_NANOS) {
            return true;
        }
        lastInteraction = surfaceKey;
        lastInteractionNanos = now;

        PlacedGraffiti existing;
        synchronized (PLACED) {
            existing = PLACED.get(surfaceKey);
        }

        if (existing != null) {
            if (existing.itemId == itemId) {
                synchronized (PLACED) {
                    PLACED.remove(surfaceKey);
                }
                notify(client, "В§cР“СЂР°С„С„РёС‚Рё СѓРґР°Р»РµРЅРѕ");
            } else {
                synchronized (PLACED) {
                    PLACED.put(surfaceKey, new PlacedGraffiti(serverKey, dimension, pos, face, itemId));
                }
                notify(client, "В§aР“СЂР°С„С„РёС‚Рё Р·Р°РјРµРЅРµРЅРѕ");
            }
        } else {
            synchronized (PLACED) {
                PLACED.put(surfaceKey, new PlacedGraffiti(serverKey, dimension, pos, face, itemId));
            }
            notify(client, "В§aР“СЂР°С„С„РёС‚Рё СѓСЃС‚Р°РЅРѕРІР»РµРЅРѕ");
        }

        save();
        return true;
    }

    // в”Ђв”Ђ Р РµРЅРґРµСЂ в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    public static void renderWorld(MatrixStack matrices, VertexConsumerProvider providers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }

        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
        String serverKey = serverKey(client);
        String dimension = client.world.getRegistryKey().getValue().toString();

        int rendered = 0;
        List<PlacedGraffiti> snapshot;
        synchronized (PLACED) {
            snapshot = new ArrayList<>(PLACED.values());
        }

        for (PlacedGraffiti placed : snapshot) {
            if (!placed.serverKey.equals(serverKey) || !placed.dimension.equals(dimension)) {
                continue;
            }
            double dx = placed.pos.getX() + 0.5 - client.player.getX();
            double dy = placed.pos.getY() + 0.5 - client.player.getY();
            double dz = placed.pos.getZ() + 0.5 - client.player.getZ();
            if (dx * dx + dy * dy + dz * dz > RENDER_DISTANCE_SQ) {
                continue;
            }
            render(placed.itemId, placed.pos, placed.face, matrices, providers, camera, 255,
                    WorldRenderer.getLightmapCoordinates(client.world, placed.pos));
            if (++rendered >= MAX_RENDERED_PER_FRAME) {
                break;
            }
        }

        // РџРѕР»СѓРїСЂРѕР·СЂР°С‡РЅС‹Р№ РїСЂРµРІСЊСЋ РЅР° РіСЂР°РЅРё, РЅР° РєРѕС‚РѕСЂСѓСЋ СЃРјРѕС‚СЂРёС‚ РёРіСЂРѕРє
        int previewId = CosmeticManager.get().getEquipped(CosmeticCategory.GRAFFITI);
        if (previewId > 0 && client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            boolean occupied;
            synchronized (PLACED) {
                occupied = PLACED.containsKey(key(serverKey, dimension, hit.getBlockPos(), hit.getSide()));
            }
            if (!occupied) {
                render(previewId, hit.getBlockPos(), hit.getSide(), matrices, providers, camera, 128,
                        WorldRenderer.getLightmapCoordinates(client.world, hit.getBlockPos()));
            }
        }
    }

    private static void render(int itemId, BlockPos pos, Direction face, MatrixStack matrices,
                               VertexConsumerProvider providers, Vec3d camera, int alpha, int light) {
        var texture = CosmeticModelLoader.getTextureId(CosmeticCategory.GRAFFITI, itemId);
        int[] size = ru.white.cosmetics.render.CosmeticTextureInfo.size(texture);
        if (size[0] <= 0 || size[1] <= 0) {
            return;
        }

        float aspect = (float) size[0] / size[1];
        float width = aspect >= 1.0F ? 0.9F : 0.9F * aspect;
        float height = aspect >= 1.0F ? 0.9F / aspect : 0.9F;
        float cx = pos.getX() + 0.5F;
        float cy = pos.getY() + 0.5F;
        float cz = pos.getZ() + 0.5F;
        final float offset = 0.01F;

        // РљР°СЃР°С‚РµР»СЊРЅР°СЏ Рє РіСЂР°РЅРё (РѕСЃСЊ U) Рё РІРµСЂС‚РёРєР°Р»СЊ (РѕСЃСЊ V)
        float[] u = new float[3];
        float[] v = {0.0F, 1.0F, 0.0F};
        switch (face) {
            case NORTH -> {
                cz -= 0.5F + offset;
                u[0] = 1.0F;
            }
            case SOUTH -> {
                cz += 0.5F + offset;
                u[0] = -1.0F;
            }
            case EAST -> {
                cx += 0.5F + offset;
                u[2] = -1.0F;
            }
            case WEST -> {
                cx -= 0.5F + offset;
                u[2] = 1.0F;
            }
            case UP -> {
                cy += 0.5F + offset;
                u[0] = 1.0F;
                v = new float[]{0.0F, 0.0F, -1.0F};
            }
            case DOWN -> {
                cy -= 0.5F + offset;
                u[0] = 1.0F;
                v = new float[]{0.0F, 0.0F, 1.0F};
            }
            default -> {
            }
        }

        float hu = width / 2.0F;
        float hv = height / 2.0F;
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = providers.getBuffer(RenderLayers.entityTranslucent(texture));

        // РўРµРєСЃС‚СѓСЂР° РїРµСЂРµРІС‘СЂРЅСѓС‚Р° РїРѕ V, РїРѕСЌС‚РѕРјСѓ UV РёРґСѓС‚ РѕС‚ (0,1) РІРЅРёР·
        vertex(vc, m, cx - u[0] * hu - v[0] * hv, cy - u[1] * hu - v[1] * hv, cz - u[2] * hu - v[2] * hv, 0.0F, 1.0F, alpha, light);
        vertex(vc, m, cx + u[0] * hu - v[0] * hv, cy + u[1] * hu - v[1] * hv, cz + u[2] * hu - v[2] * hv, 1.0F, 1.0F, alpha, light);
        vertex(vc, m, cx + u[0] * hu + v[0] * hv, cy + u[1] * hu + v[1] * hv, cz + u[2] * hu + v[2] * hv, 1.0F, 0.0F, alpha, light);
        vertex(vc, m, cx - u[0] * hu + v[0] * hv, cy - u[1] * hu + v[1] * hv, cz - u[2] * hu + v[2] * hv, 0.0F, 0.0F, alpha, light);

        matrices.pop();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v,
                               int alpha, int light) {
        vc.vertex(m, x, y, z)
                .color(255, 255, 255, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(0.0F, 1.0F, 0.0F);
    }

    // в”Ђв”Ђ РЎР»СѓР¶РµР±РЅРѕРµ в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    private static String serverKey(MinecraftClient client) {
        ServerInfo entry = client.getCurrentServerEntry();
        return entry != null && entry.address != null ? entry.address : "singleplayer";
    }

    private static String key(String serverKey, String dimension, BlockPos pos, Direction face) {
        return serverKey + "|" + dimension + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ() + "|" + face.asString();
    }

    private static void notify(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    private record PlacedGraffiti(String serverKey, String dimension, BlockPos pos, Direction face, int itemId) {
    }

    private GraffitiManager() {
    }
}

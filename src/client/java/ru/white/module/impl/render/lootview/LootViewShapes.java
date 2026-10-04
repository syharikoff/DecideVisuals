package ru.white.module.impl.render.lootview;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.ColorHelper;
import ru.white.mixin.ItemLayerRenderStateAccessor;
import ru.white.mixin.ItemRenderStateAccessor;
import ru.white.mixin.SpriteContentsAccessor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Строит силуэт выброшенного предмета: гоняет его модель через ItemModelManager,
 * берёт спрайты квадов, сжимает их пиксели в сетку 28x28 и раскидывает иглы.
 *
 * Силуэт получается настоящим — иглы повторяют форму предмета, а не просто
 * торчат из хитбокса. Кэш по ключу (предмет + цвет зелья/краски + плотность).
 */
public final class LootViewShapes {

    private static final int GRID = 28;
    private static final float CELL_SIZE = 0.017857144f;
    private static final float HALF = 14.0f;

    private static final Map<Long, LootShape> cache = new HashMap<>();

    private LootViewShapes() {
    }

    public static LootShape get(ItemStack stack, int points) {
        long key = cacheKey(stack, points);
        LootShape cached = cache.get(key);
        if (cached != null) return cached;

        LootShape shape = null;
        try {
            shape = build(stack, points);
        } catch (RuntimeException ignored) {
            shape = null;
        }
        if (shape == null) shape = fallback(points, -1);

        cache.put(key, shape);
        return shape;
    }

    public static void clear() {
        cache.clear();
    }

    private static long cacheKey(ItemStack stack, int points) {
        PotionContentsComponent potion = stack.get(DataComponentTypes.POTION_CONTENTS);
        DyedColorComponent dyed = stack.get(DataComponentTypes.DYED_COLOR);

        int disc = potion != null ? potion.getColor() : 0;
        disc = disc * 31 + (dyed != null ? dyed.rgb() : 0);

        return (long) System.identityHashCode(stack.getItem()) * -7046029254386353131L
                ^ ((long) disc & 0xFFFFFFFFL) << 8
                ^ (long) points;
    }

    private static LootShape build(ItemStack stack, int points) {
        MinecraftClient mc = MinecraftClient.getInstance();

        ItemRenderState state = new ItemRenderState();
        mc.getItemModelManager().clearAndUpdate(state, stack, ItemDisplayContext.GROUND, mc.world, null, 0);

        ItemRenderStateAccessor stateAccessor = (ItemRenderStateAccessor) (Object) state;
        ItemRenderState.LayerRenderState[] layers = stateAccessor.nightix$layers();
        if (layers == null) return null;
        int layerCount = Math.min(stateAccessor.nightix$layerCount(), layers.length);

        boolean[] mask = new boolean[GRID * GRID];
        float[] bucketW = new float[4096];
        float[] bucketR = new float[4096];
        float[] bucketG = new float[4096];
        float[] bucketB = new float[4096];

        Set<Sprite> seenSprites = new HashSet<>();
        boolean sampled = false;

        for (int i = 0; i < layerCount; i++) {
            ItemRenderState.LayerRenderState layer = layers[i];
            if (layer == null) continue;

            ItemLayerRenderStateAccessor layerAccessor = (ItemLayerRenderStateAccessor) (Object) layer;
            // спец-рендереры (посох/зачарованная книга) квадов не дают — пропускаем
            if (layerAccessor.nightix$specialModelType() != null) continue;

            List<BakedQuad> quads = layer.getQuads();
            if (quads == null || quads.isEmpty()) continue;

            int[] tints = layerAccessor.nightix$tints();

            for (BakedQuad quad : quads) {
                Sprite sprite = quad.sprite();
                if (sprite == null || !seenSprites.add(sprite)) continue;

                int tintIndex = quad.tintIndex();
                int tint = tints != null && tintIndex >= 0 && tintIndex < tints.length ? tints[tintIndex] : -1;

                if (!sampleSprite(sprite, tint, mask, bucketW, bucketR, bucketG, bucketB)) continue;
                sampled = true;
            }
        }

        if (!sampled) return null;
        return scatter(mask, points, dominantColor(bucketW, bucketR, bucketG, bucketB));
    }

    private static LootShape scatter(boolean[] mask, int points, int color) {
        List<Integer> cells = new ArrayList<>(196);
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                if (!mask[y * GRID + x]) continue;
                cells.add(x << 8 | y);
            }
        }
        if (cells.size() < 3) return null;

        // детерминированный разброс по хешу ячейки, чтобы иглы не жались к краям
        cells.sort((a, b) -> {
            int ax = a >> 8, ay = a & 0xFF;
            int bx = b >> 8, by = b & 0xFF;
            int h1 = ax * 73856093 ^ ay * 19349663 ^ (ax + 7) * (ay + 13) * 83492791;
            int h2 = bx * 73856093 ^ by * 19349663 ^ (bx + 7) * (by + 13) * 83492791;
            return Integer.compare(h1, h2);
        });

        int count = Math.min(points, cells.size());
        float[] xs = new float[count];
        float[] zs = new float[count];
        float[] jitter = new float[count];

        for (int k = 0; k < count; k++) {
            int cell = cells.get(k * cells.size() / count);
            int x = cell >> 8;
            int y = cell & 0xFF;
            xs[k] = (x + 0.5f - HALF) * CELL_SIZE;
            zs[k] = (27.5f - y - HALF) * CELL_SIZE;
            jitter[k] = 0.78f + 0.44f * fract((float) Math.sin(k * 12.9898f) * 43758.547f);
        }
        return new LootShape(xs, zs, jitter, color, count);
    }

    private static boolean sampleSprite(Sprite sprite, int tint, boolean[] mask,
                                       float[] bucketW, float[] bucketR, float[] bucketG, float[] bucketB) {
        SpriteContents contents = sprite.getContents();
        SpriteContentsAccessor contentsAccessor = (SpriteContentsAccessor) (Object) contents;
        NativeImage image = contentsAccessor.nightix$image();
        if (image == null) return false;

        int frameW = Math.min(contentsAccessor.nightix$width(), image.getWidth());
        int frameH = Math.min(contentsAccessor.nightix$height(), image.getHeight());
        if (frameW <= 0 || frameH <= 0) return false;

        int tintR = tint == -1 ? 255 : ColorHelper.getRed(tint);
        int tintG = tint == -1 ? 255 : ColorHelper.getGreen(tint);
        int tintB = tint == -1 ? 255 : ColorHelper.getBlue(tint);

        boolean any = false;
        for (int gy = 0; gy < GRID; gy++) {
            int sy = gy * frameH / GRID;
            for (int gx = 0; gx < GRID; gx++) {
                int sx = gx * frameW / GRID;
                int pixel = image.getColorArgb(sx, sy);
                int alpha = ColorHelper.getAlpha(pixel);
                if (alpha < 40) continue;

                any = true;
                if (alpha >= 96) mask[gy * GRID + gx] = true;

                int r = ColorHelper.getRed(pixel) * tintR / 255;
                int g = ColorHelper.getGreen(pixel) * tintG / 255;
                int b = ColorHelper.getBlue(pixel) * tintB / 255;

                int maxC = Math.max(r, Math.max(g, b));
                int minC = Math.min(r, Math.min(g, b));
                float saturation = maxC > 0 ? (float) (maxC - minC) / maxC : 0.0f;
                float weight = alpha / 255.0f * (0.15f + saturation) * (0.2f + 0.8f * maxC / 255.0f);
                if (weight <= 0.0f) continue;

                // гистограмма по квантам 4 бита — ищем доминирующий цвет предмета
                int bucket = r >> 4 << 8 | g >> 4 << 4 | b >> 4;
                bucketW[bucket] += weight;
                bucketR[bucket] += r * weight;
                bucketG[bucket] += g * weight;
                bucketB[bucket] += b * weight;
            }
        }
        return any;
    }

    private static int dominantColor(float[] bucketW, float[] bucketR, float[] bucketG, float[] bucketB) {
        int best = -1;
        float bestW = 0.0f;
        for (int i = 0; i < 4096; i++) {
            if (!(bucketW[i] > bestW)) continue;
            bestW = bucketW[i];
            best = i;
        }
        if (best < 0 || bestW <= 0.0f) return -1;

        int r = clamp255((int) (bucketR[best] / bestW));
        int g = clamp255((int) (bucketG[best] / bestW));
        int b = clamp255((int) (bucketB[best] / bestW));

        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        float saturation = Math.min(1.0f, hsb[1] * 1.2f);
        float brightness = Math.max(0.55f, hsb[2]);
        return Color.HSBtoRGB(hsb[0], saturation, brightness) | 0xFF000000;
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /** Раскладывает иглы по кругу, если модель предмета отрисовать не удалось. */
    private static LootShape fallback(int points, int color) {
        float[] xs = new float[points];
        float[] zs = new float[points];
        float[] jitter = new float[points];

        for (int i = 0; i < points; i++) {
            float angle = (float) Math.PI * 2 * i / points;
            xs[i] = (float) Math.cos(angle) * 0.5f * 0.4f;
            zs[i] = (float) Math.sin(angle) * 0.5f * 0.4f;
            jitter[i] = 0.78f + 0.44f * fract((float) Math.sin(i * 12.9898f) * 43758.547f);
        }
        return new LootShape(xs, zs, jitter, color, points);
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }
}

package ru.white.cosmetics.render;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Метаданные текстур косметики.
 * <p>
 * Размеры читаются из PNG-заголовка (быстро, без декодирования), аcontent-bounds —
 * один раз через {@link NativeImage}, потому что плащи рисуются как призма с UV-окном,
 * которое надо вырезать по реальному содержимому текстуры.
 */
public final class CosmeticTextureInfo {
    private static final int ALPHA_THRESHOLD = 24;
    private static final int[] UNKNOWN = {0, 0};
    private static final float[] FULL = {0.0F, 0.0F, 1.0F, 1.0F};
    private static final Map<Identifier, int[]> SIZES = new ConcurrentHashMap<>();
    private static final Map<Identifier, float[]> BOUNDS = new ConcurrentHashMap<>();

    public static int width(Identifier id) {
        return size(id)[0];
    }

    public static int height(Identifier id) {
        return size(id)[1];
    }

    public static int[] size(Identifier id) {
        return SIZES.computeIfAbsent(id, CosmeticTextureInfo::readSize);
    }

    /**
     * Нормализованные границы непрозрачной части текстуры: {u0, v0, u1, v1}.
     * null, если прочитать текстуру не удалось.
     */
    public static float[] contentBounds(Identifier id) {
        return BOUNDS.computeIfAbsent(id, CosmeticTextureInfo::readBounds);
    }

    private static int[] readSize(Identifier id) {
        try (InputStream in = open(id)) {
            if (in == null) {
                return UNKNOWN;
            }
            byte[] header = in.readNBytes(24);
            if (header.length < 24) {
                return UNKNOWN;
            }
            int width = readInt(header, 16);
            int height = readInt(header, 20);
            if (width <= 0 || height <= 0) {
                return UNKNOWN;
            }
            return new int[]{width, height};
        } catch (IOException | RuntimeException e) {
            return UNKNOWN;
        }
    }

    private static float[] readBounds(Identifier id) {
        int[] size = size(id);
        if (size[0] <= 0 || size[1] <= 0) {
            return null;
        }

        try (InputStream in = open(id)) {
            if (in == null) {
                return null;
            }
            byte[] data = in.readAllBytes();
            try (NativeImage image = NativeImage.read(data)) {
                int w = image.getWidth();
                int h = image.getHeight();
                int minX = w;
                int maxX = -1;
                int minY = h;
                int maxY = -1;
                // Шаг больше 1: точная граница не нужна, важно не дёргать по каждому пикселю
                int step = Math.max(1, Math.min(w, h) / 256);
                for (int y = 0; y < h; y += step) {
                    for (int x = 0; x < w; x += step) {
                        if ((image.getColorArgb(x, y) >>> 24) > ALPHA_THRESHOLD) {
                            if (x < minX) minX = x;
                            if (x > maxX) maxX = x;
                            if (y < minY) minY = y;
                            if (y > maxY) maxY = y;
                        }
                    }
                }
                if (maxX < minX || maxY < minY) {
                    return FULL;
                }
                return new float[]{
                        minX / (float) w,
                        minY / (float) h,
                        (maxX + 1) / (float) w,
                        (maxY + 1) / (float) h
                };
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static InputStream open(Identifier id) {
        return CosmeticTextureInfo.class.getClassLoader()
                .getResourceAsStream("assets/" + id.getNamespace() + "/" + id.getPath());
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private CosmeticTextureInfo() {
    }
}

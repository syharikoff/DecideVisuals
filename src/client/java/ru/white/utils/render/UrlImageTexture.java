package ru.white.utils.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UrlImageTexture {

    private static final Map<String, UrlImageTexture> CACHE = new ConcurrentHashMap<>();

    private final String url;
    private final Identifier id;
    private volatile boolean loading = false;
    private volatile boolean loaded = false;
    private volatile boolean failed = false;
    private int width = 0;
    private int height = 0;

    private UrlImageTexture(String url) {
        this.url = url;
        this.id = Identifier.of("white", "url_images/" + Integer.toHexString(url.hashCode()));
    }

    public static UrlImageTexture get(String url) {
        return CACHE.computeIfAbsent(url, UrlImageTexture::new);
    }

    public static Identifier getIdentifier(String url) {
        UrlImageTexture texture = get(url);
        texture.loadAsync();
        return texture.isLoaded() ? texture.id : null;
    }

    public void loadAsync() {
        if (loaded || loading || failed) return;
        loading = true;

        Thread thread = new Thread(() -> {
            try {
                if (!isSupportedImageUrl(url)) {
                    failed = true;
                    return;
                }

                URL target = URI.create(url).toURL();
                HttpURLConnection connection = (HttpURLConnection) target.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "WhiteClient/1.0");

                try (var stream = connection.getInputStream()) {
                    BufferedImage image = ImageIO.read(stream);
                    if (image == null) {
                        failed = true;
                        return;
                    }

                    int uploadWidth = image.getWidth();
                    int uploadHeight = image.getHeight();
                    int[] pixels = image.getRGB(0, 0, uploadWidth, uploadHeight, null, 0, uploadWidth);

                    MinecraftClient.getInstance().execute(() -> upload(uploadWidth, uploadHeight, pixels));
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ignored) {
                failed = true;
            } finally {
                loading = false;
            }
        }, "url-image-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void upload(int uploadWidth, int uploadHeight, int[] pixels) {
        if (loaded) return;

        NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, uploadWidth, uploadHeight, false);
        for (int y = 0; y < uploadHeight; y++) {
            for (int x = 0; x < uploadWidth; x++) {
                nativeImage.setColorArgb(x, y, pixels[y * uploadWidth + x]);
            }
        }

        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), nativeImage);
        texture.upload();
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);

        width = uploadWidth;
        height = uploadHeight;
        loaded = true;
        failed = false;
    }

    private static boolean isSupportedImageUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    public Identifier getId() {
        loadAsync();
        return loaded ? id : null;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isFailed() {
        return failed;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void destroy() {
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().getTextureManager().destroyTexture(id));
        loaded = false;
        failed = false;
    }

    public static void clearCache() {
        CACHE.values().forEach(UrlImageTexture::destroy);
        CACHE.clear();
    }
}

package ru.white.utils.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

public final class MediaTextureUtil {

    public static final Identifier ARTWORK_ID = Identifier.of("client", "media/artwork");

    private static byte[] lastArtwork = new byte[0];
    private static boolean registered;

    private MediaTextureUtil() {
    }

    public static void updateArtwork(byte[] png) {
        if (png == null) {
            png = new byte[0];
        }
        if (Arrays.equals(png, lastArtwork)) {
            return;
        }
        lastArtwork = png.clone();

        if (png.length == 0) {
            registered = false;
            return;
        }

        byte[] finalPng = png;
        MinecraftClient.getInstance().execute(() -> upload(finalPng));
    }

    private static void upload(byte[] png) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                registered = false;
                return;
            }

            int w = image.getWidth();
            int h = image.getHeight();
            NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    nativeImage.setColorArgb(x, y, image.getRGB(x, y));
                }
            }

            NativeImageBackedTexture backed = new NativeImageBackedTexture(() -> ARTWORK_ID.toString(), nativeImage);
            backed.upload();
            MinecraftClient.getInstance().getTextureManager().registerTexture(ARTWORK_ID, backed);
            registered = true;
        } catch (Exception ignored) {
            registered = false;
        }
    }

    public static boolean hasArtwork() {
        return registered;
    }

    public static void reset() {
        lastArtwork = new byte[0];
        registered = false;
    }
}

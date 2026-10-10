package ru.decide.utils.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GifTexture {

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "gif-loader");
        thread.setDaemon(true);
        return thread;
    });

    private final List<Identifier> frameIds  = new ArrayList<>();
    private final List<Integer>    delays     = new ArrayList<>();
    private int   totalDuration = 0;
    private long  startTime     = -1;
    private volatile boolean loaded = false;
    private volatile boolean failed = false;
    private int width = 0, height = 0;

    public GifTexture(InputStream stream) {
        try {
            byte[] data = stream.readAllBytes();
            LOADER.submit(() -> load(new ByteArrayInputStream(data)));
        } catch (Exception e) {
            System.err.println("[GifTexture] Failed to queue GIF: " + e);
        }
    }

    private void load(InputStream stream) {
        try {
            ImageInputStream iis = ImageIO.createImageInputStream(stream);
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                System.err.println("[GifTexture] No GIF ImageReader available");
                return;
            }

            ImageReader reader = readers.next();
            reader.setInput(iis, false);

            int frameCount = reader.getNumImages(true);
            String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            // GIF metadata for full canvas size
            IIOMetadataNode globalMeta = null;
            try {
                globalMeta = (IIOMetadataNode) reader.getStreamMetadata()
                        .getAsTree(reader.getStreamMetadata().getNativeMetadataFormatName());
            } catch (Exception ignored) {}

            // Determine canvas size from first frame
            BufferedImage firstFrame = reader.read(0);
            int canvasW = firstFrame.getWidth();
            int canvasH = firstFrame.getHeight();
            if (globalMeta != null) {
                IIOMetadataNode ld = findNode(globalMeta, "LogicalScreenDescriptor");
                if (ld != null) {
                    try { canvasW = Integer.parseInt(ld.getAttribute("logicalScreenWidth"));  } catch (Exception ignored) {}
                    try { canvasH = Integer.parseInt(ld.getAttribute("logicalScreenHeight")); } catch (Exception ignored) {}
                }
            }
            width  = canvasW;
            height = canvasH;

            List<int[]>   pixelData = new ArrayList<>();
            BufferedImage canvas    = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            BufferedImage prevCanvas = null;

            for (int i = 0; i < frameCount; i++) {
                BufferedImage rawFrame = (i == 0) ? firstFrame : reader.read(i);
                IIOMetadataNode frameMeta = null;
                try {
                    frameMeta = (IIOMetadataNode) reader.getImageMetadata(i)
                            .getAsTree(reader.getImageMetadata(i).getNativeMetadataFormatName());
                } catch (Exception ignored) {}

                // Frame offset inside canvas
                int fx = 0, fy = 0;
                String disposal = "none";
                if (frameMeta != null) {
                    IIOMetadataNode id = findNode(frameMeta, "ImageDescriptor");
                    if (id != null) {
                        try { fx = Integer.parseInt(id.getAttribute("imageLeftPosition")); } catch (Exception ignored) {}
                        try { fy = Integer.parseInt(id.getAttribute("imageTopPosition"));  } catch (Exception ignored) {}
                    }
                    IIOMetadataNode gce = findNode(frameMeta, "GraphicControlExtension");
                    if (gce != null) disposal = gce.getAttribute("disposalMethod");
                }

                // Save canvas state before drawing if we might need to restore it
                if ("restoreToPrevious".equalsIgnoreCase(disposal)) {
                    prevCanvas = copyImage(canvas);
                }

                // Draw raw frame onto canvas at offset
                java.awt.Graphics2D g = canvas.createGraphics();
                g.drawImage(rawFrame, fx, fy, null);
                g.dispose();

                // Store composited canvas pixels
                pixelData.add(canvas.getRGB(0, 0, canvasW, canvasH, null, 0, canvasW));
                delays.add(readDelay(reader, i));
                frameIds.add(Identifier.of("decide", "gif/" + uid + "/" + i));

                // Apply disposal method for next frame
                java.awt.Graphics2D gc = canvas.createGraphics();
                gc.setComposite(java.awt.AlphaComposite.Clear);
                if ("restoreToBackgroundColor".equalsIgnoreCase(disposal)) {
                    gc.fillRect(fx, fy, rawFrame.getWidth(), rawFrame.getHeight());
                } else if ("restoreToPrevious".equalsIgnoreCase(disposal) && prevCanvas != null) {
                    gc.setComposite(java.awt.AlphaComposite.Src);
                    gc.drawImage(prevCanvas, 0, 0, null);
                }
                gc.dispose();
            }

            reader.dispose();
            for (int d : delays) totalDuration += d;

            // Upload to GPU on render thread
            final int uploadW = canvasW;
            final int uploadH = canvasH;
            MinecraftClient.getInstance().execute(() -> {
                for (int i = 0; i < pixelData.size(); i++) {
                    int[] px = pixelData.get(i);

                    NativeImage img = new NativeImage(NativeImage.Format.RGBA, uploadW, uploadH, false);
                    int visiblePixels = 0;
                    for (int y = 0; y < uploadH; y++) {
                        for (int x = 0; x < uploadW; x++) {
                            int argb = px[y * uploadW + x];
                            if ((argb >>> 24) != 0 && (argb & 0x00FFFFFF) != 0) visiblePixels++;
                            img.setColorArgb(x, y, argb);
                        }
                    }

                    final int idx = i;
                    NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "white:gif_" + uid + "_" + idx, img);
                    tex.upload();
                    MinecraftClient.getInstance().getTextureManager().registerTexture(frameIds.get(idx), tex);
                    if (idx == 0) {
                        System.err.println("[GifTexture] GIF READY size=" + uploadW + "x" + uploadH
                                + " frames=" + pixelData.size() + " visiblePixels=" + visiblePixels);
                    }
                }
                loaded = true;
            });

        } catch (Exception e) {
            failed = true;
            System.err.println("[GifTexture] GIF LOAD FAILED: " + e);
            e.printStackTrace();
        }
    }

    private int readDelay(ImageReader reader, int index) {
        try {
            IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(index)
                    .getAsTree(reader.getImageMetadata(index).getNativeMetadataFormatName());
            IIOMetadataNode gce = findNode(root, "GraphicControlExtension");
            if (gce != null) {
                int cs = Integer.parseInt(gce.getAttribute("delayTime"));
                return Math.max(cs * 10, 20);
            }
        } catch (Exception ignored) {}
        return 100;
    }

    private IIOMetadataNode findNode(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name))
                return (IIOMetadataNode) root.item(i);
            if (root.item(i) instanceof IIOMetadataNode child) {
                IIOMetadataNode found = findNode(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    public Identifier currentFrame() {
        if (!loaded || frameIds.isEmpty()) return null;
        if (frameIds.size() == 1) return frameIds.get(0);
        if (startTime == -1) startTime = System.currentTimeMillis();

        long elapsed = (System.currentTimeMillis() - startTime) % totalDuration;
        int acc = 0;
        for (int i = 0; i < frameIds.size(); i++) {
            acc += delays.get(i);
            if (elapsed < acc) return frameIds.get(i);
        }
        return frameIds.get(frameIds.size() - 1);
    }

    public void draw(DrawContext ctx, int x, int y, int w, int h) {
        draw(ctx, x, y, w, h, 1f);
    }

    public void draw(DrawContext ctx, int x, int y, int w, int h, float alpha) {
        int a = (int)(Math.min(Math.max(alpha, 0f), 1f) * 255) & 0xFF;
        draw(ctx, x, y, w, h, (a << 24) | 0x00FFFFFF);
    }

    public void draw(DrawContext ctx, int x, int y, int w, int h, int color) {
        Identifier frame = currentFrame();
        if (frame == null) return;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, frame, x, y, 0f, 0f, w, h, w, h, color);
    }

    public void reset()           { startTime = System.currentTimeMillis(); }
    public boolean isLoaded()     { return loaded; }
    public boolean isFailed()     { return failed; }
    public int getWidth()         { return width; }
    public int getHeight()        { return height; }

    public void destroy() {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<Identifier> copy = new ArrayList<>(frameIds);
        mc.execute(() -> copy.forEach(id -> mc.getTextureManager().destroyTexture(id)));
        frameIds.clear();
        delays.clear();
        loaded = false;
    }
}

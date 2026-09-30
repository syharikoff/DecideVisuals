package ru.white.utils.render.gif;

import net.minecraft.client.texture.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import net.minecraft.resource.ResourceManager;
import org.w3c.dom.Node;
import ru.white.utils.render.Draw;

public final class GifRenderer {
    private static final Map<String, GifAnimation> CACHE = new HashMap<>();
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "decidevisuals-gif-loader");
        t.setDaemon(true);
        return t;
    });

    private GifRenderer() {}

    public static void shutdown() {
        LOADER.shutdownNow();
        for (GifAnimation a : CACHE.values()) a.dispose();
        CACHE.clear();
    }

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static IIOMetadataNode findNode(IIOMetadataNode node, String name) {
        for (int i = 0; i < node.getLength(); i++) {
            if (node.item(i).getNodeName().equalsIgnoreCase(name))
                return (IIOMetadataNode) node.item(i);
        }
        for (int i = 0; i < node.getLength(); i++) {
            Node n = node.item(i);
            if (n instanceof IIOMetadataNode child) {
                IIOMetadataNode found = findNode(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static BufferedImage copyImage(BufferedImage src) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    public static void preload(String path) {
        CACHE.computeIfAbsent(path, k -> {
            GifAnimation a = new GifAnimation();
            a.startLoad(k);
            return a;
        });
    }

    public static void draw(float x, float y, float w, float h, float radius, String path, float alpha) {
        GifAnimation anim = CACHE.computeIfAbsent(path, k -> {
            GifAnimation a = new GifAnimation();
            a.startLoad(k);
            return a;
        });
        if (anim.state == State.GPU_READY) {
            String frameId = anim.getCurrentFrameId();
            if (frameId != null) {
                int a = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
                Identifier id = Identifier.tryParse(frameId);
                if (id != null) {
                    Draw.texture(id, x, y, w, h, a << 24 | 0xFFFFFF);
                }
            }
        }
    }

    public static final class GifAnimation {
        volatile State state = State.IDLE;
        final List<BufferedImage> rawFrames = new ArrayList<>();
        final List<Integer> rawDelays = new ArrayList<>();
        final List<String> frames = new ArrayList<>();
        final List<Integer> frameDelays = new ArrayList<>();
        final List<GpuTexture> gpuTextures = new ArrayList<>();
        final List<GpuTextureView> gpuViews = new ArrayList<>();
        int totalDuration = 0;

        void startLoad(String path) {
            this.state = State.LOADING;
            LOADER.submit(() -> {
                try { decodeGif(path); }
                catch (Exception e) { e.printStackTrace(); this.state = State.IDLE; }
            });
        }

        private void decodeGif(String path) throws Exception {
            String namespace;
            String resourcePath;
            if (path.contains(":")) {
                String[] split = path.split(":", 2);
                namespace = split[0];
                resourcePath = split[1];
            } else {
                namespace = "client";
                resourcePath = path;
            }

            var resource = MinecraftClient.getInstance().getResourceManager()
                    .getResource(Identifier.of(namespace, resourcePath));
            if (resource.isEmpty()) {
                System.err.println("[GifRenderer] Missing GIF resource: " + namespace + ":" + resourcePath);
                this.state = State.IDLE;
                return;
            }

            InputStream is = resource.get().getInputStream();
            ImageInputStream iis = ImageIO.createImageInputStream(is);
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            reader.setInput(iis, false, false);

            int w = reader.getWidth(0);
            int h = reader.getHeight(0);
            int count = reader.getNumImages(true);
            BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.setBackground(new Color(0, 0, 0, 0));
            BufferedImage prev = null;

            for (int i = 0; i < count; i++) {
                IIOMetadata meta = reader.getImageMetadata(i);
                IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(meta.getNativeMetadataFormatName());
                IIOMetadataNode gce = findNode(root, "GraphicControlExtension");
                IIOMetadataNode desc = findNode(root, "ImageDescriptor");

                String disposal = gce != null ? gce.getAttribute("disposalMethod") : "none";
                int delay = 100;
                if (gce != null) {
                    try { delay = Integer.parseInt(gce.getAttribute("delayTime")) * 10; }
                    catch (Exception ignored) {}
                }
                if (delay <= 0) delay = 100;

                int left = desc != null ? parseInt(desc.getAttribute("imageLeftPosition"), 0) : 0;
                int top = desc != null ? parseInt(desc.getAttribute("imageTopPosition"), 0) : 0;

                if ("restoreToPrevious".equals(disposal)) {
                    prev = copyImage(canvas);
                }

                BufferedImage frame = reader.read(i);
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(frame, left, top, null);
                rawFrames.add(copyImage(canvas));
                rawDelays.add(delay);

                if ("restoreToBackgroundColor".equals(disposal)) {
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(left, top, frame.getWidth(), frame.getHeight());
                } else if ("restoreToPrevious".equals(disposal) && prev != null) {
                    g.setComposite(AlphaComposite.Src);
                    g.drawImage(prev, 0, 0, null);
                }
            }

            g.dispose();
            reader.dispose();
            iis.close();
            is.close();
            this.state = State.FRAMES_READY;
            scheduleGpuUploads();
        }

        void dispose() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                for (String f : frames) {
                    try {
                        Identifier id = Identifier.tryParse(f);
                        if (id != null) mc.getTextureManager().destroyTexture(id);
                    } catch (Exception ignored) {}
                }
            }
            gpuViews.forEach(GpuTextureView::close);
            gpuTextures.forEach(GpuTexture::close);
            frames.clear();
            frameDelays.clear();
            gpuTextures.clear();
            gpuViews.clear();
            rawFrames.clear();
            rawDelays.clear();
        }

        private void uploadSingleFrame(int index) {
            try {
                if (index >= rawFrames.size()) return;
                BufferedImage img = rawFrames.get(index);
                int w = img.getWidth();
                int h = img.getHeight();
                NativeImage ni = new NativeImage(NativeImage.Format.RGBA, w, h, false);
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++)
                        ni.setColorArgb(x, y, img.getRGB(x, y));

                int frameId = ID_COUNTER.getAndIncrement();
                GpuTexture tex = RenderSystem.getDevice().createTexture(
                        () -> "decidevisuals_gif_" + frameId, 5, TextureFormat.RGBA8, w, h, 1, 1);
                RenderSystem.getDevice().createCommandEncoder().writeToTexture(tex, ni);
                GpuTextureView view = RenderSystem.getDevice().createTextureView(tex);
                ni.close();

                Identifier id = Identifier.of("wvisual", "gif/frame_" + frameId);
                MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                        new GifFrameTexture(tex, view, w, h));
                frames.add(id.toString());
                frameDelays.add(rawDelays.get(index));
                gpuTextures.add(tex);
                gpuViews.add(view);
                totalDuration += rawDelays.get(index);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void scheduleGpuUploads() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;
            final int frameCount = rawFrames.size();
            for (int i = 0; i < frameCount; i++) {
                int idx = i;
                mc.execute(() -> uploadSingleFrame(idx));
            }
            mc.execute(() -> {
                rawFrames.clear();
                state = State.GPU_READY;
            });
        }

        String getCurrentFrameId() {
            if (frames.isEmpty()) return null;
            if (totalDuration == 0) return frames.get(0);
            long now = System.currentTimeMillis() % totalDuration;
            long acc = 0;
            for (int i = 0; i < frames.size(); i++) {
                acc += frameDelays.get(i);
                if (now < acc) return frames.get(i);
            }
            return frames.get(frames.size() - 1);
        }
    }

    public static final class GifFrameTexture extends AbstractTexture {
        private final GpuTexture gpuTexture;
        private final GpuTextureView gpuTextureView;

        public GifFrameTexture(GpuTexture tex, GpuTextureView view, int w, int h) {
            this.gpuTexture = tex;
            this.gpuTextureView = view;
        }

        @Override
        public GpuTexture getGlTexture() {
            return gpuTexture;
        }

        public void load(ResourceManager rm) {}

        public GpuTextureView getTextureView() { return gpuTextureView; }
        public GpuTexture getTexture() { return gpuTexture; }
    }

    public enum State {
        IDLE, LOADING, FRAMES_READY, GPU_READY
    }
}

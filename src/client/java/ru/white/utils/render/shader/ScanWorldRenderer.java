package ru.white.utils.render.shader;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Состояние и запуск сканирующей волны. Копирует depth-аттачмент во
 * вспомогательную текстуру (сэмплить привязанный к пассу depth нельзя)
 * и отдаёт всё в {@link ScanWorldCompositePipeline}.
 */
public final class ScanWorldRenderer {

    private static ScanWorldRenderer instance;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final ScanWorldCompositePipeline pipeline = new ScanWorldCompositePipeline();

    private GpuTexture depthTexture;
    private GpuTextureView depthTextureView;

    private int lastWidth;
    private int lastHeight;

    private record Scan(Vec3d center, long startTime) {}

    /** Одновременных волн может быть несколько (интервал + тотемы + киллы). */
    private static final int MAX_SCANS = 12;
    private final List<Scan> scans = new CopyOnWriteArrayList<>();

    private float duration = 2.5f;
    private float width = 10.0f;
    private float maxRadius = 80.0f;
    private float fadeOutPortion = 0.25f;

    private Color outerColor = new Color(0, 0, 0, 255);
    private Color midColor = new Color(0, 0, 0, 255);
    private Color innerColor = new Color(80, 220, 255, 180);
    private Color scanlineColor = new Color(120, 255, 255, 120);

    private ScanWorldRenderer() {
    }

    public static ScanWorldRenderer getInstance() {
        if (instance == null) {
            instance = new ScanWorldRenderer();
        }
        return instance;
    }

    public void startScan(Vec3d center) {
        if (scans.size() >= MAX_SCANS) {
            scans.remove(0); // самая старая волна уступает место новой
        }
        scans.add(new Scan(center, System.currentTimeMillis()));
    }

    public void stopScan() {
        scans.clear();
    }

    public void setDuration(float duration) {
        this.duration = duration;
    }

    public void setFadeOutPortion(float fadeOutPortion) {
        this.fadeOutPortion = fadeOutPortion;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public void setMaxRadius(float maxRadius) {
        this.maxRadius = maxRadius;
    }

    public void setOuterColor(Color color) {
        this.outerColor = color;
    }

    public void setMidColor(Color color) {
        this.midColor = color;
    }

    public void setInnerColor(Color color) {
        this.innerColor = color;
    }

    public void setScanlineColor(Color color) {
        this.scanlineColor = color;
    }

    public void render(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (scans.isEmpty()) return;

        long now = System.currentTimeMillis();
        float safeDuration = Math.max(duration, 0.001f);

        scans.removeIf(s -> (now - s.startTime()) / 1000.0f > safeDuration);
        if (scans.isEmpty()) return;

        Framebuffer fb = mc.getFramebuffer();
        if (fb == null || fb.getDepthAttachment() == null || fb.getColorAttachmentView() == null) return;

        int widthPx = fb.textureWidth;
        int heightPx = fb.textureHeight;

        ensureDepthTexture(widthPx, heightPx);

        // depth копируется один раз на кадр — все волны сэмплят одну копию
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                fb.getDepthAttachment(),
                depthTexture,
                0, 0, 0, 0, 0,
                widthPx, heightPx
        );

        Matrix4f invView = new Matrix4f(viewMatrix).invert();
        Matrix4f invProj = new Matrix4f(projectionMatrix).invert();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        float fadeStart = 1.0f - Math.max(0.01f, Math.min(fadeOutPortion, 0.95f));

        for (Scan scan : scans) {
            float elapsed = (now - scan.startTime()) / 1000.0f;
            float progress = elapsed / safeDuration;
            float radius = progress * maxRadius;

            float fade = 1.0f;
            if (progress >= fadeStart) {
                float t = (progress - fadeStart) / (1.0f - fadeStart);
                t = Math.max(0.0f, Math.min(t, 1.0f));
                fade = 1.0f - t;
                fade = fade * fade; // мягче затухание
            }

            pipeline.render(
                    fb.getColorAttachmentView(),
                    depthTextureView,
                    widthPx,
                    heightPx,
                    cameraPos,
                    scan.center(),
                    invView,
                    invProj,
                    radius,
                    this.width,
                    fade,
                    outerColor,
                    midColor,
                    innerColor,
                    scanlineColor
            );
        }
    }

    private void ensureDepthTexture(int width, int height) {
        if (depthTexture != null && width == lastWidth && height == lastHeight) {
            return;
        }

        cleanupDepthTexture();

        depthTexture = RenderSystem.getDevice().createTexture(
                () -> "client:scanworld_depth_copy",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                TextureFormat.DEPTH32,
                width, height, 1, 1
        );
        depthTextureView = RenderSystem.getDevice().createTextureView(depthTexture);

        lastWidth = width;
        lastHeight = height;
    }

    private void cleanupDepthTexture() {
        if (depthTextureView != null) {
            depthTextureView.close();
            depthTextureView = null;
        }
        if (depthTexture != null) {
            depthTexture.close();
            depthTexture = null;
        }
    }

    public void close() {
        cleanupDepthTexture();
        pipeline.close();
        scans.clear();
    }
}

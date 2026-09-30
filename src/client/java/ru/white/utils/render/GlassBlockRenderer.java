package ru.white.utils.render;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

public class GlassBlockRenderer {

    private static GlassBlockRenderer instance;

    private final MinecraftClient client;
    private KawaseBlurPipeline kawaseBlur;
    private GlassCompositePipeline glassComposite;
    private MaskDiffPipeline maskDiff;
    private GlassOutlinePipeline glassOutline;

    private GpuTexture sceneBeforeTexture;
    private GpuTextureView sceneBeforeTextureView;
    private GpuTexture sceneAfterTexture;
    private GpuTextureView sceneAfterTextureView;
    private GpuTexture depthBeforeTexture;
    private GpuTextureView depthBeforeTextureView;
    private GpuTexture depthAfterTexture;
    private GpuTextureView depthAfterTextureView;
    private GpuTexture maskTexture;
    private GpuTextureView maskTextureView;

    private int lastWidth = 0;
    private int lastHeight = 0;

    private boolean capturing = false;
    private boolean enabled = false;
    private boolean initialized = false;

    private boolean blurEnabled = true;
    private float blurRadius = 2.5f;
    private int blurIterations = 3;
    private float saturation = 0.0f;
    private boolean reflect = true;
    private int tintColor = 0x00000000;
    private float tintIntensity = 0.0f;

    private boolean outlineEnabled = true;
    private float outlineGlowStrength = 4.0f;
    private int outlineColor = 0xFFFFFFFF;
    private int outlineColorFill = 0xFFFFFFFF;
    private int outlineWidth = 1;
    private int outlineGlowMode = 2;

    public GlassBlockRenderer() {
        this.client = MinecraftClient.getInstance();
        instance = this;
    }

    public static GlassBlockRenderer getInstance() {
        if (instance == null) {
            instance = new GlassBlockRenderer();
        }
        return instance;
    }

    private void ensureInitialized() {
        if (initialized) return;

        if (kawaseBlur != null) kawaseBlur.close();
        if (glassComposite != null) glassComposite.close();
        if (maskDiff != null) maskDiff.close();
        if (glassOutline != null) glassOutline.close();

        kawaseBlur = new KawaseBlurPipeline();
        glassComposite = new GlassCompositePipeline();
        maskDiff = new MaskDiffPipeline();
        glassOutline = new GlassOutlinePipeline();
        lastWidth = 0;
        lastHeight = 0;
        initialized = true;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) ensureInitialized();
    }

    public void setBlurEnabled(boolean enabled) { this.blurEnabled = enabled; }
    public void setBlurRadius(float radius) { this.blurRadius = radius; }
    public void setBlurIterations(int iterations) { this.blurIterations = Math.max(1, Math.min(8, iterations)); }
    public void setSaturation(float saturation) { this.saturation = saturation; }
    public void setReflect(boolean reflect) { this.reflect = reflect; }
    public void setTintColor(int color) { this.tintColor = color; }
    public void setTintIntensity(float intensity) { this.tintIntensity = intensity; }
    public void setOutlineEnabled(boolean enabled) { this.outlineEnabled = enabled; }
    public void setOutlineGlowStrength(float strength) { this.outlineGlowStrength = strength; }
    public void setOutlineColor(int color) { this.outlineColor = color; this.outlineColorFill = color; }
    public void setOutlineWidth(int width) { this.outlineWidth = width; }
    public void setOutlineGlowMode(int mode) { this.outlineGlowMode = mode; }
    public void setShimmerEnabled(boolean enabled) { if (glassOutline != null) glassOutline.setShimmerEnabled(enabled); }
    public void setShimmerWidth(float width) { if (glassOutline != null) glassOutline.setShimmerWidth(width); }
    public void setShimmerPeriodSec(float sec) { if (glassOutline != null) glassOutline.setShimmerPeriodSec(sec); }

    private void ensureTextures(int width, int height) {
        if (width == lastWidth && height == lastHeight && sceneBeforeTexture != null) return;

        cleanupTextures();

        sceneBeforeTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_block_scene_before",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1
        );
        sceneBeforeTextureView = RenderSystem.getDevice().createTextureView(sceneBeforeTexture);

        sceneAfterTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_block_scene_after",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1
        );
        sceneAfterTextureView = RenderSystem.getDevice().createTextureView(sceneAfterTexture);

        depthBeforeTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_block_depth_before",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32, width, height, 1, 1
        );
        depthBeforeTextureView = RenderSystem.getDevice().createTextureView(depthBeforeTexture);

        depthAfterTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_block_depth_after",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32, width, height, 1, 1
        );
        depthAfterTextureView = RenderSystem.getDevice().createTextureView(depthAfterTexture);

        maskTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_block_mask",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1
        );
        maskTextureView = RenderSystem.getDevice().createTextureView(maskTexture);

        lastWidth = width;
        lastHeight = height;
    }

    private void cleanupTextures() {
        if (sceneBeforeTextureView != null) { sceneBeforeTextureView.close(); sceneBeforeTextureView = null; }
        if (sceneBeforeTexture != null) { sceneBeforeTexture.close(); sceneBeforeTexture = null; }
        if (sceneAfterTextureView != null) { sceneAfterTextureView.close(); sceneAfterTextureView = null; }
        if (sceneAfterTexture != null) { sceneAfterTexture.close(); sceneAfterTexture = null; }
        if (depthBeforeTextureView != null) { depthBeforeTextureView.close(); depthBeforeTextureView = null; }
        if (depthBeforeTexture != null) { depthBeforeTexture.close(); depthBeforeTexture = null; }
        if (depthAfterTextureView != null) { depthAfterTextureView.close(); depthAfterTextureView = null; }
        if (depthAfterTexture != null) { depthAfterTexture.close(); depthAfterTexture = null; }
        if (maskTextureView != null) { maskTextureView.close(); maskTextureView = null; }
        if (maskTexture != null) { maskTexture.close(); maskTexture = null; }
    }

    public boolean captureSceneBeforeBlock() {
        if (!enabled) return false;
        ensureInitialized();

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return false;

        int width = fb.textureWidth;
        int height = fb.textureHeight;
        ensureTextures(width, height);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(fb.getColorAttachment(), sceneBeforeTexture, 0, 0, 0, 0, 0, width, height);
        if (fb.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(fb.getDepthAttachment(), depthBeforeTexture, 0, 0, 0, 0, 0, width, height);
        }
        capturing = true;
        return true;
    }

    public void captureSceneAfterBlock() {
        if (!enabled || !capturing) return;

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(fb.getColorAttachment(), sceneAfterTexture, 0, 0, 0, 0, 0, lastWidth, lastHeight);
        if (fb.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(fb.getDepthAttachment(), depthAfterTexture, 0, 0, 0, 0, 0, lastWidth, lastHeight);
        }
    }

    public void renderGlassEffect() {
        if (!enabled || !capturing) return;

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) {
            capturing = false;
            return;
        }

        maskDiff.createMask(maskTextureView, sceneBeforeTextureView, sceneAfterTextureView,
                depthBeforeTextureView, depthAfterTextureView, lastWidth, lastHeight);

        GpuTextureView glassView = sceneBeforeTextureView;
        if (blurEnabled) {
            glassView = kawaseBlur.blur(sceneBeforeTexture, sceneBeforeTextureView,
                    lastWidth, lastHeight, blurIterations, blurRadius);
            if (glassView == null) {
                capturing = false;
                return;
            }
        }

        glassComposite.composite(fb.getColorAttachmentView(), sceneBeforeTextureView, glassView,
                maskTextureView, lastWidth, lastHeight, saturation, reflect, tintColor, tintIntensity, 0.0f, 12.0f,
                0.0f, 18.0f, 0.0f,
                0.0f, 6.0f, 0.25f, 40.0f);

        if (outlineEnabled) {
            glassOutline.renderOutline(maskTextureView, fb.getColorAttachmentView(),
                    lastWidth, lastHeight, outlineGlowStrength, outlineWidth,
                    outlineGlowMode, outlineColorFill, outlineColor);
        }

        capturing = false;
    }

    public void invalidate() {
        cleanupTextures();
        if (kawaseBlur != null) kawaseBlur.close();
        if (glassComposite != null) glassComposite.close();
        if (maskDiff != null) maskDiff.close();
        if (glassOutline != null) glassOutline.close();
        kawaseBlur = null;
        glassComposite = null;
        maskDiff = null;
        glassOutline = null;
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
        capturing = false;
    }

    public void close() {
        cleanupTextures();
        if (kawaseBlur != null) { kawaseBlur.close(); kawaseBlur = null; }
        if (glassComposite != null) { glassComposite.close(); glassComposite = null; }
        if (maskDiff != null) { maskDiff.close(); maskDiff = null; }
        if (glassOutline != null) { glassOutline.close(); glassOutline = null; }
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
        capturing = false;
    }
}

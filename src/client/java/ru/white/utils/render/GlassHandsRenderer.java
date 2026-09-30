package ru.white.utils.render;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

public class GlassHandsRenderer {

    private static GlassHandsRenderer instance;

    private final MinecraftClient client;
    private KawaseBlurPipeline kawaseBlur;
    private GlassCompositePipeline glassComposite;
    private MaskDiffPipeline maskDiff;
    private GlassOutlinePipeline glassOutline;
    private BurningHandsPipeline burningHands;

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
    private GpuTexture burnGlowTexture;
    private GpuTextureView burnGlowTextureView;
    private final GpuTexture[] burnTrailTextures = new GpuTexture[2];
    private final GpuTextureView[] burnTrailTextureViews = new GpuTextureView[2];
    private int burnTrailIndex = 0;

    private int lastWidth = 0;
    private int lastHeight = 0;

    private boolean capturing = false;
    private boolean maskReady = false;
    /** Держать в sceneAfter готовый кадр с эффектом — нужно только редактору рук. */
    private boolean keepHandsResult = false;
    private boolean enabled = false;
    private boolean initialized = false;

    private boolean blurEnabled = true;
    private float blurRadius = 6.0f;
    private int blurIterations = 4;
    private float saturation = 1.0f;
    private boolean reflect = true;
    private int tintColor = 0x00000000;
    private float tintIntensity = 0.1f;
    private float edgeGlowIntensity = 0.3f;
    /** Радиус сглаживания кромки маски в px: 0 — силуэт копирует предмет. */
    private float edgeSoftness = 0.0f;

    // Ice parameters
    private float iceIntensity = 0.0f;
    private float frostScale = 18.0f;
    private float crackIntensity = 0.5f;

    // Frost smoke parameters
    private float smokeAmount = 0.0f;
    private float smokeScale = 6.0f;
    private float smokeSpeed = 0.25f;
    private float smokeReach = 40.0f;

    // Burning hands (fire) parameters
    private boolean fireEnabled = false;
    private float fireRadius = 15.0f;       // px, ширина ореола пламени
    private float fireStrength = 1.18f;     // сила glow
    private float fireSpeed = 1.1f;         // скорость анимации пламени/дыма
    private float fireHeight = 28.0f;       // px, высота подъёма дыма
    private float fireDecay = 0.94f;        // затухание трейла между кадрами
    private float fireTrailStrength = 1.16f;
    private int fireColor = 0xFF8026;       // rgb
    private float fireColorMix = 0.0f;      // 0=цвет предмета, 1=fireColor
    private boolean fireFillMode = false;   // прожиг сквозь силуэт

    // Outline parameters
    private boolean outlineEnabled = true;
    private float outlineGlowStrength = 20.0f;
    private int outlineColor = 0xFFFFFFFF;
    private int outlineColorFill = 0xFFFFFFFF;
    private int outlineWidth = 1;
    private int outlineGlowMode = 2;
    private boolean outlineUseItemColor = false;

    public GlassHandsRenderer() {
        this.client = MinecraftClient.getInstance();
        instance = this;
    }

    public static GlassHandsRenderer getInstance() {
        if (instance == null) {
            instance = new GlassHandsRenderer();
        }
        return instance;
    }

    public static void resetInstance() {
        if (instance != null) {
            instance.close();
            instance.initialized = false;
        }
    }

    private void ensureInitialized() {
        if (initialized) return;

        if (kawaseBlur != null) kawaseBlur.close();
        if (glassComposite != null) glassComposite.close();
        if (maskDiff != null) maskDiff.close();
        if (glassOutline != null) glassOutline.close();
        if (burningHands != null) burningHands.close();

        this.kawaseBlur = new KawaseBlurPipeline();
        this.glassComposite = new GlassCompositePipeline();
        this.maskDiff = new MaskDiffPipeline();
        this.glassOutline = new GlassOutlinePipeline();
        this.burningHands = new BurningHandsPipeline();

        lastWidth = 0;
        lastHeight = 0;

        initialized = true;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) ensureInitialized();
    }

    public boolean isEnabled() { return enabled; }

    public void setBlurEnabled(boolean enabled) { this.blurEnabled = enabled; }
    public void setBlurRadius(float radius) { this.blurRadius = radius; }
    public void setBlurIterations(int iterations) { this.blurIterations = Math.max(1, Math.min(8, iterations)); }
    public void setSaturation(float saturation) { this.saturation = saturation; }
    public void setReflect(boolean reflect) { this.reflect = reflect; }
    public void setTintColor(int color) { this.tintColor = color; }
    public void setTintIntensity(float intensity) { this.tintIntensity = intensity; }
    public void setEdgeGlowIntensity(float intensity) { this.edgeGlowIntensity = intensity; }
    public void setEdgeSoftness(float px) { this.edgeSoftness = Math.max(0.0f, px); }

    public void setIceIntensity(float intensity) { this.iceIntensity = intensity; }
    public void setFrostScale(float scale) { this.frostScale = scale; }
    public void setCrackIntensity(float intensity) { this.crackIntensity = intensity; }
    public void setSmokeAmount(float amount) { this.smokeAmount = amount; }
    public void setSmokeScale(float scale) { this.smokeScale = scale; }
    public void setSmokeSpeed(float speed) { this.smokeSpeed = speed; }
    public void setSmokeReach(float reach) { this.smokeReach = reach; }

    public void setFireEnabled(boolean enabled) { this.fireEnabled = enabled; }
    public void setFireRadius(float radius) { this.fireRadius = radius; }
    public void setFireStrength(float strength) { this.fireStrength = strength; }
    public void setFireSpeed(float speed) { this.fireSpeed = speed; }
    public void setFireHeight(float height) { this.fireHeight = height; }
    public void setFireTrailStrength(float strength) { this.fireTrailStrength = strength; }
    public void setFireColor(int rgb) { this.fireColor = rgb; }
    public void setFireColorMix(float mix) { this.fireColorMix = mix; }
    public void setFireFillMode(boolean fill) { this.fireFillMode = fill; }

    public void setOutlineEnabled(boolean enabled) { this.outlineEnabled = enabled; }
    public void setOutlineGlowStrength(float strength) { this.outlineGlowStrength = strength; }
    public void setOutlineColor(int color) { this.outlineColor = color; this.outlineColorFill = color; }
    public void setOutlineFillColor(int color) { this.outlineColorFill = color; }
    public void setKeepHandsResult(boolean keep) { this.keepHandsResult = keep; }
    public void setOutlineWidth(int width) { this.outlineWidth = width; }
    public void setOutlineGlowMode(int mode) { this.outlineGlowMode = mode; }
    public void setOutlineUseItemColor(boolean use) { this.outlineUseItemColor = use; }
    public void setShimmerEnabled(boolean enabled)  { if (glassOutline != null) glassOutline.setShimmerEnabled(enabled); }
    public void setShimmerWidth(float width)        { if (glassOutline != null) glassOutline.setShimmerWidth(width); }
    public void setShimmerPeriodSec(float sec)      { if (glassOutline != null) glassOutline.setShimmerPeriodSec(sec); }

    private void ensureTextures(int width, int height) {
        if (width == lastWidth && height == lastHeight && sceneBeforeTexture != null) return;

        cleanupTextures();

        sceneBeforeTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_scene_before",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1
        );
        sceneBeforeTextureView = RenderSystem.getDevice().createTextureView(sceneBeforeTexture);

        sceneAfterTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_scene_after",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1
        );
        sceneAfterTextureView = RenderSystem.getDevice().createTextureView(sceneAfterTexture);

        depthBeforeTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_depth_before",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32, width, height, 1, 1
        );
        depthBeforeTextureView = RenderSystem.getDevice().createTextureView(depthBeforeTexture);

        depthAfterTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_depth_after",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32, width, height, 1, 1
        );
        depthAfterTextureView = RenderSystem.getDevice().createTextureView(depthAfterTexture);

        maskTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_mask",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1
        );
        maskTextureView = RenderSystem.getDevice().createTextureView(maskTexture);

        burnGlowTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:burn_glow",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1
        );
        burnGlowTextureView = RenderSystem.getDevice().createTextureView(burnGlowTexture);

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            burnTrailTextures[i] = RenderSystem.getDevice().createTexture(
                    () -> "minecraft:burn_trail_" + idx,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    TextureFormat.RGBA8, width, height, 1, 1
            );
            burnTrailTextureViews[i] = RenderSystem.getDevice().createTextureView(burnTrailTextures[i]);
            // очистка мусора: трейл — накопительный буфер, стартовать должен с нуля
            CommandEncoder clearEncoder = RenderSystem.getDevice().createCommandEncoder();
            try (com.mojang.blaze3d.systems.RenderPass clearPass = clearEncoder.createRenderPass(
                    () -> "minecraft:burn_trail_clear", burnTrailTextureViews[i],
                    java.util.OptionalInt.of(0x00000000))) {
                // пустой проход — только clear при старте пасса
            }
        }
        burnTrailIndex = 0;

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
        if (burnGlowTextureView != null) { burnGlowTextureView.close(); burnGlowTextureView = null; }
        if (burnGlowTexture != null) { burnGlowTexture.close(); burnGlowTexture = null; }
        for (int i = 0; i < 2; i++) {
            if (burnTrailTextureViews[i] != null) { burnTrailTextureViews[i].close(); burnTrailTextureViews[i] = null; }
            if (burnTrailTextures[i] != null) { burnTrailTextures[i].close(); burnTrailTextures[i] = null; }
        }
    }

    public void captureSceneBeforeHands() {
        if (!enabled) return;
        ensureInitialized();

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;

        int width = fb.textureWidth;
        int height = fb.textureHeight;
        ensureTextures(width, height);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(fb.getColorAttachment(), sceneBeforeTexture, 0, 0, 0, 0, 0, width, height);
        if (fb.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(fb.getDepthAttachment(), depthBeforeTexture, 0, 0, 0, 0, 0, width, height);
        }
        capturing = true;
    }

    public void captureSceneAfterHands() {
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

        // маска рук этого кадра готова — редактор дорисует по ней руки поверх своего затемнения
        maskReady = true;

        boolean iceActive = iceIntensity > 0.001f;
        boolean smokeActive = smokeAmount > 0.001f;

        if (blurEnabled || iceActive || smokeActive) {
            GpuTextureView blurredView;
            if (blurEnabled) {
                blurredView = kawaseBlur.blur(
                        sceneBeforeTexture, sceneBeforeTextureView, lastWidth, lastHeight, blurIterations, blurRadius);
            } else {
                // no blur requested — feed the raw scene so ice/smoke can still composite
                blurredView = sceneBeforeTextureView;
            }

            if (blurredView == null) {
                capturing = false;
                return;
            }

            float effSaturation = blurEnabled ? saturation : 1.0f;
            int effTint = blurEnabled ? tintColor : 0x00000000;
            float effTintIntensity = blurEnabled ? tintIntensity : 0.0f;

            glassComposite.composite(fb.getColorAttachmentView(), sceneBeforeTextureView, blurredView,
                    maskTextureView, lastWidth, lastHeight, effSaturation, reflect, effTint, effTintIntensity, 0.0f, edgeSoftness,
                    iceIntensity, frostScale, crackIntensity,
                    smokeAmount, smokeScale, smokeSpeed, smokeReach);
        }

        if (outlineEnabled) {


            glassOutline.renderOutline(maskTextureView, fb.getColorAttachmentView(),
                    lastWidth, lastHeight, outlineGlowStrength, outlineWidth,
                    outlineGlowMode, outlineColor, outlineColorFill,
                    sceneAfterTextureView, outlineUseItemColor);
        }

        if (fireEnabled) {
            burningHands.updateUniforms(lastWidth, lastHeight, fireFillMode,
                    fireRadius, fireStrength, fireSpeed, fireColorMix,
                    fireDecay, fireHeight, fireSpeed, fireTrailStrength,
                    fireColor, fireStrength);

            burningHands.renderGlow(burnGlowTextureView, sceneAfterTextureView, maskTextureView);

            int prev = burnTrailIndex;
            int cur = 1 - burnTrailIndex;
            burningHands.renderTrail(burnTrailTextureViews[cur], burnTrailTextureViews[prev],
                    burnGlowTextureView, sceneAfterTextureView, maskTextureView);
            burnTrailIndex = cur;

            burningHands.composite(fb.getColorAttachmentView(), maskTextureView,
                    burnGlowTextureView, burnTrailTextureViews[cur]);
        }

        // редактору нужен снимок рук уже с эффектом: sceneAfter снят до композита,
        // и если вернуть его поверх затемнения, блюр с искажением останутся под предметом
        if (keepHandsResult && sceneAfterTexture != null) {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    fb.getColorAttachment(), sceneAfterTexture, 0, 0, 0, 0, 0, lastWidth, lastHeight);
        }

        capturing = false;
    }

    /**
     * Возвращает пиксели рук поверх затемнения редактора: тёмный кадр идёт как «сцена»,
     * снимок с руками — как «эффект», и композит по маске рук оставляет тёмным только мир.
     */
    public void restoreHandsAfterOverlay() {
        if (!enabled || !maskReady || sceneBeforeTexture == null || sceneAfterTextureView == null
                || maskTextureView == null || glassComposite == null) {
            return;
        }

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null || fb.getColorAttachmentView() == null) {
            maskReady = false;
            return;
        }

        DrawBatcher.flushPending();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(fb.getColorAttachment(), sceneBeforeTexture,
                0, 0, 0, 0, 0, lastWidth, lastHeight);

        glassComposite.composite(fb.getColorAttachmentView(), sceneBeforeTextureView,
                sceneAfterTextureView, maskTextureView, lastWidth, lastHeight,
                1.0f, false, 0x00000000, 0.0f, 0.0f, edgeSoftness,
                0.0f, frostScale, 0.0f,
                0.0f, smokeScale, smokeSpeed, smokeReach);

        if (outlineEnabled && glassOutline != null) {
            glassOutline.renderOutline(maskTextureView, fb.getColorAttachmentView(),
                    lastWidth, lastHeight, outlineGlowStrength, outlineWidth,
                    outlineGlowMode, outlineColor, outlineColorFill,
                    sceneAfterTextureView, outlineUseItemColor);
        }

        maskReady = false;
    }

    public boolean isCapturing() { return capturing; }

    public void invalidate() {
        cleanupTextures();
        if (kawaseBlur != null) kawaseBlur.close();
        if (glassComposite != null) glassComposite.close();
        if (maskDiff != null) maskDiff.close();
        if (glassOutline != null) glassOutline.close();
        if (burningHands != null) burningHands.close();
        kawaseBlur = null;
        glassComposite = null;
        maskDiff = null;
        glassOutline = null;
        burningHands = null;
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
        if (burningHands != null) { burningHands.close(); burningHands = null; }
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
    }
}

package ru.white.screen;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import ru.white.Client;
import ru.white.module.impl.display.ClickGui;
import ru.white.utils.annotation.IMinecraft;

import java.nio.ByteBuffer;
import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

public class GuiCloseAnimation3D implements IMinecraft {

    private static boolean active = false;
    private static long startTime = 0;

    private static Vec3d savedCameraPos;
    private static Vec3d savedForward;
    private static Quaternionf savedCameraQuat;

    private static final long DURATION_FROZEN = 900;
    private static final long DURATION_FADE = 600;
    private static final long DURATION_TOTAL = DURATION_FROZEN + DURATION_FADE;
    private static final float PANEL_DISTANCE = 2.6f;

    private static final Identifier TEXTURE_ID = Identifier.of("client", "gui_close_3d_capture");

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/gui_close_3d")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static Function<Identifier, RenderLayer> renderLayerFunc;

    public static void start() {
        if (active) return;
        if (mc.player == null || mc.world == null) return;

        ClickGui clickGui = mc.player != null ? Client.get().moduleManager().get(ClickGui.class) : null;
        if (clickGui == null) return;
        float guiScale = clickGui.size.getValue();

        Camera camera = mc.gameRenderer.getCamera();
        savedCameraPos = camera.getCameraPos();
        savedForward = mc.player.getRotationVec(1.0f).normalize();
        savedCameraQuat = new Quaternionf(camera.getRotation());

        int guiFbW = (int) (420 * guiScale * 2);
        int guiFbH = (int) (280 * guiScale * 2);
        int fbW = mc.getWindow().getFramebufferWidth();
        int fbH = mc.getWindow().getFramebufferHeight();
        int captureX = (fbW - guiFbW) / 2;
        int captureY = (fbH - guiFbH) / 2;

        if (!captureRegion(captureX, captureY, guiFbW, guiFbH)) return;

        active = true;
        startTime = System.currentTimeMillis();
        mc.setScreen(null);
    }

    private static boolean captureRegion(int x, int y, int w, int h) {
        try {
            int fbW = mc.getWindow().getFramebufferWidth();
            int fbH = mc.getWindow().getFramebufferHeight();
            x = Math.max(0, Math.min(x, fbW));
            y = Math.max(0, Math.min(y, fbH));
            w = Math.min(w, fbW - x);
            h = Math.min(h, fbH - y);
            if (w <= 0 || h <= 0) return false;

            ByteBuffer buffer = BufferUtils.createByteBuffer(w * h * 4);
            GL11.glReadPixels(x, y, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

            NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    int i = (row * w + col) * 4;
                    int r = buffer.get(i) & 0xFF;
                    int g = buffer.get(i + 1) & 0xFF;
                    int b = buffer.get(i + 2) & 0xFF;
                    int a = buffer.get(i + 3) & 0xFF;
                    image.setColorArgb(col, h - 1 - row, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }

            mc.getTextureManager().destroyTexture(TEXTURE_ID);
            NativeImageBackedTexture backedTexture = new NativeImageBackedTexture(() -> "gui_close_3d_capture", image);
            mc.getTextureManager().registerTexture(TEXTURE_ID, backedTexture);

            renderLayerFunc = Util.memoize(id -> {
                RenderSetup setup = RenderSetup.builder(PIPELINE)
                        .texture("Sampler0", id)
                        .translucent()
                        .expectedBufferSize(1024)
                        .build();
                return RenderLayer.of("gui_close_3d_layer", setup);
            });
            return true;
        } catch (Exception e) {
            active = false;
            return false;
        }
    }

    public static void render(Matrix4f viewMatrix, float tickDelta) {
        if (!active || mc.world == null || mc.player == null || renderLayerFunc == null) return;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= DURATION_TOTAL) {
            cleanup();
            return;
        }

        float t;
        if (elapsed < DURATION_FROZEN) {
            t = 0.0f;
        } else {
            t = (float) (elapsed - DURATION_FROZEN) / DURATION_FADE;
        }
        t = Math.clamp(t, 0.0f, 1.0f);

        float tCubed = t * t * t;
        float shrink = 1.0f - tCubed;

        if (shrink <= 0.01f) {
            cleanup();
            return;
        }

        ClickGui clickGui = mc.player != null ? Client.get().moduleManager().get(ClickGui.class) : null;
        float guiScale = clickGui != null ? clickGui.size.getValue() : 1.0f;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        Vec3d panelWorldPos = savedCameraPos.add(savedForward.multiply(PANEL_DISTANCE));

        float px = (float) (panelWorldPos.x - camPos.x);
        float py = (float) (panelWorldPos.y - camPos.y);
        float pz = (float) (panelWorldPos.z - camPos.z);

        float guiScreenW = 420 * guiScale;
        float guiScreenH = 280 * guiScale;
        float screenHeight = mc.getWindow().getFramebufferHeight() / 2.0f;

        float fovRad = (float) Math.toRadians(mc.options.getFov().getValue());
        float visibleHeight = 2.0f * PANEL_DISTANCE * (float) Math.tan(fovRad / 2.0);
        float halfH = visibleHeight * (guiScreenH / screenHeight) * shrink / 2.0f;
        float halfW = halfH * (guiScreenW / guiScreenH);

        Vector3f c0 = new Vector3f(-halfW, -halfH, 0).rotate(savedCameraQuat);
        Vector3f c1 = new Vector3f( halfW, -halfH, 0).rotate(savedCameraQuat);
        Vector3f c2 = new Vector3f( halfW,  halfH, 0).rotate(savedCameraQuat);
        Vector3f c3 = new Vector3f(-halfW,  halfH, 0).rotate(savedCameraQuat);

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(1024));
        VertexConsumer consumer = immediate.getBuffer(renderLayerFunc.apply(TEXTURE_ID));

        consumer.vertex(viewMatrix, px + c0.x, py + c0.y, pz + c0.z)
                .texture(0.0f, 1.0f)
                .color(1.0f, 1.0f, 1.0f, 1.0f);
        consumer.vertex(viewMatrix, px + c1.x, py + c1.y, pz + c1.z)
                .texture(1.0f, 1.0f)
                .color(1.0f, 1.0f, 1.0f, 1.0f);
        consumer.vertex(viewMatrix, px + c2.x, py + c2.y, pz + c2.z)
                .texture(1.0f, 0.0f)
                .color(1.0f, 1.0f, 1.0f, 1.0f);
        consumer.vertex(viewMatrix, px + c3.x, py + c3.y, pz + c3.z)
                .texture(0.0f, 0.0f)
                .color(1.0f, 1.0f, 1.0f, 1.0f);

        immediate.draw();
    }

    public static boolean isActive() {
        return active;
    }

    public static void cleanup() {
        active = false;
        if (mc != null && mc.getTextureManager() != null) {
            mc.getTextureManager().destroyTexture(TEXTURE_ID);
        }
        renderLayerFunc = null;
    }
}

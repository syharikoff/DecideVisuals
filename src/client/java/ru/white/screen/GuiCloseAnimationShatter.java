package ru.white.screen;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import ru.white.Client;
import ru.white.module.impl.display.ClickGui;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.render.others.RenderSampler;
import ru.white.utils.render.voronoi.VoronoiOfQuad;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;

/**
 * Анимация закрытия GUI "Shatter" — порт из Kimiko
 * (rtx.kimiko.api.ui.window.GuiShatterAnimation + PanelAnimation).
 *
 * При закрытии панель кликабельного меню переносится в текстуру
 * (glReadPixels по прямоугольнику панели), режется на вороной-осколки,
 * и осколки разлетаются от центра с загибом (yaw/pitch по краям),
 * «чашей» (bowlZ) и перспективным сжатием. Экран закрывается сразу,
 * осколки рисуются поверх кадра в нормализованных экранных координатах.
 */
public class GuiCloseAnimationShatter implements IMinecraft {

    private static final String TEXTURE_NAME = "gui_close_shatter_capture";

    private static final Identifier TEXTURE_ID = Identifier.of("client", TEXTURE_NAME);
    private static final Identifier SHADER_ID = Identifier.of("wvisual", "post/guishatter/shatter");

    private static final VertexFormat VERTEX_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .build();

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[0])
                    .withLocation(Identifier.of("wvisual", "pipeline/guishatter"))
                    .withVertexShader(SHADER_ID)
                    .withFragmentShader(SHADER_ID)
                    .withVertexFormat(VERTEX_FORMAT, VertexFormat.DrawMode.TRIANGLES)
                    .withSampler("uGui")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    /** Параметры вороного/разлёта — 1-в-1 из Kimiko. */
    private static final int MIN_SHARDS = 22;
    private static final int SHARD_SPREAD = 7;
    private static final float RELAX_STRENGTH = 0.45F;
    private static final float SEED_JITTER = 0.32F;
    private static final float MIN_DISTANCE = 0.68F;
    private static final float MARGIN = 0.004F;
    private static final float EXPAND = 0.34F;
    private static final float SPREAD = 0.028F;
    private static final float CURL_DEGREES = 26.0F;
    private static final float CURL_DEPTH = 0.1F;
    private static final long DURATION_MS = 700L;
    private static final float PAD_DESIGN = 14.0F;

    private static final int VERTEX_BYTES = 24;
    private static final int MAX_VERTICES = 2048;

    private static boolean active = false;
    private static long startTime = 0;

    private static final List<VoronoiOfQuad.Polygon> shards = new ArrayList<>();
    private static float[] baseAngle = new float[0];
    private static float[] baseRadius = new float[0];
    private static float[] fade = new float[0];
    private static float[] radial = new float[0];

    private static float centerX;
    private static float centerY;
    private static float halfExtentX = 1.0F;
    private static float halfExtentY = 1.0F;
    private static float aspect = 1.0F;

    /** Прямоугольник панели в нормализованных экранных координатах (y вниз). */
    private static float nx1;
    private static float ny1;
    private static float nx2;
    private static float ny2;

    private static GpuBuffer vertexBuffer;
    private static ByteBuffer vertexData;
    private static boolean ready = false;

    private GuiCloseAnimationShatter() {}

    /**
     * Захватывает панель меню и запускает разлёт. Возвращает false, если
     * захват невозможен (нет игрока/мира, нет панели на экране) — тогда
     * вызывающий код должен остаться на обычной анимации закрытия.
     */
    public static boolean start() {
        if (active) return true;
        if (mc == null || mc.player == null || mc.world == null) return false;

        ClickGui clickGui = Client.get().moduleManager().get(ClickGui.class);
        if (clickGui == null) return false;
        float guiScale = clickGui.size.getValue();
        if (guiScale <= 0.0F) return false;

        int fbW = mc.getWindow().getFramebufferWidth();
        int fbH = mc.getWindow().getFramebufferHeight();
        if (fbW <= 0 || fbH <= 0) return false;

        // Панель рисуется в координатах Menu (420*S x 280*S), а Menu
        // масштабирует их ровно в 2 раза от фреймбуфера.
        float pad = PAD_DESIGN * guiScale * 2.0F;
        int panelW = (int) (420.0F * guiScale * 2.0F);
        int panelH = (int) (280.0F * guiScale * 2.0F);
        int capX = (fbW - panelW) / 2;
        int capY = (fbH - panelH) / 2;
        int capW = panelW;
        int capH = panelH;

        int rx = Math.max(0, capX - (int) pad);
        int ry = Math.max(0, capY - (int) pad);
        int rw = Math.min(fbW - rx, capW + (int) (pad * 2.0F));
        int rh = Math.min(fbH - ry, capH + (int) (pad * 2.0F));
        if (rw <= 4 || rh <= 4) return false;

        if (!captureRegion(rx, ry, rw, rh)) return false;
        if (!buildShards(rx, ry, rw, rh, fbW, fbH)) {
            releaseTexture();
            return false;
        }

        active = true;
        startTime = System.currentTimeMillis();
        // экран убираем сразу — осколки рисуются уже поверх игрового кадра
        mc.setScreen(null);
        return true;
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
                    // glReadPixels идёт снизу вверх — переворачиваем строки
                    image.setColorArgb(col, h - 1 - row, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }

            releaseTexture();
            mc.getTextureManager().registerTexture(TEXTURE_ID,
                    new NativeImageBackedTexture(() -> TEXTURE_NAME, image));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean buildShards(int rx, int ry, int rw, int rh, int fbW, int fbH) {
        nx1 = clamp01((float) rx / fbW);
        ny1 = clamp01((float) ry / fbH);
        nx2 = clamp01((float) (rx + rw) / fbW);
        ny2 = clamp01((float) (ry + rh) / fbH);

        centerX = (nx1 + nx2) * 0.5F;
        centerY = (ny1 + ny2) * 0.5F;
        halfExtentX = Math.max(0.001F, (nx2 - nx1) * 0.5F);
        halfExtentY = Math.max(0.001F, (ny2 - ny1) * 0.5F);
        aspect = Math.max(0.001F, (float) fbW / fbH);

        Random random = new Random();
        int count = MIN_SHARDS + random.nextInt(SHARD_SPREAD);
        VoronoiOfQuad voronoi = VoronoiOfQuad.spread(
                nx1, ny1, nx2, ny2,
                nx1 - MARGIN, ny1 - MARGIN, nx2 + MARGIN, ny2 + MARGIN,
                count, aspect, RELAX_STRENGTH, SEED_JITTER, MIN_DISTANCE, random);

        shards.clear();
        shards.addAll(voronoi.getPolygons());
        if (shards.isEmpty()) return false;

        int n = shards.size();
        baseAngle = new float[n];
        baseRadius = new float[n];
        fade = new float[n];
        radial = new float[n];
        for (int i = 0; i < n; i++) {
            VoronoiOfQuad.Vec2f c = shards.get(i).center;
            float dx = (c.x - centerX) * aspect;
            float dy = c.y - centerY;
            baseAngle[i] = (float) Math.atan2(dy, dx);
            baseRadius[i] = (float) Math.sqrt(dx * dx + dy * dy);
            fade[i] = 0.45F * random.nextFloat();
            radial[i] = 0.65F + 0.7F * random.nextFloat();
        }
        return true;
    }

    /** Кадр анимации. Вызывается из GameRenderMixin после отрисовки GUI. */
    public static void render() {
        if (!active) return;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= DURATION_MS) {
            cleanup();
            return;
        }

        float p = clamp01((float) elapsed / DURATION_MS);
        // общий хвост: последние 25% анимации гасим всё, чтобы не было среза
        float globalAlpha = p <= 0.75F ? 1.0F : clamp01(1.0F - (p - 0.75F) / 0.25F);

        try {
            if (!ensureBuffer()) return;
            int vertices = buildGeometry(p, globalAlpha);
            if (vertices <= 0) {
                cleanup();
                return;
            }
            draw(vertices);
        } catch (Throwable t) {
            cleanup();
        }
    }

    private static boolean ensureBuffer() {
        if (ready && vertexBuffer != null && !vertexBuffer.isClosed() && vertexData != null) return true;
        try {
            if (vertexBuffer != null && !vertexBuffer.isClosed()) vertexBuffer.close();
            vertexData = ByteBuffer.allocateDirect(MAX_VERTICES * VERTEX_BYTES).order(ByteOrder.nativeOrder());
            vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "wvisual:guishatter_vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    MAX_VERTICES * VERTEX_BYTES);
            ready = vertexBuffer != null && vertexData != null;
        } catch (Throwable t) {
            ready = false;
        }
        return ready;
    }

    private static int buildGeometry(float progress, float globalAlpha) {
        ByteBuffer out = vertexData;
        out.clear();

        float p = clamp01(progress);
        float push = easeOutCubic(p);
        float invW = 1.0F / Math.max(0.0001F, nx2 - nx1);
        float invH = 1.0F / Math.max(0.0001F, ny2 - ny1);
        int vertices = 0;
        int n = shards.size();

        for (int i = 0; i < n; i++) {
            VoronoiOfQuad.Polygon poly = shards.get(i);
            float shardAlpha = clamp01(1.0F - p * (0.9F + fade[i])) * globalAlpha;
            if (shardAlpha <= 0.002F) continue;

            float radius = baseRadius[i] * (1.0F + EXPAND * radial[i] * push)
                    + SPREAD * radial[i] * push;
            float pivotX = centerX + (float) Math.cos(baseAngle[i]) * radius / aspect;
            float pivotY = centerY + (float) Math.sin(baseAngle[i]) * radius;
            float offsetX = pivotX - poly.center.x;
            float offsetY = pivotY - poly.center.y;

            float edgeX = clampSigned((poly.center.x - centerX) * aspect / halfExtentX);
            float edgeY = clampSigned((poly.center.y - centerY) / halfExtentY);
            float yawAngle = (float) Math.toRadians(CURL_DEGREES * edgeX * push);
            float pitchAngle = (float) Math.toRadians(-CURL_DEGREES * edgeY * push);
            float bowlZ = CURL_DEPTH * (1.0F - Math.min(1.0F, edgeX * edgeX + edgeY * edgeY)) * push;
            float yawSin = (float) Math.sin(yawAngle);
            float yawCos = (float) Math.cos(yawAngle);
            float pitchSin = (float) Math.sin(pitchAngle);
            float pitchCos = (float) Math.cos(pitchAngle);

            List<VoronoiOfQuad.Vec2f> pts = poly.getAllVertices();
            int corners = Math.min(pts.size(), MAX_VERTICES / 3);
            if (corners < 3) continue;

            int alpha = Math.round(clamp01(shardAlpha) * 255.0F);
            for (int t = 1; t + 1 < corners && vertices + 3 <= MAX_VERTICES; t++) {
                putVertex(out, pts.get(0), offsetX, offsetY, pivotX, pivotY,
                        yawSin, yawCos, pitchSin, pitchCos, bowlZ, alpha, invW, invH);
                putVertex(out, pts.get(t), offsetX, offsetY, pivotX, pivotY,
                        yawSin, yawCos, pitchSin, pitchCos, bowlZ, alpha, invW, invH);
                putVertex(out, pts.get(t + 1), offsetX, offsetY, pivotX, pivotY,
                        yawSin, yawCos, pitchSin, pitchCos, bowlZ, alpha, invW, invH);
                vertices += 3;
            }
        }
        out.flip();
        return vertices;
    }

    private static void putVertex(ByteBuffer out, VoronoiOfQuad.Vec2f src,
                                  float offsetX, float offsetY, float pivotX, float pivotY,
                                  float yawSin, float yawCos, float pitchSin, float pitchCos,
                                  float bowlZ, int alpha, float invW, float invH) {
        float localX = (src.x + offsetX - pivotX) * aspect;
        float localY = src.y + offsetY - pivotY;

        float rotatedX = localX * yawCos;
        float rotatedZ = -localX * yawSin;
        float finalY = localY * pitchCos - rotatedZ * pitchSin;
        float finalZ = localY * pitchSin + rotatedZ * pitchCos + bowlZ;

        float posX = pivotX + rotatedX / aspect;
        float posY = pivotY + finalY;

        // перспективное сжатие к центру экрана (имитация "чаши")
        float perspective = 1.0F / Math.max(0.2F, 1.0F + finalZ * 0.9F);
        posX = 0.5F + (posX - 0.5F) * perspective;
        posY = 0.5F + (posY - 0.5F) * perspective;

        float uvX = (src.x - nx1) * invW;
        float uvY = (src.y - ny1) * invH;

        out.putFloat(posX);
        out.putFloat(posY);
        out.putFloat(0.0F);
        out.putFloat(uvX);
        out.putFloat(uvY);
        out.put((byte) 0xFF);
        out.put((byte) 0xFF);
        out.put((byte) 0xFF);
        out.put((byte) alpha);
    }

    private static void draw(int vertices) {
        if (mc.getFramebuffer() == null) return;

        com.mojang.blaze3d.textures.GpuTextureView view = captureView();
        if (view == null) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(vertexBuffer.slice(0L, vertexData.remaining()), vertexData);

        RenderPass pass = encoder.createRenderPass(
                () -> "wvisual:gui_close_shatter",
                mc.getFramebuffer().getColorAttachmentView(),
                OptionalInt.empty());
        try {
            pass.setPipeline(PIPELINE);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.bindTexture("uGui", view, RenderSampler.linear());
            pass.draw(0, vertices);
        } catch (Throwable t) {
            try {
                pass.close();
            } catch (Throwable ignored) {
                // pass уже закрыт — глушим
            }
            throw t;
        }
        pass.close();
    }

    private static com.mojang.blaze3d.textures.GpuTextureView captureView() {
        try {
            var texture = mc.getTextureManager().getTexture(TEXTURE_ID);
            if (texture == null) return null;
            var gpu = texture.getGlTexture();
            if (gpu == null || gpu.isClosed()) return null;
            return texture.getGlTextureView();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isActive() {
        return active;
    }

    /** Сброс анимации (открытие меню, смена режима, выход в мир). */
    public static void cancel() {
        if (!active && shards.isEmpty()) return;
        cleanup();
    }

    private static void cleanup() {
        active = false;
        shards.clear();
        baseAngle = new float[0];
        baseRadius = new float[0];
        fade = new float[0];
        radial = new float[0];
        releaseTexture();
    }

    private static void releaseTexture() {
        try {
            if (mc != null && mc.getTextureManager() != null) {
                mc.getTextureManager().destroyTexture(TEXTURE_ID);
            }
        } catch (Throwable ignored) {
            // текстура могла не существовать — не критично
        }
    }

    private static float clamp01(float f) {
        return Math.max(0.0F, Math.min(1.0F, f));
    }

    private static float clampSigned(float f) {
        return Math.max(-1.0F, Math.min(1.0F, f));
    }

    private static float easeOutCubic(float f) {
        float g = 1.0F - f;
        return 1.0F - g * g * g;
    }
}

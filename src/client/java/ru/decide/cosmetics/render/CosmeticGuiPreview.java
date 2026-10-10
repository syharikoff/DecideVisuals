package ru.decide.cosmetics.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.decide.cosmetics.CosmeticManager;
import ru.decide.cosmetics.geckolib.GeckoRenderHelper;
import ru.decide.cosmetics.geckolib.GeckolibCosmeticRenderer;
import ru.decide.cosmetics.geo.GeoBone;
import ru.decide.cosmetics.geo.GeoCube;
import ru.decide.cosmetics.geo.GeoModel;
import ru.decide.cosmetics.geo.GeoQuad;
import ru.decide.cosmetics.geo.GeoVertex;
import ru.decide.cosmetics.model.CosmeticModel;
import ru.decide.utils.render.others.RenderSampler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * 3D-превью косметики в меню.
 * <p>
 * Рендер идёт полностью сам (как у анимации Shatter): свой {@link RenderPipeline},
 * свой {@link RenderPass} в основной framebuffer и собственный вершинный буфер.
 * Вершины считаются на CPU и сразу кладутся в нормализованные экранные координаты
 * [0..1] (y вниз), поэтому шейдеру не нужны ни {@code ProjectionMatrix}, ни
 * {@code ModelViewMatrix} мира - из-за них превью уезжало за пределы карточки.
 * <p>
 * Вызывается из {@code GameRenderMixin.afterGuiRender()} - строго после сброса GUI,
 * иначе фон карточки затрёт модель.
 */
@Environment(EnvType.CLIENT)
public final class CosmeticGuiPreview {

    private static final Logger LOGGER = LoggerFactory.getLogger("DecideVisuals:CosmeticPreview");

    private static final Identifier SHADER = Identifier.of("decide", "post/cosmeticpreview/preview");

    private static final VertexFormat VERTEX_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .build();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(new RenderPipeline.Snippet[0])
            .withLocation(Identifier.of("decide", "pipeline/cosmeticpreview"))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withVertexFormat(VERTEX_FORMAT, VertexFormat.DrawMode.TRIANGLES)
            .withSampler("uGui")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final float ROTATION_SPEED = 28.0F;
    private static final int MAX_PENDING = 32;
    private static final int FLOATS_PER_VERTEX = 5;   // x, y, z, u, v
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4 + 4; // + rgba
    private static final int MAX_VERTICES = 24576;
    private static final long LOG_INTERVAL_MS = 2000L;

    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private static final List<PendingPreview> PENDING = new ArrayList<>();
    private static GpuBuffer vertexBuffer;
    private static ByteBuffer vertexData;
    private static long lastLogTime;

    /**
     * @param clipX клип области списка (в координатах меню); модель не должна
     *              вылезать за неё при прокрутке
     */
    private record PendingPreview(CosmeticManager.CosmeticEntry entry,
                                  float x, float y, float w, float h,
                                  float clipX, float clipY, float clipW, float clipH) {
    }

    /** Сброс очереди в начале кадра меню. */
    public static void clear() {
        PENDING.clear();
    }

    /**
     * Ставит карточку в очередь на отрисовку.
     *
     * @param clipX границы видимой области списка: модель обрезается по ним,
     *              иначе при прокрутке она вылезает за карточку и за сетку
     */
    public static void queuePreview(CosmeticManager.CosmeticEntry entry,
                                     float x, float y, float w, float h,
                                     float clipX, float clipY, float clipW, float clipH) {
        if (entry == null || w <= 0 || h <= 0 || PENDING.size() >= MAX_PENDING) {
            return;
        }
        PENDING.add(new PendingPreview(entry, x, y, w, h, clipX, clipY, clipW, clipH));
    }

    /** Рисует всю очередь. Вызывается после сброса GUI. */
    public static void renderPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        float rotation = (System.currentTimeMillis() % 360_000L) / 1000.0F * ROTATION_SPEED;
        int queued = PENDING.size();
        int drawn = 0;
        Throwable firstError = null;
        String info = "";

        for (PendingPreview pending : PENDING) {
            try {
                String result = pending.entry().isCape
                        ? drawCape(pending, rotation)
                        : drawModel(pending, rotation);
                if (drawn == 0) {
                    info = result;
                }
                drawn++;
            } catch (Throwable t) {
                if (firstError == null) {
                    firstError = t;
                }
            }
        }
        PENDING.clear();

        long now = System.currentTimeMillis();
        if (now - lastLogTime > LOG_INTERVAL_MS) {
            lastLogTime = now;
            LOGGER.info("[cosmetic-preview] queued={} drawn={} first={}", queued, drawn, info);
            if (firstError != null) {
                LOGGER.error("[cosmetic-preview] first error", firstError);
            }
        }
    }

    // ── 3D-модели ────────────────────────────────────────────────────────────

    private static String drawModel(PendingPreview pending, float rotationDeg) {
        CosmeticManager.CosmeticEntry entry = pending.entry();
        CosmeticModel cosmetic = CosmeticManager.getInstance().getModelForEntry(entry);
        if (cosmetic == null || cosmetic.getTextureId() == null) {
            return entry.name + ": no model";
        }
        GeckolibCosmeticRenderer renderer = GeckolibCosmeticRenderer.getInstance();
        GeoModel model = renderer.getOrParseModel(cosmetic);
        if (model == null) {
            return entry.name + ": geo parse failed";
        }
        model.computeBounds();
        renderer.applyPose(cosmetic, model);

        float maxDim = Math.max(0.05F, model.maxDimension);
        // 0.62 - запас, чтобы модель не вылезала за карточку (у нас нет scissor в своём pass)
        float fit = Math.min(pending.w(), pending.h()) * 0.62F / maxDim;

        ByteBuffer buffer = beginWrite();
        Matrix4fStack stack = new Matrix4fStack(32);
        stack.pushMatrix();
        // координаты меню -> пиксели фреймбуфера; guiUnit = framebufferWidth / scaledWidth
        float guiUnit = guiUnit();
        float centerX = (pending.x() + pending.w() * 0.5F) * guiUnit;
        float centerY = (pending.y() + pending.h() * 0.5F) * guiUnit;
        // модельные единицы -> пиксели фреймбуфера; Y переворачиваем (на экране он вниз)
        stack.translate(centerX, centerY, 0.0F);
        stack.scale(1.0F, -1.0F, 1.0F);
        stack.rotateY((float) Math.toRadians(rotationDeg));
        stack.scale(fit, fit, fit);
        stack.translate(-model.centerX, -model.centerY, -model.centerZ);

        int vertices = 0;
        for (GeoBone bone : model.topLevelBones) {
            vertices += emitBone(bone, stack, buffer);
        }
        stack.popMatrix();

        return finishDraw(entry.name + ": bones=" + model.topLevelBones.size()
                + " dim=" + fmt(maxDim) + " fit=" + fmt(fit) + " verts=" + vertices,
                cosmetic.getTextureId(), vertices,
                pending.clipX(), pending.clipY(), pending.clipW(), pending.clipH());
    }

    private static int emitBone(GeoBone bone, Matrix4fStack parent, ByteBuffer buffer) {
        if (bone.isHidden) {
            return 0;
        }
        parent.pushMatrix();
        applyBone(bone, parent);
        int written = 0;
        for (GeoCube cube : bone.childCubes) {
            written += emitCube(cube, parent, buffer);
        }
        for (GeoBone child : bone.childBones) {
            written += emitBone(child, parent, buffer);
        }
        parent.popMatrix();
        return written;
    }

    private static void applyBone(GeoBone bone, Matrix4fStack m) {
        m.translate(-bone.getPositionX() / 16.0F, bone.getPositionY() / 16.0F, bone.getPositionZ() / 16.0F);
        m.translate(bone.getPivotX() / 16.0F, bone.getPivotY() / 16.0F, bone.getPivotZ() / 16.0F);
        rotate(m, bone.getRotationX(), bone.getRotationY(), bone.getRotationZ());
        m.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
        m.translate(-bone.getPivotX() / 16.0F, -bone.getPivotY() / 16.0F, -bone.getPivotZ() / 16.0F);
    }

    private static int emitCube(GeoCube cube, Matrix4fStack parent, ByteBuffer buffer) {
        parent.pushMatrix();
        parent.translate(cube.pivot.getX() / 16.0F, cube.pivot.getY() / 16.0F, cube.pivot.getZ() / 16.0F);
        rotate(parent, cube.rotation.getX(), cube.rotation.getY(), cube.rotation.getZ());
        parent.translate(-cube.pivot.getX() / 16.0F, -cube.pivot.getY() / 16.0F, -cube.pivot.getZ() / 16.0F);

        Matrix4f m = new Matrix4f(parent);
        int written = 0;
        for (GeoQuad quad : cube.quads) {
            if (quad == null || quad.vertices == null || quad.vertices.length < 4) {
                continue;
            }
            // два треугольника на грань
            written += emitTriangle(quad.vertices[0], quad.vertices[1], quad.vertices[2], m, buffer);
            written += emitTriangle(quad.vertices[0], quad.vertices[2], quad.vertices[3], m, buffer);
        }
        parent.popMatrix();
        return written;
    }

    private static void rotate(Matrix4fStack m, float rx, float ry, float rz) {
        if (rz != 0.0F) {
            m.rotate(new Quaternionf(0.0F, 0.0F, (float) Math.sin(rz / 2.0F), (float) Math.cos(rz / 2.0F)));
        }
        if (ry != 0.0F) {
            m.rotate(new Quaternionf(0.0F, (float) Math.sin(ry / 2.0F), 0.0F, (float) Math.cos(ry / 2.0F)));
        }
        if (rx != 0.0F) {
            m.rotate(new Quaternionf((float) Math.sin(rx / 2.0F), 0.0F, 0.0F, (float) Math.cos(rx / 2.0F)));
        }
    }

    private static final Vector3f POSITION = new Vector3f();

    private static int emitTriangle(GeoVertex a, GeoVertex b, GeoVertex c, Matrix4f m, ByteBuffer buffer) {
        float fbW = MC.getWindow().getFramebufferWidth();
        float fbH = MC.getWindow().getFramebufferHeight();
        if (!writeVertex(a, m, buffer, fbW, fbH)) {
            return 0;
        }
        if (!writeVertex(b, m, buffer, fbW, fbH)) {
            return 0;
        }
        if (!writeVertex(c, m, buffer, fbW, fbH)) {
            return 0;
        }
        return 3;
    }

    private static boolean writeVertex(GeoVertex vertex, Matrix4f m, ByteBuffer buffer, float fbW, float fbH) {
        int offset = buffer.position();
        if (offset + BYTES_PER_VERTEX > buffer.capacity()) {
            buffer.position(offset);
            return false;
        }
        POSITION.set(vertex.position.getX(), vertex.position.getY(), vertex.position.getZ());
        m.transformPosition(POSITION);

        float nx = POSITION.x / fbW;
        float ny = POSITION.y / fbH;
        if (nx < -0.2F || nx > 1.2F || ny < -0.2F || ny > 1.2F) {
            buffer.position(offset);
            return false;   // модель уехала за экран - не рисуем этот треугольник
        }

        buffer.putFloat(nx);
        buffer.putFloat(ny);
        buffer.putFloat(0.0F);
        buffer.putFloat(vertex.textureU);
        buffer.putFloat(vertex.textureV);
        buffer.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF);
        return true;
    }

    // ── Плащи ────────────────────────────────────────────────────────────────

    private static String drawCape(PendingPreview pending, float rotationDeg) {
        CosmeticManager.CosmeticEntry entry = pending.entry();
        Identifier texture = entry.getTexture();
        if (texture == null) {
            return entry.name + ": no texture";
        }
        CosmeticManager.CapeAnimationInfo anim = CosmeticManager.getInstance().getCapeAnimation(texture);
        int frame = anim.getCurrentFrame();

        // Плащ 0.625 x 1.0 блока, рисуем две большие грани (внешнюю и внутреннюю)
        float halfW = 0.3125F;
        float height = 1.0F;
        float fit = Math.min(pending.w(), pending.h()) * 0.55F;

        float guiUnit = guiUnit();
        float centerX = (pending.x() + pending.w() * 0.5F) * guiUnit;
        float centerY = (pending.y() + pending.h() * 0.5F) * guiUnit;
        float fbW = MC.getWindow().getFramebufferWidth();
        float fbH = MC.getWindow().getFramebufferHeight();

        float angle = (float) Math.toRadians(rotationDeg);
        float cos = (float) Math.cos(angle);
        float halfOnScreen = Math.max(0.02F, Math.abs(cos)) * halfW * fit;

        ByteBuffer buffer = beginWrite();
        int vertices = 0;

        // внешняя грань
        vertices += writeScreenQuad(buffer,
                centerX - halfOnScreen, centerY - height * fit * 0.5F,
                centerX + halfOnScreen, centerY + height * fit * 0.5F,
                fbW, fbH,
                anim.getU(11.0F), anim.getV(0.0F, frame),
                anim.getU(1.0F), anim.getV(17.0F, frame));
        // внутренняя грань (зеркальный U)
        vertices += writeScreenQuad(buffer,
                centerX - halfOnScreen, centerY - height * fit * 0.5F,
                centerX + halfOnScreen, centerY + height * fit * 0.5F,
                fbW, fbH,
                anim.getU(1.0F), anim.getV(17.0F, frame),
                anim.getU(11.0F), anim.getV(0.0F, frame));

        return finishDraw(entry.name + ": cape frame=" + frame, texture, vertices,
                pending.clipX(), pending.clipY(), pending.clipW(), pending.clipH());
    }

    /** Квад в экранных координатах (y вниз), 2 треугольника. */
    private static int writeScreenQuad(ByteBuffer buffer,
                                       float x0, float y0, float x1, float y1,
                                       float fbW, float fbH,
                                       float u0, float v0, float u1, float v1) {
        float nx0 = x0 / fbW;
        float ny0 = y0 / fbH;
        float nx1 = x1 / fbW;
        float ny1 = y1 / fbH;

        if (!writeRaw(buffer, nx0, ny0, u0, v0)) return 0;
        if (!writeRaw(buffer, nx1, ny0, u1, v0)) return 0;
        if (!writeRaw(buffer, nx1, ny1, u1, v1)) return 0;
        if (!writeRaw(buffer, nx0, ny0, u0, v0)) return 0;
        if (!writeRaw(buffer, nx1, ny1, u1, v1)) return 0;
        if (!writeRaw(buffer, nx0, ny1, u0, v1)) return 0;
        return 6;
    }

    private static boolean writeRaw(ByteBuffer buffer, float nx, float ny, float u, float v) {
        int offset = buffer.position();
        if (offset + BYTES_PER_VERTEX > buffer.capacity()) {
            buffer.position(offset);
            return false;
        }
        buffer.putFloat(nx).putFloat(ny).putFloat(0.0F).putFloat(u).putFloat(v);
        buffer.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF);
        return true;
    }

    // ── Общая часть: буфер и вывод ───────────────────────────────────────────

    private static ByteBuffer beginWrite() {
        ensureBuffer();
        vertexData.clear();
        return vertexData;
    }

    private static String finishDraw(String info, Identifier texture, int vertices,
                                    float clipX, float clipY, float clipW, float clipH) {
        if (vertices <= 0) {
            return info + " [no verts]";
        }
        ensureBuffer();
        vertexData.position(0);
        vertexData.limit(vertices * BYTES_PER_VERTEX);

        try {
            GpuTextureView view = MC.getTextureManager().getTexture(texture).getGlTextureView();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(vertexBuffer.slice(0L, vertexData.remaining()), vertexData);

            RenderPass pass = encoder.createRenderPass(
                    () -> "decide:cosmetic_preview",
                    MC.getFramebuffer().getColorAttachmentView(),
                    OptionalInt.empty());
            try {
                pass.setPipeline(PIPELINE);
                pass.setVertexBuffer(0, vertexBuffer);
                // Клипаем по области списка: свой RenderPass не наследует scissor
                // ванильного GUI, и модель иначе вылезает за карточку при прокрутке.
                // Формула та же, что в GuiRenderer.enableScissor.
                int fbH = MC.getWindow().getFramebufferHeight();
                int guiScale = MC.getWindow().getScaleFactor();
                int sx = (int) (clipX * guiScale);
                int sy = (int) (fbH - (clipY + clipH) * guiScale);
                int sw = (int) (clipW * guiScale);
                int sh = (int) (clipH * guiScale);
                if (sw > 0 && sh > 0) {
                    pass.enableScissor(sx, sy, sw, sh);
                } else {
                    pass.disableScissor();
                }
                pass.bindTexture("uGui", view, RenderSampler.linear());
                pass.draw(0, vertices);
            } finally {
                pass.close();
            }
        } catch (Throwable t) {
            return info + " [draw error: " + t + "]";
        }
        return info;
    }

    private static synchronized void ensureBuffer() {
        if (vertexBuffer != null) {
            return;
        }
        vertexData = ByteBuffer.allocateDirect(MAX_VERTICES * BYTES_PER_VERTEX).order(ByteOrder.nativeOrder());
        // Порядок аргументов: (usage, size) - как в GuiCloseAnimationShatter.
        // С USAGE_COPY_DST иначе writeToBuffer не сможет записать данные.
        vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "decide:cosmetic_preview",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                MAX_VERTICES * BYTES_PER_VERTEX);
    }

    /**
     * Множитель из координат меню (scaledWidth-пространство) в пиксели фреймбуфера.
     * Раньше здесь было жёсткое 2.0 - работало только при GUI-масштабе 2.
     */
    private static float guiUnit() {
        int scaled = MC.getWindow().getScaledWidth();
        return scaled > 0 ? (float) MC.getWindow().getFramebufferWidth() / scaled : 2.0F;
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private CosmeticGuiPreview() {
    }
}
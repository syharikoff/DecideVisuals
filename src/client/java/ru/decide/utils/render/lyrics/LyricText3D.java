package ru.decide.utils.render.lyrics;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import ru.decide.utils.media.MediaLog;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * 3D-рендер текста с MSDF-атласом и glow через цепочку Kawase-фреймбуферов.
 * Порт {@code Text3D} из Kimiko без изменений в логике.
 *
 * Устроение кадра:
 * <ol>
 *   <li>{@link #begin} — считает ViewProjection, запоминает камеру и параметры свечения;</li>
 *   <li>{@link #plane} — задаёт базис плоскости (где стоит текст) и её масштаб;</li>
 *   <li>{@link #glyph} — по одному глифу на плоскость; состояние копится в CPU-буферы;</li>
 *   <li>{@link #end} — заливает буферы на GPU и рисует основной пасс, затем, если
 *       горит хоть один глиф, — glow-пасс и Kawase-размытие с композитой поверх кадра.</li>
 * </ol>
 *
 * Пайплайнов шесть: текст с тестом глубины, текст без него (сквозь стены), glow-источник,
 * kawase вниз, kawase вверх и композит glow. Состояние статическое, как и в Kimiko:
 * рендер идёт из одного места (мир), гонок нет.
 */
public final class LyricText3D {

    private static final LyricText3D INSTANCE = new LyricText3D();

    private static final Identifier DEPTH_PIPELINE_ID        = id("pipeline/effects/lyrics_text");
    private static final Identifier OVERLAY_PIPELINE_ID      = id("pipeline/effects/lyrics_text_overlay");
    private static final Identifier SHADER                   = id("core/lyrics_text_vertex");
    private static final Identifier FRAGMENT_SHADER          = id("core/lyrics_text_fragment");
    private static final Identifier GLOW_SHADER              = id("core/lyrics_glow_fragment");
    private static final Identifier GLOW_COMPOSITE_SHADER    = id("core/lyrics_glow_composite_fragment");
    private static final Identifier FULLSCREEN_VERTEX        = id("core/lyrics_glow_composite_vertex");
    private static final Identifier KAWASE_VERTEX            = id("core/lyrics_kawase_vertex");
    private static final Identifier KAWASE_DOWN_SHADER       = id("core/lyrics_kawase_down_fragment");
    private static final Identifier KAWASE_UP_SHADER         = id("core/lyrics_kawase_up_fragment");
    private static final Identifier GLOW_PIPELINE_ID         = id("pipeline/effects/lyrics_glow");
    private static final Identifier GLOW_DOWN_PIPELINE_ID    = id("pipeline/effects/lyrics_glow_down");
    private static final Identifier GLOW_UP_PIPELINE_ID      = id("pipeline/effects/lyrics_glow_up");
    private static final Identifier GLOW_COMPOSITE_PIPELINE_ID = id("pipeline/effects/lyrics_glow_composite");

    private static final String UNIFORM_BLOCK = "LyricsTextData";
    private static final String GLYPH_BLOCK   = "LyricsGlyphArray";
    private static final String SAMPLER       = "Atlas";
    private static final String KAWASE_PARAMS = "KawaseParams";
    private static final String GLOW_PARAMS   = "LyricsGlowParams";

    private static final int UNIFORM_BYTES      = 112;
    private static final int VERTEX_BYTES       = 24;   // 3 position + 2 uv + 1 slot
    private static final int QUAD_BYTES         = 96;   // 4 вершины
    private static final int PAGE_QUADS         = 256;
    private static final int GLYPH_PAGE_BYTES   = 8192; // 256 * (16 bounds + 16 effect)
    private static final int GLYPH_BUFFER_USAGE = 136;
    private static final int MAX_PAGES          = 8;
    private static final int MAX_QUADS          = 2048;
    private static final int GLOW_PASSES        = 2;
    private static final float GLOW_RENDER_SCALE = 0.5f;
    private static final float GLOW_OFFSET_SCALE = 1.25f;
    private static final int KAWASE_UNIFORM_BYTES = 48;
    private static final int GLOW_UNIFORM_BYTES   = 16;

    private static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("LineWidth", VertexFormatElement.LINE_WIDTH)
            .build();

    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();

    private static RenderPipeline depthPipeline;
    private static RenderPipeline overlayPipeline;
    private static RenderPipeline glowSourcePipeline;
    private static RenderPipeline glowDownPipeline;
    private static RenderPipeline glowUpPipeline;
    private static RenderPipeline glowCompositePipeline;

    private static GpuBuffer uniformBuffer;
    private static GpuBuffer vertexBuffer;
    private static GpuBuffer kawaseBuffer;
    private static GpuBuffer glowParamsBuffer;
    private static final GpuBuffer[] glyphBuffers = new GpuBuffer[MAX_PAGES];

    private static ByteBuffer uniformData;
    private static ByteBuffer glyphData;
    private static ByteBuffer vertexData;

    private static SimpleFramebuffer glowTarget;
    private static final SimpleFramebuffer[] glowDownTargets = new SimpleFramebuffer[GLOW_PASSES];
    private static final SimpleFramebuffer[] glowUpTargets = new SimpleFramebuffer[GLOW_PASSES];
    private static int glowWidth = -1;
    private static int glowHeight = -1;

    private static int capacity;
    private static int quads;
    private static boolean active;
    private static boolean failed;

    private static GpuTextureView atlasView;
    private static double cameraX, cameraY, cameraZ;
    private static double planeX, planeY, planeZ;
    private static float rightX, rightY, rightZ;
    private static float upX, upY, upZ;
    private static float planeScale, planeCenterX, planeCenterY;

    private static float grayLevel, heatBoost, glowStrength, blurReference, uvScale;
    private static float tintR = 1.0f, tintG = 1.0f, tintB = 1.0f, tintMix;
    private static float distanceRange = 4.0f;

    /** {@code distanceRange} текущего шрифта — разный у каждого MSDF-атласа. */
    public static void distanceRange(float value) {
        if (value > 0.0f) {
            distanceRange = value;
        }
    }
    private static float maxHeat;

    private LyricText3D() {}

    // ------------------------------------------------------------------ API

    public static void begin(Matrix4f modelView, Matrix4f projection, Vec3d camera,
                             float gray, float boost, float glow, float blurScale,
                             int tintRgb, boolean colored) {
        active = false;
        if (failed || modelView == null || projection == null || camera == null || !INSTANCE.ensureReady()) {
            return;
        }
        VIEW_PROJECTION.set((Matrix4fc) projection).mul((Matrix4fc) modelView);
        cameraX = camera.x;
        cameraY = camera.y;
        cameraZ = camera.z;
        grayLevel = gray;
        heatBoost = boost;
        glowStrength = Math.max(0.0f, glow);
        blurReference = Math.max(0.001f, blurScale);

        // Цвет текста: серый (как раньше) либо явный RGB с темой/градиентом.
        tintR = ((tintRgb >> 16) & 0xFF) / 255.0f;
        tintG = ((tintRgb >> 8) & 0xFF) / 255.0f;
        tintB = (tintRgb & 0xFF) / 255.0f;
        tintMix = colored ? 1.0f : 0.0f;
        uvScale = 0.0f;
        maxHeat = 0.0f;
        atlasView = null;
        quads = 0;
        active = true;
    }

    /** Базис плоскости: точка отсчёта, направления «вправо»/«вверх», масштаб и центр. */
    public static void plane(double x, double y, double z,
                             float toRightX, float toRightY, float toRightZ,
                             float toUpX, float toUpY, float toUpZ,
                             float scale, float centerX, float centerY) {
        planeX = x;
        planeY = y;
        planeZ = z;
        rightX = toRightX;
        rightY = toRightY;
        rightZ = toRightZ;
        upX = toUpX;
        upY = toUpY;
        upZ = toUpZ;
        planeScale = scale;
        planeCenterX = centerX;
        planeCenterY = centerY;
    }

    public static void glyph(LyricTextGeometry.Layout layout, LyricTextGeometry.Quad quad,
                             float shiftX, float shiftY, float blur, float heat, float alpha) {
        if (!active || layout == null || quad == null || alpha <= 0.004f) {
            return;
        }
        GpuTextureView view = layout.atlas();
        if (view == null || (atlasView != null && atlasView != view) || quads >= MAX_QUADS) {
            return;
        }
        if (!INSTANCE.ensureCapacity(quads + 1)) {
            return;
        }
        atlasView = view;

        // Радиус размытия задаём в UV, а шейдер берёт радиус в пикселях атласа:
        // пересчитываем через масштаб «локальная единица -> UV».
        float localWidth = quad.x1() - quad.x0();
        float uvWidth = quad.u1() - quad.u0();
        float uvPerLocal = localWidth > 1.0E-5f ? uvWidth / localWidth : 0.0f;
        float radius = blur * uvPerLocal;
        float padLocal = blur > 0.0f ? blur * 1.15f : 0.0f;
        float padUv = padLocal * uvPerLocal;
        uvScale = uvPerLocal;
        maxHeat = Math.max(maxHeat, heat * alpha);

        // Расширяем квад на паддинг, чтобы размытие не обрезалось краем глифа
        float leftX = quad.x0() - padLocal - planeCenterX + shiftX;
        float rightEdge = quad.x1() + padLocal - planeCenterX + shiftX;
        float topY = planeCenterY - (quad.y0() - padLocal) + shiftY;
        float bottomY = planeCenterY - (quad.y1() + padLocal) + shiftY;

        float u0 = quad.u0() - padUv;
        float u1 = quad.u1() + padUv;
        float v0 = quad.v0() - padUv;
        float v1 = quad.v1() + padUv;

        int page = quads / PAGE_QUADS;
        int slot = quads % PAGE_QUADS;
        int boundsOffset = page * GLYPH_PAGE_BYTES + slot * 16;
        int effectOffset = page * GLYPH_PAGE_BYTES + PAGE_QUADS * 16 + slot * 16;

        ByteBuffer glyphs = glyphData;
        glyphs.putFloat(boundsOffset, quad.u0());
        glyphs.putFloat(boundsOffset + 4, quad.v0());
        glyphs.putFloat(boundsOffset + 8, quad.u1());
        glyphs.putFloat(boundsOffset + 12, quad.v1());
        glyphs.putFloat(effectOffset, radius);
        glyphs.putFloat(effectOffset + 4, heat);
        glyphs.putFloat(effectOffset + 8, alpha);
        glyphs.putFloat(effectOffset + 12, 0.0f);

        int base = quads * QUAD_BYTES;
        INSTANCE.emit(base, leftX, topY, u0, v0, slot);
        INSTANCE.emit(base + VERTEX_BYTES, leftX, bottomY, u0, v1, slot);
        INSTANCE.emit(base + VERTEX_BYTES * 2, rightEdge, bottomY, u1, v1, slot);
        INSTANCE.emit(base + VERTEX_BYTES * 3, rightEdge, topY, u1, v0, slot);
        quads++;
    }

    public static void end(boolean throughWalls) {
        if (!active) {
            return;
        }
        active = false;
        if (quads == 0 || atlasView == null) {
            return;
        }

        Framebuffer target = MinecraftClient.getInstance().getFramebuffer();
        if (target == null || target.getColorAttachmentView() == null || target.getDepthAttachmentView() == null) {
            quads = 0;
            return;
        }

        int total = quads;
        quads = 0;
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

            ByteBuffer uniforms = uniformData;
            VIEW_PROJECTION.get(0, uniforms);
            uniforms.putFloat(64, grayLevel);
            uniforms.putFloat(68, heatBoost);
            uniforms.putFloat(72, glowStrength);
            uniforms.putFloat(76, Math.max(1.0E-6f, blurReference * uvScale));
            uniforms.putFloat(80, tintR);
            uniforms.putFloat(84, tintG);
            uniforms.putFloat(88, tintB);
            uniforms.putFloat(92, tintMix);
            uniforms.putFloat(96, distanceRange);
            uniforms.limit(UNIFORM_BYTES);
            encoder.writeToBuffer(uniformBuffer.slice(0L, UNIFORM_BYTES), uniforms);
            uniforms.clear();

            ByteBuffer vertices = vertexData;
            int bytes = total * QUAD_BYTES;
            vertices.limit(bytes);
            encoder.writeToBuffer(vertexBuffer.slice(0L, bytes), vertices);
            vertices.clear();

            int pages = (total + PAGE_QUADS - 1) / PAGE_QUADS;
            ByteBuffer glyphs = glyphData;
            for (int page = 0; page < pages; page++) {
                glyphs.limit((page + 1) * GLYPH_PAGE_BYTES);
                glyphs.position(page * GLYPH_PAGE_BYTES);
                encoder.writeToBuffer(INSTANCE.glyphBuffer(page).slice(0L, GLYPH_PAGE_BYTES), glyphs);
                glyphs.clear();
            }

            GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            RenderSystem.ShapeIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
            GpuBuffer indexBuffer = indices.getIndexBuffer(total * 6);

            // --- основной проход: текст поверх мира
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "white:lyrics_text",
                    target.getColorAttachmentView(), OptionalInt.empty(),
                    target.getDepthAttachmentView(), OptionalDouble.empty())) {
                pass.setPipeline(throughWalls ? overlayPipeline : depthPipeline);
                pass.setVertexBuffer(0, vertexBuffer);
                pass.setIndexBuffer(indexBuffer, indices.getIndexType());
                pass.setUniform(UNIFORM_BLOCK, uniformBuffer.slice());
                pass.bindTexture(SAMPLER, atlasView, sampler);
                for (int page = 0; page < pages; page++) {
                    int count = Math.min(PAGE_QUADS, total - page * PAGE_QUADS);
                    pass.setUniform(GLYPH_BLOCK, glyphBuffers[page].slice());
                    pass.drawIndexed(0, page * PAGE_QUADS * 6, count * 6, 1);
                }
            }

            if (glowStrength > 0.004f && maxHeat > 0.004f
                    && INSTANCE.ensureGlowTargets(target.textureWidth, target.textureHeight)) {
                INSTANCE.glowChain(encoder, target, sampler, indices, indexBuffer, total, pages, throughWalls);
            }
        } catch (Throwable error) {
            // Раньше здесь был пустой catch и фича просто гасла. Теперь причина
            // (обычно ошибка компиляции GLSL) попадает в лог под -Dwhite.lyrics.log
            MediaLog.note("gpu", "кадр упал, отключаю рендер текста: " + error);
            INSTANCE.disable();
        } finally {
            atlasView = null;
        }
    }

    /**
     * Подписывается на перезагрузку ресурсов (F3+T). Без этого кэш атласов и GPU-буферы
     * остались бы от предыдущих текстур, а шейдеры — от старой сборки пайплайнов.
     */
    public static void registerResourceReload() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public void reload(ResourceManager manager) {
                        invalidate();
                    }

                    @Override
                    public Identifier getFabricId() {
                        return Identifier.of("decide", "lyrics_text");
                    }
                });
    }

    /**
     * Сброс после перезагрузки ресурсов: шейдеры пересобираются, ресурсы GPU больше невалидны.
     * Заодно снимаем флаг {@link #failed} — после перезагрузки всё должно заработать заново.
     */
    public static void invalidate() {
        active = false;
        quads = 0;
        capacity = 0;
        atlasView = null;
        failed = false;
        LyricTextGeometry.invalidate();
        INSTANCE.releaseGpu();
    }

    // ------------------------------------------------------------------ glow

    private void glowChain(CommandEncoder encoder, Framebuffer target, GpuSampler sampler,
                           RenderSystem.ShapeIndexBuffer indices, GpuBuffer indexBuffer,
                           int total, int pages, boolean throughWalls) {

        SimpleFramebuffer glow = glowTarget;

        // 1. Чистим half-res цель и, если текст не сквозь стены, копируем в неё глубину мира
        try (RenderPass pass = encoder.createRenderPass(
                () -> "white:lyrics_glow_clear",
                glow.getColorAttachmentView(), OptionalInt.of(0),
                glow.getDepthAttachmentView(), OptionalDouble.of(1.0))) {
            // пустой пасс — только очистка
        }
        if (!throughWalls && target.getDepthAttachment() != null && glow.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(target.getDepthAttachment(), glow.getDepthAttachment(),
                    0, 0, 0, 0, 0, glowWidth, glowHeight);
        }

        // 2. Рисуем только «горящие» глифы в glow-цель — это и есть маска для размытия
        try (RenderPass pass = encoder.createRenderPass(
                () -> "white:lyrics_glow_source",
                glow.getColorAttachmentView(), OptionalInt.empty(),
                glow.getDepthAttachmentView(), OptionalDouble.empty())) {
            pass.setPipeline(glowSourcePipeline);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setIndexBuffer(indexBuffer, indices.getIndexType());
            pass.setUniform(UNIFORM_BLOCK, uniformBuffer.slice());
            pass.bindTexture(SAMPLER, atlasView, sampler);
            for (int page = 0; page < pages; page++) {
                int count = Math.min(PAGE_QUADS, total - page * PAGE_QUADS);
                pass.setUniform(GLYPH_BLOCK, glyphBuffers[page].slice());
                pass.drawIndexed(0, page * PAGE_QUADS * 6, count * 6, 1);
            }
        }

        // 3. Kawase: спуск по пирамиде и обратный подъём — дешёвое широкое свечение
        GpuTextureView current = glowDownTargets[0].getColorAttachmentView();
        int width = glowDownTargets[0].textureWidth;
        int height = glowDownTargets[0].textureHeight;
        for (int index = 1; index < GLOW_PASSES; index++) {
            kawase(encoder, glowDownPipeline, current, width, height, glowDownTargets[index], sampler);
            current = glowDownTargets[index].getColorAttachmentView();
            width = glowDownTargets[index].textureWidth;
            height = glowDownTargets[index].textureHeight;
        }
        for (int index = 0; index >= 0; index--) {
            kawase(encoder, glowUpPipeline, current, width, height, glowUpTargets[index], sampler);
            current = glowUpTargets[index].getColorAttachmentView();
            width = glowUpTargets[index].textureWidth;
            height = glowUpTargets[index].textureHeight;
        }

        // 4. Композит поверх кадра: reinhard-тонмаппинг, чтобы яркое не выбивалось в белое
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(GLOW_UNIFORM_BYTES);
            data.putFloat(0, glowStrength * 0.5f);
            data.position(0);
            encoder.writeToBuffer(glowParamsBuffer.slice(0L, GLOW_UNIFORM_BYTES), data);
        }
        try (RenderPass pass = encoder.createRenderPass(
                () -> "white:lyrics_glow_composite",
                target.getColorAttachmentView(), OptionalInt.empty())) {
            pass.setPipeline(glowCompositePipeline);
            pass.bindTexture("Sampler0", current, sampler);
            pass.setUniform(GLOW_PARAMS, glowParamsBuffer);
            pass.draw(0, 6);
        }
    }

    private void kawase(CommandEncoder encoder, RenderPipeline pipeline, GpuTextureView source,
                        int sourceWidth, int sourceHeight, SimpleFramebuffer target, GpuSampler sampler) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(KAWASE_UNIFORM_BYTES);
            data.putFloat(0, 0.0f);
            data.putFloat(4, 0.0f);
            data.putFloat(8, 1.0f);
            data.putFloat(12, 1.0f);
            data.putFloat(16, GLOW_OFFSET_SCALE / Math.max(sourceWidth, 1));
            data.putFloat(20, GLOW_OFFSET_SCALE / Math.max(sourceHeight, 1));
            data.putFloat(32, 0.0f);
            data.putFloat(36, 0.0f);
            data.putFloat(40, 0.0f);
            data.putFloat(44, 1.0f);
            data.position(0);
            encoder.writeToBuffer(kawaseBuffer.slice(0L, KAWASE_UNIFORM_BYTES), data);
        }
        try (RenderPass pass = encoder.createRenderPass(
                () -> "white:lyrics_glow_kawase",
                target.getColorAttachmentView(), OptionalInt.empty())) {
            pass.setPipeline(pipeline);
            pass.bindTexture("Sampler0", source, sampler);
            pass.setUniform(KAWASE_PARAMS, kawaseBuffer);
            pass.draw(0, 6);
        }
    }

    private boolean ensureGlowTargets(int width, int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        if (glowTarget != null && glowWidth == width && glowHeight == height) {
            return true;
        }
        closeGlowTargets();

        glowTarget = new SimpleFramebuffer("decide_lyrics_glow", width, height, true);
        int currentWidth = Math.max(1, Math.round(width * GLOW_RENDER_SCALE));
        int currentHeight = Math.max(1, Math.round(height * GLOW_RENDER_SCALE));
        for (int index = 0; index < GLOW_PASSES; index++) {
            glowDownTargets[index] = new SimpleFramebuffer("decide_lyrics_glow_down_" + index, currentWidth, currentHeight, false);
            if (index < GLOW_PASSES - 1) {
                glowUpTargets[index] = new SimpleFramebuffer("decide_lyrics_glow_up_" + index, currentWidth, currentHeight, false);
            }
            currentWidth = Math.max(currentWidth / 2, 1);
            currentHeight = Math.max(currentHeight / 2, 1);
        }
        glowWidth = width;
        glowHeight = height;
        return true;
    }

    private void closeGlowTargets() {
        if (glowTarget != null) {
            glowTarget.delete();
        }
        glowTarget = null;
        for (int index = 0; index < GLOW_PASSES; index++) {
            if (glowDownTargets[index] != null) {
                glowDownTargets[index].delete();
            }
            glowDownTargets[index] = null;
            if (glowUpTargets[index] != null) {
                glowUpTargets[index].delete();
            }
            glowUpTargets[index] = null;
        }
        glowWidth = -1;
        glowHeight = -1;
    }

    // ----------------------------------------------------------------- internals

    private void emit(int cursor, float localX, float localY, float u, float v, int slot) {
        float scaledX = localX * planeScale;
        float scaledY = localY * planeScale;
        // Базис задаётся относительно камеры — так матрица остаётся единичной
        float x = (float) (planeX - cameraX) + rightX * scaledX + upX * scaledY;
        float y = (float) (planeY - cameraY) + rightY * scaledX + upY * scaledY;
        float z = (float) (planeZ - cameraZ) + rightZ * scaledX + upZ * scaledY;
        ByteBuffer vertices = vertexData;
        vertices.putFloat(cursor, x);
        vertices.putFloat(cursor + 4, y);
        vertices.putFloat(cursor + 8, z);
        vertices.putFloat(cursor + 12, u);
        vertices.putFloat(cursor + 16, v);
        vertices.putFloat(cursor + 20, slot);
    }

    private GpuBuffer glyphBuffer(int page) {
        GpuBuffer buffer = glyphBuffers[page];
        if (buffer == null || buffer.isClosed()) {
            glyphBuffers[page] = buffer = RenderSystem.getDevice().createBuffer(
                    () -> "white:lyrics_text_glyphs", GLYPH_BUFFER_USAGE, GLYPH_PAGE_BYTES);
        }
        return buffer;
    }

    private boolean ensureCapacity(int required) {
        if (required <= capacity) {
            return true;
        }
        if (required > MAX_QUADS) {
            return false;
        }
        int target = Math.min(MAX_QUADS, Math.max(PAGE_QUADS, Integer.highestOneBit(required - 1) * 2));
        try {
            ByteBuffer resized = MemoryUtil.memRealloc(vertexData, target * QUAD_BYTES);
            if (resized == null) {
                return false;
            }
            vertexData = resized;
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }
            vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "white:lyrics_text_vertices", 40, (long) target * QUAD_BYTES);
            capacity = target;
            return true;
        } catch (Throwable error) {
            MediaLog.note("gpu", "не удалось расширить вершинный буфер: " + error);
            disable();
            return false;
        }
    }

    private boolean ensureReady() {
        if (depthPipeline != null && overlayPipeline != null
                && uniformBuffer != null && uniformData != null && glyphData != null) {
            return true;
        }
        try {
            if (depthPipeline == null) {
                depthPipeline = textPipeline(DEPTH_PIPELINE_ID, DepthTestFunction.LEQUAL_DEPTH_TEST);
            }
            if (overlayPipeline == null) {
                overlayPipeline = textPipeline(OVERLAY_PIPELINE_ID, DepthTestFunction.NO_DEPTH_TEST);
            }
            if (uniformData == null) {
                uniformData = MemoryUtil.memAlloc(UNIFORM_BYTES);
            }
            if (glyphData == null) {
                glyphData = MemoryUtil.memAlloc(MAX_PAGES * GLYPH_PAGE_BYTES);
            }
            if (uniformBuffer == null || uniformBuffer.isClosed()) {
                uniformBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "white:lyrics_text_uniform", GLYPH_BUFFER_USAGE, (long) UNIFORM_BYTES);
            }
            if (glowSourcePipeline == null) {
                glowSourcePipeline = RenderPipelines.register(RenderPipeline.builder()
                        .withLocation(GLOW_PIPELINE_ID)
                        .withVertexShader(SHADER)
                        .withFragmentShader(GLOW_SHADER)
                        .withVertexFormat(FORMAT, VertexFormat.DrawMode.QUADS)
                        .withUniform(UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER)
                        .withUniform(GLYPH_BLOCK, UniformType.UNIFORM_BUFFER)
                        .withSampler(SAMPLER)
                        .withBlend(BlendFunction.ADDITIVE)
                        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build());
            }
            if (glowDownPipeline == null) {
                glowDownPipeline = kawasePipeline(GLOW_DOWN_PIPELINE_ID, KAWASE_DOWN_SHADER);
            }
            if (glowUpPipeline == null) {
                glowUpPipeline = kawasePipeline(GLOW_UP_PIPELINE_ID, KAWASE_UP_SHADER);
            }
            if (glowCompositePipeline == null) {
                glowCompositePipeline = RenderPipelines.register(RenderPipeline.builder()
                        .withLocation(GLOW_COMPOSITE_PIPELINE_ID)
                        .withVertexShader(FULLSCREEN_VERTEX)
                        .withFragmentShader(GLOW_COMPOSITE_SHADER)
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .withUniform(GLOW_PARAMS, UniformType.UNIFORM_BUFFER)
                        .withSampler("Sampler0")
                        .withBlend(BlendFunction.LIGHTNING)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build());
            }
            if (kawaseBuffer == null || kawaseBuffer.isClosed()) {
                kawaseBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "white:lyrics_glow_kawase", GLYPH_BUFFER_USAGE, (long) KAWASE_UNIFORM_BYTES);
            }
            if (glowParamsBuffer == null || glowParamsBuffer.isClosed()) {
                glowParamsBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "white:lyrics_glow_params", GLYPH_BUFFER_USAGE, (long) GLOW_UNIFORM_BYTES);
            }
            return true;
        } catch (Throwable error) {
            MediaLog.note("gpu", "не удалось создать пайплайны/буферы: " + error);
            disable();
            return false;
        }
    }

    private RenderPipeline textPipeline(Identifier location, DepthTestFunction depth) {
        return RenderPipelines.register(RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(SHADER)
                .withFragmentShader(FRAGMENT_SHADER)
                .withVertexFormat(FORMAT, VertexFormat.DrawMode.QUADS)
                .withUniform(UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER)
                .withUniform(GLYPH_BLOCK, UniformType.UNIFORM_BUFFER)
                .withSampler(SAMPLER)
                .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                .withDepthTestFunction(depth)
                .withDepthWrite(false)
                .withCull(false)
                .build());
    }

    private RenderPipeline kawasePipeline(Identifier location, Identifier fragment) {
        return RenderPipelines.register(RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(KAWASE_VERTEX)
                .withFragmentShader(fragment)
                .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                .withUniform(KAWASE_PARAMS, UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build());
    }

    private void releaseGpu() {
        closeGlowTargets();
        closeBuffer(kawaseBuffer);
        kawaseBuffer = null;
        closeBuffer(glowParamsBuffer);
        glowParamsBuffer = null;
        closeBuffer(uniformBuffer);
        uniformBuffer = null;
        closeBuffer(vertexBuffer);
        vertexBuffer = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            closeBuffer(glyphBuffers[page]);
            glyphBuffers[page] = null;
        }
        freeBuffer(uniformData);
        uniformData = null;
        freeBuffer(glyphData);
        glyphData = null;
        freeBuffer(vertexData);
        vertexData = null;
    }

    private void disable() {
        failed = true;
        releaseGpu();
        depthPipeline = null;
        overlayPipeline = null;
        glowSourcePipeline = null;
        glowDownPipeline = null;
        glowUpPipeline = null;
        glowCompositePipeline = null;
    }

    private static void closeBuffer(GpuBuffer buffer) {
        if (buffer != null) {
            buffer.close();
        }
    }

    private static void freeBuffer(ByteBuffer buffer) {
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
        }
    }

    private static Identifier id(String path) {
        return Identifier.of("decide", path);
    }
}

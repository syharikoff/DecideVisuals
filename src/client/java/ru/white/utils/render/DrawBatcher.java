package ru.white.utils.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Координатор батчинга 2D-пайплайнов.
 *
 * Батчинг включается только внутри контролируемой зоны
 * (Render2D.beginOverlay() .. flushAll()/endOverlay()). Вне её каждый вызов
 * рисуется немедленно собственным пассом ({@link #drawImmediate}).
 *
 * Главное отличие от старой схемы: весь сброс ({@link #flushPending}) уходит
 * ОДНИМ RenderPass. Пайплайны сначала загружают uniform-данные на GPU
 * (uploadBatch), затем по очереди рисуют в общий пасс (drawBatch), меняя
 * внутри пасса только pipeline/биндинги. Общие uniform'ы (Projection,
 * DynamicTransforms и т.д.) пишутся один раз на пасс.
 *
 * Порядок слоёв при сбросе: блюр-подложки (-1), заливки (0), обводки (1),
 * текстуры (2), текст (3). Внутри слоя — порядок первого использования.
 */
public final class DrawBatcher {

    public interface Batched {

        /**
         * Слой при сбросе: меньше — ниже. Блюр (-1) под заливками (0),
         * заливки под обводками (1), обводки под текстурами (2), текст (3) сверху.
         */
        default int batchLayer() {
            return 0;
        }

        /** Фаза 1: загрузить накопленные uniform-данные на GPU (вне рендер-пасса). */
        void uploadBatch(CommandEncoder encoder);

        /** Фаза 2: нарисовать накопленное в (общий) рендер-пасс. */
        void drawBatch(RenderPass pass);

        /** Выбросить накопленное без отрисовки (например, нет фреймбуфера). */
        void discardBatch();
    }

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static final ArrayList<Batched> active = new ArrayList<>(8);
    private static boolean enabled = false;

    private DrawBatcher() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        if (!value) {
            flushPending();
        }
        enabled = value;
    }

    /** Пайплайн сообщает, что начал копить данные (порядок регистрации = порядок внутри слоя). */
    public static void register(Batched owner) {
        if (!active.contains(owner)) {
            active.add(owner);
        }
    }

    /**
     * Сбросить все накопленные батчи одним RenderPass. Порядок — по
     * {@link Batched#batchLayer()} (сортировка стабильна: внутри слоя
     * сохраняется порядок первого использования).
     */
    public static void flushPending() {
        if (active.isEmpty()) return;
        // копия — во время сброса кто-то может опять зарегистрироваться
        Batched[] pending = active.toArray(new Batched[0]);
        active.clear();
        Arrays.sort(pending, Comparator.comparingInt(Batched::batchLayer));

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null) {
            for (Batched batch : pending) {
                batch.discardBatch();
            }
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (Batched batch : pending) {
            batch.uploadBatch(encoder);
        }

        // ВАЖНО: запись буферов (dynamic uniforms) — строго ДО открытия пасса,
        // GlCommandEncoder запрещает writeToBuffer при открытом рендер-пассе
        GpuBufferSlice dynamicTransforms = writeDynamicTransforms();

        try (RenderPass pass = encoder.createRenderPass(
                () -> "client:2d_batch",
                client.getFramebuffer().getColorAttachmentView(),
                OptionalInt.empty(),
                client.getFramebuffer().getDepthAttachmentView(),
                OptionalDouble.empty())) {

            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            for (Batched batch : pending) {
                batch.drawBatch(pass);
            }
        } finally {
            // Если чей-то drawBatch упал — не даём остальным копить данные вечно
            for (Batched batch : pending) {
                batch.discardBatch();
            }
        }
    }

    /**
     * Немедленная отрисовка накопленного одним пайплайном собственным пассом
     * (вне зоны батчинга или при переполнении кольца буферов).
     */
    public static void drawImmediate(Batched owner) {
        drawImmediate(owner, true);
    }

    /**
     * То же, но с возможностью пропустить фазу upload — для сброса уже
     * запечатанных чанков без повторного запечатывания (избегает рекурсии
     * uploadBatch → seal → drawImmediate).
     */
    public static void drawImmediate(Batched owner, boolean upload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null) {
            owner.discardBatch();
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (upload) {
            owner.uploadBatch(encoder);
        }

        // Запись буферов — строго до открытия пасса (см. flushPending)
        GpuBufferSlice dynamicTransforms = writeDynamicTransforms();

        try (RenderPass pass = encoder.createRenderPass(
                () -> "client:2d_immediate",
                client.getFramebuffer().getColorAttachmentView(),
                OptionalInt.empty(),
                client.getFramebuffer().getDepthAttachmentView(),
                OptionalDouble.empty())) {

            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            owner.drawBatch(pass);
        } finally {
            owner.discardBatch();
        }
    }

    /** Единая запись матриц на пасс вместо записи в каждом пайплайне. */
    private static GpuBufferSlice writeDynamicTransforms() {
        return RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
    }
}

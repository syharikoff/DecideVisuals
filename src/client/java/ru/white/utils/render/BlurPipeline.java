package ru.white.utils.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Рисует блюр-ректы поверх захваченного кадра (ScreenBlur).
 *
 * В зоне батчинга (HUD) вызовы копятся и уходят одним RenderPass:
 * uniform-данные каждого ректа пишутся в свой буфер из кольца, а в пассе
 * между draw'ами меняется только биндинг BlurData. Вне зоны батчинга
 * (экраны) каждый вызов рисуется немедленно, как раньше.
 */
public class BlurPipeline implements DrawBatcher.Batched {

    private static final Identifier PIPELINE_ID = Identifier.of("client", "pipeline/blur");
    private static final Identifier VERTEX_SHADER = Identifier.of("client", "core/blur");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("client", "core/blur");

    private static final float FIXED_GUI_SCALE = 2.0f;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final int BUFFER_SIZE = 128;
    // Кольцо uniform-буферов: один общий буфер нельзя перезаписывать несколько
    // раз за кадр — GPU исполняет draw позже записи, и панели рисуются с чужими
    // данными (случайно «пропадают»). Аналогично KawaseBlurPipeline (буфер на проход).
    private static final int UNIFORM_RING = 64;

    private GpuBuffer[] uniformBuffers;
    private int uniformRingIndex = 0;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    // Накопленные блюр-ректы текущего батча: uniform-данные уже загружены,
    // осталось только забиндить и нарисовать
    private final ArrayList<GpuBuffer> pendingDraws = new ArrayList<>();

    private int getFixedScaledWidth() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return 960;
        return (int) Math.ceil((double) client.getWindow().getFramebufferWidth() / FIXED_GUI_SCALE);
    }

    private int getFixedScaledHeight() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return 540;
        return (int) Math.ceil((double) client.getWindow().getFramebufferHeight() / FIXED_GUI_SCALE);
    }

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "client:blur_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        this.uniformBuffers = new GpuBuffer[UNIFORM_RING];

        initialized = true;
    }

    private GpuBuffer nextUniformBuffer() {
        GpuBuffer buf = uniformBuffers[uniformRingIndex];
        if (buf == null) {
            final int idx = uniformRingIndex;
            buf = RenderSystem.getDevice().createBuffer(
                    () -> "client:blur_uniform_" + idx,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    BUFFER_SIZE
            );
            uniformBuffers[uniformRingIndex] = buf;
        }
        uniformRingIndex = (uniformRingIndex + 1) % UNIFORM_RING;
        return buf;
    }

    public void drawBlur(float x, float y, float width, float height,
                         float radius, float[] radii, int color) {
        drawBlur(x, y, width, height, radius, radii, color, 0f, 0f, 0f, 0f);
    }

    public void drawGlassBlur(float x, float y, float width, float height,
                              float alpha, float[] radii, int tintColor,
                              float distortion, float waveSize, float edgeLight, float shine) {
        drawBlur(x, y, width, height, alpha, radii, tintColor,
                distortion, waveSize, edgeLight, shine);
    }

    private void drawBlur(float x, float y, float width, float height,
                          float radius, float[] radii, int color,
                          float distortion, float waveSize, float edgeLight, float shine) {
        // Отмечаем ДО проверки isReady — иначе захват никогда не включится
        ScreenBlur.markUsed();
        if (!ScreenBlur.isReady()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) return;

        ensureInitialized();

        int fbWidth = ScreenBlur.getCachedWidth();
        int fbHeight = ScreenBlur.getCachedHeight();
        int fixedScreenWidth = getFixedScaledWidth();
        int fixedScreenHeight = getFixedScaledHeight();

        prepareUniformData(x, y, width, height,
                fixedScreenWidth, fixedScreenHeight,
                fbWidth, fbHeight,
                2, radius, radii, color,
                distortion, waveSize, edgeLight, shine);

        GpuBuffer uniformBuffer = nextUniformBuffer();
        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(uniformBuffer.slice(0, dataBuffer.remaining()), dataBuffer);
        pendingDraws.add(uniformBuffer);

        if (DrawBatcher.isEnabled()) {
            DrawBatcher.register(this);
            // Кольцо буферов не резиновое — при переполнении сбрасываем досрочно
            if (pendingDraws.size() >= UNIFORM_RING) {
                DrawBatcher.drawImmediate(this);
            }
        } else {
            // Немедленный режим: сначала выпускаем накопленные батчи (порядок отрисовки)
            DrawBatcher.flushPending();
            DrawBatcher.drawImmediate(this);
        }
    }

    @Override
    public int batchLayer() {
        return -1; // блюр-подложки — под заливками, обводками и текстом
    }

    @Override
    public void uploadBatch(CommandEncoder encoder) {
        // uniform-данные уже загружены в момент вызова drawBlur
    }

    @Override
    public void drawBatch(RenderPass pass) {
        if (pendingDraws.isEmpty()) return;
        if (!ScreenBlur.isReady()) {
            pendingDraws.clear();
            return;
        }

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        pass.setPipeline(PIPELINE);
        pass.setVertexBuffer(0, dummyVertexBuffer);
        pass.bindTexture("Sampler0", ScreenBlur.getBlurredView(), sampler);

        for (GpuBuffer uniformBuffer : pendingDraws) {
            pass.setUniform("BlurData", uniformBuffer);
            pass.draw(0, 6);
        }

        pendingDraws.clear();
    }

    @Override
    public void discardBatch() {
        pendingDraws.clear();
    }

    public void flush() {
        if (pendingDraws.isEmpty()) return;
        DrawBatcher.drawImmediate(this);
    }

    private void prepareUniformData(float x, float y, float width, float height,
                                    float screenWidth, float screenHeight,
                                    int fbWidth, int fbHeight,
                                    float guiScale, float blurRadius,
                                    float[] radii, int color,
                                    float distortion, float waveSize, float edgeLight, float shine) {
        dataBuffer.clear();

        dataBuffer.putFloat(x);
        dataBuffer.putFloat(y);
        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);

        dataBuffer.putFloat(screenWidth);
        dataBuffer.putFloat(screenHeight);
        dataBuffer.putFloat(guiScale);
        dataBuffer.putFloat(blurRadius);

        dataBuffer.putFloat(fbWidth);
        dataBuffer.putFloat(fbHeight);
        dataBuffer.putFloat(0);
        dataBuffer.putFloat(0);

        dataBuffer.putFloat(radii[0]);
        dataBuffer.putFloat(radii[1]);
        dataBuffer.putFloat(radii[2]);
        dataBuffer.putFloat(radii[3]);

        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        dataBuffer.putFloat(r);
        dataBuffer.putFloat(g);
        dataBuffer.putFloat(b);
        dataBuffer.putFloat(a);

        dataBuffer.putFloat(Math.max(0f, distortion));
        dataBuffer.putFloat(Math.max(0f, waveSize));
        dataBuffer.putFloat(Math.max(0f, edgeLight));
        dataBuffer.putFloat(Math.max(0f, shine));

        dataBuffer.flip();
    }

    public void close() {
        pendingDraws.clear();
        if (uniformBuffers != null) {
            for (GpuBuffer buf : uniformBuffers) {
                if (buf != null) buf.close();
            }
            uniformBuffers = null;
        }
        uniformRingIndex = 0;
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
        initialized = false;
    }
}

package ru.white.utils.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.module.impl.render.ShaderSky;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.OptionalInt;

public class ShaderSkyRenderer {

    private static final Identifier SHADER_PIPELINE_ID = Identifier.of("client", "pipeline/shader_sky");
    private static final Identifier BLUR_PIPELINE_ID = Identifier.of("client", "pipeline/shader_sky_blur");
    private static final Identifier VERTEX_SHADER = Identifier.of("client", "core/shader_sky");
    private static final Identifier FULLSCREEN_VERTEX_SHADER = Identifier.of("client", "core/shader_sky_fullscreen");
    private static final Identifier SHADER_FRAGMENT = Identifier.of("client", "core/shader_sky");
    private static final Identifier BLUR_FRAGMENT = Identifier.of("client", "core/shader_sky_blur");

    private static final RenderPipeline SHADER_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(SHADER_PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(SHADER_FRAGMENT)
                    .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("ShaderSkyData", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline BLUR_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(BLUR_PIPELINE_ID)
                    .withVertexShader(FULLSCREEN_VERTEX_SHADER)
                    .withFragmentShader(BLUR_FRAGMENT)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("ShaderSkyData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int BUFFER_SIZE = 96;

    private static ShaderSkyRenderer instance;

    private final MinecraftClient client;
    private final KawaseBlurPipeline blurPipeline = new KawaseBlurPipeline();
    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private GpuBuffer celestialVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;
    private boolean enabled = false;
    private long startTime = System.currentTimeMillis();

    public ShaderSkyRenderer() {
        client = MinecraftClient.getInstance();
        instance = this;
    }

    public static ShaderSkyRenderer getInstance() {
        if (instance == null) {
            instance = new ShaderSkyRenderer();
        }
        return instance;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            ensureInitialized();
            startTime = System.currentTimeMillis();
        }
    }

    private void ensureInitialized() {
        if (initialized) return;

        dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummy = MemoryUtil.memAlloc(4);
        dummy.putInt(0);
        dummy.flip();
        dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "client:shader_sky_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummy
        );
        MemoryUtil.memFree(dummy);

        uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "client:shader_sky_uniform",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                BUFFER_SIZE
        );

        ByteBuffer celestialVertices = createCelestialQuad();
        celestialVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "client:shader_sky_celestial_vertex",
                GpuBuffer.USAGE_VERTEX,
                celestialVertices
        );
        MemoryUtil.memFree(celestialVertices);

        initialized = true;
    }

    public void renderSky() {
        ShaderSky module = ShaderSky.getInstance();
        if (!enabled || module == null || !module.isEnabled()) return;
        if (!module.mode.is("Blur")) return;

        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null || framebuffer.getColorAttachmentView() == null) return;

        ensureInitialized();

        renderBlur(framebuffer, module);
    }

    public void renderCelestialShader() {
        ShaderSky module = ShaderSky.getInstance();
        if (!enabled || module == null || !module.isEnabled() || module.mode.is("Blur")) return;

        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null || framebuffer.getColorAttachmentView() == null) return;

        ensureInitialized();
        writeUniforms(framebuffer.textureWidth, framebuffer.textureHeight, module);
        drawSkybox(framebuffer);
    }

    private void renderBlur(Framebuffer framebuffer, ShaderSky module) {
        if (framebuffer.getColorAttachment() == null || framebuffer.getColorAttachmentView() == null) return;

        GpuTextureView blurred = blurPipeline.blur(
                framebuffer.getColorAttachment(),
                framebuffer.getColorAttachmentView(),
                framebuffer.textureWidth,
                framebuffer.textureHeight,
                3,
              3
        );
        if (blurred == null) return;

        writeUniforms(framebuffer.textureWidth, framebuffer.textureHeight, module);
        drawFullscreen(framebuffer, BLUR_PIPELINE, blurred);
    }

    private void writeUniforms(int width, int height, ShaderSky module) {
        float alpha = getShaderAlpha(module);
        Color color1 = new Color(ShaderSky.geteColor1(), true);
        Color color2 = new Color(ShaderSky.geteColor2(), true);
        Color accentColor = Color.WHITE;

        dataBuffer.clear();
        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);
        dataBuffer.putFloat((System.currentTimeMillis() - startTime) / 1000.0f);
        dataBuffer.putFloat(alpha);
        putColor(dataBuffer, color1, alpha);
        putColor(dataBuffer, color2, alpha);
        putColor(dataBuffer, accentColor, alpha);
        dataBuffer.putFloat(getModeId(module.mode.getValue()));
        dataBuffer.putFloat(module.speed.getValue());
        dataBuffer.putFloat(module.scale.getValue());
        dataBuffer.putFloat(module.intensity.getValue());
        dataBuffer.putFloat(module.stars.getValue());
        dataBuffer.putFloat(module.vanillaSky.getValue() ? 1.0f : 0.0f);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);
        dataBuffer.flip();

        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uniformBuffer.slice(), dataBuffer);
    }

    private void drawSkybox(Framebuffer framebuffer) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "client:shader_sky_pass",
                framebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                framebuffer.getDepthAttachmentView(),
                java.util.OptionalDouble.empty())) {

            renderPass.setPipeline(SHADER_PIPELINE);
            renderPass.setVertexBuffer(0, celestialVertexBuffer);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", transforms);
            renderPass.setUniform("ShaderSkyData", uniformBuffer);
            renderPass.draw(0, 36);
        }
    }

    private static void putColor(ByteBuffer buffer, Color color, float opacity) {
        buffer.putFloat(color.getRed() / 255.0f);
        buffer.putFloat(color.getGreen() / 255.0f);
        buffer.putFloat(color.getBlue() / 255.0f);
        buffer.putFloat(color.getAlpha() / 255.0f * opacity);
    }

    private static int getModeId(String mode) {
        return switch (mode) {
            case "Night" -> 1;
            case "Snow" -> 2;
            case "Sky" -> 3;
            case "Star" -> 4;
            case "Glow" -> 5;
            case "Full" -> 6;
            case "WebShader" -> 7;
            case "Plasma" -> 8;
            case "ChamsFill" -> 9;
            case "BaseWarp" -> 10;
            case "Waves" -> 11;
            case "PhobAurora" -> 12;
            case "BlackHole" -> 13;
            case "Galaxy" -> 14;
            default -> 0;
        };
    }

    private static float getShaderAlpha(ShaderSky module) {
        if (module.mode.is("Blur")) {
            return module.intensity.getValue();
        }
        if (module.vanillaSky.getValue()) {
            return 0.78f;
        }
        return 1.0f;
    }

    private void drawFullscreen(Framebuffer framebuffer, RenderPipeline pipeline, GpuTextureView texture) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "client:shader_sky_pass",
                framebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                framebuffer.getDepthAttachmentView(),
                java.util.OptionalDouble.empty())) {

            renderPass.setPipeline(pipeline);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            if (texture != null) {
                GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
                renderPass.bindTexture("Sampler0", texture, sampler);
            }

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", transforms);
            renderPass.setUniform("ShaderSkyData", uniformBuffer);
            renderPass.draw(0, 6);
        }
    }

    private static ByteBuffer createCelestialQuad() {
        float s = 512.0f;
        float[] vertices = new float[] {
                -s, -s, -s,  s, -s, -s,  s,  s, -s,
                -s, -s, -s,  s,  s, -s, -s,  s, -s,

                s, -s,  s, -s, -s,  s, -s,  s,  s,
                s, -s,  s, -s,  s,  s,  s,  s,  s,

                -s, -s,  s, -s, -s, -s, -s,  s, -s,
                -s, -s,  s, -s,  s, -s, -s,  s,  s,

                s, -s, -s,  s, -s,  s,  s,  s,  s,
                s, -s, -s,  s,  s,  s,  s,  s, -s,

                -s,  s, -s,  s,  s, -s,  s,  s,  s,
                -s,  s, -s,  s,  s,  s, -s,  s,  s,

                -s, -s,  s,  s, -s,  s,  s, -s, -s,
                -s, -s,  s,  s, -s, -s, -s, -s, -s
        };
        ByteBuffer buffer = MemoryUtil.memAlloc(vertices.length * Float.BYTES);
        for (float vertex : vertices) {
            buffer.putFloat(vertex);
        }
        buffer.flip();
        return buffer;
    }

    public void invalidate() {
        enabled = false;
        close();
    }

    public void close() {
        if (uniformBuffer != null) { uniformBuffer.close(); uniformBuffer = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (celestialVertexBuffer != null) { celestialVertexBuffer.close(); celestialVertexBuffer = null; }
        if (dataBuffer != null) { MemoryUtil.memFree(dataBuffer); dataBuffer = null; }
        blurPipeline.close();
        initialized = false;
    }
}

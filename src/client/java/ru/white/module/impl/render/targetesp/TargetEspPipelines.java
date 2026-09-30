package ru.white.module.impl.render.targetesp;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

/** Общие pipeline и хелперы для портированных target-esp рендереров (текстурные плашки). */
public final class TargetEspPipelines {
    private TargetEspPipelines() {
    }

    public static final RenderPipeline ROMB_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/wtextesp")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> ROMB_ESP =
            Util.memoize(texture -> RenderLayer.of("wtextesp",
                    RenderSetup.builder(ROMB_PIPELINE)
                            .texture("Sampler0", texture)
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()));

    public static Quaternionf cameraRotation() {
        return MinecraftClient.getInstance().gameRenderer.getCamera().getRotation();
    }

    public static void textured(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v, int color) {
        consumer.vertex(matrix, x, y, z).color(color).texture(u, v)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(0xF000F0).normal(0, 0, 1);
    }
}

package ru.decide.module.impl.render.targetesp;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Рендер-пайплайны для режимов, портированных из Dima 26.2.
 *
 * Пайплайны повторяют оригинальные: та же вершинная форматность, топология,
 * блендинг и политика глубины. Конкретно для «Пентаграммы» в Dima это
 * POSITION_TEX_COLOR + QUADS + ADDITIVE + depth ALWAYS_PASS + UBO Globals,
 * а саму фигуру рисует SDF-фрагментный шейдер (target_esp_pentagram.fsh).
 */
public final class DimaEspPipelines {

    private static final String SHADER_ROOT = "core/targetesp/";

    public static final RenderPipeline PENTAGRAM_PIPELINE = RenderPipeline.builder(
                    new RenderPipeline.Snippet[0])
            .withLocation(Identifier.of("decide", "targetesp_dima_pentagram"))
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR,
                    VertexFormat.DrawMode.QUADS)
            .withVertexShader(Identifier.of("decide", SHADER_ROOT + "target_esp_pentagram"))
            .withFragmentShader(Identifier.of("decide", SHADER_ROOT + "target_esp_pentagram"))
            .withBlend(BlendFunction.ADDITIVE)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withDepthWrite(false)
            .build();

    public static final RenderLayer PENTAGRAM_LAYER = RenderLayer.of("targetesp_dima_pentagram",
            RenderSetup.builder(PENTAGRAM_PIPELINE)
                    .translucent()
                    .expectedBufferSize(1 << 12)
                    .build());

    /** Квадр без текстуры — для POSITION_COLOR пайплайнов. */
    public static void fill(VertexConsumer consumer, Matrix4f matrix,
                            float x0, float y0, float x1, float y1, float z, int color) {
        consumer.vertex(matrix, x0, y0, z).color(color);
        consumer.vertex(matrix, x1, y0, z).color(color);
        consumer.vertex(matrix, x1, y1, z).color(color);
        consumer.vertex(matrix, x0, y1, z).color(color);
    }

    private DimaEspPipelines() {
    }
}
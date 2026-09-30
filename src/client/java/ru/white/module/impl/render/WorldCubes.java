package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.colors.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

@ModuleInfo(
        name = "World Cubes",
        desc = "Сетка по миру со свечением",
        category = Category.VISUALS
)
public class WorldCubes extends Module {

    public ModeSetting typeColor = new ModeSetting(this,"Режим цвета","Тема","Свой");

    public ColorSetting tintColor = new ColorSetting(this, "Цвет", 0xFF00FFFF).setVisible(() -> typeColor.is("Свой"));

    public int getColor() {

        if(typeColor.is("Тема")) {
            return ColorUtil.getClientColor1(1);
        }

        return tintColor.getValue();
    }
    public SliderSetting range       = new SliderSetting(this, "Радиус (ячейки)", 4, 1, 30, 1);
    public SliderSetting vRange      = new SliderSetting(this, "Высота (ячейки)", 2, 1, 20, 1);
    public SliderSetting lineAlphaS  = new SliderSetting(this, "Прозрачность линий", 0.22f, 0.04f, 1.0f, 0.02f);

    public BooleanSetting showGlow   = new BooleanSetting(this, "Свечение в углах", true);
    public SliderSetting  glowSize   = new SliderSetting(this, "Размер свечения", 0.08f, 0.02f, 1.0f, 0.02f)
            .setVisible(() -> showGlow.getValue());
    public SliderSetting  glowBright = new SliderSetting(this, "Яркость свечения", 1.8f, 0.5f, 4.0f, 0.1f)
            .setVisible(() -> showGlow.getValue());


    private final Animation shiftAnim = new Animation();
    private int  currOx, currOy, currOz;
    private int  prevOx, prevOy, prevOz;
    private boolean firstFrame = true;

    private static final int CELL = 12;
    private final BufferAllocator allocator = new BufferAllocator(1 << 18);


    @Override
    protected void onEnable() {
        firstFrame = true;
    }


    @EventHandler
    public void onRender3D(EventRender3D e) {
        if (mc.player == null || mc.world == null) return;


        int ox = snapDown(mc.player.getX());
        int oy = snapDown(mc.player.getY());
        int oz = snapDown(mc.player.getZ());

        if (firstFrame) {
            currOx = ox; currOy = oy; currOz = oz;
            prevOx = ox; prevOy = oy; prevOz = oz;
            shiftAnim.set(1.0);
            firstFrame = false;
        } else if (ox != currOx || oy != currOy || oz != currOz) {

            prevOx = currOx; prevOy = currOy; prevOz = currOz;
            currOx = ox;     currOy = oy;     currOz = oz;
            shiftAnim.set(0.0);
            shiftAnim.run(1.0, 0.25, Easings.QUAD_OUT);
        }
        shiftAnim.update();
        float t = shiftAnim.get();

        int col = getColor();
        int cr  = (col >> 16) & 0xFF;
        int cg  = (col >> 8)  & 0xFF;
        int cb  =  col        & 0xFF;

        float baseAlpha = lineAlphaS.getValue() * 255;

        int rI = range.getValue().intValue();
        int vI = vRange.getValue().intValue();

        Vec3d cam   = mc.gameRenderer.getCamera().getCameraPos();
        MatrixStack stack = e.getMatrixStack();
        Matrix4f mat = stack.peek().getPositionMatrix();

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        VertexConsumer lb = immediate.getBuffer(LINE_LAYER);


        if (t < 0.999f) {
            int oldA = (int)(baseAlpha * (1f - t));
            drawLines(lb, mat, cam, prevOx, prevOy, prevOz, cr, cg, cb, oldA, rI, vI);
        }

        int newA = (int)(baseAlpha * t);
        drawLines(lb, mat, cam, currOx, currOy, currOz, cr, cg, cb, newA, rI, vI);


        if (showGlow.getValue()) {
            float gs    = glowSize.getValue();
            float brite = glowBright.getValue();

            int newGlowA = Math.min(255, (int)(15 * t * brite));
            drawGlows(immediate, ROMB_ESP.apply(Particles.ParticleType.BLOOM.texture()), mat, cam, currOx, currOy, currOz,
                    cr, cg, cb, newGlowA, gs * 2, rI, vI);

             newGlowA = Math.min(255, (int)(255 * t * brite));
            drawGlows(immediate, ROMB_ESP.apply(Particles.ParticleType.CIRCLE.texture()), mat, cam, currOx, currOy, currOz,
                      cr, cg, cb, newGlowA, gs, rI, vI);
        }

        immediate.draw();
    }


    private void drawLines(VertexConsumer lb, Matrix4f mat, Vec3d cam,
                           int ox, int oy, int oz,
                           int cr, int cg, int cb, int alpha,
                           int rI, int vI) {
        if (alpha <= 0) return;

        int xMin = ox - rI * CELL,  xMax = ox + rI * CELL;
        int zMin = oz - rI * CELL,  zMax = oz + rI * CELL;
        int yMin = oy - (vI - 1) * CELL;
        int yMax = oy +  vI      * CELL;


        for (int x = xMin; x <= xMax; x += CELL) {
            for (int z = zMin; z <= zMax; z += CELL) {
                float rx = (float)(x - cam.x), rz = (float)(z - cam.z);
                lb.vertex(mat, rx, (float)(yMin - cam.y), rz).color(cr, cg, cb, alpha);
                lb.vertex(mat, rx, (float)(yMax - cam.y), rz).color(cr, cg, cb, alpha);
            }
        }

        for (int y = yMin; y <= yMax; y += CELL) {
            float ry = (float)(y - cam.y);
            for (int z = zMin; z <= zMax; z += CELL) {
                float rz = (float)(z - cam.z);
                lb.vertex(mat, (float)(xMin - cam.x), ry, rz).color(cr, cg, cb, alpha);
                lb.vertex(mat, (float)(xMax - cam.x), ry, rz).color(cr, cg, cb, alpha);
            }
        }
        for (int y = yMin; y <= yMax; y += CELL) {
            float ry = (float)(y - cam.y);
            for (int x = xMin; x <= xMax; x += CELL) {
                float rx = (float)(x - cam.x);
                lb.vertex(mat, rx, ry, (float)(zMin - cam.z)).color(cr, cg, cb, alpha);
                lb.vertex(mat, rx, ry, (float)(zMax - cam.z)).color(cr, cg, cb, alpha);
            }
        }
    }

    private void drawGlows(VertexConsumerProvider.Immediate immediate, RenderLayer glowLayer,
                           Matrix4f mat, Vec3d cam,
                           int ox, int oy, int oz,
                           int cr, int cg, int cb, int alpha,
                           float gs, int rI, int vI) {
        if (alpha <= 0) return;

        int xMin = ox - rI * CELL, xMax = ox + rI * CELL;
        int zMin = oz - rI * CELL, zMax = oz + rI * CELL;
        int yMin = oy - (vI - 1) * CELL;
        int yMax = oy +  vI      * CELL;

        Camera camera = mc.gameRenderer.getCamera();

        // биллборд одинаково повёрнут к камере во всех ячейках —
        // углы квада считаем один раз, дальше только сложение с позицией ячейки
        Quaternionf q = camera.getRotation();
        Vector3f c0 = new Vector3f(-gs, -gs, 0).rotate(q);
        Vector3f c1 = new Vector3f( gs, -gs, 0).rotate(q);
        Vector3f c2 = new Vector3f( gs,  gs, 0).rotate(q);
        Vector3f c3 = new Vector3f(-gs,  gs, 0).rotate(q);

        // направление взгляда — ячейки за спиной в буфер не пишем
        float yawRad   = (float) Math.toRadians(camera.getYaw());
        float pitchRad = (float) Math.toRadians(camera.getPitch());
        float cosPitch = (float) Math.cos(pitchRad);
        float lx = (float) (-Math.sin(yawRad) * cosPitch);
        float ly = (float) -Math.sin(pitchRad);
        float lz = (float) (Math.cos(yawRad) * cosPitch);

        VertexConsumer gb = immediate.getBuffer(glowLayer);

        for (int x = xMin; x <= xMax; x += CELL) {
            for (int y = yMin; y <= yMax; y += CELL) {
                for (int z = zMin; z <= zMax; z += CELL) {
                    float rx = (float) (x - cam.x);
                    float ry = (float) (y - cam.y);
                    float rz = (float) (z - cam.z);

                    if (rx * lx + ry * ly + rz * lz < -gs * 2) continue;

                    gb.vertex(mat, rx + c0.x, ry + c0.y, rz + c0.z).color(cr, cg, cb, alpha).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                    gb.vertex(mat, rx + c1.x, ry + c1.y, rz + c1.z).color(cr, cg, cb, alpha).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                    gb.vertex(mat, rx + c2.x, ry + c2.y, rz + c2.z).color(cr, cg, cb, alpha).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                    gb.vertex(mat, rx + c3.x, ry + c3.y, rz + c3.z).color(cr, cg, cb, alpha).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                }
            }
        }
    }


    public static final RenderPipeline ROMB_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/wtex")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> ROMB_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(ROMB_ESP_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(1536)
                        .build();
                return RenderLayer.of("wtex", setup);
            });

    private static int snapDown(double v) {
        return (int) Math.floor(v / CELL) * CELL;
    }

    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "world_cubes_grid"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer LINE_LAYER = RenderLayer.of(
            "world_cubes_lines",
            RenderSetup.builder(LINE_PIPELINE).expectedBufferSize(1 << 14).build()
    );
}

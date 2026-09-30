package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.MotionEvent;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@ModuleInfo(
        name = "Svetoch",
        desc = "Летающие светящиеся кубы с физикой",
        category = Category.VISUALS
)
public class Svetoch extends Module {

    public ModeSetting typeColor = new ModeSetting(this, "Режим цвета", "Тема", "Свой");
    public ColorSetting tintColor = new ColorSetting(this, "Цвет", 0xFF00FFFF).setVisible(() -> typeColor.is("Свой"));

    public SliderSetting count = new SliderSetting(this, "Кол кубиков", 100, 50, 300, 1);
    public SliderSetting cubeSize = new SliderSetting(this, "Размер куба", 0.26F, 0.1F, 0.6F, 0.02F);

    public int getColor() {
        if (typeColor.is("Тема")) {
            return ColorUtil.getClientColor1(1);
        }
        return tintColor.getValue();
    }

    // тайминги как у мировых партиклов: гаснуть с 1.5с, полное угасание ~3.5с
    private static final long LIFETIME = 1500L;
    private static final double CULL_DIST_SQ = 96 * 96;

    // куб: 8 углов как знаки полуразмера, грани и рёбра — статические таблицы индексов
    private static final float[][] CORNERS = {
            {-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1},
            {-1, 1, -1}, {1, 1, -1}, {1, 1, 1}, {-1, 1, 1}
    };
    private static final int[][] FACES = {
            {0, 1, 2, 3}, {4, 5, 6, 7},
            {0, 1, 5, 4}, {2, 3, 7, 6},
            {1, 2, 6, 5}, {3, 0, 4, 7}
    };
    private static final int[][] EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private final List<Particle> particles = new ArrayList<>();
    private final List<Particle> visible = new ArrayList<>();
    private final BufferAllocator allocator = new BufferAllocator(1 << 16);
    private long lastFrameTime = System.nanoTime();

    @Override
    public void toggle() {
        super.toggle();
        particles.clear();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        particles.clear();
    }

    @EventHandler
    public void onUpdate(MotionEvent e) {
        if (mc.player == null || mc.world == null) return;

        // спавн как у обычных партиклов "В мире": пачкой каждый тик вокруг игрока,
        // с привязкой к рельефу и подъёмом из блоков
        int max = count.getValue().intValue();
        int r = 24;
        for (int i = 0; i < 3 && particles.size() < max; i++) {
            Vec3d additional = mc.player.getEntityPos().add(
                    MathUtil.randomValue(-r, r),
                    0,
                    MathUtil.randomValue(-r, r)
            );

            BlockPos topPos = mc.world.getTopPosition(
                    Heightmap.Type.MOTION_BLOCKING,
                    BlockPos.ofFloored(additional)
            );

            double x = topPos.getX() + MathUtil.randomValue(-3, 3);
            double z = topPos.getZ() + MathUtil.randomValue(-3, 3);
            double y = mc.player.getY() + MathUtil.randomValue(mc.player.getHeight(), r);

            Vec3d spawnPos = new Vec3d(x, y, z);
            while (!mc.world.isAir(BlockPos.ofFloored(spawnPos)) && spawnPos.y < mc.world.getTopYInclusive()) {
                spawnPos = spawnPos.add(0, 1, 0);
            }

            Vec3d velocity = new Vec3d(
                    mc.player.getVelocity().x + MathUtil.randomValue(-0.6F, 0.6F),
                    MathUtil.randomValue(-0.01, 0.44),
                    mc.player.getVelocity().z + MathUtil.randomValue(-0.6F, 0.6F)
            );

            particles.add(new Particle(spawnPos, velocity));
        }
    }

    @EventHandler
    public void onRender3D(EventRender3D e) {
        if (particles.isEmpty() || mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        long nanoNow = System.nanoTime();
        // физика независима от FPS: всё нормировано к 60 кадрам/с
        float ticks = (float) Math.min((nanoNow - lastFrameTime) / 1_000_000_000.0, 0.1) * 60F;
        lastFrameTime = nanoNow;

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        Matrix4f base = matrices.peek().getPositionMatrix();
        Quaternionf camRotation = mc.gameRenderer.getCamera().getRotation();

        // общий поворот на все кубы — одна тригонометрия на кадр вместо одной на куб
        float rotation = (now % 9000L) / 9000F * 360F;
        float rotXRad = (float) Math.toRadians(rotation * 0.5F);

        int baseColor = getColor();
        int cr = (baseColor >> 16) & 0xFF;
        int cg = (baseColor >> 8) & 0xFF;
        int cb = baseColor & 0xFF;

        float size = cubeSize.getValue();

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

        // ВАЖНО: immediate с одним аллокатором держит активным только один слой —
        // при getBuffer() другого слоя предыдущий флушится. Поэтому рендер идёт
        // проходами по слоям, а матрицы считаются один раз и кэшируются в частице.
        visible.clear();
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update(now, ticks);
            if (p.shouldRemove()) {
                it.remove();
                continue;
            }

            if (p.alphaValue <= 0.01F) continue;

            double dx = p.x - cam.x;
            double dy = p.y - cam.y;
            double dz = p.z - cam.z;
            // дальние кубы не пишем в буферы вообще
            if (dx * dx + dy * dy + dz * dz > CULL_DIST_SQ) continue;

            // матрицы собираются напрямую (JOML), без push/pop стека
            // и без аллокаций кватернионов RotationAxis на каждый куб
            p.cubeMatrix.set(base)
                    .translate((float) dx, (float) dy, (float) dz)
                    .rotateY((float) Math.toRadians(rotation + p.phase))
                    .rotateX(rotXRad);

            p.glowMatrix.set(base)
                    .translate((float) dx, (float) dy, (float) dz)
                    .rotate(camRotation);

            visible.add(p);
        }

        if (visible.isEmpty()) return;

        // проход 1: грани — один слой, один буфер
        VertexConsumer buf = immediate.getBuffer(FILL_LAYER);
        for (Particle p : visible) {
            int faceAlpha = (int) (p.alphaValue * 0.2F * 255);
            if (faceAlpha <= 0) continue;
            for (int[] face : FACES) {
                for (int idx : face) {
                    float[] c = CORNERS[idx];
                    buf.vertex(p.cubeMatrix, c[0] * size, c[1] * size, c[2] * size).color(cr, cg, cb, faceAlpha);
                }
            }
        }

        // проход 2: оутлайн
        buf = immediate.getBuffer(LINE_LAYER);
        for (Particle p : visible) {
            int edgeAlpha = (int) (p.alphaValue  * 255);
            if (edgeAlpha <= 0) continue;
            for (int[] edge : EDGES) {
                float[] a = CORNERS[edge[0]];
                float[] b = CORNERS[edge[1]];
                buf.vertex(p.cubeMatrix, a[0] * size, a[1] * size, a[2] * size).color(cr, cg, cb, edgeAlpha);
                buf.vertex(p.cubeMatrix, b[0] * size, b[1] * size, b[2] * size).color(cr, cg, cb, edgeAlpha);
            }
        }


        buf = immediate.getBuffer(GLOW_LAYER_BIG);
        for (Particle p : visible) {
            drawGlow(buf, p.glowMatrix, cr, cg, cb, (int) (80 * p.alphaValue), size * 6);
        }




        immediate.draw();
    }

    private static void drawGlow(VertexConsumer buf, Matrix4f m, int r, int g, int b, int alpha, float s) {
        if (alpha <= 0) return;
        buf.vertex(m, -s, -s, 0).color(r, g, b, alpha).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buf.vertex(m, s, -s, 0).color(r, g, b, alpha).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buf.vertex(m, s, s, 0).color(r, g, b, alpha).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buf.vertex(m, -s, s, 0).color(r, g, b, alpha).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
    }

    private static class Particle {
        // физика как у Particles: скорость спавна умножается на BASE_VELOCITY,
        // гравитация и отскок от блоков с теми же коэффициентами
        private static final double BASE_VELOCITY = 0.05;
        private static final double BOUNCINESS = 0.85;
        private static final double GRAVITY = 0.0001;

        double x, y, z, mX, mY, mZ;
        final long start;
        final float phase;
        boolean fadingOut;
        float alphaValue;
        final Animation alpha = new Animation();
        final Matrix4f cubeMatrix = new Matrix4f();
        final Matrix4f glowMatrix = new Matrix4f();

        Particle(Vec3d pos, Vec3d velocity) {
            this.start = System.currentTimeMillis();
            this.phase = (float) (Math.random() * 100.0);
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.mX = velocity.x * BASE_VELOCITY;
            this.mY = velocity.y * BASE_VELOCITY;
            this.mZ = velocity.z * BASE_VELOCITY;
            alpha.set(0);
            alpha.run(1, 0.5, Easings.LINEAR, true);
        }

        void update(long now, float ticks) {
            double nextX = x + mX * ticks;
            double nextY = y + mY * ticks;
            double nextZ = z + mZ * ticks;

            // шаг за кадр крошечный: почти всегда все три оси остаются в текущем блоке —
            // тогда вместо трёх лукапов состояния блока хватает одного
            if (Math.floor(nextX) == Math.floor(x)
                    && Math.floor(nextY) == Math.floor(y)
                    && Math.floor(nextZ) == Math.floor(z)) {
                if (isHit(nextX, nextY, nextZ)) {
                    mX = -mX * BOUNCINESS;
                    mY = -mY * BOUNCINESS;
                    mZ = -mZ * BOUNCINESS;
                } else {
                    x = nextX;
                    y = nextY;
                    z = nextZ;
                }
            } else {
                if (isHit(nextX, y, z)) mX = -mX * BOUNCINESS;
                else x = nextX;
                if (isHit(x, nextY, z)) mY = -mY * BOUNCINESS;
                else y = nextY;
                if (isHit(x, y, nextZ)) mZ = -mZ * BOUNCINESS;
                else z = nextZ;
            }

            mY -= GRAVITY * ticks;

            if (!fadingOut && now - start > LIFETIME) {
                fadingOut = true;
                alpha.run(0, 2, Easings.LINEAR, true);
            }

            alpha.update();
            alphaValue = alpha.get();
        }

        boolean shouldRemove() {
            return fadingOut && alphaValue <= 0.01F;
        }

        private static boolean isHit(double px, double py, double pz) {
            BlockPos pos = BlockPos.ofFloored(px, py, pz);
            return mc.world.getBlockState(pos).isFullCube(mc.world, pos);
        }
    }

    private static final Identifier GLOW_TEXTURE_BIG = Identifier.of("client", "textures/particles/glow.png");
    private static final Identifier GLOW_TEXTURE_SMALL = Identifier.of("client", "textures/visuals/particles_2.png");

    private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "svetoch_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer FILL_LAYER = RenderLayer.of(
            "svetoch_fill",
            RenderSetup.builder(FILL_PIPELINE).expectedBufferSize(1 << 13).build()
    );

    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "svetoch_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer LINE_LAYER = RenderLayer.of(
            "svetoch_lines",
            RenderSetup.builder(LINE_PIPELINE).expectedBufferSize(1 << 13).build()
    );

    // свой глоу-пайплайн: в отличие от WorldCubes.ROMB_ESP здесь выключена запись
    // глубины — иначе прозрачные края биллбордов пишут depth и режут геометрию за собой
    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "svetoch_glow"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer GLOW_LAYER_BIG = RenderLayer.of(
            "svetoch_glow_big",
            RenderSetup.builder(GLOW_PIPELINE)
                    .texture("Sampler0", GLOW_TEXTURE_BIG)
                    .translucent()
                    .expectedBufferSize(1 << 12)
                    .build()
    );

    private static final RenderLayer GLOW_LAYER_SMALL = RenderLayer.of(
            "svetoch_glow_small",
            RenderSetup.builder(GLOW_PIPELINE)
                    .texture("Sampler0", GLOW_TEXTURE_SMALL)
                    .translucent()
                    .expectedBufferSize(1 << 12)
                    .build()
    );
}

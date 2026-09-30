package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.manager.event_impl.EventJump;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.preview.ModulePreview;
import ru.white.module.api.preview.PreviewContext;
import ru.white.module.api.preview.PreviewSettings;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@ModuleInfo(
        name = "Jump Cube",
        desc = "Кубики вокруг игрока - прыгают и крутятся",
        category = Category.VISUALS
)
public class JumpCube extends Module implements ModulePreview {

    public ButtonSetting previewButton = PreviewSettings.button(this);

    public ModeSetting typeColor = new ModeSetting(this, "Режим цвета", "Тема", "Свой");
    public ColorSetting tintColor = new ColorSetting(this, "Цвет", 0xFF00FFFF)
            .setVisible(() -> typeColor.is("Свой"));
    public SliderSetting count = new SliderSetting(this, "Кубиков", 3, 1, 10, 1);
    public SliderSetting size  = new SliderSetting(this, "Размер",  0.18F, 0.05F, 0.40F, 0.01F);

    // кубики живут вокруг самого игрока и прыгают без остановки — настраивать нечего
    private final PreviewSettings previewSettings = PreviewSettings.none(this);

    // ── константы из DashCubes ────────────────────────────────────────────────
    private static final int    RES_PX         = 16;       // сетка 1/16 блока
    private static final long   LIFETIME_MS    = 2000L;
    private static final double SPAWN_MIN_DST  = 0.6;
    private static final double SPAWN_MAX_DST  = 2.6;
    private static final double MAX_OWNER_DIST = 6.0;
    private static final long   RANGE_FADE_MS  = 400L;
    private static final int[]  JUMP_YAWS      = {0, 90, 180, 270};

    private final Random          random    = new Random();
    private final List<Cube>      cubes     = new ArrayList<>();
    private final List<Cube>      visible   = new ArrayList<>();
    private final BufferAllocator allocator = new BufferAllocator(1 << 16);

    @Override
    protected void onDisable() { cubes.clear(); }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) { cubes.clear(); }

    @EventHandler
    public void onJump(EventJump e) {
        for (Cube c : cubes) c.tryStartJump();
    }

    // ───────────────────────────── предпоказ ─────────────────────────────

    @Override
    public PreviewSettings previewSettings() {
        return previewSettings;
    }

    @Override
    public void previewSpawn(PreviewContext ctx) {
    }

    /** Кубики спавнятся сами, а прыжок перезапускаем сразу после предыдущего — чтобы не замирали. */
    @Override
    public void previewTick(PreviewContext ctx) {
        for (Cube c : cubes) c.tryStartJump();
    }

    @Override
    public void previewStop() {
        cubes.clear();
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        Vec3d playerPos = mc.player.getEntityPos();

        // спавним count новых кубиков в тик — точно как DashCubes
        List<BlockPos> candidates = getPlaceableAround(playerPos, SPAWN_MIN_DST, SPAWN_MAX_DST, 3);
        int toSpawn = count.getValue().intValue();
        for (int i = 0; i < toSpawn && !candidates.isEmpty(); i++) {
            int idx = random.nextInt(candidates.size());
            Vec3d sp = findSpawnPoint(candidates.get(idx), playerPos);
            if (sp != null) cubes.add(new Cube(sp));
        }

        cubes.forEach(Cube::updateLogic);
        cubes.removeIf(Cube::isDead);
    }

    @EventHandler
    public void onRender(EventRender3D e) {
        if (mc.player == null || mc.world == null || cubes.isEmpty()) return;

        float pt  = e.getTickDelta();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        MatrixStack pose = e.getMatrixStack();
        float s = size.getValue();

        int base = typeColor.is("Тема") ? ColorUtil.getClientColor1(1) : tintColor.getValue();
        int cr = (base >> 16) & 0xFF;
        int cg = (base >>  8) & 0xFF;
        int cb =  base        & 0xFF;

        // предвычисляем матрицы всех видимых кубиков
        visible.clear();
        for (Cube c : cubes) {
            if (c.isDead()) continue;
            float alpha = c.getAlpha();
            if (alpha < 0.01f) continue;

            double wx = c.spawnPos.x - cam.x;
            double wy = c.spawnPos.y + c.getJumpYOffset(pt) - cam.y;
            double wz = c.spawnPos.z - cam.z;

            // куб: поворот при прыжке + масштаб по alpha (вырастает/исчезает)
            pose.push();
            pose.translate(wx, wy, wz);
            if (c.jumpTicksMax > 0) {
                // субтик-интерполяция: lerp между предыдущим и текущим значением jumpTicks
                float tLerped = MathHelper.lerp(pt, (float) c.prevJumpTicks, (float) c.jumpTicks);
                float phase   = 1.0f - tLerped / (float) c.jumpTicksMax;
                pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(c.jumpYaw));
                pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f * phase));
            }
            pose.scale(alpha, alpha, alpha);
            c.cubeMatrix.set(pose.peek().getPositionMatrix());
            pose.pop();

            // billboard для glow
            pose.push();
            pose.translate(wx, wy, wz);
            pose.multiply(mc.gameRenderer.getCamera().getRotation());
            pose.scale(alpha, alpha, alpha);
            c.glowMatrix.set(pose.peek().getPositionMatrix());
            pose.pop();

            c.cachedAlpha = alpha;
            visible.add(c);
        }

        if (visible.isEmpty()) return;

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);


        VertexConsumer buf = immediate.getBuffer(FILL_LAYER);
        for (Cube c : visible) {
            int fa = (int) (c.cachedAlpha * 0.2f * 255);
            if (fa > 0) drawCubeFill(buf, c.cubeMatrix, s, cr, cg, cb, fa);
        }


        buf = immediate.getBuffer(LINE_LAYER);
        for (Cube c : visible) {
            int ea = (int) (c.cachedAlpha  * 255);
            if (ea > 0) drawCubeEdges(buf, c.cubeMatrix, s, cr, cg, cb, ea);
        }

        buf = immediate.getBuffer(GLOW_LAYER_BIG);
        for (Cube c : visible) drawGlow(buf, c.glowMatrix, cr, cg, cb, (int)(35  * c.cachedAlpha), s * 5.5f);

        buf = immediate.getBuffer(GLOW_LAYER_SMALL);
        for (Cube c : visible) drawGlow(buf, c.glowMatrix, cr, cg, cb, (int)(40 * c.cachedAlpha), s * 1.8f);

        immediate.draw();
    }

    // ── спавн ────────────────────────────────────────────────────────────────

    private List<BlockPos> getPlaceableAround(Vec3d center, double minDst, double maxDst, int offsetDown) {
        List<BlockPos> result = new ArrayList<>();
        if (mc.world == null) return result;
        double xE = center.x, yE = center.y + 1.0, zE = center.z;
        for (double x = xE - maxDst; x < xE + maxDst; x++) {
            for (double z = zE - maxDst; z < zE + maxDst; z++) {
                for (double y = yE - offsetDown; y < yE; y++) {
                    BlockPos pos = BlockPos.ofFloored(x, y, z);
                    if (canPlace(pos) && !result.contains(pos)) result.add(pos);
                }
            }
        }
        return result;
    }

    private boolean canPlace(BlockPos pos) {
        if (mc.world == null) return false;
        if (!mc.world.isAir(pos)) return false;
        if (mc.world.isAir(pos.down())) return false;
        return mc.world.getOtherEntities(null, new Box(pos)).isEmpty();
    }

    // выбираем случайную точку внутри блока и прищёлкиваем к сетке 1/RES_PX
    private Vec3d findSpawnPoint(BlockPos pos, Vec3d playerPos) {
        double resOff = 1.0 / RES_PX;
        double half   = resOff / 2.0;
        for (int att = 64; att > 0; att--) {
            Vec3d v = new Vec3d(
                    pos.getX() + random.nextDouble(),
                    pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble()
            );
            double dst = v.distanceTo(playerPos);
            if (dst >= SPAWN_MIN_DST && dst <= SPAWN_MAX_DST) {
                double sx = Math.floor(v.x * RES_PX) / RES_PX + half;
                double sy = Math.floor(v.y)           + half - resOff;
                double sz = Math.floor(v.z * RES_PX) / RES_PX + half;
                return new Vec3d(sx, sy, sz);
            }
        }
        return null;
    }

    // ── рендер-хелперы ───────────────────────────────────────────────────────

    private static void drawCubeFill(VertexConsumer b, Matrix4f m, float s,
                                     int r, int g, int bl, int a) {
        float h = s / 2f;
        b.vertex(m,-h,-h,-h).color(r,g,bl,a); b.vertex(m, h,-h,-h).color(r,g,bl,a);
        b.vertex(m, h,-h, h).color(r,g,bl,a); b.vertex(m,-h,-h, h).color(r,g,bl,a);

        b.vertex(m,-h, h,-h).color(r,g,bl,a); b.vertex(m, h, h,-h).color(r,g,bl,a);
        b.vertex(m, h, h, h).color(r,g,bl,a); b.vertex(m,-h, h, h).color(r,g,bl,a);

        b.vertex(m,-h,-h,-h).color(r,g,bl,a); b.vertex(m, h,-h,-h).color(r,g,bl,a);
        b.vertex(m, h, h,-h).color(r,g,bl,a); b.vertex(m,-h, h,-h).color(r,g,bl,a);

        b.vertex(m,-h,-h, h).color(r,g,bl,a); b.vertex(m, h,-h, h).color(r,g,bl,a);
        b.vertex(m, h, h, h).color(r,g,bl,a); b.vertex(m,-h, h, h).color(r,g,bl,a);

        b.vertex(m,-h,-h,-h).color(r,g,bl,a); b.vertex(m,-h,-h, h).color(r,g,bl,a);
        b.vertex(m,-h, h, h).color(r,g,bl,a); b.vertex(m,-h, h,-h).color(r,g,bl,a);

        b.vertex(m, h,-h,-h).color(r,g,bl,a); b.vertex(m, h,-h, h).color(r,g,bl,a);
        b.vertex(m, h, h, h).color(r,g,bl,a); b.vertex(m, h, h,-h).color(r,g,bl,a);
    }

    private static void drawCubeEdges(VertexConsumer b, Matrix4f m, float s,
                                      int r, int g, int bl, int a) {
        float h = s / 2f;
        line(b,m, -h,-h,-h,  h,-h,-h, r,g,bl,a); line(b,m,  h,-h,-h,  h,-h, h, r,g,bl,a);
        line(b,m,  h,-h, h, -h,-h, h, r,g,bl,a); line(b,m, -h,-h, h, -h,-h,-h, r,g,bl,a);
        line(b,m, -h, h,-h,  h, h,-h, r,g,bl,a); line(b,m,  h, h,-h,  h, h, h, r,g,bl,a);
        line(b,m,  h, h, h, -h, h, h, r,g,bl,a); line(b,m, -h, h, h, -h, h,-h, r,g,bl,a);
        line(b,m, -h,-h,-h, -h, h,-h, r,g,bl,a); line(b,m,  h,-h,-h,  h, h,-h, r,g,bl,a);
        line(b,m,  h,-h, h,  h, h, h, r,g,bl,a); line(b,m, -h,-h, h, -h, h, h, r,g,bl,a);
    }

    private static void line(VertexConsumer b, Matrix4f m,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              int r, int g, int bl, int a) {
        b.vertex(m, x1, y1, z1).color(r, g, bl, a);
        b.vertex(m, x2, y2, z2).color(r, g, bl, a);
    }

    private static void drawGlow(VertexConsumer buf, Matrix4f m,
                                  int r, int g, int b, int alpha, float s) {
        if (alpha <= 0) return;
        buf.vertex(m,-s,-s,0).color(r,g,b,alpha).texture(0,1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,0,1);
        buf.vertex(m, s,-s,0).color(r,g,b,alpha).texture(1,1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,0,1);
        buf.vertex(m, s, s,0).color(r,g,b,alpha).texture(1,0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,0,1);
        buf.vertex(m,-s, s,0).color(r,g,b,alpha).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,0,1);
    }

    // ── Cube ─────────────────────────────────────────────────────────────────

    private class Cube {
        final Vec3d  spawnPos;
        final long   spawnTime = System.currentTimeMillis();
        float        cachedAlpha;

        // jump-анимация (порт из DashCubes)
        int    jumpTicks     = 0;
        int    prevJumpTicks = 0;
        int    jumpTicksMax  = 0;
        int    jumpYaw       = 0;
        double jumpHeight    = 0;

        // out-of-range fade
        boolean outOfRange      = false;
        long    outOfRangeStart = 0L;

        final Matrix4f cubeMatrix = new Matrix4f();
        final Matrix4f glowMatrix = new Matrix4f();

        Cube(Vec3d spawnPos) { this.spawnPos = spawnPos; }

        void updateLogic() {
            prevJumpTicks = jumpTicks;
            if (jumpTicks > 0) jumpTicks--;

            if (mc.player != null && mc.player.getEntityPos().distanceTo(spawnPos) > MAX_OWNER_DIST) {
                if (!outOfRange) { outOfRange = true; outOfRangeStart = System.currentTimeMillis(); }
            }
        }

        void tryStartJump() {
            if (jumpTicks <= 0) {
                jumpTicksMax = jumpTicks = (int) (14.0f * (0.5f + 0.5f * random.nextFloat())); // 7-14 тиков
                jumpHeight   = (2 + random.nextInt(11)) / 16.0;      // 2-12 пикселей в блоках
                jumpYaw      = JUMP_YAWS[random.nextInt(JUMP_YAWS.length)];
            }
        }

        float getTimePc() {
            return MathHelper.clamp((float)(System.currentTimeMillis() - spawnTime) / LIFETIME_MS, 0f, 1f);
        }

        float getRangeFade() {
            if (!outOfRange) return 1f;
            return MathHelper.clamp(1f - (float)(System.currentTimeMillis() - outOfRangeStart) / RANGE_FADE_MS, 0f, 1f);
        }

        // fade-in первые 10% жизни, fade-out последние 20%
        float getAlpha() {
            float t = getTimePc();
            float a = t < 0.1f ? t / 0.1f
                    : t > 0.8f ? 1f - (t - 0.8f) / 0.2f
                    : 1f;
            return MathHelper.clamp(a, 0f, 1f) * getRangeFade();
        }

        // треугольная волна: куб взлетает в середине анимации и возвращается
        double getJumpYOffset(float pt) {
            if (jumpTicksMax <= 0) return 0.0;
            float t = MathHelper.lerp(pt, (float) prevJumpTicks, (float) jumpTicks) / (float) jumpTicksMax;
            if (t > 0.5f) t = 1.0f - t;
            return t * 2.0 * jumpHeight;
        }

        boolean isDead() {
            return getTimePc() >= 1f || (outOfRange && getRangeFade() <= 0.01f);
        }
    }

    // ── render pipelines ─────────────────────────────────────────────────────

    private static final Identifier GLOW_TEX_BIG   = Identifier.of("client", "textures/visuals/particles_1.png");
    private static final Identifier GLOW_TEX_SMALL  = Identifier.of("client", "textures/visuals/particles_2.png");

    private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "jump_cube_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false).withBlend(BlendFunction.LIGHTNING).build()
    );
    private static final RenderLayer FILL_LAYER = RenderLayer.of(
            "jump_cube_fill", RenderSetup.builder(FILL_PIPELINE).expectedBufferSize(1 << 13).build()
    );

    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "jump_cube_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false).withBlend(BlendFunction.LIGHTNING).build()
    );
    private static final RenderLayer LINE_LAYER = RenderLayer.of(
            "jump_cube_lines", RenderSetup.builder(LINE_PIPELINE).expectedBufferSize(1 << 12).build()
    );

    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "jump_cube_glow"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false).withBlend(BlendFunction.LIGHTNING).build()
    );
    private static final RenderLayer GLOW_LAYER_BIG = RenderLayer.of(
            "jump_cube_glow_big",
            RenderSetup.builder(GLOW_PIPELINE).texture("Sampler0", GLOW_TEX_BIG).translucent().expectedBufferSize(1 << 12).build()
    );
    private static final RenderLayer GLOW_LAYER_SMALL = RenderLayer.of(
            "jump_cube_glow_small",
            RenderSetup.builder(GLOW_PIPELINE).texture("Sampler0", GLOW_TEX_SMALL).translucent().expectedBufferSize(1 << 12).build()
    );
}

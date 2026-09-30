package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Random;

/** Длинные случайно поворачивающиеся линии (порт из AchreonVisuals LineGlyphs). */
@ModuleInfo(
        name = "Line Glyphs",
        desc = "Длинные случайно поворачивающиеся линии",
        category = Category.VISUALS
)
public class LineGlyphs extends Module {
    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation("pipeline/line_glyphs")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.DrawMode.DEBUG_LINES)
                    .build()
    );
    private static final RenderLayer LINE_LAYER = RenderLayer.of("line_glyphs",
            RenderSetup.builder(LINE_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .build());

    private static final float LIFETIME = 15.0f;
    private static final float FADE_DURATION = 2.0f;

    public SliderSetting count = new SliderSetting(this, "Количество", 40.0F, 5.0F, 120.0F, 1.0F);
    public SliderSetting speed = new SliderSetting(this, "Скорость", 0.6F, 0.2F, 5.0F, 0.1F);
    public SliderSetting width = new SliderSetting(this, "Толщина", 1.2F, 0.8F, 6.0F, 0.1F);
    public BooleanSetting themeColor = new BooleanSetting(this, "Цвет от темы", true);
    public ColorSetting color = new ColorSetting(this, "Цвет", 0xFFA0DCFF)
            .setVisible(() -> !themeColor.getValue());

    private final BufferAllocator allocator = new BufferAllocator(1 << 18);
    private final Deque<Glyph> glyphs = new ArrayDeque<>();
    private final Random random = new Random();

    @EventHandler
    public void onTick(EventTick event) {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        float step = (float) (speed.getValue() * 0.14);

        Iterator<Glyph> iterator = glyphs.iterator();
        while (iterator.hasNext()) {
            Glyph glyph = iterator.next();
            if ((now - glyph.birthTime) / 1000f >= LIFETIME) {
                glyph.reset(now);
                continue;
            }
            glyph.update(step);
        }

        int targetCount = (int) (float) count.getValue();
        while (glyphs.size() < targetCount) {
            glyphs.addLast(spawnGlyph(now));
        }
    }

    private Glyph spawnGlyph(long now) {
        Vec3d playerPos = mc.player.getEntityPos();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 3.0 + random.nextDouble() * 6.0;
        Vec3d origin = playerPos
                .add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance)
                .add(0.0, 1.0 + random.nextDouble() * 4.0, 0.0);
        double cardinal = Math.round(random.nextDouble() * 4.0) % 4.0 * (Math.PI / 2.0);
        return new Glyph(origin, cardinal, now);
    }

    @EventHandler
    public void onRender(EventRender3D e) {
        if (!isEnabled() || mc.player == null || mc.world == null) return;
        if (glyphs.isEmpty()) return;

        int selected = themeColor.getValue() ? ColorUtil.fade(1) : color.getValue();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        float lineWidth = width.getValue();
        Matrix4f matrix = e.getMatrixStack().peek().getPositionMatrix();
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        VertexConsumer buffer = immediate.getBuffer(LINE_LAYER);

        for (Glyph glyph : glyphs) {
            float age = (System.currentTimeMillis() - glyph.birthTime) / 1000f;
            float remaining = LIFETIME - age;
            float fade = remaining < FADE_DURATION ? Math.max(0f, remaining / FADE_DURATION) : 1f;
            glyph.drawPath(buffer, matrix, camera, selected, lineWidth, fade);
        }

        immediate.draw();
    }

    @Override
    protected void onEnable() {
        glyphs.clear();
    }

    @Override
    protected void onDisable() {
        glyphs.clear();
    }

    private class Glyph {
        private Vec3d pos;
        private double yaw;
        private int turnTimer;
        private boolean rising;
        private double targetY = -1.0;
        private long birthTime;
        private final Deque<Vec3d> path = new ArrayDeque<>();
        private static final int MAX_PATH = 36;
        private static final float MAX_RADIUS = 14f;

        Glyph(Vec3d pos, double yaw, long birthTime) {
            this.pos = pos;
            this.yaw = yaw;
            this.turnTimer = 0;
            this.birthTime = birthTime;
            this.path.addLast(pos);
        }

        void reset(long now) {
            Vec3d playerPos = mc.player != null ? mc.player.getEntityPos() : pos;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 3.0 + random.nextDouble() * 6.0;
            pos = playerPos
                    .add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance)
                    .add(0.0, 1.0 + random.nextDouble() * 4.0, 0.0);
            yaw = Math.round(random.nextDouble() * 4.0) % 4.0 * (Math.PI / 2.0);
            turnTimer = 0;
            rising = false;
            targetY = -1.0;
            birthTime = now;
            path.clear();
            path.addLast(pos);
        }

        void update(float step) {
            if (rising) {
                double groundY = groundTop(pos.x, pos.z);
                double nextY = pos.y + step * 0.5;
                if (nextY >= targetY) {
                    nextY = targetY;
                    rising = false;
                }
                if (nextY < groundY) {
                    nextY = groundY;
                }
                pos = new Vec3d(pos.x, nextY, pos.z);
                path.addLast(pos);
                if (path.size() > MAX_PATH) {
                    path.removeFirst();
                }
                return;
            }

            if (turnTimer <= 0) {
                turnTimer = 60;
                chooseTurn();
                if (random.nextDouble() < 0.3) {
                    rising = true;
                    targetY = pos.y + 0.3 + random.nextDouble() * 0.7;
                }
            }
            turnTimer--;

            Vec3d playerPos = mc.player.getEntityPos();
            double dist = Math.hypot(pos.x - playerPos.x, pos.z - playerPos.z);
            if (dist > MAX_RADIUS) {
                yaw = snapCardinal(Math.atan2(playerPos.z - pos.z, playerPos.x - pos.x));
                rising = false;
                turnTimer = 60;
            }

            pos = new Vec3d(pos.x + Math.cos(yaw) * step, pos.y, pos.z + Math.sin(yaw) * step);
            path.addLast(pos);
            if (path.size() > MAX_PATH) {
                path.removeFirst();
            }
        }

        private void chooseTurn() {
            if (random.nextBoolean()) {
                yaw += Math.PI / 2.0;
            } else {
                yaw -= Math.PI / 2.0;
            }
        }

        private double snapCardinal(double angle) {
            return Math.round(angle / (Math.PI / 2.0)) * (Math.PI / 2.0);
        }

        private double groundTop(double x, double z) {
            if (mc.world == null) {
                return pos.y;
            }
            int top = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, (int) Math.floor(x), (int) Math.floor(z));
            return top + 0.05;
        }

        void drawPath(VertexConsumer buffer, Matrix4f m, Vec3d camera, int color, float width, float fade) {
            Vec3d prev = null;
            Iterator<Vec3d> iterator = path.iterator();
            while (iterator.hasNext()) {
                Vec3d point = iterator.next();
                if (prev != null) {
                    Vec3d a = prev.subtract(camera);
                    Vec3d b = point.subtract(camera);
                    line(buffer, m, color, a.x, a.y, a.z, b.x, b.y, b.z, width, fade);
                }
                prev = point;
            }
        }

        private void line(VertexConsumer buffer, Matrix4f m, int color,
                          double x1, double y1, double z1, double x2, double y2, double z2,
                          float width, float fade) {
            float dx = (float) (x2 - x1);
            float dy = (float) (y2 - y1);
            float dz = (float) (z2 - z1);
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length <= 0.0001F) return;

            int alpha = (int) (255 * Math.min(1, Math.max(0, fade)));
            int c = ColorUtil.replAlpha(color, alpha);
            buffer.vertex(m, (float) x1, (float) y1, (float) z1)
                    .color(c)
                    .normal(dx / length, dy / length, dz / length)
                    .lineWidth(width);
            buffer.vertex(m, (float) x2, (float) y2, (float) z2)
                    .color(c)
                    .normal(dx / length, dy / length, dz / length)
                    .lineWidth(width);
        }
    }
}

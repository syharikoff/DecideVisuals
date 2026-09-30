package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.manager.event_impl.AttackEvent;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Эффект волны при ударе (порт из AchreonVisuals HitWave). */
@ModuleInfo(
        name = "Hit Wave",
        desc = "Эффект волны при ударе",
        category = Category.VISUALS
)
public class HitWave extends Module {
    private static final RenderPipeline WAVE_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation("pipeline/hitwave_lines")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.DrawMode.DEBUG_LINES)
                    .build()
    );
    private static final RenderLayer WAVE_LINES = RenderLayer.of("hitwave_lines",
            RenderSetup.builder(WAVE_LINES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .build());

    private static final RenderPipeline WAVE_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation("pipeline/hitwave_fill")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );
    private static final RenderLayer WAVE_FILL = RenderLayer.of("hitwave_fill",
            RenderSetup.builder(WAVE_FILL_PIPELINE).translucent().expectedBufferSize(1536).build());

    public BooleanSetting fill = new BooleanSetting(this, "Заливка", true);
    public BooleanSetting outline = new BooleanSetting(this, "Контур", true);
    public SliderSetting lineWidth = new SliderSetting(this, "Толщина линий", 2.0F, 0.5F, 5.0F, 0.1F);
    public SliderSetting fillAlpha = new SliderSetting(this, "Прозрачность заливки", 0.15F, 0.01F, 1.0F, 0.01F);
    public SliderSetting duration = new SliderSetting(this, "Длительность (сек)", 1.5F, 0.5F, 3.0F, 0.1F);
    public SliderSetting maxRadius = new SliderSetting(this, "Максимальный радиус", 12.0F, 5.0F, 20.0F, 1.0F);
    public SliderSetting waveWidth = new SliderSetting(this, "Ширина волны", 2.5F, 1.0F, 5.0F, 0.5F);
    public BooleanSetting useThemeColor = new BooleanSetting(this, "Использовать цвет темы", true);
    public ColorSetting waveColor = new ColorSetting(this, "Цвет волны", 0xFFFFFFFF)
            .setVisible(() -> !useThemeColor.getValue());

    private final BufferAllocator allocator = new BufferAllocator(1 << 18);
    private final List<WaveEffect> waveEffects = new ArrayList<>();

    @EventHandler
    public void onAttack(AttackEvent e) {
        if (e.getTarget() == null || mc.world == null || !isEnabled()) return;
        Vec3d pos = e.getTarget().getEntityPos();
        BlockPos basePos = BlockPos.ofFloored(pos.x, pos.y - 0.1, pos.z);
        waveEffects.add(new WaveEffect(basePos, System.currentTimeMillis()));
    }

    @EventHandler
    public void onRender(EventRender3D e) {
        if (mc.world == null || !isEnabled()) return;
        if (waveEffects.isEmpty()) return;

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        MatrixStack matrices = e.getMatrixStack();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();

        Iterator<WaveEffect> iterator = waveEffects.iterator();
        while (iterator.hasNext()) {
            WaveEffect wave = iterator.next();
            if (wave.isExpired()) {
                iterator.remove();
                continue;
            }
            wave.render(matrices, immediate, camera);
        }

        immediate.draw();
    }

    @Override
    protected void onDisable() {
        waveEffects.clear();
    }

    private class WaveEffect {
        private final BlockPos centerPos;
        private final long startTime;
        private final List<CachedBlock> cachedBlocks = new ArrayList<>();

        WaveEffect(BlockPos centerPos, long startTime) {
            this.centerPos = centerPos;
            this.startTime = startTime;

            int maxR = (int) (float) maxRadius.getValue();
            int maxR2 = maxR * maxR;
            for (int x = -maxR; x <= maxR; x++) {
                for (int z = -maxR; z <= maxR; z++) {
                    float distSq = x * x + z * z;
                    if (distSq > maxR2) continue;

                    BlockPos checkPos = centerPos.add(x, 0, z);
                    BlockPos renderPos = findSurface(checkPos);
                    if (renderPos == null) continue;

                    BlockState state = mc.world.getBlockState(renderPos);
                    VoxelShape shape = state.getOutlineShape(mc.world, renderPos);
                    if (shape.isEmpty()) continue;

                    cachedBlocks.add(new CachedBlock(renderPos, shape.getBoundingBox(), (float) Math.sqrt(distSq)));
                }
            }
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > (long) (duration.getValue() * 1000);
        }

        void render(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Vec3d camera) {
            long elapsed = System.currentTimeMillis() - startTime;
            float progress = (float) (elapsed / (duration.getValue() * 1000f));
            float currentRadius = progress * maxRadius.getValue();
            float waveW = waveWidth.getValue();
            float globalAlpha = (float) Math.pow(1.0f - progress, 0.6);

            int rendered = 0;
            int maxPerFrame = 200;

            float minRadSq = (currentRadius - waveW) * (currentRadius - waveW);
            float maxRadSq = (currentRadius + 0.5f) * (currentRadius + 0.5f);

            int baseColor = getColor();
            int red = useThemeColor.getValue() ? brighten(ColorUtil.red(baseColor)) : ColorUtil.red(baseColor);
            int green = useThemeColor.getValue() ? brighten(ColorUtil.green(baseColor)) : ColorUtil.green(baseColor);
            int blue = useThemeColor.getValue() ? brighten(ColorUtil.blue(baseColor)) : ColorUtil.blue(baseColor);

            int glowColor = ColorUtil.getColor(red, green, blue, 255);
            int coreColor = ColorUtil.getColor(core(red), core(green), core(blue), 255);

            for (CachedBlock cb : cachedBlocks) {
                if (rendered >= maxPerFrame) break;
                float distance = cb.distance;
                if (distance < minRadSq || distance > maxRadSq) continue;

                rendered++;
                float localAlpha = 1.0f - Math.abs(distance - currentRadius) / waveW;
                localAlpha = Math.max(0, Math.min(1, localAlpha)) * globalAlpha;

                if (localAlpha > 0.05f) {
                    drawBlock(matrices, immediate, camera, cb, glowColor, coreColor, localAlpha);
                }
            }
        }

        private void drawBlock(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Vec3d camera,
                               CachedBlock cb, int glowColor, int coreColor, float localAlpha) {
            Box worldBox = cb.box.offset(cb.pos);
            float sx = (float) (worldBox.minX - camera.x);
            float sy = (float) (worldBox.minY - camera.y);
            float sz = (float) (worldBox.minZ - camera.z);
            float w = (float) worldBox.getLengthX();
            float h = (float) worldBox.getLengthY();
            float d = (float) worldBox.getLengthZ();

            matrices.push();
            matrices.translate(sx, sy, sz);
            Matrix4f m = matrices.peek().getPositionMatrix();

            if (fill.getValue()) {
                int fAlpha = (int) (fillAlpha.getValue() * localAlpha * 255);
                drawBoxFill(immediate, m, 0, 0, 0, w, h, d, ColorUtil.replAlpha(glowColor, fAlpha));
            }
            if (outline.getValue()) {
                drawBoxOutline(immediate, m, 0, 0, 0, w, h, d, ColorUtil.replAlpha(coreColor, (int) (localAlpha * 255)),
                        lineWidth.getValue());
            }
            matrices.pop();
        }

        private BlockPos findSurface(BlockPos pos) {
            for (int y = 2; y >= -4; y--) {
                BlockPos p = pos.up(y);
                BlockState state = mc.world.getBlockState(p);
                BlockState upState = mc.world.getBlockState(p.up());

                boolean currentSolid = !state.getCollisionShape(mc.world, p).isEmpty();
                boolean upSolid = !upState.getCollisionShape(mc.world, p.up()).isEmpty();

                if (currentSolid && !upSolid) {
                    return p;
                }
            }
            return null;
        }

        private class CachedBlock {
            final BlockPos pos;
            final Box box;
            final float distance;

            CachedBlock(BlockPos pos, Box box, float distance) {
                this.pos = pos;
                this.box = box;
                this.distance = distance;
            }
        }
    }

    private int getColor() {
        if (useThemeColor.getValue()) {
            return ColorUtil.fade(1);
        }
        return waveColor.getValue();
    }

    private static int brighten(int value) {
        return (int) Math.min(255, value + (255 - value) * 0.45f);
    }

    private static int core(int value) {
        return (int) Math.min(255, value + (255 - value) * 0.72f);
    }

    private static void drawBoxFill(VertexConsumerProvider.Immediate immediate, Matrix4f m,
                                    float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        VertexConsumer buf = immediate.getBuffer(WAVE_FILL);
        quad(buf, m, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, color);
        quad(buf, m, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, color);
        quad(buf, m, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, color);
        quad(buf, m, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, color);
        quad(buf, m, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, color);
        quad(buf, m, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, color);
    }

    private static void quad(VertexConsumer buf, Matrix4f m,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4, int color) {
        buf.vertex(m, x1, y1, z1).color(color);
        buf.vertex(m, x2, y2, z2).color(color);
        buf.vertex(m, x3, y3, z3).color(color);
        buf.vertex(m, x4, y4, z4).color(color);
    }

    private static void drawBoxOutline(VertexConsumerProvider.Immediate immediate, Matrix4f m,
                                       float x1, float y1, float z1, float x2, float y2, float z2, int color, float width) {
        VertexConsumer buf = immediate.getBuffer(WAVE_LINES);
        line(buf, m, x1, y1, z1, x2, y1, z1, color, width);
        line(buf, m, x2, y1, z1, x2, y1, z2, color, width);
        line(buf, m, x2, y1, z2, x1, y1, z2, color, width);
        line(buf, m, x1, y1, z2, x1, y1, z1, color, width);

        line(buf, m, x1, y2, z1, x2, y2, z1, color, width);
        line(buf, m, x2, y2, z1, x2, y2, z2, color, width);
        line(buf, m, x2, y2, z2, x1, y2, z2, color, width);
        line(buf, m, x1, y2, z2, x1, y2, z1, color, width);

        line(buf, m, x1, y1, z1, x1, y2, z1, color, width);
        line(buf, m, x2, y1, z1, x2, y2, z1, color, width);
        line(buf, m, x2, y1, z2, x2, y2, z2, color, width);
        line(buf, m, x1, y1, z2, x1, y2, z2, color, width);
    }

    private static void line(VertexConsumer buf, Matrix4f m,
                             float x1, float y1, float z1, float x2, float y2, float z2, int color, float width) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len <= 0.0001F) return;
        buf.vertex(m, x1, y1, z1).color(color).normal(dx / len, dy / len, dz / len).lineWidth(width);
        buf.vertex(m, x2, y2, z2).color(color).normal(dx / len, dy / len, dz / len).lineWidth(width);
    }
}

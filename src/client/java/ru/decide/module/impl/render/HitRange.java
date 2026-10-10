package ru.decide.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.decide.manager.event_impl.EventRender3D;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.module.api.settings.impl.ColorSetting;
import ru.decide.module.api.settings.impl.ModeSetting;
import ru.decide.module.api.settings.impl.SliderSetting;

/**
 * Кольцо радиуса удара (3.32 блока) вокруг игрока.
 * <p>
 * В оригинале это был Tessellator с двумя буферами: контур (с внутренним
 * «свечением» втрое толще и прозрачностью 0.25) и опциональная заливка.
 * Здесь те же кольца собираются в пару VertexConsumer.
 * <p>
 * Радиус берётся из ванильного {@code 3.32}: ровно на этом расстоянии
 * цель становится «в досягаемости», поэтому цвет кольца меняется на
 * «цвет в радиусе», когда кто-то из игроков реально в этой зоне.
 */
@ModuleInfo(
        name = "Hit Range",
        desc = "Показывает радиус удара",
        category = Category.UTILITIES
)
public class HitRange extends Module {

    private static final float HIT_RADIUS = 3.32F;
    private static final float Y_OFFSET = 0.02F;
    private static final int RHOMBUS_SIDES = 8;
    private static final int CIRCLE_SIDES = 96;

    private static final RenderPipeline RING_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("decide", "pipeline/world/hit_range_ring"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );

    private static final RenderLayer RING_LAYER = RenderLayer.of("hit_range_ring",
            RenderSetup.builder(RING_PIPELINE).translucent().expectedBufferSize(1 << 14).build());

    // Заливка — треугольники, контур — квады: в одном буфере их смешивать нельзя,
    // иначе треугольник замыкается на чужую вершину и получается рваная фигура.
    private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("decide", "pipeline/world/hit_range_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );

    private static final RenderLayer FILL_LAYER = RenderLayer.of("hit_range_fill",
            RenderSetup.builder(FILL_PIPELINE).translucent().expectedBufferSize(1 << 13).build());

    private final BufferAllocator allocator = new BufferAllocator(1 << 16);

    public ModeSetting mode = new ModeSetting(this, "Режим", "Ромб", "Ромб", "Круг");
    public ColorSetting color = new ColorSetting(this, "Цвет", 0xC850B4FF);
    public ColorSetting rangeColor = new ColorSetting(this, "Цвет в радиусе", 0xDC2AFF5F);
    public SliderSetting fillAlpha = new SliderSetting(this, "Прозрачность заливки", 0.18F, 0.0F, 1.0F, 0.01F)
            .setVisible(this::isFillVisible);
    public SliderSetting thickness = new SliderSetting(this, "Толщина линии", 0.06F, 0.01F, 0.5F, 0.01F);
    public SliderSetting rotSpeed = new SliderSetting(this, "Скорость вращения", 0.4F, 0.05F, 2.0F, 0.05F);
    public BooleanSetting fill = new BooleanSetting(this, "Заполнение", false);
    public BooleanSetting showEnemies = new BooleanSetting(this, "Отображать на противниках", false);

    private float rotationAngle;

    private boolean isFillVisible() {
        return fill.getValue();
    }

    @EventHandler
    public void onRender(EventRender3D event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        float tickDelta = event.getTickDelta();
        MatrixStack pose = event.getMatrixStack();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

        int sides = mode.is("Круг") ? CIRCLE_SIDES : RHOMBUS_SIDES;
        double rotation = Math.toRadians(rotationAngle);
        rotationAngle = (rotationAngle + rotSpeed.getValue()) % 360.0F;
        float half = thickness.getValue() / 2.0F;

        PlayerEntity self = mc.player;
        boolean anyInRange = hasPlayerInRange(self);

        Vec3dLocal[] positions = collectPositions(tickDelta, self, anyInRange);

        if (positions.length == 0) {
            return;
        }

        Matrix4f[] matrices = new Matrix4f[positions.length];

        for (int i = 0; i < positions.length; i++) {
            Vec3dLocal entry = positions[i];
            pose.push();
            pose.translate(entry.x - cam.x, entry.y + Y_OFFSET - cam.y, entry.z - cam.z);
            pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
            matrices[i] = new Matrix4f(pose.peek().getPositionMatrix());
            pose.pop();
        }

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

        // Сначала заливка, потом контур: Immediate закрывает предыдущий слой при
        // запросе следующего, поэтому это обязательно два отдельных прохода.
        if (fill.getValue()) {
            VertexConsumer fillBuf = immediate.getBuffer(FILL_LAYER);

            for (int i = 0; i < positions.length; i++) {
                appendFill(fillBuf, matrices[i], sides, rotation, positions[i].color);
            }
        }

        VertexConsumer ringBuf = immediate.getBuffer(RING_LAYER);

        for (int i = 0; i < positions.length; i++) {
            appendRing(ringBuf, matrices[i], sides, rotation, half, positions[i].color);
        }

        immediate.draw();
    }

    private Vec3dLocal[] collectPositions(float tickDelta, PlayerEntity self, boolean anyInRange) {
        int enemies = showEnemies.getValue() ? mc.world.getPlayers().size() : 0;
        int size = 1 + enemies;
        Vec3dLocal[] out = new Vec3dLocal[size];

        Vec3d pos = self.getLerpedPos(tickDelta);
        out[0] = new Vec3dLocal(pos.x, pos.y, pos.z, anyInRange ? rangeColor.getValue() : color.getValue());

        if (!showEnemies.getValue()) {
            return out;
        }

        int index = 1;
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (!isVisibleTarget(player, self)) {
                continue;
            }

            Vec3d p = player.getLerpedPos(tickDelta);
            boolean canReach = player.distanceTo(self) <= HIT_RADIUS;

            if (index >= out.length) {
                break;
            }

            out[index++] = new Vec3dLocal(p.x, p.y, p.z, canReach ? rangeColor.getValue() : color.getValue());
        }

        return index == out.length ? out : java.util.Arrays.copyOf(out, index);
    }

    private boolean hasPlayerInRange(PlayerEntity owner) {
        if (mc.world == null) return false;

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (isVisibleTarget(player, owner) && player.distanceTo(owner) <= HIT_RADIUS) {
                return true;
            }
        }

        return false;
    }

    private boolean isVisibleTarget(AbstractClientPlayerEntity player, PlayerEntity owner) {
        return player != null && owner != null && player != owner && player.isAlive()
                && !player.isSpectator() && !isTeammate(player, owner);
    }

    /**
     * В 1.21 у PlayerEntity больше нет {@code isTeammate(Team.PLAYERS)}:
     * команды лежат в скорборде, поэтому сравниваем их напрямую.
     */
    private boolean isTeammate(AbstractClientPlayerEntity player, PlayerEntity owner) {
        if (mc.world == null) return false;

        Scoreboard scoreboard = mc.world.getScoreboard();
        if (scoreboard == null) return false;

        AbstractTeam ownerTeam = scoreboard.getTeam(owner.getNameForScoreboard());
        if (ownerTeam == null) return false;

        return ownerTeam.isEqual(scoreboard.getTeam(player.getNameForScoreboard()));
    }

    private void appendRing(VertexConsumer buf, Matrix4f matrix, int sides,
                            double rotation, float half, int color) {
        int glowColor = multAlpha(color, 0.25F);
        double outerR = HIT_RADIUS + half;
        double innerR = HIT_RADIUS - half;
        double glowOuter = HIT_RADIUS + half * 3.0F;
        double glowInner = HIT_RADIUS - half * 3.0F;

        for (int i = 0; i < sides; i++) {
            double a1 = rotation + (Math.PI * 2) * i / sides;
            double a2 = rotation + (Math.PI * 2) * (i + 1) / sides;

            ringQuad(buf, matrix, a1, a2, outerR, innerR, color);
            ringQuad(buf, matrix, a1, a2, glowOuter, glowInner, glowColor);
        }
    }

    private void ringQuad(VertexConsumer buf, Matrix4f matrix,
                          double a1, double a2, double outerR, double innerR, int color) {
        buf.vertex(matrix, (float) (outerR * Math.cos(a1)), (float) (outerR * Math.sin(a1)), 0.0F).color(color);
        buf.vertex(matrix, (float) (outerR * Math.cos(a2)), (float) (outerR * Math.sin(a2)), 0.0F).color(color);
        buf.vertex(matrix, (float) (innerR * Math.cos(a2)), (float) (innerR * Math.sin(a2)), 0.0F).color(color);
        buf.vertex(matrix, (float) (innerR * Math.cos(a1)), (float) (innerR * Math.sin(a1)), 0.0F).color(color);
    }

    private void appendFill(VertexConsumer buf, Matrix4f matrix, int sides, double rotation, int color) {
        int fillColor = multAlpha(color, fillAlpha.getValue());

        for (int i = 0; i < sides; i++) {
            double a1 = rotation + (Math.PI * 2) * i / sides;
            double a2 = rotation + (Math.PI * 2) * (i + 1) / sides;

            buf.vertex(matrix, 0.0F, 0.0F, 0.0F).color(fillColor);
            buf.vertex(matrix, (float) (HIT_RADIUS * Math.cos(a1)), (float) (HIT_RADIUS * Math.sin(a1)), 0.0F).color(fillColor);
            buf.vertex(matrix, (float) (HIT_RADIUS * Math.cos(a2)), (float) (HIT_RADIUS * Math.sin(a2)), 0.0F).color(fillColor);
        }
    }

    private int multAlpha(int color, float alphaMul) {
        int alpha = color >> 24 & 0xFF;
        if (alpha == 0) {
            alpha = 255;
        }

        alpha = Math.max(0, Math.min(255, (int) (alpha * alphaMul)));
        return color & 0xFFFFFF | alpha << 24;
    }

    private record Vec3dLocal(double x, double y, double z, int color) {
    }
}
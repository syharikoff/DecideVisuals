package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventRender3D;
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
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@ModuleInfo(
        name = "Totem Ghost",
        desc = "Призрак модельки при лопании тотема",
        category = Category.VISUALS
)
public class TotemGhost extends Module implements ModulePreview {

    public ButtonSetting previewButton = PreviewSettings.button(this);

    public ModeSetting typeColor = new ModeSetting(this, "Режим цвета", "Тема", "Свой");
    public ColorSetting tintColor = new ColorSetting(this, "Цвет", 0xFF00FFFF).setVisible(() -> typeColor.is("Свой"));

    public SliderSetting rise = new SliderSetting(this, "Высота подъёма", 2.0F, 0.5F, 4.0F, 0.1F);
    public SliderSetting duration = new SliderSetting(this, "Длительность", 2.0F, 1.0F, 4.0F, 0.1F);

    private final PreviewSettings previewSettings = PreviewSettings.of(this, 4F, 0F, 3F);

    public int getColor() {
        if (typeColor.is("Тема")) {
            return ColorUtil.getClientColor1(1);
        }
        return tintColor.getValue();
    }

    // моделька игрока из ректов: {x1,y1,z1, x2,y2,z2} в блоках относительно ног
    private static final float[][] PARTS = {
            {-0.25F, 1.50F, -0.25F, 0.25F, 2.00F, 0.25F},   // голова
            {-0.25F, 0.75F, -0.125F, 0.25F, 1.50F, 0.125F}, // тело
            {0.25F, 0.75F, -0.125F, 0.50F, 1.50F, 0.125F},  // правая рука
            {-0.50F, 0.75F, -0.125F, -0.25F, 1.50F, 0.125F},// левая рука
            {0.00F, 0.00F, -0.125F, 0.25F, 0.75F, 0.125F},  // правая нога
            {-0.25F, 0.00F, -0.125F, 0.00F, 0.75F, 0.125F}  // левая нога
    };

    // пивоты вращения частей: шея, центр тела, плечи, бёдра
    private static final float[][] PIVOTS = {
            {0.00F, 1.50F, 0.00F},
            {0.00F, 0.75F, 0.00F},
            {0.375F, 1.40F, 0.00F},
            {-0.375F, 1.40F, 0.00F},
            {0.125F, 0.75F, 0.00F},
            {-0.125F, 0.75F, 0.00F}
    };
    private static final int PART_COUNT = PARTS.length;

    // грани и рёбра бокса по битовым индексам углов (bit0=x2, bit1=y2, bit2=z2)
    private static final int[][] FACES = {
            {0, 1, 3, 2}, {4, 5, 7, 6},
            {0, 1, 5, 4}, {2, 3, 7, 6},
            {0, 2, 6, 4}, {1, 3, 7, 5}
    };
    private static final int[][] EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0},
            {4, 5}, {5, 7}, {7, 6}, {6, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private final List<Ghost> ghosts = new ArrayList<>();
    private final BufferAllocator allocator = new BufferAllocator(1 << 14);

    @Override
    public void toggle() {
        super.toggle();
        ghosts.clear();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        ghosts.clear();
    }

    // ───────────────────────────── предпоказ ─────────────────────────────

    @Override
    public PreviewSettings previewSettings() {
        return previewSettings;
    }

    @Override
    public boolean previewNeedsDummy() {
        return true;
    }

    @Override
    public void previewSpawn(PreviewContext ctx) {
        spawnGhost(ctx.dummy());
    }

    @Override
    public void previewStop() {
        ghosts.clear();
    }

    @EventHandler
    public void onPacket(EventPacket e) {
        if (!(e.getPacket() instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != 35) return;
        if (mc.world == null) return;

        Entity entity = packet.getEntity(mc.world);
        if (entity == null) return;

        spawnGhost(entity);
    }

    // публичный вход: сюда же стреляет FakePlayer при срабатывании фейкового тотема
    public void spawnGhost(Entity entity) {
        if (!isEnabled() || entity == null) return;

        Ghost g = new Ghost(entity.getEntityPos(), entity.getYaw());

        // запоминаем последнюю позу: корпус, поворот/наклон головы и мах конечностей
        if (entity instanceof LivingEntity le) {
            g.bodyYaw = le.getBodyYaw();
            g.headYawDeg = MathHelper.clamp(MathHelper.wrapDegrees(le.getHeadYaw() - le.getBodyYaw()), -75F, 75F);

            float limbAngle = le.limbAnimator.getAnimationProgress();
            float limbDist = Math.min(le.limbAnimator.getSpeed(), 1F);

            // формулы маха из ванильной BipedEntityModel
            g.partPitchDeg[0] = le.getPitch();
            g.partPitchDeg[2] = (float) Math.toDegrees(MathHelper.cos(limbAngle * 0.6662F + (float) Math.PI) * 2.0F * limbDist * 0.5F);
            g.partPitchDeg[3] = (float) Math.toDegrees(MathHelper.cos(limbAngle * 0.6662F) * 2.0F * limbDist * 0.5F);
            g.partPitchDeg[4] = (float) Math.toDegrees(MathHelper.cos(limbAngle * 0.6662F) * 1.4F * limbDist);
            g.partPitchDeg[5] = (float) Math.toDegrees(MathHelper.cos(limbAngle * 0.6662F + (float) Math.PI) * 1.4F * limbDist);
        } else {
            g.bodyYaw = entity.getYaw();
        }

        ghosts.add(g);
    }

    @EventHandler
    public void onRender3D(EventRender3D e) {
        if (ghosts.isEmpty() || mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        long durMs = (long) (duration.getValue() * 1000F);
        float riseH = rise.getValue();

        int baseColor = getColor();
        int cr = (baseColor >> 16) & 0xFF;
        int cg = (baseColor >> 8) & 0xFF;
        int cb = baseColor & 0xFF;

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

        // апдейт: прогресс, альфа, кэш матриц; рендер потом проходами по слоям,
        // т.к. immediate с одним аллокатором держит активным только один слой
        Iterator<Ghost> it = ghosts.iterator();
        while (it.hasNext()) {
            Ghost g = it.next();
            float t = (now - g.start) / (float) durMs;
            if (t >= 1F) {
                it.remove();
                continue;
            }

            // плавный взлёт с торможением + быстрое появление и угасание в конце
            float ease = 1F - (1F - t) * (1F - t) * (1F - t);
            float fadeIn = Math.min(t * 8F, 1F);
            float fadeOut = t > 0.55F ? 1F - (t - 0.55F) / 0.45F : 1F;
            g.alphaValue = fadeIn * fadeOut;

            double gx = g.x - cam.x;
            double gy = g.y - cam.y + ease * riseH;
            double gz = g.z - cam.z;

            matrices.push();
            matrices.translate(gx, gy, gz);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-g.bodyYaw + t * 40F));
            // каждая часть вращается вокруг своего пивота с запомненными углами позы
            for (int i = 0; i < PART_COUNT; i++) {
                float pitch = g.partPitchDeg[i];
                float yawOff = i == 0 ? g.headYawDeg : 0F;
                if (pitch == 0F && yawOff == 0F) {
                    g.partMatrices[i].set(matrices.peek().getPositionMatrix());
                    continue;
                }
                float[] pv = PIVOTS[i];
                matrices.push();
                matrices.translate(pv[0], pv[1], pv[2]);
                if (yawOff != 0F) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yawOff));
                if (pitch != 0F) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
                matrices.translate(-pv[0], -pv[1], -pv[2]);
                g.partMatrices[i].set(matrices.peek().getPositionMatrix());
                matrices.pop();
            }
            matrices.pop();

            matrices.push();
            matrices.translate(gx, gy + 1.1, gz);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            g.glowMatrix.set(matrices.peek().getPositionMatrix());
            matrices.pop();
        }

        if (ghosts.isEmpty()) return;

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

        // проход 1: грани ректов
        VertexConsumer buf = immediate.getBuffer(FILL_LAYER);
        for (Ghost g : ghosts) {
            int alpha = (int) (g.alphaValue * 0.25F * 255);
            if (alpha <= 0) continue;
            for (int i = 0; i < PART_COUNT; i++) {
                drawBoxFill(buf, g.partMatrices[i], PARTS[i], cr, cg, cb, alpha);
            }
        }

        // проход 2: оутлайн
        buf = immediate.getBuffer(LINE_LAYER);
        for (Ghost g : ghosts) {
            int alpha = (int) (g.alphaValue * 0.45F * 255);
            if (alpha <= 0) continue;
            for (int i = 0; i < PART_COUNT; i++) {
                drawBoxLines(buf, g.partMatrices[i], PARTS[i], cr, cg, cb, alpha);
            }
        }

        // проход 3: блюм — большой мягкий по центру модельки
        buf = immediate.getBuffer(GLOW_LAYER_BIG);
        for (Ghost g : ghosts) {
            drawGlow(buf, g.glowMatrix, cr, cg, cb, (int) (110 * g.alphaValue), 1.5F);
        }

        // проход 4: малый яркий
        buf = immediate.getBuffer(GLOW_LAYER_SMALL);
        for (Ghost g : ghosts) {
            drawGlow(buf, g.glowMatrix, cr, cg, cb, (int) (160 * g.alphaValue), 0.7F);
        }

        immediate.draw();
    }

    private static float corner(float[] p, int bit, int axis) {
        return ((bit >> axis) & 1) == 0 ? p[axis] : p[axis + 3];
    }

    private static void drawBoxFill(VertexConsumer buf, Matrix4f m, float[] p, int r, int g, int b, int alpha) {
        for (int[] face : FACES) {
            for (int idx : face) {
                buf.vertex(m, corner(p, idx, 0), corner(p, idx, 1), corner(p, idx, 2)).color(r, g, b, alpha);
            }
        }
    }

    private static void drawBoxLines(VertexConsumer buf, Matrix4f m, float[] p, int r, int g, int b, int alpha) {
        for (int[] edge : EDGES) {
            buf.vertex(m, corner(p, edge[0], 0), corner(p, edge[0], 1), corner(p, edge[0], 2)).color(r, g, b, alpha);
            buf.vertex(m, corner(p, edge[1], 0), corner(p, edge[1], 1), corner(p, edge[1], 2)).color(r, g, b, alpha);
        }
    }

    private static void drawGlow(VertexConsumer buf, Matrix4f m, int r, int g, int b, int alpha, float s) {
        if (alpha <= 0) return;
        buf.vertex(m, -s, -s, 0).color(r, g, b, alpha).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buf.vertex(m, s, -s, 0).color(r, g, b, alpha).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buf.vertex(m, s, s, 0).color(r, g, b, alpha).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buf.vertex(m, -s, s, 0).color(r, g, b, alpha).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
    }

    private static class Ghost {
        final double x, y, z;
        final long start;
        float bodyYaw;
        float headYawDeg;
        final float[] partPitchDeg = new float[PART_COUNT];
        float alphaValue;
        final Matrix4f[] partMatrices = new Matrix4f[PART_COUNT];
        final Matrix4f glowMatrix = new Matrix4f();

        Ghost(Vec3d pos, float yaw) {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.bodyYaw = yaw;
            this.start = System.currentTimeMillis();
            for (int i = 0; i < PART_COUNT; i++) {
                partMatrices[i] = new Matrix4f();
            }
        }
    }

    private static final Identifier GLOW_TEXTURE_BIG = Identifier.of("client", "textures/visuals/particles_1.png");
    private static final Identifier GLOW_TEXTURE_SMALL = Identifier.of("client", "textures/visuals/particles_2.png");

    private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "totem_ghost_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer FILL_LAYER = RenderLayer.of(
            "totem_ghost_fill",
            RenderSetup.builder(FILL_PIPELINE).translucent().expectedBufferSize(1 << 12).build()
    );

    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "totem_ghost_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer LINE_LAYER = RenderLayer.of(
            "totem_ghost_lines",
            RenderSetup.builder(LINE_PIPELINE).expectedBufferSize(1 << 12).build()
    );

    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "totem_ghost_glow"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer GLOW_LAYER_BIG = RenderLayer.of(
            "totem_ghost_glow_big",
            RenderSetup.builder(GLOW_PIPELINE)
                    .texture("Sampler0", GLOW_TEXTURE_BIG)
                    .translucent()
                    .expectedBufferSize(1 << 10)
                    .build()
    );

    private static final RenderLayer GLOW_LAYER_SMALL = RenderLayer.of(
            "totem_ghost_glow_small",
            RenderSetup.builder(GLOW_PIPELINE)
                    .texture("Sampler0", GLOW_TEXTURE_SMALL)
                    .translucent()
                    .expectedBufferSize(1 << 10)
                    .build()
    );
}

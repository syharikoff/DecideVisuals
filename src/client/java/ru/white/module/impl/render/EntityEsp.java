package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.Client;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.other.Instance;
import ru.white.utils.other.Projection;
import ru.white.utils.render.RenderUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4d;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        name = "Entity Esp",
        category = Category.VISUALS,
        desc = "Боксы сущностей через стены"
)
public class EntityEsp extends Module {

    public static EntityEsp get() {
        return Instance.get(EntityEsp.class);
    }

    public BooleanSetting player       = new BooleanSetting(this, "Отображения игроков", true);
    public BooleanSetting ignoreNaked  = new BooleanSetting(this, "Игнорировать голых", false)
            .setVisible(() -> player.getValue());
    public BooleanSetting mobs         = new BooleanSetting(this, "Отображения мобов", true);
    public BooleanSetting items        = new BooleanSetting(this, "Отображения предметов", true);

    public ModeSetting box = new ModeSetting(this, "Вид бокса",  "2д", "3д");


    private final List<Entity> entities = new ArrayList<>();
    private final BufferAllocator boxAllocator = new BufferAllocator(1 << 18);

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        entities.clear();
        boxAllocator.clear();
    }

    @EventHandler
    public void onTick(EventTick e) {
        entities.clear();
        if (mc.world != null) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity.isInvisible()) continue;

                if (entity instanceof PlayerEntity p) {
                    if (player.getValue()
                            && (p.getCustomName() == null || !p.getCustomName().getString().startsWith("Ghost_"))
                            && (!ignoreNaked.getValue() || hasArmor(p))) {
                        entities.add(entity);
                    }
                } else if (entity instanceof LivingEntity) {
                    if (mobs.getValue()) entities.add(entity);
                } else if (entity instanceof ItemEntity) {
                    if (items.getValue()) entities.add(entity);
                }
            }
        }
    }

    @EventHandler
    public void onTick(EventRender3D e) {
        float tickDelta = e.getTickDelta();

        if (mc.world == null || mc.player == null || entities.isEmpty()) return;
        if (!box.is("3д")) return;

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(boxAllocator);

        for (Entity entity : entities) {
            if (entity == mc.player && mc.options.getPerspective().isFirstPerson()) {
                continue;
            }
            if (!Projection.isInFrontOfCamera(entity.getBoundingBox().getCenter())) continue;
            if (!isEntityVisible(entity, tickDelta)) continue;

            renderBox(e.getMatrixStack(), immediate, entity, tickDelta);
        }

        immediate.draw();
    }

    @EventHandler
    public void onTick(EventDisplay e) {
        float tickDelta = e.getPartialTicks();

        if (mc.world == null || mc.player == null) return;
        if (!box.is("2д")) return;

        for (Entity entity : entities) {
            Vector4d vec4d = Projection.getVector4D(entity, tickDelta);
            if (Projection.cantSee(vec4d)) continue;

            float distance = (float) mc.gameRenderer.getCamera().getCameraPos().distanceTo(entity.getBoundingBox().getCenter());
            if (distance < 1) continue;

            if (!isEntityVisible(entity, tickDelta)) continue;

            boolean isFriend = entity instanceof PlayerEntity p && Client.get().friendManager().isFriend(p.getName().getString());
            drawBox(isFriend, vec4d, entity);
        }
    }

    private void drawBox(boolean friend, Vector4d vec, Entity player) {
        int client = friend ? ColorUtil.getColor(100, 200, 100) : ColorUtil.fade(1);

        float posX = (float) vec.x;
        float posY = (float) vec.y;
        float endPosX = (float) vec.z;
        float endPosY = (float) vec.w;
        float size = (endPosX - posX) / 3;

        RenderUtil.Render2D.rect(posX - 0.5F, posY - 0.5F, size, 0.5F, client);
        RenderUtil.Render2D.rect(posX - 0.5F, posY, 0.5F, size + 0.5F, client);
        RenderUtil.Render2D.rect(posX - 0.5F, endPosY - size - 0.5F, 0.5F, size, client);
        RenderUtil.Render2D.rect(posX - 0.5F, endPosY - 0.5F, size, 0.5F, client);
        RenderUtil.Render2D.rect(endPosX - size + 1, posY - 0.5F, size, 0.5F, client);
        RenderUtil.Render2D.rect(endPosX + 0.5F, posY, 0.5F, size + 0.5F, client);
        RenderUtil.Render2D.rect(endPosX + 0.5F, endPosY - size - 0.5F, 0.5F, size, client);
        RenderUtil.Render2D.rect(endPosX - size + 1, endPosY - 0.5F, size, 0.5F, client);
    }

    public static boolean hasArmor(PlayerEntity p) {
        return !p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
            || !p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
            || !p.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
            || !p.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    private boolean isEntityVisible(Entity entity, float tickDelta) {
        if (mc.world == null || mc.player == null) return false;
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d interp = entity.getLerpedPos(tickDelta);
        Vec3d entityCenter = interp.add(0, entity.getHeight() / 2.0, 0);

        BlockHitResult result = mc.world.raycast(new RaycastContext(
                cameraPos,
                entityCenter,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        return result.getType() == HitResult.Type.MISS;
    }

    private void renderBox(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Entity target, float partialTicks) {
        if (target == null) return;

        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        double x = target.lastRenderX + (target.getX() - target.lastRenderX) * partialTicks;
        double y = target.lastRenderY + (target.getY() - target.lastRenderY) * partialTicks;
        double z = target.lastRenderZ + (target.getZ() - target.lastRenderZ) * partialTicks;

        net.minecraft.util.math.Box boundingBox = target.getBoundingBox();

        double padding = 0;

        double minX = boundingBox.minX - target.getX() + x - padding - cameraPos.x;
        double minY = boundingBox.minY - target.getY() + y - padding - cameraPos.y;
        double minZ = boundingBox.minZ - target.getZ() + z - padding - cameraPos.z;
        double maxX = boundingBox.maxX - target.getX() + x + padding - cameraPos.x;
        double maxY = boundingBox.maxY - target.getY() + y + padding - cameraPos.y;
        double maxZ = boundingBox.maxZ - target.getZ() + z + padding - cameraPos.z;

        int baseColor = target instanceof AbstractClientPlayerEntity p && Client.get().friendManager().isFriend(p.getNameForScoreboard()) ? ColorUtil.GREEN : ColorUtil.getClientColor1(1);

        int colorDark = baseColor;
        int colorBright = baseColor;

        int[] gradientColors = new int[]{
                ColorUtil.gradient(colorDark,   colorBright, 0,   7),
                ColorUtil.gradient(colorBright, colorDark,   90,  7),
                ColorUtil.gradient(colorDark,   colorBright, 180, 7),
                ColorUtil.gradient(colorBright, colorDark,   270, 7)
        };

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        VertexConsumer fillBuffer = immediate.getBuffer(BOX_FILL_LAYER);
        drawBoxFill(fillBuffer, matrix, minX, minY, minZ, maxX, maxY, maxZ, gradientColors, 10);

        VertexConsumer lineBuffer = immediate.getBuffer(BOX_LINE_LAYER);
        drawBoxOutline(lineBuffer, matrix, minX, minY, minZ, maxX, maxY, maxZ, gradientColors, 255, 0.12, 0.08);
    }

    private static final RenderPipeline BOX_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minecraft", "rendertype_lequal_depth_test"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderPipeline BOX_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minecraft", "rendertype_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final int QUAD_BUFFER_SIZE_BYTES = 1 << 12;

    private static final RenderLayer BOX_FILL_LAYER = RenderLayer.of(
            "night_esp_box_fill",
            RenderSetup.builder(BOX_FILL_PIPELINE).expectedBufferSize(QUAD_BUFFER_SIZE_BYTES).build()
    );

    private static final RenderLayer BOX_LINE_LAYER = RenderLayer.of(
            "night_esp_box_line",
            RenderSetup.builder(BOX_LINE_PIPELINE)
                    .expectedBufferSize(QUAD_BUFFER_SIZE_BYTES)
                    .build()
    );

    public static void drawBoxFill(VertexConsumer buffer, Matrix4f matrix,
                                   double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ,
                                   int[] colors, int fillAlpha) {
        int c0 = ColorUtil.replAlpha(colors[0], fillAlpha);
        int c1 = ColorUtil.replAlpha(colors[1], fillAlpha);
        int c2 = ColorUtil.replAlpha(colors[2], fillAlpha);
        int c3 = ColorUtil.replAlpha(colors[3], fillAlpha);

        float x0 = (float) minX, x1 = (float) maxX;
        float y0 = (float) minY, y1 = (float) maxY;
        float z0 = (float) minZ, z1 = (float) maxZ;

        // Bottom face
        buffer.vertex(matrix, x0, y0, z0).color(c0);
        buffer.vertex(matrix, x1, y0, z0).color(c1);
        buffer.vertex(matrix, x1, y0, z1).color(c2);
        buffer.vertex(matrix, x0, y0, z1).color(c3);

        // Top face
        buffer.vertex(matrix, x0, y1, z0).color(c0);
        buffer.vertex(matrix, x0, y1, z1).color(c3);
        buffer.vertex(matrix, x1, y1, z1).color(c2);
        buffer.vertex(matrix, x1, y1, z0).color(c1);

        // Front face (maxZ)
        buffer.vertex(matrix, x0, y0, z1).color(c3);
        buffer.vertex(matrix, x1, y0, z1).color(c2);
        buffer.vertex(matrix, x1, y1, z1).color(c2);
        buffer.vertex(matrix, x0, y1, z1).color(c3);

        // Back face (minZ)
        buffer.vertex(matrix, x1, y0, z0).color(c1);
        buffer.vertex(matrix, x0, y0, z0).color(c0);
        buffer.vertex(matrix, x0, y1, z0).color(c0);
        buffer.vertex(matrix, x1, y1, z0).color(c1);

        // Left face (minX)
        buffer.vertex(matrix, x0, y0, z0).color(c0);
        buffer.vertex(matrix, x0, y0, z1).color(c3);
        buffer.vertex(matrix, x0, y1, z1).color(c3);
        buffer.vertex(matrix, x0, y1, z0).color(c0);

        // Right face (maxX)
        buffer.vertex(matrix, x1, y0, z1).color(c2);
        buffer.vertex(matrix, x1, y0, z0).color(c1);
        buffer.vertex(matrix, x1, y1, z0).color(c1);
        buffer.vertex(matrix, x1, y1, z1).color(c2);
    }

    public static void drawBoxOutline(VertexConsumer buffer, Matrix4f matrix,
                                      double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ,
                                      int[] colors, int outlineAlpha, double dashLength, double gapLength) {
        int[] c = new int[4];
        for (int i = 0; i < 4; i++) {
            c[i] = ColorUtil.replAlpha(colors[i], outlineAlpha);
        }

        // Bottom face edges
        drawDashedLineSegment(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, c[0], c[1], dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, c[1], c[2], dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, c[2], c[3], dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, c[3], c[0], dashLength, gapLength);

        // Top face edges
        drawDashedLineSegment(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, c[0], c[1], dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, c[1], c[2], dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, c[2], c[3], dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, c[3], c[0], dashLength, gapLength);

        // Vertical edges
        drawDashedLineSegment(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, c[0], c[0], dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, c[1], c[1], dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, c[2], c[2], dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, c[3], c[3], dashLength, gapLength);
    }

    public static void drawDashedLineSegment(VertexConsumer buffer, Matrix4f matrix,
                                             double x1, double y1, double z1,
                                             double x2, double y2, double z2,
                                             int color1, int color2, double dashLength, double gapLength) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (length < 0.001) return;

        double unitX = dx / length;
        double unitY = dy / length;
        double unitZ = dz / length;

        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int a1 = (color1 >> 24) & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF;

        double segmentLength = dashLength + gapLength;
        double currentPos = 0;

        while (currentPos < length) {
            double dashStart = currentPos;
            double dashEnd = Math.min(currentPos + dashLength, length);

            if (dashEnd > dashStart) {
                double startX = x1 + unitX * dashStart;
                double startY = y1 + unitY * dashStart;
                double startZ = z1 + unitZ * dashStart;

                double endX = x1 + unitX * dashEnd;
                double endY = y1 + unitY * dashEnd;
                double endZ = z1 + unitZ * dashEnd;

                double t = dashStart / length;
                int r = (int) (r1 + (r2 - r1) * t);
                int g = (int) (g1 + (g2 - g1) * t);
                int b = (int) (b1 + (b2 - b1) * t);
                int a = (int) (a1 + (a2 - a1) * t);

                buffer.vertex(matrix, (float) startX, (float) startY, (float) startZ).color(r, g, b, a);

                t = dashEnd / length;
                r = (int) (r1 + (r2 - r1) * t);
                g = (int) (g1 + (g2 - g1) * t);
                b = (int) (b1 + (b2 - b1) * t);
                a = (int) (a1 + (a2 - a1) * t);

                buffer.vertex(matrix, (float) endX, (float) endY, (float) endZ).color(r, g, b, a);
            }

            currentPos += segmentLength;
        }
    }
}

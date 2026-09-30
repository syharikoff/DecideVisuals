package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.MultiBooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.other.Projection;
import ru.white.utils.render.ItemRender;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Fonts;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ModuleInfo(
        name = "Trajectories",
        desc = "Показывает траекторию, иконку и точку падения жемчуга, стрелы и трезубца",
        category = Category.VISUALS
)
public class Trajectories extends Module {

    private static final int MAX_STEPS = 200;

    public final MultiBooleanSetting items = new MultiBooleanSetting(this, "Предметы",
            BooleanSetting.of("Жемчуг эндера", true),
            BooleanSetting.of("Стрелы", true),
            BooleanSetting.of("Трезубец", true));

    public final BooleanSetting fromHand = new BooleanSetting(this, "Из рук", true);
    public final BooleanSetting flying = new BooleanSetting(this, "Летящие снаряды", true);
    public final BooleanSetting showLine = new BooleanSetting(this, "Линия траектории", true);
    public final BooleanSetting glow = new BooleanSetting(this, "Свечение линии", false)
            .setVisible(() -> showLine.getValue());
    public final BooleanSetting showIcon = new BooleanSetting(this, "Иконка предмета", true);
    public final BooleanSetting showTime = new BooleanSetting(this, "Время до падения", true);
    public final SliderSetting lineWidth = new SliderSetting(this, "Толщина линии", 2f, 1f, 5f, 0.5f)
            .setVisible(() -> showLine.getValue());
 
    public final SliderSetting indicatorSize = new SliderSetting(this, "Размер метки", 0.5f, 0.1f, 2f, 0.1f);

    private static final int HELD_KEY = 0;

    private final BufferAllocator allocator = new BufferAllocator(1 << 17);
    private final Map<Integer, SmoothedState> smoothed = new LinkedHashMap<>();
    private final List<SmoothedState> renderStates = new ArrayList<>();
    private long lastFrameNs = 0;

    @Override
    protected void onDisable() {
        smoothed.clear();
        renderStates.clear();
        lastFrameNs = 0;
    }

    @EventHandler
    public void onRender3D(EventRender3D e) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        float partial = e.getTickDelta();
        long now = System.nanoTime();
        float dt = lastFrameNs == 0 ? 0.05f : Math.min(0.1f, (now - lastFrameNs) / 1_000_000_000f);
        lastFrameNs = now;

        updateSmoothedPredictions(partial, dt);
        if (renderStates.isEmpty()) {
            return;
        }

        MatrixStack stack = e.getMatrixStack();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        Matrix4f matrix = stack.peek().getPositionMatrix();

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

        // immediate с одним аллокатором держит активным один слой — рисуем проходами,
        // геометрия ленты считается один раз на состояние
        if (showLine.getValue()) {
            List<RibbonData> ribbons = new ArrayList<>(renderStates.size());
            for (SmoothedState state : renderStates) {
                RibbonData data = buildRibbon(state, cam);
                if (data != null) ribbons.add(data);
            }

            if (glow.getValue()) {
                VertexConsumer glowBuf = immediate.getBuffer(GLOW_RIBBON_LAYER);
                for (RibbonData data : ribbons) {
                    emitGlowRibbon(glowBuf, matrix, data);
                }
            }

            VertexConsumer ribbonBuf = immediate.getBuffer(RIBBON_LAYER);
            for (RibbonData data : ribbons) {
                emitRibbon(ribbonBuf, matrix, data);
            }
        }

        VertexConsumer fill = immediate.getBuffer(FILL_LAYER);
        for (SmoothedState state : renderStates) {
            drawLandingMarkerFill(fill, matrix, cam, state);
        }

        VertexConsumer lines = immediate.getBuffer(LINE_LAYER);
        for (SmoothedState state : renderStates) {
            drawLandingMarkerLines(lines, matrix, cam, state);
        }

        immediate.draw();
    }

    @EventHandler
    public void onDisplay(EventDisplay e) {
        if (mc.player == null || renderStates.isEmpty()) {
            return;
        }

        if (!showIcon.getValue() && !showTime.getValue()) {
            return;
        }

        for (SmoothedState state : renderStates) {
            Vec3d screen = Projection.worldSpaceToScreenSpace(state.impact.add(0, 0.25, 0));
            if (screen.z <= 0 || screen.z >= 1) {
                continue;
            }

            float centerX = (float) screen.x;
            float centerY = (float) screen.y;
            float iconSize = 10f;
            float boxSize = iconSize + 4f;

            if (showIcon.getValue() && !state.icon.isEmpty()) {
                RenderUtil.Render2D.rect(
                        centerX - boxSize / 2f,
                        centerY - boxSize / 2f,
                        boxSize,
                        boxSize,
                        ColorUtil.getColor(11, 11, 11, 140),
                        4
                );
                ItemRender.drawItemWithContext(
                        e.getDrawContext(),
                        state.icon,
                        centerX - iconSize / 2f,
                        centerY - iconSize / 2f,
                        iconSize / 16f,
                        1f
                );
            }

            if (showTime.getValue()) {
                String text = String.format(Locale.ROOT, "%.1fs", state.ticks / 20f);
                float fontSize = 6f;
                float textWidth = Fonts.sf_regular.getWidth(text, fontSize) + 6;
                float textX = centerX - textWidth / 2f;
                float textY = centerY + boxSize / 2f + 2f;

                RenderUtil.Render2D.rect(textX, textY, textWidth, fontSize + 3, ColorUtil.getColor(0, 0, 0, 128), 3);
                Fonts.sf_regular.draw(text, textX + 3, textY + 1.5f, fontSize, ColorUtil.getColor(255));
            }
        }
    }

    private void updateSmoothedPredictions(float partial, float dt) {
        for (SmoothedState state : smoothed.values()) {
            state.active = false;
        }

        if (fromHand.getValue()) {
            collectFromHand(partial, dt);
        }

        if (flying.getValue()) {
            collectFlying(partial, dt);
        }

        smoothed.entrySet().removeIf(entry -> !entry.getValue().active);

        renderStates.clear();
        renderStates.addAll(smoothed.values());
    }

    private void pushRaw(int key, Prediction raw, float dt) {
        if (raw == null) {
            return;
        }

        SmoothedState state = smoothed.get(key);
        if (state == null) {
            state = SmoothedState.from(raw);
            smoothed.put(key, state);
        } else {
            state.blendToward(raw, dt, 100);
        }
        state.active = true;
    }

    private void collectFromHand(float partial, float dt) {
        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();

        if (items.getValue("Жемчуг эндера")) {
            if (main.isOf(Items.ENDER_PEARL)) {
                pushRaw(HELD_KEY, simulateThrow(main, partial, 1.5, 0.03, false), dt);
            } else if (off.isOf(Items.ENDER_PEARL)) {
                pushRaw(HELD_KEY, simulateThrow(off, partial, 1.5, 0.03, true), dt);
            }
        }

        if (items.getValue("Стрелы")) {
            if (mc.player.isUsingItem() && mc.player.getActiveItem().getItem() == Items.BOW) {
                float pull = BowItem.getPullProgress(mc.player.getItemUseTime());
                if (pull >= 0.1f) {
                    boolean offHand = mc.player.getActiveHand() == net.minecraft.util.Hand.OFF_HAND;
                    pushRaw(HELD_KEY, simulateThrow(new ItemStack(Items.ARROW), partial, pull * 3.0, 0.05, offHand), dt);
                }
            } else {
                ItemStack crossbow = null;
                boolean crossbowOffHand = false;
                if (main.isOf(Items.CROSSBOW)) {
                    crossbow = main;
                } else if (off.isOf(Items.CROSSBOW)) {
                    crossbow = off;
                    crossbowOffHand = true;
                }

                if (crossbow != null && isCrossbowCharged(crossbow)) {
                    pushRaw(HELD_KEY, simulateThrow(getCrossbowProjectile(crossbow), partial, 3.15, 0.05, crossbowOffHand), dt);
                }
            }
        }

        if (items.getValue("Трезубец")
                && mc.player.isUsingItem()
                && mc.player.getActiveItem().getItem() == Items.TRIDENT) {
            int useTime = mc.player.getItemUseTime();
            if (useTime > 0) {
                float force = Math.min(useTime / 10f, 1.0f);
                if (force < 0.1f) {
                    force = 0.1f;
                }
                boolean offHand = mc.player.getActiveHand() == net.minecraft.util.Hand.OFF_HAND;
                pushRaw(HELD_KEY, simulateThrow(new ItemStack(Items.TRIDENT), partial, 2.5 * force, 0.05, offHand), dt);
            }
        }
    }

    private ItemStack getCrossbowProjectile(ItemStack crossbow) {
        ChargedProjectilesComponent charged = crossbow.get(DataComponentTypes.CHARGED_PROJECTILES);
        if (charged != null && !charged.getProjectiles().isEmpty()) {
            return charged.getProjectiles().getFirst();
        }
        return new ItemStack(Items.ARROW);
    }

    private void collectFlying(float partial, float dt) {
        for (Entity entity : mc.world.getEntities()) {
            if (entity.getVelocity().lengthSquared() < 0.0004) {
                continue;
            }

            double gravity;
            ItemStack icon;
            if (entity instanceof EnderPearlEntity) {
                if (!items.getValue("Жемчуг эндера")) continue;
                gravity = 0.03;
                icon = new ItemStack(Items.ENDER_PEARL);
            } else if (entity instanceof TridentEntity trident) {
                if (!items.getValue("Трезубец")) continue;
                if (trident.isOnGround() || trident.isNoClip()) continue;
                gravity = 0.05;
                icon = new ItemStack(Items.TRIDENT);
            } else if (entity instanceof ArrowEntity || entity instanceof PersistentProjectileEntity) {
                if (!items.getValue("Стрелы")) continue;
                if (entity instanceof ArrowEntity arrow && arrow.isOnGround()) continue;
                gravity = 0.05;
                icon = new ItemStack(Items.ARROW);
            } else {
                continue;
            }

            Vec3d pos = entity.getLerpedPos(partial);
            Vec3d vel = entity.getVelocity();
            Entity owner = entity instanceof ProjectileEntity projectile ? projectile.getOwner() : null;

            pushRaw(entity.getId(), simulate(pos, vel, gravity, entity, owner, icon), dt);
        }
    }

    private Prediction simulateThrow(ItemStack icon, float partial, double speed, double gravity, boolean offHand) {
        float yaw = mc.player.getYaw(partial);
        float pitch = mc.player.getPitch(partial);

        double dx = -MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE) * MathHelper.cos(pitch * MathHelper.RADIANS_PER_DEGREE);
        double dy = -MathHelper.sin(pitch * MathHelper.RADIANS_PER_DEGREE);
        double dz = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE) * MathHelper.cos(pitch * MathHelper.RADIANS_PER_DEGREE);

        Vec3d velocity = new Vec3d(dx, dy, dz).normalize().multiply(speed);
        Vec3d playerVel = mc.player.getVelocity();
        velocity = velocity.add(playerVel.x, mc.player.isOnGround() ? 0 : playerVel.y, playerVel.z);

        Vec3d lerped = mc.player.getLerpedPos(partial);
        double eyeY = lerped.y + mc.player.getStandingEyeHeight();
        Vec3d start = new Vec3d(lerped.x, eyeY, lerped.z)
                .add(new Vec3d(dx, dy, dz).normalize().multiply(0.2));

        Prediction prediction = simulate(start, velocity, gravity, mc.player, mc.player, icon);
        if (prediction == null) {
            return null;
        }

        Vec3d forward = new Vec3d(dx, dy, dz).normalize();
        Vec3d right = Vec3d.fromPolar(0, yaw + 90);
        if (offHand) {
            right = right.multiply(-1);
        }
        Vec3d handOffset = right.multiply(0.32).add(0, -0.18, 0).add(forward.multiply(0.25));

        List<Vec3d> points = prediction.points;
        int blend = Math.min(10, points.size());
        for (int i = 0; i < blend; i++) {
            float w = (1f - (float) i / blend);
            w *= w;
            points.set(i, points.get(i).add(handOffset.multiply(w)));
        }

        return prediction;
    }

    private Prediction simulate(Vec3d pos, Vec3d vel, double gravity, Entity ignore, Entity owner, ItemStack icon) {
        List<Vec3d> points = new ArrayList<>(128);
        points.add(pos);

        double step = 0.25;
        double drag = Math.pow(0.99, step);
        double stepGravity = gravity * step;

        for (int tick = 0; tick < MAX_STEPS; tick++) {
            for (int sub = 0; sub < 4; sub++) {
                Vec3d next = pos.add(vel.multiply(step));

                HitResult blockHit = mc.world.raycast(new RaycastContext(
                        pos, next,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        ignore));

                Vec3d segmentEnd = blockHit.getType() == HitResult.Type.BLOCK
                        ? ((BlockHitResult) blockHit).getPos()
                        : next;

                Entity hitEntity = null;
                Vec3d entityImpact = null;
                double closest = Double.MAX_VALUE;
                Box sweep = new Box(pos, segmentEnd).expand(0.25);

                for (Entity ent : mc.world.getOtherEntities(ignore, sweep,
                        other -> other instanceof LivingEntity && other.isAlive()
                                && other != owner && !other.isSpectator())) {
                    Vec3d clip = ent.getBoundingBox().expand(0.25).raycast(pos, segmentEnd).orElse(null);
                    if (clip == null) continue;
                    double dist = clip.squaredDistanceTo(pos);
                    if (dist < closest) {
                        closest = dist;
                        hitEntity = ent;
                        entityImpact = clip;
                    }
                }

                if (hitEntity != null) {
                    points.add(entityImpact);
                    return new Prediction(densify(points), entityImpact, tick + 1, hitEntity, icon);
                }

                if (blockHit.getType() == HitResult.Type.BLOCK) {
                    points.add(segmentEnd);
                    return new Prediction(densify(points), segmentEnd, tick + 1, null, icon);
                }

                points.add(next);
                pos = next;
                vel = vel.multiply(drag).subtract(0, stepGravity, 0);

                if (pos.y < mc.world.getBottomY() - 16) {
                    return null;
                }
            }
        }

        return null;
    }

    private static List<Vec3d> densify(List<Vec3d> points) {
        if (points.size() < 2) {
            return points;
        }
        List<Vec3d> out = new ArrayList<>(points.size() * 2);
        out.add(points.getFirst());
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec3d a = points.get(i);
            Vec3d b = points.get(i + 1);
            out.add(a.lerp(b, 0.33));
            out.add(a.lerp(b, 0.66));
            out.add(b);
        }
        return out;
    }

    /** Геометрия ленты: точки относительно камеры, боковые нормали, альфа и полуширина. */
    private static final class RibbonData {
        Vec3d[] rel;
        Vec3d[] side;
        float[] alpha;
        float[] halfW;
        int cr, cg, cb;
    }

    private RibbonData buildRibbon(SmoothedState state, Vec3d cam) {
        List<Vec3d> points = state.points;

        // точки вплотную к камере режутся ближней плоскостью и дают артефакты у старта
        int start = 0;
        while (points.size() - start > 2 && points.get(start).squaredDistanceTo(cam) < 0.16) start++;

        int n = points.size() - start;
        if (n < 2) return null;

        boolean livingHit = state.hitEntity instanceof LivingEntity;
        int baseColor = livingHit ? ColorUtil.getColor(64, 255, 64, 255) : ColorUtil.fade(1);

        RibbonData data = new RibbonData();
        data.cr = channel(baseColor, 16);
        data.cg = channel(baseColor, 8);
        data.cb = channel(baseColor, 0);
        data.rel = new Vec3d[n];
        data.side = new Vec3d[n];
        data.alpha = new float[n];
        data.halfW = new float[n];

        // толщина постоянна в пикселях экрана: ширина в мире растёт с дистанцией
        double tanHalfFov = Math.tan(Math.toRadians(mc.options.getFov().getValue().floatValue()) / 2.0);
        double pxToWorld = 2.0 * tanHalfFov / Math.max(1, mc.getWindow().getFramebufferHeight());
        float widthPx = lineWidth.getValue();

        for (int i = 0; i < n; i++) data.rel[i] = points.get(start + i).subtract(cam);

        // фейд-ин по дистанции вдоль линии (в блоках), а не в долях длины —
        // иначе на длинных траекториях прозрачный кусок у руки растягивается
        double dist = 0;
        Vec3d prevSide = new Vec3d(0, 1, 0);
        for (int i = 0; i < n; i++) {
            Vec3d tangent = data.rel[Math.min(i + 1, n - 1)].subtract(data.rel[Math.max(i - 1, 0)]);
            Vec3d s = tangent.crossProduct(data.rel[i]);
            if (s.lengthSquared() < 1.0e-8) s = prevSide; else s = s.normalize();
            data.side[i] = prevSide = s;

            data.halfW[i] = (float) (0.5 * widthPx * data.rel[i].length() * pxToWorld);

            if (i > 0) dist += points.get(start + i).distanceTo(points.get(start + i - 1));
            data.alpha[i] = (float) MathHelper.clamp((dist - 0.10) / 0.55, 0.0, 1.0);
        }

        return data;
    }

    // солидная лента постоянной экранной толщины: плотный центр (70% ширины)
    // + узкая растушёванная кромка вместо антиалиасинга
    private static void emitRibbon(VertexConsumer buf, Matrix4f m, RibbonData d) {
        int n = d.rel.length;
        for (int i = 0; i < n - 1; i++) {
            int a1 = (int) (d.alpha[i] * 255);
            int a2 = (int) (d.alpha[i + 1] * 255);
            if (a1 <= 0 && a2 <= 0) continue;

            // градиент к концу — линия чуть светлеет у точки попадания
            float t1 = i / (float) (n - 1);
            float t2 = (i + 1) / (float) (n - 1);
            int r1 = lighten(d.cr, t1), g1 = lighten(d.cg, t1), b1 = lighten(d.cb, t1);
            int r2 = lighten(d.cr, t2), g2 = lighten(d.cg, t2), b2 = lighten(d.cb, t2);

            Vec3d c1 = d.rel[i], c2 = d.rel[i + 1];
            Vec3d o1 = d.side[i].multiply(d.halfW[i]), o2 = d.side[i + 1].multiply(d.halfW[i + 1]);
            Vec3d i1 = o1.multiply(0.7), i2 = o2.multiply(0.7);

            // плотный центр
            vtx(buf, m, c1.subtract(i1), r1, g1, b1, a1);
            vtx(buf, m, c2.subtract(i2), r2, g2, b2, a2);
            vtx(buf, m, c2.add(i2), r2, g2, b2, a2);
            vtx(buf, m, c1.add(i1), r1, g1, b1, a1);
            // кромка A
            vtx(buf, m, c1.add(i1), r1, g1, b1, a1);
            vtx(buf, m, c2.add(i2), r2, g2, b2, a2);
            vtx(buf, m, c2.add(o2), r2, g2, b2, 0);
            vtx(buf, m, c1.add(o1), r1, g1, b1, 0);
            // кромка B
            vtx(buf, m, c1.subtract(i1), r1, g1, b1, a1);
            vtx(buf, m, c2.subtract(i2), r2, g2, b2, a2);
            vtx(buf, m, c2.subtract(o2), r2, g2, b2, 0);
            vtx(buf, m, c1.subtract(o1), r1, g1, b1, 0);
        }
    }

    // мягкий аддитивный ореол вокруг линии (опционально)
    private static void emitGlowRibbon(VertexConsumer buf, Matrix4f m, RibbonData d) {
        int n = d.rel.length;
        for (int i = 0; i < n - 1; i++) {
            int a1 = (int) (d.alpha[i] * 0.30f * 255);
            int a2 = (int) (d.alpha[i + 1] * 0.30f * 255);
            if (a1 <= 0 && a2 <= 0) continue;

            Vec3d c1 = d.rel[i], c2 = d.rel[i + 1];
            Vec3d o1 = d.side[i].multiply(d.halfW[i] * 3.0f), o2 = d.side[i + 1].multiply(d.halfW[i + 1] * 3.0f);

            vtx(buf, m, c1, d.cr, d.cg, d.cb, a1);
            vtx(buf, m, c2, d.cr, d.cg, d.cb, a2);
            vtx(buf, m, c2.add(o2), d.cr, d.cg, d.cb, 0);
            vtx(buf, m, c1.add(o1), d.cr, d.cg, d.cb, 0);

            vtx(buf, m, c1, d.cr, d.cg, d.cb, a1);
            vtx(buf, m, c2, d.cr, d.cg, d.cb, a2);
            vtx(buf, m, c2.subtract(o2), d.cr, d.cg, d.cb, 0);
            vtx(buf, m, c1.subtract(o1), d.cr, d.cg, d.cb, 0);
        }
    }

    private static int lighten(int c, float t) {
        return c + (int) ((255 - c) * 0.45f * t);
    }

    private static void vtx(VertexConsumer buf, Matrix4f m, Vec3d v, int r, int g, int b, int a) {
        buf.vertex(m, (float) v.x, (float) v.y, (float) v.z).color(r, g, b, a);
    }

    private void drawLandingMarkerFill(VertexConsumer fill, Matrix4f matrix, Vec3d cam, SmoothedState state) {
        float radius = indicatorSize.getValue();
        if (radius <= 0.01f) {
            return;
        }

        Vec3d impact = state.impact;
        boolean livingHit = state.hitEntity instanceof LivingEntity;
        int baseColor = livingHit ? ColorUtil.getColor(64, 255, 64, 160) : ColorUtil.multAlpha(ColorUtil.fade(1), 0.6f);
        int edgeColor = livingHit ? ColorUtil.getColor(64, 255, 64, 40) : ColorUtil.multAlpha(ColorUtil.fade(1), 0.15f);

        float cx = (float) (impact.x - cam.x);
        float cy = (float) (impact.y - cam.y) + 0.02f;
        float cz = (float) (impact.z - cam.z);

        int cr = channel(baseColor, 16);
        int cg = channel(baseColor, 8);
        int cb = channel(baseColor, 0);
        int ca = alphaOf(baseColor);
        int er = channel(edgeColor, 16);
        int eg = channel(edgeColor, 8);
        int eb = channel(edgeColor, 0);
        int ea = alphaOf(edgeColor);

        for (int i = 0; i < 24; i++) {
            double a0 = Math.PI * 2 * i / 24;
            double a1 = Math.PI * 2 * (i + 1) / 24;

            float x0 = cx + (float) (Math.cos(a0) * radius);
            float z0 = cz + (float) (Math.sin(a0) * radius);
            float x1 = cx + (float) (Math.cos(a1) * radius);
            float z1 = cz + (float) (Math.sin(a1) * radius);

            fill.vertex(matrix, cx, cy, cz).color(cr, cg, cb, ca);
            fill.vertex(matrix, x0, cy, z0).color(er, eg, eb, ea);
            fill.vertex(matrix, x1, cy, z1).color(er, eg, eb, ea);
            fill.vertex(matrix, cx, cy, cz).color(cr, cg, cb, ca);
        }
    }

    private void drawLandingMarkerLines(VertexConsumer lines, Matrix4f matrix, Vec3d cam, SmoothedState state) {
        float radius = indicatorSize.getValue();
        if (radius <= 0.01f) {
            return;
        }

        Vec3d impact = state.impact;
        boolean livingHit = state.hitEntity instanceof LivingEntity;
        int baseColor = livingHit ? ColorUtil.getColor(64, 255, 64, 160) : ColorUtil.multAlpha(ColorUtil.fade(1), 0.6f);
        int crossColor = livingHit ? ColorUtil.getColor(64, 255, 64, 200) : ColorUtil.multAlpha(baseColor, 1f);

        float cx = (float) (impact.x - cam.x);
        float cy = (float) (impact.y - cam.y) + 0.02f;
        float cz = (float) (impact.z - cam.z);

        int xcr = channel(crossColor, 16);
        int xcg = channel(crossColor, 8);
        int xcb = channel(crossColor, 0);
        int xca = alphaOf(crossColor);

        float cross = Math.max(0.15f, radius * 0.35f);
        drawLine(lines, matrix, cx - cross, cy, cz, cx + cross, cy, cz, xcr, xcg, xcb, xca);
        drawLine(lines, matrix, cx, cy, cz - cross, cx, cy, cz + cross, xcr, xcg, xcb, xca);
    }

    private static void drawLine(VertexConsumer buffer, Matrix4f matrix,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 int r, int g, int b, int a) {
        buffer.vertex(matrix, x0, y0, z0).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
    }

    private static int channel(int color, int shift) {
        return (color >> shift) & 0xFF;
    }

    private static int alphaOf(int color) {
        int a = (color >> 24) & 0xFF;
        return a == 0 ? 255 : a;
    }

    private static boolean isCrossbowCharged(ItemStack stack) {
        ChargedProjectilesComponent charged = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
        return charged != null && !charged.isEmpty();
    }

    private record Prediction(List<Vec3d> points, Vec3d impact, int ticks, Entity hitEntity, ItemStack icon) {
    }

    private static final class SmoothedState {
        List<Vec3d> points = new ArrayList<>();
        Vec3d impact = Vec3d.ZERO;
        int ticks;
        Entity hitEntity;
        ItemStack icon = ItemStack.EMPTY;
        boolean active;

        static SmoothedState from(Prediction raw) {
            SmoothedState state = new SmoothedState();
            state.points = new ArrayList<>(raw.points());
            state.impact = raw.impact();
            state.ticks = raw.ticks();
            state.hitEntity = raw.hitEntity();
            state.icon = raw.icon().copy();
            return state;
        }

        void blendToward(Prediction raw, float dt, float speed) {
            float k = 1f - (float) Math.exp(-dt * speed);

            if (impact.squaredDistanceTo(raw.impact()) > 4096) {
                points = new ArrayList<>(raw.points());
                impact = raw.impact();
            } else {
                impact = impact.lerp(raw.impact(), k);
                blendPoints(raw.points(), k);
            }

            ticks = raw.ticks();
            hitEntity = raw.hitEntity();
            icon = raw.icon().copy();
        }

        private void blendPoints(List<Vec3d> target, float k) {
            if (target.isEmpty()) {
                points.clear();
                return;
            }

            int count = Math.max(points.size(), target.size());
            if (count <= 1) {
                points = new ArrayList<>(target);
                return;
            }

            List<Vec3d> from = resample(points, count);
            List<Vec3d> to = resample(target, count);
            points = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                float weight = k * (0.15f + 0.85f * i / (count - 1));
                points.add(from.get(i).lerp(to.get(i), weight));
            }
        }

        private static List<Vec3d> resample(List<Vec3d> src, int count) {
            if (src.isEmpty()) {
                return new ArrayList<>();
            }
            if (src.size() == 1 || count <= 1) {
                List<Vec3d> out = new ArrayList<>(count);
                Vec3d only = src.getFirst();
                for (int i = 0; i < count; i++) {
                    out.add(only);
                }
                return out;
            }

            double total = 0;
            for (int i = 0; i + 1 < src.size(); i++) {
                total += src.get(i).distanceTo(src.get(i + 1));
            }

            List<Vec3d> out = new ArrayList<>(count);
            out.add(src.getFirst());
            for (int i = 1; i < count - 1; i++) {
                out.add(sampleAtDistance(src, total * i / (count - 1)));
            }
            out.add(src.getLast());
            return out;
        }

        private static Vec3d sampleAtDistance(List<Vec3d> src, double dist) {
            double walked = 0;
            for (int i = 0; i + 1 < src.size(); i++) {
                Vec3d a = src.get(i);
                Vec3d b = src.get(i + 1);
                double seg = a.distanceTo(b);
                if (walked + seg >= dist) {
                    double t = seg <= 1.0e-8 ? 0 : (dist - walked) / seg;
                    return a.lerp(b, t);
                }
                walked += seg;
            }
            return src.getLast();
        }
    }

    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "trajectories_line"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "trajectories_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer LINE_LAYER = RenderLayer.of(
            "trajectories_line",
            RenderSetup.builder(LINE_PIPELINE).expectedBufferSize(1 << 14).build()
    );

    private static final RenderLayer FILL_LAYER = RenderLayer.of(
            "trajectories_fill",
            RenderSetup.builder(FILL_PIPELINE).expectedBufferSize(1 << 12).build()
    );

    private static final RenderPipeline RIBBON_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "trajectories_ribbon"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );

    private static final RenderLayer RIBBON_LAYER = RenderLayer.of(
            "trajectories_ribbon",
            RenderSetup.builder(RIBBON_PIPELINE).expectedBufferSize(1 << 15).build()
    );

    private static final RenderPipeline GLOW_RIBBON_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "trajectories_glow_ribbon"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer GLOW_RIBBON_LAYER = RenderLayer.of(
            "trajectories_glow_ribbon",
            RenderSetup.builder(GLOW_RIBBON_PIPELINE).expectedBufferSize(1 << 15).build()
    );
}

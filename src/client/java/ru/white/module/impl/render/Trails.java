package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static java.lang.Math.sin;

@ModuleInfo(
        name = "Trails",
        desc = "Красивый эффект заде игрока при движении",
        category = Category.VISUALS
)
public class Trails extends Module implements ModulePreview {

    public ButtonSetting previewButton = PreviewSettings.button(this);

    public final ModeSetting    colorMode    = new ModeSetting(this,    "Цвет",         "Клиент",   "Случайный");
    public final ModeSetting    rotateMode   = new ModeSetting(this,    "Вращение",     "Движение", "Камера");
    public final SliderSetting  dashLength   = new SliderSetting(this,  "Длина шлейфа", 0.75f, 0.5f, 1.5f, 0.01f);
    public final SliderSetting  moveLerp     = new SliderSetting(this,  "Move Lerp",    0.2f,  0.1f, 0.5f, 0.01f);
    public final BooleanSetting motionSmooth = new BooleanSetting(this, "Сглаживание",  false);
    public final BooleanSetting dashSegs     = new BooleanSetting(this, "Сегменты",     false);
    public final BooleanSetting dashDots     = new BooleanSetting(this, "Точки",        true);
    public final BooleanSetting lighting     = new BooleanSetting(this, "Свечение",     true);

    // шлейф непрерывный — интервалу нечего перезапускать
    private final PreviewSettings previewSettings = PreviewSettings.withoutInterval(this, 4F, 0F);

    /** За сколько секунд болванчик обходит круг в предпоказе. */
    private static final double PREVIEW_ORBIT_MS = 4000.0;


    private static final int   DASH_TEX_COUNT = 21;
    private static final long  DASH_FRAME_TIME_MS = 80L;
    private static final int[] GROUP_SIZES     = {11, 23, 32, 16, 32};

    private static final List<Identifier>       DASH_TEXTURES    = new ArrayList<>();
    private static final List<List<Identifier>> DASH_ANIM_GROUPS = new ArrayList<>();
    private static final Identifier             BLOOM_TEX =
            Identifier.of("client", "textures/particles/glow.png");

    static {
        for (int i = 1; i <= DASH_TEX_COUNT; i++)
            DASH_TEXTURES.add(Identifier.of("client", "textures/dash_cubes/dashcubic" + i + ".png"));
        for (int g = 1; g <= GROUP_SIZES.length; g++) {
            List<Identifier> grp = new ArrayList<>();
            for (int f = 1; f <= GROUP_SIZES[g - 1]; f++)
                grp.add(Identifier.of("client",
                        "textures/dash_cubes/group_dashs/group" + g + "/dashcubic" + f + ".png"));
            DASH_ANIM_GROUPS.add(grp);
        }
    }


    private static final RenderPipeline SPARK_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/trails/spark"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final RenderLayer SPARK_LAYER = RenderLayer.of("trails_spark",
            RenderSetup.builder(SPARK_PIPELINE).expectedBufferSize(1 << 14).build());

    private static final RenderPipeline CUBIC_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/trails/cubic"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final Function<Identifier, RenderLayer> CUBIC_LAYER = Util.memoize(tex ->
            RenderLayer.of("trails_cubic",
                    RenderSetup.builder(CUBIC_PIPELINE)
                            .texture("Sampler0", tex)
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()));


    private final List<DashCubic> cubics        = new ArrayList<>();
    private final List<DashCubic> filteredCache = new ArrayList<>();
    private final Random          rng           = new Random(1234567891L);
    private final BufferAllocator allocator     = new BufferAllocator(1 << 18);


    private final Vector3f    renderRight   = new Vector3f();
    private final Vector3f    renderUp      = new Vector3f();
    private final Quaternionf bloomRotCache = new Quaternionf();

    private float   stateAnim = 0f;
    private float   lightAnim = 0f;
    private double  prevX, prevY, prevZ;
    private boolean prevInit  = false;

    private LivingEntity previewEntity;
    private double  previewPrevX, previewPrevY, previewPrevZ;
    private boolean previewPrevInit = false;

    @Override
    protected void onEnable() {
        cubics.clear();
        filteredCache.clear();
        stateAnim = 0f;
        prevInit  = false;
    }

    @Override
    protected void onDisable() {
        cubics.clear();
        filteredCache.clear();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        cubics.clear();
        filteredCache.clear();
        prevInit = false;
        previewPrevInit = false;
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

    /** Болванчика ведём сами по кругу — стоящий на месте шлейф показать нечем. */
    @Override
    public boolean previewControlsDummy() {
        return true;
    }

    @Override
    public void previewStart(PreviewContext ctx) {
        previewEntity = ctx.dummy();
        previewPrevInit = false;
    }

    @Override
    public void previewTick(PreviewContext ctx) {
        previewEntity = ctx.dummy();
        if (previewEntity == null) return;

        double angle = ctx.elapsedMs() / PREVIEW_ORBIT_MS * Math.PI * 2.0;
        double radius = 1.6;

        Vec3d anchor = ctx.anchor();
        previewEntity.setPosition(
                anchor.x + Math.cos(angle) * radius,
                anchor.y,
                anchor.z + Math.sin(angle) * radius);

        float yaw = (float) Math.toDegrees(angle);
        previewEntity.setYaw(yaw);
        previewEntity.setBodyYaw(yaw);
        previewEntity.setHeadYaw(yaw);
    }

    /** Шлейф непрерывный, отдельный «залп» ему не нужен. */
    @Override
    public void previewSpawn(PreviewContext ctx) {
    }

    @Override
    public void previewStop() {
        previewEntity = null;
        previewPrevInit = false;
        cubics.clear();
        filteredCache.clear();
    }


    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null) return;

        stateAnim += (1f - stateAnim) * 0.08f;
        lightAnim += ((lighting.getValue() ? 1f : 0f) - lightAnim) * 0.08f;

        if (!prevInit) {
            prevX = mc.player.getX(); prevY = mc.player.getY(); prevZ = mc.player.getZ();
            prevInit = true;
            return;
        }


        long nowMs = System.currentTimeMillis();


        int size = cubics.size();
        for (int i = 0; i < size; i++) {
            DashCubic c = cubics.get(i);
            c.cachedTimePC = Math.min(1f, (nowMs - c.startTime) / (float) c.base.rMTime);
            if (c.cachedTimePC >= 1f && c.alphaTarget != 0f) c.alphaTarget = 0f;
            c.alphaAnim += (c.alphaTarget - c.alphaAnim) * 0.08f;
        }

        for (int i = cubics.size() - 1; i >= 0; i--) {
            DashCubic c = cubics.get(i);
            if (c.cachedTimePC >= 1f && c.alphaTarget == 0f && c.alphaAnim < 0.02f)
                cubics.remove(i);
        }

        filteredCache.clear();
        for (int i = 0, n = cubics.size(); i < n; i++) {
            DashCubic c = cubics.get(i);
            if (c.alphaAnim > 0.05f) filteredCache.add(c);
        }

        float cameraPitch = mc.gameRenderer.getCamera().getPitch();
        boolean smooth = motionSmooth.getValue();
        int fsz = filteredCache.size();
        for (int i = 0; i < fsz; i++) {
            DashCubic cur  = filteredCache.get(i);
            DashCubic next = (smooth && i + 1 < fsz) ? filteredCache.get(i + 1) : null;
            if (next != null && next.base.entityId != cur.base.entityId) next = null;
            cur.motionProcess(next, nowMs, cameraPitch);
        }

        double lx = prevX, ly = prevY, lz = prevZ;
        double lr = moveLerp.getValue().doubleValue();
        prevX = MathHelper.lerp(lr, prevX, mc.player.getX());
        prevY = MathHelper.lerp(lr, prevY, mc.player.getY());
        prevZ = MathHelper.lerp(lr, prevZ, mc.player.getZ());
        if (!mc.player.isInvisible()) {
            spawnFor(mc.player, lx, ly, lz);
        }

        tickPreviewTrail(lr);
    }

    /** Тот же шлейф, но за болванчиком, которого редактор водит по кругу перед игроком. */
    private void tickPreviewTrail(double lr) {
        if (previewEntity == null || previewEntity.isRemoved()) {
            previewPrevInit = false;
            return;
        }

        if (!previewPrevInit) {
            previewPrevX = previewEntity.getX();
            previewPrevY = previewEntity.getY();
            previewPrevZ = previewEntity.getZ();
            previewPrevInit = true;
            return;
        }

        double lx = previewPrevX, ly = previewPrevY, lz = previewPrevZ;
        previewPrevX = MathHelper.lerp(lr, previewPrevX, previewEntity.getX());
        previewPrevY = MathHelper.lerp(lr, previewPrevY, previewEntity.getY());
        previewPrevZ = MathHelper.lerp(lr, previewPrevZ, previewEntity.getZ());
        spawnFor(previewEntity, lx, ly, lz);
    }

    private void spawnFor(LivingEntity entity, double lx, double ly, double lz) {
        if (entity == null) return;
        double dx = entity.getX() - lx, dy = entity.getY() - ly, dz = entity.getZ() - lz;

        if (dx * dx + dz * dz < 0.0064) return;
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int count = (int) MathHelper.clamp((float)(speed / 0.4F), 1, 16);
        for (int i = 0; i < count; i++) {
            int rMTime = (int)((550 + rng.nextInt(300)) * dashLength.getValue());
            cubics.add(new DashCubic(new DashBase(entity, 0.04f, (float) i / count, rMTime)));
        }
    }


    @EventHandler
    public void onRender3D(EventRender3D e) {
        if (mc.player == null || stateAnim < 0.05f || filteredCache.isEmpty()) return;

        float  ticks   = e.getTickDelta();
        float  alphaPC = stateAnim;
        float  lightPC = lightAnim;
        int    fsz     = filteredCache.size();
        long   nowMs   = System.currentTimeMillis();

        MatrixStack matrix = e.getMatrixStack();
        Vec3d       cam    = mc.getEntityRenderDispatcher().camera.getCameraPos();
        double      camX   = cam.x, camY = cam.y, camZ = cam.z;

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);


        float       fCamYaw   = mc.gameRenderer.getCamera().getYaw();
        float       fCamPitch = mc.gameRenderer.getCamera().getPitch();
        Quaternionf camRot    = mc.gameRenderer.getCamera().getRotation();
        camRot.transform(renderRight.set(1, 0, 0));
        camRot.transform(renderUp.set(0, 1, 0));
        bloomRotCache.identity()
                .rotateY((float) Math.toRadians(-fCamYaw))
                .rotateX((float) Math.toRadians(fCamPitch));


        for (int i = 0; i < fsz; i++) {
            DashCubic  cubic = filteredCache.get(i);
            Identifier tex   = cubic.dashTexture.getCurrent(nowMs);
            if (tex == null) continue;
            float aPC = cubic.alphaAnim * alphaPC;
            float ext = 128f * 0.033f * aPC * 0.3f;
            int   col = cubic.color;
            matrix.push();
            matrix.translate(cubic.getRenderX(ticks) - camX,
                             cubic.getRenderY(ticks) - camY,
                             cubic.getRenderZ(ticks) - camZ);
            applyBodyRotation(matrix, cubic.rotate, fCamYaw, fCamPitch);
            matrix.scale(-0.1f, -0.1f, 0.1f);
            drawTexQuad(immediate.getBuffer(CUBIC_LAYER.apply(tex)),
                        matrix.peek().getPositionMatrix(),
                        -ext, -ext, ext, ext,
                        ColorUtil.multDark(ColorUtil.overCol(col, -1, 0.4f), aPC));
            matrix.pop();
        }


        VertexConsumer bloomBuf = immediate.getBuffer(CUBIC_LAYER.apply(BLOOM_TEX));
        for (int i = 0; i < fsz; i++) {
            DashCubic cubic    = filteredCache.get(i);
            float     aPC      = cubic.alphaAnim * alphaPC;
            float     base     = 128f * 0.033f * aPC * 0.3f;

            float     timePcOf = 1f - cubic.cachedTimePC;
            int       col      = cubic.color;
            int       baseCol  = ColorUtil.overCol(col, -1, 0.18f);
            matrix.push();
            matrix.translate(cubic.getRenderX(ticks) - camX,
                             cubic.getRenderY(ticks) - camY,
                             cubic.getRenderZ(ticks) - camZ);
            matrix.multiply(bloomRotCache);
            matrix.scale(-0.1f, -0.1f, 0.1f);
            Matrix4f mat = matrix.peek().getPositionMatrix();
            drawTexQuad(bloomBuf, mat, -base*6f,   -base*6f,   base*6f,   base*6f,   ColorUtil.swapAlpha(baseCol, 15f  * aPC));
            drawTexQuad(bloomBuf, mat, -base*2.2f, -base*2.2f, base*2.2f, base*2.2f, ColorUtil.swapAlpha(baseCol, 70f  * aPC));
            drawTexQuad(bloomBuf, mat, -base*1.1f, -base*1.1f, base*1.1f, base*1.1f, ColorUtil.swapAlpha(baseCol, 140f * aPC));
            if (lightPC > 0f) {
                float aMul   = aPC * lightPC;
                float bigExt = base * (1f + 8f * timePcOf * aMul);
                drawTexQuad(bloomBuf, mat, -bigExt, -bigExt, bigExt, bigExt,
                        ColorUtil.swapAlpha(ColorUtil.multDark(baseCol, aMul / 4f), 95f * aMul));
            }
            matrix.pop();
        }

        immediate.draw();
    }


    private void applyBodyRotation(MatrixStack m, float[] rotate, float camYaw, float camPitch) {
        if (rotateMode.is("Движение")) {
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-rotate[0]));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotate[1]));
        } else {
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camYaw));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camPitch));
        }
    }

    private static void drawSparkQuad(VertexConsumer buf, Matrix4f mat,
                                       Vector3f right, Vector3f up, float s,
                                       float cx, float cy, float cz, int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF,
            b = color & 0xFF,          a = (color >> 24) & 0xFF;
        float rx = right.x * s, ry = right.y * s, rz = right.z * s;
        float ux = up.x    * s, uy = up.y    * s, uz = up.z    * s;
        buf.vertex(mat, cx - rx + ux, cy - ry + uy, cz - rz + uz).color(r, g, b, a);
        buf.vertex(mat, cx + rx + ux, cy + ry + uy, cz + rz + uz).color(r, g, b, a);
        buf.vertex(mat, cx + rx - ux, cy + ry - uy, cz + rz - uz).color(r, g, b, a);
        buf.vertex(mat, cx - rx - ux, cy - ry - uy, cz - rz - uz).color(r, g, b, a);
    }

    private static void drawTexQuad(VertexConsumer buf, Matrix4f mat,
                                     float x1, float y1, float x2, float y2, int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF,
            b = color & 0xFF,          a = (color >> 24) & 0xFF;
        buf.vertex(mat, x1, y1, 0).color(r, g, b, a).texture(0, 1);
        buf.vertex(mat, x2, y1, 0).color(r, g, b, a).texture(1, 1);
        buf.vertex(mat, x2, y2, 0).color(r, g, b, a).texture(1, 0);
        buf.vertex(mat, x1, y2, 0).color(r, g, b, a).texture(0, 0);
    }

    private int getDashColor() {
        if (colorMode.is("Случайный"))
            return Color.getHSBColor(rng.nextInt(255) / 255f, 1f, 1f).getRGB() | 0xFF000000;
        return ColorUtil.fade(0);
    }

    private static float computeMotionYaw(double mx, double mz) {
        int y = (int)(Math.toDegrees(Math.atan2(mz, mx)) - 90);
        return y < 0 ? y + 360 : y;
    }

    private class DashCubic {
        float  alphaAnim    = 0f;
        float  alphaTarget  = 1f;
        float  cachedTimePC = 0f;
        final  long        startTime   = System.currentTimeMillis();
        final  DashBase    base;
        final  int         color;
        final  float[]     rotate      = new float[2];
        final  List<DashSpark> sparks  = new ArrayList<>(4);
        final  DashTexture dashTexture;

        DashCubic(DashBase base) {
            this.base        = base;
            this.color       = getDashColor();
            this.dashTexture = new DashTexture();
            recomputeRotate(mc.gameRenderer.getCamera().getPitch());
        }

        void recomputeRotate(float cameraPitch) {
            double spd = Math.sqrt(base.motionX * base.motionX + base.motionZ * base.motionZ);
            if (spd < 5e-4) {
                rotate[0] = (float)(360 * Math.random());
                rotate[1] = cameraPitch;
            } else {
                float motYaw = computeMotionYaw(base.motionX, base.motionZ);
                rotate[0]    = motYaw - 60f - (base.prevYaw - base.yaw) * 3f;
                float diff   = Math.abs(motYaw + 26.3f - base.yaw) % 360f;
                if (diff > 180f) diff = 360f - diff;
                rotate[1]    = (diff < 10f || diff > 160f) ? -90f : cameraPitch;
            }
        }

        double getRenderX(float pt) { return base.prevPosX + (base.posX - base.prevPosX) * pt; }
        double getRenderY(float pt) { return base.prevPosY + (base.posY - base.prevPosY) * pt; }
        double getRenderZ(float pt) { return base.prevPosZ + (base.posZ - base.prevPosZ) * pt; }

        void motionProcess(DashCubic next, long nowMs, float cameraPitch) {
            base.prevPosX = base.posX;
            base.prevPosY = base.posY;
            base.prevPosZ = base.posZ;

            base.motionX = (next != null ? next.base.motionX : base.motionX) / 1.05;
            base.posX   += 5.0 * base.motionX;
            base.motionY = (next != null ? next.base.motionY : base.motionY) / 1.05;
            base.posY   += 5.0 * base.motionY / (base.motionY < 0 ? 1.0 : 3.5);
            base.motionZ = (next != null ? next.base.motionZ : base.motionZ) / 1.05;
            base.posZ   += 5.0 * base.motionZ;
            recomputeRotate(cameraPitch);

            sparks.forEach(s -> s.update(nowMs));

            for (int j = sparks.size() - 1; j >= 0; j--) {
                if (sparks.get(j).dead) sparks.remove(j);
            }
        }
    }

    private static class DashBase {
        final int    entityId;
        double motionX, motionY, motionZ;
        double posX,    posY,    posZ;
        double prevPosX, prevPosY, prevPosZ;
        final int    rMTime;
        final float  yaw, prevYaw;

        DashBase(LivingEntity entity, float speed, float offsetPC, int rmTime) {
            entityId = entity.getId();
            rMTime   = rmTime;
            yaw      = entity.getYaw();
            prevYaw  = entity.lastYaw;

            double mx = -(entity.lastRenderX - entity.getX());
            double my = -(entity.lastRenderY - entity.getY());
            double mz = -(entity.lastRenderZ - entity.getZ());
            motionX = mx; motionY = my; motionZ = mz;

            float h       = entity.getHeight();
            float swimDiv = entity.isSubmergedInWater() ? 2.4f : 1f;
            posX = entity.lastRenderX - mx * offsetPC + (-0.0875 + 0.175 * Math.random());
            posY = entity.lastRenderY - my * offsetPC
                    + (h / swimDiv / 3.0 + h / swimDiv / 4.0 * Math.random() * 2.7);
            posZ = entity.lastRenderZ - mz * offsetPC + (-0.0875 + 0.175 * Math.random());
            prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
            motionX *= speed; motionY *= speed; motionZ *= speed;
        }
    }

    private class DashTexture {
        private final List<Identifier> textures;
        private final boolean          animated;
        private final long             spawnTime = System.currentTimeMillis();
        private final long             animDur;

        DashTexture() {
            boolean doAnim = rng.nextInt(100) > 40 && !DASH_ANIM_GROUPS.isEmpty();
            animated = doAnim;
            if (doAnim) {
                textures = DASH_ANIM_GROUPS.get(rng.nextInt(DASH_ANIM_GROUPS.size()));
                animDur  = (long)((550 + rng.nextInt(300)) * dashLength.getValue());
            } else {
                textures = DASH_TEXTURES.isEmpty()
                        ? List.of()
                        : List.of(DASH_TEXTURES.get(rng.nextInt(DASH_TEXTURES.size())));
                animDur  = 0;
            }
        }


        Identifier getCurrent(long nowMs) {
            if (textures.isEmpty()) return null;
            if (animated && textures.size() > 1 && animDur > 0) {
                int idx = (int)(((nowMs - spawnTime) / DASH_FRAME_TIME_MS) % textures.size());
                return textures.get(idx);
            }
            return textures.get(0);
        }
    }

    private static class DashSpark {
        double  posX, posY, posZ;
        double  prevX, prevY, prevZ;
        boolean dead         = false;
        float   cachedAlphaPC = 1f;
        final   double sinYaw, cosYaw, cosPitchOffset;
        final   double speed     = Math.random() / 50.0;
        final   long   startTime = System.currentTimeMillis();

        DashSpark() {
            double yaw   = Math.random() * Math.PI * 2;
            double pitch = Math.toRadians(-90 + Math.random() * 180 - 90);
            sinYaw        = sin(yaw);
            cosYaw        = Math.cos(yaw);
            cosPitchOffset = Math.cos(pitch);
        }


        void update(long nowMs) {
            float timePC  = MathHelper.clamp((nowMs - startTime) / 1000f, 0f, 1f);
            dead          = timePC >= 1f;
            cachedAlphaPC = 1f - timePC;
            prevX = posX; prevY = posY; prevZ = posZ;
            posX += sinYaw        * speed;
            posY += cosPitchOffset * speed;
            posZ += cosYaw        * speed;
        }

        double getRenderX(float pt) { return prevX + (posX - prevX) * pt; }
        double getRenderY(float pt) { return prevY + (posY - prevY) * pt; }
        double getRenderZ(float pt) { return prevZ + (posZ - prevZ) * pt; }
    }
}

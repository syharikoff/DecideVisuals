package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.manager.event_impl.*;
import ru.white.manager.event_impl.*;
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
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.taskript.StopWatch;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@ModuleInfo(
        name = "Particles",
        desc = "Кастомные частицы вокруг игрока",
        category = Category.VISUALS
)
public class Particles extends Module implements ModulePreview {

    public ButtonSetting previewButton = PreviewSettings.button(this);

    public BooleanSetting onAttack = new BooleanSetting(this,"При атаке",true);
    public SliderSetting onAtSizs = new SliderSetting(this,"Кол в атаке",20,5,50,1).setVisible(() -> onAttack.getValue());
    public BooleanSetting totem = new BooleanSetting(this,"При тотеме",true);
    public SliderSetting onTtSizs = new SliderSetting(this,"Кол при тотеме",75,20,150,1).setVisible(() -> totem.getValue());
    public BooleanSetting perka = new BooleanSetting(this,"При кидании",true);
    public SliderSetting onPerkSizs = new SliderSetting(this,"Кол при кидании",3,1,8,1).setVisible(() -> perka.getValue());
    public BooleanSetting worlds = new BooleanSetting(this,"В мире",true);
    public SliderSetting onworldsSizs = new SliderSetting(this,"Кол в мире",10,2,60,1).setVisible(() -> worlds.getValue());
    public BooleanSetting move = new BooleanSetting(this,"При движении",true);
    private final ModeSetting particleMode = new ModeSetting(this, "Тип частиц", "Client","Circle","Bloom","Star","Heart","Snow","Dollar","Pumpkin","Triangle","Sakura","Genshin","Rhombus");
    private final SliderSetting size = new SliderSetting(this, "Размер", 0.5F, 0.0F, 1F, 0.1F);
    public BooleanSetting glows = new BooleanSetting(this,"Свечения",true);
    private final SliderSetting glowsiz = new SliderSetting(this, "Сила свечения", 0.5F, 0.0F, 1F, 0.1F).setVisible(() -> glows.getValue());
    public BooleanSetting rotations = new BooleanSetting(this,"Ротация",true);

    private final PreviewSettings previewSettings = PreviewSettings.of(this, 4F, 0F, 2F);

    public final List<Particle> targetParticles = new ArrayList<>();
    public final List<Particle> totemParticles = new ArrayList<>();
    public final List<Particle> frameParticles = new ArrayList<>();
    public final List<Particle> worldParticles = new ArrayList<>();

    private long totemSpawnStartTime = 0;
    private Entity totemTargetEntity = null;
    private int totemParticlesLeftToSpawn = 0;
    private int totalTotemParticlesToSpawn = 0;

    @Override
    public void toggle() {
        super.toggle();
        clear();
    }

    @EventHandler
    public void onEvent(WorldLoadEvent event) {
        clear();
    }

    private void spawnParticle(List<Particle> particles, Vec3d position, Vec3d velocity, boolean isWorld) {
        float particleSize = 0.05F + (this.size.getValue() * 0.2F);
        int color = ColorUtil.fade(particles.size() * 100);

        ParticleType type = switch (this.particleMode.getValue()) {
            case "Heart" -> ParticleType.HEART;
            case "Star" -> ParticleType.STAR;
            case "Snow" -> ParticleType.SNOOW;
            case "Bloom" -> ParticleType.BLOOM;
            case "Circle" -> ParticleType.CIRCLE;
            case "Client" -> ParticleType.STAR_NEW;
            case "Dollar" -> ParticleType.DOLLAR;
            case "Pumpkin" -> ParticleType.PUMPKIN;
            case "Triangle" -> ParticleType.TRIANGLE;
            case "Sakura" -> ParticleType.SAKURA;
            case "Genshin" -> ParticleType.GEMINI;
            case "Rhombus" -> ParticleType.SIMS;
            default ->  ParticleType.BLOOM;
        };

        particles.add(new Particle(type,
                position.add(0, particleSize, 0),
                velocity,
                particles.size(),
                (int) MathUtil.step(MathUtil.randomValue(0, 360), 15),
                color,
                particleSize,
                1.1F,
                isWorld,
                false)
        );
    }

    public void spawnParticleTotem(List<Particle> particles, Vec3d position, Vec3d velocity) {
        float particleSize = 0.05F + (this.size.getValue() * 0.2F);

        ParticleType type = switch (this.particleMode.getValue()) {
            case "Heart" -> ParticleType.HEART;
            case "Star" -> ParticleType.STAR;
            case "Snow" -> ParticleType.SNOOW;
            case "Circle" -> ParticleType.CIRCLE;
            case "Bloom" -> ParticleType.BLOOM;
            case "Client" -> ParticleType.STAR_NEW;
            case "Dollar" -> ParticleType.DOLLAR;
            case "Pumpkin" -> ParticleType.PUMPKIN;
            case "Triangle" -> ParticleType.TRIANGLE;
            case "Sakura" -> ParticleType.SAKURA;
            case "Genshin" -> ParticleType.GEMINI;
            case "Rhombus" -> ParticleType.SIMS;
            default ->  ParticleType.BLOOM;
        };

        int[] colors = new int[]{
                ColorUtil.getColor(221, 218, 127),
                ColorUtil.getColor(127, 221, 144),
                ColorUtil.getColor(255, 215, 0),
                ColorUtil.getColor(50, 205, 50)
        };
        int col = colors[MathUtil.randomInt(0, colors.length - 1)];
        particles.add(new Particle(type,
                position.add(0, particleSize, 0),
                velocity,
                particles.size(),
                (int) MathUtil.step(MathUtil.randomValue(0, 360), 15),
                col,
                particleSize,
                1.1F,
                false,
                true)
        );
    }

    @EventHandler
    public void onEvent(AttackEvent event) {
        if (onAttack.getValue()) spawnAttackBurst(event.getTarget());
    }

    public void spawnAttackBurst(Entity target) {
        if (target == null) return;

        float motion = 1.1F;
        for (int i = 0; i < onAtSizs.getValue(); i++) {
            spawnParticle(targetParticles,
                    new Vec3d(target.getX() + MathUtil.randomValue(-0.035F, 0.035F),
                            target.getY() + MathUtil.randomValue(0.1F, target.getHeight()),
                            target.getZ() + MathUtil.randomValue(-0.035F, 0.035F)),
                    new Vec3d(MathUtil.randomValue(-motion + 0.2F, motion - 0.2F),
                            MathUtil.randomValue(-motion, motion / 2 ),
                            MathUtil.randomValue(-motion + 0.2F, motion - 0.2F)),
                    false);
        }
    }

    public void spawnTotemBurst(Entity target, int count) {
        if (target == null) return;

        for (int i = 0; i < count; i++) {
            double angle = MathUtil.randomValue(0, Math.PI * 2);
            double speedXZ = MathUtil.randomValue(2.5, 4.0) * 0.1F;

            spawnParticleTotem(totemParticles,
                    new Vec3d(target.getX() + MathUtil.randomValue(-0.2, 0.2),
                            target.getY() + MathUtil.randomValue(0.1F, target.getHeight()),
                            target.getZ() + MathUtil.randomValue(-0.2, 0.2)),
                    new Vec3d(Math.cos(angle) * speedXZ,
                            MathUtil.randomValue(0.0, target.getHeight() / 4),
                            Math.sin(angle) * speedXZ));
        }
    }

    public void spawnThrowBurst(Entity target) {
        if (target == null) return;

        Vec3d pos = target.getEntityPos().add(0, target.getHeight() * 0.6F, 0);
        for (int i = 0; i < onPerkSizs.getValue() * 8; i++) {
            spawnParticle(frameParticles,
                    pos.add(MathUtil.randomValue(-0.1, 0.1),
                            MathUtil.randomValue(-0.1, 0.1),
                            MathUtil.randomValue(-0.1, 0.1)),
                    new Vec3d(MathUtil.randomValue(-0.15, 0.15),
                            MathUtil.randomValue(-0.15, 0.15),
                            MathUtil.randomValue(-0.15, 0.15)),
                    true);
        }
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

    /** Каждый цикл показывает следующий включённый повод для частиц. */
    @Override
    public void previewSpawn(PreviewContext ctx) {
        Entity target = ctx.dummy();
        if (target == null) return;

        List<Runnable> demos = new ArrayList<>(3);
        if (onAttack.getValue()) demos.add(() -> spawnAttackBurst(target));
        if (totem.getValue()) demos.add(() -> spawnTotemBurst(target, onTtSizs.getValue().intValue()));
        if (perka.getValue()) demos.add(() -> spawnThrowBurst(target));
        if (demos.isEmpty()) demos.add(() -> spawnAttackBurst(target));

        demos.get(Math.floorMod(ctx.phase(), demos.size())).run();
    }

    @Override
    public void previewStop() {
        clear();
    }

    @EventHandler
    public void onPacket(EventPacket e) {
        if (!(e.getPacket() instanceof EntityStatusS2CPacket packet)) return;

        if (packet.getStatus() == 35) {
            Entity entity = packet.getEntity(mc.world);
            if (entity == null) return;

            if (totem.getValue()) {
                this.totemTargetEntity = entity;
                this.totalTotemParticlesToSpawn = (int) onTtSizs.getValue().intValue();
                this.totemParticlesLeftToSpawn = this.totalTotemParticlesToSpawn;
                this.totemSpawnStartTime = System.currentTimeMillis();
            }
        }
    }

    @EventHandler
    public void onEvent(EventTick e) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;

        if (perka.getValue()) {
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof EnderPearlEntity || entity instanceof ArrowEntity || entity instanceof TridentEntity)) {
                    continue;
                }
                if (entity instanceof TridentEntity trident && trident.isOnGround()) {
                    continue;
                }

                boolean isMoving = entity.lastRenderX != entity.getX() || entity.lastRenderY != entity.getY() || entity.lastRenderZ != entity.getZ();
                if (!isMoving) continue;

                Vec3d pos = entity.getEntityPos();

                for (int i = 0; i < onPerkSizs.getValue(); i++) {
                    spawnParticle(
                            frameParticles,
                            new Vec3d(
                                    pos.x + MathHelper.nextDouble(Random.create(), -0.1, 0.1),
                                    pos.y + MathHelper.nextDouble(Random.create(), -0.1, 0.1),
                                    pos.z + MathHelper.nextDouble(Random.create(), -0.1, 0.1)
                            ),
                            new Vec3d(
                                    MathHelper.nextDouble(Random.create(), -0.15, 0.15),
                                    MathHelper.nextDouble(Random.create(), -0.15, 0.15 ),
                                    MathHelper.nextDouble(Random.create(), -0.15, 0.15)
                            ),
                            true
                    );
                }
            }
        }
    }

    @EventHandler
    public void onEvent(MotionEvent e) {
        if(move.getValue() && hasPlayerMoved()) {
            spawnParticle(frameParticles,
                    new Vec3d(mc.player.getX() + MathUtil.randomValue(-0.1, 0.1),
                            mc.player.getY() + MathUtil.randomValue(0, mc.player.getHeight()),
                            mc.player.getZ() + MathUtil.randomValue(-0.1, 0.1)),
                    new Vec3d(MathUtil.randomValue(-0.3, 0.3),
                            MathUtil.randomValue(-0.03, 0.3),
                            MathUtil.randomValue(-0.3, 0.3)),
                    true);
        }

        if (worlds.getValue()) {
            if (mc.world == null || mc.player == null) return;

            int r = 24;
            for (int i = 0; i < onworldsSizs.getValue(); i++) {
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

                spawnParticle(
                        worldParticles,
                        spawnPos,
                        new Vec3d(
                                mc.player.getVelocity().x + MathUtil.randomValue(-0.6F, 0.6F),
                                MathUtil.randomValue(-0.01, 0.44),
                                mc.player.getVelocity().z + MathUtil.randomValue(-0.6F, 0.6F)
                        ),
                        true
                );
            }
        }


        if (totemParticlesLeftToSpawn > 0 && totemTargetEntity != null) {
            if (totemTargetEntity.isAlive() || mc.world.getEntityById(totemTargetEntity.getId()) != null) {

                long elapsed = System.currentTimeMillis() - totemSpawnStartTime;
                long totalDuration = 1500;

                if (elapsed < totalDuration) {
                    int expectedSpawnedSoFar = (int) ((double) elapsed / totalDuration * totalTotemParticlesToSpawn);
                    int currentSpawned = totalTotemParticlesToSpawn - totemParticlesLeftToSpawn;
                    int toSpawnThisTick = expectedSpawnedSoFar - currentSpawned;

                    if (toSpawnThisTick > totemParticlesLeftToSpawn) {
                        toSpawnThisTick = totemParticlesLeftToSpawn;
                    }

                    for (int i = 0; i < toSpawnThisTick; i++) {
                        double angle = MathUtil.randomValue(0, Math.PI * 2);
                        double speedXZ = MathUtil.randomValue(2.5, 4.0) * 0.1F;
                        double motionX = Math.cos(angle) * speedXZ;
                        double motionZ = Math.sin(angle) * speedXZ;
                        double motionY = MathUtil.randomValue(0.0, totemTargetEntity.getHeight() / 4);

                        spawnParticleTotem(totemParticles,
                                new Vec3d(totemTargetEntity.getX() + MathUtil.randomValue(-0.2, 0.2),
                                        totemTargetEntity.getY() + MathUtil.randomValue(0.1F, totemTargetEntity.getHeight()),
                                        totemTargetEntity.getZ() + MathUtil.randomValue(-0.2, 0.2)),
                                new Vec3d(motionX, motionY, motionZ));

                        totemParticlesLeftToSpawn--;
                    }
                } else {
                    for (int i = 0; i < totemParticlesLeftToSpawn; i++) {
                        double angle = MathUtil.randomValue(0, Math.PI * 2);
                        double speedXZ = MathUtil.randomValue(2.5, 4.0) * 0.1F;
                        double motionX = Math.cos(angle) * speedXZ;
                        double motionZ = Math.sin(angle) * speedXZ;
                        double motionY = MathUtil.randomValue(0.0, totemTargetEntity.getHeight() / 4);

                        spawnParticleTotem(totemParticles,
                                new Vec3d(totemTargetEntity.getX() + MathUtil.randomValue(-0.2, 0.2),
                                        totemTargetEntity.getY() + MathUtil.randomValue(0.1F, totemTargetEntity.getHeight()),
                                        totemTargetEntity.getZ() + MathUtil.randomValue(-0.2, 0.2)),
                                new Vec3d(motionX, motionY, motionZ));
                    }
                    totemParticlesLeftToSpawn = 0;
                    totemTargetEntity = null;
                }
            } else {
                totemParticlesLeftToSpawn = 0;
                totemTargetEntity = null;
            }
        }

        removeExpiredParticles(frameParticles, 3000);
        removeExpiredParticles(totemParticles, 3500);
        removeExpiredParticles(worldParticles, 3500);
        removeExpiredParticles(targetParticles, 6000);
    }

    private boolean hasPlayerMoved() {
        return mc.player.lastRenderX != mc.player.getX()
                || mc.player.lastRenderY != mc.player.getY()
                || mc.player.lastRenderZ != mc.player.getZ();
    }

    private void removeExpiredParticles(List<Particle> particles, long lifespan) {
        particles.removeIf(particle -> particle.time().finished(lifespan));
    }

    private long lastUpdateTime = System.nanoTime();
    private final BufferAllocator boxAllocator = new BufferAllocator(1 << 12);

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        boxAllocator.clear();
    }

    @EventHandler
    public void onEvent(EventRender3D event) {
        MatrixStack matrix = event.getMatrixStack();
        long now = System.nanoTime();
        double deltaTime = (now - lastUpdateTime) / 1_000_000_000.0;
        lastUpdateTime = now;

        matrix.push();
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(boxAllocator);

        if(glows.getValue()) {
            renderParticlesGlow(matrix, immediate, targetParticles, 2000, 2500, deltaTime);
            renderParticlesGlow(matrix, immediate, totemParticles, 1000, 1500, deltaTime);
            renderParticlesGlow(matrix, immediate, worldParticles, 1000, 1500, deltaTime);
            renderParticlesGlow(matrix, immediate, frameParticles, 750, 1000, deltaTime);
        }
        renderParticles(matrix, immediate, targetParticles, 2000, 2500, deltaTime);
        renderParticles(matrix, immediate, totemParticles, 1000, 1500, deltaTime);
        renderParticles(matrix, immediate, worldParticles, 1000, 1500, deltaTime);
        renderParticles(matrix, immediate, frameParticles, 750, 1000, deltaTime);

        immediate.draw();
        matrix.pop();
    }

    private static final Map<ParticleType, RenderLayer> RENDER_LAYER_CACHE = new ConcurrentHashMap<>();
    private static final Identifier BLOOM_TEXTURE = Identifier.of("client", "textures/particles/glow.png");
    private static RenderLayer cachedBloomLayer = null;
    private static final RenderPipeline TEXTURED_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/world/textured_quads"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private void renderParticles(MatrixStack matrix, VertexConsumerProvider.Immediate immediate, List<Particle> particles, long fadeInTime, long fadeOutTime, double deltaTime) {
        if (particles.isEmpty()) return;

        matrix.push();
        for (Particle particle : particles) {
            particle.update(deltaTime);

            boolean notFinishedFadeIn = !particle.time().finished(fadeInTime);
            boolean finishedFadeOut = particle.time().finished(fadeOutTime);

            if (notFinishedFadeIn) {
                particle.animation().run(1, 0.5F, Easings.LINEAR, true);
            } else if (finishedFadeOut) {
                particle.animation().run(0, 2, Easings.LINEAR, true);
            }

            if (particle.animation.isAlive()) {
                particle.animation.update();
            }

            float animValue = particle.animation.get();
            int alpha = (int) (animValue * 255);
            if (alpha <= 0) continue;

            int color = ColorUtil.replAlpha(particle.color(), alpha);
            Vec3d v = particle.position();
            renderParticle(matrix, immediate, particle, (float) v.x, (float) v.y, (float) v.z, particle.size * animValue, color, alpha);
        }
        matrix.pop();
    }

    private void renderParticle(MatrixStack matrix, VertexConsumerProvider.Immediate immediate,
                                Particle particle, float x, float y, float z,
                                float pos, int color, int alpha) {
        matrix.push();
        setupOrientationMatrix(matrix, x, y, z);
        matrix.multiply(mc.gameRenderer.getCamera().getRotation());
        if(rotations.getValue()) matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(particle.rotate()));

        MatrixStack.Entry entry = matrix.peek();
        Matrix4f matrix4f = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();

        VertexConsumer buffer = immediate.getBuffer(ROMB_ESP.apply(particle.type().texture()));

        drawTexturedQuad(buffer, matrix4f, normalMatrix, -pos, -pos, pos * 2, pos * 2, color, alpha);

        if (particle.type == ParticleType.BLOOM && glows.getValue()) {
            drawTexturedQuad(buffer, matrix4f, normalMatrix, -pos / 2, -pos / 2, pos, pos, color, alpha);
        }

        if (glows.getValue()) {
            float bloomPos = pos * 3;
            int bloomAlpha = (int) (alpha * glowsiz.getValue() * 0.4F);
            if (bloomAlpha > 0) {
                if (cachedBloomLayer == null) {
                    cachedBloomLayer = ROMB_ESP.apply(BLOOM_TEXTURE);
                }
                VertexConsumer bloomBuffer = immediate.getBuffer(cachedBloomLayer);
                drawTexturedQuad(bloomBuffer, matrix4f, normalMatrix, -bloomPos, -bloomPos, bloomPos * 2, bloomPos * 2, color, bloomAlpha);
            }
        }
        matrix.pop();
    }

    private void renderParticlesGlow(MatrixStack matrix, VertexConsumerProvider.Immediate immediate, List<Particle> particles, long fadeInTime, long fadeOutTime, double deltaTime) {
        if (particles.isEmpty()) return;

        matrix.push();
        for (Particle particle : particles) {


            float animValue = particle.animation.get();
            int alpha = (int) (animValue * 255 * glowsiz.getValue());
            if (alpha <= 0) continue;

            int color = ColorUtil.replAlpha(particle.color(), alpha);
            Vec3d v = particle.position();
            renderParticle2(matrix, immediate, particle, (float) v.x, (float) v.y, (float) v.z, particle.size  * animValue, color, alpha);
        }
        matrix.pop();
    }

    private void renderParticle2(MatrixStack matrix, VertexConsumerProvider.Immediate immediate,
                                 Particle particle, float x, float y, float z,
                                 float pos, int color, int alpha) {
        matrix.push();
        setupOrientationMatrix(matrix, x, y, z);
        matrix.multiply(mc.gameRenderer.getCamera().getRotation());
        if(rotations.getValue()) matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(particle.rotate()));

        MatrixStack.Entry entry = matrix.peek();
        Matrix4f matrix4f = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();

        VertexConsumer buffer = immediate.getBuffer(ROMB_ESP.apply(ParticleType.BLOOM.texture));

        drawTexturedQuad(buffer, matrix4f, normalMatrix, -pos * 3, -pos * 3, pos * 6, pos * 6, color, alpha);


        matrix.pop();
    }

    public static final Function<Identifier, RenderLayer> ROMB_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(1536)
                        .build();
                return RenderLayer.of("wtex", setup);
            });

    private static final Vector3f REUSABLE_NORMAL = new Vector3f(0, 0, 1);

    private void drawTexturedQuad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix, float x, float y, float width, float height, int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        REUSABLE_NORMAL.set(0, 0, 1);
        normalMatrix.transform(REUSABLE_NORMAL);
        REUSABLE_NORMAL.normalize();

        float x1 = x;
        float y1 = y;
        float x2 = x + width;
        float y2 = y + height;

        buffer.vertex(matrix, x1, y1, 0.0f).color(r, g, b, alpha).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
        buffer.vertex(matrix, x2, y1, 0.0f).color(r, g, b, alpha).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
        buffer.vertex(matrix, x2, y2, 0.0f).color(r, g, b, alpha).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
        buffer.vertex(matrix, x1, y2, 0.0f).color(r, g, b, alpha).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
    }

    public static void setupOrientationMatrix(MatrixStack matrix, float x, float y, float z) {
        setupOrientationMatrix(matrix, (double) x, y, z);
    }

    public static void setupOrientationMatrix(MatrixStack matrix, double x, double y, double z) {
        final Vec3d renderPos = mc.getEntityRenderDispatcher().camera.getCameraPos();
        matrix.translate(x - renderPos.x, y - renderPos.y, z - renderPos.z);
    }

    private void clear() {
        targetParticles.clear();
        frameParticles.clear();
        totemParticles.clear();
        worldParticles.clear();
        totemParticlesLeftToSpawn = 0;
        totalTotemParticlesToSpawn = 0;
        totemTargetEntity = null;
        totemSpawnStartTime = 0;
    }

    @Getter
    @Accessors(fluent = true)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public enum ParticleType {
        HEART("heart",true),
        STAR("star",true),
        BLOOM("glow",true),
        CIRCLE("circle",true),
        STAR_NEW("client",true),
        SNOOW("snowflake",true),
        DOLLAR("dollar",true),
        PUMPKIN("pumpkin",true),
        TRIANGLE("triangle",true),
        SAKURA("sakura",true),
        GEMINI("genshin",true),
        SIMS("rhombus",true);

        Identifier texture;
        boolean rotatable;

        ParticleType(String name, boolean rotatable) {
            this.texture = Identifier.of("client", "textures/particles/" + name + ".png");
            this.rotatable = rotatable;
        }
    }

    @Getter
    @Accessors(fluent = true)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class Particle {
        ParticleType type;
        @NonFinal Vec3d position;
        @NonFinal Vec3d velocity;
        int index;
        int color;
        float size;
        private static final double BASE_VELOCITY = 0.05;
        double speedMultiplier;
        boolean isWorldParticle;
        boolean isTotem;

        StopWatch time = new StopWatch();
        Animation animation = new Animation();

        @NonFinal private float rotate = 0;

        public Particle(ParticleType type, Vec3d position, Vec3d velocity, int index, int rotate, int color, float size,
                        double speedMultiplier, boolean isWorldParticle, boolean isTotem) {
            this.type = type;
            this.position = position;
            this.velocity = isTotem ? velocity.multiply(0.08) : velocity.multiply(BASE_VELOCITY);
            this.index = index;
            this.color = color;
            this.rotate = rotate;
            this.size = size;
            this.speedMultiplier = speedMultiplier;
            this.isWorldParticle = isWorldParticle;
            this.isTotem = isTotem;
            this.time.reset();
        }

        public void update(double deltaTime) {
            double ticks = deltaTime * 120.0;
            if (ticks <= 0) return;

            double bounciness = 0.85;
            double gravity = isWorldParticle ? 0.0001 : 0.0005;

          // if (isTotem) {
          //     gravity = 0.0012;
          //     double dragXZ = Math.pow(0.89, ticks);
          //     velocity = new Vec3d(velocity.x * dragXZ, velocity.y, velocity.z * dragXZ);
          // }

            double nextX = position.x + velocity.x * ticks;
            double nextY = position.y + velocity.y * ticks;
            double nextZ = position.z + velocity.z * ticks;

            if (isBlockSolid(nextX, position.y, position.z)) {
                velocity = new Vec3d(-velocity.x * bounciness, velocity.y, velocity.z);
            } else {
                position = new Vec3d(nextX, position.y, position.z);
            }

            if (isBlockSolid(position.x, nextY, position.z)) {
                velocity = new Vec3d(velocity.x,  -velocity.y * bounciness, velocity.z);
            } else {
                position = new Vec3d(position.x, nextY, position.z);
            }

            if (isBlockSolid(position.x, position.y, nextZ)) {
                velocity = new Vec3d(velocity.x, velocity.y, -velocity.z * bounciness);
            } else {
                position = new Vec3d(position.x, position.y, nextZ);
            }

            velocity = velocity.subtract(0, gravity * ticks, 0);
            rotate += ( 2.0f) * ticks;
        }
    }

    public static boolean isBlockSolid(final double x, final double y, final double z) {
        if (mc.world == null) return false;
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        return mc.world.getBlockState(pos).isFullCube(mc.world, pos);
    }
}
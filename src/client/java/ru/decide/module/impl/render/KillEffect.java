package ru.decide.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.decide.manager.event_impl.AttackEvent;
import ru.decide.manager.event_impl.EventPacket;
import ru.decide.manager.event_impl.EventRender3D;
import ru.decide.manager.event_impl.EventTick;
import ru.decide.manager.event_impl.WorldLoadEvent;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.preview.ModulePreview;
import ru.decide.module.api.preview.PreviewContext;
import ru.decide.module.api.preview.PreviewSettings;
import ru.decide.module.api.settings.impl.ButtonSetting;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.module.api.settings.impl.ColorSetting;
import ru.decide.module.api.settings.impl.ModeSetting;
import ru.decide.module.api.settings.impl.SliderSetting;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.render.KimikoDissolve;
import ru.decide.utils.render.killeffect.EffectCatalog;
import ru.decide.utils.render.killeffect.EffectManager;
import ru.decide.utils.render.killeffect.EffectRenderer;
import ru.decide.utils.render.killeffect.ShardsEffect;
import ru.decide.utils.render.killeffect.SoulEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ModuleInfo(
        name = "Kill Effect",
        desc = "Kill effects on eliminated targets",
        category = Category.VISUALS
)
public class KillEffect extends Module implements ModulePreview {

    public ButtonSetting previewButton = PreviewSettings.button(this);

    /** Простые эффекты: id -> название. */
    private static final Map<String, String> BASIC_NAMES = new LinkedHashMap<>();
    /** 3D-анимации (BB-каталог): id -> название. */
    private static final Map<String, String> DISPLAY_NAMES = new LinkedHashMap<>();
    static {
        BASIC_NAMES.put("Beam", "Beam");
        BASIC_NAMES.put("soul", "Душа");
        BASIC_NAMES.put("shards", "Осколки");
        BASIC_NAMES.put("kimiko_dissolve", "Растворение");

        DISPLAY_NAMES.put("shark_attack", "Shark Attack");
        DISPLAY_NAMES.put("ice_shatter", "Ice Shatter");
        DISPLAY_NAMES.put("glass_shatter", "Glass Shatter");
        DISPLAY_NAMES.put("rust_decay", "Rust Decay");
        DISPLAY_NAMES.put("quicksand", "Quicksand");
        DISPLAY_NAMES.put("knockout_ko", "Knockout KO");
        DISPLAY_NAMES.put("bubble_burst", "Bubble Burst");
        DISPLAY_NAMES.put("spectral_fade", "Spectral Fade");
        DISPLAY_NAMES.put("energy_dissipation", "Energy Dissipation");
        DISPLAY_NAMES.put("stone_crumble", "Stone Crumble");
        DISPLAY_NAMES.put("sand_dissolve", "Sand Dissolve");
        DISPLAY_NAMES.put("water_evaporation", "Water Evaporation");
        DISPLAY_NAMES.put("sound_wave_disperse", "Sound Wave Disperse");
        DISPLAY_NAMES.put("digital_disintegration", "Digital Disintegration");
        DISPLAY_NAMES.put("tentacle_grasp", "Tentacle Grasp");
        DISPLAY_NAMES.put("plantfood_feasting", "Plantfood Feasting");
        DISPLAY_NAMES.put("tertis_smash", "Tetris Smash");
        DISPLAY_NAMES.put("light_absorption", "Light Absorption");
        DISPLAY_NAMES.put("astral_projection", "Astral Projection");
        DISPLAY_NAMES.put("arcade_gameover", "Arcade Gameover");
        DISPLAY_NAMES.put("angelic_bless", "Angelic Bless");
        DISPLAY_NAMES.put("liquid_meltdown", "Liquid Meltdown");
        DISPLAY_NAMES.put("hellfire_burn", "Hellfire Burn");
        DISPLAY_NAMES.put("imposter_instinct", "Imposter Instinct");
        DISPLAY_NAMES.put("colorful_explosion", "Colorful Explosion");
        DISPLAY_NAMES.put("feather_scatter", "Feather Scatter");
        DISPLAY_NAMES.put("nature_reclaim", "Nature Reclaim");
        DISPLAY_NAMES.put("dissolve_into_ash", "Dissolve Into Ash");
        DISPLAY_NAMES.put("hologram_flicker_out", "Hologram Flicker Out");
        DISPLAY_NAMES.put("kfx_abstracted", "Abstracted");
        DISPLAY_NAMES.put("kfx_acidic_corrosion", "Acidic Corrosion");
        DISPLAY_NAMES.put("kfx_clockwork_disassembly", "Clockwork Disassembly");
        DISPLAY_NAMES.put("kfx_frost_infection", "Frost Infection");
        DISPLAY_NAMES.put("kfx_graffiti_spray", "Graffiti Spray");
        DISPLAY_NAMES.put("kfx_ink_blots", "Ink Blots");
        DISPLAY_NAMES.put("kfx_magnetic_resonance", "Magnetic Resonance");
        DISPLAY_NAMES.put("kfx_origami_fold", "Origami Fold");
        DISPLAY_NAMES.put("kfx_pure_form", "Pure Form");
    }

    private static final String[] BASIC_VALUES = BASIC_NAMES.values().toArray(new String[0]);
    private static final String[] MODEL_VALUES = DISPLAY_NAMES.values().toArray(new String[0]);

    /** ��руппа списка эффектов: короткие эффекты или 3D-анимации. */
    public ModeSetting effectGroup = new ModeSetting(this, "Группа", "Эффекты", "3D анимации")
            .onAction(this::clearState);

    /** Короткий список: Beam / Душа / Осколки / Растворение. */
    public ModeSetting effectType = new ModeSetting(this, "Эффект", BASIC_VALUES)
            .setVisible(() -> effectGroup.is("Эффекты"));

    /** Длинный список 3D-анимаций. */
    public ModeSetting modelType = new ModeSetting(this, "3D анимация", MODEL_VALUES)
            .setVisible(() -> effectGroup.is("3D анимации"));

    public SliderSetting bbScale = new SliderSetting(this, "BB Scale", 1.0F, 0.2F, 3.0F, 0.1F)
            .setVisible(() -> effectGroup.is("3D анимации"));

    public ModeSetting typeColor = new ModeSetting(this, "Color mode", "Theme", "Custom")
            .setVisible(this::usesColorSetting);
    public ColorSetting tintColor = new ColorSetting(this, "Color", 0xFF00FFFF)
            .setVisible(() -> usesColorSetting() && typeColor.is("Custom"));

    // ── «Душа» / «Осколки» ──

    public BooleanSetting soulMobs = new BooleanSetting(this, "Мобы", false)
            .setVisible(this::isSoulOrShards);
    public BooleanSetting soulRotate360 = new BooleanSetting(this, "Поворот на 360", false)
            .setVisible(this::isSoul);
    public SliderSetting soulRise = new SliderSetting(this, "Высота подъёма", 5.2F, 1.0F, 10.0F, 0.2F)
            .setVisible(this::isSoul);
    public SliderSetting soulTransparency = new SliderSetting(this, "Прозрачность", 0.6F, 0.1F, 1.0F, 0.05F)
            .setVisible(this::isSoul);
    public SliderSetting soulBrightness = new SliderSetting(this, "Светлота", 0.35F, 0.0F, 1.0F, 0.05F)
            .setVisible(this::isSoul);
    public SliderSetting soulDuration = new SliderSetting(this, "Длительность", 3.0F, 1.0F, 6.0F, 0.1F)
            .setVisible(this::isSoulOrShards);
    public SliderSetting shardDensity = new SliderSetting(this, "Плотность осколков", 1.0F, 0.3F, 2.0F, 0.1F)
            .setVisible(this::isShards);

    public SliderSetting duration = new SliderSetting(this, "Duration", 2.2F, 1.0F, 5.0F, 0.1F);
    public SliderSetting beamHeight = new SliderSetting(this, "Beam height", 5.2F, 1.5F, 12.0F, 0.5F)
            .setVisible(this::isBeam);
    public SliderSetting beamWidth = new SliderSetting(this, "Beam width", 0.42F, 0.08F, 1.2F, 0.02F)
            .setVisible(this::isBeam);

    public SliderSetting dissolveCount = new SliderSetting(this, "Particles", 1400, 100, 4000, 50)
            .setVisible(this::isKimikoDissolve);
    public SliderSetting dissolveSize = new SliderSetting(this, "Particle size", 0.12F, 0.045F, 0.22F, 0.005F)
            .setVisible(this::isKimikoDissolve);
    public SliderSetting dissolveHold = new SliderSetting(this, "Hold", 550, 0, 2000, 25)
            .setVisible(this::isKimikoDissolve);
    public SliderSetting dissolveFade = new SliderSetting(this, "Fade", 1300, 250, 2400, 25)
            .setVisible(this::isKimikoDissolve);
    public SliderSetting dissolveRise = new SliderSetting(this, "Rise", 1.4F, 0.35F, 3.5F, 0.05F)
            .setVisible(this::isKimikoDissolve);
    public SliderSetting dissolveChaos = new SliderSetting(this, "Chaos", 1.0F, 0.0F, 2.5F, 0.05F)
            .setVisible(this::isKimikoDissolve);

    private final PreviewSettings previewSettings = PreviewSettings.of(this, 4F, 0F, 2.5F);

    private static final byte DEATH_STATUS = 3;
    private static final long KILL_WINDOW_MS = 6500L;
    private static final long DUPLICATE_WINDOW_MS = 750L;

    private static boolean catalogLoaded = false;

    private final List<KillBeam> beams = new ArrayList<>();
    private final BufferAllocator allocator = new BufferAllocator(1 << 15);
    private final EffectManager effectManager = new EffectManager();

    private LivingEntity lastTarget;
    private KillPoint lastPoint;
    private int lastTargetId = -1;
    private long lastAttackTime;
    private int lastSpawnedId = -1;
    private long lastSpawnTime;

    private boolean isBeam() {
        return "Beam".equals(getEffectId());
    }

    private boolean isSoul() {
        return "soul".equals(getEffectId());
    }

    private boolean isShards() {
        return "shards".equals(getEffectId());
    }

    private boolean isSoulOrShards() {
        return isSoul() || isShards();
    }

    private boolean usesColorSetting() {
        return isBeam() || isSoulOrShards();
    }

    private boolean isKimikoDissolve() {
        return "kimiko_dissolve".equals(getEffectId());
    }

    /**
 * Активный эффект: берём значение из списка той группы, которая выбрана.
 * Значение из старого конфига может отсутствовать в новом списке — тогда
 * откатываемся на первый вариант, иначе кнопка показывала бы пустоту.
 */
    private String getEffectId() {
        boolean model = effectGroup.is("3D анимации");
        ModeSetting setting = model ? modelType : effectType;
        String display = setting.getValue();

        if (!setting.values.contains(display)) {
            setting.set(setting.values.get(0));
            display = setting.getValue();
        }

        Map<String, String> names = model ? DISPLAY_NAMES : BASIC_NAMES;
        for (Map.Entry<String, String> e : names.entrySet()) {
            if (e.getValue().equals(display)) return e.getKey();
        }
        return model ? DISPLAY_NAMES.keySet().iterator().next() : "Beam";
    }

    public int getColor() {
        if (typeColor.is("Theme")) return ColorUtil.getClientColor1(1);
        return tintColor.getValue();
    }

    @Override
    protected void onEnable() {
        if (!catalogLoaded) {
            try {
                EffectCatalog.registerAll();
                catalogLoaded = true;
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onDisable() {
        clearState();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        clearState();
    }

    @Override
    public PreviewSettings previewSettings() { return previewSettings; }

    @Override
    public boolean previewNeedsDummy() { return true; }

    @Override
    public void previewSpawn(PreviewContext ctx) {
        LivingEntity target = ctx.dummy();
        if (isKimikoDissolve()) {
            LivingEntity source = target != null ? target : mc.player;
            if (source != null) {
                KimikoDissolve.get().queue(source, partialTick(), dissolveSettings());
            }
        } else if (isSoul()) {
            SoulEffect.get().queue(target != null ? target : mc.player);
        } else if (isShards()) {
            ShardsEffect.get().spawn(target != null ? target.getEntityPos() : ctx.anchor(), shardsSettings());
        } else if (!isBeam()) {
            Vec3d pos = target != null ? target.getEntityPos() : ctx.anchor();
            spawnBBEffect(pos, target != null ? target : mc.player);
        } else {
            KillPoint p = target != null ? point(target) : new KillPoint(ctx.anchor(), 0.65F);
            beams.add(new KillBeam(p));
        }
    }

    @Override
    public void previewStop() { clearState(); }

    @EventHandler
    public void onAttack(AttackEvent e) {
        if (!(e.getTarget() instanceof LivingEntity living) || living == mc.player) return;
        lastTarget = living;
        lastTargetId = living.getId();
        lastAttackTime = System.currentTimeMillis();
        lastPoint = point(living);
    }

    @EventHandler
    public void onPacket(EventPacket e) {
        if (!(e.getPacket() instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != DEATH_STATUS || mc.world == null) return;
        Entity entity = packet.getEntity(mc.world);
        if (!(entity instanceof LivingEntity living)) return;
        trySpawn(living, false);
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (lastTarget == null) return;
        if (System.currentTimeMillis() - lastAttackTime > KILL_WINDOW_MS) {
            lastTarget = null;
            lastPoint = null;
            return;
        }
        if (!lastTarget.isAlive() || lastTarget.isRemoved() || lastTarget.getHealth() <= 0.0F) {
            trySpawn(lastTarget, true);
        }
    }

    private void trySpawn(LivingEntity entity, boolean allowCachedPoint) {
        long now = System.currentTimeMillis();
        if (entity.getId() != lastTargetId || now - lastAttackTime > KILL_WINDOW_MS) return;
        if (lastSpawnedId == entity.getId() && now - lastSpawnTime < DUPLICATE_WINDOW_MS) return;
        // «Душа»/«Осколки» по умолчанию работают только по игрокам — мобы за отдельной настройкой
        if (isSoulOrShards() && !soulMobs.getValue() && !(entity instanceof AbstractClientPlayerEntity)) return;

        if (isBeam()) {
            KillPoint p = allowCachedPoint && lastPoint != null ? lastPoint : point(entity);
            beams.add(new KillBeam(p));
        } else if (isKimikoDissolve()) {
            KimikoDissolve.get().queue(entity, partialTick(), dissolveSettings());
        } else if (isSoul()) {
            SoulEffect.get().queue(entity);
        } else if (isShards()) {
            ShardsEffect.get().spawn(entity.getEntityPos(), shardsSettings());
        } else {
            spawnBBEffect(entity.getEntityPos(), entity);
        }

        lastSpawnedId = entity.getId();
        lastSpawnTime = now;
        lastTarget = null;
        lastPoint = null;
    }

    private void spawnBBEffect(Vec3d pos, LivingEntity target) {
        if (!catalogLoaded) {
            try {
                EffectCatalog.registerAll();
                catalogLoaded = true;
            } catch (Exception e) {
                return;
            }
        }
        String id = getEffectId();
        EffectCatalog.Effect effect = EffectCatalog.get(id);
        if (effect == null) {
            return;
        }

        Identifier skinTexture = null;
        if (target instanceof AbstractClientPlayerEntity player) {
            skinTexture = player.getSkin().body().texturePath();
        }

        double dx = mc.player.getX() - pos.x;
        double dz = mc.player.getZ() - pos.z;
        float yawToPlayer = (float) Math.toDegrees(Math.atan2(dx, dz));
        effectManager.spawn(effect, pos, yawToPlayer, skinTexture);
    }

    private KillPoint point(LivingEntity entity) {
        return new KillPoint(entity.getEntityPos(), Math.max(0.65F, entity.getWidth()));
    }

    private float partialTick() {
        try {
            return mc.getRenderTickCounter().getTickProgress(false);
        } catch (Throwable ignored) {
            return 0.0F;
        }
    }

    private KimikoDissolve.Settings dissolveSettings() {
        return new KimikoDissolve.Settings(
                dissolveCount.getValue().intValue(),
                dissolveSize.getValue(),
                dissolveHold.getValue().longValue(),
                dissolveFade.getValue().longValue(),
                dissolveRise.getValue(),
                dissolveChaos.getValue());
    }

    private SoulEffect.Settings soulSettings() {
        return new SoulEffect.Settings(
                soulRise.getValue(),
                soulRotate360.getValue(),
                soulColor(),
                soulDuration.getValue().longValue() * 1000L,
                soulTransparency.getValue(),
                soulBrightness.getValue());
    }

    private ShardsEffect.Settings shardsSettings() {
        return new ShardsEffect.Settings(
                shardsColor(),
                shardDensity.getValue(),
                soulDuration.getValue().longValue() * 1000L);
    }

    /**
 * Цвет режимов «Душа»/«Осколки». В оригинале тут были фиксированные цвета
 * (белая душа, тёпло-красные осколки) — здесь «Theme» берёт цвет клиента,
 * а фиксированные остаются доступны через «Custom».
 */
private int soulColor() {
        return typeColor.is("Custom") ? tintColor.getValue() : ColorUtil.getClientColor1(1);
    }

    private int shardsColor() {
        if (typeColor.is("Custom")) return tintColor.getValue();
        return 0xB98787FF;
    }

    /** Разбрасывает частицы по оттенкам вокруг базового цвета. */
    public static KillEffect getInstance() {
        return ru.decide.utils.other.Instance.get(KillEffect.class);
    }

    private static int applySeed(int base, int seed) {
        float jitter = ((seed & 0xFF) / 255.0F - 0.5F) * 0.55F;
        int r = MathHelper.clamp((int) (((base >> 16) & 0xFF) * (1.0F + jitter)), 0, 255);
        int g = MathHelper.clamp((int) (((base >> 8) & 0xFF) * (1.0F + jitter * 0.7F)), 0, 255);
        int b = MathHelper.clamp((int) ((base & 0xFF) * (1.0F + jitter * 1.3F)), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @EventHandler
    public void onRender3D(EventRender3D e) {
        if (mc.player == null || mc.world == null) return;

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

if (isKimikoDissolve()) {
            KimikoDissolve.get().render(matrices, cam, dissolveSettings(), seed -> {
                if (typeColor.is("Custom")) {
                    return applySeed(tintColor.getValue(), seed);
                }
                return applySeed(ColorUtil.getClientColor1(1), seed);
            }, partialTick());
            return;
        }

        if (isSoul()) {
            SoulEffect.get().render(matrices, cam, soulSettings(), partialTick());
            return;
        }

        if (isShards()) {
            ShardsEffect.get().render(matrices, cam, shardsSettings());
            return;
        }

        if (!isBeam()) {
            List<EffectManager.ActiveEffect> active = effectManager.active();
            if (!active.isEmpty()) {
                VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
                for (EffectManager.ActiveEffect effect : active) {
                    EffectRenderer.render(matrices, immediate, cam, effect);
                }
                immediate.draw();
            }
            return;
        }

        if (beams.isEmpty()) return;

        long now = System.currentTimeMillis();
        long durMs = (long) (duration.getValue() * 1000F);
        int color = getColor();
        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;
        Matrix4f mat = matrices.peek().getPositionMatrix();

        Iterator<KillBeam> it = beams.iterator();
        while (it.hasNext()) {
            KillBeam beam = it.next();
            float progress = (now - beam.start) / (float) durMs;
            if (progress >= 1F) { it.remove(); continue; }
            float fadeIn = MathHelper.clamp(progress * 5.5F, 0F, 1F);
            float fadeOut = progress > 0.5F ? 1F - (progress - 0.5F) / 0.5F : 1F;
            beam.alpha = smooth(fadeIn) * smooth(fadeOut);
            beam.progress = progress;
        }

        if (beams.isEmpty()) return;

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        VertexConsumer buf = immediate.getBuffer(BEAM_LAYER);
        float pulse = 0.9F + 0.1F * MathHelper.sin(now % 1600L / 1600F * MathHelper.TAU);
        for (KillBeam beam : beams) {
            drawBeam(buf, mat, cam, beam, cr, cg, cb, pulse);
        }
        immediate.draw();
    }

    private void drawBeam(VertexConsumer buf, Matrix4f mat, Vec3d cam, KillBeam beam,
                          int r, int g, int b, float pulse) {
        if (beam.alpha <= 0.01F) return;
        float grow = smooth(Math.min(beam.progress * 3.5F, 1F));
        float h = beamHeight.getValue() * grow;
        float base = beamWidth.getValue() * beam.radius;
        float x = (float) (beam.x - cam.x);
        float y = (float) (beam.y - cam.y + 0.035F);
        float z = (float) (beam.z - cam.z);
        float spin = (System.currentTimeMillis() - beam.start) / 1000F * 0.85F;
        float coreAlpha = 150F * beam.alpha * pulse;
        float softAlpha = 54F * beam.alpha;

        drawVerticalPetals(buf, mat, x, y, z, h, base * 0.72F, spin, r, g, b, (int) coreAlpha, 6);
        drawVerticalPetals(buf, mat, x, y, z, h * 0.82F, base * 1.28F, -spin * 0.55F, r, g, b, (int) softAlpha, 8);
        float bloom = 0.55F + 0.45F * grow;
        drawSoftDisc(buf, mat, x, y + 0.01F, z, base * 2.25F * bloom, r, g, b, (int) (42F * beam.alpha));
        drawSoftDisc(buf, mat, x, y + h * 0.98F, z, base * 0.92F * beam.alpha, r, g, b, (int) (28F * beam.alpha));
        for (int i = 0; i < 3; i++) {
            float wave = (beam.progress + i * 0.22F) % 1F;
            float radius = base * (1.05F + wave * 2.25F);
            int alpha = (int) (52F * beam.alpha * (1F - wave));
            drawRing(buf, mat, x, y + i * 0.025F, z, radius, 0.035F + wave * 0.045F, r, g, b, alpha);
        }
    }

    private static void drawVerticalPetals(VertexConsumer buf, Matrix4f mat, float x, float y, float z,
                                           float h, float halfWidth, float spin, int r, int g, int b,
                                           int alpha, int petals) {
        if (alpha <= 0 || h <= 0.01F) return;
        for (int i = 0; i < petals; i++) {
            float ang = spin + i / (float) petals * MathHelper.TAU;
            float dx = MathHelper.cos(ang) * halfWidth;
            float dz = MathHelper.sin(ang) * halfWidth;
            int edgeAlpha = Math.max(0, alpha / 3);
            buf.vertex(mat, x - dx, y, z - dz).color(r, g, b, edgeAlpha);
            buf.vertex(mat, x, y + h * 0.08F, z).color(r, g, b, alpha);
            buf.vertex(mat, x, y + h, z).color(r, g, b, 0);
            buf.vertex(mat, x + dx, y, z + dz).color(r, g, b, edgeAlpha);
        }
    }

    private static void drawSoftDisc(VertexConsumer buf, Matrix4f mat, float x, float y, float z,
                                     float radius, int r, int g, int b, int alpha) {
        if (alpha <= 0 || radius <= 0.01F) return;
        int segments = 56;
        for (int i = 0; i < segments; i++) {
            float a0 = i / (float) segments * MathHelper.TAU;
            float a1 = (i + 1) / (float) segments * MathHelper.TAU;
            buf.vertex(mat, x, y, z).color(r, g, b, alpha);
            buf.vertex(mat, x + MathHelper.cos(a0) * radius, y, z + MathHelper.sin(a0) * radius).color(r, g, b, 0);
            buf.vertex(mat, x + MathHelper.cos(a1) * radius, y, z + MathHelper.sin(a1) * radius).color(r, g, b, 0);
            buf.vertex(mat, x, y, z).color(r, g, b, alpha);
        }
    }

    private static void drawRing(VertexConsumer buf, Matrix4f mat, float x, float y, float z,
                                 float radius, float width, int r, int g, int b, int alpha) {
        if (alpha <= 0 || radius <= width) return;
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            float a0 = i / (float) segments * MathHelper.TAU;
            float a1 = (i + 1) / (float) segments * MathHelper.TAU;
            float x0 = MathHelper.cos(a0), z0 = MathHelper.sin(a0);
            float x1 = MathHelper.cos(a1), z1 = MathHelper.sin(a1);
            buf.vertex(mat, x + x0 * (radius - width), y, z + z0 * (radius - width)).color(r, g, b, 0);
            buf.vertex(mat, x + x0 * radius, y, z + z0 * radius).color(r, g, b, alpha);
            buf.vertex(mat, x + x1 * radius, y, z + z1 * radius).color(r, g, b, alpha);
            buf.vertex(mat, x + x1 * (radius - width), y, z + z1 * (radius - width)).color(r, g, b, 0);
        }
    }

    private static float smooth(float t) {
        t = MathHelper.clamp(t, 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    private void clearState() {
        beams.clear();
        KimikoDissolve.clear();
        SoulEffect.get().clear();
        ShardsEffect.get().clear();
        lastTarget = null;
        lastPoint = null;
        lastTargetId = -1;
        lastSpawnedId = -1;
    }

    private record KillPoint(Vec3d pos, float radius) {}

    private static class KillBeam {
        final double x, y, z;
        final float radius;
        final long start;
        float alpha;
        float progress;

        KillBeam(KillPoint point) {
            this.x = point.pos.x;
            this.y = point.pos.y;
            this.z = point.pos.z;
            this.radius = point.radius;
            this.start = System.currentTimeMillis();
        }
    }

    private static final RenderPipeline BEAM_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("decide", "kill_effect_beam"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer BEAM_LAYER = RenderLayer.of(
            "kill_effect_beam",
            RenderSetup.builder(BEAM_PIPELINE).expectedBufferSize(1 << 15).build()
    );
}

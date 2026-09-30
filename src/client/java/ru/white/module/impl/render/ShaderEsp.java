package ru.white.module.impl.render;

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
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.ShaderEspOutlinePipeline;
import ru.white.utils.render.ShaderEspRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

@ModuleInfo(name = "Shader ESP", category = Category.VISUALS, desc = "Шейдерный ESP с ореолом свечения для сущностей")
public class ShaderEsp extends Module implements ModulePreview {

    private static ShaderEsp instance;

    public ButtonSetting previewButton = PreviewSettings.button(this);

    // ---- цели ----
    public BooleanSetting localPlayer  = new BooleanSetting(this, "Локальный игрок", true);
    public BooleanSetting players      = new BooleanSetting(this, "Игроки", true);
    public BooleanSetting ignoreNaked  = new BooleanSetting(this, "Игнорировать голых", false)
            .setVisible(() -> players.getValue());
    public BooleanSetting mobs         = new BooleanSetting(this, "Мобы", true);
    public BooleanSetting items        = new BooleanSetting(this, "Предметы", false);

    // ---- цвет ----
    public ModeSetting typeColor = new ModeSetting(this, "Режим цвета", "Тема", "Свой");
    public ColorSetting tintColor = new ColorSetting(this, "Цвет", 0xFFFF3355)
            .setVisible(() -> typeColor.is("Свой"));
    public SliderSetting saturation = new SliderSetting(this, "Насыщенность", 1.15f, 0f, 2f, 0.05f);
    public SliderSetting opacity = new SliderSetting(this, "Прозрачность", 1f, 0.05f, 1f, 0.01f);
    public BooleanSetting friendColor = new BooleanSetting(this, "Цвет друзей", true);
    public ColorSetting friendTint = new ColorSetting(this, "Цвет друга", 0xFF55FF55)
            .setVisible(() -> friendColor.getValue());

    // ---- внешний ореол ----
    public BooleanSetting glow = new BooleanSetting(this, "Свечение", true);
    public SliderSetting glowRadius = new SliderSetting(this, "Радиус свечения", 0.55f, 0f, 1f, 0.01f)
            .setVisible(() -> glow.getValue());
    public SliderSetting glowStrength = new SliderSetting(this, "Сила свечения", 1.6f, 0f, 4f, 0.05f)
            .setVisible(() -> glow.getValue());
    public SliderSetting glowFalloff = new SliderSetting(this, "Спад свечения", 1.1f, 0.2f, 4f, 0.05f)
            .setVisible(() -> glow.getValue());

    // ---- заливка силуэта ----
    public BooleanSetting fill = new BooleanSetting(this, "Заливка", true);
    public SliderSetting fillOpacity = new SliderSetting(this, "Плотность заливки", 0.45f, 0f, 1.5f, 0.01f)
            .setVisible(() -> fill.getValue());
    public SliderSetting innerGlow = new SliderSetting(this, "Внутреннее свечение", 0.6f, 0f, 2f, 0.05f)
            .setVisible(() -> fill.getValue());

    // ---- кромка ----
    public BooleanSetting outline = new BooleanSetting(this, "Обводка", true);
    public ModeSetting outlineMode = new ModeSetting(this, "Тип обводки", "Снаружи", "Внутри", "Обе")
            .setVisible(() -> outline.getValue());
    public SliderSetting outlineWidth = new SliderSetting(this, "Ширина обводки", 2, 1, 5, 1)
            .setVisible(() -> outline.getValue());
    public SliderSetting outlineStrength = new SliderSetting(this, "Яркость обводки", 1.8f, 0f, 3f, 0.05f)
            .setVisible(() -> outline.getValue());
    public SliderSetting outlineWhite = new SliderSetting(this, "Белизна обводки", 0.45f, 0f, 1f, 0.01f)
            .setVisible(() -> outline.getValue());

    // ---- анимации ----
    public BooleanSetting pulse = new BooleanSetting(this, "Пульсация", false);
    public SliderSetting pulseSpeed = new SliderSetting(this, "Скорость пульсации", 1f, 0.1f, 5f, 0.1f)
            .setVisible(() -> pulse.getValue());
    public SliderSetting pulseAmount = new SliderSetting(this, "Сила пульсации", 0.3f, 0f, 1f, 0.05f)
            .setVisible(() -> pulse.getValue());

    public BooleanSetting shimmer = new BooleanSetting(this, "Шиммер", false);
    public SliderSetting shimmerWidth = new SliderSetting(this, "Ширина шиммера", 0.04f, 0.01f, 0.15f, 0.01f)
            .setVisible(() -> shimmer.getValue());
    public SliderSetting shimmerPeriod = new SliderSetting(this, "Период шиммера", 5f, 1f, 15f, 0.5f)
            .setVisible(() -> shimmer.getValue());
    public SliderSetting shimmerBrightness = new SliderSetting(this, "Яркость шиммера", 0.8f, 0f, 2f, 0.05f)
            .setVisible(() -> shimmer.getValue());

    // ESP виден постоянно, повторять нечего — интервала у предпоказа нет
    private final PreviewSettings previewSettings = PreviewSettings.withoutInterval(this, 4F, 0F);

    private static final Set<Entity> allTargets =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public ShaderEsp() {
        instance = this;
    }

    public Framebuffer customFboReference;

    public static ShaderEsp getInstance() {
        return instance;
    }

    public static boolean isEspTarget(Entity entity) {
        return allTargets.contains(entity);
    }

    public static Set<Entity> getAllTargets() {
        return allTargets;
    }

    public int getColor() {
        if (typeColor.is("Тема")) {
            return ColorUtil.getClientColor1(1);
        }
        return tintColor.getValue();
    }

    @Override
    protected void onEnable() {
        ShaderEspRenderer.getInstance().invalidate();
    }

    @Override
    protected void onDisable() {
        allTargets.clear();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        allTargets.clear();
        if (isEnabled()) ShaderEspRenderer.getInstance().invalidate();
    }

    @EventHandler
    public void onTick(EventTick event) {
        allTargets.clear();
        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (shouldTarget(entity)) {
                allTargets.add(entity);
            }
        }

        // болванчика подсвечиваем даже когда «Игроки» выключены — иначе показывать нечего
        if (previewDummy != null && !previewDummy.isRemoved()) {
            allTargets.add(previewDummy);
        }
    }

    // ───────────────────────────── предпоказ ─────────────────────────────

    private Entity previewDummy;

    @Override
    public PreviewSettings previewSettings() {
        return previewSettings;
    }

    @Override
    public boolean previewNeedsDummy() {
        return true;
    }

    @Override
    public void previewStart(PreviewContext ctx) {
        previewDummy = ctx.dummy();
    }

    @Override
    public void previewTick(PreviewContext ctx) {
        previewDummy = ctx.dummy();
    }

    /** Ореол горит на болванчике всё время, пока открыт редактор. */
    @Override
    public void previewSpawn(PreviewContext ctx) {
    }

    @Override
    public void previewStop() {
        previewDummy = null;
        allTargets.clear();
    }

    public void onRender3D() {
        if (mc.world == null || mc.player == null) return;
        if (allTargets.isEmpty()) return;

        Framebuffer fb = mc.getFramebuffer();
        if (fb == null) return;

        Framebuffer outlineFbo = customFboReference;
        if (outlineFbo == null || outlineFbo.getColorAttachmentView() == null) return;

        ShaderEspRenderer renderer = ShaderEspRenderer.getInstance();
        applySettings(renderer.params);

        renderer.renderFromFbo(outlineFbo, fb.textureWidth, fb.textureHeight);
    }

    private void applySettings(ShaderEspOutlinePipeline.Params p) {
        p.color = getColor();
        p.friendEnabled = friendColor.getValue();
        p.friendColor = friendTint.getValue();
        p.opacity = opacity.getValue();
        p.saturation = saturation.getValue();

        p.glowEnabled = glow.getValue();
        p.glowRadius = glowRadius.getValue();
        p.glowStrength = glowStrength.getValue();
        p.glowFalloff = glowFalloff.getValue();

        p.fillEnabled = fill.getValue();
        p.fillOpacity = fillOpacity.getValue();
        p.innerGlow = innerGlow.getValue();

        p.outlineEnabled = outline.getValue();
        p.outlineMode = outlineMode.getIndex();
        p.outlineWidth = outlineWidth.getValue().intValue();
        p.outlineStrength = outlineStrength.getValue();
        p.outlineWhite = outlineWhite.getValue();

        p.pulseEnabled = pulse.getValue();
        p.pulseSpeed = pulseSpeed.getValue();
        p.pulseAmount = pulseAmount.getValue();

        p.shimmerEnabled = shimmer.getValue();
        p.shimmerWidth = shimmerWidth.getValue();
        p.shimmerPeriodSec = shimmerPeriod.getValue();
        p.shimmerBrightness = shimmerBrightness.getValue();
    }

    private boolean shouldTarget(Entity entity) {
        if (entity instanceof PlayerEntity p) {
            if (p.getCustomName() != null && p.getCustomName().getString().startsWith("Ghost_")) return false;
            if (!players.getValue()) return false;
            if (ignoreNaked.getValue() && !hasArmor(p)) return false;
            if (entity.isInvisible()) return false;
        } else if (entity instanceof ClientPlayerEntity) {
            if (!localPlayer.getValue()) return false;
        } else if (entity instanceof LivingEntity) {
            if (!mobs.getValue()) return false;
            if (entity.isInvisible()) return false;
        } else if (entity instanceof ItemEntity) {
            if (!items.getValue()) return false;
        } else {
            return false;
        }
        if (!isEntityVisible(entity)) return false;
        return true;
    }

    private boolean isEntityVisible(Entity entity) {
        if (mc.world == null || mc.player == null) return false;
        net.minecraft.util.math.Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        net.minecraft.util.math.Vec3d entityCenter = new net.minecraft.util.math.Vec3d(entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ());

        BlockHitResult result = mc.world.raycast(new RaycastContext(
                cameraPos,
                entityCenter,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        return result.getType() == HitResult.Type.MISS;
    }

    private static boolean hasArmor(PlayerEntity p) {
        return !p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
            || !p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
            || !p.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
            || !p.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }
}

package ru.decide.module.impl.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.decide.manager.event_impl.EventTick;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.module.api.settings.impl.ColorSetting;
import ru.decide.module.api.settings.impl.DelimiterSetting;
import ru.decide.module.api.settings.impl.ModeSetting;
import ru.decide.module.api.settings.impl.SliderSetting;
import ru.decide.utils.other.Instance;
import ru.decide.utils.math.ChatUtils;
import ru.decide.utils.render.RenderUtil;
import ru.decide.utils.render.font.Fonts;
import ru.decide.utils.render.wasted.WastedPipeline;
import ru.decide.utils.render.wasted.WastedState;

import java.awt.Color;

/**
 * Портированный Kimiko Wasted — кинематографичная смерть в стиле GTA V:
 * обесцвечивание кадра, облёт камеры вокруг тела, надпись и авто-возрождение.
 *
 * Смерть ловится на экране смерти и по факту !player.isAlive() в тике
 * (последнее нужно для серверов с авто-респавном, где экрана смерти нет).
 */
@ModuleInfo(
        name = "Wasted",
        desc = "Кинематографичная смерть в стиле GTA V",
        category = Category.VISUALS
)
public final class WastedDeath extends Module {

    public static WastedDeath getInstance() {
        return Instance.get(WastedDeath.class);
    }

    private static WastedDeath getInstanceIfReady() {
        try {
            return getInstance();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean isRunning() {
        WastedDeath module = getInstanceIfReady();
        return module != null && module.isEnabled() && WastedState.isActive();
    }

    private static final String LABEL_WASTED = "WASTED";
    private static final String LABEL_RU = "ТЫ УМЕР";
    private static final String LABEL_NONE = "Нет";

    private static final float TEXT_Y_LIFT = 5.0f;
    private static final double CORPSE_EYE_HEIGHT = 0.7;
    private static final double DETACH_DISTANCE_SQ = 9.0;
    private static final long RETRIGGER_GUARD_MS = 4000L;
    private static final long RESPAWN_WAIT_MS = 4000L;

    private static final Identifier WASTED_SOUND_ID = Identifier.of("decide", "wasted");

    public final DelimiterSetting generalSeparator = new DelimiterSetting(this, "Эффект");
    public final SliderSetting duration = new SliderSetting(this, "Длительность", 6F, 2F, 10F, 1F);
    public final ModeSetting label = new ModeSetting(this, "Надпись", LABEL_WASTED, LABEL_RU, LABEL_NONE);
    public final ColorSetting textColor = new ColorSetting(this, "Цвет надписи", new Color(178, 20, 24, 255).getRGB());

    public final DelimiterSetting cameraSeparator = new DelimiterSetting(this, "Камера");
    public final BooleanSetting orbit = new BooleanSetting(this, "Облёт камеры", true);
    public final SliderSetting orbitSpeed = new SliderSetting(this, "Скорость облёта", 14F, 0F, 60F, 1F);
    public final SliderSetting orbitDistance = new SliderSetting(this, "Дальность", 45F, 15F, 90F, 5F);
    public final BooleanSetting autoRespawn = new BooleanSetting(this, "Возрождать после эффекта", true);

    public final DelimiterSetting soundSeparator = new DelimiterSetting(this, "Звук");
    public final BooleanSetting playSound = new BooleanSetting(this, "Звук смерти", true);
    public final SliderSetting soundVolume = new SliderSetting(this, "Громкость", 70F, 10F, 100F, 5F);

    private Perspective savedCameraType;
    private boolean respawnPending;
    private long lastFinishedAtMs;
    private long respawnWaitUntilMs;
    private Screen pendingDeathScreen;

    // ================= Публичное для миксинов =================

    /** Вызывается из DeathScreenMixin: перехватываем ванильный экран смерти. */
    public static boolean interceptDeathScreen(Screen screen) {
        WastedDeath module = getInstanceIfReady();
        if (module == null || !module.isEnabled()) return false;
        return module.beginFrom(screen);
    }

    /** Пока активен эффект или ждём респавна — ванильный экран смерти не показываем. */
    public static boolean blocksDeathScreen() {
        WastedDeath module = getInstanceIfReady();
        if (module == null || !module.isEnabled()) return false;
        return WastedState.isActive() || module.awaitingRespawn();
    }

    public boolean orbitEnabled() {
        return orbit.getValue();
    }

    public float orbitYaw() {
        return WastedState.startYaw() + WastedState.elapsedMs() / 1000.0f * orbitSpeed.getValue();
    }

    public float orbitPitch() {
        return 22.0f + 26.0f * ease();
    }

    public float orbitDistanceNow() {
        return 2.0f + (orbitDistance.getValue() / 10.0f - 2.0f) * ease();
    }

    public Vec3d orbitPosition() {
        Vec3d anchor = WastedState.anchor();
        double yaw = Math.toRadians(orbitYaw());
        double pitch = Math.toRadians(orbitPitch());
        double distance = orbitDistanceNow();

        double cosPitch = Math.cos(pitch);
        double dirX = -Math.sin(yaw) * cosPitch;
        double dirY = -Math.sin(pitch);
        double dirZ = Math.cos(yaw) * cosPitch;

        return new Vec3d(
                anchor.x - dirX * distance,
                anchor.y + CORPSE_EYE_HEIGHT - dirY * distance,
                anchor.z - dirZ * distance);
    }

    // ================= Логика =================

    @EventHandler
    public void onTick(EventTick event) {
        // серверы с авто-респавном не показывают экран смерти — ловим смерть по alive
        if (!WastedState.isActive() && !respawnPending && isEnabled()) {
            ClientPlayerEntity player = mc.player;
            if (player != null && !player.isAlive() && mc.world != null) {
                beginFrom(null);
            }
        }

        if (WastedState.isActive()) {
            if (mc.player == null || mc.world == null) {
                WastedState.stop();
            } else {
                updateDetach();
            }
        }

        if (!WastedState.isActive()) {
            restoreCamera();
            finishRespawn();
        }
    }

    /** Камера отрывается от тела, только если чанк с телом реально загружен. */
    private void updateDetach() {
        ClientPlayerEntity player = mc.player;
        ClientWorld level = mc.world;
        if (player == null || level == null || !orbit.getValue()) {
            WastedState.setDetached(false);
            return;
        }

        Vec3d anchor = WastedState.anchor();
        if (!player.isAlive() || anchor == Vec3d.ZERO) {
            WastedState.setDetached(false);
            return;
        }

        boolean moved = player.getEntityPos().squaredDistanceTo(anchor) > DETACH_DISTANCE_SQ;
        BlockPos pos = BlockPos.ofFloored(anchor);
        boolean loaded = level.getChunkManager().isChunkLoaded(
                ChunkSectionPos.getSectionCoord(pos.getX()),
                ChunkSectionPos.getSectionCoord(pos.getZ()));

        WastedState.setDetached(moved && loaded);
    }

    private boolean beginFrom(Screen screen) {
        ClientPlayerEntity player = mc.player;
        if (!isEnabled() || player == null) return false;

        if (WastedState.isActive()) {
            if (screen != null && pendingDeathScreen == null) pendingDeathScreen = screen;
            return true;
        }
        if (System.currentTimeMillis() - lastFinishedAtMs < RETRIGGER_GUARD_MS) {
            if (screen != null && respawnPending && pendingDeathScreen == null) pendingDeathScreen = screen;
            return false;
        }

        pendingDeathScreen = screen;

        Vec3d deathPos = player.isAlive() ? player.getEntityPos() : player.getLastRenderPos();
        float deathYaw = player.getYaw();

        WastedState.begin(deathPos, deathYaw, (long) (duration.getValue() * 1000L));
        respawnPending = true;
        respawnWaitUntilMs = 0L;

        if (orbit.getValue()) {
            savedCameraType = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }

        if (playSound.getValue()) {
            playWastedSound(soundVolume.getValue() / 100.0f);
        }

        // регистрируем пайплайн сразу: сбой должен быть виден в логе, а не молча отключать эффект
        if (WastedPipeline.deviceReady() && !WastedPipeline.validate()) {
            ChatUtils.addChatMessage("§c[Wasted] пайплайн недоступен, эффект не отрисуется");
        }
        return true;
    }

    private static void playWastedSound(float volume) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSoundManager() == null) return;
        // ui(event, pitch, volume)
        client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvent.of(WASTED_SOUND_ID), 1.0f, volume));
    }

    private boolean awaitingRespawn() {
        if (respawnWaitUntilMs == 0L) return false;

        ClientPlayerEntity player = mc.player;
        if (player == null || player.isAlive() || System.currentTimeMillis() > respawnWaitUntilMs) {
            respawnWaitUntilMs = 0L;
            return false;
        }
        return true;
    }

    private void finishRespawn() {
        if (!respawnPending) return;

        respawnPending = false;
        lastFinishedAtMs = System.currentTimeMillis();

        Screen deathScreen = pendingDeathScreen;
        pendingDeathScreen = null;

        ClientPlayerEntity player = mc.player;
        if (player == null || player.isAlive()) return;

        ClientWorld level = mc.world;
        boolean hardcore = level != null && level.getLevelProperties() != null && level.getLevelProperties().isHardcore();

        if (autoRespawn.getValue() && !hardcore) {
            respawnWaitUntilMs = System.currentTimeMillis() + RESPAWN_WAIT_MS;
            player.requestRespawn();
        } else if (deathScreen != null) {
            mc.setScreen(deathScreen);
        }
    }

    private void restoreCamera() {
        Perspective cam = savedCameraType;
        if (cam != null && mc.options != null) mc.options.setPerspective(cam);
        savedCameraType = null;
    }

    @Override
    protected void onEnable() {
        WastedState.stop();
        respawnPending = false;
        respawnWaitUntilMs = 0L;
        pendingDeathScreen = null;
    }

    @Override
    protected void onDisable() {
        WastedState.stop();
        restoreCamera();
        respawnPending = false;
        respawnWaitUntilMs = 0L;
        pendingDeathScreen = null;
    }

    // ================= Оверлей =================

    public void renderOverlay(DrawContext graphics) {
        float alpha = WastedState.textAlpha();
        if (alpha <= 0.01f || label.is(LABEL_NONE)) return;

        String text = label.is(LABEL_RU) ? LABEL_RU : LABEL_WASTED;
        float screenW = graphics.getScaledWindowWidth();
        float screenH = graphics.getScaledWindowHeight();

        float pop = Math.min(1.0f, alpha * 1.15f);
        float size = 34.0f * (1.06f - 0.06f * pop);
        float textWidth = Fonts.sf_bold.getWidth(text, size);
        float centerY = screenH * 0.5f;
        float bandHeight = size * 1.5f;
        float bandY = centerY - bandHeight * 0.5f;

        RenderUtil.Render2D.rect(0.0f, bandY, screenW, bandHeight, color(24, 24, 24, 150, alpha));
        RenderUtil.Render2D.rect(0.0f, bandY - 1.2f, screenW, 1.2f, color(0, 0, 0, 90, alpha));
        RenderUtil.Render2D.rect(0.0f, bandY + bandHeight, screenW, 1.2f, color(0, 0, 0, 90, alpha));

        int base = textColor.getValue();
        int red = base >> 16 & 0xFF;
        int green = base >> 8 & 0xFF;
        int blue = base & 0xFF;

        float textY = centerY - size * 0.5f - TEXT_Y_LIFT;

        Fonts.sf_bold.draw(text, (screenW - textWidth) * 0.5f + 1.5f, textY + 1.5f, size, color(0, 0, 0, 120, alpha));
        Fonts.sf_bold.draw(text, (screenW - textWidth) * 0.5f, textY, size, color(red, green, blue, 255, alpha));
    }

    /** Плавный разгон облёта в первые 2.2 секунды. */
    private static float ease() {
        float t = MathHelper.clamp(WastedState.elapsedMs() / 2200.0f, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static int color(int r, int g, int b, int a, float mult) {
        int fa = MathHelper.clamp(Math.round(a * mult), 0, 255);
        return fa <= 0 ? 0 : new Color(r, g, b, fa).getRGB();
    }

    /** Экран смерти не должен рендериться, пока идёт эффект. */
    public static boolean shouldSuppressVanillaScreen(Screen screen) {
        return screen instanceof DeathScreen && blocksDeathScreen();
    }
}

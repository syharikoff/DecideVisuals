package ru.white.module.impl.render.emotions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import ru.white.module.impl.render.Emotions;
import ru.white.utils.render.Draw;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EmotionWheelManager {
    private static final float FADE_IN_MS = 280f;
    private static final float FADE_OUT_MS = 320f;
    private static final EmotionType[] EMOTIONS = EmotionType.values();
    private static final int SLOT_COUNT = EMOTIONS.length;

    private static final Font TITLE_FONT = Fonts.sf_bold;
    private static final Font SUB_FONT = Fonts.sf_regular;
    private static final Font HUB_FONT = Fonts.sf_medium;

    private static EmotionWheelManager instance;

    private final Map<UUID, ActiveEmotion> activeEmotions = new ConcurrentHashMap<>();

    private boolean wheelOpen;
    private float wheelAnim;
    private float wheelAnimTarget;
    private long lastAnimMs;

    private final float[] slotHover = new float[SLOT_COUNT];
    private double cursorX;
    private double cursorY;
    private int stickySlot = -1;
    private EmotionType hovered;
    private EmotionType previewEmotion = EmotionType.HELLO;
    private long previewStartedAtMs;

    private EmotionWheelManager() {
    }

    public static EmotionWheelManager getInstance() {
        if (instance == null) {
            instance = new EmotionWheelManager();
        }
        return instance;
    }

    public boolean isWheelOpen() {
        return wheelOpen || wheelAnim > 0.01f;
    }

    public boolean isMouseCaptured() {
        return wheelOpen;
    }

    public void onKeyPress(int key) {
        Emotions module = Emotions.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        int openKey = module.getWheelBind().getValue();
        if (openKey < 0 || key != openKey) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.world != null && mc.currentScreen == null) {
            openWheel();
        }
    }

    public void onKeyRelease(int key) {
        Emotions module = Emotions.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        int openKey = module.getWheelBind().getValue();
        if (openKey < 0 || key != openKey) {
            return;
        }
        if (wheelOpen) {
            EmotionType pick = hovered;
            closeWheel(false);
            if (pick != null) {
                playSelectedEmotion(pick);
            }
        }
    }

    public void onMouseMove(double cursorDeltaX, double cursorDeltaY) {
        if (!wheelOpen) {
            return;
        }
        cursorX += cursorDeltaX * 0.85;
        cursorY += cursorDeltaY * 0.85;
        double max = 160.0;
        double len = Math.hypot(cursorX, cursorY);
        if (len > max) {
            cursorX = cursorX / len * max;
            cursorY = cursorY / len * max;
        }
    }

    public void onTick() {
        Emotions module = Emotions.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (module == null || !module.isEnabled()) {
            closeWheel(true);
            stopLocalEmotion();
            return;
        }
        if (mc.player == null || mc.world == null) {
            closeWheel(true);
            stopLocalEmotion();
            return;
        }
        if (mc.currentScreen != null && wheelOpen) {
            closeWheel(true);
        }

        Iterator<Map.Entry<UUID, ActiveEmotion>> it = activeEmotions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveEmotion> entry = it.next();
            if (!entry.getValue().isExpired()) {
                continue;
            }
            if (entry.getValue().local && mc.player.getUuid().equals(entry.getKey())) {
                mc.options.setPerspective(entry.getValue().previousPerspective);
            }
            it.remove();
        }
    }

    public void render() {
        Emotions module = Emotions.getInstance();
        if (module == null || !module.isEnabled() || MinecraftClient.getInstance().world == null) {
            return;
        }

        long now = System.currentTimeMillis();
        float dt = lastAnimMs == 0L ? 0.016f : MathHelper.clamp((now - lastAnimMs) / 1000f, 0.001f, 0.05f);
        lastAnimMs = now;

        wheelAnimTarget = wheelOpen ? 1f : 0f;
        wheelAnim = approach(wheelAnim, wheelAnimTarget, dt, 10f);

        if (wheelAnim <= 0.001f && !wheelOpen) {
            return;
        }

        updateHoverStable();
        for (int i = 0; i < SLOT_COUNT; i++) {
            float target = (stickySlot == i) ? 1f : 0f;
            slotHover[i] = approach(slotHover[i], target, dt, 14f);
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) {
            return;
        }
        drawWheel(mc);
    }

    public void closeWheel() {
        closeWheel(true);
    }

    public void closeWheel(boolean force) {
        wheelOpen = false;
        if (force) {
            hovered = null;
            stickySlot = -1;
        }
    }

    public void openFromModule() {
        Emotions module = Emotions.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            return;
        }
        openWheel();
    }

    public void setPreviewEmotion(EmotionType emotionType) {
        if (emotionType == null) {
            return;
        }
        if (previewEmotion != emotionType) {
            previewEmotion = emotionType;
            previewStartedAtMs = System.currentTimeMillis();
        }
    }

    public EmotionType getPreviewEmotion() {
        return previewEmotion;
    }

    public void playSelectedEmotion(EmotionType emotionType) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || emotionType == null) {
            return;
        }
        startEmotion(mc.player, emotionType, true, System.currentTimeMillis());
    }

    public void applyToModel(PlayerEntityRenderState state,
                             ModelPart head, ModelPart hat, ModelPart body,
                             ModelPart rightArm, ModelPart leftArm,
                             ModelPart rightLeg, ModelPart leftLeg) {
        Emotions module = Emotions.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (module == null || !module.isEnabled() || mc.world == null) {
            return;
        }

        Entity entity = mc.world.getEntityById(state.id);
        if (!(entity instanceof PlayerEntity player)) {
            return;
        }

        EmotionType type = null;
        float blend = 0f;
        long startedAt = 0L;

        ActiveEmotion active = activeEmotions.get(player.getUuid());
        if (active != null) {
            type = active.type;
            blend = active.getBlend();
            startedAt = active.startedAt;
        } else if (isWheelOpen() && mc.player != null && player == mc.player && hovered != null) {
            type = hovered;
            startedAt = previewStartedAtMs;
            blend = MathHelper.clamp(wheelAnim * 0.85f, 0f, 1f);
        }

        if (type == null || blend <= 0.001f) {
            return;
        }

        long elapsed = Math.max(0L, System.currentTimeMillis() - startedAt);
        boolean hands = canAnimateHands(player);

        switch (type) {
            case DEB -> poseDeb(blend, hands, head, rightArm, leftArm);
            case FLOSS -> poseFloss(blend, elapsed, hands, body, rightArm, leftArm, rightLeg, leftLeg);
            case MASTURBATE -> poseMasturbate(blend, elapsed, hands, player, body, rightArm, leftArm, rightLeg, leftLeg);
            case HELLO -> poseHello(blend, elapsed, hands, rightArm);
            case GET_GRIDDY -> poseGriddy(blend, elapsed, hands, head, body, rightArm, leftArm, rightLeg, leftLeg);
            case HAPPY -> poseHappy(blend, hands, rightArm, leftArm);
        }

        hat.pitch = head.pitch;
        hat.yaw = head.yaw;
        hat.roll = head.roll;
    }

    private void openWheel() {
        wheelOpen = true;
        cursorX = 0;
        cursorY = 0;
        stickySlot = -1;
        hovered = null;
        previewEmotion = EmotionType.HELLO;
        previewStartedAtMs = System.currentTimeMillis();
        lastAnimMs = System.currentTimeMillis();
    }

    private void updateHoverStable() {
        double len = Math.hypot(cursorX, cursorY);

        if (len < 28.0) {
            if (stickySlot >= 0 && stickySlot < SLOT_COUNT) {
                hovered = EMOTIONS[stickySlot];
            } else {
                hovered = null;
            }
            return;
        }

        double ang = Math.atan2(cursorY, cursorX);
        double deg = Math.toDegrees(ang);

        double fromTop = deg + 90.0;
        if (fromTop < 0) {
            fromTop += 360.0;
        }
        if (fromTop >= 360.0) {
            fromTop -= 360.0;
        }

        float sector = 360f / SLOT_COUNT;
        int raw = (int) Math.floor(fromTop / sector + 0.0001);
        raw = MathHelper.clamp(raw, 0, SLOT_COUNT - 1);

        if (stickySlot < 0) {
            stickySlot = raw;
        } else if (raw != stickySlot) {
            double centerOfRaw = raw * sector + sector * 0.5;
            double centerOfSticky = stickySlot * sector + sector * 0.5;
            double dRaw = angularDist(fromTop, centerOfRaw);
            double dSticky = angularDist(fromTop, centerOfSticky);

            if (dRaw + 8.0 < dSticky) {
                stickySlot = raw;
            }
        }

        if (stickySlot >= 0 && stickySlot < SLOT_COUNT) {
            hovered = EMOTIONS[stickySlot];
            setPreviewEmotion(hovered);
        } else {
            hovered = null;
        }
    }

    private void drawWheel(MinecraftClient mc) {
        float guiW = mc.getWindow().getScaledWidth();
        float guiH = mc.getWindow().getScaledHeight();
        float anim = easeOutCubic(MathHelper.clamp(wheelAnim, 0f, 1f));

        Draw.rect(0, 0, guiW, guiH, withAlpha(0xFF000000, 0.45f * anim));

        float cx = guiW * 0.5f;
        float cy = guiH * 0.5f;

        float ringR = 118f * (0.70f + 0.30f * anim);
        float slotBase = 46f;
        float hubR = 58f;

        float outerSize = (ringR + slotBase + 14f) * 2f;
        Draw.rect(
                cx - outerSize / 2f,
                cy - outerSize / 2f,
                outerSize,
                outerSize,
                withAlpha(0xFF12161C, 0.72f * anim),
                outerSize / 2f
        );

        float hubSize = hubR * 2f;
        Draw.rect(
                cx - hubR,
                cy - hubR,
                hubSize,
                hubSize,
                withAlpha(0xFF1C222C, 0.95f * anim),
                hubR
        );
        Draw.rect(
                cx - hubR + 6f,
                cy - hubR + 6f,
                hubSize - 12f,
                hubSize - 12f,
                withAlpha(0xFF252D3A, 0.95f * anim),
                hubR - 6f
        );

        String hubText = hovered != null ? hovered.getLocalized() : "Эмоции";
        HUB_FONT.drawCentered(hubText, cx, cy - 7f, 15f, withAlpha(0xFFFFFFFF, anim));

        for (int i = 0; i < SLOT_COUNT; i++) {
            float hover = slotHover[i];
            double ang = Math.toRadians(-90.0 + i * (360.0 / SLOT_COUNT));
            float sx = cx + (float) Math.cos(ang) * ringR;
            float sy = cy + (float) Math.sin(ang) * ringR;

            float r = slotBase * (0.92f + 0.18f * hover) * (0.85f + 0.15f * anim);
            float size = r * 2f;

            int plate = lerpColor(0xFF222833, 0xFF3D6FE0, hover);
            Draw.rect(
                    sx - r,
                    sy - r,
                    size,
                    size,
                    withAlpha(plate, anim),
                    r
            );

            if (hover > 0.02f) {
                float gr = r + 5f * hover;
                Draw.rect(
                        sx - gr,
                        sy - gr,
                        gr * 2f,
                        gr * 2f,
                        withAlpha(0xFF5B8CFF, anim * hover * 0.22f),
                        gr
                );
            }

            EmotionType type = EMOTIONS[i];
            float icon = r * (1.15f + 0.20f * hover);
            Draw.texture(
                    type.getTexture(),
                    sx - icon / 2f,
                    sy - icon / 2f,
                    icon,
                    icon,
                    withAlpha(0xFFFFFFFF, anim * (0.70f + 0.30f * hover))
            );
        }

        String title = "Выбери нужную эмоцию";
        String sub = "Отпусти клавишу чтобы применить";
        float titleY = cy - ringR - slotBase - 36f - 24f * (1f - anim);
        TITLE_FONT.drawCentered(title, cx, titleY, 20f, withAlpha(0xFFFFFFFF, anim));
        SUB_FONT.drawCentered(sub, cx, titleY + 18f, 13f, withAlpha(0xFFA8B4C4, anim));

        float mx = (float) (cx + cursorX);
        float my = (float) (cy + cursorY);
        Draw.rect(
                mx - 3.5f,
                my - 3.5f,
                7f,
                7f,
                withAlpha(0xFFFFFFFF, anim * 0.9f),
                3.5f
        );

        Draw.flush();
    }

    private void poseDeb(float b, boolean hands, ModelPart head, ModelPart rightArm, ModelPart leftArm) {
        if (hands) {
            armXZ(rightArm, b, -0.70f, -2.00f);
            armXZ(leftArm, b, -0.70f, -2.00f);
        }
        head.pitch = MathHelper.lerp(b, head.pitch, 0.70f);
        head.yaw = MathHelper.lerp(b, head.yaw, 0.50f);
        head.roll = MathHelper.lerp(b, head.roll, 0.0f);
    }

    private void poseFloss(float b, long t, boolean hands, ModelPart body,
                           ModelPart rightArm, ModelPart leftArm,
                           ModelPart rightLeg, ModelPart leftLeg) {
        float action = tri(t, 250L);
        float rightSide = tri(t, 500L);
        float leftSide = tri(t + 250L, 500L);
        if (hands) {
            armXZ(rightArm, b, 0.50f - rightSide, 1.00f - 2.00f * action);
            armXZ(leftArm, b, 0.50f - leftSide, 1.50f - 2.00f * action);
        }
        body.roll = MathHelper.lerp(b, body.roll, 0.30f * action - 0.15f);
        rightLeg.originX = MathHelper.lerp(b, rightLeg.originX, -0.50f - 3.00f * action);
        leftLeg.originX = MathHelper.lerp(b, leftLeg.originX, 3.00f - 2.50f * action);
    }

    private void poseHello(float b, long t, boolean hands, ModelPart rightArm) {
        if (!hands) {
            return;
        }
        float wave = tri(t, 250L);
        armXZ(rightArm, b, -0.20f, 2.75f - 0.25f * wave);
    }

    private void poseHappy(float b, boolean hands, ModelPart rightArm, ModelPart leftArm) {
        if (!hands) {
            return;
        }
        armXZ(rightArm, b, 0.0f, 2.75f);
        armXZ(leftArm, b, 0.0f, -2.75f);
    }

    private void poseMasturbate(float b, long t, boolean hands, PlayerEntity player, ModelPart body,
                                ModelPart rightArm, ModelPart leftArm,
                                ModelPart rightLeg, ModelPart leftLeg) {
        float action = tri(t, 100L);
        if (!player.isSneaking()) {
            if (hands) {
                armXZ(rightArm, b, -0.30f - 0.70f * action, -0.55f);
                armXZ(leftArm, b, 0.30f, 1.0f);
            }
            body.pitch = MathHelper.lerp(b, body.pitch, -0.10f);
            rightLeg.originZ = MathHelper.lerp(b, rightLeg.originZ, -1.0f);
            leftLeg.originZ = MathHelper.lerp(b, leftLeg.originZ, -1.0f);
        } else if (hands) {
            armXZ(rightArm, b, 0.70f - 0.70f * action, -0.55f);
        }
    }

    private void poseGriddy(float b, long t, boolean hands, ModelPart head, ModelPart body,
                            ModelPart rightArm, ModelPart leftArm,
                            ModelPart rightLeg, ModelPart leftLeg) {
        float action = tri(t, 350L);
        float leg = tri(t, 300L);
        float eye = griddyEye(t);
        if (hands) {
            float armX = 1.0f - 1.5f * action - 3.5f * eye;
            armXZ(rightArm, b, armX, 0.5f - 0.75f * action);
            armXZ(leftArm, b, armX, -0.5f + 0.75f * action);
        }
        body.pitch = MathHelper.lerp(b, body.pitch, 0.2f - 0.05f * action);
        rightLeg.originZ = MathHelper.lerp(b, rightLeg.originZ, 2f - 0.5f * action);
        leftLeg.originZ = MathHelper.lerp(b, leftLeg.originZ, 2f - 0.5f * action);
        rightLeg.pitch = MathHelper.lerp(b, rightLeg.pitch, 0.3f * leg - 0.3f);
        leftLeg.pitch = MathHelper.lerp(b, leftLeg.pitch, -0.3f * leg - 0.1f);
        head.yaw = MathHelper.lerp(b, head.yaw, -0.4f + 0.4f * eye);
        head.pitch = MathHelper.lerp(b, head.pitch, -0.3f * eye);
        head.roll = MathHelper.lerp(b, head.roll, 0f);
    }

    private static void armXZ(ModelPart arm, float blend, float xRot, float zRot) {
        float a = MathHelper.clamp(blend, 0f, 1f);
        arm.pitch = MathHelper.lerp(a, arm.pitch, xRot);
        arm.roll = MathHelper.lerp(a, arm.roll, zRot);
    }

    private static float tri(long elapsed, long halfMs) {
        if (halfMs <= 0L) {
            return 0f;
        }
        long cycle = halfMs * 2L;
        long m = elapsed % cycle;
        return m < halfMs ? (m / (float) halfMs) : (2f - m / (float) halfMs);
    }

    private static float griddyEye(long elapsed) {
        long cycle = elapsed % 3100L;
        if (cycle < 2100L) {
            return 0f;
        }
        return tri(cycle - 2100L, 500L);
    }

    private static boolean canAnimateHands(PlayerEntity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null || target != mc.player || !mc.options.getPerspective().isFirstPerson();
    }

    private void stopLocalEmotion() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            activeEmotions.clear();
            return;
        }
        ActiveEmotion active = activeEmotions.remove(mc.player.getUuid());
        if (active != null && active.local) {
            mc.options.setPerspective(active.previousPerspective);
        }
    }

    private void startEmotion(PlayerEntity player, EmotionType type, boolean local, long startedAt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Perspective previous = Perspective.FIRST_PERSON;
        if (local) {
            ActiveEmotion prev = activeEmotions.get(player.getUuid());
            previous = prev != null && prev.local
                    ? prev.previousPerspective
                    : mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        }
        activeEmotions.put(player.getUuid(), new ActiveEmotion(type, startedAt, previous, local));
    }

    private static float approach(float current, float target, float dt, float speed) {
        float t = 1f - (float) Math.exp(-speed * dt);
        return current + (target - current) * t;
    }

    private static float easeOutCubic(float x) {
        float inv = 1f - x;
        return 1f - inv * inv * inv;
    }

    private static double angularDist(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }

    private static int withAlpha(int argb, float alpha) {
        int baseA = (argb >>> 24) & 0xFF;
        int a;
        if (baseA == 0) {
            a = MathHelper.clamp((int) (255 * MathHelper.clamp(alpha, 0f, 1f)), 0, 255);
        } else {
            a = MathHelper.clamp((int) (baseA * MathHelper.clamp(alpha, 0f, 1f)), 0, 255);
        }
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int lerpColor(int a, int b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        if (aa == 0) aa = 255;
        if (ba == 0) ba = 255;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private static final class ActiveEmotion {
        private final EmotionType type;
        private final long startedAt;
        private final Perspective previousPerspective;
        private final boolean local;

        private ActiveEmotion(EmotionType type, long startedAt, Perspective previousPerspective, boolean local) {
            this.type = type;
            this.startedAt = startedAt;
            this.previousPerspective = previousPerspective;
            this.local = local;
        }

        private long elapsed() {
            return System.currentTimeMillis() - startedAt;
        }

        private boolean isExpired() {
            return elapsed() >= type.getDurationMs();
        }

        private float getBlend() {
            long e = elapsed();
            long left = Math.max(0L, type.getDurationMs() - e);
            float in = MathHelper.clamp(e / FADE_IN_MS, 0f, 1f);
            float out = MathHelper.clamp(left / FADE_OUT_MS, 0f, 1f);
            float v = Math.min(in, out);
            return v * v * (3f - 2f * v);
        }
    }
}

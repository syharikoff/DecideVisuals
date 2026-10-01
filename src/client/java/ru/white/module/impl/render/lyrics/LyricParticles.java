package ru.white.module.impl.render.lyrics;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.utils.media.LyricLine;
import ru.white.utils.media.LyricWord;
import ru.white.utils.media.Lyrics;
import ru.white.utils.media.MediaPlayer;
import ru.white.utils.render.lyrics.LyricText3D;
import ru.white.utils.render.lyrics.LyricTextGeometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Показывает строки песни в 3D перед игроком. Порт {@code LyricParticles} из Kimiko,
 * режим «Строки» (по словам и раскладки вокруг игрока в Kimiko не использовались).
 *
 * Раз в кадр:
 * <ol>
 *   <li>{@link #collect} — по таймингам LRC решает, какие строки должны висеть
 *       сейчас, и создаёт для них {@link Held} (позиция, ориентация, тайминги);</li>
 *   <li>{@link #paint} — рисует каждую строку, размазывая глифы по краям появления
 *       и ухода, и подсвечивает слово, которое сейчас поётся ({@link Held#heat});</li>
 *   <li>{@link #stepDebris} + {@link #paintDebris} — режим «Распад»: строка разлетается
 *       на отдельные глифы, которые падают, отскакивают от земли и оседают.</li>
 * </ol>
 *
 * Текст рисуется в локальной плоскости напротив камеры, базис задаётся
 * {@link LyricText3D#plane}, поэтому всегда «лицом» к игроку.
 */
public final class LyricParticles {

    public static final String MODE_LINES    = "Строки";
    public static final String MODE_WORDS    = "Слова";
    public static final String LAYOUT_ARC     = "Стандарт";
    public static final String LAYOUT_SCATTER = "Вразброс";
    public static final String LAYOUT_CIRCLE  = "360";

    /** Растровый размер для раскладки: 1.0 равен 32 «пикселям» шрифта. */
    private static final float RASTER = 32.0f;

    private static final int MAX_DEBRIS = 512;
    private static final int FALL_STEPS = 24;
    private static final float MIN_ALPHA = 0.004f;
    private static final double GROUND_CLEARANCE = 0.012;

    private static final float GRAVITY = 14.0f;
    private static final float BOUNCE = 0.34f;
    private static final float GROUND_DRAG = 0.55f;
    private static final float REST_SPEED = 1.1f;
    private static final float FALL_TIMEOUT = 9.0f;
    private static final float DEBRIS_FADE = 0.5f;
    private static final float SETTLE_SECONDS = 0.9f;
    private static final float SETTLE_RATE = 11.0f;

    private static final float HEAT_LEAD = 60.0f;
    private static final float HEAT_RISE_SHARE = 0.35f;
    private static final float HEAT_RISE_MIN = 90.0f;
    private static final float HEAT_RISE_MAX = 220.0f;
    private static final float HEAT_FALL_SHARE = 0.55f;
    private static final float HEAT_FALL_MIN = 140.0f;
    private static final float HEAT_FALL_MAX = 320.0f;
    /** Хвост «разгорающегося» глифа после конца слова. */
    private static final long HEAT_TAIL = 420L;
    /** Насколько строка живёт после своего конца. */
    private static final long LINE_TAIL = 7000L;
    /** Насколько отдельное слово живёт после конца, если дальше нечего показывать. */
    private static final long WORD_TAIL = 140L;
    private static final long WORD_MIN_VISIBLE = 650L;
    private static final long WORD_MAX_VISIBLE = 2600L;

    private static final long SHATTER_GRACE = 1600L;
    private static final long REWIND_GRACE = 500L;
    private static final float FIT_MARGIN = 0.88f;
    private static final float CULL_FACTOR = 8.0f;
    /** Строки складываются стопкой с шагом 1.35 от размера строки. */
    private static final float LINE_STACK = 1.35f;
    /** Золотой угол — равномерно расставляет слова по кругу в режиме 360. */
    private static final float GOLDEN_ANGLE = 137.5f;
    private static final float SCATTER_LIFT = 2.4f;
    private static final float SCATTER_NEAR = 0.7f;
    private static final float SCATTER_FAR = 1.45f;

    private static final long ANNOUNCE_VISIBLE = 2600L;
    private static final long ANNOUNCE_EXIT = 520L;
    private static final float ANNOUNCE_TIMING = 260.0f;

    /** inTime min/max, inCascade min/max, outTime min/max, outCascade min/max. */
    private static final float[] LINE_BOUNDS = { 140f, 300f, 110f, 340f, 180f, 380f, 110f, 340f };
    private static final float[] WORD_BOUNDS = { 70f, 170f, 40f, 120f, 110f, 230f, 40f, 140f };

    /**
     * Смещение по горизонтали, подъём и дистанция для 8 слотов — разводит слова по
     * экрану, чтобы они не наезжали друг на друга.
     */
    private static final float[] WORD_SIDES = { -0.4f, 0.52f, -0.86f, 1.0f, -0.19f, 0.31f, -0.69f, 0.78f };
    private static final float[] WORD_LIFTS = { 0.0f, 0.5f, -0.35f, 0.65f, 0.25f, -0.2f, 0.55f, -0.45f };
    private static final float[] WORD_REACH = { 1.0f, 0.86f, 1.18f, 0.94f, 1.26f, 0.9f, 1.1f, 0.82f };

    private final List<Held> held = new ArrayList<>();
    private final List<Debris> debris = new ArrayList<>();
    private final Map<Integer, LyricTextGeometry.Layout> glyphCache = new HashMap<>();
    private final BlockPos.Mutable groundProbe = new BlockPos.Mutable();
    private final Random random = new Random();

    private List<Fragment> fragments = Collections.emptyList();
    private float presence;
    private long lastFrameNanos;
    private int lastIndex = -1;
    private int spawnCount;

    private String lastTrack = "";
    private String announcedTrack = "";
    private Lyrics lastLyrics;
    private boolean lastWords;
    private boolean lastModeCaptured;
    private String lastFont;

    private int lastVocalFrames = -1;
    private long vocalFramesStampMs;

    // ------------------------------------------------------------------ кадр

    public void render(EventRender3D event, MinecraftClient mc, Options options) {
        if (mc.player == null || mc.world == null || event.getProjectionMatrix() == null) {
            return;
        }
        if (!MediaPlayer.init()) {
            return;
        }
        MediaPlayer.tick();

        if (!options.fontName().equals(lastFont)) {
            lastFont = options.fontName();
            for (Held piece : held) {
                piece.invalidate();
            }
        }

        MediaPlayer.setLyricsOffsetMillis(options.syncMillis());
        float delta = advance();
        presence = approach(presence, MediaPlayer.isPlaying() ? 1.0f : 0.0f, delta);

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) {
            return;
        }
        long time = MediaPlayer.getLyricsTimeMillis();
        float partialTicks = event.getTickDelta();

        collect(mc, camera, time, partialTicks, options);

        for (int index = held.size() - 1; index >= 0; index--) {
            Held piece = held.get(index);
            boolean expired = options.fall()
                    ? piece.shattered || time >= piece.death + SHATTER_GRACE
                    : time >= piece.death;
            if (!expired && time >= piece.spawn - REWIND_GRACE) {
                continue;
            }
            held.remove(index);
        }

        stepDebris(delta, options);

        if ((held.isEmpty() && debris.isEmpty()) || presence <= MIN_ALPHA) {
            return;
        }

        LyricText3D.begin(event.getMatrixStack().peek().getPositionMatrix(),
                event.getProjectionMatrix(), camera.getCameraPos(),
                0.66f, 0.35f, options.glow(), 12.8f);

        Vec3d cameraPos = camera.getCameraPos();
        for (Held piece : held) {
            paint(piece, time, cameraPos, options, mc);
        }
        paintDebris(cameraPos, options);

        LyricText3D.end(options.throughWalls());
    }

    /** Полный сброс: трек сменился, мир перезагрузился или модуль выключили. */
    public void reset() {
        held.clear();
        debris.clear();
        glyphCache.clear();
        fragments = Collections.emptyList();
        presence = 0.0f;
        lastFrameNanos = 0L;
        lastIndex = -1;
        lastTrack = "";
        announcedTrack = "";
        lastLyrics = null;
        lastModeCaptured = false;
        lastVocalFrames = -1;
    }

    // ------------------------------------------------------------- появление

    private void collect(MinecraftClient mc, Camera camera, long time, float partialTicks, Options options) {
        String track = MediaPlayer.getTrack().display();
        Lyrics lyrics = MediaPlayer.getLyrics();
        boolean words = options.words();

        // Пересобирать куски надо и при смене трека/текста, и при переключении режима:
        // в режиме слов нарезка принципиально другая.
        boolean fresh = !track.equals(lastTrack) || lyrics != lastLyrics
                || !lastModeCaptured || lastWords != words;
        if (fresh) {
            lastTrack = track;
            lastLyrics = lyrics;
            lastWords = words;
            lastModeCaptured = true;
            lastIndex = -1;
            held.clear();
            fragments = (lyrics.isEmpty() || !lyrics.synced())
                    ? Collections.emptyList()
                    : split(lyrics, words);
        }

        // Название трека показываем один раз при смене — как «анонс»
        if (!track.equals(announcedTrack) && !track.isBlank()
                && MediaPlayer.getTrack().durationMillis() > 0L) {
            announcedTrack = track;
            held.add(announcement(mc, camera, track, time, partialTicks, options));
        }

        if (fragments.isEmpty()) {
            return;
        }

        // Перемотка назад — всё, что было видно, надо убрать
        if (lastIndex >= 0 && lastIndex < fragments.size()
                && time < fragments.get(lastIndex).startMillis - REWIND_GRACE) {
            held.clear();
            lastIndex = -1;
        }

        int alive = Math.max(1, options.limit());
        while (lastIndex + 1 < fragments.size() && time >= fragments.get(lastIndex + 1).startMillis) {
            lastIndex++;
            Fragment candidate = fragments.get(lastIndex);
            Timing timing = timing(candidate, handoff(lastIndex), words);
            if (time >= timing.death) {
                continue;
            }
            trim(time, alive);
            held.add(create(mc, camera, candidate, timing, words, partialTicks, options));
        }
    }

    /** Убирает лишние строки, оставляя не больше {@code alive} ещё не начавших гаснуть. */
    private void trim(long time, int alive) {
        int live = 0;
        for (Held piece : held) {
            if (piece.fadeFrom > time) {
                live++;
            }
        }
        while (live >= alive) {
            Held target = null;
            for (Held piece : held) {
                if (piece.fadeFrom <= time || (target != null && piece.spawn >= target.spawn)) {
                    continue;
                }
                target = piece;
            }
            if (target == null) {
                break;
            }
            target.retire(time);
            live--;
        }
        while (held.size() > alive * 2) {
            held.remove(0);
        }
    }

    private Held announcement(MinecraftClient mc, Camera camera, String title, long time,
                               float partialTicks, Options options) {
        Fragment fragment = new Fragment(title, Collections.emptyList(), time, time + ANNOUNCE_VISIBLE);
        Timing timing = new Timing(time, time + ANNOUNCE_VISIBLE, time + ANNOUNCE_VISIBLE + ANNOUNCE_EXIT,
                ANNOUNCE_TIMING, ANNOUNCE_TIMING, ANNOUNCE_TIMING, ANNOUNCE_TIMING);
        // Анонс всегда идёт целиком, режим слов тут не применяется
        return create(mc, camera, fragment, timing, false, partialTicks, options);
    }

    private long handoff(int index) {
        if (index + 1 < fragments.size()) {
            return fragments.get(index + 1).startMillis;
        }
        Fragment last = fragments.get(index);
        return Math.max(last.endMillis, last.startMillis + 1L);
    }

    /**
     * Гейт по реальному вокалу: если DLL не отдаёт уровень, берём 1.0 и полагаемся
     * на тайминги слов. Если счётчик кадров замер — тоже 1.0, иначе текст погаснет
     * из-за одной потерянной выборки.
     */
    private float currentVocalGate() {
        float measured = MediaPlayer.vocalLevel();
        if (measured < 0.0f) {
            return 1.0f;
        }
        int frames = MediaPlayer.vocalFrames();
        long now = System.currentTimeMillis();
        if (frames != lastVocalFrames) {
            lastVocalFrames = frames;
            vocalFramesStampMs = now;
        } else if (now - vocalFramesStampMs > 1200L) {
            return 1.0f;
        }
        return Math.min(1.0f, 0.45f + measured * 1.2f);
    }

    private Held create(MinecraftClient mc, Camera camera, Fragment fragment, Timing timing,
                        boolean words, float partialTicks, Options options) {
        int index = spawnCount++;
        int slot = Math.floorMod(index, WORD_SIDES.length);

        float offset;
        float lift = options.height();
        float reach = options.radius();

        if (!words) {
            // Строки: чередуем слева/справа и складываем стопкой вверх
            int row = Math.floorMod(index, Math.max(1, options.limit()));
            offset = (slot & 1) == 0 ? -1.0f : 1.0f;
            lift += (options.limit() - 1 - row) * options.size() * LINE_STACK;
        } else if (LAYOUT_CIRCLE.equals(options.layout())) {
            // 360: равномерно по кругу вокруг игрока, угол через золотой угол
            lift += WORD_LIFTS[slot];
            reach *= WORD_REACH[slot];
            offset = Float.NaN;
        } else if (LAYOUT_SCATTER.equals(options.layout())) {
            // Вразброс: случайная высота и дистанция
            offset = (random.nextFloat() - 0.5f) * 2.0f;
            lift += (random.nextFloat() - 0.5f) * SCATTER_LIFT;
            reach *= SCATTER_NEAR + random.nextFloat() * (SCATTER_FAR - SCATTER_NEAR);
        } else {
            // Стандарт: восемь фиксированных позиций вокруг игрока
            offset = WORD_SIDES[slot];
            lift += WORD_LIFTS[slot];
            reach *= WORD_REACH[slot];
        }

        // NaN означает «угол задан явно градусами», иначе это доля свободного места
        // по бокам кадра — так строка гарантированно влезает в экран.
        offset = Float.isNaN(offset)
                ? (float) index * GOLDEN_ANGLE % 360.0f
                : offset * sideRoom(options, fragment.text, reach);

        float radians = (float) Math.toRadians(camera.getYaw() + offset);
        float directionX = -((float) Math.sin(radians));
        float directionZ = (float) Math.cos(radians);

        ClientPlayerEntity player = mc.player;
        if (player == null) {
            return new Held(fragment, timing, 0.0, 0.0, 0.0, 0.0f, 0.0f, 1.0f);
        }
        Vec3d base = player.getLerpedPos(partialTicks);
        return new Held(fragment, timing,
                base.x + directionX * reach, base.y + lift, base.z + directionZ * reach,
                -directionZ, directionX, Math.max(0.5f, reach));
    }

    // ---------------------------------------------------------------- отрисовка

    private void paint(Held piece, long time, Vec3d camera, Options options, MinecraftClient mc) {
        LyricTextGeometry.Layout layout =
                LyricTextGeometry.layout(options.fontName(), piece.fragment.text, RASTER);
        if (layout.empty()) {
            return;
        }

        double dx = piece.x - camera.x;
        double dy = piece.y - camera.y;
        double dz = piece.z - camera.z;
        float cull = options.radius() * CULL_FACTOR;
        double range = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (range > cull) {
            return;
        }

        float lineHeight = layout.height();
        float lineWidth = Math.max(1.0f, layout.width());

        // Гасим текст у края зоны отрисовки, чтобы он не щёлкал при движении камеры
        float fade = clamp((float) ((cull - range) / (cull * 0.2f)), 0.0f, 1.0f);
        float alpha = options.opacity() * fade * presence;
        if (alpha <= MIN_ALPHA) {
            return;
        }

        float scale = options.size() / lineHeight;
        float screenWidth = visibleWidth(piece.reach) * FIT_MARGIN;
        float worldWidth = lineWidth * scale;
        if (screenWidth > 0.0f && worldWidth > screenWidth) {
            scale *= screenWidth / worldWidth;
        }

        LyricText3D.plane(piece.x, piece.y, piece.z,
                piece.rightX, 0.0f, piece.rightZ,
                0.0f, 1.0f, 0.0f,
                scale, lineWidth * 0.5f, lineHeight * 0.5f);

        piece.measure(options.fontName());
        float vocalGate = currentVocalGate();
        Timing timing = piece.timing;

        float blurScale = lineHeight;
        float lift = 0.16f * lineHeight;

        if (options.fall() && time >= piece.shatterAt()) {
            if (!piece.shattered) {
                piece.shattered = true;
                piece.death = time;
                shatter(mc, piece, layout, scale, lineWidth, lineHeight);
            }
            return;
        }

        for (LyricTextGeometry.Quad quad : layout.quads()) {
            float center = (quad.x0() + quad.x1()) * 0.5f;
            // Каскад: глифы появляются слева направо, а не разом
            float phase = clamp(center / lineWidth, 0.0f, 1.0f);
            float appear = clamp((time - (piece.spawn + phase * timing.inCascade)) / timing.inTime, 0.0f, 1.0f);
            float vanish = options.fall() ? 0.0f
                    : clamp((time - (piece.fadeFrom + phase * timing.outCascade)) / timing.outTime, 0.0f, 1.0f);

            float fadeIn = 1.0f - (1.0f - appear) * (1.0f - appear) * (1.0f - appear);
            float settled = settle(appear);
            float leaving = vanish * vanish;
            float visible = fadeIn * (1.0f - leaving);
            if (visible <= MIN_ALPHA) {
                continue;
            }

            float rest = 1.0f - presence;
            float heat = piece.heat(center, time) * vocalGate;

            // Смещение вверх-вниз: оседание, уход, покой и «подпрыгивание» на вокале
            float shift = 0.3f * lineHeight * (1.0f - settled)
                    - 0.34f * lineHeight * leaving
                    + 0.35f * lineHeight * rest
                    + lift * heat * (1.0f - leaving);

            // Размытие тем больше, чем ближе глиф к «невидимому» состоянию
            float smear = (0.2f * (1.0f - appear) + 0.26f * vanish + 0.5f * rest) * blurScale;

            LyricText3D.glyph(layout, quad, 0.0f, shift, smear, heat, alpha * visible);
        }
    }

    /** Обратная ease-функция: 0 в начале, 1 с лёгким перелётом через ноль. */
    private float settle(float value) {
        float t = value - 1.0f;
        return 1.0f + 2.1f * t * t * t + 1.1f * t * t;
    }

    // ------------------------------------------------------------------- распад

    private void shatter(MinecraftClient mc, Held piece, LyricTextGeometry.Layout layout,
                         float scale, float lineWidth, float lineHeight) {
        ClientWorld world = mc.world;
        if (world == null) {
            return;
        }
        float outX = -piece.rightZ;
        float outZ = piece.rightX;

        int count = layout.quads().size();
        float step = Math.min(0.06f, 2.2f / Math.max(1, count - 1));

        for (int order = 0; order < count; order++) {
            if (debris.size() >= MAX_DEBRIS) {
                return;
            }
            LyricTextGeometry.Quad quad = layout.quads().get(order);
            float centerX = (quad.x0() + quad.x1()) * 0.5f;
            float centerY = (quad.y0() + quad.y1()) * 0.5f;
            float localX = centerX - lineWidth * 0.5f;
            float localY = lineHeight * 0.5f - centerY;

            double worldX = piece.x + piece.rightX * localX * scale;
            double worldY = piece.y + localY * scale;
            double worldZ = piece.z + piece.rightZ * localX * scale;

            float sideways = (random.nextFloat() - 0.5f) * 0.55f;
            float outward = (0.25f + random.nextFloat() * 0.6f) * 0.9f;
            float peel = 3.2f * (0.7f + random.nextFloat() * 0.6f) * (random.nextBoolean() ? 1.0f : -1.0f);

            debris.add(new Debris(quad.codePoint(), worldX, worldY, worldZ,
                    piece.rightX * sideways + outX * outward,
                    -0.05f + random.nextFloat() * 0.45f,
                    piece.rightZ * sideways + outZ * outward,
                    piece.rightX, piece.rightZ, peel,
                    (quad.y1() - quad.y0()) * 0.5f * scale,
                    groundBelow(world, worldX, worldY, worldZ),
                    scale,
                    order * step + random.nextFloat() * step * 0.3f));
        }
    }

    /** Ищет поверхность под точкой; -бесконечность = земли нет, глиф падает вечно. */
    private double groundBelow(ClientWorld world, double x, double y, double z) {
        int blockX = MathHelper.floor(x);
        int blockZ = MathHelper.floor(z);
        int startY = MathHelper.floor(y);
        for (int blockY = startY; blockY >= startY - FALL_STEPS; blockY--) {
            groundProbe.set(blockX, blockY, blockZ);
            BlockState state = world.getBlockState(groundProbe);
            if (state.isAir()) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape((BlockView) world, groundProbe);
            if (shape.isEmpty()) {
                continue;
            }
            double top = blockY + shape.getMax(Direction.Axis.Y);
            if (top <= y) {
                return top;
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    private void stepDebris(float delta, Options options) {
        if (debris.isEmpty()) {
            return;
        }
        if (!options.fall()) {
            debris.clear();
            return;
        }

        for (int index = debris.size() - 1; index >= 0; index--) {
            Debris piece = debris.get(index);

            if (piece.delay > 0.0f) {
                // Ступенчатая задержка: глифы отваливаются не одновременно
                piece.delay -= delta;
                float ready = clamp(1.0f - piece.delay / piece.delayFull, 0.0f, 1.0f);
                piece.spin = 0.13f * ready * ready * Math.signum(piece.spinRate);
                continue;
            }

            piece.age += delta;
            if (piece.grounded) {
                piece.rest += delta;
                piece.spin += (piece.spinTarget - piece.spin) * clamp(SETTLE_RATE * delta, 0.0f, 1.0f);
                piece.y = piece.ground + piece.restHeight();
            } else {
                piece.velocityY -= GRAVITY * delta;
                piece.x += piece.velocityX * delta;
                piece.y += piece.velocityY * delta;
                piece.z += piece.velocityZ * delta;
                piece.spin += piece.spinRate * delta;

                double floor = piece.ground + piece.restHeight();
                if (!Double.isInfinite(piece.ground) && piece.y <= floor) {
                    piece.y = floor;
                    if (-piece.velocityY <= REST_SPEED) {
                        // Удар был слабый — глиф ложится на землю
                        piece.grounded = true;
                        piece.velocityX = 0.0f;
                        piece.velocityY = 0.0f;
                        piece.velocityZ = 0.0f;
                        piece.spinRate = 0.0f;
                        piece.spinTarget = flatAngle(piece.spin);
                    } else {
                        piece.velocityY = -piece.velocityY * BOUNCE;
                        piece.velocityX *= GROUND_DRAG;
                        piece.velocityZ *= GROUND_DRAG;
                        piece.spinRate = 0.0f;
                    }
                }
            }

            if (debrisFade(piece) <= MIN_ALPHA || (!piece.grounded && !(piece.age > FALL_TIMEOUT))) {
                continue;
            }
            debris.remove(index);
        }
    }

    private float debrisFade(Debris piece) {
        float settled = piece.grounded
                ? clamp(1.0f - (piece.rest - SETTLE_SECONDS) / DEBRIS_FADE, 0.0f, 1.0f)
                : 1.0f;
        float timeout = piece.grounded
                ? 1.0f
                : clamp((FALL_TIMEOUT - piece.age) / DEBRIS_FADE, 0.0f, 1.0f);
        return Math.min(settled, timeout);
    }

    private void paintDebris(Vec3d camera, Options options) {
        if (debris.isEmpty()) {
            return;
        }
        glyphCache.clear();
        float cull = options.radius() * CULL_FACTOR;

        for (Debris piece : debris) {
            float fade = debrisFade(piece);
            if (fade <= MIN_ALPHA) {
                continue;
            }
            double dx = piece.x - camera.x;
            double dy = piece.y - camera.y;
            double dz = piece.z - camera.z;
            double range = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (range > cull) {
                continue;
            }
            float alpha = options.opacity() * presence * fade
                    * clamp((float) ((cull - range) / (cull * 0.2f)), 0.0f, 1.0f);
            if (alpha <= MIN_ALPHA) {
                continue;
            }

            LyricTextGeometry.Layout layout = glyphCache.get(piece.codePoint);
            if (layout == null) {
                layout = LyricTextGeometry.layout(options.fontName(),
                        new String(Character.toChars(piece.codePoint)), RASTER);
                glyphCache.put(piece.codePoint, layout);
            }
            if (layout.empty()) {
                continue;
            }

            LyricTextGeometry.Quad quad = layout.quads().get(0);
            float cos = (float) Math.cos(piece.spin);
            float sin = (float) Math.sin(piece.spin);

            LyricText3D.plane(piece.x, piece.y, piece.z,
                    piece.rightX, 0.0f, piece.rightZ,
                    -piece.rightZ * sin, cos, piece.rightX * sin,
                    piece.scale, (quad.x0() + quad.x1()) * 0.5f, (quad.y0() + quad.y1()) * 0.5f);
            LyricText3D.glyph(layout, quad, 0.0f, 0.0f, 0.0f, 0.0f, alpha);
        }
    }

    // ----------------------------------------------------------------- геометрия

    private float halfFov() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return 0.0f;
        }
        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) {
            return 0.0f;
        }
        double vertical = Math.toRadians(Math.max(30.0, mc.options.getFov().getValue()));
        double aspect = (double) width / (double) height;
        return (float) Math.atan(Math.tan(vertical * 0.5) * aspect);
    }

    /** Ширина видимой области на расстоянии {@code distance} — для подгонки размера текста. */
    private float visibleWidth(double distance) {
        float half = halfFov();
        return half <= 0.0f ? 0.0f : (float) (2.0 * distance * Math.tan(half));
    }

    /** Сколько градусов свободно по бокам, чтобы строка целиком влезла в кадр. */
    private float sideRoom(Options options, String text, float reach) {
        float bound = (float) Math.toDegrees(halfFov()) * FIT_MARGIN;
        if (bound <= 0.0f || reach <= 0.0f) {
            return 0.0f;
        }
        LyricTextGeometry.Layout layout = LyricTextGeometry.layout(options.fontName(), text, RASTER);
        if (layout.empty()) {
            return 0.0f;
        }
        float worldWidth = layout.width() * (options.size() / layout.height());
        float visible = visibleWidth(reach) * FIT_MARGIN;
        if (visible > 0.0f && worldWidth > visible) {
            worldWidth = visible;
        }
        float half = (float) Math.toDegrees(Math.atan(worldWidth * 0.5f / reach));
        return Math.max(0.0f, bound - half);
    }

    private float advance() {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 0.0f : clamp((float) (now - lastFrameNanos) / 1.0E9f, 0.0f, 0.25f);
        lastFrameNanos = now;
        return delta;
    }

    // ------------------------------------------------------------------ утилиты

    /** Округляет угол до кратного 90° — «упавший» глиф должен лежать плашмя. */
    private static float flatAngle(float spin) {
        float quarter = 1.5707964f;
        int steps = Math.round((spin - quarter) / (float) Math.PI);
        return quarter + steps * (float) Math.PI;
    }

    /**
     * Нарезка текста на куски.
     *
     * В режиме слов короткие слова (предлоги вроде «to» или «не») склеиваются со
     * следующим: по отдельности они висят мелкими буквами и читаются хуже. Смещения
     * слов пересчитываются от начала получившегося куска, чтобы они совпадали с его
     * собственным текстом.
     */
    private static List<Fragment> split(Lyrics lyrics, boolean words) {
        List<Fragment> result = new ArrayList<>();
        for (LyricLine line : lyrics.lines()) {
            String text = line.text();
            if (text.isBlank()) {
                continue;
            }
            List<LyricWord> source = line.words();
            if (!words || source.isEmpty()) {
                result.add(new Fragment(text, source, line.startMillis(), line.endMillis()));
                continue;
            }
            for (int index = 0; index < source.size(); ) {
                int take = index + 1 < source.size() && source.get(index).text().length() <= 4 ? 2 : 1;
                LyricWord first = source.get(index);
                LyricWord last = source.get(index + take - 1);

                List<LyricWord> chunk = new ArrayList<>(take);
                for (int offset = 0; offset < take; offset++) {
                    LyricWord word = source.get(index + offset);
                    chunk.add(new LyricWord(word.startMillis(), word.endMillis(), word.text(),
                            word.begin() - first.begin(), word.end() - first.begin()));
                }
                result.add(new Fragment(text.substring(first.begin(), last.end()),
                        List.copyOf(chunk), first.startMillis(), last.endMillis()));
                index += take;
            }
        }
        return List.copyOf(result);
    }

    /**
     * Раскладывает появление и уход куска по длительности.
     *
     * У строки границы шире: она живёт дольше. Слово появляется быстрее и живёт
     * недолго, иначе на экране залипнет каша из слов.
     */
    private static Timing timing(Fragment fragment, long handoff, boolean words) {
        float[] bounds = words ? WORD_BOUNDS : LINE_BOUNDS;
        float span = Math.max(1.0f, fragment.endMillis - fragment.startMillis);
        float inTime = clamp(span * 0.22f, bounds[0], bounds[1]);
        float inCascade = clamp(span * 0.3f, bounds[2], bounds[3]);
        float outTime = clamp(span * 0.24f, bounds[4], bounds[5]);
        float outCascade = clamp(span * 0.26f, bounds[6], bounds[7]);

        long spawn = fragment.startMillis;
        int exit = Math.round(outTime + outCascade);
        long entrance = spawn + Math.round(inTime + inCascade);
        long visibleEnd = words
                ? Math.min(Math.max(fragment.endMillis + WORD_TAIL, spawn + WORD_MIN_VISIBLE), spawn + WORD_MAX_VISIBLE)
                : Math.min(handoff - exit, spawn + LINE_TAIL);
        visibleEnd = Math.max(visibleEnd, entrance);
        return new Timing(spawn, visibleEnd, visibleEnd + exit, inTime, inCascade, outTime, outCascade);
    }

    private static float clamp(float value, float low, float high) {
        return value < low ? low : (value > high ? high : value);
    }

    private static float approach(float current, float target, float delta) {
        if (delta <= 0.0f) {
            return current;
        }
        return current + (target - current) * (1.0f - (float) Math.exp(-delta * 1000.0f / 420.0f));
    }

    // -------------------------------------------------------------------- модели

    /** Строка текста со своим таймингом и позицией в мире. */
    private static final class Fragment {
        final String text;
        final List<LyricWord> words;
        final long startMillis;
        final long endMillis;

        Fragment(String text, List<LyricWord> words, long startMillis, long endMillis) {
            this.text = text;
            this.words = words;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
        }
    }

    private record Timing(long spawn, long visibleEnd, long death,
                          float inTime, float inCascade, float outTime, float outCascade) {}

    /** Строка, которая сейчас висит в мире. */
    private static final class Held {

        final Fragment fragment;
        final Timing timing;
        final double x, y, z;
        final float rightX, rightZ, reach;
        final long spawn;

        long fadeFrom;
        long death;
        boolean shattered;

        /** Горизонтальные границы слов в пикселях растра — считаются один раз на шрифт. */
        private float[] wordStart;
        private float[] wordEnd;
        private float[] wordRise;
        private float[] wordFall;
        private String measured;

        Held(Fragment fragment, Timing timing, double x, double y, double z,
             float rightX, float rightZ, float reach) {
            this.fragment = fragment;
            this.timing = timing;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rightX = rightX;
            this.rightZ = rightZ;
            this.reach = reach;
            this.spawn = timing.spawn;
            this.fadeFrom = timing.visibleEnd;
            this.death = timing.death;
        }

        /** Когда строка «отваливается» на глифы: сразу после конца пения. */
        long shatterAt() {
            long sung = fragment.endMillis + HEAT_TAIL;
            return Math.max(fadeFrom, Math.min(sung, fadeFrom + SHATTER_GRACE));
        }

        void invalidate() {
            this.measured = null;
        }

        /** Досрочно отправить строку в уход, чтобы освободить место новой. */
        void retire(long time) {
            long from = Math.max(spawn, time);
            if (from >= fadeFrom) {
                return;
            }
            this.fadeFrom = from;
            this.death = from + Math.round(timing.outTime + timing.outCascade);
        }

        void measure(String fontName) {
            if (fontName.equals(measured)) {
                return;
            }
            this.measured = fontName;
            List<LyricWord> words = fragment.words;
            int count = words.size();
            if (count == 0) {
                return;
            }
            float[] starts = new float[count];
            float[] ends = new float[count];
            float[] rises = new float[count];
            float[] falls = new float[count];
            String text = fragment.text;
            for (int index = 0; index < count; index++) {
                LyricWord word = words.get(index);
                starts[index] = LyricTextGeometry.width(fontName, text.substring(0, word.begin()), RASTER);
                ends[index] = LyricTextGeometry.width(fontName, text.substring(0, word.end()), RASTER);
                float span = Math.max(1.0f, word.durationMillis());
                rises[index] = MathHelper.clamp(span * HEAT_RISE_SHARE, HEAT_RISE_MIN, HEAT_RISE_MAX);
                falls[index] = MathHelper.clamp(span * HEAT_FALL_SHARE, HEAT_FALL_MIN, HEAT_FALL_MAX);
            }
            this.wordStart = starts;
            this.wordEnd = ends;
            this.wordRise = rises;
            this.wordFall = falls;
        }

        /** Насколько сильно «горит» глиф с центром {@code center} прямо сейчас, 0..1. */
        float heat(float center, long time) {
            float[] starts = wordStart;
            float[] ends = wordEnd;
            float[] rises = wordRise;
            float[] falls = wordFall;
            if (starts == null || ends == null || rises == null || falls == null) {
                return 0.0f;
            }
            List<LyricWord> words = fragment.words;
            for (int index = 0; index < words.size(); index++) {
                if (center < starts[index] || center > ends[index]) {
                    continue;
                }
                LyricWord word = words.get(index);
                // Слегка опережаем слово: человек начинает петь раньше, чем срабатывает метка
                float up = MathHelper.clamp((time - (word.startMillis() - HEAT_LEAD)) / rises[index], 0.0f, 1.0f);
                float down = MathHelper.clamp((time - word.endMillis()) / falls[index], 0.0f, 1.0f);
                return ease(up) * (1.0f - ease(down));
            }
            return 0.0f;
        }

        private static float ease(float value) {
            return value * value * (3.0f - 2.0f * value);
        }
    }

    /** Один отвалившийся глиф: падает, отскакивает, ложится на землю. */
    private static final class Debris {

        final int codePoint;
        final float rightX, rightZ, half, scale, delayFull;
        final double ground;

        double x, y, z;
        float velocityX, velocityY, velocityZ;
        float spinRate, spin, spinTarget;
        float delay, age, rest;
        boolean grounded;

        Debris(int codePoint, double x, double y, double z,
               float velocityX, float velocityY, float velocityZ,
               float rightX, float rightZ, float spinRate,
               float half, double ground, float scale, float delay) {
            this.codePoint = codePoint;
            this.x = x;
            this.y = y;
            this.z = z;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.velocityZ = velocityZ;
            this.rightX = rightX;
            this.rightZ = rightZ;
            this.spinRate = spinRate;
            this.half = half;
            this.ground = ground;
            this.scale = scale;
            this.delay = delay;
            this.delayFull = Math.max(delay, 1.0E-4f);
        }

        /** Высота центра глифа, когда он лежит на земле: полуразмах, повёрнутый по spin. */
        double restHeight() {
            return half * Math.abs(Math.cos(spin)) + GROUND_CLEARANCE;
        }
    }

    // ------------------------------------------------------------------- настройки

    public record Options(boolean words,
                          String layout,
                          String fontName,
                          float size,
                          float opacity,
                          boolean throughWalls,
                          float glow,
                          float radius,
                          float height,
                          int limit,
                          long syncMillis,
                          boolean fall) {}
}

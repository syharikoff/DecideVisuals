package ru.decide.module.impl.display.interfaceimpl;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.math.MathHelper;
import ru.decide.manager.event_impl.MousePressEvent;
import ru.decide.module.api.settings.impl.DragSetting;
import ru.decide.module.impl.display.InterFace;
import ru.decide.theme.ThemeColor;
import ru.decide.utils.animation.Animation;
import ru.decide.utils.animation.Easings;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.render.MediaTextureUtil;
import ru.decide.utils.render.RenderUtil;
import ru.decide.utils.render.Scissor;
import ru.decide.utils.render.font.Font;
import ru.decide.utils.render.font.Fonts;
import ru.decide.utils.taskript.StopWatch;

import java.awt.Color;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicHud implements element {

    private static final MediaInfo EMPTY = new MediaInfo("Нет трека", "—", new byte[0], 0, 0, false);

    // --- Переменная для логики масштабирования ---
    private static float S = 1.0F;

    private ExecutorService executor;

    private synchronized ExecutorService executor() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "white-media-hud");
                t.setDaemon(true);
                return t;
            });
        }
        return executor;
    }

    private final StopWatch lastMedia = new StopWatch();
    private final Animation openAnimation = new Animation();

    private volatile MediaInfo mediaInfo = EMPTY;
    private volatile IMediaSession session;

    private float progressWidth;
    private float playX, playY, playW, playH;
    private float prevX, prevY, prevW, prevH;
    private float nextX, nextY, nextW, nextH;

    public void onTick() {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (mc.player.age % 5 != 0) {
            return;
        }

        executor().submit(() -> {
            try {
                IMediaSession current = MediaPlayerInfo.Instance.getMediaSessions().stream()
                        .max(Comparator.comparingInt(s -> s.getMedia().getPlaying() ? 1 : 0))
                        .orElse(null);

                session = current;
                if (current == null) {
                    return;
                }

                MediaInfo info = current.getMedia();
                if (info.getTitle().isEmpty() && info.getArtist().isEmpty()) {
                    return;
                }

                mediaInfo = info;
                lastMedia.reset();
                MediaTextureUtil.updateArtwork(info.getArtworkPng());
            } catch (Throwable ignored) {
            }
        });
    }

    public boolean onMouseClick(MousePressEvent event) {
        if (event.getAction() != 1 || event.getButton() != 0) {
            return false;
        }
        if (openAnimation.get() <= 0.01f) {
            return false;
        }

        double mx = event.getMouseX();
        double my = event.getMouseY();
        IMediaSession active = session;
        if (active == null) {
            return false;
        }

        if (inRect(mx, my, prevX, prevY, prevW, prevH)) {
            active.previous();
            return true;
        }
        if (inRect(mx, my, playX, playY, playW, playH)) {
            active.playPause();
            return true;
        }
        if (inRect(mx, my, nextX, nextY, nextW, nextH)) {
            active.next();
            return true;
        }
        return false;
    }

    @Override
    public void onRender(DragSetting dragSetting, InterFace interFace) {
        boolean hasMedia = !lastMedia.finished(2000);

        openAnimation.update();
        openAnimation.run(1, 0.12f, Easings.SINE_OUT, true);
        float alpha = openAnimation.get();
        if (alpha <= 0.01f) {
            dragSetting.active = false;
            return;
        }

        dragSetting.active = true;

        // Обновляем множитель масштаба
        S = InterFace.getInstance().sizeHud.getValue();

        // Динамические переменные с учетом скейла
        float WIDTH = 79f * S;
        float HEIGHT = 41f * S;
        float PAD = 4f * S;
        float RADIUS = 5f * S;
        float ART_SIZE = 19f * S;
        float ART_RADIUS = 4f * S;

        MediaInfo info = mediaInfo;
        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        RenderUtil.Render2D.glow(x, y, WIDTH, HEIGHT, ColorUtil.replAlpha(ColorUtil.getColor(0),0.1F * alpha), RADIUS, 12, 1);

        RenderUtil.Blur.blur(x, y, WIDTH, HEIGHT, alpha, RADIUS, ColorUtil.replAlpha(ColorUtil.background(), Math.min(InterFace.getInstance().alphaHUD.getValue() * alpha + 0.35F, 1.0F)));

        float artX = x + PAD;
        float artY = y + PAD;
        if (MediaTextureUtil.hasArtwork()) {
            RenderUtil.Images.texture(
                    MediaTextureUtil.ARTWORK_ID,
                    artX, artY, ART_SIZE, ART_SIZE,
                    1f, ART_RADIUS,
                    ColorUtil.getColor(255, alpha)
            );
        } else {
            RenderUtil.Render2D.rect(
                    artX, artY, ART_SIZE, ART_SIZE,
                    ColorUtil.multAlpha(new Color(30, 30, 36).getRGB(), alpha),
                    ART_RADIUS
            );
            Fonts.icon.drawCentered("M", artX + ART_SIZE / 2f, artY + ART_SIZE / 2f - 1.75f * S, 3.75f * S, ThemeColor.getHudColor(alpha));
        }

        float textX = artX + ART_SIZE + 5f * S;
        float textW = x + WIDTH - PAD - textX;

        Font titleFont = Fonts.sf_regular;
        Font metaFont = Fonts.sf_regular;

        String title = info.getTitle().isEmpty() ? "Нет трека" : info.getTitle();
        String artist = info.getArtist().isEmpty() ? "—" : info.getArtist();

        Scissor.enable(textX, artY, textW, ART_SIZE);
        titleFont.drawFadingText(title, textX, artY + 2F * S, textW, ColorUtil.getColor(255,alpha), 7f * S);
        metaFont.drawFadingText(artist, textX, artY + 10f * S, textW, ColorUtil.getColor(180,alpha), 6f * S);
        Scissor.disable();

        long duration = Math.max(1, info.getDuration());
        long position = MathHelper.clamp(info.getPosition(), 0, duration);

        String posText = formatDuration(position);
        String durText = formatDuration(duration);
        float timeSize = 6f * S;

        float progressRowY = artY + ART_SIZE + 2f * S;
        float posW = metaFont.getWidth(posText, timeSize);
        float durW = metaFont.getWidth(durText, timeSize);

        int timeColor = ColorUtil.getColor(150,alpha);
        metaFont.draw(posText, x + PAD, progressRowY + 0.5F * S, timeSize, timeColor);
        metaFont.draw(durText, x + WIDTH - PAD - durW, progressRowY + 0.5F * S, timeSize, timeColor);

        float barX = x + PAD + posW + 2.5f * S;
        float barW = x + WIDTH - PAD - durW - 2.5f * S - barX;
        float barY = progressRowY + 3.25F * S;
        float barH = 1.25f * S;

        float targetProgress = barW * (position / (float) duration);
        progressWidth = MathHelper.lerp(0.18f, progressWidth, MathHelper.clamp(targetProgress, 0, barW));

        int trackColor = ColorUtil.multAlpha(new Color(0).getRGB(), alpha * 0.1F);
        RenderUtil.Render2D.rect(barX, barY, barW, barH, trackColor, barH / 2f);

        if (progressWidth > 0.5f) {
            int[] progressColors = {
                    ColorUtil.multAlpha(ColorUtil.getClientColor1(1), alpha),
                    ColorUtil.multAlpha(ColorUtil.getClientColor1(1),alpha),
                    ColorUtil.multAlpha(ColorUtil.getClientColor1(1), alpha),
                    ColorUtil.multAlpha(ColorUtil.getClientColor1(1), alpha)
            };
            RenderUtil.Render2D.gradientRect(barX, barY, progressWidth, barH, progressColors, barH / 2f);
        }

        float controlsY = y + HEIGHT - PAD - 5f * S;
        float centerX = x + WIDTH / 2f;
        float controlSpacing = 10f * S;
        int controlColor = ThemeColor.getHudColor(alpha);
        int controlColor2 = ThemeColor.getHudColor(alpha * 0.6f);

        prevW = 6f * S;
        prevH = 5f * S;
        prevX = centerX - controlSpacing - prevW / 2f;
        prevY = controlsY;

        playW = 6f * S;
        playH = 5f * S;
        playX = centerX - playW / 2f;
        playY = controlsY;

        nextW = 6f * S;
        nextH = 5f * S;
        nextX = centerX + controlSpacing - nextW / 2f;
        nextY = controlsY;

        Font icon = Fonts.icon;
        float iconSize = 4.5f * S;

        icon.drawCentered("O", prevX + prevW / 2f, controlsY + 0.75f * S, iconSize, controlColor2);
        icon.drawCentered(info.getPlaying() ? "V" : "M", playX + playW / 2f, controlsY + 0.75f * S, iconSize, controlColor);
        icon.drawCentered("N", nextX + nextW / 2f, controlsY + 0.75f * S, iconSize, controlColor2);

        dragSetting.size.set(WIDTH, HEIGHT);
    }

    public synchronized void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        MediaTextureUtil.reset();
    }

    private static boolean inRect(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String formatDuration(long seconds) {
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d", mins, secs);
    }
}
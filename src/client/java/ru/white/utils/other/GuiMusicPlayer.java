package ru.white.utils.other;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public final class GuiMusicPlayer {

    private static final String RESOURCE   = "/assets/client/sound/gui/gui_bg.wav";
    private static final int    FADE_MS    = 500; // fade-in duration
    private static final int    FADE_STEPS = 60;

    private static volatile Clip  currentClip   = null;
    private static volatile float targetVolume  = 0f;
    private static volatile int generation = 0;

    private GuiMusicPlayer() {}

    public static void start(float volume) {
        synchronized (GuiMusicPlayer.class) {
            if (currentClip != null && currentClip.isRunning()) return;
            generation++;
        }
        int startGeneration = generation;
        targetVolume = volume;

        CompletableFuture.runAsync(() -> {
            Clip clip = null;
            AudioInputStream stream = null;
            try {
                InputStream is = GuiMusicPlayer.class.getResourceAsStream(RESOURCE);
                if (is == null) return;

                stream = AudioSystem.getAudioInputStream(new BufferedInputStream(is));
                clip = AudioSystem.getClip();
                clip.open(stream);

                synchronized (GuiMusicPlayer.class) {
                    if (startGeneration != generation) {
                        clip.close();
                        return;
                    }
                    currentClip = clip;
                }

                applyVolume(clip, 0f);
                clip.loop(Clip.LOOP_CONTINUOUSLY);
                clip.start();

                fadeIn(clip, volume);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (stream != null) stream.close();
                } catch (Exception ignored) {
                }
            }
        });
    }

    public static void stop() {
        synchronized (GuiMusicPlayer.class) {
            generation++;
            Clip clip = currentClip;
            if (clip != null) {
                clip.stop();
                clip.close();
                currentClip = null;
            }
        }
    }

    public static void setVolume(float volume) {
        targetVolume = volume;
        Clip clip = currentClip;
        if (clip != null && clip.isOpen()) applyVolume(clip, volume);
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private static void fadeIn(Clip clip, float target) throws InterruptedException {
        long stepMs = FADE_MS / FADE_STEPS;
        for (int i = 1; i <= FADE_STEPS; i++) {
            if (!clip.isOpen()) break;
            // ease-out квадратная: быстро поднимается в начале, плавно в конце
            float t = (float) i / FADE_STEPS;
            float eased = 1f - (1f - t) * (1f - t);
            applyVolume(clip, eased * target);
            Thread.sleep(stepMs);
        }
        // гарантируем финальное значение
        if (clip.isOpen()) applyVolume(clip, target);
    }

    private static void applyVolume(Clip clip, float volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float dB = (float) (Math.log10(volume <= 0.0001f ? 0.0001f : volume) * 20.0);
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB)));
    }
}

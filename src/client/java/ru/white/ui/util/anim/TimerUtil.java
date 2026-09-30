package ru.white.ui.util.anim;

public class TimerUtil {
    private long lastMS = System.currentTimeMillis();

    public TimerUtil() {
        this.resetCounter();
    }

    public static TimerUtil create() {
        return new TimerUtil();
    }

    public long getTime() {
        return System.currentTimeMillis() - this.lastMS;
    }

    public void setTime(long l) {
        this.lastMS = l;
    }

    public void setLastMS(long l) {
        this.lastMS = System.currentTimeMillis() + l;
    }

    public long getLastMS() {
        return this.lastMS;
    }

    public boolean isReached(long l) {
        return System.currentTimeMillis() - this.lastMS > l;
    }

    public void resetCounter() {
        this.lastMS = System.currentTimeMillis();
    }
}
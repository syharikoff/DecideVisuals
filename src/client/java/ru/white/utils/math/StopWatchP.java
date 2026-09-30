package ru.white.utils.math;


import lombok.Getter;

@Getter
public class StopWatchP {

    private long startTime;

    public StopWatchP() {
        reset();
    }

    public long lastMS = System.currentTimeMillis();

    public void reset() {
        lastMS = System.currentTimeMillis();
    }

    public boolean isReached(long time) {
        return System.currentTimeMillis() - lastMS > time;
    }

    public void setLastMS(long newValue) {
        lastMS = System.currentTimeMillis() + newValue;
    }

    public void setTime(long time) {
        lastMS = time;
    }

    public boolean finished(final double delay) {
        return System.currentTimeMillis() - delay >= startTime;
    }

    public long getTime() {
        return System.currentTimeMillis() - lastMS;
    }

    public boolean isRunning() {
        return System.currentTimeMillis() - lastMS <= 0;
    }

    public boolean hasTimeElapsed(long l) {
        return System.currentTimeMillis() - this.lastMS > l;
    }

    public boolean hasTimeElapsed() {
        return lastMS < System.currentTimeMillis();
    }

    public boolean hasTimeElapsed(long time, boolean reset) {
        boolean elapsed = System.currentTimeMillis() - lastMS >= time;
        if (elapsed && reset) {
            reset();
        }
        return elapsed;
    }


}

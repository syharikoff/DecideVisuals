package ru.white.utils.math;


import lombok.Generated;

public class Timer {
    private long millis;

    public Timer() {
        this.reset();
    }

    public boolean finished(long delay) {
        return System.currentTimeMillis() - delay >= this.millis;
    }

    public void reset() {
        this.millis = System.currentTimeMillis();
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - this.millis;
    }

    @Generated
    public long getMillis() {
        return this.millis;
    }

    @Generated
    public void setMillis(long millis) {
        this.millis = millis;
    }


    public static class TimerNew {
        private long time;

        public TimerNew() {
            reset();
        }

        public boolean passedS(double s) {
            return getMs(System.nanoTime() - time) >= (long) (s * 1000.0);
        }

        public boolean passedMs(long ms) {
            return getMs(System.nanoTime() - time) >= ms;
        }

        public boolean every(long ms) {
            boolean passed = getMs(System.nanoTime() - time) >= ms;
            if (passed)
                reset();
            return passed;
        }

        public void setMs(long ms) {
            this.time = System.nanoTime() - ms * 1000000L;
        }

        public long getPassedTimeMs() {
            return getMs(System.nanoTime() - time);
        }

        public void reset() {
            this.time = System.nanoTime();
        }

        public long getMs(long time) {
            return time / 1000000L;
        }

        public long getTimeMs() {
            return getMs(System.nanoTime() - time);
        }
    }

}


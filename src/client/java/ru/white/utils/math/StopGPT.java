package ru.white.utils.math;

public class StopGPT {

    private long lastMS = System.currentTimeMillis();

    /** Сброс таймера */
    public void reset() {
        lastMS = System.currentTimeMillis();
    }

    /**
     * Проверяет, прошло ли ms миллисекунд
     */
    public boolean hasTimePassed(long ms) {
        return System.currentTimeMillis() - lastMS >= ms;
    }

    /**
     * Сколько времени прошло с последнего reset()
     */
    public long getPassedTime() {
        return System.currentTimeMillis() - lastMS;
    }
}

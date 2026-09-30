package ru.white.module.impl.utils.consumable;

import net.minecraft.sound.SoundEvent;

public class ConsumptionStateHandler {
    private boolean suppressServerBurp;
    private boolean suppressEquipmentAnimation;
    private SoundEvent soundToSuppress;
    private int trackerState;
    private long trackerTriggerTime;
    private volatile boolean waitingForServer;
    private volatile long serverWaitStartTime;

    public void startServerWait() {
        this.waitingForServer = true;
        this.serverWaitStartTime = System.currentTimeMillis();
    }

    public boolean isWaitingForServer() {
        if (this.waitingForServer && System.currentTimeMillis() - this.serverWaitStartTime > 500L) {
            this.waitingForServer = false;
        }
        return this.waitingForServer;
    }

    public void stopServerWait() {
        this.waitingForServer = false;
    }

    public void suppressBurp() {
        this.suppressServerBurp = true;
    }

    public void suppressEquipmentAnimation() {
        this.suppressEquipmentAnimation = true;
    }

    public boolean shouldSuppressBurp() {
        return this.suppressServerBurp;
    }

    public boolean shouldSuppressEquipmentAnimation() {
        return this.suppressEquipmentAnimation;
    }

    public void resetEquipmentAnimation() {
        this.suppressEquipmentAnimation = false;
    }

    public void resetBurpSuppression() {
        this.suppressServerBurp = false;
    }

    public void setSoundSuppression(SoundEvent sound) {
        this.soundToSuppress = sound;
    }

    public boolean isSoundSuppressed(SoundEvent sound) {
        return this.soundToSuppress != null && this.soundToSuppress.equals(sound);
    }

    public void clearSoundSuppression() {
        this.soundToSuppress = null;
    }
}

package ru.white.cosmetics;

import net.minecraft.client.network.ClientPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Отслеживает, движется ли игрок, чтобы выбрать анимацию питомца.
 * Render state игрока не содержит скорости, а на блоке анимации «бег» нужен именно факт перемещения.
 */
public final class PetMovementTracker {
    /** ~0.02 блока за тик — порог, ниже которого считаем, что игрок стоит. */
    private static final double MOVE_PER_TICK_SQ = 4.0E-4;
    private static final int STOP_AFTER_TICKS = 2;
    private static final int MAX_STATES = 512;
    private static final Map<UUID, State> STATES = new HashMap<>();

    public static boolean isMoving(ClientPlayerEntity player) {
        State state = STATES.computeIfAbsent(player.getUuid(),
                uuid -> new State(player.getX(), player.getZ(), player.age));

        int age = player.age;
        if (state.age == age) {
            return state.moving;
        }

        // Телепорт, респавн или смена мира — начинаем наблюдение заново
        if (age < state.age || age - state.age > 20) {
            state.reset(player.getX(), player.getZ(), age);
            return false;
        }

        double dx = player.getX() - state.x;
        double dz = player.getZ() - state.z;
        boolean moved = dx * dx + dz * dz >= MOVE_PER_TICK_SQ;
        state.x = player.getX();
        state.z = player.getZ();
        state.age = age;

        if (moved) {
            state.stillTicks = 0;
            state.moving = true;
        } else if (++state.stillTicks >= STOP_AFTER_TICKS) {
            state.moving = false;
        }

        if (STATES.size() > MAX_STATES) {
            STATES.entrySet().removeIf(e -> e.getValue().age + 200 < age);
        }

        return state.moving;
    }

    public static void clear() {
        STATES.clear();
    }

    private static final class State {
        private double x;
        private double z;
        private int age;
        private int stillTicks;
        private boolean moving;

        private State(double x, double z, int age) {
            reset(x, z, age);
        }

        private void reset(double x, double z, int age) {
            this.x = x;
            this.z = z;
            this.age = age;
            this.stillTicks = 0;
            this.moving = false;
        }
    }

    private PetMovementTracker() {
    }
}

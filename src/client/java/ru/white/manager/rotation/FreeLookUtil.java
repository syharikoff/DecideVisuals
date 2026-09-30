package ru.white.manager.rotation;

import ru.white.manager.event_impl.EventLook;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventRotation;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.utils.math.ServerUtil;
import net.minecraft.util.math.MathHelper;

public class FreeLookUtil extends Component {

    public static boolean active;

    /**
     * Принудительный режим (модуль FreeLook). Пока true — никто не может погасить
     * {@link #active} (например, аура/ротация-процессы при остановке наведения),
     * поэтому свободная камера держится даже во время ауры.
     */
    public static boolean forced;

    public static float freeYaw, freePitch;

    /** Безопасная установка active: при forced игнорирует попытки выключить. */
    public static void setActive(boolean value) {
        if (!value && forced) return;
        active = value;
    }

    @EventHandler
    public void onEvent(EventLook event) {
        if (active) {
            rotateTowards(event.getYaw(), event.getPitch());
            event.cancel();
        }
    }

    @EventHandler
    public void onEvent(EventRotation event) {
        if (active) {
            event.setYaw(freeYaw);
            event.setPitch(freePitch);
        } else {
            freeYaw = event.getYaw();
            freePitch = event.getPitch();
        }
    }
    @EventHandler
    public void onEvent(EventTick event) {
        ServerUtil.tick();
    }
    @EventHandler
    public void onPacket(EventPacket e) {
        ServerUtil.packet(e);
    }

    private void rotateTowards(double targetYaw, double targetPitch) {
        freePitch = MathHelper.clamp((float) (freePitch + targetPitch * 0.15D), -90.0F, 90.0F);
        freeYaw = (float) (freeYaw + targetYaw * 0.15D);
    }

}

package ru.white.module.impl.render;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.Vec3d;
import ru.white.manager.event_impl.AttackEvent;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.shader.ScanWorldRenderer;

import java.awt.*;

@ModuleInfo(
        name = "Scan World",
        category = Category.VISUALS,
        desc = "Сканирующая волна по миру"
)
public class ScanWorld extends Module {

    public ModeSetting typeColor = new ModeSetting(this, "Режим цвета", "Тема", "Свой");
    public ColorSetting tintColor = new ColorSetting(this, "Цвет", 0xFF50DCFF).setVisible(() -> typeColor.is("Свой"));

    public BooleanSetting periodic = new BooleanSetting(this, "Периодически", true);
    public SliderSetting interval  = new SliderSetting(this, "Интервал", 3.0f, 1.0f, 15.0f, 0.5f)
            .setVisible(() -> periodic.getValue());
    public BooleanSetting onTotem  = new BooleanSetting(this, "При тотеме", true);
    public BooleanSetting onKill   = new BooleanSetting(this, "При убийстве", true);

    public SliderSetting duration  = new SliderSetting(this, "Длительность", 2.5f, 0.5f, 8.0f, 0.1f);
    public SliderSetting width     = new SliderSetting(this, "Ширина", 10.0f, 1.0f, 32.0f, 1.0f);
    public SliderSetting maxRadius = new SliderSetting(this, "Радиус", 80.0f, 10.0f, 256.0f, 2.0f);

    private static final byte DEATH_STATUS = 3;
    private static final byte TOTEM_STATUS = 35;
    /** Смерть засчитывается как килл, если цель атакована не раньше чем за это окно (как Kill Effect). */
    private static final long KILL_WINDOW_MS = 6500L;

    private long lastTriggerTime;
    private int lastTargetId = -1;
    private long lastAttackTime;

    public int getColor() {
        if (typeColor.is("Тема")) {
            return ColorUtil.getClientColor1(1);
        }
        return tintColor.getValue();
    }

    @Override
    protected void onEnable() {
        lastTargetId = -1;
        if (mc.player != null) {
            lastTriggerTime = System.currentTimeMillis();
            ScanWorldRenderer.getInstance().startScan(mc.player.getEntityPos());
        }
    }

    @Override
    protected void onDisable() {
        ScanWorldRenderer.getInstance().stopScan();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        lastTargetId = -1;
        ScanWorldRenderer.getInstance().stopScan();
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;
        if (!periodic.getValue()) return;

        long now = System.currentTimeMillis();
        long intervalMs = (long) (interval.getValue() * 1000.0f);

        if (now - lastTriggerTime >= intervalMs) {
            Vec3d center = mc.player.getEntityPos();
            ScanWorldRenderer.getInstance().startScan(center);
            lastTriggerTime = now;
        }
    }

    @EventHandler
    public void onAttack(AttackEvent e) {
        if (!(e.getTarget() instanceof LivingEntity living) || living == mc.player) return;
        lastTargetId = living.getId();
        lastAttackTime = System.currentTimeMillis();
    }

    @EventHandler
    public void onPacket(EventPacket e) {
        if (!(e.getPacket() instanceof EntityStatusS2CPacket packet)) return;
        if (mc.world == null) return;

        if (packet.getStatus() == TOTEM_STATUS && onTotem.getValue()) {
            Entity entity = packet.getEntity(mc.world);
            if (entity == null) return;
            ScanWorldRenderer.getInstance().startScan(entity.getEntityPos());
        } else if (packet.getStatus() == DEATH_STATUS && onKill.getValue()) {
            Entity entity = packet.getEntity(mc.world);
            if (!(entity instanceof LivingEntity living)) return;
            // волна только на нашем килле: цель недавно атакована нами
            if (living.getId() != lastTargetId) return;
            if (System.currentTimeMillis() - lastAttackTime > KILL_WINDOW_MS) return;
            lastTargetId = -1;
            ScanWorldRenderer.getInstance().startScan(living.getEntityPos());
        }
    }

    @EventHandler
    public void onRender3D(EventRender3D e) {
        if (mc.player == null || mc.world == null) return;

        int col = getColor();
        int r = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int b = col & 0xFF;

        ScanWorldRenderer renderer = ScanWorldRenderer.getInstance();
        renderer.setDuration(duration.getValue());
        renderer.setWidth(width.getValue());
        renderer.setMaxRadius(maxRadius.getValue());
        renderer.setOuterColor(new Color(r, g, b, 90));
        renderer.setMidColor(new Color(r, g, b, 90));
        renderer.setInnerColor(new Color(r, g, b, 180));
        renderer.setScanlineColor(new Color(r, g, b, 90));

        renderer.render(
                e.getMatrixStack().peek().getPositionMatrix(),
                mc.gameRenderer.getBasicProjectionMatrix(mc.options.getFov().getValue().floatValue())
        );
    }
}

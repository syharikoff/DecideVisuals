package ru.white.module.impl.render;

import org.joml.Matrix4f;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.impl.render.voidbutterflies.VoidButterflyRenderer;
import ru.white.utils.colors.ColorUtil;

/**
 * «Бабочки пустоты на всех гранях блоков» — порт модуля VoidButterflies из Dima 26.2.
 *
 * <p>Бабочки спавнятся на случайных гранях загруженных блоков в радиусе вокруг
 * камеры, живут на грани, пока блок не сменился / точка не уехала из радиуса /
 * не оказалась внутри блока, после чего плавно гаснут. Вокруг каждой — процедурное
 * гало, два скрещённых крыла, тело и светящиеся жилки с усиками.
 */
@ModuleInfo(
        name = "Void Butterflies",
        desc = "Бабочки пустоты на всех гранях блоков",
        category = Category.VISUALS
)
public class VoidButterflies extends Module {

    public SliderSetting radius = new SliderSetting(this, "Радиус", 12.0F, 4.0F, 24.0F, 1.0F);
    public SliderSetting density = new SliderSetting(this, "Плотность", 1.0F, 0.25F, 2.0F, 0.25F);
    public ModeSetting colorMode = new ModeSetting(this, "Цвет", "Тема", "Свой");
    public ColorSetting customColor = new ColorSetting(this, "Свой цвет", 0xFF9A6BFF)
            .setVisible(() -> colorMode.is("Свой"));

    private final VoidButterflyRenderer renderer = new VoidButterflyRenderer();

    @Override
    protected void onDisable() {
        super.onDisable();
        renderer.reset();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        renderer.reset();
    }

    @EventHandler
    public void onTick(EventTick e) {
        renderer.update(radius.getValue(), density.getValue());
    }

    @EventHandler
    public void onRender3D(EventRender3D e) {
        if (mc.currentScreen != null) return;

        Matrix4f projection = e.getProjectionMatrix();
        if (projection == null) return;

        int base = colorMode.is("Свой") ? customColor.getValue() : ColorUtil.getClientColor1(1);
        int second = ColorUtil.multBright(base, 0.72F);

        renderer.render(
                e.getMatrixStack().peek().getPositionMatrix(),
                projection,
                e.getTickDelta(),
                radius.getValue(),
                base,
                second
        );
    }
}
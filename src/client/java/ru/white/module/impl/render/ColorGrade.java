package ru.white.module.impl.render;

import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.events.orbit.EventPriority;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.RenderUtil;

import java.awt.*;


@ModuleInfo(
        name = "Color Grade",
        desc = "Цветовая гамма мира (руки не затрагивает)",
        category = Category.VISUALS
)
public class ColorGrade extends Module {



    public ColorSetting tintColor = new ColorSetting(this, "Цвет", new Color(80, 130, 255).getRGB());

    public SliderSetting intensity = new SliderSetting(this, "Сила тинта", 0.25f, 0.02f, 0.70f, 0.01f);


    public SliderSetting brightness = new SliderSetting(this, "Яркость", 0.00f, -0.40f, 0.40f, 0.01f);


    public BooleanSetting vignetteOn  = new BooleanSetting(this, "Виньетка", false);
    public SliderSetting  vignetteStr = new SliderSetting(this, "Сила виньетки", 0.45f, 0.10f, 0.85f, 0.05f)
            .setVisible(() -> vignetteOn.getValue());

    private static final int[][] PRESETS = {
            {255, 110, 40 },
            {25,  60,  190},
            {45,  165, 65 },
            {135, 25,  215},
            {215, 150, 75 },
    };


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRender3D(EventRender3D e) {
        if (mc.player == null || mc.world == null) return;

        float w = mc.getWindow().getFramebufferWidth()  / 2.0f;
        float h = mc.getWindow().getFramebufferHeight() / 2.0f;


        int cr, cg, cb;

            int c = tintColor.getValue();
            cr = (c >> 16) & 0xFF;
            cg = (c >> 8)  & 0xFF;
            cb =  c        & 0xFF;



        int tintAlpha = clamp255(intensity.getValue() * 255f);
        RenderUtil.Render2D.rect(0, 0, w, h,
                ColorUtil.getColor(cr, cg, cb, tintAlpha));


        float bright = brightness.getValue();
        if (bright < -0.005f) {

            RenderUtil.Render2D.rect(0, 0, w, h,
                    ColorUtil.getColor(0, 0, 0, clamp255(-bright * 255f)));
        } else if (bright > 0.005f) {

            RenderUtil.Render2D.rect(0, 0, w, h,
                    ColorUtil.getColor(255, 255, 255, clamp255(bright * 255f)));
        }

        if (vignetteOn.getValue()) {
            drawVignette(w, h, vignetteStr.getValue());
        }
    }

    private void drawVignette(float w, float h, float strength) {
        int vs    = clamp255(strength * 255f);
        int dark  = ColorUtil.getColor(0, 0, 0, vs);
        int clear = ColorUtil.getColor(0, 0, 0, 0);

        float ew = w * 0.52f;
        float eh = h * 0.52f;

        RenderUtil.Render2D.gradientRect(0,      0,      w,  eh, new int[]{dark,  dark,  clear, clear}, 0f);  // top
        RenderUtil.Render2D.gradientRect(0,      h - eh, w,  eh, new int[]{clear, clear, dark,  dark }, 0f);  // bottom
        RenderUtil.Render2D.gradientRect(0,      0,      ew, h,  new int[]{dark,  clear, clear, dark }, 0f);  // left
        RenderUtil.Render2D.gradientRect(w - ew, 0,      ew, h,  new int[]{clear, dark,  dark,  clear}, 0f);  // right
    }

    private static int[] presetRgb(String name) {
        return switch (name) {
            case "Закат"  -> PRESETS[0];
            case "Ночь"   -> PRESETS[1];
            case "Лес"    -> PRESETS[2];
            case "Пурпур" -> PRESETS[3];
            case "Ретро"  -> PRESETS[4];
            default       -> new int[]{128, 128, 128};
        };
    }

    private static int clamp255(float v) {
        return (int) Math.max(0, Math.min(255, v));
    }
}

package ru.white.module.impl.render;

import ru.white.manager.event_impl.GlassHandsRenderEvent;
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
import ru.white.utils.render.GlassHandsRenderer;
import lombok.Getter;

@Getter
@ModuleInfo(name = "Glass Hands", category = Category.VISUALS, desc = "Делает руки и предметы стеклянными")
public class GlassHands extends Module {

    private static GlassHands instance;

    public BooleanSetting enableBlur = new BooleanSetting(this, "Блюр", true);
    public SliderSetting blurRadius = new SliderSetting(this, "Сила размытия", 2.5f, 1.0f, 5.0f, 0.1f)
            .setVisible(() -> enableBlur.getValue());
    public SliderSetting blurIterations = new SliderSetting(this, "Качество", 3, 1, 5, 1)
            .setVisible(() -> enableBlur.getValue());
    public SliderSetting saturation = new SliderSetting(    this, "Насыщенность", 0, 0.0f, 2.0f, 0.1f)
            .setVisible(() -> enableBlur.getValue());

    public SliderSetting tintIntensity = new SliderSetting(this, "Сила оттенка", 0.2f, 0.0f, 1.0f, 0.01f)
            .setVisible(() -> enableBlur.getValue());

    /** 0 — кромка повторяет силуэт предмета, больше — «оплавленное» стекло с круглыми углами. */
    public SliderSetting edgeSoftness = new SliderSetting(this, "Сглаживание краёв", 0f, 0f, 28f, 1f)
            .setVisible(() -> enableBlur.getValue());

    public ModeSetting typeColor = new ModeSetting(this,"Режим цвета","Тема","Свой");

    public ColorSetting tintColor = new ColorSetting(this, "Цвет", 0xFF00FFFF).setVisible(() -> typeColor.is("Свой"));

    public int getColor() {

        if(typeColor.is("Тема")) {
            return ColorUtil.getClientColor1(1);
        }

        return tintColor.getValue();
    }

    public BooleanSetting enableIce = new BooleanSetting(this, "Искажение", false);
    public SliderSetting iceIntensity = new SliderSetting(this, "Сила искажения", 1.0f, 0.0f, 2.0f, 0.05f)
            .setVisible(() -> enableIce.getValue());
    public SliderSetting refractReach = new SliderSetting(this, "Ширина искажения", 25f, 0, 80f, 1f)
            .setVisible(() -> enableIce.getValue());

    public BooleanSetting enableFire = new BooleanSetting(this, "Огонь", false);
    public SliderSetting fireIntensity = new SliderSetting(this, "Сила огня", 1.18f, 0.2f, 2.0f, 0.02f)
            .setVisible(() -> enableFire.getValue());
    public SliderSetting fireRadius = new SliderSetting(this, "Размер пламени", 15f, 4f, 40f, 1f)
            .setVisible(() -> enableFire.getValue());
    public SliderSetting fireSpeed = new SliderSetting(this, "Скорость огня", 1.1f, 0.2f, 3.0f, 0.05f)
            .setVisible(() -> enableFire.getValue());
    public SliderSetting fireHeight = new SliderSetting(this, "Высота дыма", 28f, 5f, 56f, 1f)
            .setVisible(() -> enableFire.getValue());
    public SliderSetting fireTrail = new SliderSetting(this, "Сила дыма", 1.16f, 0.0f, 2.0f, 0.02f)
            .setVisible(() -> enableFire.getValue());
    public ModeSetting fireColorMode = new ModeSetting(this, "Цвет огня", "Предмет", "Тема", "Свой")
            .setVisible(() -> enableFire.getValue());
    public ColorSetting fireCustomColor = new ColorSetting(this, "Свой цвет огня", 0xFFFF8026)
            .setVisible(() -> enableFire.getValue() && fireColorMode.is("Свой"));
    public BooleanSetting fireBurnThrough = new BooleanSetting(this, "Прожиг", false)
            .setVisible(() -> enableFire.getValue());

    public BooleanSetting enableEdgeGlow = new BooleanSetting(this, "Свечение краёв", true);
    public SliderSetting edgeGlowIntensity = new SliderSetting(this, "Сила свечения", 0.2f, 0.0f, 1.0f, 0.01f)
            .setVisible(() -> enableEdgeGlow.getValue());
    public ModeSetting edgeGlowColorMode = new ModeSetting(this, "Цвет свечения",  "Тема", "Свой")
            .setVisible(() -> enableEdgeGlow.getValue());
    public ColorSetting edgeGlowCustomColor = new ColorSetting(this, "Свой цвет свечения", 0xFF00FFFF)
            .setVisible(() -> enableEdgeGlow.getValue() && edgeGlowColorMode.is("Свой"));
    public BooleanSetting shimmer = new BooleanSetting(this, "Шиммер", true)
            .setVisible(() -> enableEdgeGlow.getValue());
    public SliderSetting shimmerWidth = new SliderSetting(this, "Ширина шиммера", 0.04f, 0.01f, 0.15f, 0.01f)
            .setVisible(() -> enableEdgeGlow.getValue() && shimmer.getValue());
    public SliderSetting shimmerPeriod = new SliderSetting(this, "Период шиммера", 5f, 1f, 15f, 0.5f)
            .setVisible(() -> enableEdgeGlow.getValue() && shimmer.getValue());

    public GlassHands() {
        instance = this;
    }

    public static GlassHands getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
        applyRendererSettings();
    }

    @Override
    protected void onDisable() {
        GlassHandsRenderer.getInstance().setEnabled(false);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!isEnabled()) return;
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
        applyRendererSettings();
    }

    @EventHandler
    public void onGlassHandsRender(GlassHandsRenderEvent event) {
        if (!isEnabled()) return;
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        applyRendererSettings();
        if (event.getPhase() == GlassHandsRenderEvent.Phase.PRE) {
            renderer.captureSceneBeforeHands();
        } else if (event.getPhase() == GlassHandsRenderEvent.Phase.POST) {
            renderer.captureSceneAfterHands();
            renderer.renderGlassEffect();
        }
    }

    public void applyRendererSettings() {
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        renderer.setBlurEnabled(enableBlur.getValue());
        renderer.setBlurRadius(blurRadius.getValue());
        renderer.setBlurIterations(blurIterations.getValue().intValue());
        renderer.setSaturation(saturation.getValue());
        renderer.setEdgeSoftness(edgeSoftness.getValue());

        // reflect и crackIntensity — тоже входы преломления: reflect уходит в шейдер отдельным
        // юниформом, а crack вообще никто не сбрасывал и он висел на дефолтных 0.5
        if (enableIce.getValue()) {
            renderer.setReflect(true);
            renderer.setIceIntensity(iceIntensity.getValue());
            renderer.setFrostScale(refractReach.getValue());
            renderer.setCrackIntensity(0.5f);
        } else {
            renderer.setReflect(false);
            renderer.setIceIntensity(0.0f);
            renderer.setFrostScale(0.0f);
            renderer.setCrackIntensity(0.0f);
        }

        renderer.setSmokeAmount(0.0f);

        renderer.setFireEnabled(enableFire.getValue());
        if (enableFire.getValue()) {
            renderer.setFireStrength(fireIntensity.getValue());
            renderer.setFireRadius(fireRadius.getValue());
            renderer.setFireSpeed(fireSpeed.getValue());
            renderer.setFireHeight(fireHeight.getValue());
            renderer.setFireTrailStrength(fireTrail.getValue());
            renderer.setFireFillMode(fireBurnThrough.getValue());

            if (fireColorMode.is("Предмет")) {
                renderer.setFireColorMix(0.0f);
            } else if (fireColorMode.is("Тема")) {
                renderer.setFireColor(ColorUtil.getClientColor1(1) & 0xFFFFFF);
                renderer.setFireColorMix(1.0f);
            } else {
                renderer.setFireColor(fireCustomColor.getValue() & 0xFFFFFF);
                renderer.setFireColorMix(1.0f);
            }
        }

        if (enableBlur.getValue()) {
            renderer.setTintColor(getColor());
            renderer.setTintIntensity(tintIntensity.getValue());
        } else {
            renderer.setTintColor(0x00000000);
            renderer.setTintIntensity(0.0f);
        }


        renderer.setOutlineEnabled(enableEdgeGlow.getValue());
        if (enableEdgeGlow.getValue()) {
            float strength = edgeGlowIntensity.getValue() * 20.0f;
            renderer.setOutlineGlowStrength(strength);

        if (edgeGlowColorMode.is("Тема")) {
                renderer.setOutlineUseItemColor(false);
                renderer.setOutlineColor(0xFF000000 | (ColorUtil.getClientColor1(1) & 0xFFFFFF));
            } else {
                renderer.setOutlineUseItemColor(false);
                renderer.setOutlineColor(0xFF000000 | (edgeGlowCustomColor.getValue() & 0xFFFFFF));
            }

            renderer.setShimmerEnabled(shimmer.getValue());
            renderer.setShimmerWidth(shimmerWidth.getValue());
            renderer.setShimmerPeriodSec(shimmerPeriod.getValue());
        }
    }
}

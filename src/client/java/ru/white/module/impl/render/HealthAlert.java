package ru.white.module.impl.render;

import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.RenderUtil;
import org.joml.Matrix3x2fStack;

@ModuleInfo(
        name = "Health Alert",
        category = Category.VISUALS,
        desc = "Визуальный эффект когда мало здоровья"
)
public class HealthAlert extends Module {

    public SliderSetting heath = new SliderSetting(this,"Кол в хп",5,1,15,1);

    public Animation animation_1 = new Animation();
    public Animation animation_2 = new Animation();
    public Animation animation_3 = new Animation();

    @EventHandler
    public void onDisplay(EventDisplay e) {
        Matrix3x2fStack matrixStack = e.getDrawContext().getMatrices();

        matrixStack.pushMatrix();

        if(mc.player != null && mc.world != null) {

            float playerallHP = mc.player.getHealth() + mc.player.getAbsorptionAmount();

            boolean effect = false;
            if(playerallHP < heath.getValue()) {
                effect = true;
            }

            animation_1.update();
            animation_1.run(effect ? 1 : 0,0.15F, Easings.SINE_OUT);



            if(animation_1.get() > 0) {

                float alpha = (float) (0.6F + 0.15f * Math.sin(System.currentTimeMillis() / 150D) );

                float alpha2 = (float) (0.0F + 0.2f * Math.sin(System.currentTimeMillis() / 300D) );



                RenderUtil.Render2D.gradientRect(0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), new int[]{
                        ColorUtil.getColor(0, alpha * animation_1.get()),0,0,0
                },0);

                RenderUtil.Render2D.gradientRect(0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), new int[]{
                        0,ColorUtil.getColor(0, alpha * animation_1.get()),0,0
                },0);

                RenderUtil.Render2D.gradientRect(0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), new int[]{
                        0,0,ColorUtil.getColor(0, alpha * animation_1.get()),0
                },0);

                RenderUtil.Render2D.gradientRect(0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), new int[]{
                        0,0,0,ColorUtil.getColor(0, alpha * animation_1.get())
                },0);


                RenderUtil.Render2D.gradientRect(0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), new int[]{
                        ColorUtil.getColor(255,0,0, alpha2 * animation_1.get()),0,0,0
                },0);

                RenderUtil.Render2D.gradientRect(0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), new int[]{
                        0,ColorUtil.getColor(255,0,0, alpha2 * animation_1.get()),0,0
                },0);

                RenderUtil.Render2D.gradientRect(0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), new int[]{
                        0,0,ColorUtil.getColor(255,0,0, alpha2 * animation_1.get()),0
                },0);

                RenderUtil.Render2D.gradientRect(0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), new int[]{
                        0,0,0,ColorUtil.getColor(255,0,0, alpha2 * animation_1.get())
                },0);

            }

        }



        matrixStack.popMatrix();


    }

}

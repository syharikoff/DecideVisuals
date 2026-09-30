package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.preview.ModulePreview;
import ru.white.module.api.preview.PreviewContext;
import ru.white.module.api.preview.PreviewSettings;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;

import ru.white.module.impl.render.targetesp.ChainTargetEspRenderer;
import ru.white.module.impl.render.targetesp.CrystalTargetEspRenderer;
import ru.white.module.impl.render.targetesp.CrossTargetEspRenderer;
import ru.white.module.impl.render.targetesp.MarkerTargetEspRenderer;
import ru.white.module.impl.render.targetesp.RhombTargetEspRenderer;
import ru.white.module.impl.render.targetesp.SkullTargetEspRenderer;
import ru.white.module.impl.render.targetesp.TargetEspRenderContext;
import ru.white.module.impl.render.targetesp.VortexTargetEspRenderer;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

@ModuleInfo(
        name = "Target Esp",
        desc = "Отображения таргета",
        category = Category.VISUALS
)
public class TargetEsp extends Module implements ModulePreview {

    public ButtonSetting previewButton = PreviewSettings.button(this);

    public ModeSetting type = new ModeSetting(this,"Режим","Призраки","Кольцо","Бублик","Кубики","Цепь","Кристаллы","Маркер","Ромб","Череп","Вихрь","Скобы","Кресты");

    public ModeSetting typeGhost = new ModeSetting(this,"Тип призраков","1","2","3","4").setVisible(() -> type.is("Призраки"));;


    public SliderSetting sizeGlow = new SliderSetting(this,"Сила свечения",0.35F,0.1F,0.75F,0.05F).setVisible(() -> type.is("Призраки"));
    public SliderSetting sizeGlowOFF = new SliderSetting(this,"Размер свечения",0.7F,0.1F,2F,0.05F).setVisible(() -> type.is("Призраки"));

    public SliderSetting bublikSize = new SliderSetting(this,"Размер бублика",1,0.5F,2.5F,0.05F).setVisible(() -> type.is("Бублик"));

    /** Единый множитель скорости для всех режимов (одинаковый и для старых, и для новых). */
    public SliderSetting targetSpeed = new SliderSetting(this,"Скорость",1F,0.1F,4F,0.05F)
            .setVisible(() -> !type.is("Череп"));

    public SliderSetting crossesFillAlpha = new SliderSetting(this,"Прозрачность крестов",0.5F,0.1F,1.0F,0.05F)
            .setVisible(() -> type.is("Кресты"));

    private final PreviewSettings previewSettings = PreviewSettings.of(this, 4F, 0F, 2F);

    private final BufferAllocator boxAllocator = new BufferAllocator(1 << 18);
    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        boxAllocator.clear();
    }

    /** Пока открыт предпоказ, таргетом считается болванчик, а не цель ауры. */
    private LivingEntity previewTarget;

    public LivingEntity target = null;
    public Animation alpha = new Animation();
    public Animation alpha_2 = new Animation();

    private float animationNurik = 0.0F;
    private long currentTimeSpirits = 0;

    // ───────────────────────────── предпоказ ─────────────────────────────

    @Override
    public PreviewSettings previewSettings() {
        return previewSettings;
    }

    @Override
    public boolean previewNeedsDummy() {
        return true;
    }

    @Override
    public void previewStart(PreviewContext ctx) {
        previewTarget = ctx.dummy();
    }

    @Override
    public void previewTick(PreviewContext ctx) {
        previewTarget = ctx.dummy();
    }

    /** Цикл показа — вспышка урона: по ней видно, как эффект краснеет и сжимается. */
    @Override
    public void previewSpawn(PreviewContext ctx) {
        if (ctx.dummy() != null) ctx.dummy().hurtTime = 10;
    }

    @Override
    public void previewStop() {
        previewTarget = null;
        target = null;
    }

    @EventHandler
    public void onRender(EventRender3D e) {
        alpha.update();

        LivingEntity currentTarget = previewTarget != null ? previewTarget : (mc.targetedEntity != null && mc.targetedEntity.isLiving() ? mc.targetedEntity.getEntity() : null);

        if (currentTarget != null && currentTarget.isInvisible()) {
            currentTarget = null;
        }

        if (currentTarget != null) {
            target = currentTarget;
        }

        if (mc.world == null || mc.player == null) return;

        alpha.run(currentTarget != null ? 1 : 0, 0.15F, Easings.SINE_OUT);
        float alphaPC = alpha.get();



        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(boxAllocator);
        if (alphaPC > 0.001f && target != null && type.is("Призраки") && typeGhost.is("2")) {
            long currentTime = System.currentTimeMillis();
            if (currentTimeSpirits == 0) {
                currentTimeSpirits = currentTime;
            }

            long timeDiff = currentTime - currentTimeSpirits;
            if (timeDiff > 0) {
                animationNurik += (float) (5L * timeDiff) /  ghostSpeedMs();
            }
            currentTimeSpirits = currentTime;

            MatrixStack matrices = e.getMatrixStack();


            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            double x = lerpedPos.x - cameraPos.x;
            double y = lerpedPos.y  - cameraPos.y;
            double z = lerpedPos.z - cameraPos.z;

            alphaPC = (float) alpha.getValue();

            alpha_2.update();
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
            alpha_2.run(hurtPC,0.1F,Easings.SINE_OUT);

            float atts = alpha_2.get();

            int fadeColor = ColorUtil.fade(1);
            int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
            int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);


            int n2 = 3;
            int n3 = 12;
            int n4 = 3 * n2;

            matrices.push();

            Camera camera = mc.gameRenderer.getCamera();

            for (int i = 0; i < n4; i += n2) {
                for (int j = 0; j < n3; j++) {
                    float f2 = animationNurik + (float) j * 0.1F;
                    float f3 = 0.6F;
                    float f4 = 0.4F;
                    int n5 = (int) Math.pow((double) i, 2.0F);

                    matrices.push();

                    double particleX = x + (double) (f3 * Math.sin(f2 + (float) n5));
                    double particleY = y + (double) f4 + (double) (0.3F * Math.sin(animationNurik + (float) j * 0.2F))
                            + (double) (0.2F * (float) i);
                    double particleZ = z + (double) (f3 * Math.cos(f2 - (float) n5));

                    matrices.translate(particleX, particleY, particleZ);

                    float scale =  (0.006F + (float) j / 2000.0F ) * alphaPC;
                    matrices.scale(scale, scale, scale);

                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    VertexConsumer consumer = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_3.png")));

                    int color = baseColor;
 

                    int n7 = -20;
                    int n8 = 35;

                    consumer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0f)
                            .color(baseColor)
                            .texture(0.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0f)
                            .color(baseColor)
                            .texture(1.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0f)
                            .color(baseColor)
                            .texture(1.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) n7, (float) n7, 0.0f)
                            .color(baseColor)
                            .texture(0.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);



                     n7 = (int) (-20  - 20 * sizeGlowOFF.getValue());
                     n8 = (int) (35 + 40 * sizeGlowOFF.getValue());

                    consumer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(0.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(1.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(1.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) n7, (float) n7, 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(0.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    matrices.pop();
                }
            }

            matrices.pop();
        }
        if (alphaPC > 0.001f && target != null && type.is("Призраки") && typeGhost.is("4")) {
            long currentTime = System.currentTimeMillis();
            if (currentTimeSpirits == 0) {
                currentTimeSpirits = currentTime;
            }

            long timeDiff = currentTime - currentTimeSpirits;
            if (timeDiff > 0) {
                animationNurik += (float) (5L * timeDiff) /  ghostSpeedMs();
            }
            currentTimeSpirits = currentTime;

            MatrixStack matrices = e.getMatrixStack();


            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            double x = lerpedPos.x - cameraPos.x;
            double y = lerpedPos.y  - cameraPos.y;
            double z = lerpedPos.z - cameraPos.z;

            alphaPC = (float) alpha.getValue();

            alpha_2.update();
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
            alpha_2.run(hurtPC,0.1F,Easings.SINE_OUT);

            float atts = alpha_2.get();

            int fadeColor = ColorUtil.fade(1);
            int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
            int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);


            int n2 = 3;
            int n3 = 24;
            int n4 = 3 * n2;

            matrices.push();

            Camera camera = mc.gameRenderer.getCamera();

            for (int i = 0; i < n4; i += n2) {
                for (int j = 0; j < n3; j++) {
                    float f2 = animationNurik + (float) j * 0.05F;
                    float f3 = target.getWidth();
                    float f4 = 0.45F;
                    int n5 = (int) Math.pow((double) i, 2.0F);

                    matrices.push();

                    double particleX = x + (double) (f3 * Math.sin(f2 + (float) n5));
                    double particleY = y + (double) f4 + (double) (0.1F * Math.sin(animationNurik + (float) j * 0.1F))
                            + (double) (0.2F * (float) i);
                    double particleZ = z + (double) (f3 * Math.cos(f2 - (float) n5));

                    matrices.translate(particleX, particleY, particleZ);

                    float scale =  (0.009F + (float) j / 2000.0F ) * alphaPC;
                    matrices.scale(scale, scale, scale);

                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    VertexConsumer consumer = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_3.png")));

                    int color = baseColor;


                    int n7 = -12;
                    int n8 = 16;

                    consumer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0f)
                            .color(baseColor)
                            .texture(0.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0f)
                            .color(baseColor)
                            .texture(1.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0f)
                            .color(baseColor)
                            .texture(1.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) n7, (float) n7, 0.0f)
                            .color(baseColor)
                            .texture(0.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);



                    n7 = (int) (-12  - 20 * sizeGlowOFF.getValue());
                    n8 = (int) (16 + 40 * sizeGlowOFF.getValue());

                    consumer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(0.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(1.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(1.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) n7, (float) n7, 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(0.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    matrices.pop();
                }
            }

            matrices.pop();
        }
        if (alphaPC > 0.001f && target != null && type.is("Призраки") && typeGhost.is("1")) {


            long currentTime = System.currentTimeMillis();
            if (currentTimeSpirits == 0) {
                currentTimeSpirits = currentTime;
            }

            long timeDiff = currentTime - currentTimeSpirits;
            if (timeDiff > 0) {
                animationNurik += (float) (5L * timeDiff) / ghostSpeedMs();
            }
            currentTimeSpirits = currentTime;

            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
            int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0F * alphaPC));
            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(0), alphaPC), redColor, hurtPC);
            int color2 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(90), alphaPC), redColor,  hurtPC);
            int color3 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(180), alphaPC), redColor, hurtPC);
            int color4 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(360), alphaPC), redColor, hurtPC);



            MatrixStack matrices = e.getMatrixStack();

            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            long time = System.currentTimeMillis();
            double speed = (double) time / ghostSpeedMs();
            float radius = target.getWidth()  + 0.25F - 0.25F * alphaPC;
            int ghostCount = 3;
            int cound = 10;

            for (int i = 0; i < ghostCount; i++) {
                for (int s = 0; s < cound; s++) {
                    matrices.push();

                    float f2 = animationNurik + (float) s * 0.1F;

                    double angle = speed + (i * (Math.PI * 2 / ghostCount));

                    double offX = Math.cos(angle + f2) * radius;
                    double offZ = Math.sin(angle + f2) * radius;

                    double offY = Math.sin(speed + i) * 0.6 + 0.15  * Math.sin(speed + (float) s * 0.11F);


                    matrices.translate(
                            lerpedPos.x - cameraPos.x + offX,
                            lerpedPos.y - cameraPos.y + (target.getHeight() / 2.0F) + offY,
                            lerpedPos.z - cameraPos.z + offZ
                    );


                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));


                    float scale =  ((0.12F + (float) s /40.0F) * 1.25F) * alphaPC;
                    matrices.scale(scale, scale, scale);

                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    VertexConsumer consumer = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_3.png")));


                    drawGradientQuad(consumer, matrix, color,color2,color3,color4, (int) (255 * alphaPC));

                    scale = ((0.12F + (float) s /40.0F) * (100 * sizeGlowOFF.getValue()) / 10) * alphaPC;
                    matrices.scale(scale, scale, scale);
                    VertexConsumer consumer2 = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_2.png")));

                    drawGradientQuad(consumer2, matrix, color,color2,color3,color4, (int) ((255 * sizeGlow.getValue() * 1) * alphaPC));


                    matrices.pop();
                }
            }
        }
        if (alphaPC > 0.001f && target != null && type.is("Призраки") && typeGhost.is("3")) {


            long currentTime = System.currentTimeMillis();
            if (currentTimeSpirits == 0) {
                currentTimeSpirits = currentTime;
            }

            long timeDiff = currentTime - currentTimeSpirits;
            if (timeDiff > 0) {
                animationNurik += (float) (5L * timeDiff) / ghostSpeedMs();
            }
            currentTimeSpirits = currentTime;

            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
            int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0F * alphaPC));
            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(0), alphaPC), redColor, hurtPC);
            int color2 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(90), alphaPC), redColor,  hurtPC);
            int color3 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(180), alphaPC), redColor, hurtPC);
            int color4 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(360), alphaPC), redColor, hurtPC);



            MatrixStack matrices = e.getMatrixStack();

            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            long time = System.currentTimeMillis();
            double speed = (double) time / ghostSpeedMs();
            float radius = target.getWidth()  + 0.25F - 0.25F * alphaPC;
            int ghostCount = 3;
            int cound = 9;

            for (int i = 0; i < ghostCount; i++) {
                for (int s = 0; s < cound; s++) {
                    matrices.push();

                    float f2 = animationNurik + (float) s * 0.1F;

                    double angle = speed + (i * (Math.PI * 2 / ghostCount));

                    double offX = Math.cos(angle + f2) * radius;
                    double offZ = Math.sin(angle + f2) * radius;

                    double offY = Math.sin(speed ) * 0.7  + 0.2  * Math.sin(speed + (float) s * 0.11F);


                    matrices.translate(
                            lerpedPos.x - cameraPos.x + offX,
                            lerpedPos.y - cameraPos.y + (target.getHeight() / 2.0F) + offY,
                            lerpedPos.z - cameraPos.z + offZ
                    );


                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));


                    float scale =  (0.12F + (float) s /50.0F) * 1.5F;
                    matrices.scale(scale, scale, scale);

                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    VertexConsumer consumer = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_3.png")));


                    drawGradientQuad(consumer, matrix, color,color2,color3,color4, (int) (255 * alphaPC));

                    scale = (0.12F + (float) s /50.0F) * (100 * sizeGlowOFF.getValue()) / 10;
                    matrices.scale(scale, scale, scale);
                    VertexConsumer consumer2 = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_2.png")));

                    drawGradientQuad(consumer2, matrix, color,color2,color3,color4, (int) ((255 * sizeGlow.getValue() * 1) * alphaPC));


                    matrices.pop();
                }
            }
        }


        if (alphaPC > 0.001f && target != null && type.is("Бублик")) {
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));

            alpha_2.update();
            alpha_2.run(hurtPC,0.1F,Easings.SINE_OUT);

            int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0F * alphaPC));
            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(0), alphaPC), redColor, alpha_2.get());
            int color2 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(90), alphaPC), redColor, alpha_2.get());
            int color3 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(180), alphaPC), redColor, alpha_2.get());
            int color4 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(360), alphaPC), redColor, alpha_2.get());

            MatrixStack matrices = e.getMatrixStack();
            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            double duration = speedMs(1400.0);
            double elapsed = System.currentTimeMillis() % duration;
            boolean down = elapsed > duration / 2.0;
            double raw = elapsed / (duration / 2.0);
            raw = down ? raw - 1.0 : 1.0 - raw;
            double progress = raw < 0.5
                    ? 2.0 * raw * raw
                    : 1.0 - Math.pow(-2.0 * raw + 2.0, 2.0) / 2.0;

            float height = target.getHeight();
            float yBase = 0.12F + (float) progress * Math.max(0.2F, height - 0.24F);
            float direction = down ? -1F : 1F;
            double spin = System.currentTimeMillis() / (speedMs(1400.0) * 0.55);
            float radius = (target.getWidth() - 0.15F ) ;
            int count = (int) (80 * alphaPC);

            for (int layer = 2; layer >= 0; layer--) {
                float layerAlpha = alphaPC * (1F - layer * 0.28F);
                float yLayer = yBase + direction * layer * 0.075F;
                float radiusLayer = radius + layer * 0.025F;

                for (int i = 0; i < count; i++) {
                    float pc = i / (float) count;
                    double angle = pc * MathHelper.TAU + spin + layer * 0.18;
                    double x = Math.cos(angle) * radiusLayer;
                    double z = Math.sin(angle) * radiusLayer;
                    float size = (0.2F + layer * 0.018F) * bublikSize.getValue();
                    int localAlpha = (int) ((layer == 0 ? 255 : 105) * layerAlpha);



                    if (layer != 0) continue;

                    matrices.push();
                    matrices.translate(
                            lerpedPos.x - cameraPos.x + x,
                            lerpedPos.y - cameraPos.y + yLayer,
                            lerpedPos.z - cameraPos.z + z
                    );
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.toDegrees(-angle) + (float) (spin * 80.0)));
                    matrices.scale(size * 1.25F, size * 1.25F, size);
                    drawGradientQuad(immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_3.png"))),
                            matrices.peek().getPositionMatrix(), color, color2, color3, color4, localAlpha);
                    matrices.pop();
                }
            }
        }

        if (alphaPC > 0.001f && target != null && type.is("Кольцо")) {
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));
            int redColor = ColorUtil.getColor(255, 100, 100, (int)(255.0F * alphaPC));

            double duration = speedMs(1800.0);
            double elapsed  = System.currentTimeMillis() % duration;
            boolean side    = elapsed > duration / 2.0;
            double raw      = elapsed / (duration / 2.0);
            raw = side ? (raw - 1.0) : 1.0 - raw;
            double progress = raw < 0.5
                    ? 2.0 * raw * raw
                    : 1.0 - Math.pow(-2.0 * raw + 2.0, 2.0) / 2.0;

            float height2 = target.getHeight() ;
            double eased  = (height2 / 1.7) * (progress > 0.5 ? 1.0 - progress : progress) * (side ? -1 : 1) /0.7;

            MatrixStack matrices = e.getMatrixStack();
            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            matrices.push();
            matrices.translate(lerpedPos.x - cameraPos.x, lerpedPos.y - cameraPos.y, lerpedPos.z - cameraPos.z);
            Matrix4f matrix = matrices.peek().getPositionMatrix();

            float radius = (target.getWidth() - 0.1F) + 0.35F - 0.35F * alphaPC;
            float yBase  = (float)(height2 * progress);
            float yTop   = (float)(height2 * progress + eased);


            VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);
            for (int seg = 0; seg < 360; seg++) {
                float a0 = (float) Math.toRadians(seg);
                float a1 = (float) Math.toRadians(seg + 1);
                int c0 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(seg ), alphaPC), redColor, hurtPC);
                int c1 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade((seg  )), alphaPC), redColor, hurtPC);
                float x0 = (float)(Math.cos(a0) * radius), z0 = (float)(Math.sin(a0) * radius);
                float x1 = (float)(Math.cos(a1) * radius), z1 = (float)(Math.sin(a1) * radius);
                fillBuf.vertex(matrix, x0, yBase, z0).color(ColorUtil.replAlpha(c0, (int)(120 * alphaPC)));
                fillBuf.vertex(matrix, x1, yBase, z1).color(ColorUtil.replAlpha(c1, (int)(120 * alphaPC)));
                fillBuf.vertex(matrix, x1, yTop,  z1).color(ColorUtil.replAlpha(c1, 0));
                fillBuf.vertex(matrix, x0, yTop,  z0).color(ColorUtil.replAlpha(c0, 0));
            }

            VertexConsumer lineBuf = immediate.getBuffer(RING_LINE_LAYER);
            for (int seg = 0; seg < 360; seg++) {
                float a0 = (float) Math.toRadians(seg);
                float a1 = (float) Math.toRadians(seg + 1);
                int c =ColorUtil.multAlpha(ColorUtil.overCol(ColorUtil.multBright(ColorUtil.fade(1),0.7F),redColor,hurtPC), alphaPC);
                lineBuf.vertex(matrix, (float)(Math.cos(a0) * radius), yBase, (float)(Math.sin(a0) * radius)).color(ColorUtil.replAlpha(c, (int)(150 * alphaPC)));
                lineBuf.vertex(matrix, (float)(Math.cos(a1) * radius), yBase, (float)(Math.sin(a1) * radius)).color(ColorUtil.replAlpha(c, (int)(150 * alphaPC)));
            }

            matrices.pop();
        }

        if (alphaPC > 0.001f && target != null && type.is("Кубики")) {
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

            alpha_2.update();
            alpha_2.run(hurtPC,0.1F,Easings.SINE_OUT);

            int redColor = ColorUtil.getColor(255, 100, 100, (int)(255.0f * alphaPC));
            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC), redColor, alpha_2.get());

            MatrixStack matrices = e.getMatrixStack();
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
            Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

            float time = (float) ((Math.cos(System.currentTimeMillis() / speedMs(2000.0))) ) * 360 + (alpha_2.get() * 20);
            int cound = 12;
            float width = target.getWidth() * 1.5f ;
            float sizeFI = (1f - 0.3F * alpha_2.get()) * alphaPC;

            Camera camera = mc.gameRenderer.getCamera();

            for (int i = 0; i < 360; i += cound) {
                float val = 1.2f - 0.5f ;
                float sin = (float)(Math.sin((float) Math.toRadians(i + time)) * width * val);
                float cos = (float)(Math.cos((float) Math.toRadians(i + time)) * width * val);

                double x = targetPos.x + sin;
                double z = targetPos.z + cos;
                double y = targetPos.y + target.getHeight() * Math.abs(MathUtil.sin(i));

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                matrices.multiply(camera.getRotation());
                float gs = 0.6f * sizeFI;
                matrices.scale(gs, gs, gs);
                drawGradientQuad(immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png"))),
                        matrices.peek().getPositionMatrix(),
                        ColorUtil.multAlpha(color, 0.3f), ColorUtil.multAlpha(color, 0.3f),
                        ColorUtil.multAlpha(color, 0.3f), ColorUtil.multAlpha(color, 0.3f),
                        (int)(alphaPC * 0.35f * 255));
                matrices.pop();
            }

            // Pass 2: cube fills
            for (int i = 0; i < 360; i += cound) {
                float val = 1.2f - 0.5f ;
                float sin = (float)(Math.sin((float) Math.toRadians(i + time)) * width * val);
                float cos = (float)(Math.cos((float) Math.toRadians(i + time)) * width * val);

                double x = targetPos.x + sin;
                double z = targetPos.z + cos;
                double y = targetPos.y + target.getHeight() * Math.abs(MathUtil.sin(i));

                Vec3d cubePos = new Vec3d(x, y, z);
                Vector3f directionToTarget = new Vector3f(
                        (float)(targetPos.x - cubePos.x),
                        (float)(targetPos.y - cubePos.y),
                        (float)(targetPos.z - cubePos.z)
                ).normalize();

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), directionToTarget));
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                float size = 0.06f * sizeFI;
                drawCubeFillTESP(immediate.getBuffer(RING_FILL_LAYER), matrix, size,
                        ColorUtil.replAlpha(color, (int)(alphaPC * 0.2f * 255)));
                matrices.pop();
            }

            // Pass 3: cube outlines
            for (int i = 0; i < 360; i += cound) {
                float val = 1.2f - 0.5f ;
                float sin = (float)(Math.sin((float) Math.toRadians(i + time)) * width * val);
                float cos = (float)(Math.cos((float) Math.toRadians(i + time)) * width * val);

                double x = targetPos.x + sin;
                double z = targetPos.z + cos;
                double y = targetPos.y + target.getHeight() * Math.abs(MathUtil.sin(i));

                Vec3d cubePos = new Vec3d(x, y, z);
                Vector3f directionToTarget = new Vector3f(
                        (float)(targetPos.x - cubePos.x),
                        (float)(targetPos.y - cubePos.y),
                        (float)(targetPos.z - cubePos.z)
                ).normalize();

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), directionToTarget));
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                float size = 0.06f * sizeFI;
                drawCubeOutlineTESP(immediate.getBuffer(RING_LINE_LAYER), matrix, size,
                        ColorUtil.replAlpha(color, (int)(alphaPC * 255)));
                matrices.pop();
            }
        }

        // ─────────── НОВЫЕ РЕЖИМЫ (порт из Polairis) ───────────
        boolean newMode = type.is("Цепь") || type.is("Кристаллы") || type.is("Маркер")
                || type.is("Ромб") || type.is("Череп") || type.is("Вихрь") || type.is("Скобы")
                || type.is("Кресты");
        if (alphaPC > 0.001f && target != null && newMode) {
            int hurtTicks = target.hurtTime;
            float hurtPC = MathHelper.clamp((float) Math.sin(hurtTicks * (Math.PI / 10.0)), 0.0f, 1.0f);

            int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0F * alphaPC));
            int primaryColor = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(0), alphaPC), redColor, hurtPC);
            int secondaryColor = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(180), alphaPC), redColor, hurtPC);

            MatrixStack matrices = e.getMatrixStack();
            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            matrices.push();
            matrices.translate(lerpedPos.x - cameraPos.x, lerpedPos.y - cameraPos.y, lerpedPos.z - cameraPos.z);

            TargetEspRenderContext ctx = new TargetEspRenderContext(
                    target, alphaPC, (float) e.getTickDelta(), System.currentTimeMillis(),
                    primaryColor, secondaryColor, hurtPC, hurtPC);

            float speed = targetSpeed.getValue();

            if (type.is("Цепь")) {
                ChainTargetEspRenderer.render(matrices, immediate, ctx, speed);
            } else if (type.is("Кристаллы")) {
                CrystalTargetEspRenderer.render(matrices, immediate, ctx, speed);
            } else if (type.is("Маркер")) {
                MarkerTargetEspRenderer.render(matrices, immediate, ctx, speed);
            } else if (type.is("Ромб")) {
                RhombTargetEspRenderer.render(matrices, immediate, ctx, speed);
            } else if (type.is("Череп")) {
                SkullTargetEspRenderer.render(matrices, immediate, ctx);
            } else if (type.is("Вихрь")) {
                VortexTargetEspRenderer.configure(1.2f, speed, true, VortexTargetEspRenderer.TEXTURE_VORTEX);
                VortexTargetEspRenderer.render(matrices, immediate, ctx);
            } else if (type.is("Скобы")) {
                VortexTargetEspRenderer.configure(1.2f, speed, true, VortexTargetEspRenderer.TEXTURE_BRACKETS);
                VortexTargetEspRenderer.render(matrices, immediate, ctx);
            } else if (type.is("Кресты")) {
                float oldAlpha = ctx.alpha();
                TargetEspRenderContext crossCtx = new TargetEspRenderContext(
                        ctx.target(), crossesFillAlpha.getValue(), ctx.partialTicks(), ctx.frameTimeMs(),
                        ctx.primaryColor(), ctx.secondaryColor(), ctx.hurtProgress(), ctx.chainImpactProgress());
                CrossTargetEspRenderer.render(matrices, immediate, crossCtx, speed);
            }

            matrices.pop();
        }

        immediate.draw();
    }

    /** Период (мс) для призраков от единого множителя скорости. */
    private long ghostSpeedMs() {
        return Math.max(50L, Math.round(900.0 / targetSpeed.getValue()));
    }

    /** Переводит базовый период (мс при скорости 1) в текущий с учётом множителя скорости. */
    private double speedMs(double baseMs) {
        return Math.max(50.0, baseMs / targetSpeed.getValue());
    }

    public static final RenderPipeline RING_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "ring_esp_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );
    private static final RenderPipeline RING_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "ring_esp_line"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );
    public static final RenderLayer RING_FILL_LAYER = RenderLayer.of("ring_esp_fill",
            RenderSetup.builder(RING_FILL_PIPELINE).expectedBufferSize(1 << 16).build());
    private static final RenderLayer RING_LINE_LAYER = RenderLayer.of("ring_esp_line",
            RenderSetup.builder(RING_LINE_PIPELINE).expectedBufferSize(1 << 14).build());

    public static final RenderPipeline ROMB_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/wtex")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> ROMB_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(ROMB_ESP_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(1536)
                        .build();
                return RenderLayer.of("wtex", setup);
            });

    private static void drawGradientQuad(VertexConsumer buffer, Matrix4f matrix, int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;


    }


    private static void drawGradientQuad(VertexConsumer buffer, Matrix4f matrix,int color,int color2,int color3,int color4, int alpha) {

        buffer.vertex(matrix, -0.5f, -0.5f, 0.0f).color(ColorUtil.replAlpha(color, alpha)).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, -0.5f, 0.0f).color(ColorUtil.replAlpha(color2, alpha)).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, 0.5f, 0.0f).color(ColorUtil.replAlpha(color3, alpha)).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, -0.5f, 0.5f, 0.0f).color(ColorUtil.replAlpha(color4, alpha)).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
    }

    private static void drawCubeFillTESP(VertexConsumer buf, Matrix4f m, float s, int color) {
        // +Y
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
        // -Y
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        // +X
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        // -X
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m, -s,  s,  s).color(color);
        // +Z
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
        // -Z
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
    }

    private static void drawCubeOutlineTESP(VertexConsumer buf, Matrix4f m, float s, int color) {
        // bottom ring
        buf.vertex(m, -s, -s, -s).color(color); buf.vertex(m,  s, -s, -s).color(color);
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s, -s,  s).color(color); buf.vertex(m, -s, -s,  s).color(color);
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        // top ring
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        buf.vertex(m,  s,  s, -s).color(color); buf.vertex(m,  s,  s,  s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
        buf.vertex(m, -s,  s,  s).color(color); buf.vertex(m, -s,  s, -s).color(color);
        // verticals
        buf.vertex(m, -s, -s, -s).color(color); buf.vertex(m, -s,  s, -s).color(color);
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        buf.vertex(m,  s, -s,  s).color(color); buf.vertex(m,  s,  s,  s).color(color);
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
    }

}

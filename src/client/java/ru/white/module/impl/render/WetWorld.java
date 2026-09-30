package ru.white.module.impl.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.events.orbit.EventPriority;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.render.WetWorldPipeline;

@ModuleInfo(
        name = "Wet World",
        desc = "Мокрые поверхности с отражениями",
        category = Category.VISUALS
)
public final class WetWorld extends Module {

    private static final String QUALITY_LOW = "Низкое";
    private static final String QUALITY_MEDIUM = "Среднее";
    private static final String QUALITY_HIGH = "Высокое";

    private final SliderSetting reflections = new SliderSetting(this, "Отражения", 85F, 0F, 100F, 1F);
    private final SliderSetting humidity = new SliderSetting(this, "Влажность", 70F, 0F, 100F, 1F);
    private final SliderSetting ripple = new SliderSetting(this, "Рябь", 55F, 0F, 100F, 1F);
    private final ModeSetting quality = new ModeSetting(this, "Качество", QUALITY_HIGH, QUALITY_LOW, QUALITY_MEDIUM, QUALITY_HIGH);

    private final float traceDistance = 64.0F;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRender(EventRender3D e) {
        if (!isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.gameRenderer == null) return;
        if (mc.getFramebuffer() == null || mc.getFramebuffer().getColorAttachment() == null) return;
        if (mc.getFramebuffer().getDepthAttachment() == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        Matrix4f projection = mc.gameRenderer.getBasicProjectionMatrix(
                mc.options.getFov().getValue().floatValue());

        float camPitch = camera.getPitch();
        float camYaw = camera.getYaw() + 180.0F;

        Matrix4f view = new Matrix4f();
        view.rotateX((float) Math.toRadians(camPitch));
        view.rotateY((float) Math.toRadians(camYaw));

        Matrix4f viewProj = new Matrix4f(projection).mul(view);
        Matrix4f invViewProj = new Matrix4f(viewProj).invert();

        float skyR = 0.5F, skyG = 0.6F, skyB = 0.8F;
        try {
            long timeOfDay = mc.world.getTimeOfDay();
            float sunAngle = (float) (timeOfDay % 24000L) / 24000.0F;
            float skyBrightness = MathHelper.clamp(
                    (float) Math.cos(sunAngle * Math.PI * 2.0) * 0.5F + 0.5F, 0.0F, 1.0F);
            skyR = 0.4F + 0.4F * skyBrightness;
            skyG = 0.5F + 0.3F * skyBrightness;
            skyB = 0.7F + 0.2F * skyBrightness;
        } catch (Exception ignored) {}

        float sunX = 0.0F, sunY = 1.0F, sunZ = 0.0F;
        try {
            long timeOfDay = mc.world.getTimeOfDay();
            float sunAngle = (float) (timeOfDay % 24000L) / 24000.0F;
            float sx = (float) Math.cos(sunAngle * Math.PI * 2.0);
            float sy = (float) Math.sin(sunAngle * Math.PI * 2.0);
            Vector3f sunDir = new Vector3f(sx, sy, 0.0F).normalize();
            sunX = sunDir.x;
            sunY = sunDir.y;
            sunZ = sunDir.z;
        } catch (Exception ignored) {}

        int steps;
        if (quality.is(QUALITY_LOW)) {
            steps = 16;
        } else if (quality.is(QUALITY_MEDIUM)) {
            steps = 32;
        } else {
            steps = 48;
        }

        float w = mc.getWindow().getScaledWidth();
        float h = mc.getWindow().getScaledHeight();
        float time = (System.currentTimeMillis() % 1_000_000L) / 1000.0F;

        WetWorldPipeline.draw(
                w, h, time, steps,
                reflections.getValue() / 100.0F,
                humidity.getValue() / 100.0F,
                ripple.getValue() / 100.0F,
                true,
                (float) camPos.x, (float) camPos.y, (float) camPos.z,
                traceDistance,
                skyR, skyG, skyB,
                sunX, sunY, sunZ,
                viewProj,
                invViewProj
        );
    }
}

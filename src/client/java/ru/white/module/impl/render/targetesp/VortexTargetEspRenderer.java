package ru.white.module.impl.render.targetesp;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import ru.white.utils.colors.ColorUtil;

import static ru.white.module.impl.render.targetesp.RhombTargetEspRenderer.withAlpha;
import static ru.white.module.impl.render.targetesp.TargetEspPipelines.textured;

import java.util.Random;

/**
 * Вихрь / Скобы — 3 вращающиеся текстурированные плоскости вокруг таргета.
 * Портировано из Polairis (Zenith).
 */
public final class VortexTargetEspRenderer {
    public static final Identifier TEXTURE_VORTEX =
            Identifier.of("client", "textures/features/targetesp/vortex.png");
    public static final Identifier TEXTURE_BRACKETS =
            Identifier.of("client", "textures/features/targetesp/brackets.png");

    private static final int LAYERS = 3;
    private static final long MODULE_START_MS = System.currentTimeMillis();

    private static final float[] rotSpeed = new float[LAYERS];
    private static final float[] pitchSpeed = new float[LAYERS];
    private static final float[] pitchPhase = new float[LAYERS];
    private static final float[] yawBase = new float[LAYERS];

    private static LivingEntity seededFor;
    private static final Random RANDOM = new Random();

    private static float sizeMul = 1.2f;
    private static float speedMul = 1.0f;
    private static boolean redOnHit = true;
    private static Identifier activeTexture = TEXTURE_VORTEX;

    private VortexTargetEspRenderer() {
    }

    public static void configure(float size, float speed, boolean redHit) {
        configure(size, speed, redHit, TEXTURE_VORTEX);
    }

    public static void configure(float size, float speed, boolean redHit, Identifier texture) {
        sizeMul = MathHelper.clamp(size, 0.3f, 3.0f);
        speedMul = MathHelper.clamp(speed, 0.0f, 4.0f);
        redOnHit = redHit;
        activeTexture = texture != null ? texture : TEXTURE_VORTEX;
    }

    public static void render(MatrixStack stack, VertexConsumerProvider.Immediate provider, TargetEspRenderContext context) {
        LivingEntity target = context.target();
        if (target == null) {
            return;
        }
        if (seededFor != target) {
            reseed(target);
        }

        float time = (System.currentTimeMillis() - MODULE_START_MS) / 1000.0f;
        float speed = speedMul;
        float height = target.getHeight();

        float half = target.getWidth() * 2.5f * sizeMul;

        float baseY = height * 0.62f - 0.006f;
        float hurt = redOnHit ? MathHelper.clamp(context.hurtProgress(), 0.0f, 1.0f) : 0.0f;
        int baseColor = ColorUtil.interpolateColor(context.primaryColor(), 0xFFFF3A3A, hurt);

        float[] layerAlpha = new float[LAYERS];
        float[] yawDeg = new float[LAYERS];
        float[] pitchDeg = new float[LAYERS];
        for (int j = 0; j < LAYERS; j++) {
            float pulse = (MathHelper.sin(time * speed * 3.4f + j * (MathHelper.TAU / 3.0f)) + 1.0f) * 0.5f;
            layerAlpha[j] = MathHelper.lerp(pulse, 0.35f, 1.0f);
            yawDeg[j] = -time * 140.0f * speed * rotSpeed[j] + yawBase[j];
            pitchDeg[j] = MathHelper.sin(time * pitchSpeed[j] + pitchPhase[j]) * 30.0f;
        }

        Identifier tex = activeTexture;
        VertexConsumer consumer = provider.getBuffer(TargetEspPipelines.ROMB_ESP.apply(tex));

        for (int k = 0; k < LAYERS; k++) {
            int color = withAlpha(baseColor, (int) (255.0f * context.alpha() * layerAlpha[k]));
            drawLayer(stack, consumer, half, baseY + 0.006f * k, yawDeg[k], pitchDeg[k], color);
        }

        for (int l = 0; l < LAYERS; l++) {
            int color = withAlpha(baseColor, (int) (255.0f * context.alpha() * layerAlpha[l] * 0.85f));
            drawLayer(stack, consumer, half, baseY + 0.006f * l, yawDeg[l], pitchDeg[l], color);
        }
    }

    private static void drawLayer(MatrixStack stack, VertexConsumer consumer,
                                  float half, float y, float yawDeg, float pitchDeg, int color) {
        stack.push();
        stack.translate(0.0f, y, 0.0f);

        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f + yawDeg));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDeg));

        Matrix4f pose = stack.peek().getPositionMatrix();

        textured(consumer, pose, -half, 0.0f, -half, 0.0f, 0.0f, color);
        textured(consumer, pose, -half, 0.0f, half, 0.0f, 1.0f, color);
        textured(consumer, pose, half, 0.0f, half, 1.0f, 1.0f, color);
        textured(consumer, pose, half, 0.0f, -half, 1.0f, 0.0f, color);
        stack.pop();
    }

    public static void reset() {
        seededFor = null;
    }

    private static void reseed(LivingEntity target) {
        seededFor = target;
        for (int i = 0; i < LAYERS; i++) {
            rotSpeed[i] = 0.75f + RANDOM.nextFloat() * 0.5f;
            pitchSpeed[i] = 0.8f + RANDOM.nextFloat() * 0.8f;
            pitchPhase[i] = RANDOM.nextFloat() * MathHelper.TAU;
        }

        for (int attempt = 0; attempt < 64; attempt++) {
            for (int j = 0; j < LAYERS; j++) {
                yawBase[j] = -30.0f + RANDOM.nextFloat() * 60.0f;
            }
            boolean ok = true;
            for (int a = 0; a < LAYERS && ok; a++) {
                for (int b = a + 1; b < LAYERS; b++) {
                    if (Math.abs(yawBase[a] - yawBase[b]) < 22.0f) {
                        ok = false;
                        break;
                    }
                }
            }
            if (ok) {
                return;
            }
        }
        yawBase[0] = -30.0f + RANDOM.nextFloat() * 4.0f;
        yawBase[1] = -2.0f + RANDOM.nextFloat() * 4.0f;
        yawBase[2] = 30.0f - RANDOM.nextFloat() * 4.0f;
    }
}

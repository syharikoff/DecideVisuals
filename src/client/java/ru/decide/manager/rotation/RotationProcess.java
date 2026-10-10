package ru.decide.manager.rotation;


import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.decide.manager.event_impl.EventMoveInput;
import ru.decide.manager.event_impl.EventRender3D;
import ru.decide.manager.event_impl.EventTick;
import ru.decide.manager.event_impl.WorldLoadEvent;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.manager.events.orbit.EventPriority;
import ru.decide.utils.animation.Animation;
import ru.decide.utils.animation.Easings;
import ru.decide.utils.aura.GCDUtil;
import ru.decide.utils.aura.RayTraceUtil;
import ru.decide.utils.aura.UAttack;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.math.MathUtil;
import ru.decide.utils.math.ServerUtil;
import ru.decide.utils.player.MoveUtil;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;
import static net.minecraft.util.math.MathHelper.wrapDegrees;

public class RotationProcess extends Component {

    public static RotationTask currentTask = RotationTask.IDLE;
    public static float currentYawSpeed;
    public static float currentPitchSpeed;
    public static float currentYawReturnSpeed;
    public static float currentPitchReturnSpeed;
    public static int currentPriority;
    public static int currentTimeout;
    public static int idleTicks;
    public static Rotation targetRotation;

    public static boolean isRotating() {
        return !currentTask.equals(currentTask.IDLE);
    }

    private void resetRotation() {
        Rotation targetRotation = new Rotation(FreeLookUtil.freeYaw, FreeLookUtil.freePitch);


        if (ServerUtil.isHolyWorld()) {
            stopRotation();
        } else {
            if (updateRotation(targetRotation, currentYawReturnSpeed, currentPitchReturnSpeed)) {
                stopRotation();
            }
        }
    }

    public static void resetParentTimeout() {
        currentTimeout = 0;
        currentTask = RotationTask.IDLE;
        currentPriority = 0;

        FreeLookUtil.setActive(false);
    }


    @EventHandler
    public void onEventMovement(EventMoveInput eventMoveInput) {

        if (currentTask.equals(RotationTask.RESET)) {
            MoveUtil.fixMovement(eventMoveInput, mc.player.getYaw(), mc.gameRenderer.getCamera().getYaw());
        }

    }


    @EventHandler
    public void onEvent(EventTick event) {

        if (currentTask.equals(RotationTask.AIM) && idleTicks > currentTimeout) {
            currentTask = (RotationTask.RESET);
        }


        if (currentTask.equals(RotationTask.RESET)) {
            if (ServerUtil.isHolyWorld() || ServerUtil.isCopyTime()) {
                stopRotation();
            } else {
                resetRotation();
            }
        }
        idleTicks++;
    }



    public static void update(Rotation target, float yawSpeed, float pitchSpeed, float yawReturnSpeed,
                              float pitchReturnSpeed, int timeout, int priority, boolean clientRotation) {
        if (currentPriority > priority) {
            return;
        }

        if (currentTask.equals(RotationTask.IDLE) && !clientRotation) {
            FreeLookUtil.active = true;
        }

        currentYawSpeed = yawSpeed;
        currentPitchSpeed = pitchSpeed;
        currentYawReturnSpeed = yawReturnSpeed;
        currentPitchReturnSpeed = pitchReturnSpeed;
        currentTimeout = timeout;
        currentPriority = priority;
        currentTask = RotationTask.AIM;
        targetRotation = target;

        updateRotation(target, yawSpeed, pitchSpeed);
    }

    public static void update(Rotation targetRotation, float turnSpeed, float returnSpeed, int timeout, int priority) {
        update(targetRotation, turnSpeed, turnSpeed, returnSpeed, returnSpeed, timeout, priority, false);
    }


    static boolean updateRotation(Rotation targetRotation, float yawSpeed, float pitchSpeed) {
        if (mc.player == null)
            return false;

        Rotation currentRotation = new Rotation(mc.player);


        float pitchDelta = targetRotation.pitch - currentRotation.pitch;
        float yawDelta = wrapDegrees(targetRotation.yaw - currentRotation.yaw);

        float clampedYaw = Math.min(Math.abs(yawDelta), yawSpeed);
        float clampedPitch = Math.min(Math.abs(pitchDelta), pitchSpeed);


        mc.player.setYaw(
                mc.player.headYaw += GCDUtil.getSensitivity(MathHelper.clamp(yawDelta, -clampedYaw, clampedYaw)));

        mc.player.setPitch(MathHelper.clamp(
                mc.player.getPitch()
                        + GCDUtil.getSensitivity(MathHelper.clamp(pitchDelta, -clampedPitch, clampedPitch)),
                -90F, 90F));


        idleTicks = 0;
        return new Rotation(mc.player).getDelta(targetRotation) < 1F;
    }

    public void stopRotation() {
        currentTask = (RotationTask.IDLE);
        currentPriority = (0);
        FreeLookUtil.setActive(false);

    }


    public enum RotationTask {
        AIM,
        RESET,
        IDLE
    }

    private final BufferAllocator boxAllocator = new BufferAllocator(1 << 18);
    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        boxAllocator.clear();
    }
    private float animationNurik = 0.0F;
    private long currentTimeSpirits = 0;
    public LivingEntity target = null;
    public Animation alpha = new Animation();
    public Animation alpha_2 = new Animation();

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
    private static final RenderPipeline RING_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("decide", "ring_esp_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );
    private static final RenderPipeline RING_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("decide", "ring_esp_line"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );
    private static final RenderLayer RING_FILL_LAYER = RenderLayer.of("ring_esp_fill",
            RenderSetup.builder(RING_FILL_PIPELINE).expectedBufferSize(1 << 16).build());
    private static final RenderLayer RING_LINE_LAYER = RenderLayer.of("ring_esp_line",
            RenderSetup.builder(RING_LINE_PIPELINE).expectedBufferSize(1 << 14).build());



    private interface JitterPreset {
        float getYaw(long time);
        float getPitch(long time);
    }
    private final JitterPreset[] jitterPresets = new JitterPreset[] {

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.sin(t / 80D) + Math.cos(t / 35D) * 0.35) * 13);
                }
                public float getPitch(long t) {
                    return (float) ((Math.cos(t / 95D) + Math.sin(t / 42D) * 0.4) * 11);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) (Math.sin(t / 55D + Math.sin(t / 350D)) * 15);
                }
                public float getPitch(long t) {
                    return (float) (Math.cos(t / 65D + Math.cos(t / 280D)) * 14);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.cos(t / 42D) * 0.7 + Math.sin(t / 120D)) * 17);
                }
                public float getPitch(long t) {
                    return (float) ((Math.sin(t / 58D) * 0.5 + Math.cos(t / 140D)) * 12);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) (Math.sin(t / 25D) * (9 + Math.sin(t / 300D) * 4));
                }
                public float getPitch(long t) {
                    return (float) (Math.cos(t / 48D) * (13 + Math.cos(t / 250D) * 3));
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.sin(t / 70D) + Math.sin(t / 17D) * 0.25) * 16);
                }
                public float getPitch(long t) {
                    return (float) ((Math.cos(t / 90D) + Math.cos(t / 23D) * 0.2) * 13);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.cos(t / 110D) * 0.8 + Math.sin(t / 38D) * 0.6) * 12);
                }
                public float getPitch(long t) {
                    return (float) ((Math.sin(t / 100D) * 0.7 + Math.cos(t / 29D) * 0.4) * 15);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) (Math.sin(t / 170D + 1.2) * 21);
                }
                public float getPitch(long t) {
                    return (float) ((Math.cos(t / 52D) + Math.sin(t / 240D)) * 10);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.cos(t / 36D) * 0.45 + Math.sin(t / 145D)) * 18);
                }
                public float getPitch(long t) {
                    return (float) ((Math.sin(t / 44D) * 0.35 + Math.cos(t / 180D)) * 14);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) (Math.sin(t / 60D + Math.cos(t / 180D)) * 14);
                }
                public float getPitch(long t) {
                    return (float) (Math.cos(t / 73D + Math.sin(t / 210D)) * 17);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.sin(t / 48D) * 0.9 + Math.cos(t / 140D) * 0.5) * 15);
                }
                public float getPitch(long t) {
                    return (float) ((Math.cos(t / 84D) * 0.8 + Math.sin(t / 32D) * 0.3) * 12);
                }
            },
    };

    private interface SpeedPreset {
        float getYawSpeed();
        float getPitchSpeed();
    }

    private final SpeedPreset[] speedPresets = new SpeedPreset[]{

            new SpeedPreset() {
                public float getYawSpeed() { return 40F; }
                public float getPitchSpeed() { return 12F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 55F; }
                public float getPitchSpeed() { return 15F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 30F; }
                public float getPitchSpeed() { return 10F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 48F; }
                public float getPitchSpeed() { return 18F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 60F; }
                public float getPitchSpeed() { return 14F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 35F; }
                public float getPitchSpeed() { return 16F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 50F; }
                public float getPitchSpeed() { return 13F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 44F; }
                public float getPitchSpeed() { return 17F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 58F; }
                public float getPitchSpeed() { return 11F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 32F; }
                public float getPitchSpeed() { return 19F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 46F; }
                public float getPitchSpeed() { return 12F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 53F; }
                public float getPitchSpeed() { return 15F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 37F; }
                public float getPitchSpeed() { return 18F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 42F; }
                public float getPitchSpeed() { return 13F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 57F; }
                public float getPitchSpeed() { return 16F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 34F; }
                public float getPitchSpeed() { return 11F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 49F; }
                public float getPitchSpeed() { return 20F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 62F; }
                public float getPitchSpeed() { return 14F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 39F; }
                public float getPitchSpeed() { return 17F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 52F; }
                public float getPitchSpeed() { return 12F; }
            }
    };

    private float currentSpeedYaw = 0;
    private float currentSpeedPitch = 0;

    float jitterIntensity = 0;


    private long jitterSwitchTime = ThreadLocalRandom.current().nextLong(1000L, 2001L);
    private long nextJitterSwitch = System.currentTimeMillis() + jitterSwitchTime;
    private int currentJitterPreset = 0;
    private int nextJitterPreset = 1;

    private long speedSwitchTime = ThreadLocalRandom.current().nextLong(2000L, 4001L);
    private long nextSpeedSwitch = System.currentTimeMillis() + speedSwitchTime;
    private int currentSpeedPreset = 0;
    private int nextSpeedPreset = 1;


    private static final long JITTER_SWITCH_TIME = 1000L;

    private float lastYawJitter;
    private float lastPitchJitter;

    private static final long SPEED_SWITCH_TIME = 2500L;

    private float lastYawSpeed;
    private float lastPitchSpeed;

    int tick;

    private void renderCorner(MatrixStack matrices,
                              VertexConsumer consumer,
                              float x,
                              float y,
                              float rotation,
                              float scale,
                              int c1,
                              int c2,
                              int c3,
                              int c4,
                              float alpha) {

        matrices.push();

        matrices.translate(x, y, 0);

        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));

        matrices.scale(scale * 0.6F , scale * 0.6F , 1F);

        Matrix4f mat = matrices.peek().getPositionMatrix();

        drawGradientQuad(
                consumer,
                mat,
                c1,
                c2,
                c3,
                c4,
                (int)(255 * alpha)
        );

        matrices.pop();
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


    private static void drawGradientQuad(VertexConsumer buffer, Matrix4f matrix,int color,int color2,int color3,int color4, int alpha) {

        buffer.vertex(matrix, -0.5f, -0.5f, 0.0f).color(ColorUtil.replAlpha(color, alpha)).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, -0.5f, 0.0f).color(ColorUtil.replAlpha(color2, alpha)).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, 0.5f, 0.0f).color(ColorUtil.replAlpha(color3, alpha)).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, -0.5f, 0.5f, 0.0f).color(ColorUtil.replAlpha(color4, alpha)).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
    }

}

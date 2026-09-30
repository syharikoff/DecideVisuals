package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.math.ChatUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ModuleInfo(
        name = "Penguin Companion",
        desc = "Renders the gugu gaga penguin model near the player",
        category = Category.VISUALS
)
public class PenguinCompanion extends Module {

    private static final Logger LOGGER = LoggerFactory.getLogger("client/PenguinCompanion");
    private static final Identifier MODEL_ID = Identifier.of("client", "model_new/companion_mesh.bin");
    private static final String TEXTURE_PREFIX = "model_new/textures/";

    public SliderSetting scale = new SliderSetting(this, "Scale", 2.45F, 0.35F, 4.0F, 0.05F);

    private final BufferAllocator allocator = new BufferAllocator(1 << 20);
    private NanalyMesh mesh;
    private boolean loadAttempted;
    private Vec3d companionPos;
    private float companionYaw;
    private float walkCycle;
    private float walkAmount;
    private long lastFrameNanos;

    @Override
    protected void onEnable() {
        companionPos = null;
        walkCycle = 0.0F;
        walkAmount = 0.0F;
    }

    @EventHandler
    public void onRender3D(EventRender3D e) {
        if (mc.player == null || mc.world == null) return;

        NanalyMesh model = getMesh();
        if (model == null) return;

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        LivingEntity followTarget = getFollowTarget();
        Vec3d desiredPos = getDesiredPosition(followTarget, e.getTickDelta());
        updateCompanionPosition(desiredPos, followTarget);
        Vec3d worldPos = companionPos;
        Vec3d pos = worldPos.subtract(cam);

        matrices.push();
        matrices.translate(pos.x, pos.y, pos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - companionYaw));
        matrices.scale(scale.getValue(), scale.getValue(), scale.getValue());

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

        for (MeshGroup group : model.groups) {
            VertexConsumer buffer = immediate.getBuffer(layer(group.texture));
            for (int i = 0; i < group.vertices.length; i += 8) {
                float sourceX = group.vertices[i];
                float sourceDepth = group.vertices[i + 1];
                float sourceY = group.vertices[i + 2];
                float u = group.vertices[i + 3];
                float v = group.vertices[i + 4];

                float x = sourceX;
                float y = sourceY - model.minY;
                float z = -(sourceDepth - model.centerDepth);
                float[] folded = foldArms(model, group.texture, sourceX, sourceY, x, y, z);
                float[] animated = animateWalk(model, group.texture, sourceX, sourceY, folded[0], folded[1], folded[2], walkCycle, walkAmount);

                buffer.vertex(matrix, animated[0], animated[1], animated[2]).color(255, 255, 255, 255).texture(u, v);
            }
        }

        immediate.draw();
        matrices.pop();
    }

    private LivingEntity getFollowTarget() {
        return mc.player;
    }

    private Vec3d getDesiredPosition(LivingEntity followTarget, float tickDelta) {
        Vec3d targetPos = followTarget.getLerpedPos(tickDelta);
        if (followTarget == mc.player) {
            float yaw = mc.player.getYaw();
            double radians = Math.toRadians(yaw);
            Vec3d look = new Vec3d(-Math.sin(radians), 0.0, Math.cos(radians));
            return targetPos.add(look.multiply(1.15));
        }

        Vec3d awayFromPlayer = targetPos.subtract(mc.player.getLerpedPos(tickDelta));
        if (awayFromPlayer.horizontalLengthSquared() < 0.0001) {
            awayFromPlayer = new Vec3d(0.0, 0.0, 1.0);
        } else {
            awayFromPlayer = awayFromPlayer.normalize();
        }
        return targetPos.add(awayFromPlayer.multiply(0.95));
    }

    private void updateCompanionPosition(Vec3d desiredPos, LivingEntity lookTarget) {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 0.05F : MathHelper.clamp((now - lastFrameNanos) / 1_000_000_000.0F, 0.0F, 0.08F);
        lastFrameNanos = now;

        Vec3d previousPos = companionPos;
        if (companionPos == null || companionPos.squaredDistanceTo(desiredPos) > 256.0) {
            companionPos = desiredPos;
        } else {
            Vec3d toDesired = desiredPos.subtract(companionPos);
            double horizontalDistance = Math.sqrt(toDesired.x * toDesired.x + toDesired.z * toDesired.z);
            if (horizontalDistance > 0.24) {
                double speed = lookTarget == mc.player ? 4.6 : 6.2;
                double step = Math.min(horizontalDistance, speed * delta);
                companionPos = companionPos.add(new Vec3d(toDesired.x / horizontalDistance * step, 0.0, toDesired.z / horizontalDistance * step));
            }
            companionPos = new Vec3d(companionPos.x, desiredPos.y, companionPos.z);
        }

        double moved = 0.0;
        if (previousPos != null) {
            double dx = companionPos.x - previousPos.x;
            double dz = companionPos.z - previousPos.z;
            moved = Math.sqrt(dx * dx + dz * dz);
        }
        float targetWalk = delta <= 0.0F ? 0.0F : MathHelper.clamp((float) (moved / delta) * 0.22F, 0.0F, 1.0F);
        walkAmount = MathHelper.lerp(0.20F, walkAmount, targetWalk);
        if (walkAmount < 0.03F) walkAmount = 0.0F;
        walkCycle += (float) moved * 9.0F;

        Vec3d lookPos = lookTarget.getEntityPos().add(0.0, lookTarget.getHeight() * 0.45, 0.0);
        Vec3d lookVector = lookPos.subtract(companionPos);
        float targetYaw = (float) Math.toDegrees(Math.atan2(lookVector.x, lookVector.z));
        companionYaw = lerpAngle(companionYaw, targetYaw, 0.20F);
    }

    private static float[] foldArms(NanalyMesh model, Identifier texture, float sourceX, float sourceY, float x, float y, float z) {
        float height = (sourceY - model.minY) / Math.max(model.maxY - model.minY, 0.0001F);
        float absX = Math.abs(sourceX);
        String path = texture.getPath();
        boolean cloth = path.contains("cloth");
        if (!cloth || absX < 0.16F || height < 0.22F || height > 0.72F) {
            return new float[]{x, y, z};
        }

        float side = Math.signum(sourceX);
        float outer = smoothStep(0.16F, 0.42F, absX);
        float vertical = smoothBand(height, 0.22F, 0.34F, 0.58F, 0.72F);
        float influence = outer * vertical * 0.72F;
        float targetX = side * 0.15F;
        float targetZ = z + 0.08F;
        return new float[]{
                MathHelper.lerp(influence, x, targetX),
                y - 0.015F * influence,
                MathHelper.lerp(influence, z, targetZ)
        };
    }

    private static float[] animateWalk(NanalyMesh model, Identifier texture, float sourceX, float sourceY, float x, float y, float z, float cycle, float amount) {
        if (amount <= 0.0F) {
            return new float[]{x, y, z};
        }

        float height = (sourceY - model.minY) / Math.max(model.maxY - model.minY, 0.0001F);
        float absX = Math.abs(sourceX);
        float side = sourceX >= 0.0F ? 1.0F : -1.0F;
        float phase = MathHelper.sin(cycle + (side > 0.0F ? 0.0F : MathHelper.PI));
        float lift = Math.max(phase, 0.0F);
        String path = texture.getPath();

        if (path.contains("cloth") && height < 0.20F && absX > 0.025F && absX < 0.22F) {
            float foot = smoothBand(height, 0.00F, 0.03F, 0.16F, 0.22F);
            return new float[]{
                    x + side * 0.010F * lift * foot * amount,
                    y + 0.045F * lift * foot * amount,
                    z + phase * 0.070F * foot * amount
            };
        }

        if (path.contains("cloth") && height >= 0.22F && height <= 0.62F && absX > 0.16F) {
            float paw = smoothBand(height, 0.22F, 0.34F, 0.54F, 0.66F) * smoothStep(0.16F, 0.38F, absX);
            return new float[]{
                    x + side * 0.012F * phase * paw * amount,
                    y + 0.010F * lift * paw * amount,
                    z - phase * 0.035F * paw * amount
            };
        }

        if (height > 0.18F && height < 0.78F && absX < 0.34F) {
            float body = smoothBand(height, 0.18F, 0.36F, 0.62F, 0.78F);
            return new float[]{
                    x + side * 0.002F * phase * body * amount,
                    y,
                    z - Math.abs(phase) * 0.010F * body * amount
            };
        }

        return new float[]{x, y, z};
    }

    private static float lerpAngle(float from, float to, float delta) {
        float diff = MathHelper.wrapDegrees(to - from);
        return from + diff * delta;
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = MathHelper.clamp((value - edge0) / Math.max(edge1 - edge0, 0.0001F), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float smoothBand(float value, float fadeInStart, float fadeInEnd, float fadeOutStart, float fadeOutEnd) {
        return smoothStep(fadeInStart, fadeInEnd, value) * (1.0F - smoothStep(fadeOutStart, fadeOutEnd, value));
    }

    private NanalyMesh getMesh() {
        if (mesh != null || loadAttempted) return mesh;
        loadAttempted = true;

        try {
            Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(MODEL_ID);
            if (resource.isEmpty()) {
                LOGGER.warn("Nanaly mesh not found: {}", MODEL_ID);
                if (mc.player != null) {
                    ChatUtils.addChatMessage("§cPenguin Companion: mesh не найден §7" + MODEL_ID);
                }
                return null;
            }

            try (InputStream in = resource.get().getInputStream()) {
                mesh = NanalyMesh.read(in.readAllBytes());
                LOGGER.info("Loaded Nanaly mesh with {} groups", mesh.groups.length);
                if (mc.player != null) {
                    ChatUtils.addChatMessage("§aPenguin Companion: mesh загружен §7(" + mesh.groups.length + " groups)");
                }
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.error("Failed to load Nanaly mesh", ex);
            if (mc.player != null) {
                ChatUtils.addChatMessage("§cPenguin Companion: ошибка загрузки mesh, смотри latest.log");
            }
        }

        return mesh;
    }

    private static final Map<Identifier, RenderLayer> LAYERS = new HashMap<>();

    private static RenderLayer layer(Identifier texture) {
        return LAYERS.computeIfAbsent(texture, id -> RenderLayer.of(
                "nanaly_model_" + id.getPath().replace('/', '_').replace('.', '_'),
                RenderSetup.builder(MODEL_PIPELINE)
                        .texture("Sampler0", id)
                        .translucent()
                        .expectedBufferSize(1 << 20)
                        .build()
        ));
    }

    private static class NanalyMesh {
        final MeshGroup[] groups;
        final float minY;
        final float maxY;
        final float centerDepth;

        NanalyMesh(MeshGroup[] groups, float minY, float maxY, float centerDepth) {
            this.groups = groups;
            this.minY = minY;
            this.maxY = maxY;
            this.centerDepth = centerDepth;
        }

        static NanalyMesh read(byte[] bytes) {
            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            byte[] magic = new byte[5];
            buf.get(magic);
            if (!"NMSH1".equals(new String(magic, StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("Bad Nanaly mesh magic");
            }

            int groupCount = buf.getInt();
            MeshGroup[] groups = new MeshGroup[groupCount];
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float minDepth = Float.POSITIVE_INFINITY;
            float maxDepth = Float.NEGATIVE_INFINITY;

            for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
                int textureNameLength = Short.toUnsignedInt(buf.getShort());
                byte[] textureNameBytes = new byte[textureNameLength];
                buf.get(textureNameBytes);
                String textureName = new String(textureNameBytes, StandardCharsets.UTF_8);

                int vertexCount = buf.getInt();
                float[] vertices = new float[vertexCount * 8];
                for (int i = 0; i < vertices.length; i++) {
                    vertices[i] = buf.getFloat();
                }

                for (int i = 0; i < vertices.length; i += 8) {
                    minDepth = Math.min(minDepth, vertices[i + 1]);
                    maxDepth = Math.max(maxDepth, vertices[i + 1]);
                    minY = Math.min(minY, vertices[i + 2]);
                    maxY = Math.max(maxY, vertices[i + 2]);
                }

                Identifier texture = Identifier.of("client", TEXTURE_PREFIX + textureName);
                groups[groupIndex] = new MeshGroup(texture, vertices);
            }

            return new NanalyMesh(groups, minY, maxY, (minDepth + maxDepth) * 0.5F);
        }
    }

    private record MeshGroup(Identifier texture, float[] vertices) {
    }

    private static final RenderPipeline MODEL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "nanaly_model"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );
}

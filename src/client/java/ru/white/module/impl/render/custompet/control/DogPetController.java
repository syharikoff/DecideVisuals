package ru.white.module.impl.render.custompet.control;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.module.impl.render.richdog.PetBrain;
import ru.white.module.impl.render.richdog.PetModel;

public final class DogPetController {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Identifier TEX_JACK_RUSSELL = Identifier.of("client", "textures/richdog/djekrussel.png");
    private static final Identifier TEX_DACHSHUND = Identifier.of("client", "textures/richdog/taksa.png");
    private static final Map<Identifier, RenderLayer> LAYERS = new HashMap<>();

    private final PetModel model = new PetModel();
    private final PetBrain brain = new PetBrain();
    private final BufferAllocator allocator = new BufferAllocator(1 << 18);

    public void tick(PlayerEntity player) {
        if (player == null) {
            return;
        }
        brain.setEntity(player);
        brain.onUpdate();
    }

    public void reset() {
        brain.setEntity(null);
    }

    public void render(EventRender3D e, float tickDelta, PlayerEntity player, boolean dachshund) {
        if (player == null || mc.world == null) {
            return;
        }
        brain.setEntity(player);

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d render = brain.getPos().subtract(cam);

        Identifier texture = dachshund ? TEX_DACHSHUND : TEX_JACK_RUSSELL;

        matrices.push();
        matrices.translate(render.x, render.y, render.z);

        model.setupAnim(player.age + tickDelta, brain);

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        VertexConsumer consumer = immediate.getBuffer(layer(texture));
        model.render(matrices, consumer, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, brain);
        immediate.draw();

        matrices.pop();
    }

    private static RenderLayer layer(Identifier id) {
        return LAYERS.computeIfAbsent(id, tex -> RenderLayer.of(
                "custompet_dog_" + tex.getPath().replace('/', '_').replace('.', '_'),
                RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT_NO_CULL)
                        .texture("Sampler0", tex)
                        .expectedBufferSize(1 << 18)
                        .build()
        ));
    }
}
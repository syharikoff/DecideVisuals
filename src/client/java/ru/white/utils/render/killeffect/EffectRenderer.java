package ru.white.utils.render.killeffect;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;

public final class EffectRenderer {
    private static final int FULL_LIGHT = 0xF000F0;

    private EffectRenderer() {}

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d camera, EffectManager.ActiveEffect effect) {
        BBModel model = effect.effect().model();
        if (model == null) return;

        matrices.push();
        matrices.translate(
                effect.position().x - camera.x,
                effect.position().y - camera.y,
                effect.position().z - camera.z);
        matrices.multiply(((Quaternionfc) RotationAxis.POSITIVE_Y.rotationDegrees(effect.spawnYaw() + 180.0f)));

        float scale = effect.effect().scale();
        if (scale != 1.0f) {
            matrices.scale(scale, scale, scale);
        }

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);

        float seconds = effect.timeSeconds();
        BBAnimation animation = model.animations.isEmpty() ? null : model.animations.get(0);

        for (BBModel.Bone bone : model.roots) {
            renderBone(bone, matrices, consumers, model, animation, seconds, BBModel.Vec.ZERO, effect.skinTexture());
        }

        GlStateManager._disableBlend();
        matrices.pop();
    }

    private static void renderBone(BBModel.Bone bone, MatrixStack matrices, VertexConsumerProvider consumers,
                                    BBModel model, BBAnimation animation, float seconds, BBModel.Vec parentOrigin,
                                    Identifier skinTexture) {
        if (!bone.visible()) return;

        BBAnimation.Transform animated = BBAnimation.Transform.IDENTITY;
        if (animation != null) {
            animated = animation.sample(bone.uuid(), seconds);
        }

        matrices.push();
        BBModel.Vec relative = bone.origin().sub(parentOrigin).add(animated.position);
        matrices.translate(relative.x() / 16.0f, relative.y() / 16.0f, relative.z() / 16.0f);
        rotate(matrices, bone.rotation().add(animated.rotation));
        BBModel.Vec s = animated.scale;
        matrices.scale(s.x(), s.y(), s.z());

        for (BBModel.Cube cube : bone.cubes()) {
            renderCube(cube, bone.origin(), matrices, consumers, model, skinTexture);
        }
        for (BBModel.Bone child : bone.children()) {
            renderBone(child, matrices, consumers, model, animation, seconds, bone.origin(), skinTexture);
        }
        matrices.pop();
    }

    private static void renderCube(BBModel.Cube cube, BBModel.Vec boneOrigin, MatrixStack matrices,
                                    VertexConsumerProvider consumers, BBModel model, Identifier skinTexture) {
        matrices.push();
        BBModel.Vec pivot = cube.origin().sub(boneOrigin);
        matrices.translate(pivot.x() / 16.0f, pivot.y() / 16.0f, pivot.z() / 16.0f);
        rotate(matrices, cube.rotation());

        float x1 = (Math.min(cube.from().x(), cube.to().x()) - cube.origin().x()) / 16.0f;
        float y1 = (Math.min(cube.from().y(), cube.to().y()) - cube.origin().y()) / 16.0f;
        float z1 = (Math.min(cube.from().z(), cube.to().z()) - cube.origin().z()) / 16.0f;
        float x2 = (Math.max(cube.from().x(), cube.to().x()) - cube.origin().x()) / 16.0f;
        float y2 = (Math.max(cube.from().y(), cube.to().y()) - cube.origin().y()) / 16.0f;
        float z2 = (Math.max(cube.from().z(), cube.to().z()) - cube.origin().z()) / 16.0f;

        for (BBModel.Face face : cube.faces()) {
            float width = x2 - x1, height = y2 - y1, depth = z2 - z1;
            boolean degenerate = switch (face.direction()) {
                case "north", "south" -> width < 5.0E-4f || height < 5.0E-4f;
                case "east", "west" -> height < 5.0E-4f || depth < 5.0E-4f;
                case "up", "down" -> width < 5.0E-4f || depth < 5.0E-4f;
                default -> true;
            };
            if (!degenerate) renderFace(face, x1, y1, z1, x2, y2, z2, matrices, consumers, model, skinTexture);
        }
        matrices.pop();
    }

    private static void renderFace(BBModel.Face face, float x1, float y1, float z1, float x2, float y2, float z2,
                                    MatrixStack matrices, VertexConsumerProvider consumers, BBModel model,
                                    Identifier skinTexture) {
        float nx = 0, ny = 0, nz = 0;
        float[][] positions;
        switch (face.direction()) {
            case "north" -> { positions = new float[][]{{x2,y2,z1},{x2,y1,z1},{x1,y1,z1},{x1,y2,z1}}; nz = -1; }
            case "south" -> { positions = new float[][]{{x1,y2,z2},{x1,y1,z2},{x2,y1,z2},{x2,y2,z2}}; nz = 1; }
            case "east"  -> { positions = new float[][]{{x2,y2,z2},{x2,y1,z2},{x2,y1,z1},{x2,y2,z1}}; nx = 1; }
            case "west"  -> { positions = new float[][]{{x1,y2,z1},{x1,y1,z1},{x1,y1,z2},{x1,y2,z2}}; nx = -1; }
            case "up"    -> { positions = new float[][]{{x1,y2,z1},{x1,y2,z2},{x2,y2,z2},{x2,y2,z1}}; ny = 1; }
            case "down"  -> { positions = new float[][]{{x1,y1,z2},{x1,y1,z1},{x2,y1,z1},{x2,y1,z2}}; ny = -1; }
            default -> { return; }
        }

        if (face.texture() < 0 || model.textures.isEmpty()) return;
        int texIndex = Math.min(model.textures.size() - 1, face.texture());
        BBModel.Texture texture = model.textures.get(texIndex);

        Identifier textureId = texture.id();
        if (skinTexture != null && isBodyTexture(texture)) {
            textureId = skinTexture;
        }

        RenderLayer layer = entityCutout(textureId);
        VertexConsumer vertices = consumers.getBuffer(layer);

        float uvW = texture.uvWidth(), uvH = texture.uvHeight();
        if (skinTexture != null && isBodyTexture(texture)) {
            uvW = 64.0f;
            uvH = 64.0f;
        }

        float u1 = face.u1() / uvW, v1 = face.v1() / uvH;
        float u2 = face.u2() / uvW, v2 = face.v2() / uvH;
        float[][] baseUv = {{u1,v1},{u1,v2},{u2,v2},{u2,v1}};
        int uvTurns = Math.floorMod(face.rotation() / 90, 4);
        float[][] uv = new float[4][];
        for (int i = 0; i < 4; i++) uv[i] = baseUv[(i + uvTurns) % 4];

        Matrix4f mat = matrices.peek().getPositionMatrix();
        for (int i = 0; i < 4; i++) {
            vertices.vertex(mat, positions[i][0], positions[i][1], positions[i][2])
                    .color(255, 255, 255, 255)
                    .texture(uv[i][0], uv[i][1])
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(FULL_LIGHT)
                    .normal(nx, ny, nz);
        }
    }

    private static boolean isBodyTexture(BBModel.Texture texture) {
        String name = texture.name().toLowerCase();
        return name.contains("body_grey") || name.contains("body_gray") || name.contains("player_body");
    }

    private static void rotate(MatrixStack matrices, BBModel.Vec rotation) {
        if (rotation.z() != 0) matrices.multiply(((Quaternionfc) RotationAxis.POSITIVE_Z.rotationDegrees(rotation.z())));
        if (rotation.y() != 0) matrices.multiply(((Quaternionfc) RotationAxis.POSITIVE_Y.rotationDegrees(rotation.y())));
        if (rotation.x() != 0) matrices.multiply(((Quaternionfc) RotationAxis.POSITIVE_X.rotationDegrees(rotation.x())));
    }

    private static RenderLayer entityCutout(Identifier textureId) {
        RenderSetup renderSetup = RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT_NO_CULL)
                .texture("Sampler0", textureId)
                .expectedBufferSize(1 << 18)
                .build();
        return RenderLayer.of("killeffect_entity_cutout", renderSetup);
    }
}

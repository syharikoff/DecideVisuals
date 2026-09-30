package ru.white.cosmetics.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.white.cosmetics.CosmeticCategory;
import ru.white.cosmetics.model.GeoBone;
import ru.white.cosmetics.model.GeoCube;
import ru.white.cosmetics.model.GeoQuad;
import ru.white.cosmetics.model.GeoVertex;

/**
 * Геометрия косметики, рисуемая в мировых координатах (питомцы).
 * <p>
 * Отличие от {@link CosmeticCommandGeoRenderer}: здесь матрицы уже в системе координат мира,
 * поэтому нужен дополнительный разворот на 180° вокруг Y и инверсия оси Y — иначе питомец
 * будет лежать лицом в пол и отражаться зеркально.
 */
public final class CosmeticWorldGeoRenderer {

    public static boolean renderPet(
            CosmeticCategory category,
            int itemId,
            Identifier textureId,
            MatrixStack matrices,
            VertexConsumerProvider providers,
            int light,
            boolean gliding,
            boolean swimming,
            boolean sneaking,
            boolean moving,
            float timeSeconds
    ) {
        CosmeticSourceModel source = CosmeticSourceModelLoader.get(category, itemId);
        if (source == null || source.getModel() == null || textureId == null) {
            return false;
        }

        source.resetBones();
        String animationName = source.chooseAnimation(gliding, swimming, sneaking, moving);
        if (animationName != null) {
            CosmeticSourceModel.AnimationData animation = source.getAnimations().get(animationName);
            if (animation != null) {
                CosmeticCommandGeoRenderer.applyAnimation(source, animation, timeSeconds);
            }
        }

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
        if (source.getYaw() != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(source.getYaw()));
        if (source.getPitch() != 0.0F) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(source.getPitch()));
        if (source.getRoll() != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(source.getRoll()));
        matrices.scale(source.getScale(), source.getScale(), source.getScale());

        // Разворот вокруг пивота корневого bone, иначе инверсия Y сместит модель
        if (!source.getModel().getTopLevelBones().isEmpty()) {
            GeoBone root = source.getModel().getTopLevelBones().get(0);
            float px = root.getPivotX() / 16.0F;
            float py = root.getPivotY() / 16.0F;
            float pz = root.getPivotZ() / 16.0F;
            matrices.translate(px, py, pz);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
            matrices.scale(1.0F, -1.0F, 1.0F);
            matrices.translate(-px, -py, -pz);
        } else {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
            matrices.scale(1.0F, -1.0F, 1.0F);
        }

        VertexConsumer consumer = providers.getBuffer(RenderLayers.entityCutoutNoCull(textureId));
        float frameV = source.frameVOffset(timeSeconds);
        try {
            for (GeoBone bone : source.getModel().getTopLevelBones()) {
                renderBone(bone, matrices, consumer, light, frameV);
            }
        } finally {
            matrices.pop();
        }
        return true;
    }

    private static void renderBone(GeoBone bone, MatrixStack matrices, VertexConsumer consumer, int light, float frameV) {
        if (bone.isHidden()) {
            return;
        }
        matrices.push();
        GeckoRenderHelper.translate(bone, matrices);
        GeckoRenderHelper.moveToPivot(bone, matrices);
        GeckoRenderHelper.rotate(bone, matrices);
        GeckoRenderHelper.scale(bone, matrices);
        GeckoRenderHelper.moveBackFromPivot(bone, matrices);

        for (GeoCube cube : bone.getChildCubes()) {
            renderCube(cube, matrices, consumer, light, frameV);
        }
        for (GeoBone child : bone.getChildBones()) {
            renderBone(child, matrices, consumer, light, frameV);
        }

        matrices.pop();
    }

    private static void renderCube(GeoCube cube, MatrixStack matrices, VertexConsumer consumer, int light, float frameV) {
        matrices.push();
        GeckoRenderHelper.moveToPivot(cube, matrices);
        GeckoRenderHelper.rotate(cube, matrices);
        GeckoRenderHelper.moveBackFromPivot(cube, matrices);

        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();
        int overlay = OverlayTexture.DEFAULT_UV;

        float sizeX = cube.getSize().getX();
        float sizeY = cube.getSize().getY();
        float sizeZ = cube.getSize().getZ();
        boolean flatYOrZ = sizeY == 0.0F || sizeZ == 0.0F;
        boolean flatXOrZ = sizeX == 0.0F || sizeZ == 0.0F;
        boolean flatXOrY = sizeX == 0.0F || sizeY == 0.0F;

        for (GeoQuad quad : cube.getQuads()) {
            if (quad == null || quad.getVertices() == null) {
                continue;
            }

            Vector3f normal = new Vector3f(quad.getNormal().getX(), quad.getNormal().getY(), quad.getNormal().getZ());
            normMat.transform(normal);

            float nx = normal.x();
            float ny = normal.y();
            float nz = normal.z();
            if (flatYOrZ && nx < 0.0F) nx = -nx;
            if (flatXOrZ && ny < 0.0F) ny = -ny;
            if (flatXOrY && nz < 0.0F) nz = -nz;

            for (GeoVertex v : quad.getVertices()) {
                consumer.vertex(posMat, v.getPosition().getX(), v.getPosition().getY(), v.getPosition().getZ())
                        .color(255, 255, 255, 255)
                        .texture(v.getTextureU(), v.getTextureV() + frameV)
                        .overlay(overlay)
                        .light(light)
                        .normal(nx, ny, nz);
            }
        }

        matrices.pop();
    }

    private CosmeticWorldGeoRenderer() {
    }
}

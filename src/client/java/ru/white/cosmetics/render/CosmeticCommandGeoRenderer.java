package ru.white.cosmetics.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
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

import java.util.Map;

/**
 * Рендер Bedrock/Geo геометрии косметики (крылья, одежда, шапки, питомцы).
 * <p>
 * Модели пачканы в системе Bedrock, поэтому перед отрисовкой выполняется оборот на 180° вокруг Z.
 * Рендер идёт в системе координат игрока (внутри {@code FeatureRenderer}), то есть
 * ориентацию «сзади» уже развернул ванильный рендер модели.
 */
public final class CosmeticCommandGeoRenderer {
    /**
     * Рендерит геометрию косметики на игроке.
     *
     * @return true, если что-то было отрисовано
     */
    public static boolean render(
            CosmeticCategory category,
            int itemId,
            Identifier textureId,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
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
                applyAnimation(source, animation, timeSeconds);
            }
        }

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
        matrices.translate(source.getX(), source.getY(), source.getZ());

        if (source.getYaw() != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(source.getYaw()));
        if (source.getPitch() != 0.0F) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(source.getPitch()));
        if (source.getRoll() != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(source.getRoll()));

        matrices.scale(source.getScale(), source.getScale(), source.getScale());
        RenderLayer layer = RenderLayers.entityCutoutNoCull(textureId);
        float frameV = source.frameVOffset(timeSeconds);

        for (GeoBone bone : source.getModel().getTopLevelBones()) {
            submitBone(bone, matrices, queue, layer, light, frameV);
        }

        matrices.pop();
        return true;
    }

    private static void submitBone(GeoBone bone, MatrixStack matrices, OrderedRenderCommandQueue queue, RenderLayer layer, int light, float frameV) {
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
            submitCube(cube, matrices, queue, layer, light, frameV);
        }

        for (GeoBone child : bone.getChildBones()) {
            submitBone(child, matrices, queue, layer, light, frameV);
        }

        matrices.pop();
    }

    private static void submitCube(GeoCube cube, MatrixStack matrices, OrderedRenderCommandQueue queue, RenderLayer layer, int light, float frameV) {
        matrices.push();
        GeckoRenderHelper.moveToPivot(cube, matrices);
        GeckoRenderHelper.rotate(cube, matrices);
        GeckoRenderHelper.moveBackFromPivot(cube, matrices);

        queue.submitCustom(matrices, layer, (entry, consumer) -> emitCube(cube, entry, consumer, light, frameV));
        matrices.pop();
    }

    private static void emitCube(GeoCube cube, MatrixStack.Entry entry, VertexConsumer consumer, int light, float frameV) {
        Matrix4f posMat = entry.getPositionMatrix();
        Matrix3f normMat = entry.getNormalMatrix();
        int overlay = OverlayTexture.DEFAULT_UV;

        float sizeX = cube.getSize().getX();
        float sizeY = cube.getSize().getY();
        float sizeZ = cube.getSize().getZ();
        // Плоские кубы (нулевая толщина по одной из осей) светятся не с той стороны —
        // разворачиваем нормаль, иначе грань «съедает» свет и выглядит чёрной.
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
    }

    /**
     * Раскладывает анимацию поверх позы покоя.
     * Вращение и позиция аддитивны к базовому трансформу кости, масштаб мультипликативен —
     * иначе анимация «съедает» статичные пивоты и модель разлетается.
     */
    public static void applyAnimation(CosmeticSourceModel model, CosmeticSourceModel.AnimationData animation, float timeSeconds) {
        float time = animation.loop ? timeSeconds % animation.length : Math.min(timeSeconds, animation.length);
        Map<String, float[]> baseTransforms = model.getInitialBoneTransforms();

        for (Map.Entry<String, CosmeticSourceModel.BoneAnimationData> entry : animation.bones.entrySet()) {
            GeoBone bone = model.findBone(entry.getKey());
            if (bone == null) {
                continue;
            }
            float[] base = baseTransforms.get(entry.getKey());
            if (base == null) {
                base = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
            }
            CosmeticSourceModel.BoneAnimationData boneAnim = entry.getValue();

            if (boneAnim.rotation != null && !boneAnim.rotation.isEmpty()) {
                float[] rot = boneAnim.rotation.sample(time, ZERO3);
                bone.setRotateX(base[0] + (float) Math.toRadians(-rot[0]));
                bone.setRotateY(base[1] + (float) Math.toRadians(-rot[1]));
                bone.setRotateZ(base[2] + (float) Math.toRadians(rot[2]));
            }
            if (boneAnim.position != null && !boneAnim.position.isEmpty()) {
                float[] pos = boneAnim.position.sample(time, ZERO3);
                bone.setPositionX(base[3] + pos[0]);
                bone.setPositionY(base[4] + pos[1]);
                bone.setPositionZ(base[5] + pos[2]);
            }
            if (boneAnim.scale == null || boneAnim.scale.isEmpty()) {
                continue;
            }
            float[] sc = boneAnim.scale.sample(time, ONE3);
            bone.setScaleX(base[6] * sc[0]);
            bone.setScaleY(base[7] * sc[1]);
            bone.setScaleZ(base[8] * sc[2]);
        }
    }

    private static final float[] ZERO3 = {0.0F, 0.0F, 0.0F};
    private static final float[] ONE3 = {1.0F, 1.0F, 1.0F};

    private CosmeticCommandGeoRenderer() {
    }
}

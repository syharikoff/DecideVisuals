/*
 * Decompiled with CFR 0.152.
 */
package ru.decide.cosmetics.geckolib;

import ru.decide.cosmetics.geo.GeoBone;
import ru.decide.cosmetics.geo.GeoCube;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(value=EnvType.CLIENT)
public class GeckoRenderHelper {
    private static final Vector3f POSITIVE_X = new Vector3f(1.0f, 0.0f, 0.0f);
    private static final Vector3f POSITIVE_Y = new Vector3f(0.0f, 1.0f, 0.0f);
    private static final Vector3f POSITIVE_Z = new Vector3f(0.0f, 0.0f, 1.0f);

    public static void translate(GeoBone bone, MatrixStack matrices) {
        matrices.translate(-bone.getPositionX() / 16.0f, bone.getPositionY() / 16.0f, bone.getPositionZ() / 16.0f);
    }

    public static void moveToPivot(GeoBone bone, MatrixStack matrices) {
        matrices.translate(bone.getPivotX() / 16.0f, bone.getPivotY() / 16.0f, bone.getPivotZ() / 16.0f);
    }

    public static void moveBackFromPivot(GeoBone bone, MatrixStack matrices) {
        matrices.translate(-bone.getPivotX() / 16.0f, -bone.getPivotY() / 16.0f, -bone.getPivotZ() / 16.0f);
    }

    public static void rotate(GeoBone bone, MatrixStack matrices) {
        if (bone.getRotationZ() != 0.0f) {
            matrices.multiply(GeckoRenderHelper.getRadialQuaternion(POSITIVE_Z, bone.getRotationZ()));
        }
        if (bone.getRotationY() != 0.0f) {
            matrices.multiply(GeckoRenderHelper.getRadialQuaternion(POSITIVE_Y, bone.getRotationY()));
        }
        if (bone.getRotationX() != 0.0f) {
            matrices.multiply(GeckoRenderHelper.getRadialQuaternion(POSITIVE_X, bone.getRotationX()));
        }
    }

    public static void scale(GeoBone bone, MatrixStack matrices) {
        matrices.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
    }

    public static void moveToPivot(GeoCube cube, MatrixStack matrices) {
        matrices.translate(cube.pivot.getX() / 16.0f, cube.pivot.getY() / 16.0f, cube.pivot.getZ() / 16.0f);
    }

    public static void moveBackFromPivot(GeoCube cube, MatrixStack matrices) {
        matrices.translate(-cube.pivot.getX() / 16.0f, -cube.pivot.getY() / 16.0f, -cube.pivot.getZ() / 16.0f);
    }

    public static void rotate(GeoCube cube, MatrixStack matrices) {
        if (cube.rotation.getZ() != 0.0f) {
            matrices.multiply(GeckoRenderHelper.getRadialQuaternion(POSITIVE_Z, cube.rotation.getZ()));
        }
        if (cube.rotation.getY() != 0.0f) {
            matrices.multiply(GeckoRenderHelper.getRadialQuaternion(POSITIVE_Y, cube.rotation.getY()));
        }
        if (cube.rotation.getX() != 0.0f) {
            matrices.multiply(GeckoRenderHelper.getRadialQuaternion(POSITIVE_X, cube.rotation.getX()));
        }
    }

    public static Quaternionf getRadialQuaternion(Vector3f axis, float angle) {
        float sin = (float)Math.sin(angle / 2.0f);
        float x = axis.x() * sin;
        float y = axis.y() * sin;
        float z = axis.z() * sin;
        float cos = (float)Math.cos(angle / 2.0f);
        return new Quaternionf(x, y, z, cos);
    }
}


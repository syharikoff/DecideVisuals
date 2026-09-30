package ru.white.cosmetics.render;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.white.cosmetics.model.GeoBone;
import ru.white.cosmetics.model.GeoCube;

public class GeckoRenderHelper {
    private static final Vector3f POSITIVE_X = new Vector3f(1.0F, 0.0F, 0.0F);
    private static final Vector3f POSITIVE_Y = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Vector3f POSITIVE_Z = new Vector3f(0.0F, 0.0F, 1.0F);

    public static void translate(GeoBone bone, MatrixStack matrices) {
        matrices.translate(-bone.getPositionX() / 16.0F, bone.getPositionY() / 16.0F, bone.getPositionZ() / 16.0F);
    }

    public static void moveToPivot(GeoBone bone, MatrixStack matrices) {
        matrices.translate(bone.getPivotX() / 16.0F, bone.getPivotY() / 16.0F, bone.getPivotZ() / 16.0F);
    }

    public static void moveBackFromPivot(GeoBone bone, MatrixStack matrices) {
        matrices.translate(-bone.getPivotX() / 16.0F, -bone.getPivotY() / 16.0F, -bone.getPivotZ() / 16.0F);
    }

    public static void rotate(GeoBone bone, MatrixStack matrices) {
        if (bone.getRotateZ() != 0.0F) {
            matrices.multiply(getRadialQuaternion(POSITIVE_Z, bone.getRotateZ()));
        }
        if (bone.getRotateY() != 0.0F) {
            matrices.multiply(getRadialQuaternion(POSITIVE_Y, bone.getRotateY()));
        }
        if (bone.getRotateX() != 0.0F) {
            matrices.multiply(getRadialQuaternion(POSITIVE_X, bone.getRotateX()));
        }
    }

    public static void scale(GeoBone bone, MatrixStack matrices) {
        matrices.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
    }

    public static void moveToPivot(GeoCube cube, MatrixStack matrices) {
        matrices.translate(cube.getPivot().getX() / 16.0F, cube.getPivot().getY() / 16.0F, cube.getPivot().getZ() / 16.0F);
    }

    public static void moveBackFromPivot(GeoCube cube, MatrixStack matrices) {
        matrices.translate(-cube.getPivot().getX() / 16.0F, -cube.getPivot().getY() / 16.0F, -cube.getPivot().getZ() / 16.0F);
    }

    public static void rotate(GeoCube cube, MatrixStack matrices) {
        if (cube.getRotation().getZ() != 0.0F) {
            matrices.multiply(getRadialQuaternion(POSITIVE_Z, cube.getRotation().getZ()));
        }
        if (cube.getRotation().getY() != 0.0F) {
            matrices.multiply(getRadialQuaternion(POSITIVE_Y, cube.getRotation().getY()));
        }
        if (cube.getRotation().getX() != 0.0F) {
            matrices.multiply(getRadialQuaternion(POSITIVE_X, cube.getRotation().getX()));
        }
    }

    public static Quaternionf getRadialQuaternion(Vector3f axis, float angle) {
        float half = angle / 2.0F;
        float sin = (float) Math.sin(half);
        float cos = (float) Math.cos(half);
        return new Quaternionf(axis.x() * sin, axis.y() * sin, axis.z() * sin, cos);
    }
}

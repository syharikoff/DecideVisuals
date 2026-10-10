/*
 * Decompiled with CFR 0.152.
 */
package ru.decide.cosmetics.geo;

import ru.decide.cosmetics.geo.GeoCube;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public class GeoBone {
    public GeoBone parent;
    public List<GeoBone> childBones = new ArrayList<GeoBone>();
    public List<GeoCube> childCubes = new ArrayList<GeoCube>();
    public String name;
    public boolean isHidden = false;
    public float rotationPointX;
    public float rotationPointY;
    public float rotationPointZ;
    private float rotateX;
    private float rotateY;
    private float rotateZ;
    private float positionX;
    private float positionY;
    private float positionZ;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private float scaleZ = 1.0f;

    public GeoBone(String name) {
        this.name = name;
    }

    public float getRotationX() {
        return this.rotateX;
    }

    public float getRotationY() {
        return this.rotateY;
    }

    public float getRotationZ() {
        return this.rotateZ;
    }

    public void setRotationX(float x) {
        this.rotateX = x;
    }

    public void setRotationY(float y) {
        this.rotateY = y;
    }

    public void setRotationZ(float z) {
        this.rotateZ = z;
    }

    public float getPositionX() {
        return this.positionX;
    }

    public float getPositionY() {
        return this.positionY;
    }

    public float getPositionZ() {
        return this.positionZ;
    }

    public void setPositionX(float x) {
        this.positionX = x;
    }

    public void setPositionY(float y) {
        this.positionY = y;
    }

    public void setPositionZ(float z) {
        this.positionZ = z;
    }

    public float getScaleX() {
        return this.scaleX;
    }

    public float getScaleY() {
        return this.scaleY;
    }

    public float getScaleZ() {
        return this.scaleZ;
    }

    public void setScaleX(float x) {
        this.scaleX = x;
    }

    public void setScaleY(float y) {
        this.scaleY = y;
    }

    public void setScaleZ(float z) {
        this.scaleZ = z;
    }

    public float getPivotX() {
        return this.rotationPointX;
    }

    public float getPivotY() {
        return this.rotationPointY;
    }

    public float getPivotZ() {
        return this.rotationPointZ;
    }
}


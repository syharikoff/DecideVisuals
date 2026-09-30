package ru.white.cosmetics.model;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GeoBone {
    public GeoBone parent;
    public List<GeoBone> childBones = new ArrayList<>();
    public List<GeoCube> childCubes = new ArrayList<>();
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

    private float scaleX = 1.0F;
    private float scaleY = 1.0F;
    private float scaleZ = 1.0F;

    public GeoBone(String name) {
        this.name = name;
    }

    public float getPivotX() { return rotationPointX; }
    public float getPivotY() { return rotationPointY; }
    public float getPivotZ() { return rotationPointZ; }

    public void setPivotX(float v) { this.rotationPointX = v; }
    public void setPivotY(float v) { this.rotationPointY = v; }
    public void setPivotZ(float v) { this.rotationPointZ = v; }
}

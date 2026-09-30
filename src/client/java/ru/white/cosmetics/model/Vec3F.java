package ru.white.cosmetics.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Vec3F {
    public static final Vec3F NULL_VECTOR = new Vec3F(0.0F, 0.0F, 0.0F);

    public float x;
    public float y;
    public float z;

    public Vec3F(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3F crossProduct(Vec3F o) {
        return new Vec3F(
                this.y * o.z - this.z * o.y,
                this.z * o.x - this.x * o.z,
                this.x * o.y - this.y * o.x
        );
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}

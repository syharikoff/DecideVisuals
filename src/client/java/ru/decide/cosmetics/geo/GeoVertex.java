/*
 * Decompiled with CFR 0.152.
 */
package ru.decide.cosmetics.geo;

import ru.decide.cosmetics.geo.Vec3F;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public class GeoVertex {
    public Vec3F position;
    public float textureU;
    public float textureV;

    public GeoVertex(float x, float y, float z, float u, float v) {
        this.position = new Vec3F(x, y, z);
        this.textureU = u;
        this.textureV = v;
    }

    public GeoVertex(Vec3F position, float u, float v) {
        this.position = position;
        this.textureU = u;
        this.textureV = v;
    }
}


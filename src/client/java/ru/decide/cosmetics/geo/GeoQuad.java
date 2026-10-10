/*
 * Decompiled with CFR 0.152.
 */
package ru.decide.cosmetics.geo;

import ru.decide.cosmetics.geo.GeoVertex;
import ru.decide.cosmetics.geo.Vec3F;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public class GeoQuad {
    public GeoVertex[] vertices;
    public Vec3F normal;

    public GeoQuad(GeoVertex[] vertices, Vec3F normal) {
        this.vertices = vertices;
        this.normal = normal;
    }

    public GeoQuad(GeoVertex[] vertices, float nx, float ny, float nz) {
        this.vertices = vertices;
        this.normal = new Vec3F(nx, ny, nz);
    }
}


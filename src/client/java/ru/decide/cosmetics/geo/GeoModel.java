/*
 * Decompiled with CFR 0.152.
 */
package ru.decide.cosmetics.geo;

import ru.decide.cosmetics.geo.GeoBone;
import ru.decide.cosmetics.geo.GeoCube;
import ru.decide.cosmetics.geo.GeoQuad;
import ru.decide.cosmetics.geo.GeoVertex;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public class GeoModel {
    public List<GeoBone> topLevelBones = new ArrayList<GeoBone>();
    public int textureWidth = 64;
    public int textureHeight = 64;
    public float minX;
    public float maxX;
    public float minY;
    public float maxY;
    public float minZ;
    public float maxZ;
    public float centerX;
    public float centerY;
    public float centerZ;
    public float maxDimension = 1.0f;
    private boolean boundsComputed = false;

    public Optional<GeoBone> getBone(String name) {
        for (GeoBone bone : this.topLevelBones) {
            GeoBone found = this.getBoneRecursively(name, bone);
            if (found == null) continue;
            return Optional.of(found);
        }
        return Optional.empty();
    }

    private GeoBone getBoneRecursively(String name, GeoBone bone) {
        if (bone.name.equals(name)) {
            return bone;
        }
        for (GeoBone child : bone.childBones) {
            GeoBone found = this.getBoneRecursively(name, child);
            if (found == null) continue;
            return found;
        }
        return null;
    }

    public void computeBounds() {
        if (this.boundsComputed) {
            return;
        }
        this.minZ = Float.MAX_VALUE;
        this.minY = Float.MAX_VALUE;
        this.minX = Float.MAX_VALUE;
        this.maxZ = -3.4028235E38f;
        this.maxY = -3.4028235E38f;
        this.maxX = -3.4028235E38f;
        for (GeoBone bone : this.topLevelBones) {
            this.computeBoneBounds(bone);
        }
        if (this.minX == Float.MAX_VALUE) {
            this.minX = -0.5f;
            this.maxX = 0.5f;
            this.minY = 0.0f;
            this.maxY = 1.0f;
            this.minZ = -0.5f;
            this.maxZ = 0.5f;
        }
        this.centerX = (this.minX + this.maxX) / 2.0f;
        this.centerY = (this.minY + this.maxY) / 2.0f;
        this.centerZ = (this.minZ + this.maxZ) / 2.0f;
        float dx = this.maxX - this.minX;
        float dy = this.maxY - this.minY;
        float dz = this.maxZ - this.minZ;
        this.maxDimension = Math.max(dx, Math.max(dy, dz));
        if (this.maxDimension <= 0.001f) {
            this.maxDimension = 1.0f;
        }
        this.boundsComputed = true;
    }

    private void computeBoneBounds(GeoBone bone) {
        for (GeoCube c : bone.childCubes) {
            for (GeoQuad q : c.quads) {
                if (q == null) continue;
                for (GeoVertex v : q.vertices) {
                    this.minX = Math.min(this.minX, v.position.getX());
                    this.maxX = Math.max(this.maxX, v.position.getX());
                    this.minY = Math.min(this.minY, v.position.getY());
                    this.maxY = Math.max(this.maxY, v.position.getY());
                    this.minZ = Math.min(this.minZ, v.position.getZ());
                    this.maxZ = Math.max(this.maxZ, v.position.getZ());
                }
            }
        }
        for (GeoBone child : bone.childBones) {
            this.computeBoneBounds(child);
        }
    }
}


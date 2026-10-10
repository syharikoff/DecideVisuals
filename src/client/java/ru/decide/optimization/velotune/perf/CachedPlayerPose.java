/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.minecraft.ModelPart
 */
package ru.decide.optimization.velotune.perf;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;

@Environment(value=EnvType.CLIENT)
public final class CachedPlayerPose {
    private float[] transforms = new float[0];
    private boolean[] visibility = new boolean[0];
    private int signature;
    private long frame;
    private long capturedNanos;

    public int signature() {
        return this.signature;
    }

    public long frame() {
        return this.frame;
    }

    public long capturedNanos() {
        return this.capturedNanos;
    }

    public void capture(List<ModelPart> parts, int signature, long frame, long capturedNanos) {
        int count = parts.size();
        int floatCount = count * 9;
        int booleanCount = count * 2;
        if (this.transforms.length != floatCount) {
            this.transforms = new float[floatCount];
        }
        if (this.visibility.length != booleanCount) {
            this.visibility = new boolean[booleanCount];
        }
        int f = 0;
        int b = 0;
        for (ModelPart part : parts) {
            this.transforms[f++] = part.originX;
            this.transforms[f++] = part.originY;
            this.transforms[f++] = part.originZ;
            this.transforms[f++] = part.pitch;
            this.transforms[f++] = part.yaw;
            this.transforms[f++] = part.roll;
            this.transforms[f++] = part.xScale;
            this.transforms[f++] = part.yScale;
            this.transforms[f++] = part.zScale;
            this.visibility[b++] = part.visible;
            this.visibility[b++] = part.hidden;
        }
        this.signature = signature;
        this.frame = frame;
        this.capturedNanos = capturedNanos;
    }

    public boolean apply(List<ModelPart> parts) {
        if (this.transforms.length != parts.size() * 9 || this.visibility.length != parts.size() * 2) {
            return false;
        }
        int f = 0;
        int b = 0;
        for (ModelPart part : parts) {
            part.originX = this.transforms[f++];
            part.originY = this.transforms[f++];
            part.originZ = this.transforms[f++];
            part.pitch = this.transforms[f++];
            part.yaw = this.transforms[f++];
            part.roll = this.transforms[f++];
            part.xScale = this.transforms[f++];
            part.yScale = this.transforms[f++];
            part.zScale = this.transforms[f++];
            part.visible = this.visibility[b++];
            part.hidden = this.visibility[b++];
        }
        return true;
    }
}

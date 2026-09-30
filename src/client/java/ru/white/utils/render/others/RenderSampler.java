package ru.white.utils.render.others;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gl.GpuSampler;

public final class RenderSampler {
    private static GpuSampler linear;
    private static GpuSampler nearest;
    private static GpuSampler linearRepeat;

    private RenderSampler() {}

    public static GpuSampler nearest() {
        if (nearest == null) {
            nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
        }
        return nearest;
    }

    public static GpuSampler linear() {
        if (linear == null) {
            linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        }
        return linear;
    }

    public static GpuSampler linearRepeat() {
        if (linearRepeat == null) {
            linearRepeat = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        }
        return linearRepeat;
    }
}

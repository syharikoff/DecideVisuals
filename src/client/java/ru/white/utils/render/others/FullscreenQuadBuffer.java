package ru.white.utils.render.others;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class FullscreenQuadBuffer {
    private static GpuBuffer buffer;

    private FullscreenQuadBuffer() {}

    public static void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
    }

    public static GpuBuffer getOrCreate() {
        GpuDevice gpuDevice = RenderSystem.tryGetDevice();
        if (gpuDevice == null) return null;
        if (buffer != null && !buffer.isClosed()) return buffer;

        float[] vertices = {
            -1.0F, -1.0F, 0.0F,
             1.0F, -1.0F, 0.0F,
             1.0F,  1.0F, 0.0F,
            -1.0F, -1.0F, 0.0F,
             1.0F,  1.0F, 0.0F,
            -1.0F,  1.0F, 0.0F
        };

        ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4).order(ByteOrder.nativeOrder());
        for (float v : vertices) bb.putFloat(v);
        bb.flip();

        buffer = gpuDevice.createBuffer(() -> "decidevisuals:fullscreen_quad", 32, bb);
        return buffer;
    }
}

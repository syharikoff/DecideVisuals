package ru.white.utils.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

public class ShaderEspRenderer {

    private ShaderEspOutlinePipeline outlinePipeline;

    /** Настройки внешнего вида — модуль пишет сюда перед вызовом renderFromFbo. */
    public final ShaderEspOutlinePipeline.Params params = new ShaderEspOutlinePipeline.Params();

    private static ShaderEspRenderer instance;

    public ShaderEspRenderer() {
        instance = this;
    }

    public static ShaderEspRenderer getInstance() {
        if (instance == null) instance = new ShaderEspRenderer();
        return instance;
    }

    private void ensureInitialized() {
        if (outlinePipeline == null) outlinePipeline = new ShaderEspOutlinePipeline();
    }

    public void renderFromFbo(Framebuffer maskFbo, int width, int height) {
        if (maskFbo == null) return;
        ensureInitialized();

        var maskView = maskFbo.getColorAttachmentView();
        if (maskView == null) return;

        Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        if (fb == null || fb.getColorAttachmentView() == null) return;

        outlinePipeline.render(maskView, fb.getColorAttachmentView(), width, height, params);
    }

    public void invalidate() {
        if (outlinePipeline != null) {
            outlinePipeline.close();
            outlinePipeline = null;
        }
    }

    public void close() {
        invalidate();
    }
}

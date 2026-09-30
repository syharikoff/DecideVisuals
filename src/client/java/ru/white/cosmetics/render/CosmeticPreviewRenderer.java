package ru.white.cosmetics.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.white.cosmetics.CosmeticCategory;
import ru.white.cosmetics.CosmeticItem;
import ru.white.cosmetics.CosmeticManager;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.Draw;
import ru.white.utils.render.RenderUtil;

public final class CosmeticPreviewRenderer {

    public static void renderPreview(DrawContext context, CosmeticCategory category, int id, float x, float y, float w, float h, float globalAnim) {
        CosmeticItem item = CosmeticManager.get().getItem(category, id);
        if (item == null || item.getPreview() == null || context == null) return;

        float time = (float) (System.currentTimeMillis() % 1000000L);
        renderTextureFallback(category, item.getPreview(), x, y, w, h, time, globalAnim);
    }


    private static void renderTextureFallback(CosmeticCategory category, Identifier tex, float x, float y,
                                               float w, float h, float time, float globalAnim) {
        float rotSpeed = 16.0F;
        float rotAngle = (time / rotSpeed) % 360.0F;
        float rad = (float) Math.toRadians(rotAngle);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        float bob = (float) Math.sin(time / 0.32F) * 2.0F * S();
        float centerX = x + w / 2.0F;
        float centerY = y + h / 2.0F + bob - 1.0F * S();

        float shadowW = (w * 0.45F) * (0.85F + 0.15F * Math.abs(cos));
        float shadowH = 4.5F * S();
        float shadowX = centerX - shadowW / 2.0F;
        float shadowY = y + h - 9.0F * S() - bob * 0.4F;
        RenderUtil.Render2D.rect(shadowX, shadowY, shadowW, shadowH, ColorUtil.getColor(0, 0.35F * globalAnim), shadowH / 2.0F);

        // Вращение по оси Y с 3D-перспективой
        switch (category) {
            case WINGS:
                renderRotatingWings(tex, centerX, centerY, w, h, cos, sin, time, globalAnim);
                break;
            case CAPES:
                renderRotatingCape(tex, centerX, centerY, w, h, cos, sin, time, globalAnim);
                break;
            case HATS:
                renderRotatingHat(tex, centerX, centerY, w, h, cos, sin, time, globalAnim);
                break;
            case CLOTHES:
                renderRotatingClothes(tex, centerX, centerY, w, h, cos, sin, time, globalAnim);
                break;
            case PETS:
                renderRotatingPet(tex, centerX, centerY, w, h, cos, sin, time, globalAnim);
                break;
            case GRAFFITI:
                renderRotatingGraffiti(tex, centerX, centerY, w, h, cos, sin, time, globalAnim);
                break;
        }
    }

    private static float S() {
        return ru.white.screen.Menu.S;
    }

    private static void renderRotatingWings(Identifier tex, float cx, float cy, float w, float h, float cos, float sin, float time, float globalAnim) {
        float maxDim = Math.min(w, h) * 0.72F;
        float flap = (float) Math.sin(time / 180.0) * 0.18F;

        float widthFactor = Math.abs(cos);
        float curW = Math.max(6.0F * S(), maxDim * widthFactor * (1.0F + flap));
        float curH = maxDim;

        float imgX = cx - curW / 2.0F;
        float imgY = cy - curH / 2.0F;

        // Эффект объемного освечения при повороте спиной/лицом
        float brightness = 0.75F + 0.25F * (sin * 0.5F + 0.5F);
        if (cos < 0) brightness *= 0.85F;
        int tint = ColorUtil.getColor((int)(255 * brightness), (int)(255 * brightness), (int)(255 * brightness), globalAnim);

        // Левое и правое крыло с 3D-разворотом
        if (cos >= 0) {
            Draw.texture(tex, imgX, imgY, curW, curH, 0.0F, 0.0F, 1.0F, 1.0F, tint, 1.0F, 2.0F * S());
        } else {
            // Оборотная сторона (зеркальный рендер по оси X)
            Draw.texture(tex, imgX, imgY, curW, curH, 1.0F, 0.0F, 0.0F, 1.0F, tint, 1.0F, 2.0F * S());
        }
    }

    private static void renderRotatingCape(Identifier tex, float cx, float cy, float w, float h, float cos, float sin, float time, float globalAnim) {
        float capeH = Math.min(w, h) * 0.74F;
        float capeW = capeH * 0.62F;

        float widthFactor = Math.abs(cos);
        float curW = Math.max(5.0F * S(), capeW * widthFactor);
        float curH = capeH;

        float imgX = cx - curW / 2.0F;
        float imgY = cy - curH / 2.0F;

        float brightness = 0.7F + 0.3F * (sin * 0.5F + 0.5F);
        if (cos < 0) brightness *= 0.82F;
        int tint = ColorUtil.getColor((int)(255 * brightness), (int)(255 * brightness), (int)(255 * brightness), globalAnim);

        if (cos >= 0) {
            Draw.texture(tex, imgX, imgY, curW, curH, 0.0F, 0.0F, 1.0F, 1.0F, tint, 1.0F, 3.0F * S());
        } else {
            Draw.texture(tex, imgX, imgY, curW, curH, 1.0F, 0.0F, 0.0F, 1.0F, tint, 1.0F, 3.0F * S());
        }
    }

    private static void renderRotatingHat(Identifier tex, float cx, float cy, float w, float h, float cos, float sin, float time, float globalAnim) {
        float size = Math.min(w, h) * 0.65F;
        float widthFactor = Math.abs(cos);
        float curW = Math.max(6.0F * S(), size * (0.35F + 0.65F * widthFactor));
        float curH = size;

        float imgX = cx - curW / 2.0F;
        float imgY = cy - curH / 2.0F;

        float brightness = 0.8F + 0.2F * (sin * 0.5F + 0.5F);
        int tint = ColorUtil.getColor((int)(255 * brightness), (int)(255 * brightness), (int)(255 * brightness), globalAnim);

        if (cos >= 0) {
            Draw.texture(tex, imgX, imgY, curW, curH, 0.0F, 0.0F, 1.0F, 1.0F, tint, 1.0F, 3.0F * S());
        } else {
            Draw.texture(tex, imgX, imgY, curW, curH, 1.0F, 0.0F, 0.0F, 1.0F, tint, 1.0F, 3.0F * S());
        }
    }

    private static void renderRotatingClothes(Identifier tex, float cx, float cy, float w, float h, float cos, float sin, float time, float globalAnim) {
        float size = Math.min(w, h) * 0.66F;
        float widthFactor = Math.abs(cos);
        float curW = Math.max(6.0F * S(), size * (0.3F + 0.7F * widthFactor));
        float curH = size;

        float imgX = cx - curW / 2.0F;
        float imgY = cy - curH / 2.0F;

        float brightness = 0.78F + 0.22F * (sin * 0.5F + 0.5F);
        int tint = ColorUtil.getColor((int)(255 * brightness), (int)(255 * brightness), (int)(255 * brightness), globalAnim);

        if (cos >= 0) {
            Draw.texture(tex, imgX, imgY, curW, curH, 0.0F, 0.0F, 1.0F, 1.0F, tint, 1.0F, 3.0F * S());
        } else {
            Draw.texture(tex, imgX, imgY, curW, curH, 1.0F, 0.0F, 0.0F, 1.0F, tint, 1.0F, 3.0F * S());
        }
    }

    private static void renderRotatingPet(Identifier tex, float cx, float cy, float w, float h, float cos, float sin, float time, float globalAnim) {
        float size = Math.min(w, h) * 0.68F;
        float widthFactor = Math.abs(cos);
        float curW = Math.max(6.0F * S(), size * (0.25F + 0.75F * widthFactor));
        float curH = size;

        float imgX = cx - curW / 2.0F;
        float imgY = cy - curH / 2.0F;

        float brightness = 0.82F + 0.18F * (sin * 0.5F + 0.5F);
        int tint = ColorUtil.getColor((int)(255 * brightness), (int)(255 * brightness), (int)(255 * brightness), globalAnim);

        if (cos >= 0) {
            Draw.texture(tex, imgX, imgY, curW, curH, 0.0F, 0.0F, 1.0F, 1.0F, tint, 1.0F, 3.0F * S());
        } else {
            Draw.texture(tex, imgX, imgY, curW, curH, 1.0F, 0.0F, 0.0F, 1.0F, tint, 1.0F, 3.0F * S());
        }
    }

    private static void renderRotatingGraffiti(Identifier tex, float cx, float cy, float w, float h, float cos, float sin, float time, float globalAnim) {
        float size = Math.min(w, h) * 0.70F;
        float widthFactor = Math.abs(cos);
        float curW = Math.max(5.0F * S(), size * widthFactor);
        float curH = size;

        float imgX = cx - curW / 2.0F;
        float imgY = cy - curH / 2.0F;

        // 3D свечение при развороте
        float glowA = (0.2F + 0.15F * Math.abs(sin)) * globalAnim;
        RenderUtil.Render2D.glow(imgX, imgY, curW, curH, ColorUtil.replAlpha(ColorUtil.client(), glowA), 4.0F * S(), 8.0F * S(), 1.0F);

        float brightness = 0.75F + 0.25F * (sin * 0.5F + 0.5F);
        int tint = ColorUtil.getColor((int)(255 * brightness), (int)(255 * brightness), (int)(255 * brightness), globalAnim);

        if (cos >= 0) {
            Draw.texture(tex, imgX, imgY, curW, curH, 0.0F, 0.0F, 1.0F, 1.0F, tint, 1.0F, 3.0F * S());
        } else {
            Draw.texture(tex, imgX, imgY, curW, curH, 1.0F, 0.0F, 0.0F, 1.0F, tint, 1.0F, 3.0F * S());
        }
    }
}

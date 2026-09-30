package ru.white.cosmetics.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import ru.white.mixin.cosmetics.PlayerCapeStateAccessor;

/**
 * Плащ на спине игрока.
 * <p>
 * Геометрия — ровно ванильный куб {@code cuboid(-5, 0, -1, 10, 16, 1)}, то есть полуширина
 * 0.3125, высота 1.0 и толщина 0.0625 блока. Трансформация тоже ванильная (см. {@code PlayerCapeModel}),
 * поэтому плащ занимает то же место и так же отклоняется при беге и полёте на элитре.
 * <p>
 * Свой модели у косметических плащей нет, текстура лежит в атласе, поэтому UV считаются вручную.
 * Приёмов два, см. {@link CapeUv}.
 */
public final class CosmeticCommandCapeRenderer {

    /** Ванильный куб плаща в блоках. */
    private static final float X0 = -0.3125F;
    private static final float X1 = 0.3125F;
    private static final float Y0 = 0.0F;
    private static final float Y1 = 1.0F;
    private static final float Z0 = -0.0625F;
    private static final float Z1 = 0.0F;

    /**
     * Сетка коробочного unwrap, которую {@code ModelPart.Cuboid} строит для куба 10x16x1
     * в атласе 64x64: 22 ячейки по U и 17 по V. Рисунок плаща занимает ровно этот кусок.
     */
    private static final float GRID_U = 22.0F;
    private static final float GRID_V = 17.0F;

    public static void render(CosmeticCapeData cape, MatrixStack matrices, OrderedRenderCommandQueue queue,
                              int light, float timeSeconds, PlayerEntityRenderState state) {
        if (cape == null || cape.getTexture() == null) return;

        matrices.push();
        matrices.translate(0.0, 0.0, 0.125);

        float capeLean = 0.0F;
        float capeFlap = 0.0F;
        float capeLean2 = 0.0F;
        if (state instanceof PlayerCapeStateAccessor accessor) {
            capeLean = accessor.nightix$capeLean();
            capeFlap = accessor.nightix$capeFlap();
            capeLean2 = accessor.nightix$capeLean2();
        }

        float lean = 6.0F + capeLean / 2.0F + capeFlap;
        float lean2 = capeLean2 / 2.0F;

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(lean));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(lean2));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - lean2));

        CapeUv uv = frameUv(cape, timeSeconds);
        RenderLayer layer = RenderLayers.entityCutoutNoCull(cape.getTexture());
        queue.submitCustom(matrices, layer, (entry, consumer) ->
                emit(consumer, entry.getPositionMatrix(), uv, light));

        matrices.pop();
    }

    /**
     * Окно UV плаща.
     * <p>
     * У анимированных плащей ({@code capeAnimation}) каждый кадр — одна цельная картинка,
     * поэтому окно просто сдвигается по вертикали на текущий кадр.
     * <p>
     * У статичных плащей текстура — атлас 2:1, где рисунок занимает левый верхний угол и
     * состоит из двух зеркальных половин: NORTH-грань берёт левую половину, SOUTH — правую.
     * Если натянуть на грань всё окно целиком, плащ разорвётся пополам швом посередине.
     */
    private static CapeUv frameUv(CosmeticCapeData cape, float timeSeconds) {
        int textureWidth = cape.getTextureWidth();
        int textureHeight = cape.getTextureHeight();

        if (cape.getFrameCount() > 1 && cape.getFrameHeight() > 0 && textureHeight > 0) {
            float frameSeconds = Math.max(1, cape.getFrameTime()) / 20.0F;
            int frame = (int) (timeSeconds / frameSeconds) % cape.getFrameCount();
            return new CapeUv(
                    0.0F,
                    (float) (frame * cape.getFrameHeight()) / textureHeight,
                    Math.min(1.0F, (float) cape.getFrameWidth() / Math.max(1, textureWidth)),
                    (float) ((frame + 1) * cape.getFrameHeight()) / textureHeight,
                    false
            );
        }

        float[] bounds = CosmeticTextureInfo.contentBounds(cape.getTexture());
        if (bounds != null) {
            return new CapeUv(bounds[0], bounds[1], bounds[2], bounds[3], true);
        }
        // Текстуру не прочитали: у атласов 2:1 рисунок живёт в левом верхнем углу
        if (textureWidth > 0 && textureHeight > 0 && Math.abs((float) textureWidth / textureHeight - 2.0F) < 0.25F) {
            return new CapeUv(0.0F, 0.0F, 0.34F, 0.525F, true);
        }
        return new CapeUv(0.0F, 0.0F, 1.0F, 1.0F, false);
    }

    private static void emit(VertexConsumer vc, Matrix4f m, CapeUv uv, int light) {
        if (uv.atlas()) {
            emitAtlasCube(vc, m, uv, light);
        } else {
            emitFlatCape(vc, m, uv, light);
        }
    }

    /**
     * Атлас из двух зеркальных половин: раскладка граней один-в-один как в
     * {@code ModelPart.Cuboid} для куба 10x16x1, но сетка 22x17 натягивается на найденное
     * окно текстуры (в атласе 2:1 оно растянуто, поэтому пропорции берём из окна, а не 1/64).
     */
    private static void emitAtlasCube(VertexConsumer vc, Matrix4f m, CapeUv uv, int light) {
        float du = (uv.u1() - uv.u0()) / GRID_U;
        float dv = (uv.v1() - uv.v0()) / GRID_V;

        // NORTH — внешняя грань плаща, левая половина рисунка
        quad(vc, m,
                X1, Y0, Z0, X0, Y0, Z0, X0, Y1, Z0, X1, Y1, Z0,
                uv.u0() + 11.0F * du, uv.v0() + 1.0F * dv,
                uv.u0() + 1.0F * du, uv.v0() + 1.0F * dv,
                uv.u0() + 1.0F * du, uv.v0() + 17.0F * dv,
                uv.u0() + 11.0F * du, uv.v0() + 17.0F * dv,
                0.0F, 0.0F, -1.0F, light);

        // SOUTH — внутренняя грань, правая половина рисунка
        quad(vc, m,
                X0, Y0, Z1, X1, Y0, Z1, X1, Y1, Z1, X0, Y1, Z1,
                uv.u0() + 22.0F * du, uv.v0() + 1.0F * dv,
                uv.u0() + 12.0F * du, uv.v0() + 1.0F * dv,
                uv.u0() + 12.0F * du, uv.v0() + 17.0F * dv,
                uv.u0() + 22.0F * du, uv.v0() + 17.0F * dv,
                0.0F, 0.0F, 1.0F, light);

        // WEST — левый торец
        quad(vc, m,
                X0, Y0, Z0, X0, Y0, Z1, X0, Y1, Z1, X0, Y1, Z0,
                uv.u0() + 1.0F * du, uv.v0() + 1.0F * dv,
                uv.u0(), uv.v0() + 1.0F * dv,
                uv.u0(), uv.v0() + 17.0F * dv,
                uv.u0() + 1.0F * du, uv.v0() + 17.0F * dv,
                -1.0F, 0.0F, 0.0F, light);

        // EAST — правый торец
        quad(vc, m,
                X1, Y0, Z1, X1, Y0, Z0, X1, Y1, Z0, X1, Y1, Z1,
                uv.u0() + 12.0F * du, uv.v0() + 1.0F * dv,
                uv.u0() + 11.0F * du, uv.v0() + 1.0F * dv,
                uv.u0() + 11.0F * du, uv.v0() + 17.0F * dv,
                uv.u0() + 12.0F * du, uv.v0() + 17.0F * dv,
                1.0F, 0.0F, 0.0F, light);

        // Верхний торец
        quad(vc, m,
                X1, Y1, Z0, X0, Y1, Z0, X0, Y1, Z1, X1, Y1, Z1,
                uv.u0() + 21.0F * du, uv.v0() + 1.0F * dv,
                uv.u0() + 11.0F * du, uv.v0() + 1.0F * dv,
                uv.u0() + 11.0F * du, uv.v0(),
                uv.u0() + 21.0F * du, uv.v0(),
                0.0F, 1.0F, 0.0F, light);

        // Нижний торец
        quad(vc, m,
                X1, Y0, Z1, X0, Y0, Z1, X0, Y0, Z0, X1, Y0, Z0,
                uv.u0() + 11.0F * du, uv.v0() + 1.0F * dv,
                uv.u0() + 1.0F * du, uv.v0() + 1.0F * dv,
                uv.u0() + 1.0F * du, uv.v0(),
                uv.u0() + 11.0F * du, uv.v0(),
                0.0F, -1.0F, 0.0F, light);
    }

    /**
     * Анимированный плащ: в кадре одна цельная картинка, обе большие грани берут её целиком,
     * торцы — тонкой полоской от края, чтобы не было щели на скользящих углах.
     */
    private static void emitFlatCape(VertexConsumer vc, Matrix4f m, CapeUv uv, int light) {
        float u0 = uv.u0();
        float v0 = uv.v0();
        float u1 = uv.u1();
        float v1 = uv.v1();
        float edge = Math.max(1.0F / 2048.0F, (u1 - u0) * 0.02F);
        float dv = v1 - v0;
        float strip = dv * 0.1F;

        // Внешняя грань — картинка в authored-ориентации
        quad(vc, m,
                X1, Y0, Z0, X0, Y0, Z0, X0, Y1, Z0, X1, Y1, Z0,
                u1, v0, u0, v0, u0, v1, u1, v1,
                0.0F, 0.0F, -1.0F, light);
        // Внутренняя грань — зеркальный U, чтобы картинка читалась с обеих сторон
        quad(vc, m,
                X0, Y0, Z1, X1, Y0, Z1, X1, Y1, Z1, X0, Y1, Z1,
                u1, v0, u0, v0, u0, v1, u1, v1,
                0.0F, 0.0F, 1.0F, light);
        // Боковые торцы — узкая полоска от края, чтобы не было щели на скользящих углах
        quad(vc, m,
                X0, Y0, Z0, X0, Y0, Z1, X0, Y1, Z1, X0, Y1, Z0,
                u0, v0, u0 + edge, v0, u0 + edge, v1, u0, v1,
                -1.0F, 0.0F, 0.0F, light);
        quad(vc, m,
                X1, Y0, Z1, X1, Y0, Z0, X1, Y1, Z0, X1, Y1, Z1,
                u1, v0, u1 - edge, v0, u1 - edge, v1, u1, v1,
                1.0F, 0.0F, 0.0F, light);
        // Верхний и нижний торцы берут соответствующую полоску картинки
        quad(vc, m,
                X1, Y1, Z0, X0, Y1, Z0, X0, Y1, Z1, X1, Y1, Z1,
                u1 - edge, v0 + strip, u0 + edge, v0 + strip, u0 + edge, v0, u1 - edge, v0,
                0.0F, 1.0F, 0.0F, light);
        quad(vc, m,
                X1, Y0, Z1, X0, Y0, Z1, X0, Y0, Z0, X1, Y0, Z0,
                u1 - edge, v1 - strip, u0 + edge, v1 - strip, u0 + edge, v1, u1 - edge, v1,
                0.0F, -1.0F, 0.0F, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float aU, float aV, float bU, float bV, float cU, float cV, float dU, float dV,
                             float nx, float ny, float nz, int light) {
        vertex(vc, m, ax, ay, az, aU, aV, nx, ny, nz, light);
        vertex(vc, m, bx, by, bz, bU, bV, nx, ny, nz, light);
        vertex(vc, m, cx, cy, cz, cU, cV, nx, ny, nz, light);
        vertex(vc, m, dx, dy, dz, dU, dV, nx, ny, nz, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int light) {
        vc.vertex(m, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(nx, ny, nz);
    }

    /** Окно UV и признак того, что это атлас из двух половин. */
    private record CapeUv(float u0, float v0, float u1, float v1, boolean atlas) {
    }

    private CosmeticCommandCapeRenderer() {
    }
}

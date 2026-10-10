package ru.decide.module.impl.render.jumpcircle;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Геометрия Jump Circle для режимов «Пульс / Спираль / Нова».
 * Оригинал рисовал всё через Tessellator в два буфера — здесь те же
 * треугольники и линии собираются в два VertexConsumer (fills + lines).
 * Экземпляр один на модуль: часть таблиц переиспользуется как scratch.
 */
public final class CircleShapes {

    private static final float[] SIN_LUT = new float[512];
    private static final float[] COS_LUT = new float[512];

    private static final float[] PULSE_LAYER_A = {0.12F, 0.45F, 1.0F};
    private static final float[] PULSE_LEN_M = {1.5F, 1.0F, 0.65F};
    private static final float[] SPIRAL_LAYER_ALPHA = {0.1F, 0.4F, 0.9F};

    private static final float[] ARC_SPEEDS = {9.0E-4F, -7.0E-4F, 0.0013F, -0.0011F, 6.0E-4F, -0.0015F};
    private static final float[] ARC_LEN = {0.55F, 0.7F, 0.45F, 0.65F, 0.5F, 0.6F};
    private static final float[] ARC_R_MULT = {1.0F, 0.96F, 1.03F, 0.98F, 1.01F, 0.95F};
    private static final int[] ORBIT_DOT_COUNTS = {16, 24};
    private static final float[] ORBIT_SPDS = {0.001F, 6.0E-4F};
    private static final int[] ORBIT_DIRS = {1, -1};
    private static final float[] NOVA_LAYER_A = {0.08F, 0.35F, 0.9F};
    private static final float[] NOVA_LAYER_W = {3.0F, 1.5F, 0.6F};

    private static final double TWO_PI_ORBIT_0 = (Math.PI * 2) / ORBIT_DOT_COUNTS[0];
    private static final double TWO_PI_ORBIT_1 = (Math.PI * 2) / ORBIT_DOT_COUNTS[1];

    private static final int SPOKE_SEGS = 10;
    private static final float[] SPOKE_EDGE = new float[SPOKE_SEGS + 1];

    private static final float[] ARC_GLOW_POW = new float[8];
    private static final float[] DOT_GLOW_POW = new float[5];
    private static final float[] CENTER_GLOW_POW = new float[10];
    private static final float[] HALO_POW = new float[14];
    private static final float[] ARC_EDGE_LUT = new float[64];
    private static final float[] NEB_POW = new float[7];
    private static final float[] PULSE_CENTER_POW = new float[9];

    private static final int TRAIL_SEGS = 40;
    private static final float[] TRAIL_EDGE = new float[TRAIL_SEGS];
    private static final float[] TRAIL_ANGLE_OFFSET = new float[TRAIL_SEGS];
    private static final float[] TRAIL_F = new float[TRAIL_SEGS];
    private static final float[] TRAIL_ANGLE_OFFSET_B = new float[TRAIL_SEGS];
    private static final float[] TRAIL_F_B = new float[TRAIL_SEGS];
    private static final float[] TRAIL_COS_OFFSET = new float[TRAIL_SEGS];
    private static final float[] TRAIL_SIN_OFFSET = new float[TRAIL_SEGS];
    private static final float[] TRAIL_COS_OFFSET_B = new float[TRAIL_SEGS];
    private static final float[] TRAIL_SIN_OFFSET_B = new float[TRAIL_SEGS];

    private static final float[] SPIRAL_HALO_POW = new float[10];
    private static final float[] SPIRAL_INNER_POW = new float[10];
    private static final float[] SPIRAL_TIP_POW = new float[7];

    private static final float TAIL_COS_DELTA = (float) Math.cos(0.1);
    private static final float TAIL_SIN_DELTA = (float) Math.sin(0.1);

    private static final int SPIRAL_SEGS = 80;
    private static final float[] SPIRAL_EDGE_POW07 = new float[SPIRAL_SEGS];

    private final float[] orbitRsBuf = new float[2];
    private final float[] orbitCos0 = new float[ORBIT_DOT_COUNTS[0]];
    private final float[] orbitSin0 = new float[ORBIT_DOT_COUNTS[0]];
    private final float[] orbitCos1 = new float[ORBIT_DOT_COUNTS[1]];
    private final float[] orbitSin1 = new float[ORBIT_DOT_COUNTS[1]];
    private final double[] arcStartA = new double[ARC_SPEEDS.length];

    static {
        for (int i = 0; i < 512; i++) {
            double a = 0.01227184630308513 * i;
            SIN_LUT[i] = (float) Math.sin(a);
            COS_LUT[i] = (float) Math.cos(a);
        }

        for (int i = 0; i <= SPOKE_SEGS; i++) {
            SPOKE_EDGE[i] = (float) Math.sin(i / (double) SPOKE_SEGS * Math.PI);
        }

        for (int g = 0; g < ARC_GLOW_POW.length; g++) {
            float v = 1.0F - g / (float) ARC_GLOW_POW.length;
            ARC_GLOW_POW[g] = v * v;
        }

        for (int g = 0; g < DOT_GLOW_POW.length; g++) {
            float v = 1.0F - g / (float) DOT_GLOW_POW.length;
            DOT_GLOW_POW[g] = v * v;
        }

        for (int g = 0; g < CENTER_GLOW_POW.length; g++) {
            float v = 1.0F - g / (float) CENTER_GLOW_POW.length;
            CENTER_GLOW_POW[g] = v * v * v;
        }

        for (int g = 0; g < HALO_POW.length; g++) {
            HALO_POW[g] = (float) Math.pow(1.0F - g / (float) HALO_POW.length, 2.2F);
        }

        for (int i = 0; i < ARC_EDGE_LUT.length; i++) {
            ARC_EDGE_LUT[i] = (float) Math.sin(i / (double) (ARC_EDGE_LUT.length - 1) * Math.PI);
        }

        for (int b = 0; b < NEB_POW.length; b++) {
            float v = 1.0F - b / (float) NEB_POW.length;
            NEB_POW[b] = (float) Math.pow(v, 1.4F);
        }

        for (int g = 0; g < PULSE_CENTER_POW.length; g++) {
            float v = 1.0F - g / (float) PULSE_CENTER_POW.length;
            PULSE_CENTER_POW[g] = v * v * v;
        }

        for (int seg = 0; seg < TRAIL_SEGS; seg++) {
            float fA = seg / (float) TRAIL_SEGS;
            TRAIL_EDGE[seg] = (float) Math.sin(fA * Math.PI);
            TRAIL_ANGLE_OFFSET[seg] = (float) (fA * Math.PI * 1.6);
            TRAIL_F[seg] = fA;

            float fB = (seg + 1) / (float) TRAIL_SEGS;
            TRAIL_ANGLE_OFFSET_B[seg] = (float) (fB * Math.PI * 1.6);
            TRAIL_F_B[seg] = fB;
        }

        for (int g = 0; g < SPIRAL_HALO_POW.length; g++) {
            float v = 1.0F - g / (float) SPIRAL_HALO_POW.length;
            SPIRAL_HALO_POW[g] = v * v;
        }

        for (int g = 0; g < SPIRAL_INNER_POW.length; g++) {
            float v = 1.0F - g / (float) SPIRAL_INNER_POW.length;
            SPIRAL_INNER_POW[g] = (float) Math.pow(v, 2.5);
        }

        for (int g = 0; g < SPIRAL_TIP_POW.length; g++) {
            float v = 1.0F - g / (float) SPIRAL_TIP_POW.length;
            SPIRAL_TIP_POW[g] = v * v;
        }

        for (int seg = 0; seg < TRAIL_SEGS; seg++) {
            TRAIL_COS_OFFSET[seg] = (float) Math.cos(TRAIL_ANGLE_OFFSET[seg]);
            TRAIL_SIN_OFFSET[seg] = (float) Math.sin(TRAIL_ANGLE_OFFSET[seg]);
            TRAIL_COS_OFFSET_B[seg] = (float) Math.cos(TRAIL_ANGLE_OFFSET_B[seg]);
            TRAIL_SIN_OFFSET_B[seg] = (float) Math.sin(TRAIL_ANGLE_OFFSET_B[seg]);
        }

        for (int i = 0; i < SPIRAL_SEGS; i++) {
            float fA = i / (float) SPIRAL_SEGS;
            SPIRAL_EDGE_POW07[i] = (float) Math.pow(Math.sin(fA * Math.PI), 0.7F);
        }
    }

    // ───────────────────────────── Пульс ─────────────────────────────

    public void drawPulse(VertexConsumer fills, VertexConsumer lines, Matrix4f mat,
                          float r, float alpha, long elapsed, int theme) {
        int transparent = theme & 0xFFFFFF;

        for (int g = 0; g < 12; g++) {
            float gf = g / 12.0F;
            float grI = r * (1.0F + gf * 0.7F);
            float grO = r * (1.0F + (gf + 0.083333336F) * 0.7F);
            float ga = alpha * (1.0F - gf) * (1.0F - gf) * 0.18F;
            batchFilledRing(fills, mat, grI, grO, 48, col(theme, ga));
        }

        int nebSegs = 52;
        int nebBands = 7;
        double elapsedD = elapsed * 0.0028;

        for (int b = 0; b < nebBands; b++) {
            float bf = b / (float) nebBands;
            float pulse = (float) (Math.sin(elapsedD + b * 0.95) * 0.5 + 0.5);
            float bandR = r * (0.15F + bf * 0.72F);
            float thick = r * (0.055F + pulse * 0.035F);
            float ba = alpha * NEB_POW[b] * (0.06F + pulse * 0.1F);
            if (ba >= 0.004F) {
                batchFilledRing(fills, mat, bandR - thick, bandR + thick, nebSegs, col(theme, ba));
            }
        }

        float trailRot = (float) (elapsed * 3.5E-4);
        double elapsedTrail = elapsed * 0.003;
        float halfW = r * 0.03F;

        for (int arm = 0; arm < 2; arm++) {
            float cosBase = (float) Math.cos(arm * Math.PI + trailRot);
            float sinBase = (float) Math.sin(arm * Math.PI + trailRot);

            for (int seg = 0; seg < TRAIL_SEGS - 1; seg++) {
                float edge = TRAIL_EDGE[seg];
                float fA = TRAIL_F[seg];
                float pulse = (float) (Math.sin(elapsedTrail + fA * 5.0F + arm * 2.1) * 0.25 + 0.75);
                float sa = alpha * edge * pulse * 0.22F;
                if (sa < 0.004F) continue;

                float coA = cosBase * TRAIL_COS_OFFSET[seg] - sinBase * TRAIL_SIN_OFFSET[seg];
                float siA = sinBase * TRAIL_COS_OFFSET[seg] + cosBase * TRAIL_SIN_OFFSET[seg];
                float coB = cosBase * TRAIL_COS_OFFSET_B[seg] - sinBase * TRAIL_SIN_OFFSET_B[seg];
                float siB = sinBase * TRAIL_COS_OFFSET_B[seg] + cosBase * TRAIL_SIN_OFFSET_B[seg];
                float dxA = -siA;
                float dxB = -siB;
                float fB = TRAIL_F_B[seg];
                float rA = fA * r * 0.88F;
                float rB = fB * r * 0.88F;

                fills.vertex(mat, coA * rA + dxA * halfW, siA * rA + coA * halfW, 0.0F).color(col(theme, sa));
                fills.vertex(mat, coA * rA - dxA * halfW, siA * rA - coA * halfW, 0.0F).color(col(theme, sa * 0.15F));
                fills.vertex(mat, coB * rB - dxB * halfW, siB * rB - coB * halfW, 0.0F).color(col(theme, sa * 0.15F));
                fills.vertex(mat, coB * rB + dxB * halfW, siB * rB + coB * halfW, 0.0F).color(col(theme, sa));
            }
        }

        float cp = (float) (Math.sin(elapsed * 0.005) * 0.5 + 0.5);
        float cpMod = 0.35F + cp * 0.3F;

        for (int g = 0; g < PULSE_CENTER_POW.length; g++) {
            float gr = 0.006F + g * 0.016F;
            float ga = alpha * PULSE_CENTER_POW[g] * cpMod;
            batchFilledRingOffset(fills, mat, gr * 0.25F, gr, 20, col(theme, ga), 0.0F, 0.0F);
        }

        batchRing(lines, mat, r * 1.12F, 64, col(theme, alpha * 0.05F));
        batchRing(lines, mat, r * 1.06F, 64, col(theme, alpha * 0.14F));
        batchRing(lines, mat, r * 1.02F, 64, col(theme, alpha * 0.35F));
        batchRing(lines, mat, r * 1.0F, 64, col(theme, alpha * 0.8F));
        batchRing(lines, mat, r * 0.98F, 64, col(theme, alpha));

        double rot = elapsed * 6.5E-4;
        double elapsedRay = elapsed * 0.0045;

        for (int i = 0; i < 16; i++) {
            double angle = (Math.PI / 8) * i + rot;
            float pulse = (float) (Math.sin(elapsedRay + i * 0.6) * 0.5 + 0.5);
            float rLen = r * (0.1F + pulse * 0.35F);
            float rS = r * 1.005F;
            float ca = (float) Math.cos(angle);
            float sa2 = (float) Math.sin(angle);
            float pulsedAlphaBase = 0.2F + pulse * 0.6F;

            for (int l = 0; l < 3; l++) {
                float la = alpha * pulsedAlphaBase * PULSE_LAYER_A[l];
                lines.vertex(mat, ca * rS, sa2 * rS, 0.0F).color(col(theme, la));
                lines.vertex(mat, ca * (rS + rLen * PULSE_LEN_M[l]), sa2 * (rS + rLen * PULSE_LEN_M[l]), 0.0F).color(transparent);
            }
        }

        for (int e = 0; e < 3; e++) {
            float ep = (elapsed + e * 500L) % 1600L / 1600.0F;
            float er = r * (1.0F + ep * 0.85F);
            float ea = alpha * (1.0F - ep) * (1.0F - ep) * 0.45F;
            if (ea < 0.004F) continue;
            batchRing(lines, mat, er, 56, col(theme, ea * 0.4F));
            batchRing(lines, mat, er * 0.991F, 56, col(theme, ea * 0.8F));
            batchRing(lines, mat, er * 0.982F, 56, col(theme, ea));
        }
    }

    // ───────────────────────────── Спираль ─────────────────────────────

    public void drawSpiral(VertexConsumer fills, VertexConsumer lines, Matrix4f mat,
                           float r, float alpha, long elapsed, int theme) {
        int gs = 44;

        for (int g = 0; g < SPIRAL_HALO_POW.length; g++) {
            float gf = g / (float) SPIRAL_HALO_POW.length;
            float gr = r * (1.0F + gf * 0.55F);
            float ga = alpha * SPIRAL_HALO_POW[g] * 0.16F;
            batchFilledRing(fills, mat, gr * 0.976F, gr, gs, col(theme, ga));
        }

        for (int i = 0; i < SPIRAL_INNER_POW.length; i++) {
            float rf = i / (float) SPIRAL_INNER_POW.length;
            float ia = alpha * SPIRAL_INNER_POW[i] * 0.22F;
            batchFilledRing(fills, mat, r * rf * 0.92F, r * (rf + 0.1F) * 0.92F, gs, col(theme, ia));
        }

        int spirals = 3;

        for (int s = 0; s < spirals; s++) {
            double phase = elapsed * 0.0013 * (s % 2 == 0 ? 1.0 : -1.15) + (Math.PI * 2.0 / 3.0) * s;
            double tipA = (Math.PI * 11.0 / 2.0) + phase;
            float tx = (float) (Math.cos(tipA) * r);
            float ty = (float) (Math.sin(tipA) * r);
            float pulse = (float) (Math.sin(elapsed * 0.006 + s * 2.1) * 0.5 + 0.5);
            float pBase = alpha * pulse * 0.75F;

            for (int g = 0; g < SPIRAL_TIP_POW.length; g++) {
                float gr = 0.015F + g * 0.028F;
                float ga = pBase * SPIRAL_TIP_POW[g];
                batchFilledRingOffset(fills, mat, gr * 0.4F, gr, 20, col(theme, ga), tx, ty);
            }
        }

        int segs = SPIRAL_SEGS;

        for (int s = 0; s < spirals; s++) {
            double dir = s % 2 == 0 ? 1.0 : -1.15;
            double phase = elapsed * 0.0013 * dir + (Math.PI * 2.0 / 3.0) * s;

            for (int i = 0; i < segs - 1; i++) {
                float fA = i / (float) segs;
                float fB = (i + 1) / (float) segs;
                float edge = SPIRAL_EDGE_POW07[i];
                float pulse = (float) (Math.sin(elapsed * 0.004 + fA * 8.0F + s) * 0.06 + 0.94);
                float rA = fA * r * pulse;
                float rB = fB * r * pulse;
                double aA = fA * Math.PI * 5.5 + phase;
                double aB = fB * Math.PI * 5.5 + phase;
                float xA = (float) Math.cos(aA) * rA;
                float yA = (float) Math.sin(aA) * rA;
                float xB = (float) Math.cos(aB) * rB;
                float yB = (float) Math.sin(aB) * rB;

                for (int layer = 0; layer < 3; layer++) {
                    float sa = alpha * edge * SPIRAL_LAYER_ALPHA[layer] * pulse;
                    if (sa < 0.008F) continue;
                    lines.vertex(mat, xA, yA, 0.0F).color(col(theme, sa));
                    lines.vertex(mat, xB, yB, 0.0F).color(col(theme, sa));
                }
            }
        }

        batchRing(lines, mat, r * 1.1F, 64, col(theme, alpha * 0.05F));
        batchRing(lines, mat, r * 1.05F, 64, col(theme, alpha * 0.15F));
        batchRing(lines, mat, r * 1.01F, 64, col(theme, alpha * 0.4F));
        batchRing(lines, mat, r * 1.0F, 64, col(theme, alpha * 0.85F));
        batchRing(lines, mat, r * 0.97F, 64, col(theme, alpha * 0.5F));

        for (int e = 0; e < 2; e++) {
            float ep = (elapsed + e * 700L) % 1400L / 1400.0F;
            float er = r * (1.0F + ep * 0.7F);
            float ea = alpha * (1.0F - ep) * (1.0F - ep) * 0.38F;
            if (ea < 0.004F) continue;
            batchRing(lines, mat, er, 56, col(theme, ea * 0.5F));
            batchRing(lines, mat, er * 0.989F, 56, col(theme, ea));
        }
    }

    // ───────────────────────────── Нова ─────────────────────────────

    public void drawNova(VertexConsumer fills, VertexConsumer lines, Matrix4f mat,
                         float r, float alpha, long elapsed, int theme) {
        int arcCount = ARC_SPEEDS.length;

        for (int a2 = 0; a2 < arcCount; a2++) {
            arcStartA[a2] = elapsed * ARC_SPEEDS[a2] + a2 * (Math.PI / 3);
        }

        double orbitBase0 = elapsed * ORBIT_SPDS[0] * ORBIT_DIRS[0];
        double orbitBase1 = elapsed * ORBIT_SPDS[1] * ORBIT_DIRS[1];

        for (int i = 0; i < ORBIT_DOT_COUNTS[0]; i++) {
            double a = TWO_PI_ORBIT_0 * i + orbitBase0;
            orbitCos0[i] = (float) Math.cos(a);
            orbitSin0[i] = (float) Math.sin(a);
        }

        for (int i = 0; i < ORBIT_DOT_COUNTS[1]; i++) {
            double a = TWO_PI_ORBIT_1 * i + orbitBase1;
            orbitCos1[i] = (float) Math.cos(a);
            orbitSin1[i] = (float) Math.sin(a);
        }

        int gs = 52;

        for (int g = 0; g < HALO_POW.length; g++) {
            float gf = g / (float) HALO_POW.length;
            float gr = r * (1.0F + gf * 0.9F);
            float ga = alpha * HALO_POW[g] * 0.2F;
            batchFilledRing(fills, mat, gr * 0.976F, gr, gs, col(theme, ga));
        }

        float webR = r * 0.88F;
        double webRot = elapsed * 8.0E-4;

        for (int sp = 0; sp < 12; sp++) {
            double angle = (Math.PI / 6) * sp + webRot;
            float pulse3 = (float) (Math.sin(elapsed * 0.005 + sp * 0.8) * 0.5 + 0.5);
            float pulseAlpha = 0.08F + pulse3 * 0.12F;
            float ca = (float) Math.cos(angle);
            float si = (float) Math.sin(angle);

            for (int seg = 0; seg < SPOKE_SEGS - 1; seg++) {
                float edgeA = SPOKE_EDGE[seg];
                float edgeB = SPOKE_EDGE[seg + 1];
                float sa2 = alpha * edgeA * pulseAlpha;
                float sb = alpha * edgeB * pulseAlpha;
                if (sa2 < 0.004F && sb < 0.004F) continue;

                float fA = seg / (float) SPOKE_SEGS;
                float fB = (seg + 1) / (float) SPOKE_SEGS;
                float rA = fA * webR;
                float rB = fB * webR;

                fills.vertex(mat, ca * rA + si * 0.025F, si * rA - ca * 0.025F, 0.0F).color(col(theme, sa2));
                fills.vertex(mat, ca * rA - si * 0.025F, si * rA + ca * 0.025F, 0.0F).color(col(theme, sa2));
                fills.vertex(mat, ca * rB - si * 0.025F, si * rB + ca * 0.025F, 0.0F).color(col(theme, sb));
                fills.vertex(mat, ca * rB + si * 0.025F, si * rB - ca * 0.025F, 0.0F).color(col(theme, sb));
            }
        }

        for (int wr = 1; wr <= 4; wr++) {
            float wrf = wr / 4.0F;
            float wRad = webR * wrf;
            float pulse4 = (float) (Math.sin(elapsed * 0.004 + wr * 1.2) * 0.5 + 0.5);
            float wa = alpha * (1.0F - wrf * 0.5F) * (0.08F + pulse4 * 0.12F);
            batchFilledRing(fills, mat, wRad * 0.985F, wRad * 1.015F, 40, col(theme, wa));
        }

        for (int g = 0; g < CENTER_GLOW_POW.length; g++) {
            float gr = 0.01F + g * 0.02F;
            float ga = alpha * CENTER_GLOW_POW[g] * 0.65F;
            batchFilledRingOffset(fills, mat, gr * 0.25F, gr, 20, col(theme, ga), 0.0F, 0.0F);
        }

        for (int a3 = 0; a3 < arcCount; a3++) {
            float arcR = r * ARC_R_MULT[a3];
            double tipA = arcStartA[a3] + (Math.PI * 2) * ARC_LEN[a3];
            float tx = (float) (Math.cos(tipA) * arcR);
            float ty = (float) (Math.sin(tipA) * arcR);
            float pulse = (float) (Math.sin(elapsed * 0.007 + a3 * 1.7) * 0.5 + 0.5);
            float pBase = alpha * pulse * 0.85F;

            for (int g = 0; g < ARC_GLOW_POW.length; g++) {
                float gr = 0.012F + g * 0.022F;
                float ga = pBase * ARC_GLOW_POW[g];
                batchFilledRingOffset(fills, mat, gr * 0.35F, gr, 16, col(theme, ga), tx, ty);
            }
        }

        orbitRsBuf[0] = r * 1.28F;
        orbitRsBuf[1] = r * 1.48F;

        for (int o = 0; o < 2; o++) {
            int dc = ORBIT_DOT_COUNTS[o];
            float or = orbitRsBuf[o];
            float[] oCos = o == 0 ? orbitCos0 : orbitCos1;
            float[] oSin = o == 0 ? orbitSin0 : orbitSin1;

            for (int i = 0; i < dc; i++) {
                float dx = oCos[i] * or;
                float dy = oSin[i] * or;
                float pulse = (float) (Math.sin(elapsed * 0.008 + i * 1.3 + o) * 0.5 + 0.5);
                float daB = alpha * (0.3F + pulse * 0.55F) * 0.7F;

                for (int g = 0; g < DOT_GLOW_POW.length; g++) {
                    float gr = 0.01F + g * 0.018F;
                    float ga = daB * DOT_GLOW_POW[g];
                    batchFilledRingOffset(fills, mat, gr * 0.3F, gr, 12, col(theme, ga), dx, dy);
                }
            }
        }

        int arcSegs = 36;

        for (int a4 = 0; a4 < arcCount; a4++) {
            float arcR = r * ARC_R_MULT[a4];
            int total = (int) (arcSegs * ARC_LEN[a4]);
            float pulse = (float) (Math.sin(elapsed * 0.005 + a4 * 1.3) * 0.35 + 0.65);
            int totalM1 = total - 1;
            double arcAngleSpan = (Math.PI * 2) * ARC_LEN[a4];

            for (int layer = 0; layer < 3; layer++) {
                float rW = arcR * (1.0F + NOVA_LAYER_W[layer] * 0.012F);
                float layerAlphaPulse = alpha * NOVA_LAYER_A[layer] * pulse;

                for (int seg = 0; seg < totalM1; seg++) {
                    float edge = ARC_EDGE_LUT[seg * 63 / totalM1];
                    float sa = layerAlphaPulse * edge;
                    if (sa < 0.008F) continue;
                    double aA = arcStartA[a4] + (double) seg / total * arcAngleSpan;
                    double aB = arcStartA[a4] + (double) (seg + 1) / total * arcAngleSpan;
                    lines.vertex(mat, (float) (Math.cos(aA) * rW), (float) (Math.sin(aA) * rW), 0.0F).color(col(theme, sa));
                    lines.vertex(mat, (float) (Math.cos(aB) * rW), (float) (Math.sin(aB) * rW), 0.0F).color(col(theme, sa));
                }
            }
        }

        batchRing(lines, mat, r * 1.14F, 72, col(theme, alpha * 0.04F));
        batchRing(lines, mat, r * 1.08F, 72, col(theme, alpha * 0.1F));
        batchRing(lines, mat, r * 1.03F, 72, col(theme, alpha * 0.25F));
        batchRing(lines, mat, r * 1.0F, 72, col(theme, alpha * 0.75F));
        batchRing(lines, mat, r * 0.98F, 72, col(theme, alpha));
        batchRing(lines, mat, r * 0.95F, 72, col(theme, alpha * 0.4F));

        for (int o = 0; o < 2; o++) {
            int dc = ORBIT_DOT_COUNTS[o];
            float or = orbitRsBuf[o];
            float[] oCos = o == 0 ? orbitCos0 : orbitCos1;
            float[] oSin = o == 0 ? orbitSin0 : orbitSin1;
            float cosDelta = TAIL_COS_DELTA;
            float sinDelta = o == 0 ? -TAIL_SIN_DELTA : TAIL_SIN_DELTA;

            for (int i = 0; i < dc; i++) {
                float pulse = (float) (Math.sin(elapsed * 0.008 + i * 1.3 + o) * 0.5 + 0.5);
                float da = alpha * (0.3F + pulse * 0.55F);
                float px = oCos[i] * or;
                float py = oSin[i] * or;

                for (int seg = 1; seg <= 3; seg++) {
                    float sa = da * (1.0F - seg * 0.28F);
                    float nx = px * cosDelta - py * sinDelta;
                    float ny = px * sinDelta + py * cosDelta;
                    if (sa >= 0.008F) {
                        lines.vertex(mat, px, py, 0.0F).color(col(theme, sa));
                        lines.vertex(mat, nx, ny, 0.0F).color(col(theme, sa));
                    }
                    px = nx;
                    py = ny;
                }
            }
        }

        for (int e = 0; e < 3; e++) {
            float ep = (elapsed + e * 550L) % 1800L / 1800.0F;
            float er = r * (1.0F + ep);
            float ea = alpha * (1.0F - ep) * (1.0F - ep) * 0.48F;
            if (ea < 0.004F) continue;
            batchRing(lines, mat, er, 72, col(theme, ea * 0.3F));
            batchRing(lines, mat, er * 0.993F, 72, col(theme, ea * 0.65F));
            batchRing(lines, mat, er * 0.985F, 72, col(theme, ea));
        }
    }

    // ───────────────────────────── батчинг ─────────────────────────────

    private static void batchRing(VertexConsumer buf, Matrix4f mat, float r, int segs, int color) {
        float step = 512.0F / segs;

        for (int i = 0; i < segs; i++) {
            int iA = (int) (i * step) & 511;
            int iB = (int) ((i + 1) * step) & 511;
            buf.vertex(mat, COS_LUT[iA] * r, SIN_LUT[iA] * r, 0.0F).color(color);
            buf.vertex(mat, COS_LUT[iB] * r, SIN_LUT[iB] * r, 0.0F).color(color);
        }
    }

    private static void batchFilledRing(VertexConsumer buf, Matrix4f mat, float inner, float outer,
                                        int segs, int color) {
        batchFilledRingOffset(buf, mat, inner, outer, segs, color, 0.0F, 0.0F);
    }

    private static void batchFilledRingOffset(VertexConsumer buf, Matrix4f mat, float inner, float outer,
                                              int segs, int color, float cx, float cy) {
        float step = 512.0F / segs;

        for (int i = 0; i < segs; i++) {
            int iA = (int) (i * step) & 511;
            int iB = (int) ((i + 1) * step) & 511;
            float coA = COS_LUT[iA];
            float siA = SIN_LUT[iA];
            float coB = COS_LUT[iB];
            float siB = SIN_LUT[iB];
            buf.vertex(mat, cx + coA * outer, cy + siA * outer, 0.0F).color(color);
            buf.vertex(mat, cx + coA * inner, cy + siA * inner, 0.0F).color(color);
            buf.vertex(mat, cx + coB * inner, cy + siB * inner, 0.0F).color(color);
            buf.vertex(mat, cx + coB * outer, cy + siB * outer, 0.0F).color(color);
        }
    }

    private static int col(int themeRGB, float a) {
        int ai = Math.max(0, Math.min(255, (int) (a * 255.0F)));
        return themeRGB & 0xFFFFFF | ai << 24;
    }

    // ───────────────────────────── тайминги появления/затухания ─────────────────────────────

    public static float easeOutCubic(float x) {
        return 1.0F - (float) Math.pow(1.0F - x, 3.0);
    }

    public static float easeInCubic(float x) {
        return x * x * x;
    }
}
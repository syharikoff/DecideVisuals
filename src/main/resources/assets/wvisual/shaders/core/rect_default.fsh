#version 150

#moj_import <wvisual:common.glsl>

in vec2 FragCoord;
in vec2 ScreenPos;
flat in int QuadIndex;

layout(std140) uniform RectangleParamsArray {
    vec4 params[4032];
};

layout(std140) uniform PaletteParams {
    vec4 paletteMeta;
    vec4 paletteColors[6];
    vec4 paletteMeta2;
    vec4 paletteLayerA[6];
    vec4 paletteWaveA;
    vec4 paletteWave2;
    vec4 paletteLayerB[6];
    vec4 paletteWaveB;
    vec4 palettePadB;
    vec4 paletteLayerC[6];
    vec4 paletteWaveC;
    vec4 palettePadC;
    vec4 paletteLayerD[6];
    vec4 paletteWaveD;
    vec4 palettePadD;
    vec4 paletteLayerE[6];
    vec4 paletteWaveE;
    vec4 palettePadE;
    vec4 paletteLayerF[6];
    vec4 paletteWaveF;
    vec4 palettePadF;
};

out vec4 OutColor;

float kLayerA = 0.0;
float kLayerB = 0.0;
float kLayerC = 0.0;
float kLayerD = 0.0;
float kLayerE = 0.0;
float kLayerF = 0.0;

float themeLayerCoverage(vec4 wave, vec2 fragXY) {
    if (wave.w < 0.5) {
        return 0.0;
    }
    if (wave.w > 1.5) {
        return clamp(wave.z, 0.0, 1.0);
    }
    vec2 res = max(paletteWave2.xy, vec2(1.0));
    float aspect = res.x / res.y;
    vec2 d = (fragXY / res - wave.xy) * vec2(aspect, 1.0);
    float wf = max(paletteWave2.z, 0.0005);
    return 1.0 - smoothstep(wave.z - wf, wave.z + wf, length(d));
}

void themeWaveMix(vec2 fragXY) {
    kLayerA = themeLayerCoverage(paletteWaveA, fragXY);
    kLayerB = themeLayerCoverage(paletteWaveB, fragXY);
    kLayerC = themeLayerCoverage(paletteWaveC, fragXY);
    kLayerD = themeLayerCoverage(paletteWaveD, fragXY);
    kLayerE = themeLayerCoverage(paletteWaveE, fragXY);
    kLayerF = themeLayerCoverage(paletteWaveF, fragXY);
}

vec3 themeBlend(vec3 base, vec3 a, vec3 b, vec3 c, vec3 d, vec3 e, vec3 f) {
    vec3 r = mix(base, a, kLayerA);
    r = mix(r, b, kLayerB);
    r = mix(r, c, kLayerC);
    r = mix(r, d, kLayerD);
    r = mix(r, e, kLayerE);
    return mix(r, f, kLayerF);
}

vec3 paletteColorAt(int idx) {
    if (idx <= 0) return themeBlend(paletteColors[0].rgb, paletteLayerA[0].rgb, paletteLayerB[0].rgb, paletteLayerC[0].rgb, paletteLayerD[0].rgb, paletteLayerE[0].rgb, paletteLayerF[0].rgb);
    if (idx == 1) return themeBlend(paletteColors[1].rgb, paletteLayerA[1].rgb, paletteLayerB[1].rgb, paletteLayerC[1].rgb, paletteLayerD[1].rgb, paletteLayerE[1].rgb, paletteLayerF[1].rgb);
    if (idx == 2) return themeBlend(paletteColors[2].rgb, paletteLayerA[2].rgb, paletteLayerB[2].rgb, paletteLayerC[2].rgb, paletteLayerD[2].rgb, paletteLayerE[2].rgb, paletteLayerF[2].rgb);
    if (idx == 3) return themeBlend(paletteColors[3].rgb, paletteLayerA[3].rgb, paletteLayerB[3].rgb, paletteLayerC[3].rgb, paletteLayerD[3].rgb, paletteLayerE[3].rgb, paletteLayerF[3].rgb);
    if (idx == 4) return themeBlend(paletteColors[4].rgb, paletteLayerA[4].rgb, paletteLayerB[4].rgb, paletteLayerC[4].rgb, paletteLayerD[4].rgb, paletteLayerE[4].rgb, paletteLayerF[4].rgb);
    return themeBlend(paletteColors[5].rgb, paletteLayerA[5].rgb, paletteLayerB[5].rgb, paletteLayerC[5].rgb, paletteLayerD[5].rgb, paletteLayerE[5].rgb, paletteLayerF[5].rgb);
}

vec3 paletteRamp(float t) {
    int count = int(paletteMeta.x + 0.5);
    if (count <= 1) {
        return paletteColorAt(0);
    }
    float f = clamp(t, 0.0, 1.0) * float(count - 1);
    int i = clamp(int(floor(f)), 0, count - 1);
    int j = min(i + 1, count - 1);
    float frac = clamp(f - float(i), 0.0, 1.0);
    frac = frac * frac * (3.0 - 2.0 * frac);
    return mix(paletteColorAt(i), paletteColorAt(j), frac);
}

vec3 paletteLoop(float t) {
    int count = int(paletteMeta.x + 0.5);
    if (count <= 1) {
        return paletteColorAt(0);
    }
    float f = fract(t) * float(count);
    int i1 = clamp(int(floor(f)), 0, count - 1);
    float u = clamp(f - float(i1), 0.0, 1.0);

    int i0 = i1 - 1; if (i0 < 0) i0 += count;
    int i2 = i1 + 1; if (i2 >= count) i2 -= count;
    int i3 = i1 + 2; if (i3 >= count) i3 -= count;

    vec3 c0 = paletteColorAt(i0);
    vec3 c1 = paletteColorAt(i1);
    vec3 c2 = paletteColorAt(i2);
    vec3 c3 = paletteColorAt(i3);

    float u2 = u * u;
    float u3 = u2 * u;
    vec3 cyclicCol = 0.5 * ((2.0 * c1)
                      + (-c0 + c2) * u
                      + (2.0 * c0 - 5.0 * c1 + 4.0 * c2 - c3) * u2
                      + (-c0 + 3.0 * c1 - 3.0 * c2 + c3) * u3);

    float tri = 0.5 - 0.5 * cos(6.2831853 * fract(t));
    vec3 mirrorCol = paletteRamp(tri);

    float closed = clamp(paletteMeta2.y, 0.0, 1.0);
    return clamp(mix(mirrorCol, cyclicCol, closed), 0.0, 1.0);
}

float roundedAlpha(vec2 coord, vec2 size, vec4 radius, float smoothness) {
    return ralpha(size, coord, radius, smoothness);
}

vec4 rectangleColor(vec2 coord, vec4 topLeft, vec4 topRight, vec4 bottomRight, vec4 bottomLeft) {
    vec4 top = mix(topLeft, topRight, coord.x);
    vec4 bottom = mix(bottomLeft, bottomRight, coord.x);
    return mix(top, bottom, coord.y);
}

void main() {
    int base = QuadIndex * 9;
    vec4 radius = params[base];
    vec4 sizeSmooth = params[base + 1];
    vec4 topLeft = params[base + 2];
    vec4 topRight = params[base + 3];
    vec4 bottomRight = params[base + 4];
    vec4 bottomLeft = params[base + 5];
    vec4 scissorPosSize = params[base + 6];
    vec4 scissorRadius = params[base + 7];
    vec4 scissorRotation = params[base + 8];

    vec2 size = max(sizeSmooth.xy, vec2(1.0));

    const float AA_PAD = 1.5;
    vec2 f = vec2(AA_PAD) / size;
    vec2 shapeCoord = FragCoord * (vec2(1.0) + 2.0 * f) - f;
    vec2 coord = clamp(shapeCoord, vec2(0.0), vec2(1.0));
    float alpha;
    if (sizeSmooth.z < 0.0) {

        vec2 halfSize = size * 0.5;
        vec2 pos = halfSize - shapeCoord * size;
        float r = max(radius.x, 0.0);
        vec2 q = abs(pos) - halfSize + vec2(r);
        float dist = min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - r;
        float feather = max(fwidth(dist) * 0.5, -sizeSmooth.z);
        alpha = 1.0 - smoothstep(-feather, feather, dist);
    } else {
        alpha = roundedAlpha(shapeCoord, size, max(radius, vec4(0.0)), sizeSmooth.z);
    }

    int paletteMode = int(sizeSmooth.w + 0.5);
    vec4 color;
    if (paletteMode > 0) {
        themeWaveMix(gl_FragCoord.xy);

        if (paletteMode == 3) {

            color = vec4(paletteLoop(coord.x + paletteMeta2.z) * topLeft.x, topLeft.y);
        } else {
            float t = (paletteMode == 1) ? coord.y : coord.x;
            color = vec4(paletteRamp(t) * topLeft.x, topLeft.y);
        }
    } else {
        color = rectangleColor(coord, topLeft, topRight, bottomRight, bottomLeft);
    }
    color.a *= alpha;

    if (scissorPosSize.z > 0.0 && scissorPosSize.w > 0.0) {
        vec2 clipPoint = ScreenPos;
        if (abs(scissorRotation.y) > 0.0001) {
            vec2 clipCenter = scissorPosSize.xy + scissorPosSize.zw * 0.5;
            vec2 rel = ScreenPos - clipCenter;
            clipPoint = clipCenter + vec2(
                rel.x * scissorRotation.x + rel.y * scissorRotation.y,
                -rel.x * scissorRotation.y + rel.y * scissorRotation.x
            );
        }
        vec2 scissorCoord = (clipPoint - scissorPosSize.xy) / scissorPosSize.zw;
        float scissorAlpha = ralpha(scissorPosSize.zw, scissorCoord, max(scissorRadius, vec4(0.0)), sizeSmooth.z);
        color.a *= scissorAlpha;
    }

    if (color.a <= 0.001) {
        discard;
    }

    OutColor = color;
}

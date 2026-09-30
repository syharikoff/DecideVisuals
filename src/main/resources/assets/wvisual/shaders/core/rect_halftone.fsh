#version 150

#moj_import <wvisual:common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform HalftoneRectangleParamsArray {
    vec4 params[3072];
};

out vec4 OutColor;

vec4 rectangleColor(vec2 coord, vec4 topLeft, vec4 topRight, vec4 bottomRight, vec4 bottomLeft) {
    vec4 top = mix(topLeft, topRight, coord.x);
    vec4 bottom = mix(bottomLeft, bottomRight, coord.x);
    return mix(top, bottom, coord.y);
}

float hash21(vec2 p) {
    return fract(sin(dot(floor(p), vec2(127.1, 311.7))) * 43758.5453123);
}

vec3 squareMosaic(vec2 cell, vec3 baseRgb) {
    float h = hash21(cell);
    float step = floor(h * 8.0) / 7.0;
    float shade = mix(0.86, 1.05, step);
    return baseRgb * shade;
}

void main() {
    int base = QuadIndex * 8;
    vec4 radius = params[base];
    vec4 sizeSmoothDot = params[base + 1];
    vec4 topLeft = params[base + 2];
    vec4 topRight = params[base + 3];
    vec4 bottomRight = params[base + 4];
    vec4 bottomLeft = params[base + 5];

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 size = max(sizeSmoothDot.xy, vec2(1.0));
    float shapeAlpha = ralpha(size, coord, radius, sizeSmoothDot.z);

    vec4 baseColor = rectangleColor(coord, topLeft, topRight, bottomRight, bottomLeft);
    baseColor.a *= shapeAlpha;

    float pitch = max(sizeSmoothDot.w, 1.0);

    vec2 cell = floor(gl_FragCoord.xy / pitch);

    vec3 grainRgb = squareMosaic(cell, baseColor.rgb);

    vec4 color = vec4(grainRgb, baseColor.a);
    if (color.a <= 0.001) discard;

    OutColor = color;
}

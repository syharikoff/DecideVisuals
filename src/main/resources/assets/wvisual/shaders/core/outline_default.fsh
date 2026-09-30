#version 150

#moj_import <wvisual:common.glsl>

in vec2 FragCoord;
in vec2 ScreenPos;
flat in int QuadIndex;

layout(std140) uniform OutlineParamsArray {
    vec4 params[4032];
};

out vec4 OutColor;

vec4 outlineColor(vec2 coord, vec4 topLeft, vec4 topRight, vec4 bottomRight, vec4 bottomLeft) {
    vec4 top = mix(topLeft, topRight, coord.x);
    vec4 bottom = mix(bottomLeft, bottomRight, coord.x);
    return mix(top, bottom, coord.y);
}

float outlineAlpha(vec2 coord, vec2 size, vec4 radius, float thickness, float smoothness) {
    float outer = ralpha(size, coord, radius, smoothness);
    vec2 innerSize = size - vec2(thickness * 2.0);
    if (innerSize.x <= 0.0 || innerSize.y <= 0.0) {
        return outer;
    }

    vec2 local = coord * size;
    vec2 innerCoord = (local - vec2(thickness)) / innerSize;
    vec4 innerRadius = max(radius - vec4(thickness), vec4(0.0));
    float inner = ralpha(innerSize, innerCoord, innerRadius, smoothness);
    return clamp(outer - inner, 0.0, 1.0);
}

void main() {
    int base = QuadIndex * 9;
    vec4 radius = params[base];
    vec4 sizeThicknessSmooth = params[base + 1];
    vec4 topLeft = params[base + 2];
    vec4 topRight = params[base + 3];
    vec4 bottomRight = params[base + 4];
    vec4 bottomLeft = params[base + 5];
    vec4 scissorPosSize = params[base + 6];
    vec4 scissorRadius = params[base + 7];
    vec4 scissorRotation = params[base + 8];

    vec2 size = max(sizeThicknessSmooth.xy, vec2(1.0));

    const float AA_PAD = 1.5;
    vec2 f = vec2(AA_PAD) / size;
    vec2 shapeCoord = FragCoord * (vec2(1.0) + 2.0 * f) - f;
    vec2 coord = clamp(shapeCoord, vec2(0.0), vec2(1.0));
    float thickness = max(sizeThicknessSmooth.z, 0.0);
    float alpha = outlineAlpha(shapeCoord, size, radius, thickness, sizeThicknessSmooth.w);

    vec4 color = outlineColor(coord, topLeft, topRight, bottomRight, bottomLeft);
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
        color.a *= ralpha(scissorPosSize.zw, scissorCoord, max(scissorRadius, vec4(0.0)), sizeThicknessSmooth.w);
    }

    if (color.a <= 0.001) {
        discard;
    }

    OutColor = color;
}

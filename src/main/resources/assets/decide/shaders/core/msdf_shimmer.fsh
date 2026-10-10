#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2  TexCoord;

in vec4  VertexColor;

in float LinePos;

out vec4 OutColor;

const float DISTANCE_RANGE = 4.0;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float screenPxRange() {
    vec2 unitRange     = vec2(DISTANCE_RANGE) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(TexCoord);
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

void main() {

    const float EDGE_SHARPNESS = 1.45;
    vec3  msd      = texture(Sampler0, TexCoord).rgb;
    float sd       = median(msd.r, msd.g, msd.b);
    float coverage = clamp(screenPxRange() * (sd - 0.5) * EDGE_SHARPNESS + 0.5, 0.0, 1.0);

    float baseOpacity   = VertexColor.a;
    float progress      = VertexColor.r * 2.0 - 0.5;
    float halfW         = VertexColor.g;
    float shimIntensity = VertexColor.b;

    float packedLine = LinePos;
    float linePos = mod(packedLine, 64.0) / 63.0;
    float tintB   = mod(floor(packedLine / 64.0), 64.0) / 63.0;
    float tintG   = mod(floor(packedLine / 4096.0), 64.0) / 63.0;
    float tintR   = floor(packedLine / 262144.0) / 63.0;

    float dist  = abs(linePos - progress);
    float spot  = smoothstep(halfW, 0.0, dist);
    spot        = spot * spot;

    float alpha = coverage * spot * shimIntensity * baseOpacity;
    if (alpha <= 0.001) discard;

    OutColor = vec4(tintR, tintG, tintB, alpha) * ColorModulator;
}

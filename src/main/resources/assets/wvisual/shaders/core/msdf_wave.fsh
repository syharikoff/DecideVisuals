#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 TexCoord;
in vec4 VertexColor;
in float Phase;
in float ScreenY;

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

    float alpha = coverage * VertexColor.a;
    if (alpha <= 0.001) {
        discard;
    }

    const float FREQ  = 0.05;
    const float SHARP = 1.0;
    const float MAXW  = 0.85;

    float s = 0.5 + 0.5 * sin(ScreenY * FREQ - Phase);
    s = pow(s, SHARP);
    float w = s * MAXW;
    vec3 col = mix(VertexColor.rgb, vec3(1.0), w);

    OutColor = vec4(col, alpha) * ColorModulator;
}

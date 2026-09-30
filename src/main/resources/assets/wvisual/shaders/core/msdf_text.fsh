#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 TexCoord;
in vec4 VertexColor;

out vec4 OutColor;

const float DISTANCE_RANGE = 4.0;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float screenPxRange() {
    vec2 unitRange = vec2(DISTANCE_RANGE) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(TexCoord);
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

void main() {
    vec3 msd = texture(Sampler0, TexCoord).rgb;
    float sd = median(msd.r, msd.g, msd.b);

    const float EDGE_SHARPNESS = 1.45;
    float screenPxDistance = screenPxRange() * (sd - 0.5);
    float coverage = clamp(screenPxDistance * EDGE_SHARPNESS + 0.5, 0.0, 1.0);

    float alpha = coverage * VertexColor.a;
    if (alpha <= 0.001) {
        discard;
    }

    OutColor = vec4(VertexColor.rgb, alpha) * ColorModulator;
}

#version 150

uniform sampler2D MotionInput;

layout(std140) uniform MotionBlurParams {
    vec4 BlurStep;
};

in vec2 TexCoord;
out vec4 OutColor;

void main() {
    vec2 blurStep = BlurStep.xy;
    vec4 color = vec4(0.0);
    float totalWeight = 0.0;

    for (int i = -16; i <= 16; i++) {
        float x = float(i);
        float weight = exp(-(x * x) / 128.0);
        vec2 coord = clamp(TexCoord + blurStep * x, vec2(0.0), vec2(1.0));
        color += texture(MotionInput, coord) * weight;
        totalWeight += weight;
    }

    OutColor = color / totalWeight;
}

#version 150

uniform sampler2D uInput;

layout(std140) uniform BlurData {
    vec4 Step;
};

in vec2 texCoord;
out vec4 outColor;

void main() {
    vec2 step = Step.xy;
    vec4 color = vec4(0.0);
    float total = 0.0;
    for (int i = -16; i <= 16; i++) {
        float x = float(i);
        float w = exp(-(x * x) / 128.0);
        vec2 uv = clamp(texCoord + step * x, vec2(0.0), vec2(1.0));
        color += texture(uInput, uv) * w;
        total += w;
    }
    outColor = color / total;
}

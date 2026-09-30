#version 150

uniform sampler2D uGui;

layout(std140) uniform CompositeData {
    vec4 Params;
};

in vec2 texCoord;
out vec4 outColor;

void main() {

    vec2 uv = vec2(0.5) + (texCoord - vec2(0.5)) * Params.x;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        outColor = vec4(0.0);
        return;
    }
    outColor = texture(uGui, uv) * Params.y;
}

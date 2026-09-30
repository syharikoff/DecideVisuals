#version 150

uniform sampler2D uGui;

in vec2 texCoord;
in vec4 vColor;
out vec4 outColor;

void main() {
    if (texCoord.x < 0.0 || texCoord.x > 1.0 || texCoord.y < 0.0 || texCoord.y > 1.0) {
        outColor = vec4(0.0);
        return;
    }
    outColor = texture(uGui, texCoord) * vColor.a;
}

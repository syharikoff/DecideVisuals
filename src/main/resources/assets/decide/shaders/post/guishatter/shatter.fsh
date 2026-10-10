#version 150

uniform sampler2D uGui;

in vec2 texCoord;
in vec4 vColor;
out vec4 outColor;

void main() {
    outColor = texture(uGui, texCoord) * vColor.a;
}

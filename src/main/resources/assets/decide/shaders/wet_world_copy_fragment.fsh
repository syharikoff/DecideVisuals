#version 150

uniform sampler2D Sampler0;

in vec2 vUV;
out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, vUV);
}

#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec3 vPos;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a == 0.0) {
        discard;
    }
    float fade = 1.0 - smoothstep(28.0, 96.0, length(vPos));
    if (fade <= 0.003) {
        discard;
    }
    fragColor = vec4(color.rgb, color.a * fade) * ColorModulator;
}

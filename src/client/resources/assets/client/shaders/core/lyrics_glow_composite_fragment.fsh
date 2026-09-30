#version 150

uniform sampler2D Sampler0;

layout(std140) uniform LyricsGlowParams {
    vec4 Params;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 bloom = texture(Sampler0, texCoord).rgb;
    float strength = Params.x;
    vec3 lifted = bloom * strength;
    fragColor = vec4(lifted / (1.0 + lifted), 1.0);
}

#version 150

uniform sampler2D Sampler0;

layout(std140) uniform KawaseParams {
    vec4 SourceRect;
    vec4 HalfPixel;
    vec4 FallbackColor;
};

in vec2 TexCoord;

out vec4 OutColor;

vec4 sampleSource(vec2 coord) {
    return texture(Sampler0, clamp(coord, vec2(0.0), vec2(1.0)));
}

void main() {
    vec2 sourceCoord = SourceRect.xy + TexCoord * SourceRect.zw;
    vec4 sum = sampleSource(sourceCoord) * 4.0;
    sum += sampleSource(sourceCoord - HalfPixel.xy);
    sum += sampleSource(sourceCoord + HalfPixel.xy);
    sum += sampleSource(sourceCoord + vec2(HalfPixel.x, -HalfPixel.y));
    sum += sampleSource(sourceCoord - vec2(HalfPixel.x, -HalfPixel.y));
    OutColor = sum / 8.0;
}

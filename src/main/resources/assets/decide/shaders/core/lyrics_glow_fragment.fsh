#version 150

uniform sampler2D Atlas;

layout(std140) uniform LyricsTextData {
    mat4 ViewProjection;
    vec4 Palette;
    vec4 Tint;
    vec4 Metrics;
};

layout(std140) uniform LyricsGlyphArray {
    vec4 GlyphBounds[256];
    vec4 GlyphEffect[256];
};

in vec2 atlasUv;
in vec4 atlasBounds;
in vec4 glyphEffect;

out vec4 fragColor;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

void main() {
    vec2 inside = step(atlasBounds.xy, atlasUv) * step(atlasUv, atlasBounds.zw);
    vec4 texel = texture(Atlas, atlasUv);
    vec2 atlasSize = vec2(textureSize(Atlas, 0));
    vec2 unitRange = vec2(4.0) / atlasSize;
    vec2 footprint = abs(dFdx(atlasUv)) + abs(dFdy(atlasUv));
    float screenRange = max(0.5 * dot(unitRange, vec2(1.0) / max(footprint, vec2(1.0e-6))), 1.0);
    float shape = clamp((median(texel.r, texel.g, texel.b) - 0.5) * screenRange + 0.5, 0.0, 1.0);
    float heat = clamp(glyphEffect.y, 0.0, 1.0);
    float eased = heat * heat * (3.0 - 2.0 * heat);
    float energy = shape * inside.x * inside.y * eased * glyphEffect.z;
    if (energy <= 0.003) {
        discard;
    }
    fragColor = vec4(vec3(energy), energy);
}

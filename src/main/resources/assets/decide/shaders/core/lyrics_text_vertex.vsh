#version 150

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

in vec3 Position;
in vec2 UV0;
in float LineWidth;

out vec2 atlasUv;
out vec4 atlasBounds;
out vec4 glyphEffect;

void main() {
    int slot = int(LineWidth + 0.5);
    gl_Position = ViewProjection * vec4(Position, 1.0);
    atlasUv = UV0;
    atlasBounds = GlyphBounds[slot];
    glyphEffect = GlyphEffect[slot];
}

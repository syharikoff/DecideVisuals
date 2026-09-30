#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in ivec2 UV1;
in vec4 Color;

out vec2 LocalPx;
flat out vec2 HalfSize;
flat out float Radius;
out vec4 FillColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    LocalPx = UV0;
    HalfSize = vec2(UV1) / 16.0;
    Radius = Position.z;
    FillColor = Color;
}

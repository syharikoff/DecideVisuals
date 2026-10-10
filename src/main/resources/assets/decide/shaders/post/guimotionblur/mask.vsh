#version 150

in vec3 Position;
in vec2 UV0;
in ivec2 UV1;
in vec4 Color;

out vec2 vLocalPx;
flat out vec2 vHalfSize;
flat out float vRadius;
out vec4 vMaskData;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    vLocalPx = UV0;
    vHalfSize = vec2(UV1) / 16.0;
    vRadius = Position.z;
    vMaskData = Color;
}

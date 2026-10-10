#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

layout(std140) uniform WorldQuadData {
    mat4 WorldMat;
    vec4 QuadParams;
    vec4 UvRect;
};

out vec2 texCoord;
out vec4 vColor;

void main() {
    if (QuadParams.z > 0.5) {
        gl_Position = WorldMat * vec4(Position.xy * QuadParams.xy, Position.z * QuadParams.y, 1.0);
    } else {
        gl_Position = vec4(Position.x * 2.0 - 1.0, 1.0 - Position.y * 2.0, 0.0, 1.0);
    }
    texCoord = UV0;
    vColor = Color;
}

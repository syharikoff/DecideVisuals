#version 150

// Позиция приходит уже в нормализованных экранных координатах [0..1] (y вниз),
// поэтому глобальные ProjectionMatrix/ModelViewMatrix не участвуют - они
// принадлежат мировому кадру, и из-за них превью уезжало за экран.
in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord;
out vec4 vColor;

void main() {
    gl_Position = vec4(Position.x * 2.0 - 1.0, 1.0 - Position.y * 2.0, 0.0, 1.0);
    texCoord = UV0;
    vColor = Color;
}
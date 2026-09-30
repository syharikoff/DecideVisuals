#version 150

in vec2 vLocalPx;
flat in vec2 vHalfSize;
flat in float vRadius;
in vec4 vMaskData;

out vec4 OutColor;

const float FEATHER = 8.0;

void main() {
    vec2 p = vLocalPx - vHalfSize;
    vec2 q = abs(p) - vHalfSize + vec2(vRadius);
    float d = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - vRadius;
    float coverage = 1.0 - smoothstep(0.0, FEATHER, d);
    OutColor = vec4(vMaskData.r, vMaskData.g, 0.0, coverage);
}

#version 150

in vec2 LocalPx;
flat in vec2 HalfSize;
flat in float Radius;
in vec4 FillColor;

out vec4 fragColor;

void main() {
    vec2 p = LocalPx - HalfSize;
    vec2 q = abs(p) - (HalfSize - vec2(Radius));
    float d = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - Radius;
    float alpha = clamp(0.5 - d, 0.0, 1.0);
    if (alpha <= 0.003) {
        discard;
    }
    fragColor = vec4(FillColor.rgb, FillColor.a * alpha);
}

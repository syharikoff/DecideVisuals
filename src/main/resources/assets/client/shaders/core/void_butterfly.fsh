#version 330

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

// Полоса UV.x выбирает примитив, всё рисуется процедурно, текстуры нет:
//   x in [0;1]  — крыло (два эллипса + жилки + ободок)
//   x in [2;3]  — тело (диск)
//   x in [4;5]  — гало (мягкое пятно за бабочкой)
//   x in [6;7]  — штрих (свечение жилки / усика)
void main() {
    if (texCoord.x > 5.5) {
        float d = abs(texCoord.y * 2.0 - 1.0);
        float aa = max(fwidth(d), 0.025);
        float core = 1.0 - smoothstep(0.06, 0.06 + aa, d);
        float halo = exp(-d * d * 5.0) * (1.0 - smoothstep(0.75, 1.0, d));
        float a = (core * 0.8 + halo * 0.42) * vertexColor.a;
        if (a < 0.003) discard;
        vec3 color = mix(vertexColor.rgb * 1.45, vec3(1.0), core * 0.7);
        fragColor = vec4(color, min(a, 1.0));
        return;
    }

    if (texCoord.x > 3.5) {
        vec2 p = vec2((texCoord.x - 4.0) * 2.0 - 1.0, texCoord.y * 2.0 - 1.0);
        float d = dot(p, p);
        float halo = exp(-d * 3.8) * (1.0 - smoothstep(0.6, 1.0, d));
        float a = halo * vertexColor.a;
        if (a < 0.003) discard;
        fragColor = vec4(vertexColor.rgb * 1.3, a);
        return;
    }

    if (texCoord.x > 1.5) {
        vec2 p = vec2((texCoord.x - 2.0) * 2.0 - 1.0, texCoord.y * 2.0 - 1.0);
        float d = length(p);
        float a = (1.0 - smoothstep(0.65, 1.0, d)) * vertexColor.a;
        if (a < 0.003) discard;
        fragColor = vec4(mix(vertexColor.rgb * 1.3, vec3(1.0), 0.18), a);
        return;
    }

    vec2 p = vec2(texCoord.x, texCoord.y * 2.0 - 1.0);
    float upper = length((p - vec2(0.48, 0.38)) / vec2(0.5, 0.56)) - 1.0;
    float lower = length((p - vec2(0.34, -0.42)) / vec2(0.34, 0.46)) - 1.0;
    float d = min(upper, lower);
    float aa = max(fwidth(d), 0.016);
    float fill = 1.0 - smoothstep(-aa, aa, d);
    float rim = exp(-abs(d) * 23.0);
    float halo = exp(-max(d, 0.0) * 15.0) * 0.14;
    float vein = pow(0.5 + 0.5 * cos(atan(p.y, p.x + 0.05) * 19.0 + length(p) * 5.0), 20.0);
    vec3 color = vertexColor.rgb * mix(0.9, 1.8, clamp(rim * 0.9 + vein * 0.24, 0.0, 1.0));
    color = mix(color, vec3(1.0), rim * 0.16);
    color = mix(vertexColor.rgb, color, fill);
    float alpha = max(fill * 0.96, halo) * vertexColor.a;
    if (alpha < 0.003) discard;
    fragColor = vec4(color, alpha);
}
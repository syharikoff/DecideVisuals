#version 150

uniform sampler2D Atlas;

layout(std140) uniform LyricsTextData {
    mat4 ViewProjection;
    vec4 Palette;
    vec4 Tint;
    vec4 Metrics;
};

in vec2 atlasUv;
in vec4 atlasBounds;
in vec4 glyphEffect;

out vec4 fragColor;

const vec2 INNER_RING[8] = vec2[](
    vec2(0.5500, 0.0000),
    vec2(0.3889, 0.3889),
    vec2(0.0000, 0.5500),
    vec2(-0.3889, 0.3889),
    vec2(-0.5500, 0.0000),
    vec2(-0.3889, -0.3889),
    vec2(0.0000, -0.5500),
    vec2(0.3889, -0.3889)
);

const vec2 OUTER_RING[8] = vec2[](
    vec2(0.9239, 0.3827),
    vec2(0.3827, 0.9239),
    vec2(-0.3827, 0.9239),
    vec2(-0.9239, 0.3827),
    vec2(-0.9239, -0.3827),
    vec2(-0.3827, -0.9239),
    vec2(0.3827, -0.9239),
    vec2(0.9239, -0.3827)
);

const float CENTER_WEIGHT = 0.42;
const float INNER_WEIGHT = 0.62;
const float OUTER_WEIGHT = 0.24;
const float BLUR_GAIN = 1.35;
const float EPSILON = 0.0000015;

float screenRange = 1.0;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float coverage(vec2 uv) {
    vec2 inside = step(atlasBounds.xy, uv) * step(uv, atlasBounds.zw);
    vec4 texel = texture(Atlas, uv);
    float distance = median(texel.r, texel.g, texel.b);
    float shape = clamp((distance - 0.5) * screenRange + 0.5, 0.0, 1.0);
    return shape * inside.x * inside.y;
}

void main() {
    vec2 atlasSize = vec2(textureSize(Atlas, 0));
    vec2 unitRange = vec2(max(Metrics.x, 0.001)) / atlasSize;
    vec2 footprint = abs(dFdx(atlasUv)) + abs(dFdy(atlasUv));
    screenRange = max(0.5 * dot(unitRange, vec2(1.0) / max(footprint, vec2(1.0e-6))), 1.0);

    float radius = glyphEffect.x;
    float shape;

    if (radius <= EPSILON) {
        shape = coverage(atlasUv);
    } else {
        float sum = coverage(atlasUv) * CENTER_WEIGHT;
        for (int index = 0; index < 8; index++) {
            sum += coverage(atlasUv + INNER_RING[index] * radius) * INNER_WEIGHT;
            sum += coverage(atlasUv + OUTER_RING[index] * radius) * OUTER_WEIGHT;
        }
        shape = sum / (CENTER_WEIGHT + 8.0 * (INNER_WEIGHT + OUTER_WEIGHT));
        shape *= 1.0 + BLUR_GAIN * min(1.0, radius / max(Palette.w, EPSILON));
    }
    shape = clamp(shape, 0.0, 1.0);

    float heat = clamp(glyphEffect.y, 0.0, 1.0);
    float eased = heat * heat * (3.0 - 2.0 * heat);
    float alpha = min(shape * glyphEffect.z * (1.0 + eased * Palette.y), 1.0);

    float halo = 0.0;
    if (eased > 0.004) {
        vec2 insideCenter = step(atlasBounds.xy, atlasUv) * step(atlasUv, atlasBounds.zw);
        vec4 centerTexel = texture(Atlas, atlasUv);
        float field = median(centerTexel.r, centerTexel.g, centerTexel.b) * insideCenter.x * insideCenter.y;
        float ring = smoothstep(0.0, 0.5, field);
        halo = ring * ring * eased * Palette.z * glyphEffect.z;
    }

    if (alpha <= 0.004 && halo <= 0.004) {
        discard;
    }

    vec3 base = mix(vec3(Palette.x), Tint.rgb, Tint.a);
    vec3 tint = mix(base, vec3(1.0), eased);
    vec3 glow = vec3(halo * (1.0 - alpha) * 0.85);
    fragColor = vec4(tint * alpha + glow, alpha);
}

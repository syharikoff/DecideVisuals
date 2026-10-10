#version 150

layout(std140) uniform Uniforms {
    vec4 uScreen;   // x=width y=height z=time w=traceSteps
    vec4 uParams;   // x=reflectionStrength y=humidity z=ripple w=hasSkyLight
    vec4 uCam;      // xyz=cameraPos w=traceDistance
    vec4 uSky;      // rgb=skyColor
    vec4 uSun;      // xyz=sunDirection
    mat4 uViewProj;
    mat4 uInvViewProj;
};

uniform sampler2D Sampler0; // scene color
uniform sampler2D Sampler1; // scene depth

in vec2 vUV;
out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += noise(p) * a;
        p = p * 2.03 + vec2(7.3, 4.1);
        a *= 0.5;
    }
    return v;
}

vec3 unproject(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 p = uInvViewProj * clip;
    return p.xyz / p.w;
}

void main() {
    float depth = texture(Sampler1, vUV).r;
    vec3 base = texture(Sampler0, vUV).rgb;

    if (depth >= 0.99999) {
        fragColor = vec4(base, 0.0);
        return;
    }

    vec2 texel = 1.0 / uScreen.xy;
    vec3 P = unproject(vUV, depth);
    vec3 V = normalize(P);

    float dRight = texture(Sampler1, vUV + vec2(texel.x, 0.0)).r;
    float dLeft  = texture(Sampler1, vUV - vec2(texel.x, 0.0)).r;
    float dUp    = texture(Sampler1, vUV + vec2(0.0, texel.y)).r;
    float dDown  = texture(Sampler1, vUV - vec2(0.0, texel.y)).r;

    vec3 ddx = abs(dRight - depth) < abs(depth - dLeft)
             ? unproject(vUV + vec2(texel.x, 0.0), dRight) - P
             : P - unproject(vUV - vec2(texel.x, 0.0), dLeft);
    vec3 ddy = abs(dUp - depth) < abs(depth - dDown)
             ? unproject(vUV + vec2(0.0, texel.y), dUp) - P
             : P - unproject(vUV - vec2(0.0, texel.y), dDown);

    vec3 N = cross(ddx, ddy);
    if (dot(N, N) < 1e-12) {
        fragColor = vec4(base, 0.0);
        return;
    }
    N = normalize(N);
    if (dot(N, V) > 0.0) N = -N;

    float mask = smoothstep(0.62, 0.90, clamp(N.y, 0.0, 1.0)) * uParams.y;

    float dist = length(P);
    mask *= 1.0 - smoothstep(uCam.w * 1.1, uCam.w * 2.0, dist);
    if (mask <= 0.002) {
        fragColor = vec4(base, 0.0);
        return;
    }

    vec3 world = P + uCam.xyz;
    float time = uScreen.z;
    vec2 rp = world.xz * 1.7;
    vec2 drift = vec2(time * 0.05, time * -0.04);
    float e = 0.14;
    float h0 = fbm(rp + drift);
    float hx = fbm(rp + vec2(e, 0.0) + drift);
    float hz = fbm(rp + vec2(0.0, e) + drift);
    vec3 Nw = normalize(N + vec3(h0 - hx, 0.0, h0 - hz) * uParams.z * 3.0);

    vec3 R = reflect(V, Nw);
    R.y = max(R.y, 0.02);
    R = normalize(R);

    int steps = int(uScreen.w);
    float stepLen = uCam.w / float(max(steps, 1));
    float jitter = hash(vUV * uScreen.xy + vec2(time, time * 1.7));
    vec3 prev = P + Nw * 0.03;
    vec3 cur = prev + R * stepLen * (0.35 + jitter * 0.65);

    bool hit = false;
    vec2 hitUv = vec2(0.0);

    for (int i = 0; i < 64; i++) {
        if (i >= steps) break;

        vec4 clip = uViewProj * vec4(cur, 1.0);
        if (clip.w <= 0.0) break;
        vec3 ndc = clip.xyz / clip.w;
        vec2 uv = ndc.xy * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) break;

        float sceneDepth = texture(Sampler1, uv).r;
        if (sceneDepth < 0.99999 && ndc.z > sceneDepth * 2.0 - 1.0 + 2e-5) {
            vec3 a = prev;
            vec3 b = cur;
            for (int k = 0; k < 5; k++) {
                vec3 m = (a + b) * 0.5;
                vec4 mc = uViewProj * vec4(m, 1.0);
                vec3 mn = mc.xyz / mc.w;
                float sd = texture(Sampler1, mn.xy * 0.5 + 0.5).r;
                if (mn.z > sd * 2.0 - 1.0) b = m; else a = m;
            }
            vec4 hc = uViewProj * vec4(b, 1.0);
            hitUv = (hc.xy / hc.w) * 0.5 + 0.5;

            vec3 hp = unproject(hitUv, texture(Sampler1, hitUv).r);
            if (abs(length(hp) - length(b)) < stepLen * 2.0 + 0.5) {
                hit = true;
            }
            break;
        }

        prev = cur;
        cur += R * stepLen;
    }

    vec3 fallback = mix(uSky.rgb * 0.82, uSky.rgb * 1.18, clamp(R.y, 0.0, 1.0));
    fallback += vec3(1.0, 0.95, 0.85) * pow(max(dot(R, uSun.xyz), 0.0), 48.0) * 0.55 * uParams.w;

    vec3 reflection = fallback;
    if (hit) {
        vec2 d = abs(hitUv - 0.5) * 2.0;
        float edge = 1.0 - smoothstep(0.72, 1.0, max(d.x, d.y));
        reflection = mix(fallback, texture(Sampler0, hitUv).rgb, edge);
    }

    float fresnel = 0.05 + 0.95 * pow(1.0 - clamp(dot(-V, Nw), 0.0, 1.0), 4.0);
    float k = clamp(mask * fresnel * uParams.x * 1.7, 0.0, 0.92);

    vec3 wetBase = base * mix(1.0, 0.70, mask);
    vec3 color = mix(wetBase, reflection, k);
    color += vec3(1.0, 0.97, 0.90)
           * pow(max(dot(R, uSun.xyz), 0.0), 120.0) * mask * uParams.w * 0.75;

    fragColor = vec4(color, smoothstep(0.0, 0.02, mask));
}

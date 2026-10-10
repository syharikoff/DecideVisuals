#version 150

layout(std140) uniform Uniforms {
    vec4 uBrightness; // x: brightness
    vec4 uSaturation; // x: saturation
    vec4 uContrast;   // x: contrast
    vec4 uHue;        // x: hue (degrees)
};

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec3 col = texture(Sampler0, texCoord).rgb;

    vec3 hsv = rgb2hsv(col);
    hsv.x = fract(hsv.x + uHue.x / 360.0);
    col = hsv2rgb(hsv);

    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(luma), col, uSaturation.x);

    col = (col - 0.5) * uContrast.x + 0.5;

    col += uBrightness.x;

    fragColor = vec4(col, 1.0);
}
#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

layout(std140) uniform WaveParams {
    vec4 WaveData;
};

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

vec3 wvisual_raw_wave(vec3 p, float wind) {
    float magnitude = sin(wind * 0.0027 + p.x + p.y) * WaveData.w + 0.04;
    float d0 = sin(wind * 0.0127);
    float d1 = sin(wind * 0.0089);
    float d2 = sin(wind * 0.0114);
    vec3 wave;
    wave.x = magnitude * sin(wind * 0.0224 + d1 + d2 + p.x - p.z + p.y);
    wave.y = magnitude * sin(wind * 0.0015 + d2 + d0 + p.x);
    wave.z = magnitude * sin(wind * 0.0063 + d0 + d1 - p.x + p.z + p.y);
    return wave;
}

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    float alpha = Color.a;
    int tag = int(round(alpha * 255.0));

    if (tag >= 252 && tag <= 254) {
        vec3 worldPos = mod(Position + vec3(ChunkPosition), 50.26548245743669);
        float wind = WaveData.x * 170.0;

        vec3 wave;
        if (tag == 254) {
            float sky = clamp(float(UV2.y) / 240.0, 0.0, 1.0);
            float gate = clamp(sky - 0.87, 0.0, 0.1);
            vec3 p = vec3(worldPos.x, worldPos.y * 0.5, worldPos.z);
            wave = wvisual_raw_wave(p, wind);
            wave.z = wave.z * 8.0 + wave.y * 4.0;
            wave.x *= 3.0;
            wave.y = 0.0;
            wave *= gate * WaveData.y;
        } else {
            vec3 p = worldPos * vec3(0.75, 0.375, 0.75);
            wave = wvisual_raw_wave(p, wind) * vec3(4.0, 3.0, 8.0);
            wave *= 0.1 * WaveData.z;
            if (tag == 252) {
                wave *= 0.75;
            }
        }

        pos += wave;
        alpha = 1.0;
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = vec4(Color.rgb, alpha) * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}

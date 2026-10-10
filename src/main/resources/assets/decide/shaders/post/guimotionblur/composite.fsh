#version 150

uniform sampler2D MotionScene;
uniform sampler2D MotionBackground;
uniform sampler2D MotionBlurred;
uniform sampler2D MotionBackgroundBlurred;
uniform sampler2D MotionMask;

layout(std140) uniform MotionCompositeParams {
    vec4 CompositeData;
    vec4 GuiRectData;
    vec4 TargetData;
    vec4 SourceRectData;
    vec4 TransformData;
};

in vec2 TexCoord;
out vec4 OutColor;

float roundedRectMask(vec2 point, vec4 rect, float radius, float feather) {
    vec2 halfSize = max(rect.zw * 0.5, vec2(0.0));
    vec2 center = rect.xy + halfSize;
    float safeRadius = min(radius, min(halfSize.x, halfSize.y));
    vec2 q = abs(point - center) - halfSize + vec2(safeRadius);
    float distance = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - safeRadius;
    return 1.0 - smoothstep(-feather, feather, distance);
}

void main() {
    float opacity = clamp(CompositeData.x, 0.0, 1.0);
    float bloom = clamp(CompositeData.y, 0.0, 0.12);
    float layerBoost = max(CompositeData.z, 1.0);
    float radius = max(CompositeData.w, 0.0);
    vec2 targetSize = max(TargetData.xy, vec2(1.0));
    float feather = max(TargetData.z, 0.5);
    float scale = max(TargetData.w, 0.0001);
    vec4 scene = texture(MotionScene, TexCoord);
    vec4 blurred = texture(MotionBlurred, TexCoord);
    vec2 fragPx = vec2(TexCoord.x * targetSize.x, (1.0 - TexCoord.y) * targetSize.y);

    if (abs(scale - 1.0) <= 0.0005) {

        vec4 maskSample = clamp(texture(MotionMask, TexCoord), vec4(0.0), vec4(1.0));
        vec4 background = texture(MotionBackground, TexCoord);
        vec4 blurredBackground = texture(MotionBackgroundBlurred, TexCoord);
        vec3 layer = scene.rgb - background.rgb;
        vec3 blurredLayer = blurred.rgb - blurredBackground.rgb;
        float layerAlpha = scene.a - background.a;
        float blurredLayerAlpha = blurred.a - blurredBackground.a;

        float amount = opacity * maskSample.a;
        vec3 boostedBlur = blurredLayer * mix(1.0, layerBoost, amount);
        vec3 card = mix(layer, boostedBlur, maskSample.g);
        vec3 composed = background.rgb + layer * (1.0 - amount) + card * amount * maskSample.r;
        float cardAlpha = mix(layerAlpha, blurredLayerAlpha, maskSample.g);
        float composedAlpha = background.a
                + layerAlpha * (1.0 - amount)
                + cardAlpha * amount * maskSample.r;
        OutColor = vec4(clamp(composed, 0.0, 1.0), clamp(composedAlpha, 0.0, 1.0));
        return;
    }

    vec4 background = texture(MotionBackground, TexCoord);
    vec4 blurredBackground = texture(MotionBackgroundBlurred, TexCoord);
    vec2 sourcePx = TransformData.xy + (fragPx - TransformData.xy) / scale;
    vec2 sourceUv = clamp(vec2(sourcePx.x / targetSize.x, 1.0 - sourcePx.y / targetSize.y), vec2(0.0), vec2(1.0));
    vec4 sourceScene = texture(MotionScene, sourceUv);
    vec4 sourceBackground = texture(MotionBackground, sourceUv);
    vec4 sourceBlurred = texture(MotionBlurred, sourceUv);
    vec4 sourceBlurredBackground = texture(MotionBackgroundBlurred, sourceUv);

    vec3 layer = sourceScene.rgb - sourceBackground.rgb;
    vec3 blurredLayer = sourceBlurred.rgb - sourceBlurredBackground.rgb;
    vec3 origLayer = scene.rgb - background.rgb;

    float srcContent = smoothstep(0.0015, 0.02, max(max(abs(layer.r), abs(layer.g)), abs(layer.b)));
    float blurContent = smoothstep(0.0015, 0.02, max(max(abs(blurredLayer.r), abs(blurredLayer.g)), abs(blurredLayer.b)));
    float scaledContent = max(srcContent, blurContent);
    float origContent = smoothstep(0.0015, 0.02, max(max(abs(origLayer.r), abs(origLayer.g)), abs(origLayer.b)));

    float amount = opacity;
    vec3 softLayer = blurredLayer * layerBoost + bloom * smoothstep(0.25, 0.95, max(blurredLayer, vec3(0.0)));
    vec3 scaledLayer = mix(layer, softLayer, amount) * scaledContent;

    float clearMask = max(origContent, scaledContent);
    vec3 composed = background.rgb + scaledLayer;
    OutColor = vec4(clamp(mix(scene.rgb, composed, clearMask), 0.0, 1.0), scene.a);
}

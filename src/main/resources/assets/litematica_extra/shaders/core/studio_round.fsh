#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 localPosition;
flat in vec2 halfSize;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 extent = halfSize - vec2(1.0);
    float radius = min(3.0, min(extent.x, extent.y));
    vec2 corner = abs(localPosition) - extent + radius;
    float distance = length(max(corner, 0.0)) + min(max(corner.x, corner.y), 0.0) - radius;
    float pixel = max(fwidth(distance), 0.001);
    float coverage = 1.0 - smoothstep(-0.5 * pixel, 0.5 * pixel, distance);
    if (coverage <= 0.0) discard;
    fragColor = vertexColor * ColorModulator;
    fragColor.a *= coverage;
}

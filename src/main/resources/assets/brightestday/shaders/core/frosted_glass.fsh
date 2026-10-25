#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec4 vertexColor;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

const float TINT = 0.4;

void main() {
    if (vertexColor.a == 0.0) {
        discard;
    }
    vec3 behind = texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb;
    vec4 color = vec4(mix(behind, vertexColor.rgb * ColorModulator.rgb, TINT), min(vertexColor.a * ColorModulator.a, 1.0 + behind.r));

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    #endif
    fragColor = color;
    #endif
}

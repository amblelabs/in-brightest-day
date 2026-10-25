#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float WEIGHTS[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
#ifdef BLUR_VERTICAL
    vec2 direction = vec2(0.0, texel.y * 2.0);
#else
    vec2 direction = vec2(texel.x * 2.0, 0.0);
#endif
    vec3 sum = texture(InSampler, texCoord).rgb * WEIGHTS[0];
    for (int i = 1; i < 5; i++) {
        vec2 offset = direction * float(i);
        sum += texture(InSampler, texCoord + offset).rgb * WEIGHTS[i];
        sum += texture(InSampler, texCoord - offset).rgb * WEIGHTS[i];
    }
    fragColor = vec4(sum, 1.0);
}

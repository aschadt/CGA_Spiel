#version 330 core

//#define USE_PCF  // optional: aktivieren, wenn du auch im Scene-Shader PCF aktivierst

// aus tron_vert.glsl
in struct VertexData {
    vec3 fragPos_view;
    vec3 normal_view;
    vec3 toLight_view;
    vec3 toCamera_view;
    vec3 color;
    vec2 texCoords;
    vec4 fragPos_lightSpace;
} vertexData;

uniform sampler2D shadowMap;

float ShadowCalculation(vec4 fragPosLightSpace, vec3 normal_view, vec3 lightDir_view)
{
    // identisch zu tron_frag.glsl
    vec3 projCoords = fragPosLightSpace.xyz / max(fragPosLightSpace.w, 1e-6);
    projCoords = projCoords * 0.5 + 0.5;

    // außerhalb des Shadow-Frustums -> kein Schatten
    if (projCoords.z > 1.0 ||
    projCoords.x < 0.0 || projCoords.x > 1.0 ||
    projCoords.y < 0.0 || projCoords.y > 1.0)
    return 0.0;

    float currentDepth = projCoords.z;

    // gleicher, winkelabhängiger Bias wie im Scene-Shader
    float ndotl = max(dot(normalize(normal_view), normalize(lightDir_view)), 0.0);
    float bias = max(0.05 * (1.0 - ndotl), 0.005);

    #ifndef USE_PCF
    // Binär (kein PCF)
    float mapDepth = texture(shadowMap, projCoords.xy).r;
    return (currentDepth - bias > mapDepth) ? 1.0 : 0.0;
    #else
    // 3x3 PCF (weich) – identisch zur Szene halten!
    float shadow = 0.0;
    vec2 texelSize = 1.0 / textureSize(shadowMap, 0);
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            float mapDepth = texture(shadowMap, projCoords.xy + vec2(x, y) * texelSize).r;
            shadow += (currentDepth - bias > mapDepth) ? 1.0 : 0.0;
        }
    }
    return shadow / 9.0;
    #endif
}

out vec4 fragColor;

void main()
{
    // exakt die gleichen Eingaben wie im Scene-Shader
    vec3 N = normalize(vertexData.normal_view);
    vec3 L = normalize(vertexData.toLight_view);

    float shadow = ShadowCalculation(vertexData.fragPos_lightSpace, N, L);

    // 1 = Schatten, 0 = Licht (binär/soft je nach PCF)
    fragColor = vec4(shadow, shadow, shadow, 1.0);
}
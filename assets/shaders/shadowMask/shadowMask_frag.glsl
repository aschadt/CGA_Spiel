#version 330 core

// Vom Vertex-Shader (muss exakt übereinstimmen!)
in struct VertexData {
    vec3 fragPos_view;
    vec3 normal_view;
    vec3 toLight_view;
    vec3 toCamera_view;
    vec3 color;               // <--- hinzugefügt, damit Match da ist
    vec2 texCoords;
    vec4 fragPos_lightSpace;  // Position im Licht-Raum
} vertexData;

uniform sampler2D shadowMap;

// --- Minimalistische Shadow-Berechnung für Binärmaske ---
float ShadowMask(vec4 fragPosLightSpace)
{
    vec3 projCoords = fragPosLightSpace.xyz / max(fragPosLightSpace.w, 1e-6);
    projCoords = projCoords * 0.5 + 0.5;

    if (projCoords.z > 1.0) return 0.0;

    float currentDepth = projCoords.z;
    const float bias = 0.003;

    float depthFromMap = texture(shadowMap, projCoords.xy).r;
    return (currentDepth - bias > depthFromMap) ? 1.0 : 0.0;
}

out vec4 fragColor;

void main()
{
    float mask = ShadowMask(vertexData.fragPos_lightSpace);
    fragColor = vec4(mask, mask, mask, 1.0); // harte Binärmaske
}
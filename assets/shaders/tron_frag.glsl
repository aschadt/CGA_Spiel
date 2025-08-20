#version 330 core
// Spotlight
uniform vec3  spot_direction_view;
uniform float spot_innerCutoff;
uniform float spot_outerCutoff;

// Lichter
uniform vec3 pointLight_color;
uniform vec3 spotLight_color;

uniform vec3 emission_tint;

#define MAX_POINT_LIGHTS 5
uniform int  numPointLights;
uniform vec3 pointLight_positions[MAX_POINT_LIGHTS];
uniform vec3 pointLight_colors[MAX_POINT_LIGHTS];

// Vom Vertex-Shader
in struct VertexData {
    vec3 fragPos_view;
    vec3 normal_view;
    vec3 toLight_view;
    vec3 toCamera_view;

    vec3 color;
    vec2 texCoords;

    vec4 fragPos_lightSpace;   // NEU: Position im Licht-Raum
} vertexData;

const float gammaVal = 2.2;

vec3 invgamma(vec3 c) { return pow(c, vec3(gammaVal)); }
vec3 gamma(vec3 c)    { return pow(c, vec3(1.0 / gammaVal)); }

// Ausgabe
out vec4 fragColor;

// Material
uniform sampler2D material_diffuse;
uniform sampler2D material_specular;
uniform sampler2D material_emissive;
uniform sampler2D material_roughness;
uniform sampler2D material_normal;
uniform float     material_shininess;

// ShadowMap
uniform sampler2D shadowMap;

// Shadow-Berechnung mit Bias + 3x3 PCF
float ShadowCalculation(vec4 fragPosLightSpace, vec3 normal_view, vec3 lightDir_view)
{
    // Projektionsraum -> NDC -> [0,1]
    vec3 projCoords = fragPosLightSpace.xyz / max(fragPosLightSpace.w, 1e-6);
    projCoords = projCoords * 0.5 + 0.5;

    // Außerhalb des Lichtfrustums: kein Schatten
    if (projCoords.z > 1.0) return 0.0;

    float currentDepth = projCoords.z;

    // Winkelabhängiger Bias gegen Shadow Acne
    float bias = max(0.05 * (1.0 - max(dot(normalize(normal_view), normalize(lightDir_view)), 0.0)), 0.005);

    // PCF 3x3
    float shadow = 0.0;
    vec2 texelSize = 1.0 / textureSize(shadowMap, 0);
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            float pcfDepth = texture(shadowMap, projCoords.xy + vec2(x, y) * texelSize).r;
            shadow += (currentDepth - bias > pcfDepth) ? 1.0 : 0.0;
        }
    }
    shadow /= 9.0;

    return shadow;
}

void main()
{
    // Normalmap im Tangent Space
    vec3 normalMap = texture(material_normal, vertexData.texCoords).rgb;
    normalMap = normalize(normalMap * 2.0 - 1.0); // [0,1] → [-1,1]

    // Normale Richtungsvektoren
    vec3 N = normalMap;
    vec3 L = normalize(vertexData.toLight_view);
    vec3 V = normalize(vertexData.toCamera_view);
    vec3 H = normalize(L + V);

    float distance   = length(vertexData.toLight_view);
    float attenuation = 1.0 / (1.0 + 1.0 * distance + 1.0 * distance * distance);

    float theta   = dot(-L, normalize(spot_direction_view));
    float epsilon = spot_innerCutoff - spot_outerCutoff;
    float intensity = clamp((theta - spot_outerCutoff) / max(epsilon, 1e-6), 0.0, 1.0);

    // Texturen (linear)
    vec3 diffuseColor  = invgamma(texture(material_diffuse,  vertexData.texCoords).rgb);
    vec3 specularColor = invgamma(texture(material_specular, vertexData.texCoords).rgb);
    vec3 emissiveColor = invgamma(texture(material_emissive, vertexData.texCoords).rgb) * emission_tint;

    // Roughness
    float rough = texture(material_roughness, vertexData.texCoords).r; // Grauwert [0..1]
    float gloss = 1.0 - rough;                                        // Glossiness = 1 - Roughness
    float shininessFactor = material_shininess * gloss * 128.0;       // Skaliert exponent




    // Beleuchtung
    float diff = max(dot(N, L), 0.0);
    float spec = pow(max(dot(N, H), 0.0), shininessFactor);

    // Punktlichter
    vec3 pointDiffuse = vec3(0.0);
    vec3 pointSpecular = vec3(0.0);
    for (int i = 0; i < numPointLights; ++i) {
        vec3 L_i = normalize(pointLight_positions[i] - vertexData.fragPos_view);
        float d_i = length(pointLight_positions[i] - vertexData.fragPos_view);
        float att_i = 1.0 / (1.0 + 3.0 * d_i + 0.1 * d_i * d_i);

        vec3 H_i = normalize(L_i + V);
        float diff_i = max(dot(N, L_i), 0.0);
        float spec_i = pow(max(dot(N, H_i), 0.0), shininessFactor);

        pointDiffuse  += att_i * diff_i * diffuseColor  * pointLight_colors[i];
        pointSpecular += att_i * spec_i * specularColor * pointLight_colors[i];
    }

    // Spotlight-Beiträge (vor Schatten)
    vec3 diffuse_SP  = (attenuation * intensity * 5.0) * diff * diffuseColor  * spotLight_color;
    vec3 specular_SP = (attenuation * intensity * 5.0) * spec * specularColor * spotLight_color;

    // Schatten nur auf Spotlight anwenden
    float shadow = ShadowCalculation(vertexData.fragPos_lightSpace, N, L);
    float lit = 1.0 - shadow;

    // Ambient ohne Schatten
    vec3 ambient = 0.001 * diffuseColor;

    vec3 result = ambient
    + pointDiffuse + pointSpecular
    + lit * (diffuse_SP + specular_SP)
    + emissiveColor;

    fragColor = vec4(gamma(result), 1.0);
}
#version 330 core
out vec4 FragColor;

in VS_OUT {
    vec3 FragPos;
    vec3 Normal;
    vec2 TexCoords;
    vec4 FragPosLightSpace;
} fs_in;

uniform sampler2D diffuseTexture;
uniform sampler2D shadowMap;

uniform vec3 lightPos;
uniform vec3 viewPos;

float ShadowCalculation(vec4 fragPosLightSpace, vec3 normal, vec3 lightDir)
{
    // Perspective Divide
    vec3 projCoords = fragPosLightSpace.xyz / fragPosLightSpace.w;
    // in [0,1] Bereich transformieren
    projCoords = projCoords * 0.5 + 0.5;

    // frühzeitig abbrechen: außerhalb vom Licht-Frustum
    if(projCoords.z > 1.0)
    return 0.0;

    // Tiefenwert aus ShadowMap
    float closestDepth = texture(shadowMap, projCoords.xy).r;
    // Aktuelle Tiefe
    float currentDepth = projCoords.z;

    // Bias gegen Shadow-Akne
    float bias = max(0.05 * (1.0 - dot(normal, lightDir)), 0.005);

    // Percentage-Closer Filtering (3x3)
    float shadow = 0.0;
    vec2 texelSize = 1.0 / textureSize(shadowMap, 0);
    for(int x = -1; x <= 1; ++x)
    {
        for(int y = -1; y <= 1; ++y)
        {
            float pcfDepth = texture(shadowMap, projCoords.xy + vec2(x, y) * texelSize).r;
            shadow += currentDepth - bias > pcfDepth ? 1.0 : 0.0;
        }
    }
    shadow /= 9.0;

    return shadow;
}

void main()
{
    vec3 color = texture(diffuseTexture, fs_in.TexCoords).rgb;
    vec3 normal = normalize(fs_in.Normal);

    // Lichtberechnung
    vec3 lightColor = vec3(1.0);
    vec3 lightDir = normalize(lightPos - fs_in.FragPos);

    // Ambient
    vec3 ambient = 0.15 * lightColor;

    // Diffuse
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = diff * lightColor;

    // Specular (Blinn-Phong)
    vec3 viewDir = normalize(viewPos - fs_in.FragPos);
    vec3 halfwayDir = normalize(lightDir + viewDir);
    float spec = pow(max(dot(normal, halfwayDir), 0.0), 64.0);
    vec3 specular = spec * lightColor;

    // Shadow-Berechnung
    float shadow = ShadowCalculation(fs_in.FragPosLightSpace, normal, lightDir);

    // Finales Lighting
    vec3 lighting = (ambient + (1.0 - shadow) * (diffuse + specular)) * color;
    FragColor = vec4(lighting, 1.0);
}
#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec2 aTexCoords;

out VS_OUT {
    vec3 FragPos;
    vec3 Normal;
    vec2 TexCoords;
    vec4 FragPosLightSpace;
} vs_out;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
uniform mat4 lightSpaceMatrix;

void main()
{
    // Weltkoordinaten
    vec4 worldPos = model * vec4(aPos, 1.0);
    vs_out.FragPos = worldPos.xyz;

    // Normalen ins Weltkoordinatensystem
    vs_out.Normal = transpose(inverse(mat3(model))) * aNormal;

    // Texturkoordinaten einfach durchreichen
    vs_out.TexCoords = aTexCoords;

    // Position im Licht-Raum für ShadowMap-Lookup
    vs_out.FragPosLightSpace = lightSpaceMatrix * worldPos;

    // Normale Kameraprojektion für das eigentliche Bild
    gl_Position = projection * view * worldPos;
}
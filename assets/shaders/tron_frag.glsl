#version 330 core
// Spotlight
uniform vec3 spot_direction_view;     // Richtung des Spotlights im Viewspace
uniform float spot_innerCutoff;       // Cosinus von innerem Kegelwinkel (ϕ)
uniform float spot_outerCutoff;       // Cosinus von äußerem Kegelwinkel (γ)

// Lichtquellen
uniform vec3 pointLight_color;
uniform vec3 spotLight_color;

uniform vec3 emission_tint;


// Empfängt interpolierte Werte vom Vertex-Shader
in struct VertexData
{
    vec3 fragPos_view;
    vec3 normal_view;
    vec3 toLight_view;
    vec3 toCamera_view;

    vec3 color;         // In diesem Fall: die transformierte (und abs() genommene) Normale
    vec2 texCoords;     // (neu) Texturkoordinaten, vom Vertex-Shader übergeben
} vertexData;

const float gammaVal = 2.2;

vec3 invgamma(vec3 color_gamma) {
    return pow(color_gamma, vec3(gammaVal)); // von sRGB → linear
}

vec3 gamma(vec3 color_linear) {
    return pow(color_linear, vec3(1.0 / gammaVal)); // von linear → sRGB
}


// Ausgabe des Fragment-Shaders: finale Farbe des Pixels
out vec4 fragColor;

// Material-Uniforms
uniform sampler2D material_diffuse;
uniform sampler2D material_specular;
uniform sampler2D material_emissive;
uniform float material_shininess;


void main(){

    // --- Normieren aller Richtungsvektoren ---
    vec3 N = normalize(vertexData.normal_view);
    vec3 L = normalize(vertexData.toLight_view);
    vec3 V = normalize(vertexData.toCamera_view);
    vec3 R = reflect(-L, N);

    // Entfernung zur Lichtquelle (Länge des Vektors toLight_view)
    float distance = length(vertexData.toLight_view);

    // Dämpfungsfaktor berechnen
    float attenuation = 1.0 / (1.0 + 1.0 * distance + 1.0 * distance * distance);

    // Spotlight-Einfluss berechnen (θ = Winkel zwischen Lichtstrahl und Fragment)
    float theta = dot(-L, normalize(spot_direction_view));
    float epsilon = spot_innerCutoff - spot_outerCutoff;
    float intensity = clamp((theta - spot_outerCutoff) / epsilon, 0.0, 1.0);


    // --- Texturen laden ---
    vec3 diffuseColor = invgamma(texture(material_diffuse, vertexData.texCoords).rgb);
    vec3 specularColor = invgamma(texture(material_specular, vertexData.texCoords).rgb);
    vec3 emissiveColor = invgamma(texture(material_emissive, vertexData.texCoords).rgb) * emission_tint;;

    // --- Phong-Berechnung ---
    float diff = max(dot(N, L), 0.0);
    float spec = pow(max(dot(R, V), 0.0), material_shininess);

    // PointLight-Anteil
    vec3 diffuse_PL = attenuation * diff * diffuseColor * pointLight_color;
    vec3 specular_PL = attenuation * spec * specularColor * pointLight_color;

    // Spotlight-Anteil
    vec3 diffuse_SP = (attenuation * intensity * 5) * diff * diffuseColor * spotLight_color;
    vec3 specular_SP = (attenuation * intensity * 5) * spec * specularColor * spotLight_color;

    // Ambient
    vec3 ambient = 0.001 * diffuseColor;

    // Final
    vec3 result = ambient + diffuse_PL + specular_PL + diffuse_SP + specular_SP + emissiveColor;
    fragColor = vec4(gamma(result), 1.0);


    //fragColor = vec4(result, 1.0);


     // Hole die Texturfarbe an der Stelle texCoords
     //vec4 emissiveColor = texture(material_emissive, vertexData.texCoords);

     // Verwende die Farbe der emissive-Textur als endgültige Fragmentfarbe
     //fragColor = emissiveColor;

}
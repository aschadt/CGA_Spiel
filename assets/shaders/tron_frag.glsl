#version 330 core
// Spotlight
uniform vec3 spot_direction_view;     // Richtung des Spotlights im Viewspace
uniform float spot_innerCutoff;       // Cosinus von innerem Kegelwinkel (ϕ)
uniform float spot_outerCutoff;       // Cosinus von äußerem Kegelwinkel (γ)

// Lichtquellen
uniform vec3 pointLight_color;        // (nicht mehr verwendet bei mehreren Lichtern)
uniform vec3 spotLight_color;         // Lichtfarbe des Spotlights

uniform vec3 emission_tint;           // Zeit- oder Objektabhängiger Farbwert für Emission (z.B. grün beim Boden)

#define MAX_POINT_LIGHTS 5
uniform int numPointLights;                                       // aktive Lichtquellen
uniform vec3 pointLight_positions[MAX_POINT_LIGHTS];              // Positionen der zusätzlichen Punktlichter (Viewspace)
uniform vec3 pointLight_colors[MAX_POINT_LIGHTS];                 // Farben der Punktlichter

// Empfängt interpolierte Werte vom Vertex-Shader
in struct VertexData
{
    vec3 fragPos_view;      // Fragmentposition im Viewspace
    vec3 normal_view;       // Normale im Viewspace
    vec3 toLight_view;      // Richtung zum Hauptlicht
    vec3 toCamera_view;     // Richtung zur Kamera

    vec3 color;             // Visualisierte Normale (wird hier nicht verwendet)
    vec2 texCoords;         // UV-Koordinaten der Textur
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

    // --- Normalisieren aller relevanten Richtungsvektoren ---
    vec3 N = normalize(vertexData.normal_view);     // Normalvektor
    vec3 L = normalize(vertexData.toLight_view);    // Richtung zur Hauptlichtquelle (Motorrad)
    vec3 V = normalize(vertexData.toCamera_view);   // Richtung zur Kamera
    vec3 R = reflect(-L, N);                        // Reflektierter Lichtstrahl (für Phong)
    vec3 H = normalize(L + V);                      // Halfway-Vektor für Blinn-Phong

    // Entfernung zur HauptLichtquelle (Länge des Vektors toLight_view)
    float distance = length(vertexData.toLight_view);

    // Lichtabschwächung nach Entfernung (Attenuation)
    float attenuation = 1.0 / (1.0 + 1.0 * distance + 1.0 * distance * distance);

    // Spotlight-Einfluss berechnen (θ = Winkel zwischen Lichtstrahl und Fragment)
    float theta = dot(-L, normalize(spot_direction_view));  // Winkel zwischen Licht und Richtung
    float epsilon = spot_innerCutoff - spot_outerCutoff;    // Übergangsbereich
    float intensity = clamp((theta - spot_outerCutoff) / epsilon, 0.0, 1.0); // Lichtintensität im Spotkegel


    // --- Texturen laden ---
    vec3 diffuseColor = invgamma(texture(material_diffuse, vertexData.texCoords).rgb);
    vec3 specularColor = invgamma(texture(material_specular, vertexData.texCoords).rgb);
    vec3 emissiveColor = invgamma(texture(material_emissive, vertexData.texCoords).rgb) * emission_tint;;

    // ---(Blinn-) Phong-Berechnung ---
    float diff = max(dot(N, L), 0.0);
    //float spec = pow(max(dot(R, V), 0.0), material_shininess);    // Phong
    float spec = pow(max(dot(N, H), 0.0), material_shininess);      // Blinn-Phong

    // 4 EckpointLights
        vec3 pointDiffuse = vec3(0.0);    // Summe der Diffusanteile aller Pointlights
        vec3 pointSpecular = vec3(0.0);   // Summe der Specularanteile aller Pointlights

        for (int i = 0; i < numPointLights; ++i) {
            vec3 L_i = normalize(pointLight_positions[i] - vertexData.fragPos_view);    // Richtung zur Hauptlichtquelle (Motorrad)
            float d_i = length(pointLight_positions[i] - vertexData.fragPos_view);      // Abstand zum Lichtpunkt (distance)
            float att_i = 1.0 / (1.0 + 3.0 * d_i + 0.1 * d_i * d_i);                    // Attenuation (angepasst)

            vec3 H_i = normalize(L_i + V);                                      // Halfway-Vektor
            float diff_i = max(dot(N, L_i), 0.0);                               // Diffuskomponente
            float spec_i = pow(max(dot(N, H_i), 0.0), material_shininess);      // Specularkomponente

            // Beleuchtung pro Lichtquelle addieren
            pointDiffuse += att_i * diff_i * diffuseColor * pointLight_colors[i];
            pointSpecular += att_i * spec_i * specularColor * pointLight_colors[i];
        }

    // PointLight-Anteil
    vec3 diffuse_PL = pointDiffuse;
    vec3 specular_PL = pointSpecular;

    // Spotlight-Anteil
    vec3 diffuse_SP = (attenuation * intensity * 5) * diff * diffuseColor * spotLight_color;
    vec3 specular_SP = (attenuation * intensity * 5) * spec * specularColor * spotLight_color;



    // Ambient
    vec3 ambient = 0.001 * diffuseColor;

    // Final
    vec3 result = ambient + diffuse_PL + specular_PL + diffuse_SP + specular_SP + emissiveColor;
    fragColor = vec4(gamma(result), 1.0);

}
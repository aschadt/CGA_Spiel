#version 330 core
// Eingabewerte aus dem Vertex-Buffer
layout(location = 0) in vec3 position;      // Vertex-Position im Objektkoordinatensystem
layout(location = 1) in vec2 texCoords;     // Texture-Koordinaten (UVs)
layout(location = 2) in vec3 normal;        // Vertex-Normale im Objektkoordinatensystem

// --- Uniforms (werden vom CPU-Code gesetzt)
uniform mat4 model_matrix;                  // Modellmatrix: Objekt- in Weltkoordinaten
uniform mat4 view_matrix;                   // View-Matrix: Welt- in Kamerakoordinaten
uniform mat4 proj_matrix;                   // Projektionsmatrix: Kamera- in Clipraum
uniform mat4 lightSpaceMatrix;              // NEU: Licht-Raum Transformation

uniform vec2 material_tcMultiplier;         // Uniform zur Steuerung der Texturwiederholung
uniform vec3 light_position;                // Pointlight

// Struct zur Übergabe an den Fragment-Shader
out struct VertexData {
    vec3 fragPos_view;                      // Fragmentposition im Viewspace
    vec3 normal_view;                       // Normale im Viewspace
    vec3 toLight_view;                      // Licht-Vektor (nicht normalisiert)
    vec3 toCamera_view;                     // Kamera-Vektor (nicht normalisiert)
    vec3 color;                             // Normale als Farbe speichern
    vec2 texCoords;                         // Texturkoordinaten für Fragment-Shader
    vec4 fragPos_lightSpace;                // NEU: Position im Licht-Raum
} vertexData;

void main() {
    // Welt-Transformation
    vec4 worldPosition = model_matrix * vec4(position, 1.0);
    vec3 worldNormal = mat3(transpose(inverse(model_matrix))) * normal;

    // Umwandlung in Viewspace
    vec4 viewPosition = view_matrix * worldPosition;
    vec3 lightPos_view = (view_matrix * vec4(light_position, 1.0)).xyz;

    // Ergebnisposition für GPU-Rasterisierung
    gl_Position = proj_matrix * viewPosition;

    // Standard-Farbe (optional zur Visualisierung)
    vertexData.color = abs(normalize(worldNormal));

    // Texturkoordinaten skalieren
    vertexData.texCoords = texCoords * material_tcMultiplier;

    // Übergabe an Fragment-Shader (Viewspace-Daten)
    vertexData.fragPos_view   = viewPosition.xyz;
    vertexData.normal_view    = mat3(view_matrix * model_matrix) * normal;
    vertexData.toLight_view   = lightPos_view - viewPosition.xyz;
    vertexData.toCamera_view  = -viewPosition.xyz; // Kamera in (0,0,0)

    // NEU: Position im Licht-Raum berechnen
    vertexData.fragPos_lightSpace = lightSpaceMatrix * worldPosition;
}
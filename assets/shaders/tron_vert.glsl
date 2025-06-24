#version 330 core
// Eingabewerte aus dem Vertex-Buffer
layout(location = 0) in vec3 position;      // Vertex-Position im Objektkoordinatensystem
layout(location = 1) in vec2 texCoords;     // Texture-Koordinaten (UVs)
layout(location = 2) in vec3 normal;        // Vertex-Normale im Objektkoordinatensystem


// --- Uniforms (werden vom CPU-Code gesetzt)
uniform mat4 model_matrix;                  // Modellmatrix: Objekt- in Weltkoordinaten
uniform mat4 view_matrix;                   // View-Matrix: Welt- in Kamerakoordinaten
uniform mat4 proj_matrix;                   // Projektionsmatrix: Kamera- in Clipraum

uniform vec2 material_tcMultiplier;         // Uniform zur Steuerung der Texturwiederholung

// Struct zur Übergabe an den Fragment-Shader
out struct VertexData {
    vec3 color;                             // Normale als Farbe speichern
    vec2 texCoords;                         // Texturkoordinaten für Fragment-Shader
} vertexData;

void main() {
    // Objektkoordinaten in einen 4D-Vektor umwandeln
    vec4 objectSpacePos = vec4(position, 1.0);

    // Berechne Model-View-Matrix aus Modell- und View-Matrix
    mat4 modelViewMatrix = view_matrix * model_matrix;

    // Berechne die endgültige Position im Clipraum für die Ausgabe auf dem Bildschirm
    gl_Position = proj_matrix * modelViewMatrix * objectSpacePos;

    // --- Normale korrekt transformieren ---
    // Für korrekte Beleuchtung müssen Normalen mit der Inversen-Transponierten der oberen 3x3 der Model-View-Matrix transformiert werden
    mat3 normalMatrix = transpose(inverse(mat3(modelViewMatrix)));

    // Wandle die Normale ins Kamerakoordinatensystem um und normalisiere sie
    vec3 worldNormal = normalize(normalMatrix * normal);

    // Gib die Normale (als Farbe) an den Fragment Shader weiter
    vertexData.color = abs(worldNormal);            // abs() sorgt für positive RGB-Werte

    // --- Texturkoordinaten skalieren ---
    // Multipliziere die UV-Koordinaten mit dem tcMultiplier, um die Textur häufiger zu wiederholen
    vertexData.texCoords = texCoords * material_tcMultiplier;

}

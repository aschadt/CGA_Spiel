#version 330 core

// Empfängt interpolierte Werte vom Vertex-Shader
in struct VertexData
{
    vec3 color;         // In diesem Fall: die transformierte (und abs() genommene) Normale
    vec2 texCoords;     // (neu) Texturkoordinaten, vom Vertex-Shader übergeben
} vertexData;

// Ausgabe des Fragment-Shaders: finale Farbe des Pixels
out vec4 fragColor;

// Material-Uniforms
uniform sampler2D material_emissive;  // Emissive-Textur

void main(){

     // Hole die Texturfarbe an der Stelle texCoords
     vec4 emissiveColor = texture(material_emissive, vertexData.texCoords);

     // Verwende die Farbe der emissive-Textur als endgültige Fragmentfarbe
     fragColor = emissiveColor;


}
#version 330 core
layout (location = 0) in vec2 aPos; // Pixel-Koordinaten

uniform vec2 uViewport; // (width, height)

void main() {
    // Pixel → NDC
    vec2 ndc = vec2(
    (aPos.x / uViewport.x) * 2.0 - 1.0,
    1.0 - (aPos.y / uViewport.y) * 2.0
    );
    gl_Position = vec4(ndc, 0.0, 1.0);
}

#version 330 core
out vec4 FragColor;

uniform float fadeAlpha;   // 0.0 … 1.0
uniform vec3  fadeColor;   // z.B. Schwarz

void main() {
    float a = clamp(fadeAlpha, 0.0, 1.0);
    FragColor = vec4(fadeColor, a);
}

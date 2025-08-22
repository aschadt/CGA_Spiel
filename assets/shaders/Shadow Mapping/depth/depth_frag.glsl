#version 330 core
out vec4 fragColor;

void main() {
    float depth = gl_FragCoord.z;
    float brightness = pow(1.0 - depth, 0.5); // Helligkeit „hochziehen“
    fragColor = vec4(0.0, brightness, 0.0, 1.0);
}


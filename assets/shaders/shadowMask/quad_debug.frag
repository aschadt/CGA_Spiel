#version 330 core
out vec4 fragColor;

uniform sampler2D debugTex;

in vec2 TexCoords;

void main()
{
    float v = texture(debugTex, TexCoords).r;
    fragColor = vec4(v, v, v, 1.0); // Graustufen
}
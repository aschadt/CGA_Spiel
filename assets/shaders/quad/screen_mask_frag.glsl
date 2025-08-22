//  Fragment-Shader, der für jedes Pixel (vUV) die Shadow-Mask-Textur

#version 330 core
in vec2 vUV;
uniform sampler2D uTex;   // deine shadowMaskTex
out vec4 fragColor;
void main(){
    float m = texture(uTex, vUV).r;   // GL_R8 -> .r
    fragColor = vec4(m, m, m, 1.0);
}
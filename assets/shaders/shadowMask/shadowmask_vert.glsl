#version 330 core
layout (location = 0) in vec3 position;

uniform mat4 model_matrix;
uniform mat4 view_matrix;
uniform mat4 proj_matrix;
uniform mat4 lightSpaceMatrix;

out vec4 vFragPosLightSpace;

void main() {
    vec4 worldPos  = model_matrix * vec4(position, 1.0);
    vFragPosLightSpace = lightSpaceMatrix * worldPos;
    gl_Position = proj_matrix * view_matrix * worldPos;
}
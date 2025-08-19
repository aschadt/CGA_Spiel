#version 330 core
// Fullscreen-Triangle über gl_VertexID – keine Vertex-Buffer nötig.
const vec2 V[3] = vec2[](
vec2(-1.0, -1.0),
vec2( 3.0, -1.0),
vec2(-1.0,  3.0)
);
void main() {
    gl_Position = vec4(V[gl_VertexID], 0.0, 1.0);
}

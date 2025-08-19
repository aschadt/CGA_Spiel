package cga.exercise.components.shadow

import cga.exercise.components.shader.ShaderProgram
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL14.GL_TEXTURE_BORDER_COLOR
import org.lwjgl.opengl.GL30.*

class ShadowRenderer(
    private val shadowWidth: Int = 1024,
    private val shadowHeight: Int = 1024
) {
    // Depth-FBO + -Textur
    private val depthMapFBO: Int
    val depthMapTex: Int

    // Minimaler Depth-Shader (assets/shaders/depth_vert.glsl, depth_frag.glsl)
    private val depthShader = ShaderProgram(
        "assets/shaders/Shadow Mapping/depth/depth_vert.glsl",
        "assets/shaders/Shadow Mapping/depth/depth_frag.glsl"
    )

    fun depthShader(): ShaderProgram = depthShader

    init {
        depthMapFBO = glGenFramebuffers()
        depthMapTex = glGenTextures()

        glBindTexture(GL_TEXTURE_2D, depthMapTex)
        glTexImage2D(
            GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT,
            shadowWidth, shadowHeight, 0,
            GL_DEPTH_COMPONENT, GL_FLOAT, 0L
        )
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER)
        val border = floatArrayOf(1f, 1f, 1f, 1f)
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, border)

        glBindFramebuffer(GL_FRAMEBUFFER, depthMapFBO)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthMapTex, 0)
        glDrawBuffer(GL_NONE)
        glReadBuffer(GL_NONE)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    /**
     * Orthographische LightSpace-Matrix (für Directional Light).
     * Passe bounds/near/far je nach Szene an.
     */
    fun buildLightSpaceOrtho(
        lightPos: Vector3f,
        lookAt: Vector3f = Vector3f(0f, 0f, 0f),
        up: Vector3f = Vector3f(0f, 1f, 0f),
        bounds: Float = 30f,
        near: Float = 0.1f,
        far: Float = 100f
    ): Matrix4f {
        val lightProj = Matrix4f().ortho(-bounds, bounds, -bounds, bounds, near, far)
        val lightView = Matrix4f().lookAt(lightPos, lookAt, up)
        return Matrix4f(lightProj).mul(lightView)
    }

    /**
     * Perspektivische LightSpace-Matrix (für Spot/Point Light Shadow-Cones).
     */
    fun buildLightSpacePerspective(
        lightPos: Vector3f,
        target: Vector3f,
        up: Vector3f = Vector3f(0f, 1f, 0f),
        fovRad: Float = Math.toRadians(90.0).toFloat(),
        near: Float = 0.1f,
        far: Float = 100f
    ): Matrix4f {
        val lightProj = Matrix4f().perspective(fovRad, 1f, near, far)
        val lightView = Matrix4f().lookAt(lightPos, target, up)
        return Matrix4f(lightProj).mul(lightView)
    }

    /**
     * Depth-Pass starten. Ruft vor dem eigentlichen Zeichnen auf.
     * Du gibst die vorberechnete lightSpaceMatrix hinein.
     */
    fun beginDepthPass(lightSpaceMatrix: Matrix4f) {
        glViewport(0, 0, shadowWidth, shadowHeight)
        glBindFramebuffer(GL_FRAMEBUFFER, depthMapFBO)
        glClear(GL_DEPTH_BUFFER_BIT)

        // Optional: Front-Face culling zur Reduktion von Peter-Panning (nicht für Single-Plane-Böden)
        glEnable(GL_CULL_FACE)
        glCullFace(GL_FRONT)

        depthShader.use()
        depthShader.setUniform("lightSpaceMatrix", lightSpaceMatrix)
    }

    /**
     * Während des Depth-Passes pro Objekt aufrufen.
     * Übergib je Objekt seine Welt-Modelmatrix.
     */
    fun drawDepth(modelMatrix: Matrix4f, drawCall: () -> Unit) {
        // Beide Namensvarianten setzen, damit du Renderable.render() weiterverwenden kannst
        depthShader.setUniform("model", modelMatrix, false)
        depthShader.setUniform("model_matrix", modelMatrix, false)
        drawCall()
    }

    /**
     * Depth-Pass beenden. Viewport und State werden nicht zurückgesetzt.
     */
    fun endDepthPass() {
        glCullFace(GL_BACK)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    /**
     * Shadow-Map und LightSpaceMatrix im zweiten Pass binden.
     * @param shader    dein Beleuchtungs/Shadow-Shader
     * @param unit      Textureinheit für die Shadow-Map (z.B. 1)
     */
    fun bindForScenePass(shader: ShaderProgram, lightSpaceMatrix: Matrix4f, unit: Int = 1) {
        shader.use()
        shader.setUniform("lightSpaceMatrix", lightSpaceMatrix)
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, depthMapTex)
        shader.setUniform("shadowMap", unit)
    }

    fun cleanup() {
        glDeleteFramebuffers(depthMapFBO)
        glDeleteTextures(depthMapTex)
        depthShader.cleanup()
    }
}
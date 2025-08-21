package cga.exercise.components.level

import cga.exercise.components.geometry.Material
import cga.exercise.components.geometry.Mesh
import cga.exercise.components.geometry.Renderable
import cga.exercise.components.geometry.VertexAttribute
import cga.exercise.components.texture.Texture2D
import cga.framework.OBJLoader.loadOBJ
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.GL_FLOAT

object LevelLoader {
    fun loadLevel1(): Level {
        // Texturen laden
        val diffuseWall = Texture2D("assets/textures/red_brick_diff_2k.jpg", true)
        val roughWall = Texture2D("assets/textures/red_brick_rough_2k.jpg", true)
        val normalWall = Texture2D("assets/textures/red_brick_nor_gl_2k.jpg", true)

        val diffuseGround = Texture2D("assets/textures/gray_rocks_diff_2k.jpg", true)
        val roughGround = Texture2D("assets/textures/gray_rocks_rough_2k.jpg", true)
        val normalGround = Texture2D("assets/textures/gray_rocks_nor_gl_2k.jpg", true)

        val diffuseBauer = Texture2D("assets/textures/plywood_diff_2k.jpg", true)
        val roughBauer = Texture2D("assets/textures/plywood_rough_2k.jpg", true)
        val normalBauer = Texture2D("assets/textures/plywood_nor_gl_2k.jpg", true)

        val emissiveBlack = Texture2D("assets/textures/schwarz.png", true)

        val startPosObj1 = Vector3f(2f, 2f, -2f)
        val startPosObj2 = Vector3f(0f, 2f, -2f)
        val startPosObj3 = Vector3f(-2f, 2f, -2f)

        val wallMaterial = Material(
            diff = diffuseWall,
            emit = emissiveBlack,
            specular = emissiveBlack,
            roughness = roughWall,
            normal = normalWall,
            shininess = 60.0f,
            tcMultiplier = Vector2f(8.0f, 8.0f)
        )
        val groundMaterial = Material(
            diff = diffuseGround,
            emit = emissiveBlack,
            specular = emissiveBlack,
            roughness = roughGround,
            normal = normalGround,
            shininess = 60.0f,
            tcMultiplier = Vector2f(16.0f, 16.0f)
        )

        val bauerMaterial = Material(
            diff = diffuseBauer,
            emit = emissiveBlack,
            specular = emissiveBlack,
            roughness = roughBauer,
            normal = normalBauer,
            shininess = 60.0f,
            tcMultiplier = Vector2f(2.0f, 2.0f)
        )

        // Ground
        val groundObj = loadOBJ("assets/models/roomGround.obj")
        val groundMeshList = groundObj.objects[0].meshes
        val groundAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val groundMesh = Mesh(groundMeshList[0].vertexData, groundMeshList[0].indexData, groundAttribs, groundMaterial)
        val groundRenderable = Renderable(mutableListOf(groundMesh))

        // Room
        val roomObj = loadOBJ("assets/models/roomWall_v2.obj")
        val roomMeshList = roomObj.objects[0].meshes
        val roomAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val roomMesh = Mesh(roomMeshList[0].vertexData, roomMeshList[0].indexData, roomAttribs, wallMaterial)
        val roomRenderable = Renderable(mutableListOf(roomMesh))
        roomRenderable.rotate(0f, Math.toRadians(-90.0).toFloat(), 0f)
        roomRenderable.translate(Vector3f(0.0f, 0.0f, 0.0f))

        // Obj 1 (Cube)
        val obj1 = loadOBJ("assets/models/Bauer_T1.obj")
        val obj1MeshList = obj1.objects[0].meshes
        val obj1Attribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val obj1Mesh = Mesh(obj1MeshList[0].vertexData, obj1MeshList[0].indexData, obj1Attribs, bauerMaterial)
        val obj1Renderable = Renderable(mutableListOf(obj1Mesh)).apply {
            translate(startPosObj1)
            scale(Vector3f(0.5f, 0.5f, 0.5f))
        }

        // Obj 2 (Cone)
        val obj2 = loadOBJ("assets/models/Bauer_T2.obj")
        val obj2MeshList = obj2.objects[0].meshes
        val obj2Attribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val obj2Mesh = Mesh(obj2MeshList[0].vertexData, obj2MeshList[0].indexData, obj2Attribs, bauerMaterial)
        val obj2Renderable = Renderable(mutableListOf(obj2Mesh)).apply {
            translate(startPosObj2)
            scale(Vector3f(0.5f, 0.5f, 0.5f))
        }

        // Obj 3 (Zylinder)
        val obj3 = loadOBJ("assets/models/Bauer_T3.obj")
        val obj3MeshList = obj3.objects[0].meshes
        val obj3Attribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val obj3Mesh = Mesh(obj3MeshList[0].vertexData, obj3MeshList[0].indexData, obj3Attribs, bauerMaterial)
        val obj3Renderable = Renderable(mutableListOf(obj3Mesh)).apply {
            translate(startPosObj3)
            scale(Vector3f(0.5f, 0.5f, 0.5f))
        }

        return Level(ground = groundRenderable,
            room = roomRenderable,
            objects = listOf(obj1Renderable, obj2Renderable, obj3Renderable),
            )
    }

    fun loadLevel2(): Level {
        // Texturen laden
        val diffuseWall = Texture2D("assets/textures/GreyMatteTiles01_2K_BaseColor.png", true)
        val roughWall = Texture2D("assets/textures/GreyMatteTiles01_2K_Roughness.png", true)
        val normalWall = Texture2D("assets/textures/GreyMatteTiles01_2K_Normal.png", true)

        val diffuseGround = Texture2D("assets/textures/WhiteHexagonalTiles01_2K_BaseColor.png", true)
        val roughGround = Texture2D("assets/textures/WhiteHexagonalTiles01_2K_Roughness.png", true)
        val normalGround = Texture2D("assets/textures/WhiteHexagonalTiles01_2K_Normal.png", true)

        val diffuseObj = Texture2D("assets/textures/Porcelain001_2K-JPG_Color.jpg", true)
        val roughObj = Texture2D("assets/textures/Porcelain001_2K-JPG_Roughness.jpg", true)
        val normalObj = Texture2D("assets/textures/Porcelain001_2K-JPG_NormalGL.jpg", true)

        val emissiveBlack = Texture2D("assets/textures/schwarz.png", true)

        val startPosObj1 = Vector3f(2f, 2f, -2f)
        val startPosObj2 = Vector3f(0f, 2f, -2f)
        val startPosObj3 = Vector3f(-2f, 2f, -2f)

        val wallMaterial = Material(
            diff = diffuseWall,
            emit = emissiveBlack,
            specular = emissiveBlack,
            roughness = roughWall,
            normal = normalWall,
            shininess = 60.0f,
            tcMultiplier = Vector2f(4.0f, 8.0f)
        )
        val groundMaterial = Material(
            diff = diffuseGround,
            emit = emissiveBlack,
            specular = emissiveBlack,
            roughness = roughGround,
            normal = normalGround,
            shininess = 60.0f,
            tcMultiplier = Vector2f(16.0f, 16.0f)
        )

        val bauerMaterial = Material(
            diff = diffuseObj,
            emit = emissiveBlack,
            specular = emissiveBlack,
            roughness = roughObj,
            normal = normalObj,
            shininess = 60.0f,
            tcMultiplier = Vector2f(2.0f, 2.0f)
        )

        // Ground
        val groundObj = loadOBJ("assets/models/roomGround.obj")
        val groundMeshList = groundObj.objects[0].meshes
        val groundAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val groundMesh = Mesh(groundMeshList[0].vertexData, groundMeshList[0].indexData, groundAttribs, groundMaterial)
        val groundRenderable = Renderable(mutableListOf(groundMesh))

        // Room
        val roomObj = loadOBJ("assets/models/roomWall_v2.obj")
        val roomMeshList = roomObj.objects[0].meshes
        val roomAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val roomMesh = Mesh(roomMeshList[0].vertexData, roomMeshList[0].indexData, roomAttribs, wallMaterial)
        val roomRenderable = Renderable(mutableListOf(roomMesh))
        roomRenderable.rotate(0f, Math.toRadians(-90.0).toFloat(), 0f)

        // Obj 1 (Cube)
        val obj1 = loadOBJ("assets/models/teapot_T1.obj")
        val obj1MeshList = obj1.objects[0].meshes
        val obj1Attribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val obj1Mesh = Mesh(obj1MeshList[0].vertexData, obj1MeshList[0].indexData, obj1Attribs, bauerMaterial)
        val obj1Renderable = Renderable(mutableListOf(obj1Mesh)).apply {
            translate(startPosObj1)
            scale(Vector3f(0.5f, 0.5f, 0.5f))
        }

        // Obj 2 (Cone)
        val obj2 = loadOBJ("assets/models/teapot_T2.obj")
        val obj2MeshList = obj2.objects[0].meshes
        val obj2Attribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val obj2Mesh = Mesh(obj2MeshList[0].vertexData, obj2MeshList[0].indexData, obj2Attribs, bauerMaterial)
        val obj2Renderable = Renderable(mutableListOf(obj2Mesh)).apply {
            translate(startPosObj2)
            scale(Vector3f(0.5f, 0.5f, 0.5f))
        }

        // Obj 3 (Zylinder)
        val obj3 = loadOBJ("assets/models/teapot_T3.obj")
        val obj3MeshList = obj3.objects[0].meshes
        val obj3Attribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val obj3Mesh = Mesh(obj3MeshList[0].vertexData, obj3MeshList[0].indexData, obj3Attribs, bauerMaterial)
        val obj3Renderable = Renderable(mutableListOf(obj3Mesh)).apply {
            translate(startPosObj3)
            scale(Vector3f(0.5f, 0.5f, 0.5f))
        }

        return Level(ground = groundRenderable,
            room = roomRenderable,
            objects = listOf(obj1Renderable, obj2Renderable, obj3Renderable),
        )
    }
}
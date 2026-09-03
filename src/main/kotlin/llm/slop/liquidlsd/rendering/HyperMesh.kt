package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.*

private val logger = KotlinLogging.logger {}

/**
 * 4D single-precision vector representation for 4-polytope computations.
 */
data class Vector4(val x: Float, val y: Float, val z: Float, val w: Float) {
    fun length(): Float = sqrt(x * x + y * y + z * z + w * w)

    fun normalize(): Vector4 {
        val len = length()
        return if (len > 1e-7f) Vector4(x / len, y / len, z / len, w / len) else Vector4(0f, 0f, 0f, 0f)
    }

    fun dot(other: Vector4): Float = x * other.x + y * other.y + z * other.z + w * other.w

    fun distanceSquared(other: Vector4): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        val dw = w - other.w
        return dx * dx + dy * dy + dz * dz + dw * dw
    }

    operator fun plus(other: Vector4): Vector4 = Vector4(x + other.x, y + other.y, z + other.z, w + other.w)
    operator fun minus(other: Vector4): Vector4 = Vector4(x - other.x, y - other.y, z - other.z, w - other.w)
    operator fun times(scalar: Float): Vector4 = Vector4(x * scalar, y * scalar, z * scalar, w * scalar)
    operator fun div(scalar: Float): Vector4 = Vector4(x / scalar, y / scalar, z / scalar, w / scalar)
}

/**
 * Visual source representing 4D Polytopes (600-cell and 120-cell) with real-time 4D rotation,
 * perspective/stereographic projection, Hopf fibration coloring, and GPU-accelerated strut rendering.
 */
class HyperMesh(
    id: String,
    displayName: String,
    shader: Shader,
    parameters: LinkedHashMap<String, ModulatableParameter>,
    globalAlpha: ModulatableParameter = ModulatableParameter(1.0f),
    hasFeedback: Boolean = false,
    ownsShader: Boolean = false
) : DynamicVisualSource(id, displayName, shader, parameters, globalAlpha = globalAlpha, hasFeedback = hasFeedback, ownsShader = ownsShader) {

    var edgeVao600: Int = 0
        private set
    var edgeVao120: Int = 0
        private set
    var nodeVao600: Int = 0
        private set
    var nodeVao120: Int = 0
        private set

    private var edgeVbo600: Int = 0
    private var edgeIbo600: Int = 0
    private var edgeVbo120: Int = 0
    private var edgeIbo120: Int = 0
    private var nodeVbo600: Int = 0
    private var nodeIbo600: Int = 0
    private var nodeVbo120: Int = 0
    private var nodeIbo120: Int = 0

    var edgeCount600: Int = 0
        private set
    var edgeCount120: Int = 0
        private set
    var vertexCount600: Int = 0
        private set
    var vertexCount120: Int = 0
        private set

    init {
        // Build VAO/VBO once if in main OpenGL context
        initializeBuffers()
    }

    private fun initializeBuffers() {
        val geo600 = Geometry600Cell
        val geo120 = Geometry120Cell

        edgeCount600 = geo600.edges.size
        vertexCount600 = geo600.vertices.size
        edgeCount120 = geo120.edges.size
        vertexCount120 = geo120.vertices.size

        // 1. Build 600-cell Edge Buffer
        // Each edge has 4 vertices forming a quad (2 triangles)
        // Vertex format: [posA.xyzw (4), posB.xyzw (4), hopfA.xy (2), hopfB.xy (2), corner.xy (2)] = 14 floats
        val edgeFloats600 = FloatArray(geo600.edges.size * 4 * 14)
        val edgeIndices600 = IntArray(geo600.edges.size * 6)
        buildEdgeMesh(geo600.vertices, geo600.hopfCoords, geo600.edges, edgeFloats600, edgeIndices600)

        val (vaoE600, vboE600, iboE600) = createVaoIbo(edgeFloats600, edgeIndices600)
        edgeVao600 = vaoE600
        edgeVbo600 = vboE600
        edgeIbo600 = iboE600

        // 2. Build 600-cell Node Buffer (quad billboard per vertex)
        // Vertex format: [pos.xyzw (4), hopf.xy (2), corner.xy (2)] = 8 floats
        val nodeFloats600 = FloatArray(geo600.vertices.size * 4 * 8)
        val nodeIndices600 = IntArray(geo600.vertices.size * 6)
        buildNodeMesh(geo600.vertices, geo600.hopfCoords, nodeFloats600, nodeIndices600)

        val (vaoN600, vboN600, iboN600) = createNodeVaoIbo(nodeFloats600, nodeIndices600)
        nodeVao600 = vaoN600
        nodeVbo600 = vboN600
        nodeIbo600 = iboN600

        // 3. Build 120-cell Edge Buffer
        val edgeFloats120 = FloatArray(geo120.edges.size * 4 * 14)
        val edgeIndices120 = IntArray(geo120.edges.size * 6)
        buildEdgeMesh(geo120.vertices, geo120.hopfCoords, geo120.edges, edgeFloats120, edgeIndices120)

        val (vaoE120, vboE120, iboE120) = createVaoIbo(edgeFloats120, edgeIndices120)
        edgeVao120 = vaoE120
        edgeVbo120 = vboE120
        edgeIbo120 = iboE120

        // 4. Build 120-cell Node Buffer
        val nodeFloats120 = FloatArray(geo120.vertices.size * 4 * 8)
        val nodeIndices120 = IntArray(geo120.vertices.size * 6)
        buildNodeMesh(geo120.vertices, geo120.hopfCoords, nodeFloats120, nodeIndices120)

        val (vaoN120, vboN120, iboN120) = createNodeVaoIbo(nodeFloats120, nodeIndices120)
        nodeVao120 = vaoN120
        nodeVbo120 = vboN120
        nodeIbo120 = iboN120

        logger.debug { "HyperMesh initialized with 600-cell ($vertexCount600 verts, $edgeCount600 edges) and 120-cell ($vertexCount120 verts, $edgeCount120 edges)" }
    }

    private fun createVaoIbo(vertexData: FloatArray, indexData: IntArray): Triple<Int, Int, Int> {
        val vBuffer: FloatBuffer = MemoryUtil.memAllocFloat(vertexData.size)
        vBuffer.put(vertexData).flip()
        val iBuffer: IntBuffer = MemoryUtil.memAllocInt(indexData.size)
        iBuffer.put(indexData).flip()

        var vao = 0
        var vbo = 0
        var ibo = 0
        try {
            vao = glGenVertexArrays()
            vbo = glGenBuffers()
            ibo = glGenBuffers()

            glBindVertexArray(vao)

            glBindBuffer(GL_ARRAY_BUFFER, vbo)
            glBufferData(GL_ARRAY_BUFFER, vBuffer, GL_STATIC_DRAW)

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo)
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, iBuffer, GL_STATIC_DRAW)

            val stride = 14 * Float.SIZE_BYTES

            // Location 0: vec4 aPosA
            glVertexAttribPointer(0, 4, GL_FLOAT, false, stride, 0)
            glEnableVertexAttribArray(0)

            // Location 1: vec4 aPosB
            glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, (4 * Float.SIZE_BYTES).toLong())
            glEnableVertexAttribArray(1)

            // Location 2: vec2 aHopfA
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, (8 * Float.SIZE_BYTES).toLong())
            glEnableVertexAttribArray(2)

            // Location 3: vec2 aHopfB
            glVertexAttribPointer(3, 2, GL_FLOAT, false, stride, (10 * Float.SIZE_BYTES).toLong())
            glEnableVertexAttribArray(3)

            // Location 4: vec2 aCorner
            glVertexAttribPointer(4, 2, GL_FLOAT, false, stride, (12 * Float.SIZE_BYTES).toLong())
            glEnableVertexAttribArray(4)

            glBindVertexArray(0)
            glBindBuffer(GL_ARRAY_BUFFER, 0)
        } finally {
            MemoryUtil.memFree(vBuffer)
            MemoryUtil.memFree(iBuffer)
        }
        return Triple(vao, vbo, ibo)
    }

    private fun createNodeVaoIbo(vertexData: FloatArray, indexData: IntArray): Triple<Int, Int, Int> {
        val vBuffer: FloatBuffer = MemoryUtil.memAllocFloat(vertexData.size)
        vBuffer.put(vertexData).flip()
        val iBuffer: IntBuffer = MemoryUtil.memAllocInt(indexData.size)
        iBuffer.put(indexData).flip()

        var vao = 0
        var vbo = 0
        var ibo = 0
        try {
            vao = glGenVertexArrays()
            vbo = glGenBuffers()
            ibo = glGenBuffers()

            glBindVertexArray(vao)

            glBindBuffer(GL_ARRAY_BUFFER, vbo)
            glBufferData(GL_ARRAY_BUFFER, vBuffer, GL_STATIC_DRAW)

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo)
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, iBuffer, GL_STATIC_DRAW)

            val stride = 8 * Float.SIZE_BYTES

            // Location 0: vec4 aPos
            glVertexAttribPointer(0, 4, GL_FLOAT, false, stride, 0)
            glEnableVertexAttribArray(0)

            // Location 1: vec2 aHopf
            glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, (4 * Float.SIZE_BYTES).toLong())
            glEnableVertexAttribArray(1)

            // Location 2: vec2 aCorner
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, (6 * Float.SIZE_BYTES).toLong())
            glEnableVertexAttribArray(2)

            glBindVertexArray(0)
            glBindBuffer(GL_ARRAY_BUFFER, 0)
        } finally {
            MemoryUtil.memFree(vBuffer)
            MemoryUtil.memFree(iBuffer)
        }
        return Triple(vao, vbo, ibo)
    }

    private fun buildEdgeMesh(
        vertices: List<Vector4>,
        hopfCoords: List<Pair<Float, Float>>,
        edges: List<Pair<Int, Int>>,
        outFloats: FloatArray,
        outIndices: IntArray
    ) {
        val corners = arrayOf(
            Pair(0f, -1f),
            Pair(1f, -1f),
            Pair(1f, 1f),
            Pair(0f, 1f)
        )

        var floatOffset = 0
        var indexOffset = 0

        for (e in edges.indices) {
            val (idxA, idxB) = edges[e]
            val posA = vertices[idxA]
            val posB = vertices[idxB]
            val hopfA = hopfCoords[idxA]
            val hopfB = hopfCoords[idxB]

            val baseVertex = e * 4

            for (c in 0 until 4) {
                val (t, side) = corners[c]
                outFloats[floatOffset++] = posA.x
                outFloats[floatOffset++] = posA.y
                outFloats[floatOffset++] = posA.z
                outFloats[floatOffset++] = posA.w

                outFloats[floatOffset++] = posB.x
                outFloats[floatOffset++] = posB.y
                outFloats[floatOffset++] = posB.z
                outFloats[floatOffset++] = posB.w

                outFloats[floatOffset++] = hopfA.first
                outFloats[floatOffset++] = hopfA.second

                outFloats[floatOffset++] = hopfB.first
                outFloats[floatOffset++] = hopfB.second

                outFloats[floatOffset++] = t
                outFloats[floatOffset++] = side
            }

            // Triangle 1: 0, 1, 2
            outIndices[indexOffset++] = baseVertex + 0
            outIndices[indexOffset++] = baseVertex + 1
            outIndices[indexOffset++] = baseVertex + 2

            // Triangle 2: 0, 2, 3
            outIndices[indexOffset++] = baseVertex + 0
            outIndices[indexOffset++] = baseVertex + 2
            outIndices[indexOffset++] = baseVertex + 3
        }
    }

    private fun buildNodeMesh(
        vertices: List<Vector4>,
        hopfCoords: List<Pair<Float, Float>>,
        outFloats: FloatArray,
        outIndices: IntArray
    ) {
        val corners = arrayOf(
            Pair(-1f, -1f),
            Pair(1f, -1f),
            Pair(1f, 1f),
            Pair(-1f, 1f)
        )

        var floatOffset = 0
        var indexOffset = 0

        for (v in vertices.indices) {
            val pos = vertices[v]
            val hopf = hopfCoords[v]
            val baseVertex = v * 4

            for (c in 0 until 4) {
                val (cx, cy) = corners[c]
                outFloats[floatOffset++] = pos.x
                outFloats[floatOffset++] = pos.y
                outFloats[floatOffset++] = pos.z
                outFloats[floatOffset++] = pos.w

                outFloats[floatOffset++] = hopf.first
                outFloats[floatOffset++] = hopf.second

                outFloats[floatOffset++] = cx
                outFloats[floatOffset++] = cy
            }

            outIndices[indexOffset++] = baseVertex + 0
            outIndices[indexOffset++] = baseVertex + 1
            outIndices[indexOffset++] = baseVertex + 2

            outIndices[indexOffset++] = baseVertex + 0
            outIndices[indexOffset++] = baseVertex + 2
            outIndices[indexOffset++] = baseVertex + 3
        }
    }

    override fun drawTopology() {
        val is120     = (parameters["Polytope"]?.value ?: 0f) >= 0.5f
        val edgeVao   = if (is120) edgeVao120   else edgeVao600
        val edgeCount = if (is120) edgeCount120 else edgeCount600
        val nodeVao   = if (is120) nodeVao120   else nodeVao600
        val vertCount = if (is120) vertexCount120 else vertexCount600
        val nodeSize  = parameters["Node Size"]?.value ?: 0f

        if (edgeVao != 0 && edgeCount > 0) {
            shader.setUniform("uPassType", 0)
            glBindVertexArray(edgeVao)
            glDrawElements(GL_TRIANGLES, edgeCount * 6, GL_UNSIGNED_INT, 0)
            glBindVertexArray(0)
        }
        if (nodeSize > 0.0001f && nodeVao != 0 && vertCount > 0) {
            shader.setUniform("uPassType", 1)
            glBindVertexArray(nodeVao)
            glDrawElements(GL_TRIANGLES, vertCount * 6, GL_UNSIGNED_INT, 0)
            glBindVertexArray(0)
        }
    }

    override fun dispose() {
        super.dispose()
        if (edgeVao600 != 0) {
            glDeleteVertexArrays(edgeVao600)
            glDeleteBuffers(edgeVbo600)
            glDeleteBuffers(edgeIbo600)
            edgeVao600 = 0
        }
        if (nodeVao600 != 0) {
            glDeleteVertexArrays(nodeVao600)
            glDeleteBuffers(nodeVbo600)
            glDeleteBuffers(nodeIbo600)
            nodeVao600 = 0
        }
        if (edgeVao120 != 0) {
            glDeleteVertexArrays(edgeVao120)
            glDeleteBuffers(edgeVbo120)
            glDeleteBuffers(edgeIbo120)
            edgeVao120 = 0
        }
        if (nodeVao120 != 0) {
            glDeleteVertexArrays(nodeVao120)
            glDeleteBuffers(nodeVbo120)
            glDeleteBuffers(nodeIbo120)
            nodeVao120 = 0
        }
    }

    override fun clone(): HyperMesh {
        val clonedParams = LinkedHashMap<String, ModulatableParameter>()
        this.parameters.forEach { (name, param) ->
            clonedParams[name] = param.clone()
        }
        val copy = HyperMesh(
            id = this.id,
            displayName = this.displayName,
            shader = this.shader,
            parameters = clonedParams,
            globalAlpha = this.globalAlpha.clone(),
            hasFeedback = this.hasFeedback,
            ownsShader = false
        )
        copy.edgeVao600 = this.edgeVao600
        copy.nodeVao600 = this.nodeVao600
        copy.edgeVao120 = this.edgeVao120
        copy.nodeVao120 = this.nodeVao120
        copy.edgeCount600 = this.edgeCount600
        copy.vertexCount600 = this.vertexCount600
        copy.edgeCount120 = this.edgeCount120
        copy.vertexCount120 = this.vertexCount120
        return copy
    }

    companion object {
        val PHI = (1f + sqrt(5f)) / 2f
        val INV_PHI = 1f / PHI
        val TWO_PI = (2.0 * Math.PI).toFloat()

        fun computeHopfCoords(v: Vector4): Pair<Float, Float> {
            val hx = 2f * (v.x * v.z + v.y * v.w)
            val hy = 2f * (v.y * v.z - v.x * v.w)
            val hz = (v.x * v.x + v.y * v.y) - (v.z * v.z + v.w * v.w)

            val theta = (acos(hz.coerceIn(-1f, 1f)) / Math.PI).toFloat()
            var phi = (atan2(hy, hx) / TWO_PI)
            if (phi < 0f) phi += 1f

            return Pair(theta, phi)
        }
    }

    /**
     * Geometry generator for the 600-cell (120 vertices, 720 edges).
     */
    object Geometry600Cell {
        val vertices: List<Vector4>
        val hopfCoords: List<Pair<Float, Float>>
        val edges: List<Pair<Int, Int>>

        init {
            val rawVerts = mutableListOf<Vector4>()

            // 1. 8 vertices: (±1, 0, 0, 0) permutations
            val signs1 = floatArrayOf(-1f, 1f)
            for (s in signs1) {
                rawVerts.add(Vector4(s, 0f, 0f, 0f))
                rawVerts.add(Vector4(0f, s, 0f, 0f))
                rawVerts.add(Vector4(0f, 0f, s, 0f))
                rawVerts.add(Vector4(0f, 0f, 0f, s))
            }

            // 2. 16 vertices: (±1/2, ±1/2, ±1/2, ±1/2)
            for (sx in signs1) {
                for (sy in signs1) {
                    for (sz in signs1) {
                        for (sw in signs1) {
                            rawVerts.add(Vector4(sx * 0.5f, sy * 0.5f, sz * 0.5f, sw * 0.5f))
                        }
                    }
                }
            }

            // 3. 96 vertices: Even permutations of (±phi/2, ±1/2, ±1/(2*phi), 0)
            val evenPerms = arrayOf(
                intArrayOf(0, 1, 2, 3), intArrayOf(0, 2, 3, 1), intArrayOf(0, 3, 1, 2),
                intArrayOf(1, 0, 3, 2), intArrayOf(1, 2, 0, 3), intArrayOf(1, 3, 2, 0),
                intArrayOf(2, 0, 1, 3), intArrayOf(2, 1, 3, 0), intArrayOf(2, 3, 0, 1),
                intArrayOf(3, 0, 2, 1), intArrayOf(3, 1, 0, 2), intArrayOf(3, 2, 1, 0)
            )

            val baseVals = floatArrayOf(PHI / 2f, 0.5f, INV_PHI / 2f, 0f)

            for (perm in evenPerms) {
                for (sx in signs1) {
                    for (sy in signs1) {
                        for (sz in signs1) {
                            val coords = FloatArray(4)
                            val signs = floatArrayOf(sx, sy, sz, 1f)
                            for (i in 0 until 4) {
                                coords[perm[i]] = baseVals[i] * signs[i]
                            }
                            rawVerts.add(Vector4(coords[0], coords[1], coords[2], coords[3]))
                        }
                    }
                }
            }

            // Deduplicate vertices
            val uniqueVerts = mutableListOf<Vector4>()
            for (v in rawVerts) {
                val nv = v.normalize()
                if (uniqueVerts.none { it.distanceSquared(nv) < 1e-4f }) {
                    uniqueVerts.add(nv)
                }
            }
            check(uniqueVerts.size == 120) { "Expected 120 vertices for 600-cell, got ${uniqueVerts.size}" }
            vertices = uniqueVerts

            hopfCoords = vertices.map { computeHopfCoords(it) }

            // Find all 720 edges: Adjacent vertices have dot product ≈ 0.809017 (cos(pi/5) = phi / 2)
            val edgeList = mutableListOf<Pair<Int, Int>>()
            val targetDot = PHI / 2f

            for (i in 0 until vertices.size) {
                for (j in (i + 1) until vertices.size) {
                    val dot = vertices[i].dot(vertices[j])
                    if (abs(dot - targetDot) < 0.05f) {
                        edgeList.add(Pair(i, j))
                    }
                }
            }
            check(edgeList.size == 720) { "Expected 720 edges for 600-cell, got ${edgeList.size}" }
            edges = edgeList
        }
    }

    /**
     * Geometry generator for the 120-cell (600 vertices, 1200 edges).
     * Constructed as the dual polytope of the 600-cell.
     */
    object Geometry120Cell {
        val vertices: List<Vector4>
        val hopfCoords: List<Pair<Float, Float>>
        val edges: List<Pair<Int, Int>>

        init {
            val v600 = Geometry600Cell.vertices
            val e600 = Geometry600Cell.edges

            val adj = Array(120) { BooleanArray(120) }
            for ((a, b) in e600) {
                adj[a][b] = true
                adj[b][a] = true
            }

            // Find all 600 regular tetrahedra in the 600-cell
            val tetraCenters = mutableListOf<Vector4>()

            for (i in 0 until 120) {
                for (j in (i + 1) until 120) {
                    if (!adj[i][j]) continue
                    for (k in (j + 1) until 120) {
                        if (!adj[i][k] || !adj[j][k]) continue
                        for (l in (k + 1) until 120) {
                            if (adj[i][l] && adj[j][l] && adj[k][l]) {
                                val center = (v600[i] + v600[j] + v600[k] + v600[l]).normalize()
                                if (tetraCenters.none { it.distanceSquared(center) < 1e-4f }) {
                                    tetraCenters.add(center)
                                }
                            }
                        }
                    }
                }
            }
            check(tetraCenters.size == 600) { "Expected 600 vertices for 120-cell, got ${tetraCenters.size}" }
            vertices = tetraCenters
            hopfCoords = vertices.map { computeHopfCoords(it) }

            // Find all 1,200 edges: In the unit 120-cell, edge neighbor dot product is ≈ 0.9510565f
            val edgeList = mutableListOf<Pair<Int, Int>>()
            val targetDot = 0.9510565f

            for (i in 0 until vertices.size) {
                for (j in (i + 1) until vertices.size) {
                    val dot = vertices[i].dot(vertices[j])
                    if (abs(dot - targetDot) < 0.02f) {
                        edgeList.add(Pair(i, j))
                    }
                }
            }
            check(edgeList.size == 1200) { "Expected 1200 edges for 120-cell, got ${edgeList.size}" }
            edges = edgeList
        }
    }
}

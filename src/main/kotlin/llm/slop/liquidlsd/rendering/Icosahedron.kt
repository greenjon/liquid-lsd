package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import org.lwjgl.opengl.GL33.*
import kotlin.math.*

/**
 * 3D single-precision vector representation for geometric computations.
 */
data class Vector3(val x: Float, val y: Float, val z: Float) {
    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalize(): Vector3 {
        val len = length()
        return if (len > 1e-7f) Vector3(x / len, y / len, z / len) else Vector3(0f, 0f, 0f)
    }

    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3): Vector3 = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float): Vector3 = Vector3(x / scalar, y / scalar, z / scalar)
}

/**
 * 3x3 rotation / transformation matrix.
 */
data class Matrix3(
    val m00: Float, val m01: Float, val m02: Float,
    val m10: Float, val m11: Float, val m12: Float,
    val m20: Float, val m21: Float, val m22: Float
) {
    operator fun times(v: Vector3): Vector3 = Vector3(
        m00 * v.x + m01 * v.y + m02 * v.z,
        m10 * v.x + m11 * v.y + m12 * v.z,
        m20 * v.x + m21 * v.y + m22 * v.z
    )

    operator fun times(o: Matrix3): Matrix3 = Matrix3(
        m00 * o.m00 + m01 * o.m10 + m02 * o.m20,
        m00 * o.m01 + m01 * o.m11 + m02 * o.m21,
        m00 * o.m02 + m01 * o.m12 + m02 * o.m22,

        m10 * o.m00 + m11 * o.m10 + m12 * o.m20,
        m10 * o.m01 + m11 * o.m11 + m12 * o.m21,
        m10 * o.m02 + m11 * o.m12 + m12 * o.m22,

        m20 * o.m00 + m21 * o.m10 + m22 * o.m20,
        m20 * o.m01 + m21 * o.m11 + m22 * o.m21,
        m20 * o.m02 + m21 * o.m12 + m22 * o.m22
    )

    fun determinant(): Float =
        m00 * (m11 * m22 - m12 * m21) -
        m01 * (m10 * m22 - m12 * m20) +
        m02 * (m10 * m21 - m11 * m20)

    fun distanceSquared(o: Matrix3): Float {
        val d00 = m00 - o.m00
        val d01 = m01 - o.m01
        val d02 = m02 - o.m02
        val d10 = m10 - o.m10
        val d11 = m11 - o.m11
        val d12 = m12 - o.m12
        val d20 = m20 - o.m20
        val d21 = m21 - o.m21
        val d22 = m22 - o.m22
        return d00 * d00 + d01 * d01 + d02 * d02 +
               d10 * d10 + d11 * d11 + d12 * d12 +
               d20 * d20 + d21 * d21 + d22 * d22
    }

    companion object {
        val IDENTITY = Matrix3(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
        )

        fun fromAxisAngle(axis: Vector3, angleRad: Float): Matrix3 {
            val u = axis.normalize()
            val c = cos(angleRad)
            val s = sin(angleRad)
            val t = 1f - c
            return Matrix3(
                c + u.x * u.x * t,        u.x * u.y * t - u.z * s,  u.x * u.z * t + u.y * s,
                u.y * u.x * t + u.z * s,  c + u.y * u.y * t,        u.y * u.z * t - u.x * s,
                u.z * u.x * t - u.y * s,  u.z * u.y * t + u.x * s,  c + u.z * u.z * t
            )
        }
    }
}

/**
 * Visual source representing the continuous 32-stellation icosahedral manifold.
 *
 * Every frame, generates the 60 H3 normal vectors corresponding to the icosahedral orbit
 * of a generator vector slerped between pole3 (3-fold face) and pole5 (5-fold face),
 * and uploads them to the shader uniform `vec3 uH3Normals[60]`.
 */
class Icosahedron(
    id: String,
    displayName: String,
    shader: Shader,
    parameters: LinkedHashMap<String, ModulatableParameter>,
    globalAlpha: ModulatableParameter = ModulatableParameter(1.0f),
    hasFeedback: Boolean = false,
    ownsShader: Boolean = false
) : DynamicVisualSource(id, displayName, shader, parameters, globalAlpha = globalAlpha, hasFeedback = hasFeedback, ownsShader = ownsShader) {

    // Pre-allocated array to store the 60 flattened vec3 normals (60 * 3 = 180 floats)
    // without runtime allocations inside the frame rendering loop.
    private val h3NormalsBuffer = FloatArray(180)

    override fun setupUniforms(shader: Shader) {
        super.setupUniforms(shader)
        val controlY = parameters["Control Y"]?.value ?: 0.0f
        updateAndUploadH3Normals(shader, controlY, h3NormalsBuffer)
        shader.setUniform("uSupportH", 0.82f)
    }

    override fun clone(): Icosahedron {
        val clonedParams = LinkedHashMap<String, ModulatableParameter>()
        this.parameters.forEach { (name, param) ->
            clonedParams[name] = param.clone()
        }
        return Icosahedron(
            id = this.id,
            displayName = this.displayName,
            shader = this.shader,
            parameters = clonedParams,
            globalAlpha = this.globalAlpha.clone(),
            hasFeedback = this.hasFeedback,
            ownsShader = false
        )
    }

    companion object {
        const val PHI: Float = 1.6180339887f

        // Two Poles:
        // pole3 (3-fold Icosahedron face): normalize(vec3(1, 1, 1))
        val pole3: Vector3 = Vector3(1f, 1f, 1f).normalize()

        // pole5 (5-fold Dodecahedron face): normalize(vec3(0, 1, phi))
        val pole5: Vector3 = Vector3(0f, 1f, PHI).normalize()

        /**
         * The 60 chiral icosahedral rotation matrices, cached at startup.
         */
        val rotationMatrices: List<Matrix3> by lazy {
            generateIcosahedralRotations()
        }

        /**
         * Spherical linear interpolation between two unit vectors.
         */
        fun slerp(p0: Vector3, p1: Vector3, t: Float): Vector3 {
            val clampedT = t.coerceIn(0f, 1f)
            val dot = p0.dot(p1).coerceIn(-1f, 1f)
            val theta = acos(dot)
            val sinTheta = sin(theta)
            if (abs(sinTheta) < 1e-5f) {
                return lerp(p0, p1, clampedT)
            }
            val w0 = sin((1f - clampedT) * theta) / sinTheta
            val w1 = sin(clampedT * theta) / sinTheta
            return (p0 * w0 + p1 * w1).normalize()
        }

        /**
         * Normalized linear interpolation between two vectors.
         */
        fun lerp(p0: Vector3, p1: Vector3, t: Float): Vector3 {
            val clampedT = t.coerceIn(0f, 1f)
            return (p0 * (1f - clampedT) + p1 * clampedT).normalize()
        }

        /**
         * Generates the single generator vector from uControlY (0.0 to 1.0)
         * interpolated between pole3 and pole5.
         */
        fun generateGeneratorVector(controlY: Float): Vector3 {
            return slerp(pole3, pole5, controlY)
        }

        /**
         * Generates the 60 H3 normal vectors as a List<Vector3>.
         */
        fun generateH3NormalVectors(controlY: Float): List<Vector3> {
            val gen = generateGeneratorVector(controlY)
            val matrices = rotationMatrices
            return matrices.map { m -> (m * gen).normalize() }
        }

        /**
         * Generates the unique H3 normal vectors into the pre-allocated FloatArray.
         * Returns the number of UNIQUE planes (e.g., 20 for Icosahedron, 12 for Dodecahedron).
         */
        fun generateH3Normals(controlY: Float, targetArray: FloatArray = FloatArray(180)): Int {
            require(targetArray.size >= 180) { "Target array must have at least 180 elements" }
            val gen = generateGeneratorVector(controlY)
            val matrices = rotationMatrices
            
            var uniqueCount = 0
            
            for (i in 0 until 60) {
                val m = matrices[i]
                val vx = m.m00 * gen.x + m.m01 * gen.y + m.m02 * gen.z
                val vy = m.m10 * gen.x + m.m11 * gen.y + m.m12 * gen.z
                val vz = m.m20 * gen.x + m.m21 * gen.y + m.m22 * gen.z
                
                val len = sqrt(vx * vx + vy * vy + vz * vz)
                val invLen = if (len > 1e-7f) 1f / len else 1f
                val nx = vx * invLen
                val ny = vy * invLen
                val nz = vz * invLen
                
                // CPU Deduplication in-place
                var isDuplicate = false
                for (j in 0 until uniqueCount) {
                    val dx = targetArray[j * 3] - nx
                    val dy = targetArray[j * 3 + 1] - ny
                    val dz = targetArray[j * 3 + 2] - nz
                    if (dx * dx + dy * dy + dz * dz < 1e-9f) {
                        isDuplicate = true
                        break
                    }
                }
                
                if (!isDuplicate) {
                    targetArray[uniqueCount * 3] = nx
                    targetArray[uniqueCount * 3 + 1] = ny
                    targetArray[uniqueCount * 3 + 2] = nz
                    uniqueCount++
                }
            }
            
            var offset = uniqueCount * 3
            // Zero out remaining array slots just to be safe
            while (offset < 180) {
                targetArray[offset++] = 0f
            }
            
            return uniqueCount
        }

        /**
         * Computes the normals, uploads them, and uploads the plane count.
         */
        fun updateAndUploadH3Normals(shader: Shader, controlY: Float, targetArray: FloatArray = FloatArray(180)) {
            val planeCount = generateH3Normals(controlY, targetArray)
            shader.setUniform3fv("uH3Normals", targetArray)
            shader.setUniform("uPlaneCount", planeCount) // NEW: Send the count!
        }

        /**
         * Generates the 60 icosahedral rotation matrices using BFS from 5-fold and 3-fold generators.
         */
        private fun generateIcosahedralRotations(): List<Matrix3> {
            val angle5 = (2.0 * PI / 5.0).toFloat()
            val angle3 = (2.0 * PI / 3.0).toFloat()

            // 5-fold rotation axes: (0, ±1, ±phi), (±phi, 0, ±1), (±1, ±phi, 0)
            val axes5 = listOf(
                Vector3(0f, 1f, PHI).normalize(),
                Vector3(0f, -1f, PHI).normalize(),
                Vector3(PHI, 0f, 1f).normalize(),
                Vector3(-PHI, 0f, 1f).normalize(),
                Vector3(1f, PHI, 0f).normalize(),
                Vector3(-1f, PHI, 0f).normalize()
            )

            // 3-fold rotation axes: (±1, ±1, ±1)
            val axes3 = listOf(
                Vector3(1f, 1f, 1f).normalize(),
                Vector3(-1f, 1f, 1f).normalize(),
                Vector3(1f, -1f, 1f).normalize(),
                Vector3(1f, 1f, -1f).normalize()
            )

            val baseGenerators = mutableListOf<Matrix3>()
            for (axis in axes5) {
                baseGenerators.add(Matrix3.fromAxisAngle(axis, angle5))
                baseGenerators.add(Matrix3.fromAxisAngle(axis, -angle5))
            }
            for (axis in axes3) {
                baseGenerators.add(Matrix3.fromAxisAngle(axis, angle3))
                baseGenerators.add(Matrix3.fromAxisAngle(axis, -angle3))
            }

            val result = mutableListOf<Matrix3>()
            result.add(Matrix3.IDENTITY)

            val queue = ArrayDeque<Matrix3>()
            queue.add(Matrix3.IDENTITY)

            fun isNewMatrix(m: Matrix3): Boolean {
                for (existing in result) {
                    if (existing.distanceSquared(m) < 1e-4f) {
                        return false
                    }
                }
                return true
            }

            while (queue.isNotEmpty() && result.size < 60) {
                val current = queue.removeFirst()
                for (gen in baseGenerators) {
                    val next = current * gen
                    if (isNewMatrix(next)) {
                        result.add(next)
                        queue.add(next)
                        if (result.size == 60) break
                    }
                }
            }

            check(result.size == 60) { "Failed to generate all 60 icosahedral rotations (got ${result.size})" }
            return result
        }
    }
}

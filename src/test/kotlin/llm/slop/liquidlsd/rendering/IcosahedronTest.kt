package llm.slop.liquidlsd.rendering

import kotlin.math.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IcosahedronTest {

    @Test
    fun testPolesAreNormalized() {
        val p3 = Icosahedron.pole3
        val p5 = Icosahedron.pole5

        assertEquals(1.0f, p3.length(), 1e-5f, "pole3 must be normalized")
        assertEquals(1.0f, p5.length(), 1e-5f, "pole5 must be normalized")

        // pole3 should be (1, 1, 1) / sqrt(3)
        val invSqrt3 = (1.0 / sqrt(3.0)).toFloat()
        assertEquals(invSqrt3, p3.x, 1e-5f)
        assertEquals(invSqrt3, p3.y, 1e-5f)
        assertEquals(invSqrt3, p3.z, 1e-5f)

        // pole5 x should be 0.0
        assertEquals(0f, p5.x, 1e-5f)
        // ratio between p5.z and p5.y should be PHI / 1 = PHI
        assertEquals(Icosahedron.PHI, p5.z / p5.y, 1e-4f)
    }

    @Test
    fun test60IcosahedralRotations() {
        val matrices = Icosahedron.rotationMatrices
        assertEquals(60, matrices.size, "Should have exactly 60 icosahedral rotation matrices")

        for (i in matrices.indices) {
            val m = matrices[i]
            // Determinant of proper rotations must be +1
            assertEquals(1.0f, m.determinant(), 1e-4f, "Matrix $i determinant should be +1.0")

            // Orthogonality: M * M^T = I
            val row0Len = sqrt(m.m00 * m.m00 + m.m01 * m.m01 + m.m02 * m.m02)
            val row1Len = sqrt(m.m10 * m.m10 + m.m11 * m.m11 + m.m12 * m.m12)
            val row2Len = sqrt(m.m20 * m.m20 + m.m21 * m.m21 + m.m22 * m.m22)
            assertEquals(1.0f, row0Len, 1e-4f, "Row 0 of matrix $i should be unit length")
            assertEquals(1.0f, row1Len, 1e-4f, "Row 1 of matrix $i should be unit length")
            assertEquals(1.0f, row2Len, 1e-4f, "Row 2 of matrix $i should be unit length")

            // Check pairwise distinctness
            for (j in i + 1 until matrices.size) {
                val distSq = m.distanceSquared(matrices[j])
                assertTrue(distSq > 1e-3f, "Matrices $i and $j must be distinct (distSq=$distSq)")
            }
        }
    }

    @Test
    fun testGeneratorSlerp() {
        val g0 = Icosahedron.generateGeneratorVector(0.0f)
        assertEquals(Icosahedron.pole3.x, g0.x, 1e-5f)
        assertEquals(Icosahedron.pole3.y, g0.y, 1e-5f)
        assertEquals(Icosahedron.pole3.z, g0.z, 1e-5f)

        val g1 = Icosahedron.generateGeneratorVector(1.0f)
        assertEquals(Icosahedron.pole5.x, g1.x, 1e-5f)
        assertEquals(Icosahedron.pole5.y, g1.y, 1e-5f)
        assertEquals(Icosahedron.pole5.z, g1.z, 1e-5f)

        val gMid = Icosahedron.generateGeneratorVector(0.5f)
        assertEquals(1.0f, gMid.length(), 1e-5f, "Midpoint generator must be normalized")
    }

    @Test
    fun testH3NormalsGeneration() {
        val buffer = FloatArray(180)
        val resultCount = Icosahedron.generateH3Normals(0.42f, buffer)
        assertEquals(60, resultCount)

        for (i in 0 until 60) {
            val x = buffer[i * 3 + 0]
            val y = buffer[i * 3 + 1]
            val z = buffer[i * 3 + 2]
            val len = sqrt(x * x + y * y + z * z)
            assertEquals(1.0f, len, 1e-4f, "Normal vector $i should be unit length")
        }

        val vectorList = Icosahedron.generateH3NormalVectors(0.42f)
        assertEquals(60, vectorList.size)
        for (i in 0 until 60) {
            assertEquals(buffer[i * 3 + 0], vectorList[i].x, 1e-5f)
            assertEquals(buffer[i * 3 + 1], vectorList[i].y, 1e-5f)
            assertEquals(buffer[i * 3 + 2], vectorList[i].z, 1e-5f)
        }
    }

    @Test
    fun testGroupClosure() {
        val matrices = Icosahedron.rotationMatrices
        for (a in matrices) {
            for (b in matrices) {
                val prod = a * b
                val found = matrices.any { it.distanceSquared(prod) < 1e-3f }
                assertTrue(found, "Product of group elements must be in the group")
            }
        }
    }

    @Test
    fun testIcosahedralOrbitPole3Produces20FacesWithMultiplicity3() {
        val vectors = Icosahedron.generateH3NormalVectors(0.0f)
        assertEquals(60, vectors.size)

        // Cluster vectors that are within 1e-3 of each other
        val clusters = mutableListOf<MutableList<Vector3>>()
        for (v in vectors) {
            val cluster = clusters.find { c -> (c[0] - v).length() < 1e-3f }
            if (cluster != null) {
                cluster.add(v)
            } else {
                clusters.add(mutableListOf(v))
            }
        }

        assertEquals(20, clusters.size, "Pole 3 orbit must have exactly 20 unique face normals (Icosahedron)")
        for (c in clusters) {
            assertEquals(3, c.size, "Each icosahedron face normal must appear exactly 3 times (3-fold symmetry)")
        }
    }

    @Test
    fun testIcosahedralOrbitPole5Produces12FacesWithMultiplicity5() {
        val vectors = Icosahedron.generateH3NormalVectors(1.0f)
        assertEquals(60, vectors.size)

        val clusters = mutableListOf<MutableList<Vector3>>()
        for (v in vectors) {
            val cluster = clusters.find { c -> (c[0] - v).length() < 1e-3f }
            if (cluster != null) {
                cluster.add(v)
            } else {
                clusters.add(mutableListOf(v))
            }
        }

        assertEquals(12, clusters.size, "Pole 5 orbit must have exactly 12 unique face normals (Dodecahedron)")
        for (c in clusters) {
            assertEquals(5, c.size, "Each dodecahedron face normal must appear exactly 5 times (5-fold symmetry)")
        }
    }

    @Test
    fun testIcosahedralOrbitIntermediateProduces60DistinctFaces() {
        val vectors = Icosahedron.generateH3NormalVectors(0.5f)
        assertEquals(60, vectors.size)

        val clusters = mutableListOf<MutableList<Vector3>>()
        for (v in vectors) {
            val cluster = clusters.find { c -> (c[0] - v).length() < 1e-3f }
            if (cluster != null) {
                cluster.add(v)
            } else {
                clusters.add(mutableListOf(v))
            }
        }

        assertEquals(60, clusters.size, "Intermediate generator orbit must produce 60 distinct face normals")
    }
}


package llm.slop.liquidlsd.rendering

import kotlin.math.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HyperMeshTest {

    @Test
    fun test600CellTopology() {
        val geo = HyperMesh.Geometry600Cell
        assertEquals(120, geo.vertices.size, "600-cell must have exactly 120 vertices")
        assertEquals(720, geo.edges.size, "600-cell must have exactly 720 edges")
        assertEquals(120, geo.hopfCoords.size, "Hopf coords must exist for all 120 vertices")

        // Test that all vertices lie on S³ (unit 4-sphere)
        for (i in geo.vertices.indices) {
            val v = geo.vertices[i]
            assertEquals(1.0f, v.length(), 1e-4f, "Vertex $i must be unit length on S³")
        }

        // Test edge length / dot product consistency
        val targetDot = HyperMesh.PHI / 2f // cos(pi/5) ≈ 0.809017
        for ((a, b) in geo.edges) {
            val dot = geo.vertices[a].dot(geo.vertices[b])
            assertEquals(targetDot, dot, 0.05f, "Edge between $a and $b must have dot product close to phi/2")
        }
    }

    @Test
    fun test120CellTopology() {
        val geo = HyperMesh.Geometry120Cell
        assertEquals(600, geo.vertices.size, "120-cell must have exactly 600 vertices")
        assertEquals(1200, geo.edges.size, "120-cell must have exactly 1200 edges")
        assertEquals(600, geo.hopfCoords.size, "Hopf coords must exist for all 600 vertices")

        // Test that all vertices lie on S³ (unit 4-sphere)
        for (i in geo.vertices.indices) {
            val v = geo.vertices[i]
            assertEquals(1.0f, v.length(), 1e-4f, "Vertex $i must be unit length on S³")
        }

        // Test edge length / dot product consistency for 120-cell
        val targetDot = 0.9510565f
        for ((a, b) in geo.edges) {
            val dot = geo.vertices[a].dot(geo.vertices[b])
            assertEquals(targetDot, dot, 0.03f, "Edge between $a and $b must have dot product close to 0.951")
        }
    }

    @Test
    fun testHopfFibrationCoordinates() {
        val v1 = Vector4(1f, 0f, 0f, 0f)
        val hopf1 = HyperMesh.computeHopfCoords(v1)
        assertTrue(hopf1.first in 0f..1f, "Hopf theta must be in [0, 1]")
        assertTrue(hopf1.second in 0f..1f, "Hopf phi must be in [0, 1]")

        val v2 = Vector4(0f, 1f, 0f, 0f)
        val hopf2 = HyperMesh.computeHopfCoords(v2)
        assertTrue(hopf2.first in 0f..1f, "Hopf theta must be in [0, 1]")
        assertTrue(hopf2.second in 0f..1f, "Hopf phi must be in [0, 1]")
    }
}

package llm.slop.liquidlsd.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemVerTest {

    @Test
    fun testParseStandardVersions() {
        val v1 = SemVer.parseOrNull("1.0.0")
        assertNotNull(v1)
        assertEquals(1, v1.major)
        assertEquals(0, v1.minor)
        assertEquals(0, v1.patch)
        assertNull(v1.preRelease)

        val v2 = SemVer.parseOrNull("v2.4.11")
        assertNotNull(v2)
        assertEquals(2, v2.major)
        assertEquals(4, v2.minor)
        assertEquals(11, v2.patch)
        assertNull(v2.preRelease)
    }

    @Test
    fun testParsePreReleaseVersions() {
        val beta = SemVer.parseOrNull("v1.0.0-beta.41")
        assertNotNull(beta)
        assertEquals(1, beta.major)
        assertEquals(0, beta.minor)
        assertEquals(0, beta.patch)
        assertEquals("beta.41", beta.preRelease)
        assertTrue(beta.isPreRelease)

        val snapshot = SemVer.parseOrNull("1.0.0-SNAPSHOT")
        assertNotNull(snapshot)
        assertEquals("SNAPSHOT", snapshot.preRelease)
        assertTrue(snapshot.isSnapshot)
    }

    @Test
    fun testPrecedenceMajorMinorPatch() {
        assertTrue(SemVer.parse("2.0.0") > SemVer.parse("1.9.9"))
        assertTrue(SemVer.parse("1.1.0") > SemVer.parse("1.0.9"))
        assertTrue(SemVer.parse("1.0.1") > SemVer.parse("1.0.0"))
        assertEquals(0, SemVer.parse("v1.0.0").compareTo(SemVer.parse("1.0.0")))
    }

    @Test
    fun testPrecedenceBetaSequences() {
        val beta41 = SemVer.parse("v1.0.0-beta.41")
        val beta42 = SemVer.parse("v1.0.0-beta.42")
        val rc1 = SemVer.parse("v1.0.0-rc.1")
        val release = SemVer.parse("v1.0.0")

        // beta.42 > beta.41
        assertTrue(beta42 > beta41)
        assertTrue(beta41 < beta42)

        // rc.1 > beta.42
        assertTrue(rc1 > beta42)

        // Normal release > pre-release of same core version
        assertTrue(release > rc1)
        assertTrue(release > beta42)
        assertTrue(release > beta41)
    }

    @Test
    fun testSnapshotComparison() {
        val snapshot = SemVer.parse("1.0.0-SNAPSHOT")
        val beta41 = SemVer.parse("1.0.0-beta.41")
        val release = SemVer.parse("1.0.0")

        assertTrue(release > snapshot)
        // Numeric pre-release vs SNAPSHOT
        assertTrue(release > beta41)
    }
}

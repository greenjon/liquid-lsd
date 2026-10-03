package llm.slop.liquidlsd.control

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ControllerProfileSchemaVersionTest {
    private fun dir() = createTempDirectory("controllers").toFile()

    @Test
    fun missingVersionLoadsAsOneWithoutWarning() {
        val d = dir()
        File(d, "x.json").writeText("""{"id":"x","name":"X"}""")
        val s = ControllerProfileStore(d, emptyList())
        assertEquals(1, s.get("x")?.profile?.version)
        assertTrue(s.warnings().isEmpty())
    }

    @Test
    fun newerVersionLoadsWithWarningAndIsNotOverwritten() {
        val d = dir()
        val text = """{"version":99,"id":"x","name":"X","futureField":1}"""
        val file = File(d, "x.json").also { it.writeText(text) }
        val s = ControllerProfileStore(d, emptyList())
        val profile = s.get("x")?.profile
        assertNotNull(profile)
        assertEquals(1, s.warnings().size)
        assertTrue(s.saveUser(profile.copy(name = "CHANGED")).isNotEmpty())
        assertEquals(text, file.readText())
    }

    @Test
    fun writtenFilesCarryCurrentVersionAndRoundTrip() {
        val d = dir()
        val s = ControllerProfileStore(d, emptyList())
        val profile = ControllerProfile(id = "x", name = "X")
        assertTrue(s.saveUser(profile).isEmpty())
        assertTrue(File(d, "x.json").readText().contains("\"version\": ${ControllerProfile.CURRENT_SCHEMA_VERSION}"))
        assertEquals(profile, s.get("x")?.profile)
    }

    @Test
    fun builtInsAllCarryVersion() {
        val names = ControllerProfileStore.BUILT_IN_NAMES
        for (name in names) {
            val text = ControllerProfileStore::class.java.getResourceAsStream("/controllers/$name.json")!!.bufferedReader().readText()
            assertTrue(Regex("\"version\"\\s*:\\s*${ControllerProfile.CURRENT_SCHEMA_VERSION}\\b").containsMatchIn(text), "$name lacks version")
        }
        val onDisk = File("src/main/resources/controllers").listFiles()!!.map { it.nameWithoutExtension }.toSet()
        assertEquals(onDisk, names.toSet())
    }
}

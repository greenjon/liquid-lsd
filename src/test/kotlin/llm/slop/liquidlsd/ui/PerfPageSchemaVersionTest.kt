package llm.slop.liquidlsd.ui

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PerfPageSchemaVersionTest {
    private val rows = """[{"row":"master.mix"},{"row":"trans"},{"row":"wetdry"},{"row":"global"}]"""

    @Test
    fun missingVersionLoadsAsOneWithoutWarning() {
        val dir = createTempDirectory().toFile()
        File(dir, "p.json").writeText("""{"id":"mine","name":"MINE","rows":$rows}""")
        val s = PerfPageStore(dir)
        assertEquals(1, s.get("mine")?.version)
        assertTrue(s.warnings().isEmpty())
    }

    @Test
    fun newerVersionLoadsWithWarningAndIsNotOverwritten() {
        val dir = createTempDirectory().toFile()
        val text = """{"version":99,"id":"mine","name":"MINE","futureField":true,"rows":$rows}"""
        val file = File(dir, "p.json").also { it.writeText(text) }
        val s = PerfPageStore(dir)
        assertNotNull(s.get("mine"))
        assertEquals(1, s.warnings().size)
        assertEquals("p.json", s.warnings()[0].file.name)
        val problems = s.saveUser(s.get("mine")!!.copy(name = "CHANGED"))
        assertTrue(problems.isNotEmpty())
        assertEquals(text, file.readText())
        assertTrue(s.deleteUser("mine"))
    }

    @Test
    fun writtenFilesCarryCurrentVersionAndRoundTrip() {
        val dir = createTempDirectory().toFile()
        val s = PerfPageStore(dir)
        val page = PerfPageDef("mine", "MINE", rows = List(4) { RowPlacement("master.mix") })
        assertTrue(s.saveUser(page).isEmpty())
        assertTrue(File(dir, "mine.json").readText().contains("\"version\": ${PerfPageDef.CURRENT_SCHEMA_VERSION}"))
        assertEquals(page, s.get("mine"))
    }

    @Test
    fun builtInsAllCarryVersion() {
        for (name in PerfPageStore.BUILT_IN_NAMES) {
            val text = PerfPageStore::class.java.getResourceAsStream("/perform_pages/$name.json")!!.bufferedReader().readText()
            assertTrue(Regex("\"version\"\\s*:\\s*${PerfPageDef.CURRENT_SCHEMA_VERSION}\\b").containsMatchIn(text), "$name lacks version")
        }
        val dirNames = File("src/main/resources/perform_pages").listFiles()!!.map { it.nameWithoutExtension }.toSet()
        assertEquals(dirNames, PerfPageStore.BUILT_IN_NAMES.toSet())
    }
}

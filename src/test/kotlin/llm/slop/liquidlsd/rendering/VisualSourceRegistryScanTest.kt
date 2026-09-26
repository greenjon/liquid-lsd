package llm.slop.liquidlsd.rendering

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class VisualSourceRegistryScanTest {

    @Test
    fun standaloneScanSkipsMetaJsonSourceFolders() {
        val root = createTempDirectory("lsd_scan").toFile()
        try {
            File(root, "mandala").apply { mkdirs() }.let {
                File(it, "meta.json").writeText("{}")
                File(it, "shader.frag").writeText("")
                File(it, "nested").mkdirs()
                File(it, "nested/inner.fs").writeText("")
            }
            File(root, "plasma").apply { mkdirs() }.let { File(it, "plasma.fs").writeText("") }
            File(root, "top.fs").writeText("")

            val names = VisualSourceRegistry.standaloneShaderFiles(root).map { it.name }.sorted()
            assertEquals(listOf("plasma.fs", "top.fs"), names)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun bundledMandalaShaderIsNotAStandaloneSource() {
        val files = VisualSourceRegistry.standaloneShaderFiles(File("library/sources"))
        assertEquals(emptyList(), files.filter { it.parentFile.name == "mandala" })
    }
}

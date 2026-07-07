package llm.slop.spirals.midi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import kotlin.io.path.createTempDirectory

class MidiMappingManagerTest {

    @Test
    fun testSanitizeMidiProfileNameRemovesPathTraversal() {
        assertEquals("external_profile", sanitizeMidiProfileName("../external/profile"))
        assertEquals("default", sanitizeMidiProfileName(" ../ "))
        assertEquals("Live_Set_01", sanitizeMidiProfileName("Live Set 01"))
    }

    @Test
    fun testMidiProfileFileStaysUnderMidiDirectory() {
        val midiDir = createTempDirectory().toFile()
        val file = midiProfileFile(midiDir, "../outside")

        assertEquals("outside.json", file.name)
        assertTrue(file.canonicalPath.startsWith(midiDir.canonicalPath + File.separator))
    }
}

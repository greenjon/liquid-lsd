package llm.slop.liquidlsd.midi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.io.File
import kotlin.io.path.createTempDirectory

class MidiMappingManagerTest {

    @Test
    fun testSanitiseProfileNameRejectsPathTraversal() {
        assertFailsWith<IllegalArgumentException> { sanitiseProfileName("../external/profile") }
        assertFailsWith<IllegalArgumentException> { sanitiseProfileName(" ../ ") }
        assertFailsWith<IllegalArgumentException> { sanitiseProfileName("Live Set 01") }
        assertEquals("Live_Set_01", sanitiseProfileName("Live_Set_01"))
    }

    @Test
    fun testMidiProfileFileStaysUnderMidiDirectory() {
        val midiDir = createTempDirectory().toFile()
        // Wait, midiProfileFile now throws if it's invalid so this test might need adjustment
        val throws = runCatching { midiProfileFile(midiDir, "../outside") }.isFailure
        assertTrue(throws)
    }

    @Test
    fun testLoadSettingsAppliesMidiProfile() {
        val tempProps = File("lsd-settings.properties")
        try {
            tempProps.writeText("activeMidiProfile=test_profile\n")
            // Use reflection to call private loadSettings if it is private
            val method = llm.slop.liquidlsd.ui.UITheme::class.java.getDeclaredMethod("loadSettings")
            method.isAccessible = true
            method.invoke(llm.slop.liquidlsd.ui.UITheme)

            MidiMappingManager.loadProfile(llm.slop.liquidlsd.ui.UITheme.activeMidiProfile)

            assertEquals("test_profile", MidiMappingManager.activeProfileName)
        } finally {
            tempProps.delete()
        }
    }
}

package llm.slop.liquidlsd.presets

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.models.SessionStateDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.io.File
import kotlin.io.path.createTempDirectory

class SessionStateTest {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testSessionStateDtoSerialization() {
        val dummyParam = ParameterDto(
            baseValue = 0.5f,
            baseMin = 0.0f,
            baseMax = 1.0f,
            randomizeBase = false,
            modulators = emptyList()
        )
        val dummyDeck = DeckPresetDto(
            name = "Deck A",
            visualSourceType = "mandala",
            parameters = emptyMap(),
            feedbackParameters = emptyMap(),
            globalAlpha = dummyParam,
            isEmpty = false
        )
        val session = SessionStateDto(
            version = 5,
            deckA = dummyDeck,
            deckB = dummyDeck.copy(name = "Deck B"),
            deckBG = dummyDeck.copy(name = "Deck BG", isEmpty = true),
            deckPV = dummyDeck.copy(name = "Deck PV", isEmpty = true),
            deckC = dummyDeck.copy(name = "Deck C", isEmpty = true),
            crossfade = dummyParam,
            masterAlpha = dummyParam,
            blendMode = 4.0f,
            queue = listOf("presets/test.lsd"),
            activeIndex = 0,
            isAutoVJEnabled = true,
            bgQueue = listOf("presets/bg.lsd"),
            bgActiveIndex = 0,
            isAutoBGEnabled = true,
            bloom = dummyParam,
            xfadeSpeed = dummyParam,
            queueNext = dummyParam,
            queuePrev = dummyParam,
            isRepeatEnabled = true,
            isShuffleEnabled = true
        )

        val jsonStr = json.encodeToString(session)
        val decoded = json.decodeFromString<SessionStateDto>(jsonStr)
        assertEquals(5, decoded.version)
        assertEquals("Deck A", decoded.deckA.name)
        assertEquals("Deck B", decoded.deckB.name)
        assertNotNull(decoded.deckBG)
        assertNotNull(decoded.deckPV)
        assertEquals("Deck BG", decoded.deckBG?.name)
        assertEquals("Deck PV", decoded.deckPV?.name)
        assertTrue(decoded.deckBG?.isEmpty == true)
        assertTrue(decoded.deckPV?.isEmpty == true)
        assertNotNull(decoded.bloom)
        assertEquals(0.5f, decoded.bloom?.baseValue)
        assertNotNull(decoded.xfadeSpeed)
        assertEquals(0.5f, decoded.xfadeSpeed?.baseValue)
        assertNotNull(decoded.queueNext)
        assertEquals(0.5f, decoded.queueNext?.baseValue)
        assertNotNull(decoded.queuePrev)
        assertEquals(0.5f, decoded.queuePrev?.baseValue)
        assertTrue(decoded.isRepeatEnabled)
        assertTrue(decoded.isShuffleEnabled)
        assertTrue(decoded.isAutoBGEnabled)
        assertEquals(listOf("presets/bg.lsd"), decoded.bgQueue)
    }

    @Test
    fun testRestoredQueueRebasesActiveIndexAfterFilteringMissingFiles() {
        val tempDir = createTempDirectory().toFile()
        val activeFile = File(tempDir, "active.lsd").apply { writeText("{}") }
        val nextFile = File(tempDir, "next.lsd").apply { writeText("{}") }
        val missingFile = File(tempDir, "missing.lsd")

        val restored = PresetManager.resolveRestoredQueue(
            listOf(missingFile.absolutePath, activeFile.absolutePath, nextFile.absolutePath),
            savedActiveIndex = 1
        )

        assertEquals(listOf(activeFile.absoluteFile, nextFile.absoluteFile), restored.files.map { it.absoluteFile })
        assertEquals(0, restored.activeIndex)
    }

    @Test
    fun testRestoredQueueMovesToNextSurvivingItemWhenActiveFileIsMissing() {
        val tempDir = createTempDirectory().toFile()
        val previousFile = File(tempDir, "previous.lsd").apply { writeText("{}") }
        val nextFile = File(tempDir, "next.lsd").apply { writeText("{}") }
        val missingActiveFile = File(tempDir, "active.lsd")

        val restored = PresetManager.resolveRestoredQueue(
            listOf(previousFile.absolutePath, missingActiveFile.absolutePath, nextFile.absolutePath),
            savedActiveIndex = 1
        )

        assertEquals(listOf(previousFile.absoluteFile, nextFile.absoluteFile), restored.files.map { it.absoluteFile })
        assertEquals(1, restored.activeIndex)
    }

    @Test
    fun testSessionPathSerialization() {
        val root = File("library").absoluteFile
        val presetFile = File(root, "presets/MyPreset.lsd")
        
        val serialized = PresetManager.serializeSessionPath(presetFile)
        assertEquals("presets/MyPreset.lsd", serialized)
        
        val outsideFile = File("/tmp/some_other_place.lsd")
        val serializedOutside = PresetManager.serializeSessionPath(outsideFile)
        assertEquals(outsideFile.absolutePath, serializedOutside)
    }

    @Test
    fun testSessionPathResolution() {
        val root = File("library").absoluteFile
        val presetFile = File(root, "presets/MyPreset.lsd")
        presetFile.parentFile.mkdirs()
        presetFile.writeText("{}")
        
        val resolved = PresetManager.resolveSessionPath("presets/MyPreset.lsd")
        assertEquals(presetFile.absoluteFile, resolved?.absoluteFile)
        
        val outsideFile = File.createTempFile("outside", ".lsd")
        outsideFile.writeText("{}")
        
        val resolvedOutside = PresetManager.resolveSessionPath(outsideFile.absolutePath)
        assertEquals(outsideFile.absoluteFile, resolvedOutside?.absoluteFile)
        
        val missing = PresetManager.resolveSessionPath("missing/Preset.lsd")
        assertNull(missing)
        
        presetFile.delete()
        outsideFile.delete()
    }
    
    @Test
    fun testRestoredQueueUnresolvedItems() {
        val root = File("library").absoluteFile
        val presetFile = File(root, "presets/MyPreset.lsd")
        presetFile.parentFile.mkdirs()
        presetFile.writeText("{}")
        
        PresetManager.resolveRestoredQueue(
            listOf("presets/MyPreset.lsd", "presets/MissingPreset.lsd"),
            savedActiveIndex = 0
        )
        
        val unresolved = PresetManager.sessionState.unresolvedItems
        assertEquals(listOf("presets/MissingPreset.lsd"), unresolved)
        
        presetFile.delete()
    }
}

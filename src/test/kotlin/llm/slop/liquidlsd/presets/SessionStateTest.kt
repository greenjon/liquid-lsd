package llm.slop.liquidlsd.presets

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroBinding
import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.macro.MacroTargetType
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.MixerDto
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
        val mixerDto = MixerDto(
            crossfade = dummyParam,
            masterAlpha = dummyParam,
            blendMode = 4.0f,
            bloom = dummyParam,
            xfadeSpeed = dummyParam,
            queueNext = dummyParam,
            queuePrev = dummyParam,
            bgQueueNext = dummyParam,
            bgQueuePrev = dummyParam,
            tapTempo = dummyParam,
            levelA = 0.8f,
            levelB = 0.6f,
            levelBG = 0.4f,
            levelPV = 0.2f,
            masterLevel = 0.9f
        )
        val session = SessionStateDto(
            version = 6,
            deckA = dummyDeck,
            deckB = dummyDeck.copy(name = "Deck B"),
            deckBG = dummyDeck.copy(name = "Deck BG", isEmpty = true),
            deckPV = dummyDeck.copy(name = "Deck PV", isEmpty = true),
            mixer = mixerDto,
            queue = listOf("presets/test.lsd"),
            activeIndex = 0,
            isAutoVJEnabled = true,
            bgQueue = listOf("presets/bg.lsd"),
            bgActiveIndex = 0,
            isAutoBGEnabled = true,
            isRepeatEnabled = true,
            isShuffleEnabled = true
        )

        val jsonStr = json.encodeToString(session)
        val decoded = json.decodeFromString<SessionStateDto>(jsonStr)
        assertEquals(6, decoded.version)
        assertEquals("Deck A", decoded.deckA.name)
        assertEquals("Deck B", decoded.deckB.name)
        assertNotNull(decoded.deckBG)
        assertNotNull(decoded.deckPV)
        assertEquals("Deck BG", decoded.deckBG.name)
        assertEquals("Deck PV", decoded.deckPV.name)
        assertTrue(decoded.deckBG.isEmpty)
        assertTrue(decoded.deckPV.isEmpty)
        assertNotNull(decoded.mixer.bloom)
        assertEquals(0.5f, decoded.mixer.bloom?.baseValue)
        assertNotNull(decoded.mixer.xfadeSpeed)
        assertEquals(0.5f, decoded.mixer.xfadeSpeed?.baseValue)
        assertNotNull(decoded.mixer.queueNext)
        assertEquals(0.5f, decoded.mixer.queueNext?.baseValue)
        assertNotNull(decoded.mixer.queuePrev)
        assertEquals(0.5f, decoded.mixer.queuePrev?.baseValue)
        assertTrue(decoded.isRepeatEnabled)
        assertTrue(decoded.isShuffleEnabled)
        assertTrue(decoded.isAutoBGEnabled)
        assertEquals(listOf("presets/bg.lsd"), decoded.bgQueue)
        assertEquals(0.8f, decoded.mixer.levelA)
        assertEquals(0.6f, decoded.mixer.levelB)
        assertEquals(0.4f, decoded.mixer.levelBG)
        assertEquals(0.2f, decoded.mixer.levelPV)
        assertEquals(0.9f, decoded.mixer.masterLevel)
    }

    @Test
    fun testSessionStateDtoRoundTripsRackUnitMacroBanks() {
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
        val mixerDto = MixerDto(
            crossfade = dummyParam,
            masterAlpha = dummyParam,
            blendMode = 4.0f
        )

        // A per-unit bank with a curated knob binding, exactly as RackUnitMacroCuration would
        // produce for a rack unit's own MacroBank -- keyed by the unit's stable id.
        val curatedKnob = MacroControl(
            label = "ZOOM",
            value = 0.5f,
            bindings = mutableListOf(
                MacroBinding(
                    unitInstanceId = "deckA",
                    parameterId = "viewZoom",
                    targetType = MacroTargetType.PARAM_BASE_VALUE,
                    minVal = 0.2f,
                    maxVal = 3.0f
                )
            )
        )
        val deckAUnitBank = MacroBank(knobs = listOf(curatedKnob) + List(7) { MacroControl(label = "KNOB ${it + 2}") })

        val session = SessionStateDto(
            deckA = dummyDeck,
            deckB = dummyDeck.copy(name = "Deck B"),
            mixer = mixerDto,
            queue = emptyList(),
            activeIndex = -1,
            isAutoVJEnabled = false,
            deckMacroBanks = mapOf("deckA" to deckAUnitBank)
        )

        val jsonStr = json.encodeToString(session)
        val decoded = json.decodeFromString<SessionStateDto>(jsonStr)

        assertEquals(1, decoded.deckMacroBanks.size)
        val restoredBank = decoded.deckMacroBanks["deckA"]
        assertNotNull(restoredBank)
        val restoredBinding = restoredBank.knobs[0].bindings.first()
        assertEquals("deckA", restoredBinding.unitInstanceId)
        assertEquals("viewZoom", restoredBinding.parameterId)
        assertEquals(0.2f, restoredBinding.minVal)
        assertEquals(3.0f, restoredBinding.maxVal)
        assertEquals("ZOOM", restoredBank.knobs[0].label)
    }

    @Test
    fun testSessionStateDtoWithoutDeckMacroBanksFieldDecodesGracefully() {
        // Simulates a session file saved before deckMacroBanks existed: the field is simply
        // absent from the JSON. Must decode without error and default to an empty map so
        // SessionSerializer.loadSession() falls back to an empty MacroBank for every canonical id.
        val legacyJson = """
            {
              "version": 6,
              "deckA": {"name": "Deck A", "visualSourceType": "mandala", "parameters": {}, "feedbackParameters": {}},
              "deckB": {"name": "Deck B", "visualSourceType": "mandala", "parameters": {}, "feedbackParameters": {}},
              "mixer": {
                "crossfade": {"baseValue": 0.0, "baseMin": -1.0, "baseMax": 1.0, "randomizeBase": false, "modulators": []},
                "masterAlpha": {"baseValue": 1.0, "baseMin": 0.0, "baseMax": 1.0, "randomizeBase": false, "modulators": []},
                "blendMode": 0.0
              },
              "queue": [],
              "activeIndex": -1,
              "isAutoVJEnabled": false
            }
        """.trimIndent()

        val decoded = json.decodeFromString<SessionStateDto>(legacyJson)
        assertTrue(decoded.deckMacroBanks.isEmpty())
    }

    @Test
    fun testRestoredQueueRebasesActiveIndexAfterFilteringMissingFiles() {
        val tempDir = createTempDirectory().toFile()
        val activeFile = File(tempDir, "active.lsd").apply { writeText("{}") }
        val nextFile = File(tempDir, "next.lsd").apply { writeText("{}") }
        val missingFile = File(tempDir, "missing.lsd")

        val restored = PresetManager.resolveRestoredQueue(
            listOf(missingFile.absolutePath, activeFile.absolutePath, nextFile.absolutePath),
            activeIndex = 1
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
            activeIndex = 1
        )

        assertEquals(listOf(previousFile.absoluteFile, nextFile.absoluteFile), restored.files.map { it.absoluteFile })
        assertEquals(1, restored.activeIndex)
    }

    @Test
    fun testSessionPathSerialization() {
        val root = File("library").absoluteFile
        val presetFile = File(root, "presets/MyPreset.lsd")
        
        val serialized = PresetManager.serializeSessionPath(presetFile)
        assertEquals("\${LIBRARY}/presets/MyPreset.lsd", serialized)
        
        val outsideFile = File("/tmp/some_other_place.lsd")
        val serializedOutside = PresetManager.serializeSessionPath(outsideFile)
        assertEquals(outsideFile.absolutePath.replace('\\', '/'), serializedOutside)
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
        PresetManager.sessionState = SessionState()
        val root = File("library").absoluteFile
        val presetFile = File(root, "presets/MyPreset.lsd")
        presetFile.parentFile.mkdirs()
        presetFile.writeText("{}")
        
        val result = PresetManager.resolveRestoredQueue(
            listOf("presets/MyPreset.lsd", "presets/MissingPreset.lsd"),
            activeIndex = 0
        )
        PresetManager.sessionState = PresetManager.sessionState.copy(unresolvedItems = result.unresolvedPaths)
        
        val unresolved = PresetManager.sessionState.unresolvedItems
        assertEquals(listOf("presets/MissingPreset.lsd"), unresolved)
        assertEquals(listOf("presets/MissingPreset.lsd"), result.unresolvedPaths)
        
        presetFile.delete()
    }

    @Test
    fun testCombinedQueueUnresolvedItems() {
        val root = File("library").absoluteFile
        val mainFile = File(root, "presets/ExistingMain.lsd").apply {
            parentFile.mkdirs()
            writeText("{}")
        }
        val bgFile = File(root, "presets/ExistingBg.lsd").apply {
            parentFile.mkdirs()
            writeText("{}")
        }

        val resMain = PresetManager.resolveRestoredQueue(
            listOf("presets/ExistingMain.lsd", "presets/MissingMain1.lsd"),
            activeIndex = 0
        )
        val resBg = PresetManager.resolveRestoredQueue(
            listOf("presets/ExistingBg.lsd", "presets/MissingBg1.lsd"),
            activeIndex = 0
        )

        val combined = (resMain.unresolvedPaths + resBg.unresolvedPaths).distinct()
        PresetManager.sessionState = PresetManager.sessionState.copy(unresolvedItems = combined)

        assertEquals(2, PresetManager.sessionState.unresolvedItems.size)
        assertTrue(PresetManager.sessionState.unresolvedItems.contains("presets/MissingMain1.lsd"))
        assertTrue(PresetManager.sessionState.unresolvedItems.contains("presets/MissingBg1.lsd"))

        mainFile.delete()
        bgFile.delete()
    }
}

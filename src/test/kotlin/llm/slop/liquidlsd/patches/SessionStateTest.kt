package llm.slop.liquidlsd.patches

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.DeckPatchDto
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
    fun testSessionStateDtoSerializationAndBackwardCompatibility() {
        // 1. Verify deserializing an older version (version 2) of SessionStateDto lacking the new optional fields
        val oldJsonStr = """
            {
                "version": 2,
                "deckA": {
                    "name": "Deck A",
                    "visualSourceType": "Mandala",
                    "parameters": {},
                    "feedbackParameters": {},
                    "globalAlpha": {
                        "baseValue": 1.0,
                        "baseMin": 1.0,
                        "baseMax": 1.0,
                        "randomizeBase": false,
                        "modulators": []
                    },
                    "isEmpty": false
                },
                "deckB": {
                    "name": "Deck B",
                    "visualSourceType": "Mandala",
                    "parameters": {},
                    "feedbackParameters": {},
                    "globalAlpha": {
                        "baseValue": 1.0,
                        "baseMin": 1.0,
                        "baseMax": 1.0,
                        "randomizeBase": false,
                        "modulators": []
                    },
                    "isEmpty": false
                },
                "deckC": {
                    "name": "Deck C",
                    "visualSourceType": "Mandala",
                    "parameters": {},
                    "feedbackParameters": {},
                    "globalAlpha": {
                        "baseValue": 1.0,
                        "baseMin": 1.0,
                        "baseMax": 1.0,
                        "randomizeBase": false,
                        "modulators": []
                    },
                    "isEmpty": true
                },
                "crossfade": {
                    "baseValue": 0.0,
                    "baseMin": 0.0,
                    "baseMax": 0.0,
                    "randomizeBase": false,
                    "modulators": []
                },
                "masterAlpha": {
                    "baseValue": 1.0,
                    "baseMin": 1.0,
                    "baseMax": 1.0,
                    "randomizeBase": false,
                    "modulators": []
                },
                "blendMode": 4.0,
                "queue": [],
                "activeIndex": -1,
                "isAutoVJEnabled": false
            }
        """.trimIndent()

        val decodedOld = json.decodeFromString<SessionStateDto>(oldJsonStr)
        assertEquals(2, decodedOld.version)
        assertNull(decodedOld.bloom)
        assertNull(decodedOld.xfadeSpeed)
        assertNull(decodedOld.queueNext)
        assertNull(decodedOld.queuePrev)
        assertFalse(decodedOld.isRepeatEnabled)
        assertFalse(decodedOld.isShuffleEnabled)

        // 2. Verify serializing and deserializing a new version (version 4) of SessionStateDto including the new fields
        val dummyParam = ParameterDto(
            baseValue = 0.5f,
            baseMin = 0.0f,
            baseMax = 1.0f,
            randomizeBase = false,
            modulators = emptyList()
        )
        val newSession = SessionStateDto(
            version = 4,
            deckA = decodedOld.deckA,
            deckB = decodedOld.deckB,
            deckC = decodedOld.deckC,
            crossfade = decodedOld.crossfade,
            masterAlpha = decodedOld.masterAlpha,
            blendMode = decodedOld.blendMode,
            queue = emptyList(),
            activeIndex = -1,
            isAutoVJEnabled = true,
            bloom = dummyParam,
            xfadeSpeed = dummyParam,
            queueNext = dummyParam,
            queuePrev = dummyParam,
            isRepeatEnabled = true,
            isShuffleEnabled = true
        )

        val newJsonStr = json.encodeToString(newSession)
        val decodedNew = json.decodeFromString<SessionStateDto>(newJsonStr)
        assertEquals(4, decodedNew.version)
        assertNotNull(decodedNew.bloom)
        assertEquals(0.5f, decodedNew.bloom?.baseValue)
        assertNotNull(decodedNew.xfadeSpeed)
        assertEquals(0.5f, decodedNew.xfadeSpeed?.baseValue)
        assertNotNull(decodedNew.queueNext)
        assertEquals(0.5f, decodedNew.queueNext?.baseValue)
        assertNotNull(decodedNew.queuePrev)
        assertEquals(0.5f, decodedNew.queuePrev?.baseValue)
        assertTrue(decodedNew.isRepeatEnabled)
        assertTrue(decodedNew.isShuffleEnabled)
    }

    @Test
    fun testRestoredQueueRebasesActiveIndexAfterFilteringMissingFiles() {
        val tempDir = createTempDirectory().toFile()
        val activeFile = File(tempDir, "active.lsd").apply { writeText("{}") }
        val nextFile = File(tempDir, "next.lsd").apply { writeText("{}") }
        val missingFile = File(tempDir, "missing.lsd")

        val restored = PatchManager.resolveRestoredQueue(
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

        val restored = PatchManager.resolveRestoredQueue(
            listOf(previousFile.absolutePath, missingActiveFile.absolutePath, nextFile.absolutePath),
            savedActiveIndex = 1
        )

        assertEquals(listOf(previousFile.absoluteFile, nextFile.absoluteFile), restored.files.map { it.absoluteFile })
        assertEquals(1, restored.activeIndex)
    }

    @Test
    fun testSessionPathSerialization() {
        val root = File("presets").absoluteFile
        val presetFile = File(root, "patches/MyPatch.lsd")
        
        val serialized = PatchManager.serializeSessionPath(presetFile)
        assertEquals("patches/MyPatch.lsd", serialized)
        
        val outsideFile = File("/tmp/some_other_place.lsd")
        val serializedOutside = PatchManager.serializeSessionPath(outsideFile)
        assertEquals(outsideFile.absolutePath, serializedOutside)
    }

    @Test
    fun testSessionPathResolution() {
        val root = File("presets").absoluteFile
        val presetFile = File(root, "patches/MyPatch.lsd")
        presetFile.parentFile.mkdirs()
        presetFile.writeText("{}")
        
        val resolved = PatchManager.resolveSessionPath("patches/MyPatch.lsd")
        assertEquals(presetFile.absoluteFile, resolved?.absoluteFile)
        
        val outsideFile = File.createTempFile("outside", ".lsd")
        outsideFile.writeText("{}")
        
        val resolvedOutside = PatchManager.resolveSessionPath(outsideFile.absolutePath)
        assertEquals(outsideFile.absoluteFile, resolvedOutside?.absoluteFile)
        
        val missing = PatchManager.resolveSessionPath("missing/Patch.lsd")
        assertNull(missing)
        
        presetFile.delete()
        outsideFile.delete()
    }
    
    @Test
    fun testRestoredQueueUnresolvedItems() {
        val root = File("presets").absoluteFile
        val presetFile = File(root, "patches/MyPatch.lsd")
        presetFile.parentFile.mkdirs()
        presetFile.writeText("{}")
        
        PatchManager.resolveRestoredQueue(
            listOf("patches/MyPatch.lsd", "patches/MissingPatch.lsd"),
            savedActiveIndex = 0
        )
        
        val unresolved = PatchManager.sessionState.unresolvedItems
        assertEquals(listOf("patches/MissingPatch.lsd"), unresolved)
        
        presetFile.delete()
    }
}

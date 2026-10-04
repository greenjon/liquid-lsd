package llm.slop.liquidlsd.presets

import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.encodeToString
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.models.TransitionPresetDto
import llm.slop.liquidlsd.rendering.Mixer
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TransitionOpsTest {
    private val mixer = mockk<Mixer>(relaxed = true)

    @AfterTest
    fun drain() = TransitionOps.drainOnGlThread(mixer)

    private fun dto(filterId: String) = TransitionPresetDto(
        name = "t",
        slot = FXSlotDto(
            filterId = filterId,
            dryWet = ParameterDto(baseValue = 1f, baseMin = 0f, baseMax = 1f, randomizeBase = false, modulators = emptyList())
        )
    )

    @Test
    fun changesPostedFromAnotherThreadOnlyApplyWhenDrained() {
        val io = Executors.newSingleThreadExecutor()
        try {
            // Mimics an async file load completing on the preset IO thread.
            CompletableFuture.runAsync({ TransitionOps.setStock("wipe") }, io).get(5, TimeUnit.SECONDS)
        } finally {
            io.shutdown()
        }

        verify(exactly = 0) { mixer.setTransition(any()) }
        assertEquals(1, TransitionOps.pendingCount)

        TransitionOps.drainOnGlThread(mixer)

        verify(exactly = 1) { mixer.setTransition("wipe") }
        assertEquals(0, TransitionOps.pendingCount)
    }

    @Test
    fun presetFileIsReadOffThreadAndAppliedOnDrain() {
        val file = File.createTempFile("trans", ".lsdtrans").apply {
            deleteOnExit()
            writeText(PresetManager.json.encodeToString(dto("swirl")))
        }
        TransitionOps.loadPreset(file).get(5, TimeUnit.SECONDS)

        verify(exactly = 0) { mixer.applyTransitionPreset(any()) }
        TransitionOps.drainOnGlThread(mixer)
        verify(exactly = 1) { mixer.applyTransitionPreset(match { it.slot.filterId == "swirl" }) }
    }

    @Test
    fun unreadablePresetFallsBackToStockIdNamedAfterFile() {
        val missing = File(System.getProperty("java.io.tmpdir"), "does_not_exist_fade.lsdtrans")
        TransitionOps.loadPreset(missing).get(5, TimeUnit.SECONDS)

        TransitionOps.drainOnGlThread(mixer)
        verify(exactly = 1) { mixer.setTransition("does_not_exist_fade") }
    }

    @Test
    fun applyItemMapsShaderFilesAndBareIdsToStockTransitions() {
        TransitionOps.applyItem(File("/some/dir/dissolve.fs"))
        TransitionOps.applyItem(File("cube"))

        TransitionOps.drainOnGlThread(mixer)
        verify(exactly = 1) { mixer.setTransition("dissolve") }
        verify(exactly = 1) { mixer.setTransition("cube") }
    }
}

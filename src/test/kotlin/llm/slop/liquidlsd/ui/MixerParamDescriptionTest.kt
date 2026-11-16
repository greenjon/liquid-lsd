package llm.slop.liquidlsd.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/** Every row on the MST tabs (CTRL, LEVELS, TRANS crossfade rows) has a description in its grid tooltip (audit L5-2). */
class MixerParamDescriptionTest {
    private val mixerRows = listOf(
        "crossfade", "xfadeSpeed", "masterLevel", "levelA", "levelB", "levelBG", "levelPV",
        "queuePrev", "queueNext", "bgQueuePrev", "bgQueueNext", "transQueuePrev", "transQueueNext",
        "fxQueuePrev", "fxQueueNext", "fxBgQueuePrev", "fxBgQueueNext", "tapTempo",
        "randDeckA", "randDeckB", "randDeckBG", "randDeckPV", "randAll"
    )

    @Test
    fun mixerRowsHaveDescriptions() {
        val missing = mixerRows.filter { ParametersRenderer.paramDescription("Mixer/$it", it).isEmpty() }
        assertTrue(missing.isEmpty(), "Mixer rows without a description: $missing")
    }

    @Test
    fun mixerDescriptionsAvoidBannedTerms() {
        val banned = Regex("""(?i)\bplay queue\b|\bbackground queue\b|\bbackground FX queue\b""")
        val bad = mixerRows.map { ParametersRenderer.paramDescription("Mixer/$it", it) }.filter { banned.containsMatchIn(it) }
        assertTrue(bad.isEmpty(), "banned terms in: $bad")
    }
}

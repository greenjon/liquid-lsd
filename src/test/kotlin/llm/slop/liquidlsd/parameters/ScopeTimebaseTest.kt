package llm.slop.liquidlsd.parameters

import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.cv.evaluateModulatorAtOffset
import llm.slop.liquidlsd.cv.getCombinedEffectiveValueAtOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopeTimebaseTest {

    @Test
    fun `test formatTimeOffset correctly handles sub-second, seconds, minutes and hours`() {
        assertEquals("NOW", ScopeTimebase.formatTimeOffset(0.0f))
        assertEquals("-250ms", ScopeTimebase.formatTimeOffset(-0.25f))
        assertEquals("+500ms", ScopeTimebase.formatTimeOffset(0.5f))
        assertEquals("-2s", ScopeTimebase.formatTimeOffset(-2.0f))
        assertEquals("+10s", ScopeTimebase.formatTimeOffset(10.0f))
        assertEquals("-3m", ScopeTimebase.formatTimeOffset(-180.0f))
        assertEquals("+15m", ScopeTimebase.formatTimeOffset(900.0f))
        assertEquals("-2.5h", ScopeTimebase.formatTimeOffset(-9000.0f))
        assertEquals("+24h", ScopeTimebase.formatTimeOffset(86400.0f))
    }

    @Test
    fun `test ModulatableParameter resolveEffectiveTimebase auto derives from active LFO`() {
        val param = ModulatableParameter(baseValue = 0.5f)
        assertEquals(ScopeTimebase.AUTO, param.scopeTimebase)

        // No modulators -> defaults to 10s
        val (dur10, div10) = param.resolveEffectiveTimebase()
        assertEquals(10.0f, dur10)
        assertEquals(2.0f, div10)

        val (durOne, divOne) = param.resolveEffectiveTimebase(defaultWhenNoLfo = ScopeTimebase.ONE_SEC)
        assertEquals(1.0f, durOne)
        assertEquals(0.25f, divOne)

        // Add 24-hour LFO
        val lfo24h = CvModulator(
            id = "lfo_24h",
            sourceId = "lfo",
            genUnit = GenUnit.TIME,
            subdivision = 86400.0f
        )
        param.modulators.add(lfo24h)
        val (dur24, _) = param.resolveEffectiveTimebase()
        assertEquals(86400.0f, dur24)

        // Add fast 0.5s LFO
        param.modulators.clear()
        val fastLfo = CvModulator(
            id = "lfo_fast",
            sourceId = "lfo",
            genUnit = GenUnit.TIME,
            subdivision = 0.5f
        )
        param.modulators.add(fastLfo)
        val (durFast, _) = param.resolveEffectiveTimebase()
        assertEquals(1.0f, durFast)

        // Explicit timebase override
        param.scopeTimebase = ScopeTimebase.FIFTEEN_MIN
        val (durExplicit, divExplicit) = param.resolveEffectiveTimebase()
        assertEquals(900.0f, durExplicit)
        assertEquals(180.0f, divExplicit)
    }

    @Test
    fun `test decoupled per-scope timebases across LFO, Audio, and Final tabs`() {
        val param = ModulatableParameter(baseValue = 0.5f)
        param.setScopeTimebase("lfo", ScopeTimebase.TEN_SEC)
        param.setScopeTimebase("audio", ScopeTimebase.HUNDRED_SEC)
        param.setScopeTimebase("final", ScopeTimebase.FIFTEEN_MIN)

        assertEquals(ScopeTimebase.TEN_SEC, param.getScopeTimebase("lfo"))
        assertEquals(ScopeTimebase.HUNDRED_SEC, param.getScopeTimebase("audio"))
        assertEquals(ScopeTimebase.FIFTEEN_MIN, param.getScopeTimebase("final"))
        assertEquals(ScopeTimebase.TEN_SEC, param.getScopeTimebase("trigger"))

        assertEquals(10.0f, param.resolveEffectiveTimebase("lfo").first)
        assertEquals(100.0f, param.resolveEffectiveTimebase("audio").first)
        assertEquals(900.0f, param.resolveEffectiveTimebase("final").first)
        assertEquals(10.0f, param.resolveEffectiveTimebase("trigger").first)
    }

    @Test
    fun `test evaluateModulatorAtOffset future lookahead calculation`() {
        val mod = CvModulator(
            id = "test_lfo",
            sourceId = "lfo",
            genUnit = GenUnit.TIME,
            subdivision = 4.0f, // 4-second sine period
            waveform = Waveform.SINE
        )

        // Evaluate at now vs quarter cycle ahead (+1.0s)
        val valNow = evaluateModulatorAtOffset(mod, 0.0)
        val valQuarter = evaluateModulatorAtOffset(mod, 1.0)
        val valHalf = evaluateModulatorAtOffset(mod, 2.0)

        // Future evaluation produces valid numerical output in [-1, 1]
        assertTrue(valNow in -1.0f..1.0f)
        assertTrue(valQuarter in -1.0f..1.0f)
        assertTrue(valHalf in -1.0f..1.0f)

        // Combined effective value at offset
        val combined = getCombinedEffectiveValueAtOffset(listOf(mod), isBipolar = false, timeOffsetSec = 1.0)
        assertTrue(combined in 0.0f..1.0f)
    }

    @Test
    fun `test CvHistoryBuffer sampleWindow interpolation and bounds`() {
        val buffer = llm.slop.liquidlsd.cv.CvHistoryBuffer(10)
        for (i in 1..10) {
            buffer.add(i * 10f) // 10.0, 20.0, ..., 100.0 (NOW = 100.0)
        }

        // Fraction 1.0 is newest (NOW)
        assertEquals(100f, buffer.sampleWindow(10, 1.0f))
        // Fraction 0.0 is oldest (10.0)
        assertEquals(10f, buffer.sampleWindow(10, 0.0f))
        // Fraction 0.5 is midpoint (55.0)
        assertEquals(55f, buffer.sampleWindow(10, 0.5f))

        // Span smaller than buffer size (last 4 samples: 70, 80, 90, 100)
        assertEquals(70f, buffer.sampleWindow(4, 0.0f))
        assertEquals(100f, buffer.sampleWindow(4, 1.0f))
        assertEquals(85f, buffer.sampleWindow(4, 0.5f))
    }
}


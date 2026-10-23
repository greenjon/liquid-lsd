package llm.slop.liquidlsd.cv

import llm.slop.liquidlsd.parameters.GenUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps seconds- and frame-clocked generators continuous when their period changes. `clock / period` alone moves by
 * clock x the change in 1/period, which grows over a set until any wobble of the period (a knob, a macro, a morph) jumps
 * the LFO; on a change the anchor's shift absorbs that step, so only the rate changes. Beat-clocked generators are not
 * anchored: they stay locked to the bar.
 *
 * One per [llm.slop.liquidlsd.parameters.ModulatableParameter], keyed by modulator id: the same preset on two decks shares
 * ids, so a global table would mix their periods.
 */
class ClockAnchors {
    private class Anchor(var unit: GenUnit, var period: Double) {
        var shift = 0.0
    }

    private val carriers = ConcurrentHashMap<String, Anchor>()
    private val lfo2s = ConcurrentHashMap<String, Anchor>()

    /** Cycle position at [clock] + [offset] of the main generator (LFO 1 or a sequencer) of modulator [id]. */
    fun cycles(id: String, unit: GenUnit, clock: Double, offset: Double, period: Double): Double =
        position(carriers, id, unit, clock, offset, period)

    /** As [cycles], for the LFO 2 generator that modulates LFO 1. */
    fun lfo2Cycles(id: String, unit: GenUnit, clock: Double, offset: Double, period: Double): Double =
        position(lfo2s, id, unit, clock, offset, period)

    private fun position(
        table: ConcurrentHashMap<String, Anchor>, id: String, unit: GenUnit, clock: Double, offset: Double, period: Double
    ): Double {
        val a = table[id] ?: table.computeIfAbsent(id) { Anchor(unit, period) }
        if (a.unit != unit) {
            a.unit = unit
            a.period = period
            a.shift = 0.0
        } else if (a.period != period) {
            a.shift += clock / a.period - clock / period
            a.period = period
        }
        return (clock + offset) / period + a.shift
    }
}

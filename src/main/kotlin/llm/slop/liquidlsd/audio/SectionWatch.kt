package llm.slop.liquidlsd.audio

import kotlin.math.min

/**
 * Song sections from the band levels: a *breakdown* is the low end falling well below where it has been
 * for the last ~20 s; a *drop* is it coming back after a breakdown of at least [DROP_MIN] seconds. The
 * long-term level freezes during a breakdown (and while one is being confirmed), so the drop is measured against the music before it, and it
 * jumps to the current level when the music settles somewhere new (a quieter or a louder song).
 *
 * [AudioAgc] normalises against the same long-term level, so a breakdown also reads quiet. A port of
 * `web/energy.js` (EnergyWatch), which the web TV's autopilot also uses: keep the two identical
 * (`WebAudioAgcVectorsTest`). No allocation; audio thread only.
 */
class SectionWatch {
    enum class Section { NORMAL, BREAKDOWN }

    var short = 0f; private set
    var long = 0f; private set
    var section = Section.NORMAL; private set
    var drops = 0; private set
    /** True on the update in which [long] jumped to the current level (a new normal). */
    var rebased = false; private set

    /** [long] is not following the music: a breakdown, or the level has just fallen under the breakdown ratio. */
    val holding: Boolean get() = section == Section.BREAKDOWN || below > 0f

    private var time = 0f
    private var below = 0f
    private var louder = 0f
    private var sectionTime = 0f

    /** One block of fixed-scale band levels (0..1) over [dt] seconds. */
    fun update(bass: Float, amp: Float, dt: Float) {
        val level = 0.7f * bass + 0.3f * amp
        time += dt
        sectionTime += dt
        rebased = false
        short += (level - short) * min(1f, dt / SHORT_TAU)
        // A running mean until LONG_TAU has passed, so the reference is right from the first seconds. It
        // stops as soon as the level dips under the breakdown ratio, so a breakdown does not drag it down
        // while it is being confirmed.
        if (!holding) long += (level - long) * min(1f, dt / min(LONG_TAU, time))
        if (time < WARMUP || long < SILENCE) { below = 0f; louder = 0f; return }

        if (section == Section.NORMAL) {
            below = if (short < BREAK_RATIO * long) below + dt else 0f
            if (below >= BREAK_HOLD) { enter(Section.BREAKDOWN); return }
            louder = if (short > LOUDER_RATIO * long) louder + dt else 0f
            if (louder >= LOUDER_HOLD) rebase()
            return
        }
        // breakdown
        if (short > DROP_RATIO * long) {
            if (sectionTime >= DROP_MIN) drops++ // otherwise only a short dip
            enter(Section.NORMAL)
            return
        }
        if (sectionTime > BREAK_MAX) { rebase(); enter(Section.NORMAL) }
    }

    /** Starts afresh (a new feed); the drop count is kept. */
    fun reset() {
        short = 0f; long = 0f; time = 0f; below = 0f; louder = 0f; sectionTime = 0f
        section = Section.NORMAL
        rebased = false
    }

    private fun enter(next: Section) {
        section = next
        sectionTime = 0f
        below = 0f
    }

    private fun rebase() {
        long = short
        louder = 0f
        rebased = true
    }

    companion object {
        const val SHORT_TAU = 1.5f    // s, the "now" level
        const val LONG_TAU = 20f      // s, the reference level
        const val WARMUP = 20f        // s of audio before any section is reported
        const val SILENCE = 0.03f     // a reference level below this is silence, not a breakdown
        const val BREAK_RATIO = 0.45f // now below this fraction of the reference...
        const val BREAK_HOLD = 3f     // ...for this long (s) is a breakdown
        const val DROP_RATIO = 0.85f  // back above this fraction of the pre-breakdown level is a drop...
        const val DROP_MIN = 4f       // ...if the breakdown lasted at least this long (s)
        const val BREAK_MAX = 60f     // a "breakdown" this long is just a quieter song: adopt it
        const val LOUDER_RATIO = 1.6f // now above this multiple of the reference...
        const val LOUDER_HOLD = 4f    // ...for this long (s) is a louder song: adopt it at once
    }
}

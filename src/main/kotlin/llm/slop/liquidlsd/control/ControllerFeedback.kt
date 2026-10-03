package llm.slop.liquidlsd.control

import kotlin.math.roundToInt

/**
 * Keeps one controller's rings and LEDs in step with the app.
 *
 * Every hardware bank's knob n controls page knob n (the page follows the screen, not the bank), so
 * every bank is meant to show the same 16 lights. The Twister only reliably shows what was written to
 * the bank that is on screen (other banks fall back to their own stored colours), so lights go to the
 * *active* bank only: all banks while the active bank is still unknown, and the whole new bank
 * whenever it changes. Only changes are sent; the sink paces and coalesces what is sent.
 */
class ControllerFeedback(private val compiled: CompiledController, private val sink: MidiSink) {
    private class Target(
        val bank: Int,
        val knob: Int,
        val ringChannel: Int,
        val ringCc: Int,
        val colorChannel: Int?,
        val colorCc: Int
    ) {
        var lastRing = -1
        var lastColor = -1
        fun forget() { lastRing = -1; lastColor = -1 }
    }

    private val wheel: HueWheel
    private val targets: List<Target>
    private var lastActiveBank: Int? = null
    private var hasUpdated = false

    init {
        val fb = compiled.profile.output.knobs
        val group = fb?.let { def -> compiled.profile.inputs.firstOrNull { it.id == def.input } }
        wheel = fb?.color ?: HueWheel()
        targets = if (fb == null || group == null) emptyList() else {
            val baseCcs = group.ccs.ifEmpty { (group.cc until group.cc + group.count).toList() }
            val banks = if (group.bankStride != 0) compiled.profile.banks.count.coerceAtLeast(1) else 1
            (0 until banks).flatMap { bank ->
                baseCcs.mapIndexed { knob, baseCc ->
                    val cc = baseCc + bank * group.bankStride
                    Target(bank, knob, fb.ringChannel ?: group.channel, cc, fb.colorChannel, cc)
                }
            }
        }
    }

    val isHealthy: Boolean get() = sink.isHealthy

    /**
     * Sends what differs from the last send for [lights] (index = knob, null = nothing there).
     * [activeBank] is the device's 0-based bank if known; a bank change rewrites the whole new bank.
     */
    fun update(lights: List<KnobLight?>, activeBank: Int? = null) {
        if (targets.isEmpty()) return
        val active = targets.filter { activeBank == null || it.bank == activeBank }

        val bankChanged = hasUpdated && activeBank != lastActiveBank
        hasUpdated = true
        lastActiveBank = activeBank
        if (bankChanged) active.forEach { it.forget() }

        for (t in active) send(t, lights.getOrNull(t.knob))
    }

    /** Sends [t]'s ring and LED, on the encoder's own (per-bank) CC numbers, if they changed. */
    private fun send(t: Target, light: KnobLight?) {
        val ring = ((light?.value ?: 0f).coerceIn(0f, 1f) * 127f).roundToInt()
        if (ring != t.lastRing) {
            t.lastRing = ring
            sink.sendCc(t.ringChannel, t.ringCc, ring)
        }
        val colorChannel = t.colorChannel ?: return
        val color = if (light == null || !light.lit) wheel.off else wheel.valueFor(light.r, light.g, light.b)
        if (color != t.lastColor) {
            t.lastColor = color
            sink.sendCc(colorChannel, t.colorCc, color)
        }
    }

    /** Forgets what was sent, so the next [update] rewrites every ring and LED (new connection, device reset). */
    fun resync() {
        targets.forEach { it.forget() }
    }

    fun close() = sink.close()
}

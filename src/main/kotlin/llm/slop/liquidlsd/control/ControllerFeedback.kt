package llm.slop.liquidlsd.control

import kotlin.math.roundToInt

/**
 * Keeps one controller's rings and LEDs in step with the app.
 *
 * Every hardware bank's knob n controls page knob n (the page follows the screen, not the bank), so
 * every bank is meant to show the same 16 lights. The Twister only reliably shows what was written to
 * the bank that is on screen (other banks fall back to their own stored colours), so lights go to the
 * *active* bank only: all banks while the active bank is still unknown, and the whole new bank
 * whenever it changes. Only changes are sent, plus two re-assertions that heal writes the device
 * drops or overwrites: a full rewrite [SETTLE_MS] after the last change, rewrites at
 * [BANK_REWRITES_MS] after connecting or a bank switch (the device is busy redrawing for a while),
 * and one every [HEARTBEAT_MS]. The sink paces and coalesces what is sent.
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
    private var settleAtMs = NEVER
    private val bankRewrites = ArrayList<Long>()
    private var nextHeartbeatMs = 0L

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
     * [activeBank] is the device's 0-based bank if known; [nowMs] drives the settle and heartbeat rewrites.
     */
    fun update(lights: List<KnobLight?>, activeBank: Int? = null, nowMs: Long = System.currentTimeMillis()) {
        if (targets.isEmpty()) return
        val active = targets.filter { activeBank == null || it.bank == activeBank }

        val first = !hasUpdated
        val bankChanged = !first && activeBank != lastActiveBank
        hasUpdated = true
        lastActiveBank = activeBank

        // A rewrite (forced) must not count as a change, or it would re-arm the settle timer forever.
        var forced = bankChanged
        if (first || bankChanged) {
            // The device is busy redrawing after connecting / switching bank: write again a few times.
            bankRewrites.clear()
            BANK_REWRITES_MS.forEach { bankRewrites += nowMs + it }
        }
        if (nowMs >= nextHeartbeatMs) {
            nextHeartbeatMs = nowMs + HEARTBEAT_MS
            forced = true
        }
        if (bankRewrites.isNotEmpty() && bankRewrites.first() <= nowMs) {
            bankRewrites.removeAll { it <= nowMs }
            forced = true
        }
        if (settleAtMs != NEVER && nowMs >= settleAtMs) {
            settleAtMs = NEVER
            forced = true
        }
        if (forced) active.forEach { it.forget() }

        var sentChange = false
        for (t in active) sentChange = send(t, lights.getOrNull(t.knob)) || sentChange
        // A real change restarts the settle timer, so the rewrite fires once things go quiet.
        if (sentChange && !forced) settleAtMs = nowMs + SETTLE_MS
    }

    /** Sends [t]'s ring and LED, on the encoder's own (per-bank) CC numbers, if they changed. */
    private fun send(t: Target, light: KnobLight?): Boolean {
        var sent = false
        val ring = ((light?.value ?: 0f).coerceIn(0f, 1f) * 127f).roundToInt()
        if (ring != t.lastRing) {
            t.lastRing = ring
            sink.sendCc(t.ringChannel, t.ringCc, ring)
            sent = true
        }
        val colorChannel = t.colorChannel ?: return sent
        val color = if (light == null || !light.lit) wheel.off else wheel.valueFor(light.r, light.g, light.b)
        if (color != t.lastColor) {
            t.lastColor = color
            sink.sendCc(colorChannel, t.colorCc, color)
            sent = true
        }
        return sent
    }

    /** Forgets what was sent, so the next [update] rewrites every ring and LED (new connection, device reset). */
    fun resync() {
        targets.forEach { it.forget() }
    }

    fun close() = sink.close()

    companion object {
        /** Quiet time after the last change before the active bank is rewritten once more. */
        const val SETTLE_MS = 120L
        /** Rewrites of the active bank after connecting or switching to it, relative to that moment. */
        val BANK_REWRITES_MS = longArrayOf(100, 400, 1200)
        /** Interval of the unconditional rewrite of the active bank. */
        const val HEARTBEAT_MS = 3000L
        private const val NEVER = Long.MIN_VALUE
    }
}

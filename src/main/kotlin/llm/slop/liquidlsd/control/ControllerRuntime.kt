package llm.slop.liquidlsd.control

import llm.slop.liquidlsd.midi.MidiEvent
import mu.KotlinLogging

/**
 * Drives the [CommandRegistry] from one connected controller using its [CompiledController]:
 * tracks the active bank and held modifiers, turns encoder messages into acceleration-scaled
 * deltas, and routes each input to its bound command. Render thread only.
 */
class ControllerRuntime(
    val compiled: CompiledController,
    private val registry: CommandRegistry,
    private val trace: Boolean = false
) {
    /** The device's active 0-based bank, once known (after a bank button or any bank-aware input). */
    var activeBank: Int? = null

    // Bit set of held modifiers (see CompiledController.modifierBit) and per-input state in flat
    // arrays indexed by ResolvedInput.slot / idIndex, so the event path allocates nothing.
    private var heldMask = 0
    private val absoluteSeen = BooleanArray(compiled.slotCount)
    private val lastAbsolute = IntArray(compiled.slotCount)
    private val turnSeen = BooleanArray(compiled.slotCount)
    private val lastTurnMs = LongArray(compiled.slotCount)
    private val pressCommand = arrayOfNulls<String>(compiled.idCount)

    /** Forgets held modifiers and in-flight presses (device went away mid-press); no release is delivered. */
    fun clearHeldState() {
        for (i in pressCommand.indices) {
            val command = pressCommand[i] ?: continue
            pressCommand[i] = null
            registry.forgetHeld(command)
        }
        heldMask = 0
    }

    /** Handles [event]; returns true if the profile consumed it (so legacy MIDI bindings skip it). */
    fun handle(event: MidiEvent, ctx: CommandContext): Boolean {
        val input = compiled.resolve(event) ?: return false
        val down = event.rawValue > 0

        if (input.kind == InputKind.BANK_SWITCH) {
            if (down) enterBank(input.bank ?: return true, ctx)
            return true
        }
        input.bank?.let { activeBank = it }

        return when (input.kind) {
            InputKind.MODIFIER -> {
                val bit = compiled.modifierBit(input.inputId)
                heldMask = if (down) heldMask or bit else heldMask and bit.inv()
                true
            }
            InputKind.ENCODER -> turn(input, event, ctx)
            InputKind.BUTTON -> button(input, down, ctx)
            InputKind.FADER -> {
                val command = compiled.bindingForMask(input.inputId, heldMask, input.bank ?: -1) ?: return false
                registry.execute(command, CommandInput.Value(event.rawValue / 127f), ctx)
                true
            }
            InputKind.BANK_SWITCH -> true
        }
    }

    private fun enterBank(bank: Int, ctx: CommandContext) {
        if (trace) logger.info { "controller rx bank entered: ${bank + 1} (page ${compiled.profile.banks.pages.getOrNull(bank)})" }
        activeBank = bank
        val page = compiled.profile.banks.pages.getOrNull(bank)
        if (!page.isNullOrBlank()) ctx.knobSurface?.showPage(page)
    }

    /**
     * A bank-step button: shows the neighbouring bank's page. The device's own bank follows from the
     * page ([ControllerFeedback.syncActiveBank] sends the bank change), so [activeBank] is left alone here.
     */
    private fun stepBank(ctx: CommandContext) {
        val delta = ctx.bankDelta
        ctx.bankDelta = 0
        val pages = compiled.profile.banks.pages
        if (pages.isEmpty()) return
        val count = compiled.profile.banks.count.coerceIn(1, pages.size)
        val target = Math.floorMod((activeBank ?: 0) + delta, count)
        if (trace) logger.info { "controller bank step $delta: ${(activeBank ?: 0) + 1} -> ${target + 1}" }
        pages.getOrNull(target)?.takeIf { it.isNotBlank() }?.let { ctx.knobSurface?.showPage(it) }
    }

    private fun turn(input: ResolvedInput, event: MidiEvent, ctx: CommandContext): Boolean {
        val command = compiled.bindingForMask(input.inputId, heldMask, input.bank ?: -1) ?: return false
        val slot = input.slot
        if (trace) logger.info { "controller rx turn cc=${event.index} raw=${event.rawValue} -> ${input.inputId} bank=${input.bank?.plus(1)} mode=${input.mode}" }

        val ticks = if (input.mode == EncoderMode.ABSOLUTE) {
            // The first message only tells us where the knob is; later ones are changes from there.
            val seen = absoluteSeen[slot]
            val previous = lastAbsolute[slot]
            absoluteSeen[slot] = true
            lastAbsolute[slot] = event.rawValue
            if (!seen) 0 else event.rawValue - previous
        } else {
            decodeRelative(input.mode, event.rawValue)
        }
        if (ticks == 0) return true

        val hadPrevious = turnSeen[slot]
        val previousMs = lastTurnMs[slot]
        turnSeen[slot] = true
        lastTurnMs[slot] = event.timestampMs
        val boost = if (!hadPrevious) 1f else accelerationFactor(event.timestampMs - previousMs, input.accel)
        registry.execute(command, CommandInput.Delta(ticks * input.step * boost), ctx)
        return true
    }

    private fun button(input: ResolvedInput, down: Boolean, ctx: CommandContext): Boolean {
        if (down) {
            val command = compiled.bindingForMask(input.inputId, heldMask, input.bank ?: -1) ?: return false
            // The release goes to the command that got the press, even if shift has changed since.
            pressCommand[input.idIndex] = command
            registry.execute(command, CommandInput.Press.DOWN, ctx)
            if (ctx.bankDelta != 0) stepBank(ctx)
            return true
        }
        val pressed = pressCommand[input.idIndex]
        pressCommand[input.idIndex] = null
        val command = pressed
            ?: compiled.bindingForMask(input.inputId, heldMask, input.bank ?: -1)
            ?: return false
        registry.execute(command, CommandInput.Press.UP, ctx)
        return true
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** Intervals at or under this get the full boost; at or over [SLOW_MS] none. */
        const val FAST_MS = 8L
        const val SLOW_MS = 60L

        /** Signed ticks for a relative encoder message; 0 for absolute mode. */
        fun decodeRelative(mode: EncoderMode, raw: Int): Int = when (mode) {
            EncoderMode.RELATIVE_BINARY_OFFSET -> raw - 64
            EncoderMode.RELATIVE_SIGNED_BIT -> if (raw > 64) -(raw - 64) else raw
            EncoderMode.RELATIVE_TWOS_COMP -> if (raw >= 64) raw - 128 else raw
            EncoderMode.ABSOLUTE -> 0
        }

        /** Speed-up for two ticks [dtMs] apart: 1 when slow, [maxBoost] when fast, linear between. */
        fun accelerationFactor(dtMs: Long, maxBoost: Float): Float {
            if (maxBoost <= 1f || dtMs >= SLOW_MS) return 1f
            if (dtMs <= FAST_MS) return maxBoost
            val t = (SLOW_MS - dtMs).toFloat() / (SLOW_MS - FAST_MS)
            return 1f + (maxBoost - 1f) * t
        }

        fun warnUnknownCommands(compiled: CompiledController, registry: CommandRegistry) {
            val unknown = compiled.unknownCommands(registry)
            if (unknown.isNotEmpty()) {
                logger.warn { "Controller profile ${compiled.profile.id} binds unknown commands: ${unknown.joinToString()}" }
            }
        }
    }
}

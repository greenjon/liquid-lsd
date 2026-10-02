package llm.slop.liquidlsd.control

import kotlinx.serialization.Serializable
import llm.slop.liquidlsd.midi.MidiEvent
import llm.slop.liquidlsd.midi.MidiMessageType

@Serializable
enum class InputKind { ENCODER, BUTTON, FADER, MODIFIER, BANK_SWITCH }

@Serializable
enum class EncoderMode { ABSOLUTE, RELATIVE_BINARY_OFFSET, RELATIVE_SIGNED_BIT, RELATIVE_TWOS_COMP }

/**
 * Hardware banks. Bank-aware inputs add their `bankStride` to the CC for each bank, so the same
 * logical input keeps one id across banks. [switch] describes the device's own bank buttons: the
 * CC is `switch.cc + bankIndex` and a non-zero value means "this bank is now active".
 * [pages] names the app page to show when a bank becomes active (index = 0-based bank, e.g.
 * `perform.decks`); banks beyond the list leave the app's page alone.
 */
@Serializable
data class BankConfig(val count: Int = 1, val switch: BankSwitchDef? = null, val pages: List<String> = emptyList())

@Serializable
data class BankSwitchDef(val channel: Int, val cc: Int = 0)

/** Encoder push switch: same CC numbers (and bank stride) as the encoder, on another channel. */
@Serializable
data class PressDef(val channel: Int)

/**
 * One physical input, or a group of them. Group members come from [ccs] if given, otherwise
 * `cc until cc + count`. A group's inputs are named `<id>.<n>` (1-based); a single input is `<id>`.
 *
 * For encoders, [step] is the fraction of a control's full range one tick moves it (ticks are the
 * relative delta, or for [EncoderMode.ABSOLUTE] the change since the previous value), and [accel]
 * is the largest speed-up applied to a fast turn (1 disables acceleration).
 */
@Serializable
data class InputDef(
    val id: String,
    val kind: InputKind,
    val channel: Int,
    val messageType: MidiMessageType = MidiMessageType.CC,
    val cc: Int = 0,
    val count: Int = 1,
    val ccs: List<Int> = emptyList(),
    val bankStride: Int = 0,
    val mode: EncoderMode = EncoderMode.ABSOLUTE,
    val step: Float = 1f / 127f,
    val accel: Float = 4f,
    val press: PressDef? = null
)

/**
 * How a hue (RGB colour) becomes the single CC value the controller's RGB LEDs take. Values
 * [min]..[max] sweep the colour wheel starting at [hueAtMin] degrees and moving [degreesPerStep]
 * per value (negative = hue decreases); [off] turns the LED off and [white] is used for greys.
 * The defaults are the Midi Fighter Twister's wheel as best understood; tune them in the profile.
 */
@Serializable
data class HueWheel(
    val min: Int = 1,
    val max: Int = 126,
    val off: Int = 0,
    val white: Int = 127,
    val hueAtMin: Float = 240f,
    val degreesPerStep: Float = -2.88f
) {
    /** The CC value closest to the colour ([r], [g], [b] each 0..1). */
    fun valueFor(r: Float, g: Float, b: Float): Int {
        val hi = maxOf(r, g, b)
        val lo = minOf(r, g, b)
        val chroma = hi - lo
        if (hi <= 0.02f) return off
        if (chroma < 0.1f * hi) return white
        val hue = when (hi) {
            r -> 60f * (((g - b) / chroma) % 6f)
            g -> 60f * ((b - r) / chroma + 2f)
            else -> 60f * ((r - g) / chroma + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val travel = if (degreesPerStep < 0f) hueAtMin - hue else hue - hueAtMin
        val wrapped = ((travel % 360f) + 360f) % 360f
        return (min + Math.round(wrapped / kotlin.math.abs(degreesPerStep))).coerceIn(min, max)
    }
}

/**
 * Ring and LED feedback for an encoder group ([input]): the ring (position indicator) is set by
 * sending the encoder's own CC back on [ringChannel] (default: the encoder's channel), and the RGB
 * LED by sending the same CC number on [colorChannel] (null = no colour feedback).
 */
@Serializable
data class KnobFeedbackDef(
    val input: String = "knob",
    val ringChannel: Int? = null,
    val colorChannel: Int? = null,
    val color: HueWheel = HueWheel(),
    val addressing: FeedbackAddressing = FeedbackAddressing.BANK_ABSOLUTE
)

/**
 * Which CC numbers a bank's rings and LEDs are written to. [BANK_ABSOLUTE] uses the numbers the
 * encoders send on that bank (knob 1 on bank 2 is CC 16); [ACTIVE_BANK] always uses the first bank's
 * numbers (CC 0..15), which some firmware treats as "whichever bank is showing"; [BOTH] writes both,
 * which is harmless when only one form is live (the bank is rewritten whenever it is entered).
 * On the Twister the per-bank numbers are the live ones (checked with amidi: CC 16 lights bank 2's
 * knob 1 while it is showing, CC 0 does nothing visible), so [BANK_ABSOLUTE] is the default.
 */
@Serializable
enum class FeedbackAddressing { BANK_ABSOLUTE, ACTIVE_BANK, BOTH }

/**
 * [minIntervalMs] is the least time between two feedback messages to the device: controllers drop
 * messages that arrive in a burst (the Twister's rings and LEDs did).
 */
@Serializable
data class OutputConfig(
    val knobs: KnobFeedbackDef? = null,
    val minIntervalMs: Int = 2,
    /** Log every feedback message sent, every bank change and every encoder message at INFO (also enabled by env LSD_MIDI_TRACE=1). */
    val trace: Boolean = false
)

/**
 * Declarative description of a MIDI controller: how to recognise it, what its inputs are, and the
 * default command bindings. Binding keys are an input id with optional held-modifier prefixes
 * (`shift+knob.3.press`); values are [CommandRegistry] ids. In the last part of a key `*` matches
 * a group index, and `{n}` in the value is replaced by it (`"knob.*": "knob.{n}"`). `bankBindings`
 * (keyed by 1-based bank) override `bindings` while that bank is active.
 */
@Serializable
data class ControllerProfile(
    val id: String,
    val name: String = id,
    val description: String = "",
    val match: List<String> = emptyList(),
    val banks: BankConfig = BankConfig(),
    val inputs: List<InputDef> = emptyList(),
    val bindings: Map<String, String> = emptyMap(),
    val bankBindings: Map<String, Map<String, String>> = emptyMap(),
    val output: OutputConfig = OutputConfig()
) {
    /** True if [deviceName] contains any of the [match] strings (case-insensitive). */
    fun matches(deviceName: String): Boolean =
        match.any { it.isNotBlank() && deviceName.contains(it, ignoreCase = true) }

    fun compile(): CompiledController = CompiledController.build(this)

    companion object {
        /** [profile] with every `knob` encoder group switched to [mode] (e.g. a copy for the factory absolute mode). */
        fun copyWithKnobMode(profile: ControllerProfile, mode: EncoderMode): ControllerProfile =
            profile.copy(inputs = profile.inputs.map { if (it.id == "knob") it.copy(mode = mode) else it })
    }
}

/** The result of looking up an incoming message in a [CompiledController]. */
data class ResolvedInput(
    val inputId: String,
    val kind: InputKind,
    /** 0-based bank for bank-aware inputs; null if the input is the same on every bank. */
    val bank: Int?,
    val mode: EncoderMode = EncoderMode.ABSOLUTE,
    val step: Float = 1f / 127f,
    val accel: Float = 1f
)

/**
 * A [ControllerProfile] expanded into a (type, channel, cc) lookup table and concrete bindings,
 * plus any structural [problems] found while building it. Profiles with problems must not be used.
 */
class CompiledController private constructor(
    val profile: ControllerProfile,
    private val table: Map<Long, ResolvedInput>,
    private val bindings: Map<String, String>,
    private val bankBindings: Map<Int, Map<String, String>>,
    val problems: List<String>
) {
    fun resolve(type: MidiMessageType, channel: Int, index: Int): ResolvedInput? =
        table[key(type, channel, index)]

    fun resolve(event: MidiEvent): ResolvedInput? = resolve(event.type, event.channel, event.index)

    /** The 0-based bank this event switched to, or null if it isn't a bank-entered message. */
    fun bankEntered(event: MidiEvent): Int? {
        if (event.rawValue <= 0) return null
        val resolved = resolve(event) ?: return null
        return if (resolved.kind == InputKind.BANK_SWITCH) resolved.bank else null
    }

    /**
     * The command bound to [inputId] given the currently [held] modifier input ids and the active
     * 0-based [bank]. The most specific binding wins: more modifiers beat fewer, and within the same
     * modifier set a bank binding beats a global one. Null if nothing is bound.
     */
    fun bindingFor(inputId: String, held: Collection<String> = emptyList(), bank: Int? = null): String? {
        for (modifiers in modifierSubsetsLargestFirst(held.toSortedSet().toList())) {
            val key = (modifiers + inputId).joinToString("+")
            if (bank != null) bankBindings[bank + 1]?.get(key)?.let { return it }
            bindings[key]?.let { return it }
        }
        return null
    }

    /** Binding targets (after wildcard expansion) that aren't registered commands or aliases in [registry]. */
    fun unknownCommands(registry: CommandRegistry): List<String> =
        (bindings.values + bankBindings.values.flatMap { it.values })
            .filter { registry.resolveId(it) == null }
            .distinct()

    companion object {
        private val validId = Regex("[a-z0-9][a-z0-9_-]*")

        private fun key(type: MidiMessageType, channel: Int, index: Int): Long =
            (type.ordinal.toLong() shl 16) or (channel.toLong() shl 8) or index.toLong()

        private fun modifierSubsetsLargestFirst(sorted: List<String>): List<List<String>> {
            val subsets = (0 until (1 shl sorted.size)).map { mask -> sorted.filterIndexed { i, _ -> mask and (1 shl i) != 0 } }
            return subsets.sortedByDescending { it.size }
        }

        internal fun build(profile: ControllerProfile): CompiledController {
            val problems = ArrayList<String>()
            val table = HashMap<Long, ResolvedInput>()
            val owners = HashMap<Long, String>()
            val inputIds = LinkedHashMap<String, InputKind>()

            if (!validId.matches(profile.id)) {
                problems += "Profile id '${profile.id}' must be lowercase letters, digits, '_' or '-'"
            }
            val bankCount = profile.banks.count
            if (bankCount !in 1..16) problems += "banks.count must be 1..16 (was $bankCount)"
            if (profile.banks.pages.size > bankCount) problems += "banks.pages has more entries than banks.count"

            fun claim(type: MidiMessageType, channel: Int, cc: Int, resolved: ResolvedInput) {
                if (channel !in 0..15) { problems += "${resolved.inputId}: channel $channel out of range 0..15"; return }
                if (cc !in 0..127) { problems += "${resolved.inputId}: cc $cc out of range 0..127"; return }
                val k = key(type, channel, cc)
                val existing = owners[k]
                if (existing != null) {
                    problems += "${resolved.inputId} and $existing both use ${type.name} ch$channel #$cc"
                    return
                }
                owners[k] = resolved.inputId
                table[k] = resolved
            }

            profile.banks.switch?.let { sw ->
                for (bank in 0 until bankCount.coerceAtLeast(1)) {
                    claim(MidiMessageType.CC, sw.channel, sw.cc + bank,
                        ResolvedInput("bank.${bank + 1}", InputKind.BANK_SWITCH, bank))
                }
            }

            for (def in profile.inputs) {
                if (def.id.isBlank() || def.id.any { it == '+' || it == '*' || it.isWhitespace() }) {
                    problems += "Input id '${def.id}' must be non-empty with no spaces, '+' or '*'"
                    continue
                }
                if (def.kind == InputKind.BANK_SWITCH) {
                    problems += "${def.id}: declare bank buttons under banks.switch, not as an input"
                    continue
                }
                if (def.press != null && def.kind != InputKind.ENCODER) {
                    problems += "${def.id}: press is only valid on ENCODER inputs"
                }
                if (def.step <= 0f || def.accel < 1f) problems += "${def.id}: step must be > 0 and accel >= 1"
                if (def.ccs.isEmpty() && def.count < 1) {
                    problems += "${def.id}: count must be >= 1"
                    continue
                }
                val baseCcs = def.ccs.ifEmpty { (def.cc until def.cc + def.count).toList() }
                val grouped = baseCcs.size > 1
                baseCcs.forEachIndexed { i, baseCc ->
                    val inputId = if (grouped) "${def.id}.${i + 1}" else def.id
                    if (inputIds.put(inputId, def.kind) != null) problems += "Duplicate input id $inputId"
                    val banksToExpand = if (def.bankStride != 0) bankCount.coerceAtLeast(1) else 1
                    for (bank in 0 until banksToExpand) {
                        val bankOrNull = if (def.bankStride != 0) bank else null
                        val cc = baseCc + bank * def.bankStride
                        claim(def.messageType, def.channel, cc,
                            ResolvedInput(inputId, def.kind, bankOrNull, def.mode, def.step, def.accel))
                        def.press?.let { press ->
                            claim(def.messageType, press.channel, cc,
                                ResolvedInput("$inputId.press", InputKind.BUTTON, bankOrNull))
                        }
                    }
                    if (def.press != null) inputIds["$inputId.press"] = InputKind.BUTTON
                }
            }

            if (profile.output.minIntervalMs !in 0..100) problems += "output.minIntervalMs must be 0..100"
            profile.output.knobs?.let { fb ->
                val group = profile.inputs.firstOrNull { it.id == fb.input }
                when {
                    group == null -> problems += "output.knobs.input '${fb.input}' is not an input"
                    group.kind != InputKind.ENCODER -> problems += "output.knobs.input '${fb.input}' must be an ENCODER"
                }
                listOf("ringChannel" to fb.ringChannel, "colorChannel" to fb.colorChannel).forEach { (name, channel) ->
                    if (channel != null && channel !in 0..15) problems += "output.knobs.$name $channel out of range 0..15"
                }
                val c = fb.color
                if (c.min !in 0..127 || c.max !in c.min..127 || c.off !in 0..127 || c.white !in 0..127) {
                    problems += "output.knobs.color values must be 0..127 with min <= max"
                }
                if (c.degreesPerStep == 0f) problems += "output.knobs.color.degreesPerStep must not be 0"
            }

            // Expands one binding map: wildcard keys first so explicit keys override them.
            fun expand(scope: String, map: Map<String, String>): Map<String, String> {
                val out = LinkedHashMap<String, String>()
                val ordered = map.entries.sortedBy { if ('*' in it.key.substringAfterLast('+')) 0 else 1 }
                for ((binding, target) in ordered) {
                    if (target.isBlank()) problems += "$scope'$binding' has an empty command"
                    val parts = binding.split('+')
                    val modifiers = parts.dropLast(1)
                    val last = parts.last()
                    for (m in modifiers) {
                        if (inputIds[m] != InputKind.MODIFIER) problems += "$scope'$binding': '$m' is not a MODIFIER input"
                    }
                    val concrete: List<Pair<String, String>> =
                        if ('*' in last) {
                            val pattern = Regex(last.split("*").joinToString("(\\d+)") { Regex.escape(it) })
                            inputIds.keys.mapNotNull { id -> pattern.matchEntire(id)?.let { id to target.replace("{n}", it.groupValues[1]) } }
                                .also { if (it.isEmpty()) problems += "$scope'$binding' matches no input" }
                        } else {
                            if (last !in inputIds) problems += "$scope'$binding' refers to unknown input '$last'"
                            listOf(last to target)
                        }
                    for ((inputId, command) in concrete) {
                        out[(modifiers.sorted() + inputId).joinToString("+")] = command
                    }
                }
                return out
            }

            val expanded = expand("binding ", profile.bindings)
            val expandedBank = HashMap<Int, Map<String, String>>()
            profile.bankBindings.forEach { (bank, map) ->
                val bankNumber = bank.toIntOrNull()
                if (bankNumber !in 1..bankCount) problems += "bankBindings key '$bank' is not a bank in 1..$bankCount"
                val concrete = expand("bank $bank binding ", map)
                if (bankNumber != null) expandedBank[bankNumber] = concrete
            }

            return CompiledController(profile, table, expanded, expandedBank, problems)
        }
    }
}

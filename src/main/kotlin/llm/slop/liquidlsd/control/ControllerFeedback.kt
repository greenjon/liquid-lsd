package llm.slop.liquidlsd.control

import llm.slop.liquidlsd.parameters.MeterType
import kotlin.math.roundToInt

/**
 * Keeps one controller's rings and LEDs in step with the app.
 *
 * Every hardware bank's knob n controls page knob n (the page follows the screen, not the bank), so
 * every bank is meant to show the same 16 lights. The Twister only reliably shows what was written to
 * the bank that is on screen (other banks fall back to their own stored colours), so lights go to the
 * *active* bank only: all banks while the active bank is still unknown, and the whole new bank
 * whenever it changes. Only changes are sent; the sink paces and coalesces what is sent.
 *
 * With [OutputConfig.native] the device is host-driven instead: no banks, no hue wheel and no ring
 * brightness. Each knob gets its ring style (from the parameter's meter type) and an RGB LED colour
 * as SysEx, sent only when they change and always before the ring position they apply to. Call
 * [enterNativeMode] when the device connects and [leaveNativeMode] when it is let go.
 */
class ControllerFeedback(private val compiled: CompiledController, private val sink: MidiSink) {
    private class Target(
        val bank: Int,
        val knob: Int,
        val ringChannel: Int,
        val ringCc: Int,
        val colorChannel: Int?,
        val colorCc: Int,
        val indicatorChannel: Int?
    ) {
        var lastRing = -1
        var lastColor = -1
        var lastBrightness = -1
        var lastStyle = -1
        var lastRgb = -1
        fun forget() { lastRing = -1; lastColor = -1; lastBrightness = -1; lastStyle = -1; lastRgb = -1 }
    }

    private val native: NativeModeDef? = compiled.profile.output.native
    private val nativeSysex: NativeSysex? = native?.let { NativeSysex.from(it) }
    private val wheel: HueWheel
    private val brightnessMin: Int
    private val brightnessMax: Int
    private val targets: List<Target>
    /** [targets] split by bank, so a frame only walks the active bank's. */
    private val targetsByBank: Array<Array<Target>>
    private val allTargets: Array<Target>
    private val knobSlots: Int
    private val scratchLights: Array<KnobLight?>
    // Per-knob ring/LED values computed at most once per update (several banks share a knob's light).
    private val ringValue: IntArray
    private val colorValue: IntArray
    private val brightnessValue: IntArray
    private val valueStamp: IntArray
    private var stamp = 0
    private var lastActiveBank: Int? = null
    private var hasUpdated = false

    init {
        val fb = compiled.profile.output.knobs
        val group = fb?.let { def -> compiled.profile.inputs.firstOrNull { it.id == def.input } }
        wheel = fb?.color ?: HueWheel()
        brightnessMin = fb?.indicatorBrightnessMin ?: 0
        brightnessMax = fb?.indicatorBrightnessMax ?: 0
        targets = if (fb == null || group == null) emptyList() else {
            val baseCcs = group.ccs.ifEmpty { (group.cc until group.cc + group.count).toList() }
            val banks = if (group.bankStride != 0) compiled.profile.banks.count.coerceAtLeast(1) else 1
            (0 until banks).flatMap { bank ->
                baseCcs.mapIndexed { knob, baseCc ->
                    val cc = baseCc + bank * group.bankStride
                    Target(bank, knob, fb.ringChannel ?: group.channel, cc, fb.colorChannel, cc, fb.indicatorChannel)
                }
            }
        }
    }

    init {
        val bankCount = (targets.maxOfOrNull { it.bank } ?: -1) + 1
        targetsByBank = Array(bankCount) { b -> targets.filter { it.bank == b }.toTypedArray() }
        allTargets = targets.toTypedArray()
        knobSlots = (targets.maxOfOrNull { it.knob } ?: -1) + 1
        scratchLights = arrayOfNulls(knobSlots)
        ringValue = IntArray(knobSlots)
        colorValue = IntArray(knobSlots)
        brightnessValue = IntArray(knobSlots)
        valueStamp = IntArray(knobSlots)
    }

    val isHealthy: Boolean get() = sink.isHealthy

    /**
     * Synchronizes the hardware controller's active bank with [activePageId] (from the app's UI).
     * If the profile defines bank switch outputs ([BankConfig.switch]) and pages ([BankConfig.pages]),
     * and [activePageId] maps to a different bank than the current active bank, sends a bank switch
     * CC message to the controller hardware and updates [runtime]'s active bank.
     */
    fun syncActiveBank(activePageId: String, runtime: ControllerRuntime? = null) {
        val switchDef = compiled.profile.banks.switch ?: return
        val pages = compiled.profile.banks.pages
        if (pages.isEmpty()) return

        val normalizedActive = activePageId.removePrefix("perform.")
        val targetBank = pages.indexOfFirst { page ->
            page == activePageId || page.removePrefix("perform.") == normalizedActive
        }
        if (targetBank < 0 || targetBank >= compiled.profile.banks.count) return

        val currentBank = runtime?.activeBank ?: lastActiveBank
        if (currentBank != null && currentBank != targetBank) {
            sink.sendCc(switchDef.channel, switchDef.cc + targetBank, 127)
            runtime?.activeBank = targetBank
            lastActiveBank = targetBank
            for (t in targetsByBank.getOrNull(targetBank) ?: emptyArray()) {
                t.forget()
            }
        }
    }

    /**
     * Sends what differs from the last send for [lights] (index = knob, null = nothing there).
     * [activeBank] is the device's 0-based bank if known; a bank change rewrites the whole new bank.
     */
    fun update(lights: List<KnobLight?>, activeBank: Int? = null) {
        if (targets.isEmpty()) return
        for (i in scratchLights.indices) scratchLights[i] = lights.getOrNull(i)
        update(scratchLights, activeBank)
    }

    /** As [update] for a reusable buffer (index = knob; knobs past its end count as null). Allocation-free. */
    fun update(lights: Array<KnobLight?>, activeBank: Int? = null) {
        if (targets.isEmpty()) return
        val active = if (activeBank == null) allTargets else targetsByBank.getOrNull(activeBank) ?: return

        val bankChanged = hasUpdated && activeBank != lastActiveBank
        hasUpdated = true
        lastActiveBank = activeBank
        if (bankChanged) for (t in active) t.forget()

        stamp++
        for (t in active) send(t, lights)
    }

    /** Switches the device into native mode (no-op without [OutputConfig.native]) and forgets what was sent, so the next [update] rewrites everything. */
    fun enterNativeMode() {
        val sysex = nativeSysex ?: return
        sink.sendSysex(sysex.enter())
        resync()
    }

    /** Returns the device to its stock behaviour (no-op without [OutputConfig.native]). */
    fun leaveNativeMode() {
        val sysex = nativeSysex ?: return
        sink.sendSysex(sysex.leave())
        resync()
    }

    private fun sendNative(t: Target, light: KnobLight?, native: NativeModeDef, sysex: NativeSysex) {
        val style = native.styleFor(light?.meterType ?: MeterType.MONOPOLAR)
        val styleKey = style.type.code or ((if (style.detent) 1 else 0) shl 1) or (style.detentColor.coerceIn(0, 127) shl 2)
        if (styleKey != t.lastStyle) {
            t.lastStyle = styleKey
            sink.sendSysex(sysex.indicator(t.knob, style))
        }
        val lit = light != null && light.lit
        val r = if (lit) (light!!.r.coerceIn(0f, 1f) * 127f).roundToInt() else 0
        val g = if (lit) (light!!.g.coerceIn(0f, 1f) * 127f).roundToInt() else 0
        val b = if (lit) (light!!.b.coerceIn(0f, 1f) * 127f).roundToInt() else 0
        val rgb = (r shl 14) or (g shl 7) or b
        if (rgb != t.lastRgb) {
            t.lastRgb = rgb
            sink.sendSysex(sysex.color(t.knob, r, g, b))
        }
        val ring = ((light?.value ?: 0f).coerceIn(0f, 1f) * 127f).roundToInt()
        if (ring != t.lastRing) {
            t.lastRing = ring
            sink.sendCc(t.ringChannel, t.ringCc, ring)
        }
    }

    /** Sends [t]'s ring and LED, on the encoder's own (per-bank) CC numbers, if they changed. */
    private fun send(t: Target, lights: Array<KnobLight?>) {
        val knob = t.knob
        if (native != null && nativeSysex != null) {
            sendNative(t, if (knob < lights.size) lights[knob] else null, native, nativeSysex)
            return
        }
        if (valueStamp[knob] != stamp) {
            valueStamp[knob] = stamp
            val light = if (knob < lights.size) lights[knob] else null
            ringValue[knob] = ((light?.value ?: 0f).coerceIn(0f, 1f) * 127f).roundToInt()
            colorValue[knob] = if (light == null || !light.lit) wheel.off else wheel.valueFor(light.r, light.g, light.b)
            val level = (light?.ringBrightness ?: 1f).coerceIn(0f, 1f)
            brightnessValue[knob] = brightnessMin + (level * (brightnessMax - brightnessMin)).roundToInt()
        }
        val ring = ringValue[knob]
        if (ring != t.lastRing) {
            t.lastRing = ring
            sink.sendCc(t.ringChannel, t.ringCc, ring)
        }
        t.indicatorChannel?.let { channel ->
            val brightness = brightnessValue[knob]
            if (brightness != t.lastBrightness) {
                t.lastBrightness = brightness
                sink.sendCc(channel, t.ringCc, brightness)
            }
        }
        val colorChannel = t.colorChannel ?: return
        val color = colorValue[knob]
        if (color != t.lastColor) {
            t.lastColor = color
            sink.sendCc(colorChannel, t.colorCc, color)
        }
    }

    /** Forgets what was sent, so the next [update] rewrites every ring and LED (new connection, device reset). */
    fun resync() {
        for (t in allTargets) t.forget()
    }

    fun close() = sink.close()
}

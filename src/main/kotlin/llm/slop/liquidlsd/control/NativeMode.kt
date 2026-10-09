package llm.slop.liquidlsd.control

import kotlinx.serialization.Serializable
import llm.slop.liquidlsd.parameters.MeterType

/** Ring drawing style of a knob in a controller's native mode (hardware-verified: dot and bar only). */
@Serializable
enum class IndicatorType(val code: Int) { DOT(0), BAR(1) }

/** One knob's ring style; [detentColor] is 0..127 (red..blue) and only matters when [detent] is set. */
@Serializable
data class IndicatorStyle(val type: IndicatorType, val detent: Boolean = false, val detentColor: Int = 127)

/**
 * A controller's host-driven native mode (Midi Fighter Twister XT firmware): the app switches the device
 * into it on connect and out of it on exit. The ring style follows the bound parameter's [MeterType].
 * [header] is the hex of the SysEx bytes between F0 and the command (manufacturer id and device byte).
 * Native mode has no hardware banks, so use [BankConfig.virtual] pages with it.
 */
@Serializable
data class NativeModeDef(
    val header: String = "00 01 79 05",
    val indicatorStyles: Map<MeterType, IndicatorStyle> = mapOf(
        MeterType.MONOPOLAR to IndicatorStyle(IndicatorType.BAR),
        MeterType.BIPOLAR to IndicatorStyle(IndicatorType.BAR, detent = true),
        MeterType.ENDLESS to IndicatorStyle(IndicatorType.DOT),
        MeterType.DISCRETE to IndicatorStyle(IndicatorType.DOT)
    )
) {
    /** The header bytes, or null if [header] isn't 1+ hex bytes of 0..7F. */
    fun headerBytes(): ByteArray? {
        val parts = header.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val bytes = parts.map { it.toIntOrNull(16) ?: return null }
        return if (bytes.all { it in 0..0x7F }) ByteArray(bytes.size) { bytes[it].toByte() } else null
    }

    /** The style for [meter]; unlisted types fall back to a plain bar. */
    fun styleFor(meter: MeterType): IndicatorStyle = indicatorStyles[meter] ?: IndicatorStyle(IndicatorType.BAR)
}

/**
 * Builds the SysEx messages of a controller's native mode (Midi Fighter Twister XT firmware). Each
 * message carries exactly one setting: hardware showed that several configs in one SysEx are
 * misparsed, so callers send these one after another.
 */
class NativeSysex(private val header: ByteArray = TWISTER_HEADER) {
    fun enter(): ByteArray = message(0x00, 0x01)

    fun leave(): ByteArray = message(0x00, 0x00)

    fun indicator(knob: Int, style: IndicatorStyle): ByteArray = message(
        0x01, 0x00, knob.coerceIn(0, 15), style.type.code, if (style.detent) 1 else 0, style.detentColor.coerceIn(0, 127)
    )

    /** Switch LED colour; each channel is 0..127 (7 bits). */
    fun color(knob: Int, r: Int, g: Int, b: Int): ByteArray = message(
        0x01, 0x01, knob.coerceIn(0, 15), r.coerceIn(0, 127), g.coerceIn(0, 127), b.coerceIn(0, 127)
    )

    private fun message(vararg body: Int): ByteArray =
        byteArrayOf(0xF0.toByte()) + header + ByteArray(body.size) { body[it].toByte() } + byteArrayOf(0xF7.toByte())

    companion object {
        /** A builder for [def], or null if its header is invalid (caught by profile validation). */
        fun from(def: NativeModeDef): NativeSysex? = def.headerBytes()?.let { NativeSysex(it) }

        /** Manufacturer id 00 01 79 and the native-mode command byte 05. */
        val TWISTER_HEADER = byteArrayOf(0x00, 0x01, 0x79, 0x05)
    }
}

package llm.slop.liquidlsd.macro

import mu.KotlinLogging
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Open Sound Control (OSC) bridge and protocol specification for Macro Controls.
 *
 * - Inbound address routing, namespaced per canonical deck/mixer bank
 *   ([MacroEngine.CANONICAL_BANK_IDS], e.g. "deckA"):
 *     `/macro/<bankId>/knob/1`..`/macro/<bankId>/knob/8` (Float [0.0..1.0])
 * - Outbound feedback dispatch: keeps external surfaces (e.g. TouchOSC on tablets)
 *   in bidirectional sync with knob values.
 */
object MacroOscBridge {
    private val logger = KotlinLogging.logger {}

    fun interface MacroFeedbackListener {
        fun onMacroChanged(address: String, value: Float)
    }

    private val listeners = CopyOnWriteArrayList<MacroFeedbackListener>()

    fun addListener(listener: MacroFeedbackListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: MacroFeedbackListener) {
        listeners.remove(listener)
    }

    /**
     * Broadcasts a macro value update to all registered OSC listeners (e.g. tablet clients).
     */
    fun broadcast(address: String, value: Float) {
        for (listener in listeners) {
            try {
                listener.onMacroChanged(address, value)
            } catch (e: Exception) {
                logger.error(e) { "Error in MacroFeedbackListener for address $address" }
            }
        }
    }

    /**
     * Handles an incoming OSC message.
     *
     * @param address e.g. "/macro/deckA/knob/1" or "/macro/trans/switch/2"
     * @param value normalized float argument
     * @return true if the address was matched and handled
     */
    fun handleOscMessage(address: String, value: Float): Boolean {
        if (!address.startsWith("/macro/")) return false
        val rest = address.removePrefix("/macro/")
        val parts = rest.split("/")
        if (parts.size != 3) return false
        val (bankId, slotType, numStr) = parts
        val bank = MacroEngine.getBank(bankId) ?: return false
        val num = numStr.toIntOrNull() ?: return false
        val idx = num - 1

        if (slotType == "knob") {
            val knob = bank.knobs.getOrNull(idx) ?: return false
            val clamped = value.coerceIn(0f, 1f)
            knob.value = clamped
            broadcast(address, clamped)
            return true
        }

        return false
    }

    /** Returns the canonical OSC address for a knob index (0..7) within [bankId]. */
    fun getKnobAddress(bankId: String, index: Int): String = "/macro/$bankId/knob/${index + 1}"
}

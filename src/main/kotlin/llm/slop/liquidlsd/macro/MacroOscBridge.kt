package llm.slop.liquidlsd.macro

import mu.KotlinLogging
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Open Sound Control (OSC) bridge and protocol specification for Macro Controls.
 *
 * Implements proposal §5.1:
 * - Inbound address routing:
 *     `/macro/knob/1`..`/macro/knob/8` (Float [0.0..1.0])
 *     `/macro/switch/1`..`/macro/switch/4` (Float [0.0 or 1.0])
 * - Outbound feedback dispatch: keeps external surfaces (e.g. TouchOSC on tablets)
 *   in bidirectional sync with knob/switch values.
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
     * @param address e.g. "/macro/knob/1" or "/macro/switch/2"
     * @param value normalized float argument
     * @return true if the address was matched and handled
     */
    fun handleOscMessage(address: String, value: Float): Boolean {
        val bank = MacroEngine.globalBank()

        if (address.startsWith("/macro/knob/")) {
            val num = address.removePrefix("/macro/knob/").toIntOrNull() ?: return false
            val idx = num - 1
            val knob = bank.knobs.getOrNull(idx) ?: return false
            val clamped = value.coerceIn(0f, 1f)
            knob.value = clamped
            broadcast(address, clamped)
            return true
        }

        if (address.startsWith("/macro/switch/")) {
            val num = address.removePrefix("/macro/switch/").toIntOrNull() ?: return false
            val idx = num - 1
            val switch = bank.switches.getOrNull(idx) ?: return false
            if (value >= 0.5f) {
                switch.onPress()
            } else {
                switch.onRelease()
            }
            broadcast(address, switch.value)
            return true
        }

        return false
    }

    /** Returns the canonical OSC address for a knob index (0..7). */
    fun getKnobAddress(index: Int): String = "/macro/knob/${index + 1}"

    /** Returns the canonical OSC address for a switch index (0..3). */
    fun getSwitchAddress(index: Int): String = "/macro/switch/${index + 1}"
}

package llm.slop.liquidlsd.rack

import java.util.UUID

enum class PortDirection {
    INPUT,
    OUTPUT
}

enum class SignalType {
    VIDEO,
    CV,
    MASK
}

/**
 * Metadata defining a single patchable rear terminal on a [RackUnit].
 */
data class PatchPort(
    val unitId: String,
    val portId: String,
    val label: String,
    val direction: PortDirection,
    val signalType: SignalType = SignalType.VIDEO
) {
    val fullId: String get() = "$unitId:$portId"
}

/**
 * A virtual patch cable connecting a source output port to a destination input port.
 */
data class PatchCable(
    val id: String = UUID.randomUUID().toString().take(8),
    val fromPort: PatchPort,
    val toPort: PatchPort,
    val colorHex: Long = DEFAULT_CABLE_COLORS.random()
) {
    companion object {
        // High-contrast, vibrant cable palette (Neon Cyan, Electric Amber, Hot Pink, Acid Green, Violet, Bright Orange)
        val DEFAULT_CABLE_COLORS = listOf(
            0xFF00E5FF, // Neon Cyan
            0xFFFFD700, // Electric Gold/Amber
            0xFFFF1493, // Deep Pink
            0xFF00FF66, // Acid Green
            0xFFB026FF, // Neon Violet
            0xFFFF6600  // Safety Orange
        )
    }
}

/**
 * Manages virtual patch cabling and signal routing overrides across the Modular Video Rack bay.
 */
class RackPatchBay {

    private val cables = mutableListOf<PatchCable>()

    /** Returns all active patch cables. */
    fun getCables(): List<PatchCable> = cables

    /**
     * Finds the active cable connected to a given port, if any.
     */
    fun findCableForPort(fullPortId: String): PatchCable? {
        return cables.find { it.fromPort.fullId == fullPortId || it.toPort.fullId == fullPortId }
    }

    /**
     * Finds any cable plugged into a given destination input port.
     */
    fun findCableInputFor(unitId: String, portId: String): PatchCable? {
        val target = "$unitId:$portId"
        return cables.find { it.toPort.fullId == target }
    }

    /**
     * Connects an output port to an input port.
     * Enforces direction checks and replaces any existing cable on the target input port.
     *
     * @return Created [PatchCable] or null if invalid connection.
     */
    fun connect(
        from: PatchPort,
        to: PatchPort,
        colorHex: Long? = null
    ): PatchCable? {
        // Validation: must connect OUTPUT to INPUT
        if (from.direction != PortDirection.OUTPUT || to.direction != PortDirection.INPUT) {
            return null
        }

        // Avoid connecting ports on the same unit
        if (from.unitId == to.unitId) {
            return null
        }

        // Remove any existing cable connected to the destination input port (single input source)
        disconnectInput(to.unitId, to.portId)

        val color = colorHex ?: PatchCable.DEFAULT_CABLE_COLORS[cables.size % PatchCable.DEFAULT_CABLE_COLORS.size]
        val cable = PatchCable(
            fromPort = from,
            toPort = to,
            colorHex = color
        )
        cables.add(cable)
        return cable
    }

    /**
     * Disconnects and removes a specific cable.
     */
    fun disconnect(cableId: String): Boolean {
        return cables.removeIf { it.id == cableId }
    }

    /**
     * Disconnects any cable plugged into a specific input port.
     */
    fun disconnectInput(unitId: String, portId: String): Boolean {
        val target = "$unitId:$portId"
        return cables.removeIf { it.toPort.fullId == target }
    }

    /**
     * Disconnects any cables plugged into any port on the specified unit.
     */
    fun removeUnitConnections(unitId: String) {
        cables.removeIf { it.fromPort.unitId == unitId || it.toPort.unitId == unitId }
    }

    /**
     * Clears all patch cables, resetting the entire bay back to default normalled flow.
     */
    fun clearAll() {
        cables.clear()
    }
}

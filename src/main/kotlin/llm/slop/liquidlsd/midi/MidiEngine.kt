package llm.slop.liquidlsd.midi

import javax.sound.midi.*
import mu.KotlinLogging
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicIntegerArray

@Serializable
enum class MidiMessageType {
    CC,
    NOTE,
    PITCH_BEND
}

data class MidiEvent(
    val channel: Int,           // 0..15
    val type: MidiMessageType,  // CC, NOTE, PITCH_BEND
    val index: Int,             // CC# (0..127), Note# (0..127), or 0 for Pitch Bend
    val rawValue: Int,          // 0..127, or 0..16383 for Pitch Bend
    val normalizedValue: Float, // 0.0..1.0, or -1.0..1.0 for Pitch Bend
    val timestampMs: Long = System.currentTimeMillis()
)

object MidiEngine {
    private val logger = KotlinLogging.logger {}

    // 128 CCs across 16 channels stored as float bit-patterns in an AtomicIntegerArray.
    // Using atomic storage prevents data races between the MIDI receiver thread (writer)
    // and the render thread (reader) without requiring any locking.
    private val ccValues = AtomicIntegerArray(16 * 128)
    private val noteValues = AtomicIntegerArray(16 * 128)
    private val pitchBendValues = AtomicIntegerArray(16) // 1 per channel

    // Thread-safe queue to pass typed MIDI events to the main render thread
    val receivedEvents = ConcurrentLinkedQueue<MidiEvent>()
    // Maintained for backward compatibility
    val receivedCcEvents = ConcurrentLinkedQueue<Pair<Int, Int>>()

    // Thread-safe circular buffer for live monitoring / sniffer UI
    private const val MAX_RECENT_EVENTS = 32
    private val recentEventsLock = Any()
    private val recentEventsList = java.util.ArrayDeque<MidiEvent>(MAX_RECENT_EVENTS)

    private val openDevices = mutableListOf<MidiDevice>()

    init {
        try {
            if (llm.slop.liquidlsd.ui.UITheme.midiEnabled) {
                initialize()
            }
        } catch (e: Throwable) {
            logger.error(e) { "Failed to initialize MidiEngine" }
        }
    }

    private fun initialize() {
        synchronized(openDevices) {
            val infos = try {
                MidiSystem.getMidiDeviceInfo()
            } catch (e: Throwable) {
                logger.error(e) { "Failed to query MIDI device info during init" }
                emptyArray()
            }
            logger.info { "Found ${infos.size} MIDI devices" }
            for (info in infos) {
                try {
                    val device = MidiSystem.getMidiDevice(info)
                    // We want input devices (which have transmitters)
                    if (device.maxTransmitters != 0) {
                        device.open()
                        val transmitter = device.transmitter
                        transmitter.receiver = MidiInputReceiver()
                        openDevices.add(device)
                        logger.info { "Successfully opened MIDI input device: ${info.name} - ${info.description}" }
                    }
                } catch (e: Throwable) {
                    logger.warn { "Could not open MIDI device: ${info.name}. Error: ${e.message}" }
                }
            }
        }
    }

    /**
     * Periodically called by a background watchdog thread to:
     * 1. Remove disconnected/closed MIDI devices
     * 2. Probe for newly plugged-in MIDI controllers and open them
     */
    fun scanForNewDevices() {
        if (!llm.slop.liquidlsd.ui.UITheme.midiEnabled) return
        synchronized(openDevices) {
            // 1. Clean up dead or closed devices
            val iterator = openDevices.iterator()
            while (iterator.hasNext()) {
                val dev = iterator.next()
                try {
                    if (!dev.isOpen) {
                        logger.info { "Removing inactive MIDI device: ${dev.deviceInfo.name}" }
                        try { dev.close() } catch (e: Throwable) {}
                        iterator.remove()
                    }
                } catch (e: Throwable) {
                    try { dev.close() } catch (_: Throwable) {}
                    iterator.remove()
                }
            }

            // 2. Scan for newly plugged-in devices
            val infos = try {
                MidiSystem.getMidiDeviceInfo()
            } catch (e: Throwable) {
                logger.error(e) { "Failed to query MIDI device info" }
                emptyArray()
            }

            for (info in infos) {
                // Check if this device is already opened
                val alreadyOpen = openDevices.any { 
                    try {
                        it.deviceInfo.name == info.name && it.deviceInfo.description == info.description 
                    } catch (e: Throwable) {
                        false
                    }
                }
                if (alreadyOpen) continue

                try {
                    val device = MidiSystem.getMidiDevice(info)
                    // We only want input devices (which have transmitters)
                    if (device.maxTransmitters != 0) {
                        device.open()
                        val transmitter = device.transmitter
                        transmitter.receiver = MidiInputReceiver()
                        openDevices.add(device)
                        logger.info { "Successfully opened newly detected MIDI input device: ${info.name} - ${info.description}" }
                    }
                } catch (e: Throwable) {
                    // Log at debug so as not to spam warnings if a device is locked by another app
                    logger.debug { "Could not open newly detected MIDI device: ${info.name}. Error: ${e.message}" }
                }
            }
        }
    }

    fun getActiveDeviceCount(): Int {
        if (!llm.slop.liquidlsd.ui.UITheme.midiEnabled) return 0
        return synchronized(openDevices) {
            openDevices.size
        }
    }

    fun getConnectedDeviceNames(): List<String> {
        if (!llm.slop.liquidlsd.ui.UITheme.midiEnabled) return emptyList()
        return synchronized(openDevices) {
            openDevices.map { 
                try { it.deviceInfo.name ?: "Unknown" } catch (e: Throwable) { "Unknown" }
            }
        }
    }

    fun getCcValue(channel: Int, cc: Int): Float {
        if (!llm.slop.liquidlsd.ui.UITheme.midiEnabled) return 0.0f
        val idx = (channel.coerceIn(0, 15) * 128) + cc.coerceIn(0, 127)
        return Float.fromBits(ccValues.get(idx))
    }

    fun getNoteValue(channel: Int, note: Int): Float {
        if (!llm.slop.liquidlsd.ui.UITheme.midiEnabled) return 0.0f
        val idx = (channel.coerceIn(0, 15) * 128) + note.coerceIn(0, 127)
        return Float.fromBits(noteValues.get(idx))
    }

    fun getPitchBendValue(channel: Int): Float {
        if (!llm.slop.liquidlsd.ui.UITheme.midiEnabled) return 0.0f
        return Float.fromBits(pitchBendValues.get(channel.coerceIn(0, 15)))
    }

    fun getNormalizedValue(channel: Int, type: MidiMessageType, index: Int): Float {
        return when (type) {
            MidiMessageType.CC -> getCcValue(channel, index)
            MidiMessageType.NOTE -> getNoteValue(channel, index)
            MidiMessageType.PITCH_BEND -> getPitchBendValue(channel)
        }
    }

    fun getRecentEvents(): List<MidiEvent> {
        synchronized(recentEventsLock) {
            return recentEventsList.toList()
        }
    }

    private fun recordRecentEvent(event: MidiEvent) {
        synchronized(recentEventsLock) {
            if (recentEventsList.size >= MAX_RECENT_EVENTS) {
                recentEventsList.removeFirst()
            }
            recentEventsList.addLast(event)
        }
    }

    fun close() {
        logger.info { "Closing MidiEngine..." }
        synchronized(openDevices) {
            for (device in openDevices) {
                try {
                    if (device.isOpen) {
                        device.close()
                    }
                } catch (e: Throwable) {
                    logger.error(e) { "Error closing MIDI device" }
                }
            }
            openDevices.clear()
        }
        receivedEvents.clear()
        receivedCcEvents.clear()
        synchronized(recentEventsLock) {
            recentEventsList.clear()
        }
    }

    private class MidiInputReceiver : Receiver {
        override fun send(message: MidiMessage?, timeStamp: Long) {
            if (message !is ShortMessage) return
            val channel = message.channel.coerceIn(0, 15)

            when (message.command) {
                ShortMessage.CONTROL_CHANGE -> {
                    val cc = message.data1.coerceIn(0, 127)
                    val rawVal = message.data2.coerceIn(0, 127)
                    val normalizedValue = rawVal.toFloat() / 127.0f

                    val idx = (channel * 128) + cc
                    ccValues.set(idx, normalizedValue.toBits())

                    val event = MidiEvent(channel, MidiMessageType.CC, cc, rawVal, normalizedValue)
                    receivedEvents.offer(event)
                    receivedCcEvents.offer(channel to cc)
                    recordRecentEvent(event)
                }
                ShortMessage.NOTE_ON -> {
                    val note = message.data1.coerceIn(0, 127)
                    val velocity = message.data2.coerceIn(0, 127)
                    val normalizedValue = if (velocity > 0) velocity.toFloat() / 127.0f else 0.0f

                    val idx = (channel * 128) + note
                    noteValues.set(idx, normalizedValue.toBits())

                    val event = MidiEvent(channel, MidiMessageType.NOTE, note, velocity, normalizedValue)
                    receivedEvents.offer(event)
                    recordRecentEvent(event)
                }
                ShortMessage.NOTE_OFF -> {
                    val note = message.data1.coerceIn(0, 127)
                    val velocity = message.data2.coerceIn(0, 127)

                    val idx = (channel * 128) + note
                    noteValues.set(idx, 0.0f.toBits())

                    val event = MidiEvent(channel, MidiMessageType.NOTE, note, 0, 0.0f)
                    receivedEvents.offer(event)
                    recordRecentEvent(event)
                }
                ShortMessage.PITCH_BEND -> {
                    val lsb = message.data1.coerceIn(0, 127)
                    val msb = message.data2.coerceIn(0, 127)
                    val raw = (msb shl 7) or lsb // 0..16383, center 8192
                    val normalizedValue = ((raw - 8192).toFloat() / 8192.0f).coerceIn(-1.0f, 1.0f)

                    pitchBendValues.set(channel, normalizedValue.toBits())

                    val event = MidiEvent(channel, MidiMessageType.PITCH_BEND, 0, raw, normalizedValue)
                    receivedEvents.offer(event)
                    recordRecentEvent(event)
                }
            }
        }

        override fun close() {
            // No-op
        }
    }
}

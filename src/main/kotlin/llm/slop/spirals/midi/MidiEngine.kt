package llm.slop.spirals.midi

import javax.sound.midi.*
import mu.KotlinLogging
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicIntegerArray

object MidiEngine {
    private val logger = KotlinLogging.logger {}

    // 128 CCs across 16 channels stored as float bit-patterns in an AtomicIntegerArray.
    // Using atomic storage prevents data races between the MIDI receiver thread (writer)
    // and the render thread (reader) without requiring any locking.
    private val ccValues = AtomicIntegerArray(16 * 128)

    // Callback hook for MIDI Learn Mode — REMOVED.
    // All MIDI events (including learn events) are routed through the thread-safe
    // receivedCcEvents queue below and processed on the render thread by UIManager.

    // Thread-safe queue to pass MIDI events to the main render thread
    val receivedCcEvents = ConcurrentLinkedQueue<Pair<Int, Int>>()

    private val openDevices = mutableListOf<MidiDevice>()

    init {
        try {
            initialize()
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize MidiEngine" }
        }
    }

    private fun initialize() {
        val infos = MidiSystem.getMidiDeviceInfo()
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
            } catch (e: Exception) {
                logger.warn { "Could not open MIDI device: ${info.name}. Error: ${e.message}" }
            }
        }
    }

    /**
     * Periodically called by a background watchdog thread to:
     * 1. Remove disconnected/closed MIDI devices
     * 2. Probe for newly plugged-in MIDI controllers and open them
     */
    fun scanForNewDevices() {
        synchronized(openDevices) {
            // 1. Clean up dead or closed devices
            val iterator = openDevices.iterator()
            while (iterator.hasNext()) {
                val dev = iterator.next()
                if (!dev.isOpen) {
                    logger.info { "Removing inactive MIDI device: ${dev.deviceInfo.name}" }
                    try { dev.close() } catch (e: Exception) {}
                    iterator.remove()
                }
            }

            // 2. Scan for newly plugged-in devices
            val infos = try {
                MidiSystem.getMidiDeviceInfo()
            } catch (e: Exception) {
                logger.error(e) { "Failed to query MIDI device info" }
                emptyArray()
            }

            for (info in infos) {
                // Check if this device is already opened
                val alreadyOpen = openDevices.any { 
                    it.deviceInfo.name == info.name && it.deviceInfo.description == info.description 
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
                } catch (e: Exception) {
                    // Log at debug so as not to spam warnings if a device is locked by another app
                    logger.debug { "Could not open newly detected MIDI device: ${info.name}. Error: ${e.message}" }
                }
            }
        }
    }

    fun getActiveDeviceCount(): Int {
        return synchronized(openDevices) {
            openDevices.size
        }
    }

    fun getCcValue(channel: Int, cc: Int): Float {
        val idx = (channel.coerceIn(0, 15) * 128) + cc.coerceIn(0, 127)
        return Float.fromBits(ccValues.get(idx))
    }

    fun close() {
        logger.info { "Closing MidiEngine..." }
        synchronized(openDevices) {
            for (device in openDevices) {
                try {
                    if (device.isOpen) {
                        device.close()
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error closing MIDI device: ${device.deviceInfo.name}" }
                }
            }
            openDevices.clear()
        }
    }

    private class MidiInputReceiver : Receiver {
        override fun send(message: MidiMessage?, timeStamp: Long) {
            if (message is ShortMessage) {
                if (message.command == ShortMessage.CONTROL_CHANGE) {
                    val channel = message.channel // 0-15
                    val cc = message.data1 // CC number (0-127)
                    val value = message.data2 // Value (0-127)
                    val normalizedValue = value.toFloat() / 127.0f

                    val idx = (channel * 128) + cc
                    if (idx in 0 until ccValues.length()) {
                        ccValues.set(idx, normalizedValue.toBits())
                    }

                    // Queue event for main thread polling
                    receivedCcEvents.offer(channel to cc)

                    // MIDI Learn routing: the render thread drains receivedCcEvents
                    // each frame (in UIManager.render) and handles all state mutations
                    // there. No direct callback into render-thread state from here.
                }
            }
        }

        override fun close() {
            // No-op
        }
    }
}

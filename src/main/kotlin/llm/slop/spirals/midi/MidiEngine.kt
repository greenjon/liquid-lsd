package llm.slop.spirals.midi

import javax.sound.midi.*
import mu.KotlinLogging
import java.util.concurrent.ConcurrentLinkedQueue

object MidiEngine {
    private val logger = KotlinLogging.logger {}

    // 128 CCs across 16 channels (0.0f - 1.0f)
    private val ccValues = FloatArray(16 * 128)

    // Callback hook for MIDI Learn Mode
    @Volatile
    var onMidiCcReceived: ((channel: Int, cc: Int) -> Unit)? = null

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

    fun getCcValue(channel: Int, cc: Int): Float {
        val idx = (channel.coerceIn(0, 15) * 128) + cc.coerceIn(0, 127)
        return ccValues[idx]
    }

    fun close() {
        logger.info { "Closing MidiEngine..." }
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

    private class MidiInputReceiver : Receiver {
        override fun send(message: MidiMessage?, timeStamp: Long) {
            if (message is ShortMessage) {
                if (message.command == ShortMessage.CONTROL_CHANGE) {
                    val channel = message.channel // 0-15
                    val cc = message.data1 // CC number (0-127)
                    val value = message.data2 // Value (0-127)
                    val normalizedValue = value.toFloat() / 127.0f

                    val idx = (channel * 128) + cc
                    if (idx in ccValues.indices) {
                        ccValues[idx] = normalizedValue
                    }

                    // Queue event for main thread polling
                    receivedCcEvents.offer(channel to cc)

                    // Trigger callback for MIDI Learn Mode
                    onMidiCcReceived?.invoke(channel, cc)
                }
            }
        }

        override fun close() {
            // No-op
        }
    }
}

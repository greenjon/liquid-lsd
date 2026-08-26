package llm.slop.liquidlsd.audio

import java.nio.FloatBuffer
import javax.sound.sampled.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Fallback audio capture client that uses the Java Sound API (TargetDataLine).
 * This works natively on macOS, Windows, and Linux without native JACK/PipeWire dependencies.
 */
class JavaSoundClient(
    val deviceName: String? = null,
    val onProcess: (FloatBuffer, Int, Float) -> Unit // (buffer, nframes, sampleRate)
) {
    @Volatile
    var isConnected = false
        private set

    private var line: TargetDataLine? = null
    private var thread: Thread? = null
    @Volatile
    private var running = false

    /**
     * Starts audio capture from the system's default or selected input line.
     */
    fun start(): Boolean {
        try {
            logger.info { "Starting Java Sound Audio client (device: ${deviceName ?: "Default"})..." }
            val format = AudioFormat(44100f, 16, 1, true, false) // 44.1kHz, 16-bit, Mono, Signed, Little-Endian
            val info = DataLine.Info(TargetDataLine::class.java, format)
            
            val targetLine = findTargetDataLine(info, deviceName) ?: run {
                // Try 48kHz if 44.1kHz is not supported
                val altFormat = AudioFormat(48000f, 16, 1, true, false)
                val altInfo = DataLine.Info(TargetDataLine::class.java, altFormat)
                findTargetDataLine(altInfo, deviceName)
            }

            if (targetLine == null) {
                logger.warn { "No supported audio input TargetDataLine found for device: ${deviceName ?: "Default"}." }
                return false
            }

            val bufferSize = 512 // 512 samples per read chunk (approx. 11.6ms at 44.1kHz)
            try {
                targetLine.open(targetLine.format, bufferSize * 2) // buffer size in bytes
            } catch (e: LineUnavailableException) {
                logger.warn { "Failed to open TargetDataLine with buffer size ${bufferSize * 2}: ${e.message}. Trying default buffer size." }
                targetLine.open(targetLine.format)
            }
            
            targetLine.start()
            line = targetLine
            isConnected = true
            running = true

            val sampleRate = targetLine.format.sampleRate

            thread = Thread({
                val byteBuffer = ByteArray(bufferSize * 2)
                val floatArray = FloatArray(bufferSize)
                val floatBuffer = FloatBuffer.wrap(floatArray)

                try {
                    while (running) {
                        val currentLine = line ?: break
                        val bytesRead = currentLine.read(byteBuffer, 0, byteBuffer.size)
                        if (bytesRead <= 0) continue

                        val samplesRead = convertPcmToFloat(byteBuffer, bytesRead, floatArray)

                        floatBuffer.position(0)
                        floatBuffer.limit(samplesRead)

                        onProcess(floatBuffer, samplesRead, sampleRate)
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error in Java Sound capture loop" }
                    isConnected = false
                }
            }, "JavaSoundClient-Capture").apply { isDaemon = true }

            thread?.start()
            logger.info { "Java Sound Audio client started successfully on ${targetLine.format.sampleRate}Hz." }
            return true
        } catch (e: Throwable) {
            logger.warn { "Failed to start Java Sound audio: ${e.message}" }
            stop()
            return false
        }
    }

    private fun findTargetDataLine(info: DataLine.Info, preferredDeviceName: String?): TargetDataLine? {
        if (preferredDeviceName == null || preferredDeviceName.equals("Default", ignoreCase = true)) {
            if (AudioSystem.isLineSupported(info)) {
                return AudioSystem.getLine(info) as? TargetDataLine
            }
        }

        val mixers = AudioSystem.getMixerInfo()
        for (mixerInfo in mixers) {
            if (preferredDeviceName != null && !mixerInfo.name.contains(preferredDeviceName, ignoreCase = true) && !mixerInfo.description.contains(preferredDeviceName, ignoreCase = true)) {
                continue
            }
            try {
                val mixer = AudioSystem.getMixer(mixerInfo)
                if (mixer.isLineSupported(info)) {
                    return mixer.getLine(info) as? TargetDataLine
                }
            } catch (e: Exception) {
                // Ignore incompatible mixers
            }
        }

        // Fallback to system default if preferred not found
        if (AudioSystem.isLineSupported(info)) {
            return AudioSystem.getLine(info) as? TargetDataLine
        }

        return null
    }

    /**
     * Stops capture and releases system resources.
     */
    fun stop() {
        running = false
        isConnected = false
        try {
            thread?.interrupt()
            thread = null
        } catch (e: Exception) {
            // Ignore
        }
        try {
            line?.stop()
            line?.close()
            line = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    companion object {
        /**
         * Returns list of available input devices from Java Sound mixers.
         */
        fun getAvailableInputDevices(): List<AudioInputDevice> {
            val devices = mutableListOf<AudioInputDevice>()
            devices.add(AudioInputDevice("default", "System Default", "Default system capture device", isDefault = true))

            val dummyFormat = AudioFormat(44100f, 16, 1, true, false)
            val info = DataLine.Info(TargetDataLine::class.java, dummyFormat)

            val mixers = AudioSystem.getMixerInfo()
            for ((index, mixerInfo) in mixers.withIndex()) {
                try {
                    val mixer = AudioSystem.getMixer(mixerInfo)
                    val targetLineInfos = mixer.targetLineInfo
                    if (targetLineInfos.isNotEmpty()) {
                        // Has input/capture lines
                        devices.add(
                            AudioInputDevice(
                                id = "javasound_$index",
                                name = mixerInfo.name,
                                description = "${mixerInfo.description} (${mixerInfo.vendor})"
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            return devices
        }

        /**
         * Converts 16-bit signed little-endian PCM byte data into floats in range [-1.0, 1.0].
         * Returns the number of samples successfully written to floatArray.
         */
        fun convertPcmToFloat(byteBuffer: ByteArray, bytesRead: Int, floatArray: FloatArray): Int {
            val samplesRead = bytesRead / 2
            val limit = minOf(samplesRead, floatArray.size)
            for (i in 0 until limit) {
                val low = byteBuffer[i * 2].toInt() and 0xff
                val high = byteBuffer[i * 2 + 1].toInt()
                val sample = ((high shl 8) or low).toShort()
                floatArray[i] = sample.toFloat() / 32768f
            }
            return limit
        }
    }
}

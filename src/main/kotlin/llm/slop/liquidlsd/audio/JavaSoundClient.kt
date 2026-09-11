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
    val onProcess: (FloatBuffer, FloatBuffer?, Int, Float) -> Unit // (leftBuffer, rightBuffer, nframes, sampleRate)
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
            
            // Prefer stereo (2 channels) at 44.1kHz, then 48kHz; fallback to mono (1 channel)
            val formatsToTry = listOf(
                AudioFormat(44100f, 16, 2, true, false),
                AudioFormat(48000f, 16, 2, true, false),
                AudioFormat(44100f, 16, 1, true, false),
                AudioFormat(48000f, 16, 1, true, false)
            )

            var targetLine: TargetDataLine? = null
            for (fmt in formatsToTry) {
                val info = DataLine.Info(TargetDataLine::class.java, fmt)
                targetLine = findTargetDataLine(info, deviceName)
                if (targetLine != null) {
                    logger.info { "Found compatible TargetDataLine: ${fmt.sampleRate}Hz, ${fmt.channels}ch" }
                    break
                }
            }

            if (targetLine == null) {
                logger.warn { "No supported audio input TargetDataLine found for device: ${deviceName ?: "Default"}." }
                return false
            }

            val channels = targetLine.format.channels
            val bufferFrames = 512 // 512 frames per read chunk (~11.6ms at 44.1kHz)
            val bytesPerFrame = channels * 2
            val bufferSizeBytes = bufferFrames * bytesPerFrame

            try {
                targetLine.open(targetLine.format, bufferSizeBytes * 2) // buffer size in bytes
            } catch (e: LineUnavailableException) {
                logger.warn { "Failed to open TargetDataLine with buffer size ${bufferSizeBytes * 2}: ${e.message}. Trying default buffer size." }
                targetLine.open(targetLine.format)
            }
            
            targetLine.start()
            line = targetLine
            isConnected = true
            running = true

            val sampleRate = targetLine.format.sampleRate

            thread = Thread({
                val byteBuffer = ByteArray(bufferSizeBytes)
                val leftArray = FloatArray(bufferFrames)
                val rightArray = FloatArray(bufferFrames)
                val leftBuffer = FloatBuffer.wrap(leftArray)
                val rightBuffer = FloatBuffer.wrap(rightArray)

                try {
                    while (running) {
                        val currentLine = line ?: break
                        val bytesRead = currentLine.read(byteBuffer, 0, byteBuffer.size)
                        if (bytesRead <= 0) continue

                        val framesRead = if (channels == 2) {
                            convertStereoPcmToFloat(byteBuffer, bytesRead, leftArray, rightArray)
                        } else {
                            val count = convertPcmToFloat(byteBuffer, bytesRead, leftArray)
                            for (i in 0 until count) {
                                rightArray[i] = leftArray[i]
                            }
                            count
                        }

                        leftBuffer.position(0)
                        leftBuffer.limit(framesRead)
                        rightBuffer.position(0)
                        rightBuffer.limit(framesRead)

                        onProcess(leftBuffer, rightBuffer, framesRead, sampleRate)
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error in Java Sound capture loop" }
                    isConnected = false
                }
            }, "JavaSoundClient-Capture").apply { isDaemon = true }

            thread?.start()
            logger.info { "Java Sound Audio client started successfully on ${targetLine.format.sampleRate}Hz (${channels}ch)." }
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

        /**
         * Converts 16-bit signed little-endian interleaved stereo PCM byte data into floats in range [-1.0, 1.0].
         * Writes channel 0 to leftArray and channel 1 to rightArray.
         * Returns the number of frames successfully written.
         */
        fun convertStereoPcmToFloat(byteBuffer: ByteArray, bytesRead: Int, leftArray: FloatArray, rightArray: FloatArray): Int {
            val framesRead = bytesRead / 4
            val limit = minOf(framesRead, leftArray.size, rightArray.size)
            for (i in 0 until limit) {
                val lowL = byteBuffer[i * 4].toInt() and 0xff
                val highL = byteBuffer[i * 4 + 1].toInt()
                val sampleL = ((highL shl 8) or lowL).toShort()
                leftArray[i] = sampleL.toFloat() / 32768f

                val lowR = byteBuffer[i * 4 + 2].toInt() and 0xff
                val highR = byteBuffer[i * 4 + 3].toInt()
                val sampleR = ((highR shl 8) or lowR).toShort()
                rightArray[i] = sampleR.toFloat() / 32768f
            }
            return limit
        }
    }
}

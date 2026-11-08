package llm.slop.liquidlsd.export

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Decoded raw PCM audio data in 32-bit floating point format [-1.0f, 1.0f].
 */
data class DecodedAudio(
    val samples: FloatArray,
    val sampleRate: Float,
    val channels: Int,
    val durationSeconds: Float
)

/**
 * Decodes audio files (WAV, MP3, FLAC, OGG, M4A, AAC, etc.) into mono float PCM arrays.
 * Uses FFmpeg CLI subprocess if available, with JavaSound WAV fallback.
 */
object AudioDecoder {

    /**
     * Attempts to decode an audio file into mono 44.1kHz 32-bit float samples.
     */
    fun decodeAudioFile(file: File, targetSampleRate: Float = 44100f): DecodedAudio {
        require(file.exists()) { "Audio file does not exist: ${file.absolutePath}" }

        // Try FFmpeg first for universal format support
        if (isFFmpegAvailable()) {
            try {
                return decodeWithFFmpeg(file, targetSampleRate)
            } catch (e: Exception) {
                logger.warn(e) { "FFmpeg audio decode failed, trying JavaSound fallback for ${file.name}" }
            }
        }

        // Fallback to JavaSound (mostly supports uncompressed WAV/AIFF)
        return decodeWithJavaSound(file)
    }

    fun isFFmpegAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("ffmpeg", "-version")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Decodes through a temp file so the samples are read once into an array of the exact size. Collecting them in
     * chunks and copying needed twice the memory: a two-hour mix (1.3 GB of samples) ran out of the 2 GB heap.
     */
    private fun decodeWithFFmpeg(file: File, targetSampleRate: Float): DecodedAudio {
        val tmp = File.createTempFile("lsd-decode-", ".f32")
        try {
            val pb = ProcessBuilder(
                "ffmpeg", "-nostdin",
                "-v", "error",
                "-y",
                "-i", file.absolutePath,
                "-f", "f32le",
                "-ac", "1", // Downmix to mono for CV/DSP analysis
                "-ar", targetSampleRate.toInt().toString(),
                tmp.absolutePath
            )
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            pb.start().waitFor()

            val count = tmp.length() / 4
            require(count <= Int.MAX_VALUE) { "${file.name} is too long to analyse (${count / targetSampleRate / 3600} h)" }
            val fullSamples = FloatArray(count.toInt())
            java.nio.channels.FileChannel.open(tmp.toPath()).use { ch ->
                val bb = ByteBuffer.allocateDirect(1 shl 20).order(ByteOrder.LITTLE_ENDIAN)
                var offset = 0
                while (offset < fullSamples.size) {
                    val r = ch.read(bb)
                    bb.flip()
                    val n = minOf(bb.remaining() / 4, fullSamples.size - offset)
                    bb.asFloatBuffer().get(fullSamples, offset, n)
                    bb.position(bb.position() + n * 4)
                    bb.compact()
                    offset += n
                    if (r < 0) break
                }
            }

            val duration = fullSamples.size / targetSampleRate
            logger.info { "Decoded ${file.name} with FFmpeg: ${fullSamples.size} samples, duration: ${"%.2f".format(duration)}s" }
            return DecodedAudio(fullSamples, targetSampleRate, 1, duration)
        } finally {
            tmp.delete()
        }
    }

    private fun decodeWithJavaSound(file: File): DecodedAudio {
        val inStream: AudioInputStream = AudioSystem.getAudioInputStream(file)
        val baseFormat = inStream.format
        val decodedFormat = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            baseFormat.sampleRate.takeIf { it > 0 } ?: 44100f,
            16,
            1, // mono
            2,
            baseFormat.sampleRate.takeIf { it > 0 } ?: 44100f,
            false // little-endian
        )

        val convertedStream = AudioSystem.getAudioInputStream(decodedFormat, inStream)
        val sampleRate = decodedFormat.sampleRate

        val buffer = ByteArray(4096)
        val floatList = mutableListOf<FloatArray>()
        var totalFloats = 0

        while (true) {
            val bytesRead = convertedStream.read(buffer)
            if (bytesRead <= 0) break
            val samplesCount = bytesRead / 2
            val chunk = FloatArray(samplesCount)
            for (i in 0 until samplesCount) {
                val low = buffer[i * 2].toInt() and 0xff
                val high = buffer[i * 2 + 1].toInt()
                val sample = ((high shl 8) or low).toShort()
                chunk[i] = sample.toFloat() / 32768f
            }
            floatList.add(chunk)
            totalFloats += samplesCount
        }
        convertedStream.close()

        val fullSamples = FloatArray(totalFloats)
        var offset = 0
        for (chunk in floatList) {
            System.arraycopy(chunk, 0, fullSamples, offset, chunk.size)
            offset += chunk.size
        }

        val duration = totalFloats / sampleRate
        logger.info { "Decoded ${file.name} with JavaSound: $totalFloats samples, duration: ${"%.2f".format(duration)}s" }
        return DecodedAudio(fullSamples, sampleRate, 1, duration)
    }
}

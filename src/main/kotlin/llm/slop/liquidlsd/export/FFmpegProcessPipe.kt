package llm.slop.liquidlsd.export

import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

enum class VideoCodec(val ffmpegCodecName: String, val extension: String) {
    H264("libx264", "mp4"),
    H265("libx265", "mp4"),
    PRORES("prores_ks", "mov")
}

enum class VideoResolutionPreset(val label: String, val width: Int, val height: Int) {
    HD_720P("720p (1280x720)", 1280, 720),
    FHD_1080P("1080p Full HD (1920x1080)", 1920, 1080),
    QHD_1440P("1440p 2K (2560x1440)", 2560, 1440),
    UHD_4K("4K UHD (3840x2160)", 3840, 2160),
    VERTICAL_9_16("Vertical 9:16 (1080x1920)", 1080, 1920),
    SQUARE_1_1("Square 1:1 (1080x1080)", 1080, 1080),
    ULTRAWIDE_21_9("Ultrawide 21:9 (2560x1080)", 2560, 1080)
}

data class VideoExportConfig(
    val outputFile: File,
    val width: Int,
    val height: Int,
    val fps: Int = 60,
    val codec: VideoCodec = VideoCodec.H264,
    val crf: Int = 18, // 18 is visually near-lossless for x264/x265
    val bitrateMbps: Int = 15,
    val audioFile: File? = null,
    val superSamplingFactor: Int = 1 // 1x = standard, 2x, 4x, 8x = motion blur accumulation
)

/**
 * Manages an FFmpeg subprocess that receives raw RGBA frames over stdin.
 */
class FFmpegProcessPipe(
    val config: VideoExportConfig
) {
    companion object {
        private val detectedEncoders: Set<String> by lazy {
            try {
                val proc = ProcessBuilder("ffmpeg", "-encoders").start()
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                val encoders = mutableSetOf<String>()
                val encoderRegex = Regex("""^\s*[VAS][\S.]{5}\s+(\S+)""")
                reader.useLines { lines ->
                    for (line in lines) {
                        val match = encoderRegex.find(line)
                        if (match != null) {
                            encoders.add(match.groupValues[1])
                        }
                    }
                }
                proc.waitFor()
                logger.info { "Detected ${encoders.size} FFmpeg encoders available on system." }
                encoders
            } catch (e: Exception) {
                logger.warn { "Failed to query FFmpeg encoders: ${e.message}" }
                emptySet()
            }
        }

        fun getBestEncoder(codec: VideoCodec): String {
            val available = detectedEncoders
            if (available.isEmpty()) return codec.ffmpegCodecName // fallback to default

            val candidates = when (codec) {
                VideoCodec.H264 -> listOf("libx264", "libopenh264", "mpeg4", "h264_nvenc", "h264_vaapi", "h264_qsv", "h264_amf", "h264_v4l2m2m")
                VideoCodec.H265 -> listOf("libx265", "hevc_nvenc", "hevc_vaapi", "hevc_qsv", "hevc_amf", "hevc_v4l2m2m")
                VideoCodec.PRORES -> listOf("prores_ks", "prores", "prores_aw")
            }

            return candidates.firstOrNull { it in available } ?: codec.ffmpegCodecName
        }
    }

    private var process: Process? = null
    private var outputStream: OutputStream? = null
    private val isRunning = AtomicBoolean(false)
    private var stderrReaderThread: Thread? = null
    private val errorLogs = StringBuilder()

    fun isAlive(): Boolean = isRunning.get() && (process?.isAlive ?: false)

    fun start(): Boolean {
        if (isRunning.get()) return true

        val chosenEncoder = getBestEncoder(config.codec)
        logger.info { "Selected video encoder '$chosenEncoder' for codec ${config.codec}" }

        val cmd = mutableListOf(
            "ffmpeg",
            "-y", // overwrite
            "-f", "rawvideo",
            "-vcodec", "rawvideo",
            "-s", "${config.width}x${config.height}",
            "-pix_fmt", "rgba",
            "-r", config.fps.toString(),
            "-i", "-" // read video from stdin
        )

        if (config.audioFile != null && config.audioFile.exists()) {
            cmd.addAll(listOf("-i", config.audioFile.absolutePath))
        }

        when (config.codec) {
            VideoCodec.H264 -> {
                cmd.addAll(listOf("-c:v", chosenEncoder))
                when (chosenEncoder) {
                    "libx264" -> cmd.addAll(listOf("-preset", "fast", "-crf", config.crf.toString()))
                    "libopenh264" -> cmd.addAll(listOf("-b:v", "${config.bitrateMbps}M"))
                    "h264_nvenc" -> cmd.addAll(listOf("-preset", "p4", "-cq", config.crf.toString()))
                    "mpeg4" -> cmd.addAll(listOf("-b:v", "${config.bitrateMbps}M"))
                    else -> cmd.addAll(listOf("-b:v", "${config.bitrateMbps}M"))
                }
                cmd.addAll(listOf("-pix_fmt", "yuv420p"))
            }
            VideoCodec.H265 -> {
                cmd.addAll(listOf("-c:v", chosenEncoder))
                when (chosenEncoder) {
                    "libx265" -> cmd.addAll(listOf("-preset", "fast", "-crf", config.crf.toString()))
                    "hevc_nvenc" -> cmd.addAll(listOf("-preset", "p4", "-cq", config.crf.toString()))
                    else -> cmd.addAll(listOf("-b:v", "${config.bitrateMbps}M"))
                }
                cmd.addAll(listOf("-pix_fmt", "yuv420p"))
            }
            VideoCodec.PRORES -> {
                cmd.addAll(listOf("-c:v", chosenEncoder))
                if (chosenEncoder == "prores_ks") {
                    cmd.addAll(listOf("-profile:v", "3")) // ProRes 422 HQ
                }
                cmd.addAll(listOf("-pix_fmt", "yuv422p10le"))
            }
        }

        if (config.audioFile != null && config.audioFile.exists()) {
            cmd.addAll(listOf("-c:a", "aac", "-b:a", "320k", "-shortest"))
        }

        cmd.add(config.outputFile.absolutePath)

        try {
            logger.info { "Launching FFmpeg pipe: ${cmd.joinToString(" ")}" }
            val pb = ProcessBuilder(cmd)
            val proc = pb.start()
            process = proc
            outputStream = BufferedOutputStream(proc.outputStream, 1024 * 1024)
            isRunning.set(true)

            // Read stderr in background to avoid pipe deadlocks
            stderrReaderThread = Thread({
                try {
                    val reader = BufferedReader(InputStreamReader(proc.errorStream))
                    while (isRunning.get()) {
                        val line = reader.readLine() ?: break
                        synchronized(errorLogs) {
                            if (errorLogs.length < 20000) {
                                errorLogs.append(line).append("\n")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore stream closure
                }
            }, "FFmpeg-Stderr-Reader").apply { isDaemon = true }
            stderrReaderThread?.start()

            return true
        } catch (e: Exception) {
            logger.error(e) { "Failed to start FFmpeg subprocess: ${e.message}" }
            cancel()
            return false
        }
    }

    /**
     * Pushes a single raw RGBA frame to FFmpeg's stdin.
     */
    fun pushFrame(rgbaBytes: ByteArray, offset: Int = 0, length: Int = rgbaBytes.size) {
        val stream = outputStream ?: return
        if (!isRunning.get()) return
        try {
            stream.write(rgbaBytes, offset, length)
        } catch (e: Exception) {
            val logs = getErrorLogs()
            logger.error(e) { "Error writing frame to FFmpeg pipe: ${e.message}.${if (logs.isNotBlank()) " FFmpeg stderr:\n$logs" else ""}" }
            cancel()
        }
    }

    /**
     * Pushes a ByteBuffer containing RGBA bytes to FFmpeg's stdin.
     */
    fun pushFrame(byteBuffer: ByteBuffer) {
        val stream = outputStream ?: return
        if (!isRunning.get()) return
        try {
            if (byteBuffer.hasArray()) {
                stream.write(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining())
            } else {
                val tempArray = ByteArray(minOf(65536, byteBuffer.remaining()))
                while (byteBuffer.hasRemaining()) {
                    val toRead = minOf(tempArray.size, byteBuffer.remaining())
                    byteBuffer.get(tempArray, 0, toRead)
                    stream.write(tempArray, 0, toRead)
                }
            }
        } catch (e: Exception) {
            val logs = getErrorLogs()
            logger.error(e) { "Error writing ByteBuffer to FFmpeg pipe: ${e.message}.${if (logs.isNotBlank()) " FFmpeg stderr:\n$logs" else ""}" }
            cancel()
        }
    }

    fun finish(waitForExit: Boolean = true): Boolean {
        if (!isRunning.compareAndSet(true, false)) return true
        try {
            outputStream?.flush()
            outputStream?.close()
            outputStream = null

            val proc = process
            if (proc != null && waitForExit) {
                val exitCode = proc.waitFor()
                if (exitCode != 0) {
                    logger.error { "FFmpeg exited with non-zero code $exitCode. Logs:\n$errorLogs" }
                    return false
                }
                logger.info { "FFmpeg export completed successfully: ${config.outputFile.absolutePath}" }
            }
            return true
        } catch (e: Exception) {
            logger.error(e) { "Error finishing FFmpeg pipe: ${e.message}" }
            return false
        } finally {
            process = null
        }
    }

    fun cancel() {
        isRunning.set(false)
        try {
            outputStream?.close()
        } catch (e: Exception) { /* Ignore */ }
        outputStream = null
        try {
            process?.destroyForcibly()
        } catch (e: Exception) { /* Ignore */ }
        process = null
    }

    fun getErrorLogs(): String = synchronized(errorLogs) { errorLogs.toString() }
}

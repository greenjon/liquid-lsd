package llm.slop.liquidlsd.export

import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Real-time video recorder for live VJ sessions.
 * Uses an asynchronous PBO readback ring buffer and a background queue to write frames to FFmpeg
 * without stalling the OpenGL render loop.
 */
object RealtimeRecorder {

    @Volatile
    var isRecording = false
        private set

    @Volatile
    var droppedFramesCount = 0L
        private set

    @Volatile
    var totalFramesCount = 0L
        private set

    @Volatile
    var recordingStartTimeMs = 0L
        private set

    @Volatile
    var currentOutputFile: File? = null
        private set

    private var pboPipeline: PboReadbackPipeline? = null
    private var ffmpegPipe: FFmpegProcessPipe? = null
    private var workerThread: Thread? = null
    private val isWorkerRunning = AtomicBoolean(false)

    // Bounded frame queue to cap memory usage (~10 frames buffer)
    private const val QUEUE_CAPACITY = 10
    private var frameQueue: ArrayBlockingQueue<ByteBuffer>? = null
    private val bufferPool = mutableListOf<ByteBuffer>()

    val droppedPercentage: Float
        get() {
            val total = totalFramesCount
            return if (total > 0) (droppedFramesCount.toFloat() / total.toFloat()) * 100f else 0f
        }

    val elapsedSeconds: Float
        get() {
            if (!isRecording) return 0f
            return (System.currentTimeMillis() - recordingStartTimeMs) / 1000f
        }

    val fileSizeBytes: Long
        get() = currentOutputFile?.length() ?: 0L

    /**
     * Starts live recording to a destination file.
     */
    @Synchronized
    fun startRecording(
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int = 60,
        codec: VideoCodec = VideoCodec.H264,
        bitrateMbps: Int = 12
    ): Boolean {
        if (isRecording) return true

        logger.info { "Starting live recording to ${outputFile.name} (${width}x${height} @ ${fps}fps)..." }
        outputFile.parentFile?.mkdirs()

        val config = VideoExportConfig(
            outputFile = outputFile,
            width = width,
            height = height,
            fps = fps,
            codec = codec,
            bitrateMbps = bitrateMbps
        )

        val pipe = FFmpegProcessPipe(config)
        if (!pipe.start()) {
            logger.error { "Failed to start FFmpeg process for live recording." }
            return false
        }

        pboPipeline = PboReadbackPipeline(width, height)
        ffmpegPipe = pipe
        currentOutputFile = outputFile
        droppedFramesCount = 0L
        totalFramesCount = 0L
        recordingStartTimeMs = System.currentTimeMillis()

        // Allocate buffer pool for queue
        val bufferSize = width * height * 4
        bufferPool.clear()
        for (i in 0 until (QUEUE_CAPACITY + 2)) {
            bufferPool.add(MemoryUtil.memAlloc(bufferSize))
        }

        val queue = ArrayBlockingQueue<ByteBuffer>(QUEUE_CAPACITY)
        frameQueue = queue
        isWorkerRunning.set(true)

        workerThread = Thread({
            logger.info { "RealtimeRecorder worker thread started." }
            while (isWorkerRunning.get() || queue.isNotEmpty()) {
                val frame = queue.poll(50, TimeUnit.MILLISECONDS) ?: continue
                try {
                    pipe.pushFrame(frame)
                } finally {
                    synchronized(bufferPool) {
                        bufferPool.add(frame)
                    }
                }
            }
            logger.info { "RealtimeRecorder worker thread finished." }
        }, "RealtimeRecorder-Worker").apply { isDaemon = true }
        workerThread?.start()

        isRecording = true
        return true
    }

    /**
     * Called once per rendered frame from Thread 0 (OpenGL thread).
     */
    fun captureFrame(fboId: Int) {
        if (!isRecording) return
        val pbo = pboPipeline ?: return
        val queue = frameQueue ?: return
        val pipe = ffmpegPipe

        if (pipe != null && !pipe.isAlive()) {
            logger.warn { "FFmpeg pipe terminated unexpectedly. Stopping live recording." }
            stopRecording()
            return
        }

        totalFramesCount++

        // Obtain a free buffer from pool
        var targetBuffer: ByteBuffer? = null
        synchronized(bufferPool) {
            if (bufferPool.isNotEmpty()) {
                targetBuffer = bufferPool.removeAt(bufferPool.size - 1)
            }
        }

        if (targetBuffer == null) {
            // Queue is congested: drop frame
            droppedFramesCount++
            return
        }

        targetBuffer.clear()
        val readResult = pbo.readFrameAsync(fboId, targetBuffer)
        if (readResult == null) {
            // Warmup frame: return buffer to pool
            synchronized(bufferPool) {
                bufferPool.add(targetBuffer)
            }
            return
        }

        targetBuffer.position(0)
        targetBuffer.limit(pbo.bufferSizeBytes)

        if (!queue.offer(targetBuffer)) {
            // Queue is full: drop frame and return buffer to pool
            droppedFramesCount++
            synchronized(bufferPool) {
                bufferPool.add(targetBuffer)
            }
        }
    }

    /**
     * Stops live recording, flushes all pending frames to disk, and cleans up resources.
     */
    @Synchronized
    fun stopRecording(): Boolean {
        if (!isRecording && ffmpegPipe == null && pboPipeline == null) return true
        logger.info { "Stopping live recording. Total frames: $totalFramesCount, Dropped: $droppedFramesCount (${"%.2f".format(droppedPercentage)}%)" }

        isRecording = false
        isWorkerRunning.set(false)

        workerThread?.join(3000)
        workerThread = null

        val success = ffmpegPipe?.finish(waitForExit = true) ?: true
        ffmpegPipe = null

        pboPipeline?.dispose()
        pboPipeline = null

        synchronized(bufferPool) {
            for (buf in bufferPool) {
                MemoryUtil.memFree(buf)
            }
            bufferPool.clear()
        }
        frameQueue = null

        return success
    }
}

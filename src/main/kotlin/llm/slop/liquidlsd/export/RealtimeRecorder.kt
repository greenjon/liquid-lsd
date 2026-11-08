package llm.slop.liquidlsd.export

import org.lwjgl.system.MemoryUtil
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Real-time video recorder for live VJ sessions.
 * Uses an asynchronous PBO readback ring buffer and a background queue to write video frames and
 * audio PCM blocks to disk without stalling the OpenGL render loop or the real-time audio thread.
 */
object RealtimeRecorder {

    @Volatile
    var isRecording = false
        private set

    @Volatile
    var isRecordingAudio = false
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

    private var finalDestinationFile: File? = null
    private var tempVideoFile: File? = null
    private var tempAudioFile: File? = null

    private var pboPipeline: PboReadbackPipeline? = null
    private var ffmpegPipe: FFmpegProcessPipe? = null
    private var videoWorkerThread: Thread? = null
    private var audioWorkerThread: Thread? = null
    private val isWorkerRunning = AtomicBoolean(false)

    // Bounded video frame queue to cap memory usage (~10 frames buffer)
    private const val QUEUE_CAPACITY = 10
    private var frameQueue: ArrayBlockingQueue<Frame>? = null
    private val bufferPool = mutableListOf<Frame>()

    /** A pooled frame buffer and when it was captured, so the writer can place it in time. */
    private class Frame(val buf: ByteBuffer) { @Volatile var capturedNanos = 0L }

    /** Start of the recording on [System.nanoTime]'s clock: frame N of the file belongs at N / fps seconds after it. */
    @Volatile private var startNanos = 0L

    // Audio block pre-allocated pool for zero-allocation, lock-free audio callback tapping
    class AudioBlock(val buffer: FloatArray = FloatArray(8192)) {
        @Volatile var length: Int = 0
        @Volatile var sampleRate: Float = 44100f
    }

    /**
     * Lock-free, wait-free Single-Producer Single-Consumer (SPSC) bounded queue.
     * Operates with zero heap allocations during offer/poll and zero mutex locks.
     */
    class SpscQueue<T : Any>(val capacity: Int) {
        init {
            require(capacity > 0 && (capacity and (capacity - 1)) == 0) { "Capacity must be a power of 2" }
        }

        private val mask = capacity - 1
        @Suppress("UNCHECKED_CAST")
        private val buffer = arrayOfNulls<Any>(capacity) as Array<T?>

        @Volatile private var head: Long = 0L // Write index (Producer)
        @Volatile private var tail: Long = 0L // Read index (Consumer)

        fun offer(element: T): Boolean {
            val currentHead = head
            val currentTail = tail
            if ((currentHead - currentTail).toInt() >= capacity) {
                return false // Queue full
            }
            buffer[(currentHead.toInt() and mask)] = element
            head = currentHead + 1L
            return true
        }

        fun poll(): T? {
            val currentTail = tail
            val currentHead = head
            if (currentTail >= currentHead) {
                return null // Queue empty
            }
            val idx = currentTail.toInt() and mask
            val item = buffer[idx]
            buffer[idx] = null
            tail = currentTail + 1L
            return item
        }

        fun isNotEmpty(): Boolean = head > tail
        fun isEmpty(): Boolean = tail >= head

        fun size(): Int = (head - tail).toInt().coerceAtLeast(0)

        @Synchronized
        fun clear() {
            head = 0L
            tail = 0L
            for (i in buffer.indices) {
                buffer[i] = null
            }
        }
    }

    private const val AUDIO_POOL_SIZE = 128
    private val staticAudioBlockPool = Array(AUDIO_POOL_SIZE) { AudioBlock() }
    private val freeAudioBlocks = SpscQueue<AudioBlock>(AUDIO_POOL_SIZE).apply {
        for (b in staticAudioBlockPool) offer(b)
    }
    private val pendingAudioBlocks = SpscQueue<AudioBlock>(AUDIO_POOL_SIZE)

    @Synchronized
    private fun resetAudioPool() {
        pendingAudioBlocks.clear()
        freeAudioBlocks.clear()
        for (b in staticAudioBlockPool) {
            freeAudioBlocks.offer(b)
        }
    }

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
        get() = (tempVideoFile?.length() ?: currentOutputFile?.length()) ?: 0L

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
        bitrateMbps: Int = 12,
        includeAudio: Boolean = true
    ): Boolean {
        if (isRecording) return true
        // The last recording is still being written out; its buffers and temp files are in use.
        if (isFinishing) {
            logger.warn { "Not starting a recording: the last one is still being saved." }
            return false
        }

        logger.info { "Starting live recording to ${outputFile.name} (${width}x${height} @ ${fps}fps, audio: $includeAudio)..." }
        outputFile.parentFile?.mkdirs()

        finalDestinationFile = outputFile
        isRecordingAudio = includeAudio

        if (includeAudio) {
            tempVideoFile = File(outputFile.parentFile, ".tmp_vid_${System.currentTimeMillis()}_${outputFile.name}")
            tempAudioFile = File(outputFile.parentFile, ".tmp_aud_${System.currentTimeMillis()}_${outputFile.nameWithoutExtension}.wav")
        } else {
            tempVideoFile = outputFile
            tempAudioFile = null
        }

        val videoTarget = tempVideoFile ?: outputFile
        val config = VideoExportConfig(
            outputFile = videoTarget,
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

        // Allocate buffer pool for video queue
        val bufferSize = width * height * 4
        bufferPool.clear()
        for (i in 0 until (QUEUE_CAPACITY + 2)) {
            bufferPool.add(Frame(MemoryUtil.memAlloc(bufferSize)))
        }
        startNanos = System.nanoTime()

        val queue = ArrayBlockingQueue<Frame>(QUEUE_CAPACITY)
        frameQueue = queue
        isWorkerRunning.set(true)

        videoWorkerThread = Thread({
            logger.info { "RealtimeRecorder video worker thread started." }
            // The file is constant-rate at [fps] while the app renders at whatever rate it manages. Each captured
            // frame is written as many times as the clock says (or skipped), so the video keeps real time and stays
            // in step with the audio, which is recorded as it plays.
            val frameNanos = 1_000_000_000.0 / fps
            val maxRepeats = fps * 5L   // a long stall still keeps sync, without writing minutes of copies at once
            var written = 0L
            while (isWorkerRunning.get() || queue.isNotEmpty()) {
                val frame = queue.poll(50, TimeUnit.MILLISECONDS) ?: continue
                try {
                    val due = ((frame.capturedNanos - startNanos) / frameNanos).toLong() + 1
                    val copies = if (written == 0L) 1L else (due - written).coerceIn(0L, maxRepeats)
                    for (i in 0 until copies) {
                        frame.buf.position(0)
                        pipe.pushFrame(frame.buf)
                    }
                    written += copies
                } finally {
                    synchronized(bufferPool) {
                        bufferPool.add(frame)
                    }
                }
            }
            logger.info { "RealtimeRecorder video worker thread finished." }
        }, "RealtimeRecorder-VideoWorker").apply { isDaemon = true }
        videoWorkerThread?.start()

        if (includeAudio && tempAudioFile != null) {
            resetAudioPool()
            val audioFile = tempAudioFile!!
            audioWorkerThread = Thread({
                logger.info { "RealtimeRecorder audio worker thread started: ${audioFile.name}" }
                var raf: RandomAccessFile? = null
                var recordedSampleRate = 44100
                var totalBytesWritten = 0L
                val pcmBuffer = ByteArray(16384)

                try {
                    raf = RandomAccessFile(audioFile, "rw")
                    raf.setLength(0)
                    // Write placeholder 44-byte WAV header
                    raf.write(ByteArray(44))

                    while (isWorkerRunning.get() || pendingAudioBlocks.isNotEmpty()) {
                        val block = pendingAudioBlocks.poll()
                        if (block == null) {
                            try {
                                Thread.sleep(5)
                            } catch (e: InterruptedException) {
                                // proceed
                            }
                            continue
                        }
                        try {
                            recordedSampleRate = block.sampleRate.toInt()
                            val count = block.length
                            var pcmOffset = 0

                            for (i in 0 until count) {
                                val s = (block.buffer[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                                pcmBuffer[pcmOffset] = (s.toInt() and 0xFF).toByte()
                                pcmBuffer[pcmOffset + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                                pcmOffset += 2

                                if (pcmOffset >= pcmBuffer.size) {
                                    raf.write(pcmBuffer, 0, pcmOffset)
                                    totalBytesWritten += pcmOffset
                                    pcmOffset = 0
                                }
                            }
                            if (pcmOffset > 0) {
                                raf.write(pcmBuffer, 0, pcmOffset)
                                totalBytesWritten += pcmOffset
                            }
                        } finally {
                            freeAudioBlocks.offer(block)
                        }
                    }

                    // Write finalized WAV header
                    writeWavHeader(raf, recordedSampleRate, 1, 16, totalBytesWritten)
                } catch (e: Exception) {
                    logger.error(e) { "Error in RealtimeRecorder audio worker thread: ${e.message}" }
                } finally {
                    try {
                        raf?.close()
                    } catch (e: Exception) { /* ignore */ }
                    logger.info { "RealtimeRecorder audio worker thread finished ($totalBytesWritten bytes recorded)." }
                }
            }, "RealtimeRecorder-AudioWorker").apply { isDaemon = true }
            audioWorkerThread?.start()
        }

        isRecording = true
        return true
    }

    /**
     * Called from AudioEngine.processAudio on the audio callback thread.
     * Zero allocations and non-blocking.
     */
    fun pushAudioBlock(buffer: FloatBuffer, offset: Int, count: Int, sampleRate: Float, gain: Float) {
        if (!isRecording || !isRecordingAudio) return
        val block = freeAudioBlocks.poll() ?: return // Drop if congested
        val len = count.coerceAtMost(block.buffer.size)
        for (i in 0 until len) {
            block.buffer[i] = buffer.get(offset + i) * gain
        }
        block.length = len
        block.sampleRate = sampleRate
        if (!pendingAudioBlocks.offer(block)) {
            freeAudioBlocks.offer(block)
        }
    }

    /**
     * Called from AudioEngine.processAudio with pre-routed/downmixed FloatArray samples.
     * Zero allocations and non-blocking.
     */
    fun pushAudioArray(samples: FloatArray, offset: Int, count: Int, sampleRate: Float) {
        if (!isRecording || !isRecordingAudio) return
        val block = freeAudioBlocks.poll() ?: return
        val len = count.coerceAtMost(block.buffer.size)
        System.arraycopy(samples, offset, block.buffer, 0, len)
        block.length = len
        block.sampleRate = sampleRate
        if (!pendingAudioBlocks.offer(block)) {
            freeAudioBlocks.offer(block)
        }
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
        var target: Frame? = null
        synchronized(bufferPool) {
            if (bufferPool.isNotEmpty()) {
                target = bufferPool.removeAt(bufferPool.size - 1)
            }
        }
        val frame = target ?: run {
            // Queue is congested: drop frame
            droppedFramesCount++
            return
        }

        frame.buf.clear()
        val readResult = pbo.readFrameAsync(fboId, frame.buf)
        if (readResult == null) {
            // Warmup frame: return buffer to pool
            synchronized(bufferPool) {
                bufferPool.add(frame)
            }
            return
        }

        frame.buf.position(0)
        frame.buf.limit(pbo.bufferSizeBytes)
        frame.capturedNanos = System.nanoTime()

        if (!queue.offer(frame)) {
            // Queue is full: drop frame and return buffer to pool
            droppedFramesCount++
            synchronized(bufferPool) {
                bufferPool.add(frame)
            }
        }
    }

    /** True while a stopped recording is still being written out (encoder flush, audio mux). */
    @Volatile
    var isFinishing = false
        private set

    private var finishThread: Thread? = null

    /**
     * Stops live recording. Only the GL readback is torn down here (it must be, on the GL thread); flushing the
     * encoder and muxing the audio, which takes minutes for a long set, run on a background thread so the output
     * never freezes ([isFinishing] meanwhile). [waitForFinish] blocks until the file is complete (app exit).
     */
    @Synchronized
    fun stopRecording(waitForFinish: Boolean = false): Boolean {
        if (!isRecording && ffmpegPipe == null && pboPipeline == null) {
            if (waitForFinish) awaitFinish()
            return true
        }
        logger.info { "Stopping live recording. Total frames: $totalFramesCount, Dropped: $droppedFramesCount (${"%.2f".format(droppedPercentage)}%)" }

        isRecording = false
        val wasRecordingAudio = isRecordingAudio
        isRecordingAudio = false
        isWorkerRunning.set(false)

        pboPipeline?.dispose()
        pboPipeline = null

        val videoWorker = videoWorkerThread
        val audioWorker = audioWorkerThread
        val pipe = ffmpegPipe
        val tempVid = tempVideoFile
        val tempAud = tempAudioFile
        val finalOut = finalDestinationFile
        val seconds = (System.nanoTime() - startNanos) / 1_000_000_000L
        videoWorkerThread = null
        audioWorkerThread = null
        ffmpegPipe = null
        frameQueue = null
        tempVideoFile = null
        tempAudioFile = null
        finalDestinationFile = null

        isFinishing = true
        val t = Thread({
            try {
                finish(videoWorker, audioWorker, pipe, wasRecordingAudio, tempVid, tempAud, finalOut, seconds)
            } catch (e: Throwable) {
                logger.error(e) { "Finishing the recording failed" }
            } finally {
                isFinishing = false
            }
        }, "RealtimeRecorder-Finish")
        finishThread = t
        t.start()
        if (waitForFinish) awaitFinish()
        return true
    }

    /** Waits for a stopped recording to be written out. */
    fun awaitFinish(timeoutMs: Long = 0) {
        finishThread?.join(timeoutMs)
    }

    private fun finish(
        videoWorker: Thread?, audioWorker: Thread?, pipe: FFmpegProcessPipe?, wasRecordingAudio: Boolean,
        tempVid: File?, tempAud: File?, finalOut: File?, seconds: Long
    ) {
        // The workers drain what's queued, then end; the audio worker writes the WAV header last.
        videoWorker?.join(60_000)
        audioWorker?.join(60_000)
        resetAudioPool()
        val videoSuccess = pipe?.finish(waitForExit = true) ?: true
        synchronized(bufferPool) {
            // A worker still stuck in a write holds one of these: leaking them beats freeing memory in use.
            if (videoWorker?.isAlive != true) for (f in bufferPool) MemoryUtil.memFree(f.buf)
            else logger.warn { "Video worker still running after 60 s; leaving its frame buffers allocated" }
            bufferPool.clear()
        }

        if (wasRecordingAudio && tempVid != null && tempAud != null && finalOut != null && tempVid.exists() && tempAud.exists() && tempAud.length() > 44) {
            logger.info { "Muxing live audio (${tempAud.name}) and video (${tempVid.name}) to ${finalOut.name}..." }
            // Encoding the audio takes a while for a long set (minutes for an hour): allow for it, and never treat a
            // mux that is still running as failed (that used to drop the audio and leave ffmpeg writing the file).
            val limitSec = 120L + seconds
            val muxSuccess = try {
                val cmd = listOf(
                    "ffmpeg", "-y", "-nostdin", "-nostats", "-loglevel", "error",
                    "-i", tempVid.absolutePath,
                    "-i", tempAud.absolutePath,
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-b:a", "320k",
                    "-shortest",
                    finalOut.absolutePath
                )
                // Output goes nowhere: an unread pipe fills up and stalls ffmpeg.
                val proc = ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                if (proc.waitFor(limitSec, TimeUnit.SECONDS)) proc.exitValue() == 0
                else {
                    logger.error { "Muxing took over $limitSec s; stopped it" }
                    proc.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
                    false
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to mux audio into live recording: ${e.message}" }
                false
            }

            if (muxSuccess && finalOut.exists() && finalOut.length() > 0) {
                logger.info { "Muxing complete: ${finalOut.absolutePath} (${finalOut.length() / (1024 * 1024)} MB)" }
                tempVid.delete()
                tempAud.delete()
            } else {
                // Keep both halves rather than lose the audio.
                logger.warn { "Muxing failed; keeping the video as ${finalOut.name} and the audio as ${tempAud.name}." }
                finalOut.delete()
                tempVid.renameTo(finalOut)
            }
        } else if (tempVid != null && finalOut != null && tempVid != finalOut && tempVid.exists()) {
            tempVid.renameTo(finalOut)
            tempAud?.delete()
        }
        logger.info { "Recording saved: ${finalOut?.absolutePath} (encoder ok: $videoSuccess)" }
    }

    private fun writeWavHeader(raf: RandomAccessFile, sampleRate: Int, channels: Int, bitsPerSample: Int, totalAudioBytes: Long) {
        raf.seek(0)
        val totalDataLen = totalAudioBytes + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen.toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size for PCM
        header.putShort(1) // AudioFormat 1 = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * bitsPerSample / 8).toShort()) // BlockAlign
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray())
        header.putInt(totalAudioBytes.toInt())

        raf.write(header.array())
    }
}

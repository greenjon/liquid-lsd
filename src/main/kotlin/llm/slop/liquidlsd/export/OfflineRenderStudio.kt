package llm.slop.liquidlsd.export

import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Renderer
import llm.slop.liquidlsd.utils.TimeSource
import java.io.File
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

data class OfflineRenderProgress(
    val currentFrame: Int,
    val totalFrames: Int,
    val percent: Float,
    val currentFps: Float,
    val elapsedSeconds: Float,
    val etaSeconds: Float,
    val estimatedFileSizeMb: Float,
    val previewTextureId: Int
)

/**
 * Deterministic offline audio-to-video rendering studio.
 * Renders frame-by-frame with sample-accurate DSP stepping, deterministic time virtualization,
 * and optional multi-pass motion blur.
 * Runs on Thread 0 without blocking OS window event processing.
 */
object OfflineRenderStudio {

    @Volatile
    var isRendering = false
        private set

    private val cancelRequested = AtomicBoolean(false)

    @Volatile
    var progress = OfflineRenderProgress(0, 1, 0f, 0f, 0f, 0f, 0f, 0)
        private set

    @Volatile
    var statusMessage: String? = null
        private set

    @Volatile
    var isSuccess: Boolean? = null
        private set

    private var currentConfig: VideoExportConfig? = null
    private var decodedAudio: DecodedAudio? = null
    private var totalFrames = 1
    private var currentFrame = 0
    private var renderStartTimeMs = 0L
    private var ffmpegPipe: FFmpegProcessPipe? = null
    private var pboPipeline: PboReadbackPipeline? = null
    private var accumulationBuffer: AccumulationBuffer? = null
    private var origMixerW = 1920
    private var origMixerH = 1080
    private var superSampling = 1
    private const val audioBlockSize = 512
    private val audioFloatArray = FloatArray(audioBlockSize)
    private val audioFloatBuffer: FloatBuffer = FloatBuffer.wrap(audioFloatArray)
    private var lastFpsTimeMs = 0L
    private var framesSinceLastFps = 0
    private var currentFps = 0f

    fun cancel() {
        cancelRequested.set(true)
    }

    fun startExport(config: VideoExportConfig, mixer: Mixer, renderer: Renderer): Boolean {
        require(config.audioFile != null && config.audioFile.exists()) { "Audio file required for offline export" }
        if (isRendering) return false

        logger.info { "Starting offline render: ${config.outputFile.name} (${config.width}x${config.height} @ ${config.fps}fps, ${config.superSamplingFactor}x sampling)..." }

        val decoded = try {
            AudioDecoder.decodeAudioFile(config.audioFile)
        } catch (e: Exception) {
            logger.error(e) { "Failed to decode audio file: ${e.message}" }
            statusMessage = "Audio decode failed: ${e.message}"
            isSuccess = false
            return false
        }

        val frames = (decoded.durationSeconds * config.fps).toInt().coerceAtLeast(1)
        origMixerW = mixer.width
        origMixerH = mixer.height

        mixer.resize(config.width, config.height)

        val pipe = FFmpegProcessPipe(config)
        if (!pipe.start()) {
            mixer.resize(origMixerW, origMixerH)
            val err = pipe.getRecentErrorLines(4)
            statusMessage = "Failed to launch FFmpeg: ${if (err.isNotBlank()) err else "encoder/process error"}"
            isSuccess = false
            return false
        }

        val pbo = PboReadbackPipeline(config.width, config.height)
        val sSamp = config.superSamplingFactor.coerceIn(1, 8)
        val accum = if (sSamp > 1) {
            AccumulationBuffer(config.width, config.height, renderer.blitShader)
        } else null

        currentConfig = config
        decodedAudio = decoded
        totalFrames = frames
        currentFrame = 0
        renderStartTimeMs = System.currentTimeMillis()
        ffmpegPipe = pipe
        pboPipeline = pbo
        accumulationBuffer = accum
        superSampling = sSamp
        cancelRequested.set(false)
        isRendering = true
        statusMessage = null
        isSuccess = null
        lastFpsTimeMs = System.currentTimeMillis()
        framesSinceLastFps = 0
        currentFps = 0f
        progress = OfflineRenderProgress(0, totalFrames, 0f, 0f, 0f, 0f, 0f, accum?.fbo?.texture ?: mixer.masterFBO.texture)

        return true
    }

    /**
     * Executes a single frame step of the offline render on Thread 0.
     * Returns true if rendering is still ongoing, false if complete or cancelled.
     */
    fun step(mixer: Mixer, renderer: Renderer): Boolean {
        if (!isRendering) return false
        val config = currentConfig ?: return false
        val decoded = decodedAudio ?: return false
        val pipe = ffmpegPipe ?: return false
        val pbo = pboPipeline ?: return false
        val accum = accumulationBuffer

        if (cancelRequested.get() || currentFrame >= totalFrames || !pipe.isAlive()) {
            finish(mixer)
            return false
        }

        val frameDeltaSec = 1.0 / config.fps.toDouble()
        val subFrameDt = frameDeltaSec / superSampling.toDouble()
        val sampleRate = decoded.sampleRate
        val totalAudioSamples = decoded.samples.size

        accum?.clear()

        for (sampleIdx in 0 until superSampling) {
            val subFrameTimeSec = (currentFrame + (sampleIdx.toDouble() / superSampling.toDouble())) * frameDeltaSec
            val timeNanos = (subFrameTimeSec * 1_000_000_000.0).toLong()

            // Enable virtualized deterministic time across all shaders, evaluators, and models
            TimeSource.setSimulatedTime(subFrameTimeSec, subFrameDt)

            val sampleCenter = (subFrameTimeSec * sampleRate).toInt()
            val startSample = (sampleCenter - audioBlockSize / 2).coerceIn(0, totalAudioSamples)

            for (i in 0 until audioBlockSize) {
                val srcIdx = startSample + i
                audioFloatArray[i] = if (srcIdx in 0 until totalAudioSamples) decoded.samples[srcIdx] else 0f
            }
            audioFloatBuffer.position(0)
            audioFloatBuffer.limit(audioBlockSize)

            AudioEngine.processAudio(audioFloatBuffer, audioBlockSize, sampleRate, timeNanos)
            CVRegistry.updateAll()

            mixer.deckA.update()
            renderer.renderDeck(mixer.deckA)

            mixer.deckB.update()
            renderer.renderDeck(mixer.deckB)

            mixer.deckBG.update()
            renderer.renderDeck(mixer.deckBG)

            mixer.update()
            renderer.renderMixer(mixer)

            if (accum != null) {
                accum.accumulate(mixer.masterFBO, 1.0f / superSampling.toFloat())
            }
        }

        val targetFboId = accum?.fbo?.framebufferId ?: mixer.masterFBO.framebufferId
        val frameBytes = pbo.readFrameSync(targetFboId)
        pipe.pushFrame(frameBytes)

        currentFrame++
        framesSinceLastFps++
        val now = System.currentTimeMillis()
        if (now - lastFpsTimeMs >= 500) {
            currentFps = (framesSinceLastFps.toFloat() / ((now - lastFpsTimeMs) / 1000f))
            framesSinceLastFps = 0
            lastFpsTimeMs = now
        }

        val elapsedSec = (now - renderStartTimeMs) / 1000f
        val percent = (currentFrame.toFloat() / totalFrames.toFloat()) * 100f
        val remainingFrames = totalFrames - currentFrame
        val etaSec = if (currentFps > 0) remainingFrames / currentFps else 0f
        val previewTexture = accum?.fbo?.texture ?: mixer.masterFBO.texture

        val currentFileSizeMb = (config.outputFile.length() / (1024f * 1024f)).coerceAtLeast(0f)

        progress = OfflineRenderProgress(
            currentFrame = currentFrame,
            totalFrames = totalFrames,
            percent = percent,
            currentFps = currentFps,
            elapsedSeconds = elapsedSec,
            etaSeconds = etaSec,
            estimatedFileSizeMb = currentFileSizeMb,
            previewTextureId = previewTexture
        )

        if (currentFrame >= totalFrames) {
            finish(mixer)
            return false
        }

        return true
    }

    private fun finish(mixer: Mixer) {
        if (!isRendering) return
        isRendering = false
        TimeSource.clearSimulatedTime()

        val pipe = ffmpegPipe
        val accum = accumulationBuffer
        val pbo = pboPipeline
        val config = currentConfig

        val wasCancelled = cancelRequested.get()
        val success = if (!wasCancelled) {
            pipe?.finish(waitForExit = true) ?: true
        } else {
            pipe?.cancel()
            false
        }

        pbo?.dispose()
        accum?.dispose()
        pboPipeline = null
        accumulationBuffer = null
        ffmpegPipe = null
        currentConfig = null
        decodedAudio = null

        mixer.resize(origMixerW, origMixerH)

        if (wasCancelled) {
            statusMessage = "Export was cancelled."
            isSuccess = false
        } else if (success) {
            statusMessage = "Export complete: ${config?.outputFile?.name}"
            isSuccess = true
        } else {
            val err = pipe?.getRecentErrorLines(3)
            statusMessage = "Export failed.${if (!err.isNullOrBlank()) " FFmpeg:\n$err" else " Check logs for details."}"
            isSuccess = false
        }
    }
}

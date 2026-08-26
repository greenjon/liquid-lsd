package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImInt
import imgui.type.ImString
import llm.slop.liquidlsd.export.*
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Renderer
import java.io.File
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Modal dialog for offline deterministic audio-to-video export.
 */
object VideoExportModal {

    private const val POPUP_ID = "Export Video##modal"

    private var isOpen = false

    private val audioPath = ImString("audio.wav", 512)
    private val outputPath = ImString("output.mp4", 512)

    private val resolutionPresets = VideoResolutionPreset.values()
    private val resolutionPresetNames = resolutionPresets.map { it.label }.toTypedArray()
    private val selectedResolutionIdx = ImInt(1) // Default to 1080p

    private val fpsOptions = intArrayOf(24, 30, 60, 120)
    private val fpsOptionLabels = arrayOf("24 FPS", "30 FPS", "60 FPS", "120 FPS")
    private val selectedFpsIdx = ImInt(2) // Default to 60 FPS

    private val codecOptions = VideoCodec.values()
    private val codecOptionLabels = arrayOf("H.264 (MP4)", "H.265 / HEVC (MP4)", "Apple ProRes 422 HQ (MOV)")
    private val selectedCodecIdx = ImInt(0) // Default H.264

    private val samplingOptions = intArrayOf(1, 2, 4, 8)
    private val samplingOptionLabels = arrayOf("1x (Off)", "2x (Fast Blur)", "4x (Standard Motion Blur)", "8x (Cinematic Motion Blur)")
    private val selectedSamplingIdx = ImInt(0)

    private var statusMessage: String? = null
    private var isSuccess: Boolean? = null

    fun open() {
        isOpen = true
        statusMessage = null
        isSuccess = null
        if (outputPath.get() == "output.mp4" || outputPath.get().isBlank()) {
            val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())
            outputPath.set("output/renders/liquid_lsd_$dateStr.mp4")
        }
        ImGui.openPopup(POPUP_ID)
    }

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, renderer: Renderer, displayW: Float, displayH: Float) {
        if (!isOpen) return

        val modalW = (640f * (session.uiTheme.baseSize / 15f)).coerceIn(580f, displayW * 0.95f)
        ImGui.setNextWindowPos(displayW * 0.5f, displayH * 0.5f, ImGuiCond.Always, 0.5f, 0.5f)
        ImGui.setNextWindowSize(modalW, 0f, ImGuiCond.Always)

        val flags = ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.AlwaysAutoResize

        if (!ImGui.beginPopupModal(POPUP_ID, flags)) {
            isOpen = false
            return
        }

        session.uiTheme.h1("Offline Video Export Studio")
        ImGui.separator()
        ImGui.spacing()

        if (OfflineRenderStudio.isRendering) {
            drawRenderingProgress(session)
        } else {
            drawConfigForm(session, mixer, renderer)
        }

        ImGui.endPopup()
    }

    private fun drawRenderingProgress(session: llm.slop.liquidlsd.SessionContext) {
        val prog = OfflineRenderStudio.progress
        session.uiTheme.h2("Rendering in Progress...")
        ImGui.spacing()

        ImGui.progressBar(prog.percent / 100f, -1f, 24f, "%.1f%% (Frame %d / %d)".format(prog.percent, prog.currentFrame, prog.totalFrames))
        ImGui.spacing()

        session.uiTheme.caption("Speed: %.1f FPS | ETA: %.0f seconds".format(prog.currentFps, prog.etaSeconds))
        ImGui.spacing()

        if (prog.previewTextureId != 0) {
            val previewAspect = 16f / 9f
            val previewW = 320f
            val previewH = previewW / previewAspect
            ImGui.image(prog.previewTextureId, previewW, previewH)
        }

        ImGui.spacing()
        if (ImGui.button("Cancel Export", 120f, 30f)) {
            OfflineRenderStudio.cancel()
        }
    }

    private fun drawConfigForm(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, renderer: Renderer) {
        session.uiTheme.caption("Deterministic offline audio-visual renderer with sample-accurate DSP and multi-sampling.")
        ImGui.spacing()

        // 1. Audio Source File
        session.uiTheme.body("Audio Source File:")
        ImGui.inputText("##AudioInput", audioPath)
        ImGui.sameLine()
        if (ImGui.button("Browse...##Audio")) {
            // Simple default pick helper or native dialog
            val audioDir = File("library/audio")
            audioDir.mkdirs()
            val files = audioDir.listFiles { _, name -> name.matches(Regex(".*\\.(wav|mp3|flac|ogg|m4a|aac)$", RegexOption.IGNORE_CASE)) }
            if (!files.isNullOrEmpty()) {
                audioPath.set(files.first().path)
            }
        }
        session.uiTheme.caption("Supports .wav, .mp3, .flac, .ogg, .m4a, .aac")
        ImGui.spacing()

        // 2. Output Video File
        session.uiTheme.body("Destination Video File:")
        ImGui.inputText("##VideoOutput", outputPath)
        ImGui.spacing()

        // 3. Resolution Preset
        session.uiTheme.body("Video Resolution:")
        ImGui.combo("##Resolution", selectedResolutionIdx, resolutionPresetNames)
        ImGui.spacing()

        // 4. Framerate & Codec
        session.uiTheme.body("Framerate & Codec:")
        ImGui.combo("##Framerate", selectedFpsIdx, fpsOptionLabels)
        ImGui.sameLine()
        ImGui.combo("##Codec", selectedCodecIdx, codecOptionLabels)
        ImGui.spacing()

        // 5. Motion Blur / Super-Sampling
        session.uiTheme.body("Temporal Super-Sampling (Motion Blur):")
        ImGui.combo("##SuperSampling", selectedSamplingIdx, samplingOptionLabels)
        session.uiTheme.caption("Renders multiple sub-frames per output frame to create smooth, natural motion trails.")
        ImGui.spacing()

        val currentStatus = statusMessage ?: OfflineRenderStudio.statusMessage
        val currentSuccess = if (statusMessage != null) isSuccess else OfflineRenderStudio.isSuccess

        currentStatus?.let { msg ->
            if (currentSuccess == true) {
                ImGui.textColored(0.2f, 0.9f, 0.2f, 1f, msg)
            } else {
                ImGui.textColored(0.9f, 0.2f, 0.2f, 1f, msg)
            }
            ImGui.spacing()
        }

        ImGui.separator()
        ImGui.spacing()

        if (ImGui.button("Start Export", 140f, 32f)) {
            val audioFile = File(audioPath.get())
            if (!audioFile.exists()) {
                statusMessage = "Audio file not found: ${audioFile.path}"
                isSuccess = false
            } else {
                statusMessage = null
                isSuccess = null
                val res = resolutionPresets[selectedResolutionIdx.get()]
                val chosenFps = fpsOptions[selectedFpsIdx.get()]
                val chosenCodec = codecOptions[selectedCodecIdx.get()]
                val chosenSampling = samplingOptions[selectedSamplingIdx.get()]
                val outFile = File(outputPath.get())
                outFile.parentFile?.mkdirs()

                val config = VideoExportConfig(
                    outputFile = outFile,
                    width = res.width,
                    height = res.height,
                    fps = chosenFps,
                    codec = chosenCodec,
                    audioFile = audioFile,
                    superSamplingFactor = chosenSampling
                )

                OfflineRenderStudio.startExport(config, mixer, renderer)
            }
        }

        ImGui.sameLine()
        if (ImGui.button("Close", 100f, 32f)) {
            isOpen = false
            ImGui.closeCurrentPopup()
        }
    }
}

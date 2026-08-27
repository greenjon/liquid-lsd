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

    private val audioPath = ImString("", 512)
    private val outputPath = ImString("", 512)
    private val presetPath = ImString("", 512)

    private val audioBrowser = ImGuiFileBrowser("##exportAudioBrowser")
    private val outputBrowser = ImGuiFileBrowser("##exportOutputBrowser")
    private val presetBrowser = ImGuiFileBrowser("##exportPresetBrowser")

    private val resolutionPresets = VideoResolutionPreset.values()
    private val selectedResolutionIdx = ImInt(0) // Default 0 = Match Project Canvas

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
        if (outputPath.get().isBlank()) {
            val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())
            val exportDir = File(UITheme.getDefaultVideosDirectory(), "renders").apply { mkdirs() }
            outputPath.set(File(exportDir, "liquid_lsd_export_$dateStr.mp4").absolutePath)
        }
        ImGui.openPopup(POPUP_ID)
    }

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, renderer: Renderer, displayW: Float, displayH: Float) {
        if (!isOpen) return

        val modalW = (660f * (session.uiTheme.baseSize / 15f)).coerceIn(600f, displayW * 0.95f)
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

        // Draw child file browsers at modal scope
        audioBrowser.draw { chosenFile ->
            audioPath.set(chosenFile.absolutePath)
        }

        outputBrowser.draw { chosenFile ->
            outputPath.set(chosenFile.absolutePath)
        }

        presetBrowser.draw { chosenFile ->
            presetPath.set(chosenFile.absolutePath)
        }

        ImGui.endPopup()
    }

    private fun drawRenderingProgress(session: llm.slop.liquidlsd.SessionContext) {
        val prog = OfflineRenderStudio.progress
        session.uiTheme.h2("Rendering in Progress...")
        ImGui.spacing()

        ImGui.progressBar(prog.percent / 100f, -1f, 24f, "%.1f%% (Frame %d / %d)".format(prog.percent, prog.currentFrame, prog.totalFrames))
        ImGui.spacing()

        val elapsedMin = prog.elapsedSeconds.toInt() / 60
        val elapsedSec = prog.elapsedSeconds.toInt() % 60
        val etaMin = prog.etaSeconds.toInt() / 60
        val etaSec = prog.etaSeconds.toInt() % 60

        session.uiTheme.body("Speed: %.1f FPS | Elapsed: %02d:%02d | ETA: %02d:%02d | File Size: %.1f MB"
            .format(prog.currentFps, elapsedMin, elapsedSec, etaMin, etaSec, prog.estimatedFileSizeMb))
        ImGui.spacing()

        if (prog.previewTextureId != 0) {
            val previewAspect = 16f / 9f
            val previewW = 340f
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
            val startDir = File("library/audio").takeIf { it.exists() } ?: File(System.getProperty("user.home") ?: ".")
            audioBrowser.open(
                mode = ImGuiFileBrowser.Mode.LOAD,
                startDir = startDir,
                extensions = listOf(".wav", ".mp3", ".flac", ".ogg", ".m4a", ".aac")
            )
        }
        session.uiTheme.caption("Select an audio track (.wav, .mp3, .flac, .ogg, .m4a, .aac)")
        ImGui.spacing()

        // 2. Preset / Setlist Snapshot (Optional)
        session.uiTheme.body("Preset Snapshot (Optional):")
        ImGui.inputText("##PresetInput", presetPath)
        ImGui.sameLine()
        if (ImGui.button("Browse...##Preset")) {
            val startDir = File("library/presets").takeIf { it.exists() } ?: File("library")
            presetBrowser.open(
                mode = ImGuiFileBrowser.Mode.LOAD,
                startDir = startDir,
                extensions = listOf(".lsd", ".json", ".lsdset")
            )
        }
        ImGui.sameLine()
        if (ImGui.button("Clear##Preset")) {
            presetPath.set("")
        }
        session.uiTheme.caption("Leave blank to render current live session, or select a .lsd preset / .lsdset setlist.")
        ImGui.spacing()

        // 3. Output Video File
        session.uiTheme.body("Destination Video File:")
        ImGui.inputText("##VideoOutput", outputPath)
        ImGui.sameLine()
        if (ImGui.button("Browse...##Output")) {
            val startDir = File(outputPath.get()).parentFile?.takeIf { it.exists() } ?: session.uiTheme.getDefaultVideosDirectory()
            val initialName = File(outputPath.get()).name.takeIf { it.isNotBlank() } ?: "liquid_lsd_export.mp4"
            outputBrowser.open(
                mode = ImGuiFileBrowser.Mode.SAVE,
                startDir = startDir,
                initialName = initialName,
                extensions = listOf(".mp4", ".mov")
            )
        }
        ImGui.spacing()

        // 4. Resolution Preset
        session.uiTheme.body("Video Resolution:")
        val resolutionLabels = arrayOf("Match Project Canvas (%dx%d)".format(session.uiTheme.renderWidth, session.uiTheme.renderHeight)) +
                resolutionPresets.map { it.label }.toTypedArray()
        ImGui.combo("##Resolution", selectedResolutionIdx, resolutionLabels)
        ImGui.spacing()

        // 5. Framerate & Codec
        session.uiTheme.body("Framerate & Codec:")
        ImGui.combo("##Framerate", selectedFpsIdx, fpsOptionLabels)
        ImGui.sameLine()
        ImGui.combo("##Codec", selectedCodecIdx, codecOptionLabels)
        ImGui.spacing()

        // 6. Motion Blur / Super-Sampling
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
            val audioFile = File(audioPath.get().trim())
            if (!audioFile.exists() || audioFile.isDirectory) {
                statusMessage = "Audio file not found: ${audioPath.get()}"
                isSuccess = false
            } else {
                statusMessage = null
                isSuccess = null

                // Optionally load selected preset snapshot
                val pPath = presetPath.get().trim()
                if (pPath.isNotBlank()) {
                    val pFile = File(pPath)
                    if (pFile.exists()) {
                        try {
                            session.presetManager.loadDeckPresetAsync(pFile, isDeckA = true)
                            logger.info { "Loaded preset snapshot for export: ${pFile.name}" }
                        } catch (e: Exception) {
                            logger.warn(e) { "Could not load preset snapshot before export: ${e.message}" }
                        }
                    }
                }

                val (exportW, exportH) = if (selectedResolutionIdx.get() == 0) {
                    session.uiTheme.renderWidth to session.uiTheme.renderHeight
                } else {
                    val res = resolutionPresets[selectedResolutionIdx.get() - 1]
                    res.width to res.height
                }

                val chosenFps = fpsOptions[selectedFpsIdx.get()]
                val chosenCodec = codecOptions[selectedCodecIdx.get()]
                val chosenSampling = samplingOptions[selectedSamplingIdx.get()]
                val outFile = File(outputPath.get().trim())
                outFile.parentFile?.mkdirs()

                val config = VideoExportConfig(
                    outputFile = outFile,
                    width = exportW,
                    height = exportH,
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

package llm.slop.liquidlsd.ui

import imgui.ImFont
import imgui.ImFontConfig
import imgui.ImGui
import imgui.ImGuiIO
import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.audio.AudioTarget
import llm.slop.liquidlsd.audio.BeatDetectionSettings
import mu.KotlinLogging
import java.io.File
import java.util.Properties

/**
 * Central typography / styling system for Liquid LSD.
 *
 * Six semantic text levels are defined, each backed by a separately loaded
 * ImFont at the correct pixel size. Call [loadFonts] once during ImGui
 * initialisation (before the backend renders the first frame). Call
 * [rebuildFonts] to hot-reload all fonts after the user changes sizes in the
 * Settings panel -- the backend will upload the new atlas texture on the next
 * frame automatically via imgui-java's GL3 renderer.
 *
 * Usage:
 *   session.uiTheme.h1("Main Title")
 *   session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.text("small note") }
 *
 * For coloured variants use withFont directly; the named helpers are just
 * convenience wrappers for the most common single-text-call pattern.
 */
object UITheme {

    private val logger = KotlinLogging.logger {}

    private val settingsFile = File("lsd-settings.properties")

    // -- Semantic Levels -------------------------------------------------------

    enum class FontLevel { H1, H2, H3, BODY, CAPTION, CODE }

    enum class AutoVjDirtyBehavior { SKIP, AUTO_DISCARD, AUTO_SAVE }

    enum class Theme {
        BORING,
        DARK_SOLARIZED,
        LIGHT_SOLARIZED,
        DARK_LUNARIZED,
        LIGHT_LUNARIZED,
        NEON
    }

    enum class ResolutionPreset(val displayName: String, val width: Int, val height: Int) {
        RES_1080P("1080p (1920x1080 - 16:9)", 1920, 1080),
        RES_720P("720p (1280x720 - 16:9)", 1280, 720),
        RES_540P("540p (960x540 - 16:9)", 960, 540),
        RES_1440P("1440p (2560x1440 - 16:9)", 2560, 1440),
        RES_4K("4K UHD (3840x2160 - 16:9)", 3840, 2160),
        RES_UXGA("UXGA (1600x1200 - 4:3)", 1600, 1200),
        RES_XGA("XGA (1024x768 - 4:3)", 1024, 768),
        RES_SVGA("SVGA (800x600 - 4:3)", 800, 600),
        RES_SQUARE_1080("Square (1080x1080 - 1:1)", 1080, 1080),
        RES_SQUARE_800("Square (800x800 - 1:1)", 800, 800),
        RES_SQUARE_600("Square (600x600 - 1:1)", 600, 600),
        CUSTOM("Custom", 1920, 1080)
    }

    enum class OutputScaleMode(val displayName: String) {
        FIT("Fit (Letterbox / Pillarbox)"),
        FILL("Fill (Crop)"),
        STRETCH("Stretch")
    }

    // -- Mutable sizing knobs (user-tweakable from Settings later) -------------

    @Volatile
    var settings = AppSettings()

    var theme: Theme
        get() = settings.theme
        set(value) { settings = settings.copy(theme = value) }

    const val BASE_FONT_PX = 15f

    @Volatile
    var systemDpiScale: Float = 1.0f

    var guiScalePercent: Int
        get() = settings.guiScalePercent
        set(value) { settings = settings.copy(guiScalePercent = value.coerceIn(75, 200)) }

    val baseSize: Float
        get() = BASE_FONT_PX * (guiScalePercent / 100f) * systemDpiScale

    var audioEngineEnabled: Boolean
        get() = settings.audioEngineEnabled
        set(value) { settings = settings.copy(audioEngineEnabled = value) }

    var backgroundVideoEnabled: Boolean
        get() = settings.backgroundVideoEnabled
        set(value) { settings = settings.copy(backgroundVideoEnabled = value) }

    var cleanModeEnabled: Boolean
        get() = settings.cleanModeEnabled
        set(value) { settings = settings.copy(cleanModeEnabled = value) }

    var randomizationEnabled: Boolean
        get() = settings.randomizationEnabled
        set(value) { settings = settings.copy(randomizationEnabled = value) }

    var sequencerEnabled: Boolean
        get() = settings.sequencerEnabled
        set(value) { settings = settings.copy(sequencerEnabled = value) }

    var midiEnabled: Boolean
        get() = settings.midiEnabled
        set(value) { settings = settings.copy(midiEnabled = value) }

    enum class QueueKeyTrigger { NONE, ARROWS, PAGE_UP_DOWN, SPACE_BACKSPACE }

    var autoVjDirtyBehavior: AutoVjDirtyBehavior
        get() = settings.autoVjDirtyBehavior
        set(value) { settings = settings.copy(autoVjDirtyBehavior = value) }
        
    var activeMidiProfile: String
        get() = settings.activeMidiProfile
        set(value) { settings = settings.copy(activeMidiProfile = value) }
        
    var queueKeyTrigger: QueueKeyTrigger
        get() = settings.queueKeyTrigger
        set(value) { settings = settings.copy(queueKeyTrigger = value) }
        
    var tooltipsEnabled: Boolean
        get() = settings.tooltipsEnabled
        set(value) { settings = settings.copy(tooltipsEnabled = value) }
        
    var maxFps: Int
        get() = settings.maxFps
        set(value) { settings = settings.copy(maxFps = value) }

    enum class StartupBehavior { PREVIOUS_SESSION, EMPTY }
    
    var startupBehavior: StartupBehavior
        get() = settings.startupBehavior
        set(value) { settings = settings.copy(startupBehavior = value) }

    enum class LibraryMode { FULL, HALF, HIDE }
    
    var libraryMode: LibraryMode
        get() = settings.libraryMode
        set(value) { settings = settings.copy(libraryMode = value) }

    var showMidiCol: Boolean
        get() = settings.showMidiCol
        set(value) { settings = settings.copy(showMidiCol = value) }

    var showLfoCol: Boolean
        get() = settings.showLfoCol
        set(value) { settings = settings.copy(showLfoCol = value) }

    var showSeqCol: Boolean
        get() = settings.showSeqCol
        set(value) { settings = settings.copy(showSeqCol = value) }

    var showAudioCol: Boolean
        get() = settings.showAudioCol
        set(value) { settings = settings.copy(showAudioCol = value) }

    var showTriggerCol: Boolean
        get() = settings.showTriggerCol
        set(value) { settings = settings.copy(showTriggerCol = value) }

    var col1Ratio: Float
        get() = settings.col1Ratio
        set(value) { settings = settings.copy(col1Ratio = value) }

    var col2Ratio: Float
        get() = settings.col2Ratio
        set(value) { settings = settings.copy(col2Ratio = value) }

    var libraryRatio: Float
        get() = settings.libraryRatio
        set(value) { settings = settings.copy(libraryRatio = value) }

    var lastCustomLibraryRatio: Float
        get() = settings.lastCustomLibraryRatio
        set(value) { settings = settings.copy(lastCustomLibraryRatio = value) }

    var gridCellRatio: Float
        get() = settings.gridCellRatio
        set(value) { settings = settings.copy(gridCellRatio = value) }

    var renderResolutionPreset: ResolutionPreset
        get() = settings.renderResolutionPreset
        set(value) { settings = settings.copy(renderResolutionPreset = value) }

    var customRenderWidth: Int
        get() = settings.customRenderWidth
        set(value) { settings = settings.copy(customRenderWidth = value.coerceIn(128, 7680)) }

    var customRenderHeight: Int
        get() = settings.customRenderHeight
        set(value) { settings = settings.copy(customRenderHeight = value.coerceIn(128, 4320)) }

    var outputScaleMode: OutputScaleMode
        get() = settings.outputScaleMode
        set(value) { settings = settings.copy(outputScaleMode = value) }

    var recordingDirectory: String
        get() = settings.recordingDirectory
        set(value) { settings = settings.copy(recordingDirectory = value) }

    var recordingIncludeAudio: Boolean
        get() = settings.recordingIncludeAudio
        set(value) { settings = settings.copy(recordingIncludeAudio = value) }

    var recordingBitrateMbps: Int
        get() = settings.recordingBitrateMbps
        set(value) { settings = settings.copy(recordingBitrateMbps = value.coerceIn(2, 100)) }

    var recordingFps: Int
        get() = settings.recordingFps
        set(value) { settings = settings.copy(recordingFps = if (value == 30) 30 else 60) }

    var settingsWidth: Float
        get() = settings.settingsWidth
        set(value) { settings = settings.copy(settingsWidth = value.coerceIn(400f, 3840f)) }

    var settingsHeight: Float
        get() = settings.settingsHeight
        set(value) { settings = settings.copy(settingsHeight = value.coerceIn(300f, 2160f)) }

    var framelessWindow: Boolean
        get() = settings.framelessWindow
        set(value) { settings = settings.copy(framelessWindow = value) }

    fun getDefaultVideosDirectory(): File {
        val configured = settings.recordingDirectory.trim()
        if (configured.isNotBlank()) {
            val f = File(configured)
            if (f.exists() || f.mkdirs()) return f
        }
        val xdg = System.getenv("XDG_VIDEOS_DIR")
        if (!xdg.isNullOrBlank()) {
            val dir = File(xdg, "liquid-lsd")
            if (dir.exists() || dir.mkdirs()) return dir
        }
        val userHome = System.getProperty("user.home") ?: "."
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        val standardDir = if (os.contains("mac")) File(userHome, "Movies/liquid-lsd") else File(userHome, "Videos/liquid-lsd")
        return try {
            standardDir.mkdirs()
            if (standardDir.isDirectory) standardDir else File("output/recordings").apply { mkdirs() }
        } catch (e: Exception) {
            File("output/recordings").apply { mkdirs() }
        }
    }

    val renderWidth: Int
        get() = if (renderResolutionPreset == ResolutionPreset.CUSTOM) customRenderWidth else renderResolutionPreset.width

    val renderHeight: Int
        get() = if (renderResolutionPreset == ResolutionPreset.CUSTOM) customRenderHeight else renderResolutionPreset.height

    val renderAspectRatio: Float
        get() = if (renderWidth > 0) renderHeight.toFloat() / renderWidth.toFloat() else 9f / 16f

    init {
        loadSettings()
    }

    private fun Properties.getBoolean(key: String): Boolean? {
        val raw = getProperty(key)?.trim() ?: return null
        return raw.toBooleanStrictOrNull() ?: raw.toBoolean()
    }

    private fun loadSettings() {
        try {
            if (settingsFile.exists()) {
                val props = Properties()
                settingsFile.inputStream().use { props.load(it) }
                val savedPct = props.getProperty("guiScalePercent")?.toIntOrNull()
                if (savedPct != null) {
                    guiScalePercent = savedPct.coerceIn(75, 200)
                    logger.info { "Loaded guiScalePercent from settings file: $guiScalePercent%" }
                } else {
                    val savedSize = props.getProperty("baseSize")?.toFloatOrNull()
                    if (savedSize != null) {
                        guiScalePercent = ((savedSize / BASE_FONT_PX) * 100f).toInt().coerceIn(75, 200)
                        logger.info { "Migrated baseSize ($savedSize px) to guiScalePercent: $guiScalePercent%" }
                    }
                }
                val savedAudio = props.getBoolean("audioEngineEnabled")
                if (savedAudio != null) {
                    audioEngineEnabled = savedAudio
                    logger.info { "Loaded audioEngineEnabled from settings file: $audioEngineEnabled" }
                }
                val savedBackend = props.getProperty("audioBackend")
                if (savedBackend != null) {
                    try {
                        AudioEngine.backendMode = AudioEngine.AudioBackendMode.valueOf(savedBackend)
                        logger.info { "Loaded audioBackend from settings: ${AudioEngine.backendMode}" }
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to parse audioBackend '$savedBackend'" }
                    }
                }
                val savedDevice = props.getProperty("audioDeviceName")
                if (savedDevice != null) {
                    AudioEngine.selectedDeviceName = if (savedDevice.isBlank()) null else savedDevice
                    logger.info { "Loaded audioDeviceName from settings: ${AudioEngine.selectedDeviceName ?: "<default>"}" }
                }
                val savedGain = props.getProperty("audioInputGain")?.toFloatOrNull()
                if (savedGain != null) {
                    AudioEngine.inputGain = savedGain.coerceIn(0.0f, 10.0f)
                    logger.info { "Loaded audioInputGain from settings file: $savedGain" }
                }
                props.getBoolean("audioBpmLocked")?.let { savedBpmLocked ->
                    AudioEngine.isBpmLocked = savedBpmLocked
                }
                props.getProperty("audioManualBpm")?.toFloatOrNull()?.let { savedManualBpm ->
                    AudioEngine.manualBpm = savedManualBpm.coerceIn(40f, 200f)
                    AudioEngine.setBpmDirectly(AudioEngine.manualBpm)
                    logger.info { "Loaded audioManualBpm from settings: $savedManualBpm" }
                }

                val savedBeatTarget = props.getProperty("audioBeatTarget")?.let {
                    try { AudioTarget.valueOf(it) } catch (e: Exception) { null }
                }
                val savedFloor = props.getProperty("audioBpmFloor")?.toIntOrNull()?.coerceIn(40, 240)
                val savedCeiling = props.getProperty("audioBpmCeiling")?.toIntOrNull()?.coerceIn(40, 240)
                val savedAlpha = props.getProperty("audioTransitionAlpha")?.toFloatOrNull()
                val savedInertia = props.getProperty("audioTrackingInertia")?.toFloatOrNull()

                val currentDetectorSettings = AudioEngine.beatDetector.settings
                AudioEngine.beatDetector.applyPreset(
                    BeatDetectionSettings(
                        target = savedBeatTarget ?: currentDetectorSettings.target,
                        bpmSearchFloor = savedFloor ?: currentDetectorSettings.bpmSearchFloor,
                        bpmSearchCeiling = savedCeiling ?: currentDetectorSettings.bpmSearchCeiling,
                        transitionWeightAlpha = savedAlpha ?: currentDetectorSettings.transitionWeightAlpha,
                        trackingInertiaBpmPerBeat = savedInertia ?: currentDetectorSettings.trackingInertiaBpmPerBeat
                    )
                )

                val savedBgVideo = props.getBoolean("backgroundVideoEnabled")
                if (savedBgVideo != null) {
                    backgroundVideoEnabled = savedBgVideo
                    logger.info { "Loaded backgroundVideoEnabled from settings file: $backgroundVideoEnabled" }
                }

                val savedCleanMode = props.getBoolean("cleanModeEnabled")
                if (savedCleanMode != null) {
                    cleanModeEnabled = savedCleanMode
                    logger.info { "Loaded cleanModeEnabled from settings file: $cleanModeEnabled" }
                }

                val savedRandomization = props.getBoolean("randomizationEnabled")
                if (savedRandomization != null) {
                    randomizationEnabled = savedRandomization
                    logger.info { "Loaded randomizationEnabled from settings file: $randomizationEnabled" }
                }

                val savedSequencer = props.getBoolean("sequencerEnabled")
                if (savedSequencer != null) {
                    sequencerEnabled = savedSequencer
                    logger.info { "Loaded sequencerEnabled from settings file: $sequencerEnabled" }
                }

                val savedMidi = props.getBoolean("midiEnabled")
                if (savedMidi != null) {
                    midiEnabled = savedMidi
                    logger.info { "Loaded midiEnabled from settings file: $midiEnabled" }
                }


                val savedTooltips = props.getBoolean("tooltipsEnabled")
                if (savedTooltips != null) {
                    tooltipsEnabled = savedTooltips
                    logger.info { "Loaded tooltipsEnabled from settings file: $savedTooltips" }
                }
                val savedMaxFps = props.getProperty("maxFps")?.toIntOrNull()
                if (savedMaxFps != null) {
                    maxFps = if (savedMaxFps == 60) 60 else 30
                    logger.info { "Loaded maxFps from settings file: $maxFps" }
                }
                val savedMode = props.getProperty("libraryMode") ?: props.getProperty("assetBrowserMode")
                if (savedMode != null) {
                    libraryMode = try { LibraryMode.valueOf(savedMode) } catch (e: Exception) { LibraryMode.HALF }
                    logger.info { "Loaded libraryMode from settings file: $libraryMode" }
                } else {
                    val savedHalfHeight = props.getBoolean("assetManagerHalfHeight")
                    if (savedHalfHeight != null) {
                        libraryMode = if (savedHalfHeight) LibraryMode.HALF else LibraryMode.FULL
                        logger.info { "Migrated assetManagerHalfHeight to libraryMode: $libraryMode" }
                    }
                }
                val savedAutoVj = props.getProperty("autoVjDirtyBehavior")
                if (savedAutoVj != null) {
                    autoVjDirtyBehavior = try { AutoVjDirtyBehavior.valueOf(savedAutoVj) } catch (e: Exception) { AutoVjDirtyBehavior.AUTO_DISCARD }
                    logger.info { "Loaded autoVjDirtyBehavior from settings file: $autoVjDirtyBehavior" }
                }
                val savedProfile = props.getProperty("activeMidiProfile")
                if (savedProfile != null) {
                    activeMidiProfile = savedProfile
                }
                val savedKeyTrigger = props.getProperty("queueKeyTrigger")
                if (savedKeyTrigger != null) {
                    queueKeyTrigger = try { QueueKeyTrigger.valueOf(savedKeyTrigger) } catch (e: Exception) { QueueKeyTrigger.NONE }
                }
                val savedStartup = props.getProperty("startupBehavior")
                if (savedStartup != null) {
                    startupBehavior = try { StartupBehavior.valueOf(savedStartup) } catch (e: Exception) { StartupBehavior.PREVIOUS_SESSION }
                    logger.info { "Loaded startupBehavior from settings file: $startupBehavior" }
                }
                val savedTheme = props.getProperty("theme")
                if (savedTheme != null) {
                    theme = try { Theme.valueOf(savedTheme) } catch (e: Exception) { Theme.BORING }
                    logger.info { "Loaded theme from settings file: $theme" }
                }
                props.getBoolean("showMidiCol")?.let { showMidiCol = it }
                props.getBoolean("showLfoCol")?.let { showLfoCol = it }
                props.getBoolean("showSeqCol")?.let { showSeqCol = it }
                props.getBoolean("showAudioCol")?.let { showAudioCol = it }
                props.getBoolean("showTriggerCol")?.let { showTriggerCol = it }
                props.getProperty("col1Ratio")?.toFloatOrNull()?.let { col1Ratio = it.coerceIn(0.10f, 0.70f) }
                props.getProperty("col2Ratio")?.toFloatOrNull()?.let { col2Ratio = it.coerceIn(0.10f, 0.70f) }
                (props.getProperty("libraryRatio") ?: props.getProperty("assetBrowserRatio"))?.toFloatOrNull()?.let { libraryRatio = it.coerceIn(0.10f, 0.90f) }
                (props.getProperty("lastCustomLibraryRatio") ?: props.getProperty("lastCustomAssetBrowserRatio"))?.toFloatOrNull()?.let { lastCustomLibraryRatio = it.coerceIn(0.10f, 0.90f) }
                props.getProperty("gridCellRatio")?.toFloatOrNull()?.let { gridCellRatio = it.coerceIn(0.70f, 2.00f) }
                props.getProperty("renderResolutionPreset")?.let { saved ->
                    renderResolutionPreset = try { ResolutionPreset.valueOf(saved) } catch (e: Exception) { ResolutionPreset.RES_1080P }
                }
                props.getProperty("customRenderWidth")?.toIntOrNull()?.let { customRenderWidth = it.coerceIn(128, 7680) }
                props.getProperty("customRenderHeight")?.toIntOrNull()?.let { customRenderHeight = it.coerceIn(128, 4320) }
                props.getProperty("outputScaleMode")?.let { saved ->
                    outputScaleMode = try { OutputScaleMode.valueOf(saved) } catch (e: Exception) { OutputScaleMode.FIT }
                }
                props.getProperty("recordingDirectory")?.let { recordingDirectory = it }
                props.getBoolean("recordingIncludeAudio")?.let { recordingIncludeAudio = it }
                props.getProperty("recordingBitrateMbps")?.toIntOrNull()?.let { recordingBitrateMbps = it }
                props.getProperty("recordingFps")?.toIntOrNull()?.let { recordingFps = it }
                props.getProperty("settingsWidth")?.toFloatOrNull()?.let { settingsWidth = it.coerceIn(400f, 3840f) }
                props.getProperty("settingsHeight")?.toFloatOrNull()?.let { settingsHeight = it.coerceIn(300f, 2160f) }
                props.getBoolean("framelessWindow")?.let { framelessWindow = it }
            } else {
                logger.info { "No settings file found, using default baseSize: $baseSize, audioEngineEnabled: $audioEngineEnabled, backgroundVideoEnabled: $backgroundVideoEnabled, tooltipsEnabled: $tooltipsEnabled, maxFps: $maxFps, framelessWindow: $framelessWindow" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load settings, using defaults" }
        }
    }

    fun saveSettings() {
        try {
            val props = Properties()
            if (settingsFile.exists()) {
                settingsFile.inputStream().use { props.load(it) }
            }
            props.setProperty("guiScalePercent", guiScalePercent.toString())
            props.setProperty("baseSize", baseSize.toString())
            props.setProperty("audioEngineEnabled", audioEngineEnabled.toString())
            props.setProperty("audioBackend", AudioEngine.backendMode.name)
            props.setProperty("audioDeviceName", AudioEngine.selectedDeviceName ?: "")
            props.setProperty("audioInputGain", AudioEngine.inputGain.toString())
            props.setProperty("audioBpmLocked", AudioEngine.isBpmLocked.toString())
            props.setProperty("audioManualBpm", AudioEngine.manualBpm.toString())
            props.setProperty("audioBeatTarget", AudioEngine.beatDetector.settings.target.name)
            props.setProperty("audioBpmFloor", AudioEngine.beatDetector.settings.bpmSearchFloor.toString())
            props.setProperty("audioBpmCeiling", AudioEngine.beatDetector.settings.bpmSearchCeiling.toString())
            props.setProperty("audioTransitionAlpha", AudioEngine.beatDetector.settings.transitionWeightAlpha.toString())
            props.setProperty("audioTrackingInertia", AudioEngine.beatDetector.settings.trackingInertiaBpmPerBeat.toString())
            props.setProperty("backgroundVideoEnabled", backgroundVideoEnabled.toString())
            props.setProperty("cleanModeEnabled", cleanModeEnabled.toString())
            props.setProperty("randomizationEnabled", randomizationEnabled.toString())
            props.setProperty("sequencerEnabled", sequencerEnabled.toString())
            props.setProperty("midiEnabled", midiEnabled.toString())
            props.setProperty("tooltipsEnabled", tooltipsEnabled.toString())
            props.setProperty("maxFps", maxFps.toString())
            props.setProperty("libraryMode", libraryMode.name)
            props.setProperty("autoVjDirtyBehavior", autoVjDirtyBehavior.name)
            props.setProperty("activeMidiProfile", activeMidiProfile)
            props.setProperty("queueKeyTrigger", queueKeyTrigger.name)
            props.setProperty("startupBehavior", startupBehavior.name)
            props.setProperty("theme", theme.name)
            props.setProperty("showMidiCol", showMidiCol.toString())
            props.setProperty("showLfoCol", showLfoCol.toString())
            props.setProperty("showSeqCol", showSeqCol.toString())
            props.setProperty("showAudioCol", showAudioCol.toString())
            props.setProperty("showTriggerCol", showTriggerCol.toString())
            props.setProperty("col1Ratio", col1Ratio.toString())
            props.setProperty("col2Ratio", col2Ratio.toString())
            props.setProperty("libraryRatio", libraryRatio.toString())
            props.setProperty("lastCustomLibraryRatio", lastCustomLibraryRatio.toString())
            props.setProperty("gridCellRatio", gridCellRatio.toString())
            props.setProperty("renderResolutionPreset", renderResolutionPreset.name)
            props.setProperty("customRenderWidth", customRenderWidth.toString())
            props.setProperty("customRenderHeight", customRenderHeight.toString())
            props.setProperty("outputScaleMode", outputScaleMode.name)
            props.setProperty("recordingDirectory", recordingDirectory)
            props.setProperty("recordingIncludeAudio", recordingIncludeAudio.toString())
            props.setProperty("recordingBitrateMbps", recordingBitrateMbps.toString())
            props.setProperty("recordingFps", recordingFps.toString())
            props.setProperty("settingsWidth", settingsWidth.toString())
            props.setProperty("settingsHeight", settingsHeight.toString())
            props.setProperty("framelessWindow", framelessWindow.toString())
            val tmpFile = File("${settingsFile.absolutePath}.tmp")
            tmpFile.outputStream().use { props.store(it, "Liquid LSD Settings") }
            java.nio.file.Files.move(
                tmpFile.toPath(),
                settingsFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
            logger.info { "Saved settings to file" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save settings" }
        }
    }

    /** Per-level multipliers. Changing these + calling rebuildFonts() is all
     *  the Settings panel needs to do. */
    var multH1:      Float = 1.60f
    var multH2:      Float = 1.30f
    var multH3:      Float = 1.12f
    var multBody:    Float = 1.00f
    var multCaption: Float = 0.85f
    var multCode:    Float = 1.00f   // code always body-sized but different face

    // -- Loaded fonts (initialised by loadFonts) -------------------------------

    private lateinit var fontH1:      ImFont
    private lateinit var fontH2:      ImFont
    private lateinit var fontH3:      ImFont
    private lateinit var fontBody:    ImFont
    private lateinit var fontCaption: ImFont
    private lateinit var fontCode:    ImFont

    // Keep raw bytes of loaded fonts and ranges permanently alive to prevent GC/JNI unpinning segfaults
    private var regularBytes: ByteArray? = null
    private var mediumBytes: ByteArray? = null
    private var boldBytes: ByteArray? = null
    private var codeBytes: ByteArray? = null
    private var lucideBytes: ByteArray? = null

    // Range for standard Lucide (E000 - E7FF covers all Lucide icons used in the app)
    // Range format is [start, end, ..., 0]
    private val ICON_RANGE = shortArrayOf(0xe000.toShort(), 0xe7ff.toShort(), 0)

    // Glyph ranges for main TTF fonts: Basic Latin, Extended Latin, General Punctuation, Arrows, Math, Geometric Shapes
    private val MAIN_RANGES = shortArrayOf(
        0x0020.toShort(), 0x00FF.toShort(), // Basic Latin + Latin-1 Supplement
        0x0100.toShort(), 0x017F.toShort(), // Latin Extended-A
        0x2000.toShort(), 0x206F.toShort(), // General Punctuation (dashes, quotes, bullets, ellipses)
        0x2190.toShort(), 0x21FF.toShort(), // Arrows (←, ↑, →, ↓)
        0x2200.toShort(), 0x22FF.toShort(), // Mathematical Operators (±, −, ×, etc.)
        0x25A0.toShort(), 0x25FF.toShort(), // Geometric Shapes (▶, ▼, ●, ○, ◻, ❐)
        0x2700.toShort(), 0x27BF.toShort(), // Dingbats (✕, ✔, etc.)
        0
    )

    /** True once [loadFonts] has completed successfully. */
    var isLoaded: Boolean = false
        private set

    // -- Font resource paths (classpath-relative, inside resources/fonts/) -----

    private const val INTER_REGULAR = "/fonts/Inter-Regular.ttf"
    private const val INTER_MEDIUM  = "/fonts/Inter-Medium.ttf"
    private const val INTER_BOLD    = "/fonts/Inter-Bold.ttf"
    private const val JETBRAINS     = "/fonts/JetBrainsMono-Regular.ttf"
    private const val LUCIDE         = "/fonts/lucide.ttf"

    // -- Initialisation --------------------------------------------------------

    private fun loadFontBytesOnce() {
        if (regularBytes != null && mediumBytes != null && boldBytes != null && codeBytes != null && lucideBytes != null) {
            return
        }
        fun loadBytes(resource: String): ByteArray {
            val stream = UITheme::class.java.getResourceAsStream(resource)
                ?: error("Font resource not found on classpath: $resource")
            return stream.use { it.readBytes() }
        }
        regularBytes = loadBytes(INTER_REGULAR)
        mediumBytes  = loadBytes(INTER_MEDIUM)
        boldBytes    = loadBytes(INTER_BOLD)
        codeBytes    = loadBytes(JETBRAINS)
        lucideBytes  = loadBytes(LUCIDE)
        logger.info { "Font bytes loaded permanently: Inter=${regularBytes!!.size}, Lucide=${lucideBytes!!.size}" }
    }

    /**
     * Loads all six font levels into ImGui's font atlas.
     * Must be called after [ImGui.createContext] but before the GL3 backend
     * initialises (i.e. before [imguiGl3.init]), or after a [rebuildFonts]
     * cycle (atlas clear -> reload -> GL3 re-upload).
     *
     * imgui-java's GL3 backend will call [ImFontAtlas.build] and upload the
     * texture automatically on the first render call after init.
     */
    fun loadFonts(io: ImGuiIO) {
        val atlas = io.fonts
        loadFontBytesOnce()

        fun cfg(owned: Boolean = false): ImFontConfig = ImFontConfig().apply {
            setFontDataOwnedByAtlas(owned)
        }

        fun addFont(bytes: ByteArray, size: Float, config: ImFontConfig, withIcons: Boolean = true): ImFont {
            val safeSize = size.coerceIn(9f, 64f)
            val f = atlas.addFontFromMemoryTTF(bytes, safeSize, config, MAIN_RANGES)
            if (f.ptr == 0L) logger.error { "Failed to load main font at size $safeSize" }
            config.destroy()

            if (withIcons) {
                val iconCfg = ImFontConfig().apply {
                    setFontDataOwnedByAtlas(false)
                    setMergeMode(true)
                    setPixelSnapH(true)
                    // Offset icon glyphs downward proportionally so they are vertically centered
                    // and do not collide with the top border of buttons or bounding frames.
                    setGlyphOffset(0f, kotlin.math.round(safeSize * 0.18f))
                }
                
                val iconFont = atlas.addFontFromMemoryTTF(lucideBytes!!, safeSize, iconCfg, ICON_RANGE)
                if (iconFont.ptr == 0L) logger.error { "Failed to merge Lucide icons at size $safeSize" }
                
                iconCfg.destroy()
            }
            return f
        }

        // Load each level; bodies/captions/H3 use icons, large headers and code don't duplicate icons.
        fontBody    = addFont(regularBytes!!, baseSize * multBody,    cfg(), withIcons = true)
        fontCaption = addFont(regularBytes!!, baseSize * multCaption, cfg(), withIcons = true)
        fontH3      = addFont(mediumBytes!!,  baseSize * multH3,      cfg(), withIcons = true)
        fontH2      = addFont(boldBytes!!,    baseSize * multH2,      cfg(), withIcons = false)
        fontH1      = addFont(boldBytes!!,    baseSize * multH1,      cfg(), withIcons = false)
        fontCode    = addFont(codeBytes!!,    baseSize * multCode,    cfg(), withIcons = false)

        isLoaded = true
        logger.info {
            "UITheme fonts loaded -- base=${baseSize}px  " +
            "H1=${(baseSize * multH1).toInt()}  H2=${(baseSize * multH2).toInt()}  " +
            "H3=${(baseSize * multH3).toInt()}  Body=${(baseSize * multBody).toInt()}  " +
            "Caption=${(baseSize * multCaption).toInt()}  Code=${(baseSize * multCode).toInt()}"
        }
    }

    /**
     * Clears the font atlas and reloads all fonts at the current [baseSize] /
     * multiplier values. Call this from the Settings panel whenever the user
     * commits a size change. The GL3 backend will detect the atlas dirty flag
     * and re-upload the texture on the very next frame.
     */
    fun rebuildFonts(io: ImGuiIO) {
        isLoaded = false
        io.fonts.clear()
        loadFonts(io)
        // Instruct the backend to re-upload by clearing the cached texture.
        // imgui-java's ImGuiImplGl3 checks for this automatically each frame.
        io.fonts.build()
        logger.info { "UITheme fonts rebuilt at baseSize=$baseSize" }
    }

    // -- Core rendering primitive ----------------------------------------------

    /** Resolve a [FontLevel] to its loaded [ImFont]. Falls back to the ImGui
     *  default font if [loadFonts] has not been called yet. */
    fun fontFor(level: FontLevel): ImFont? = if (!isLoaded) null else when (level) {
        FontLevel.H1      -> fontH1
        FontLevel.H2      -> fontH2
        FontLevel.H3      -> fontH3
        FontLevel.BODY    -> fontBody
        FontLevel.CAPTION -> fontCaption
        FontLevel.CODE    -> fontCode
    }

    /**
     * Pushes [level]'s font, executes [block], then pops. Safe to call before
     * [loadFonts] -- falls back to the current ImGui default font gracefully.
     */
    inline fun <T> withFont(level: FontLevel, block: () -> T): T {
        val font = fontFor(level)
        val pushed = font != null && font.ptr != 0L
        if (pushed) ImGui.pushFont(font)
        try {
            return block()
        } finally {
            if (pushed) ImGui.popFont()
        }
    }

    // -- Semantic text helpers -------------------------------------------------

    fun h1(text: String)      = withFont(FontLevel.H1)      { ImGui.text(text) }
    fun h2(text: String)      = withFont(FontLevel.H2)      { ImGui.text(text) }
    fun h3(text: String)      = withFont(FontLevel.H3)      { ImGui.text(text) }
    fun body(text: String)    = withFont(FontLevel.BODY)    { ImGui.text(text) }
    fun caption(text: String) = withFont(FontLevel.CAPTION) { ImGui.textDisabled(text) }
    fun code(text: String)    = withFont(FontLevel.CODE)    { ImGui.text(text) }

    // -- Coloured variants -----------------------------------------------------

    fun h1Colored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.H1) { ImGui.textColored(r, g, b, a, text) }

    fun h2Colored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.H2) { ImGui.textColored(r, g, b, a, text) }

    fun h3Colored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.H3) { ImGui.textColored(r, g, b, a, text) }

    fun bodyColored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.BODY) { ImGui.textColored(r, g, b, a, text) }

    fun captionColored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.CAPTION) { ImGui.textColored(r, g, b, a, text) }

    fun codeColored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.CODE) { ImGui.textColored(r, g, b, a, text) }
}

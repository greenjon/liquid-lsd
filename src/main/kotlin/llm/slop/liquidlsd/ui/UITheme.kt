package llm.slop.liquidlsd.ui

import imgui.ImFont
import imgui.ImFontConfig
import imgui.ImGui
import imgui.ImGuiIO
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.audio.AudioEngine
import mu.KotlinLogging
import java.io.File

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

    // -- Semantic Levels -------------------------------------------------------

    enum class FontLevel { H1, H2, H3, BODY, CAPTION, CODE, PRESET_NAME }

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

    // -- Sizing knobs & typography constants -----------------------------------

    const val FONT_CAPTION = 12f
    const val FONT_BODY    = 14f
    const val FONT_CODE    = 14f
    const val FONT_H3      = 15f
    const val FONT_H2      = 18f
    const val FONT_H1      = 22f

    const val BASE_FONT_PX = 14f
    const val BASE_SIZE    = 14.25f
    val baseSize: Float get() = BASE_SIZE

    @Volatile
    var preferences = AppPreferences()

    var settings: AppPreferences
        get() = preferences
        set(value) { preferences = value }

    var theme: Theme
        get() = settings.theme
        set(value) { settings = settings.copy(theme = value) }

    var presetNameScalePercent: Int
        get() = settings.presetNameScalePercent
        set(value) { settings = settings.copy(presetNameScalePercent = (kotlin.math.round(value / 10f) * 10).toInt().coerceIn(80, 120)) }

    var audioEngineEnabled: Boolean
        get() = settings.audioEngineEnabled
        set(value) { settings = settings.copy(audioEngineEnabled = value) }

    var audioChannelRouting: llm.slop.liquidlsd.audio.AudioChannelRouting
        get() = AudioEngine.channelRouting
        set(value) {
            AudioEngine.channelRouting = value
            settings = settings.copy(audioChannelRouting = value)
        }

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

    var autoVjDirtyBehavior: AutoVjDirtyBehavior
        get() = settings.autoVjDirtyBehavior
        set(value) { settings = settings.copy(autoVjDirtyBehavior = value) }
        
    var activeMidiProfile: String
        get() = settings.activeMidiProfile
        set(value) { settings = settings.copy(activeMidiProfile = value) }
        
    enum class QueueKeyTrigger { NONE, ARROWS, PAGE_UP_DOWN, SPACE_BACKSPACE }

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

    /** Column 3 dual-mode header toggle (see docs/user_guide/macros_and_rack.md). */
    enum class Column3Mode { MIXER, MACROS }

    var column3Mode: Column3Mode
        get() = settings.column3Mode
        set(value) { settings = settings.copy(column3Mode = value) }

    /** Primary workspace view layout mode: Classic 3-column Suite C vs Performance Mode (RACK name kept for save-file compatibility). */
    enum class WorkspaceMode { CLASSIC, RACK }

    var workspaceMode: WorkspaceMode
        get() = settings.workspaceMode
        set(value) { settings = settings.copy(workspaceMode = value) }

    /** Index of the active tab in the Performance Mode 4×4 Matrix (see [PerformanceMatrixPanel.Tab]). */
    var performanceMatrixTab: Int
        get() = settings.performanceMatrixTab
        set(value) { settings = settings.copy(performanceMatrixTab = value.coerceIn(0, PerformanceMatrixPanel.Tab.entries.size - 1)) }

    /** Modular Rack: whether opening one module's Bay/Deep Edit auto-collapses the others (see [llm.slop.liquidlsd.ui.rack.UnifiedRackPanel]). */
    var rackSoloMode: Boolean
        get() = settings.rackSoloMode
        set(value) { settings = settings.copy(rackSoloMode = value) }

    /** Modular Rack: persisted moduleId -> [ParametersState.DisclosureLevel] name (BAY/DEEP_EDIT only). */
    var rackExpandedModules: Map<String, String>
        get() = settings.rackExpandedModules
        set(value) { settings = settings.copy(rackExpandedModules = value) }

    var showMidiCol: Boolean
        get() = settings.midiEnabled
        set(value) { settings = settings.copy(midiEnabled = value, showMidiCol = value) }

    var showLfoCol: Boolean
        get() = settings.showLfoCol
        set(value) { settings = settings.copy(showLfoCol = value) }

    var showSeqCol: Boolean
        get() = settings.sequencerEnabled
        set(value) { settings = settings.copy(sequencerEnabled = value, showSeqCol = value) }

    var showAudioCol: Boolean
        get() = settings.audioEngineEnabled
        set(value) { settings = settings.copy(audioEngineEnabled = value, showAudioCol = value) }

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

    var preferencesWidth: Float
        get() = preferences.preferencesWidth
        set(value) { preferences = preferences.copy(preferencesWidth = value.coerceIn(400f, 3840f)) }

    var preferencesHeight: Float
        get() = preferences.preferencesHeight
        set(value) { preferences = preferences.copy(preferencesHeight = value.coerceIn(300f, 2160f)) }

    var settingsWidth: Float
        get() = preferencesWidth
        set(value) { preferencesWidth = value }

    var settingsHeight: Float
        get() = preferencesHeight
        set(value) { preferencesHeight = value }

    var framelessWindow: Boolean
        get() = settings.framelessWindow
        set(value) { settings = settings.copy(framelessWindow = value) }

    var trackpadConsoleEnabled: Boolean
        get() = settings.trackpadConsoleEnabled
        set(value) { settings = settings.copy(trackpadConsoleEnabled = value) }

    var checkUpdatesOnStartup: Boolean
        get() = settings.checkUpdatesOnStartup
        set(value) { settings = settings.copy(checkUpdatesOnStartup = value) }

    var ignoredUpdateVersion: String
        get() = settings.ignoredUpdateVersion
        set(value) { settings = settings.copy(ignoredUpdateVersion = value) }

    var videoOutputConfigs: Map<llm.slop.liquidlsd.rendering.VideoOutputEndpoint, llm.slop.liquidlsd.rendering.VideoOutputConfig>
        get() = settings.videoOutputConfigs
        set(value) { settings = settings.copy(videoOutputConfigs = value) }

    fun updateVideoOutputConfig(endpoint: llm.slop.liquidlsd.rendering.VideoOutputEndpoint, config: llm.slop.liquidlsd.rendering.VideoOutputConfig) {
        val nextMap = settings.videoOutputConfigs.toMutableMap()
        nextMap[endpoint] = config
        videoOutputConfigs = nextMap
    }

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
        AppPreferencesStore.loadPreferences()
    }

    // -- Loaded fonts (initialised by loadFonts) -------------------------------

    private lateinit var fontH1:         ImFont
    private lateinit var fontH2:         ImFont
    private lateinit var fontH3:         ImFont
    private lateinit var fontBody:       ImFont
    private lateinit var fontCaption:    ImFont
    private lateinit var fontCode:       ImFont
    private lateinit var fontPresetName: ImFont

    // Keep raw bytes of loaded fonts and ranges permanently alive to prevent GC/JNI unpinning segfaults
    private var regularBytes: ByteArray? = null
    private var mediumBytes: ByteArray? = null
    private var boldBytes: ByteArray? = null
    private var codeBytes: ByteArray? = null
    private var lucideBytes: ByteArray? = null

    // Baking the *entire* E000-E7FF Lucide PUA block (2048 codepoints) into every
    // merged font size was pure waste: only a few dozen codepoints are ever used.
    // Building a tight range from only the codepoints Icons.kt actually references
    // keeps the atlas small. (This does NOT fix the separate chevron-glyph
    // corruption -- see the CHEVRON_UP/CHEVRON_DOWN note in Icons.kt.)
    // Range format is [start, end, ..., 0]
    private val ICON_RANGE: ShortArray = run {
        val codepoints = sortedSetOf<Int>()
        for (field in Icons::class.java.fields) {
            val value = field.get(null)
            if (value is String) {
                value.codePoints().forEach { cp -> if (cp in 0xE000..0xF8FF) codepoints.add(cp) }
            }
        }
        val range = ShortArray(codepoints.size * 2 + 1)
        var i = 0
        for (cp in codepoints) {
            range[i++] = cp.toShort()
            range[i++] = cp.toShort()
        }
        range[i] = 0
        range
    }

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

    // NOTE: These Inter TTFs have had their stray Private Use Area cmap entries
    // (E000-F8FF -- OpenType stylistic-alternate glyphs like "G.1") stripped via
    // fontTools. Left in place, those entries collided with Lucide's merged icon
    // glyphs at the same codepoints below, corrupting ~25 icons (wrong bitmap AND
    // wrong advance width) even though Inter is only ever requested for
    // MAIN_RANGES, not the PUA block. See git history for the stripping script.
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
     * Loads all font levels into ImGui's font atlas and synchronously builds it.
     * Must be called after [ImGui.createContext] but before the GL3 backend
     * initialises (i.e. before [imguiGl3.init]), or after a [rebuildFonts]
     * cycle (atlas clear -> reload -> GL3 re-upload).
     *
     * Calling [ImFontAtlas.build] immediately within this method ensures glyph
     * ranges and font data arrays are rasterized into the atlas while JNI memory
     * pointers remain valid and before any concurrent GC (e.g. ZGC) relocates them.
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

        // Fixed semantic fonts (95% UI baseline)
        fontBody       = addFont(regularBytes!!, FONT_BODY,    cfg(), withIcons = true)
        fontCaption    = addFont(regularBytes!!, FONT_CAPTION, cfg(), withIcons = true)
        fontH3         = addFont(mediumBytes!!,  FONT_H3,      cfg(), withIcons = true)
        fontH2         = addFont(boldBytes!!,    FONT_H2,      cfg(), withIcons = true)
        fontH1         = addFont(boldBytes!!,    FONT_H1,      cfg(), withIcons = true)
        fontCode       = addFont(codeBytes!!,    FONT_CODE,    cfg(), withIcons = false)

        val presetFontSize = (FONT_BODY * (presetNameScalePercent / 100f)).coerceIn(10f, 22f)
        fontPresetName = addFont(regularBytes!!, presetFontSize, cfg(), withIcons = true)

        val built = atlas.build()
        if (!built) {
            logger.error { "Failed to build ImGui font atlas!" }
        }

        isLoaded = true
        logger.info {
            "UITheme fonts loaded -- H1=${FONT_H1}px  H2=${FONT_H2}px  H3=${FONT_H3}px  Body=${FONT_BODY}px  Caption=${FONT_CAPTION}px  Code=${FONT_CODE}px  PresetName=${presetFontSize}px ($presetNameScalePercent%)"
        }
    }

    /**
     * Clears the font atlas and reloads all fonts at their configured sizes.
     * Call this whenever the user commits a preset name font size change.
     */
    fun rebuildFonts(io: ImGuiIO) {
        isLoaded = false
        io.fonts.clear()
        loadFonts(io)
        logger.info { "UITheme fonts rebuilt (presetNameScalePercent=$presetNameScalePercent%)" }
    }

    /**
     * Resets the loaded state of UITheme fonts. Call when destroying ImGui contexts.
     */
    fun unloadFonts() {
        isLoaded = false
    }

    // -- Core rendering primitive ----------------------------------------------

    /** Resolve a [FontLevel] to its loaded [ImFont]. Falls back to the ImGui
     *  default font if [loadFonts] has not been called yet. */
    fun fontFor(level: FontLevel): ImFont? = if (!isLoaded) null else when (level) {
        FontLevel.H1          -> fontH1
        FontLevel.H2          -> fontH2
        FontLevel.H3          -> fontH3
        FontLevel.BODY        -> fontBody
        FontLevel.CAPTION     -> fontCaption
        FontLevel.CODE        -> fontCode
        FontLevel.PRESET_NAME -> fontPresetName
    }

    /**
     * Pushes [level]'s font, executes [block], then pops. Safe to call before
     * [loadFonts] -- falls back to the current ImGui default font gracefully.
     */
    inline fun <T> withFont(level: FontLevel, block: () -> T): T {
        val font = fontFor(level)
        val pushed = font != null && font.ptr != 0L
        if (pushed) ImGui.pushFont(font, 0f)
        try {
            return block()
        } finally {
            if (pushed) ImGui.popFont()
        }
    }

    // -- Semantic text helpers -------------------------------------------------

    fun h1(text: String)      = withFont(FontLevel.H1)      { ImGui.textUnformatted(text) }
    fun h2(text: String)      = withFont(FontLevel.H2)      { ImGui.textUnformatted(text) }
    fun h3(text: String)      = withFont(FontLevel.H3)      { ImGui.textUnformatted(text) }
    fun body(text: String)    = withFont(FontLevel.BODY)    { ImGui.textUnformatted(text) }
    fun caption(text: String) = withFont(FontLevel.CAPTION) {
        ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(ImGuiCol.TextDisabled))
        ImGui.textUnformatted(text)
        ImGui.popStyleColor()
    }
    fun code(text: String)    = withFont(FontLevel.CODE)    { ImGui.textUnformatted(text) }

    // -- Coloured variants -----------------------------------------------------

    fun h1Colored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.H1) {
            ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, a)
            ImGui.textUnformatted(text)
            ImGui.popStyleColor()
        }

    fun h2Colored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.H2) {
            ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, a)
            ImGui.textUnformatted(text)
            ImGui.popStyleColor()
        }

    fun h3Colored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.H3) {
            ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, a)
            ImGui.textUnformatted(text)
            ImGui.popStyleColor()
        }

    fun bodyColored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.BODY) {
            ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, a)
            ImGui.textUnformatted(text)
            ImGui.popStyleColor()
        }

    fun captionColored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.CAPTION) {
            ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, a)
            ImGui.textUnformatted(text)
            ImGui.popStyleColor()
        }

    fun codeColored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.CODE) {
            ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, a)
            ImGui.textUnformatted(text)
            ImGui.popStyleColor()
        }
}

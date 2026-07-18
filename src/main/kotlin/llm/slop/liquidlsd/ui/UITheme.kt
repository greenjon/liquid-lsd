package llm.slop.liquidlsd.ui

import imgui.ImFont
import imgui.ImFontConfig
import imgui.ImGui
import imgui.ImGuiIO
import llm.slop.liquidlsd.audio.AudioEngine
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
 *   UITheme.h1("Main Title")
 *   UITheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.text("small note") }
 *
 * For coloured variants use withFont directly; the named helpers are just
 * convenience wrappers for the most common single-text-call pattern.
 */
object UITheme {

    private val logger = KotlinLogging.logger {}

    private val settingsFile = File("lsd-settings.properties")
    private val legacySettingsFile = File("spirals-settings.properties")

    // -- Semantic Levels -------------------------------------------------------

    enum class FontLevel { H1, H2, H3, BODY, CAPTION, CODE }

    enum class AutoVjDirtyBehavior { SKIP, AUTO_DISCARD, AUTO_SAVE }

    // -- Mutable sizing knobs (user-tweakable from Settings later) -------------

    /** Base pixel size at which BODY text is rendered. All others are derived. */
    var baseSize: Float = 20f

    /** True if the JACK audio engine should process incoming audio and estimate tempo. */
    var audioEngineEnabled: Boolean = true

    /** True if the background video should be rendered and UI panels are semi-transparent. */
    var backgroundVideoEnabled: Boolean = false



    /** True if the main window should hide all UI overlay panels to show full video mix. */
    var cleanModeEnabled: Boolean = false

    /** True if parameter and modulator randomization is enabled. */
    var randomizationEnabled: Boolean = true


    enum class QueueKeyTrigger { NONE, ARROWS, PAGE_UP_DOWN, SPACE_BACKSPACE }

    var autoVjDirtyBehavior: AutoVjDirtyBehavior = AutoVjDirtyBehavior.AUTO_DISCARD
    var activeMidiProfile: String = "default"
    var queueKeyTrigger: QueueKeyTrigger = QueueKeyTrigger.NONE
    var tooltipsEnabled: Boolean = true
    var maxFps: Int = 30

    enum class StartupBehavior { PREVIOUS_SESSION, EMPTY }
    var startupBehavior: StartupBehavior = StartupBehavior.PREVIOUS_SESSION

    enum class AssetBrowserMode { FULL, HALF, HIDE }
    var assetBrowserMode: AssetBrowserMode = AssetBrowserMode.HALF

    init {
        loadSettings()
    }

    private fun loadSettings() {
        try {
            // One-time migration: carry over settings from the old filename
            if (!settingsFile.exists() && legacySettingsFile.exists()) {
                legacySettingsFile.copyTo(settingsFile)
                logger.info { "Migrated settings from ${legacySettingsFile.name} to ${settingsFile.name}" }
            }
            if (settingsFile.exists()) {
                val props = Properties()
                settingsFile.inputStream().use { props.load(it) }
                val savedSize = props.getProperty("baseSize")?.toFloatOrNull()
                if (savedSize != null) {
                    baseSize = savedSize
                    logger.info { "Loaded baseSize from settings file: $baseSize" }
                }
                val savedAudio = props.getProperty("audioEngineEnabled")?.toBooleanStrictOrNull()
                if (savedAudio != null) {
                    audioEngineEnabled = savedAudio
                    logger.info { "Loaded audioEngineEnabled from settings file: $audioEngineEnabled" }
                }
                val savedGain = props.getProperty("audioInputGain")?.toFloatOrNull()
                if (savedGain != null) {
                    AudioEngine.inputGain = savedGain
                    logger.info { "Loaded audioInputGain from settings file: $savedGain" }
                }
                val savedBpmLocked = props.getProperty("audioBpmLocked")?.toBooleanStrictOrNull()
                if (savedBpmLocked != null) {
                    AudioEngine.isBpmLocked = savedBpmLocked
                    logger.info { "Loaded audioBpmLocked from settings: $savedBpmLocked" }
                }
                val savedManualBpm = props.getProperty("audioManualBpm")?.toFloatOrNull()
                if (savedManualBpm != null) {
                    AudioEngine.manualBpm = savedManualBpm
                    logger.info { "Loaded audioManualBpm from settings: $savedManualBpm" }
                }


                val savedBgVideo = props.getProperty("backgroundVideoEnabled")?.toBooleanStrictOrNull()
                if (savedBgVideo != null) {
                    backgroundVideoEnabled = savedBgVideo
                    logger.info { "Loaded backgroundVideoEnabled from settings file: $backgroundVideoEnabled" }
                }

                val savedRandomization = props.getProperty("randomizationEnabled")?.toBooleanStrictOrNull()
                if (savedRandomization != null) {
                    randomizationEnabled = savedRandomization
                    logger.info { "Loaded randomizationEnabled from settings file: $randomizationEnabled" }
                }


                val savedTooltips = props.getProperty("tooltipsEnabled")?.toBooleanStrictOrNull()
                if (savedTooltips != null) {
                    tooltipsEnabled = savedTooltips
                    logger.info { "Loaded tooltipsEnabled from settings file: $tooltipsEnabled" }
                }
                val savedMaxFps = props.getProperty("maxFps")?.toIntOrNull()
                if (savedMaxFps != null) {
                    maxFps = if (savedMaxFps == 60) 60 else 30
                    logger.info { "Loaded maxFps from settings file: $maxFps" }
                }
                val savedMode = props.getProperty("assetBrowserMode")
                if (savedMode != null) {
                    assetBrowserMode = try { AssetBrowserMode.valueOf(savedMode) } catch (e: Exception) { AssetBrowserMode.HALF }
                    logger.info { "Loaded assetBrowserMode from settings file: $assetBrowserMode" }
                } else {
                    val savedHalfHeight = props.getProperty("assetManagerHalfHeight")?.toBooleanStrictOrNull()
                    if (savedHalfHeight != null) {
                        assetBrowserMode = if (savedHalfHeight) AssetBrowserMode.HALF else AssetBrowserMode.FULL
                        logger.info { "Migrated assetManagerHalfHeight to assetBrowserMode: $assetBrowserMode" }
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
            } else {
                logger.info { "No settings file found, using default baseSize: $baseSize, audioEngineEnabled: $audioEngineEnabled, backgroundVideoEnabled: $backgroundVideoEnabled, tooltipsEnabled: $tooltipsEnabled, maxFps: $maxFps" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load settings, using defaults" }
        }
    }

    fun saveSettings() {
        try {
            val props = Properties()
            props.setProperty("baseSize", baseSize.toString())
            props.setProperty("audioEngineEnabled", audioEngineEnabled.toString())
            props.setProperty("audioInputGain", AudioEngine.inputGain.toString())
            props.setProperty("audioBpmLocked", AudioEngine.isBpmLocked.toString())
            props.setProperty("audioManualBpm", AudioEngine.manualBpm.toString())


            props.setProperty("backgroundVideoEnabled", backgroundVideoEnabled.toString())
            props.setProperty("randomizationEnabled", randomizationEnabled.toString())

            props.setProperty("tooltipsEnabled", tooltipsEnabled.toString())
            props.setProperty("maxFps", maxFps.toString())
            props.setProperty("assetBrowserMode", assetBrowserMode.name)
            props.setProperty("autoVjDirtyBehavior", autoVjDirtyBehavior.name)
            props.setProperty("activeMidiProfile", activeMidiProfile)
            props.setProperty("queueKeyTrigger", queueKeyTrigger.name)
            props.setProperty("startupBehavior", startupBehavior.name)
            settingsFile.outputStream().use { props.store(it, "Liquid LSD Settings") }
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

    // Keep raw bytes of loaded fonts and range alive to prevent GC/JNI unpinning issues
    private var regularBytes: ByteArray? = null
    private var mediumBytes: ByteArray? = null
    private var boldBytes: ByteArray? = null
    private var codeBytes: ByteArray? = null
    private var lucideBytes: ByteArray? = null
    private var iconRange: ShortArray? = null

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

        // Keep the raw bytes alive for ImGui (it may reference them until atlas
        // is cleared). setFontDataOwnedByAtlas(false) so the JVM byte arrays
        // are not double-freed by native code.
        fun loadBytes(resource: String): ByteArray {
            val stream = UITheme::class.java.getResourceAsStream(resource)
                ?: error("Font resource not found on classpath: $resource")
            return stream.use { it.readBytes() }
        }

        fun cfg(owned: Boolean = false): ImFontConfig = ImFontConfig().apply {
            setFontDataOwnedByAtlas(owned)
        }

        regularBytes = loadBytes(INTER_REGULAR)
        mediumBytes  = loadBytes(INTER_MEDIUM)
        boldBytes    = loadBytes(INTER_BOLD)
        codeBytes    = loadBytes(JETBRAINS)
        lucideBytes  = loadBytes(LUCIDE)

        logger.info { "Font bytes loaded: Inter=${regularBytes!!.size}, Lucide=${lucideBytes!!.size}" }

        // Range for standard Lucide (E000 - F8FF range)
        // Range format is [start, end, 0]
        iconRange = shortArrayOf(0xe000.toShort(), 0xf8ff.toShort(), 0)

        fun addWithIcons(bytes: ByteArray, size: Float, config: ImFontConfig): ImFont {
            val f = atlas.addFontFromMemoryTTF(bytes, size, config)
            if (f.ptr == 0L) logger.error { "Failed to load main font at size $size" }
            config.destroy()

            val iconCfg = ImFontConfig().apply {
                setFontDataOwnedByAtlas(false)
                setMergeMode(true)
                setPixelSnapH(true)
            }
            
            val iconFont = atlas.addFontFromMemoryTTF(lucideBytes!!, size, iconCfg, iconRange!!)
            if (iconFont.ptr == 0L) logger.error { "Failed to merge Lucide icons at size $size" }
            
            iconCfg.destroy()
            return f
        }

        // Load each level; bodies/captions use Regular, headers use Bold/Medium.
        fontBody    = addWithIcons(regularBytes!!, baseSize * multBody,    cfg())
        fontCaption = addWithIcons(regularBytes!!, baseSize * multCaption, cfg())
        fontH3      = addWithIcons(mediumBytes!!,  baseSize * multH3,      cfg())
        fontH2      = addWithIcons(boldBytes!!,    baseSize * multH2,      cfg())
        fontH1      = addWithIcons(boldBytes!!,    baseSize * multH1,      cfg())
        fontCode    = addWithIcons(codeBytes!!,    baseSize * multCode,    cfg())

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

    // Convenience alias kept for callers that used the old withScale signature.
    @Deprecated("Use withFont(FontLevel, block) instead", ReplaceWith("withFont(level, block)"))
    inline fun <T> withScale(scaleMultiplier: Float, block: () -> T): T = block()

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

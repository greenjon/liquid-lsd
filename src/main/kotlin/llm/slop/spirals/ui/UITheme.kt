package llm.slop.spirals.ui

import imgui.ImFont
import imgui.ImFontConfig
import imgui.ImGui
import imgui.ImGuiIO
import imgui.flag.ImGuiCol
import llm.slop.spirals.config.ProjectConfig
import llm.slop.spirals.config.UiConfig
import llm.slop.spirals.audio.AudioEngine
import mu.KotlinLogging
import java.io.File
import java.util.Properties

/**
 * Central typography / styling system for the application.
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

    private val settingsFile = File(ProjectConfig.Paths.SETTINGS_FILE)

    // -- Semantic Levels -------------------------------------------------------

    enum class FontLevel { H1, H2, H3, BODY, CAPTION, CODE }

    enum class AutoVjDirtyBehavior { SKIP, AUTO_DISCARD, AUTO_SAVE }

    // -- Mutable sizing knobs (user-tweakable from Settings later) -------------

    /** Base pixel size at which BODY text is rendered. All others are derived. */
    var baseSize: Float = UiConfig.ThemeDefaults.BASE_SIZE

    object Colors {
        const val BACKGROUND_R = 0.035f
        const val BACKGROUND_G = 0.043f
        const val BACKGROUND_B = 0.055f

        const val PANEL_R = 0.055f
        const val PANEL_G = 0.070f
        const val PANEL_B = 0.090f

        const val PANEL_ALT_R = 0.075f
        const val PANEL_ALT_G = 0.095f
        const val PANEL_ALT_B = 0.125f

        const val FRAME_R = 0.105f
        const val FRAME_G = 0.135f
        const val FRAME_B = 0.180f

        const val FRAME_HOVER_R = 0.145f
        const val FRAME_HOVER_G = 0.190f
        const val FRAME_HOVER_B = 0.255f

        const val ACCENT_R = 0.185f
        const val ACCENT_G = 0.465f
        const val ACCENT_B = 0.820f

        const val ACCENT_HOVER_R = 0.255f
        const val ACCENT_HOVER_G = 0.565f
        const val ACCENT_HOVER_B = 0.950f

        const val TEXT_R = 0.900f
        const val TEXT_G = 0.930f
        const val TEXT_B = 0.960f

        const val MUTED_R = 0.560f
        const val MUTED_G = 0.610f
        const val MUTED_B = 0.680f

        const val BORDER_R = 0.165f
        const val BORDER_G = 0.205f
        const val BORDER_B = 0.270f

        const val DECK_A_R = 0.250f
        const val DECK_A_G = 0.520f
        const val DECK_A_B = 0.980f

        const val DECK_B_R = 0.960f
        const val DECK_B_G = 0.470f
        const val DECK_B_B = 0.240f

        const val DECK_C_R = 0.190f
        const val DECK_C_G = 0.760f
        const val DECK_C_B = 0.560f
    }

    /** True if the JACK audio engine should process incoming audio and estimate tempo. */
    var audioEngineEnabled: Boolean = UiConfig.ThemeDefaults.AUDIO_ENGINE_ENABLED

    /** True if the background video should be rendered and UI panels are semi-transparent. */
    var backgroundVideoEnabled: Boolean = UiConfig.ThemeDefaults.BACKGROUND_VIDEO_ENABLED



    /** True if the main window should hide all UI overlay panels to show full video mix. */
    var cleanModeEnabled: Boolean = false

    enum class QueueKeyTrigger { NONE, ARROWS, PAGE_UP_DOWN, SPACE_BACKSPACE }

    var autoVjDirtyBehavior: AutoVjDirtyBehavior = AutoVjDirtyBehavior.valueOf(UiConfig.ThemeDefaults.AUTO_VJ_DIRTY_BEHAVIOR)
    var activeMidiProfile: String = ProjectConfig.Files.DEFAULT_MIDI_PROFILE
    var queueKeyTrigger: QueueKeyTrigger = QueueKeyTrigger.valueOf(UiConfig.ThemeDefaults.QUEUE_KEY_TRIGGER)
    var tooltipsEnabled: Boolean = UiConfig.ThemeDefaults.TOOLTIPS_ENABLED
    var maxFps: Int = UiConfig.ThemeDefaults.MAX_FPS

    enum class StartupBehavior { PREVIOUS_SESSION, EMPTY }
    var startupBehavior: StartupBehavior = StartupBehavior.valueOf(UiConfig.ThemeDefaults.STARTUP_BEHAVIOR)

    enum class AssetBrowserMode { FULL, HALF, HIDE }
    var assetBrowserMode: AssetBrowserMode = AssetBrowserMode.valueOf(UiConfig.ThemeDefaults.ASSET_BROWSER_MODE)

    init {
        loadSettings()
    }

    private fun loadSettings() {
        try {
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
                    assetBrowserMode = try { AssetBrowserMode.valueOf(savedMode) } catch (e: Exception) { AssetBrowserMode.valueOf(UiConfig.ThemeDefaults.ASSET_BROWSER_MODE) }
                    logger.info { "Loaded assetBrowserMode from settings file: $assetBrowserMode" }
                } else {
                    val savedHalfHeight = props.getProperty("assetManagerHalfHeight")?.toBooleanStrictOrNull()
                    if (savedHalfHeight != null) {
                        assetBrowserMode = if (savedHalfHeight) AssetBrowserMode.valueOf(UiConfig.ThemeDefaults.ASSET_BROWSER_MODE) else AssetBrowserMode.FULL
                        logger.info { "Migrated assetManagerHalfHeight to assetBrowserMode: $assetBrowserMode" }
                    }
                }
                val savedAutoVj = props.getProperty("autoVjDirtyBehavior")
                if (savedAutoVj != null) {
                    autoVjDirtyBehavior = try { AutoVjDirtyBehavior.valueOf(savedAutoVj) } catch (e: Exception) { AutoVjDirtyBehavior.valueOf(UiConfig.ThemeDefaults.AUTO_VJ_DIRTY_BEHAVIOR) }
                    logger.info { "Loaded autoVjDirtyBehavior from settings file: $autoVjDirtyBehavior" }
                }
                val savedProfile = props.getProperty("activeMidiProfile")
                if (savedProfile != null) {
                    activeMidiProfile = savedProfile
                }
                val savedKeyTrigger = props.getProperty("queueKeyTrigger")
                if (savedKeyTrigger != null) {
                    queueKeyTrigger = try { QueueKeyTrigger.valueOf(savedKeyTrigger) } catch (e: Exception) { QueueKeyTrigger.valueOf(UiConfig.ThemeDefaults.QUEUE_KEY_TRIGGER) }
                }
                val savedStartup = props.getProperty("startupBehavior")
                if (savedStartup != null) {
                    startupBehavior = try { StartupBehavior.valueOf(savedStartup) } catch (e: Exception) { StartupBehavior.valueOf(UiConfig.ThemeDefaults.STARTUP_BEHAVIOR) }
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
            props.setProperty("tooltipsEnabled", tooltipsEnabled.toString())
            props.setProperty("maxFps", maxFps.toString())
            props.setProperty("assetBrowserMode", assetBrowserMode.name)
            props.setProperty("autoVjDirtyBehavior", autoVjDirtyBehavior.name)
            props.setProperty("activeMidiProfile", activeMidiProfile)
            props.setProperty("queueKeyTrigger", queueKeyTrigger.name)
            props.setProperty("startupBehavior", startupBehavior.name)
            settingsFile.outputStream().use { props.store(it, ProjectConfig.App.SETTINGS_FILE_COMMENT) }
            logger.info { "Saved settings to file" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save settings" }
        }
    }

    fun colorU32(r: Float, g: Float, b: Float, a: Float = 1f): Int {
        return ImGui.colorConvertFloat4ToU32(r, g, b, a)
    }

    fun deckAccentColor(label: String, alpha: Float = 1f): Int {
        return when (label) {
            "Deck A" -> colorU32(Colors.DECK_A_R, Colors.DECK_A_G, Colors.DECK_A_B, alpha)
            "Deck B" -> colorU32(Colors.DECK_B_R, Colors.DECK_B_G, Colors.DECK_B_B, alpha)
            "Deck C" -> colorU32(Colors.DECK_C_R, Colors.DECK_C_G, Colors.DECK_C_B, alpha)
            else -> colorU32(Colors.ACCENT_R, Colors.ACCENT_G, Colors.ACCENT_B, alpha)
        }
    }

    fun applyStyleColors(backgroundVideo: Boolean) {
        val alpha = if (backgroundVideo) 0.78f else 1.0f
        val style = ImGui.getStyle()

        style.setColor(ImGuiCol.Text, Colors.TEXT_R, Colors.TEXT_G, Colors.TEXT_B, 1f)
        style.setColor(ImGuiCol.TextDisabled, Colors.MUTED_R, Colors.MUTED_G, Colors.MUTED_B, 1f)
        style.setColor(ImGuiCol.WindowBg, Colors.BACKGROUND_R, Colors.BACKGROUND_G, Colors.BACKGROUND_B, alpha)
        style.setColor(ImGuiCol.ChildBg, Colors.PANEL_R, Colors.PANEL_G, Colors.PANEL_B, alpha)
        style.setColor(ImGuiCol.PopupBg, Colors.PANEL_ALT_R, Colors.PANEL_ALT_G, Colors.PANEL_ALT_B, 0.98f)
        style.setColor(ImGuiCol.Border, Colors.BORDER_R, Colors.BORDER_G, Colors.BORDER_B, 0.95f)
        style.setColor(ImGuiCol.BorderShadow, 0f, 0f, 0f, 0f)
        style.setColor(ImGuiCol.FrameBg, Colors.FRAME_R, Colors.FRAME_G, Colors.FRAME_B, 1f)
        style.setColor(ImGuiCol.FrameBgHovered, Colors.FRAME_HOVER_R, Colors.FRAME_HOVER_G, Colors.FRAME_HOVER_B, 1f)
        style.setColor(ImGuiCol.FrameBgActive, Colors.ACCENT_R, Colors.ACCENT_G, Colors.ACCENT_B, 0.70f)
        style.setColor(ImGuiCol.TitleBg, Colors.PANEL_R, Colors.PANEL_G, Colors.PANEL_B, alpha)
        style.setColor(ImGuiCol.TitleBgActive, Colors.PANEL_ALT_R, Colors.PANEL_ALT_G, Colors.PANEL_ALT_B, alpha)
        style.setColor(ImGuiCol.MenuBarBg, Colors.PANEL_ALT_R, Colors.PANEL_ALT_G, Colors.PANEL_ALT_B, alpha)
        style.setColor(ImGuiCol.ScrollbarBg, Colors.BACKGROUND_R, Colors.BACKGROUND_G, Colors.BACKGROUND_B, 0.8f)
        style.setColor(ImGuiCol.ScrollbarGrab, 0.22f, 0.27f, 0.34f, 1f)
        style.setColor(ImGuiCol.ScrollbarGrabHovered, 0.30f, 0.38f, 0.48f, 1f)
        style.setColor(ImGuiCol.ScrollbarGrabActive, Colors.ACCENT_R, Colors.ACCENT_G, Colors.ACCENT_B, 1f)
        style.setColor(ImGuiCol.CheckMark, Colors.ACCENT_HOVER_R, Colors.ACCENT_HOVER_G, Colors.ACCENT_HOVER_B, 1f)
        style.setColor(ImGuiCol.SliderGrab, Colors.ACCENT_R, Colors.ACCENT_G, Colors.ACCENT_B, 1f)
        style.setColor(ImGuiCol.SliderGrabActive, Colors.ACCENT_HOVER_R, Colors.ACCENT_HOVER_G, Colors.ACCENT_HOVER_B, 1f)
        style.setColor(ImGuiCol.Button, 0.125f, 0.175f, 0.245f, 1f)
        style.setColor(ImGuiCol.ButtonHovered, 0.175f, 0.255f, 0.360f, 1f)
        style.setColor(ImGuiCol.ButtonActive, Colors.ACCENT_R, Colors.ACCENT_G, Colors.ACCENT_B, 1f)
        style.setColor(ImGuiCol.Header, 0.140f, 0.205f, 0.300f, 1f)
        style.setColor(ImGuiCol.HeaderHovered, 0.180f, 0.275f, 0.405f, 1f)
        style.setColor(ImGuiCol.HeaderActive, Colors.ACCENT_R, Colors.ACCENT_G, Colors.ACCENT_B, 0.95f)
        style.setColor(ImGuiCol.Separator, Colors.BORDER_R, Colors.BORDER_G, Colors.BORDER_B, 0.85f)
        style.setColor(ImGuiCol.SeparatorHovered, Colors.ACCENT_R, Colors.ACCENT_G, Colors.ACCENT_B, 0.8f)
        style.setColor(ImGuiCol.SeparatorActive, Colors.ACCENT_HOVER_R, Colors.ACCENT_HOVER_G, Colors.ACCENT_HOVER_B, 1f)
        style.setColor(ImGuiCol.ResizeGrip, Colors.ACCENT_R, Colors.ACCENT_G, Colors.ACCENT_B, 0.25f)
        style.setColor(ImGuiCol.ResizeGripHovered, Colors.ACCENT_R, Colors.ACCENT_G, Colors.ACCENT_B, 0.55f)
        style.setColor(ImGuiCol.ResizeGripActive, Colors.ACCENT_HOVER_R, Colors.ACCENT_HOVER_G, Colors.ACCENT_HOVER_B, 0.85f)
        style.setColor(ImGuiCol.Tab, Colors.FRAME_R, Colors.FRAME_G, Colors.FRAME_B, 1f)
        style.setColor(ImGuiCol.TabHovered, Colors.FRAME_HOVER_R, Colors.FRAME_HOVER_G, Colors.FRAME_HOVER_B, 1f)
        style.setColor(ImGuiCol.TabActive, Colors.ACCENT_R, Colors.ACCENT_G, Colors.ACCENT_B, 1f)
        style.setColor(ImGuiCol.PlotHistogram, Colors.ACCENT_R, Colors.ACCENT_G, Colors.ACCENT_B, 1f)
        style.setColor(ImGuiCol.ModalWindowDimBg, 0f, 0f, 0f, 0.72f)
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

    fun iconButton(id: String, icon: String, tooltip: String, active: Boolean = false, size: Float = ImGui.getFrameHeight()): Boolean {
        return iconButton(id, icon, tooltip, active, size, size)
    }

    fun iconButton(id: String, icon: String, tooltip: String, active: Boolean = false, width: Float, height: Float): Boolean {
        val posX = ImGui.getCursorScreenPosX()
        val posY = ImGui.getCursorScreenPosY()
        val clicked = ImGui.invisibleButton(id, width, height)
        val hovered = ImGui.isItemHovered()
        val held = ImGui.isItemActive()
        val drawList = ImGui.getWindowDrawList()

        val bg = when {
            active -> colorU32(Colors.ACCENT_R, Colors.ACCENT_G, Colors.ACCENT_B, 0.90f)
            held -> colorU32(Colors.FRAME_HOVER_R, Colors.FRAME_HOVER_G, Colors.FRAME_HOVER_B, 0.95f)
            hovered -> colorU32(Colors.FRAME_HOVER_R, Colors.FRAME_HOVER_G, Colors.FRAME_HOVER_B, 0.72f)
            else -> colorU32(Colors.FRAME_R, Colors.FRAME_G, Colors.FRAME_B, 0.54f)
        }
        val border = if (active || hovered) {
            colorU32(Colors.ACCENT_HOVER_R, Colors.ACCENT_HOVER_G, Colors.ACCENT_HOVER_B, 0.82f)
        } else {
            colorU32(Colors.BORDER_R, Colors.BORDER_G, Colors.BORDER_B, 0.80f)
        }
        val text = if (active) {
            colorU32(1f, 1f, 1f, 1f)
        } else {
            colorU32(Colors.TEXT_R, Colors.TEXT_G, Colors.TEXT_B, 0.88f)
        }

        val rounding = 2f * (baseSize / 15f)
        drawList.addRectFilled(posX, posY, posX + width, posY + height, bg, rounding)
        drawList.addRect(posX, posY, posX + width, posY + height, border, rounding)

        val textSize = ImGui.calcTextSize(icon)
        val opticalBaselineOffset = height * 0.12f
        drawList.addText(
            posX + (width - textSize.x) * 0.5f,
            posY + (height - textSize.y) * 0.5f + opticalBaselineOffset,
            text,
            icon
        )

        if (hovered && tooltipsEnabled) {
            ImGui.setTooltip(tooltip)
        }
        return clicked
    }

    fun tabButton(id: String, label: String, active: Boolean, activeColor: Int, width: Float, height: Float = ImGui.getFrameHeight()): Boolean {
        val posX = ImGui.getCursorScreenPosX()
        val posY = ImGui.getCursorScreenPosY()
        val clicked = ImGui.invisibleButton(id, width, height)
        val hovered = ImGui.isItemHovered()
        val held = ImGui.isItemActive()
        val drawList = ImGui.getWindowDrawList()

        val bg = when {
            active -> activeColor
            held || hovered -> colorU32(Colors.FRAME_HOVER_R, Colors.FRAME_HOVER_G, Colors.FRAME_HOVER_B, 0.92f)
            else -> colorU32(Colors.FRAME_R, Colors.FRAME_G, Colors.FRAME_B, 0.72f)
        }
        val border = if (active || hovered) {
            activeColor
        } else {
            colorU32(Colors.BORDER_R, Colors.BORDER_G, Colors.BORDER_B, 0.78f)
        }
        val text = if (active) {
            colorU32(1f, 1f, 1f, 0.96f)
        } else {
            colorU32(Colors.TEXT_R, Colors.TEXT_G, Colors.TEXT_B, 0.82f)
        }

        val rounding = 2f * (baseSize / 15f)
        drawList.addRectFilled(posX, posY, posX + width, posY + height, bg, rounding)
        drawList.addRect(posX, posY, posX + width, posY + height, border, rounding)
        if (active) {
            drawList.addRectFilled(posX, posY + height - 2f, posX + width, posY + height, colorU32(1f, 1f, 1f, 0.28f), rounding)
        }

        val textSize = ImGui.calcTextSize(label)
        drawList.addText(
            posX + (width - textSize.x) * 0.5f,
            posY + (height - textSize.y) * 0.5f,
            text,
            label
        )
        return clicked
    }

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

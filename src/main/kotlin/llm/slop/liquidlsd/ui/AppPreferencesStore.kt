package llm.slop.liquidlsd.ui

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.audio.AudioTarget
import llm.slop.liquidlsd.audio.BeatDetectionSettings
import llm.slop.liquidlsd.rendering.VideoOutputEndpoint
import llm.slop.liquidlsd.rendering.VideoOutputConfig
import mu.KotlinLogging
import java.io.File
import java.util.Properties

/**
 * Persistence layer for [UITheme]'s [AppPreferences]: reads and writes the
 * `lsd-preferences.properties` file (with fallback to the legacy
 * `lsd-settings.properties` name) that backs Ableton Link damping, audio
 * backend/routing, video output configs, recording, MIDI profile, and the
 * rest of the app-wide preference toggles exposed as properties on
 * [UITheme].
 *
 * Extracted out of [UITheme] so that file owns only font/typography and
 * semantic text rendering concerns. Property-file keys and defaults are
 * unchanged from the prior in-UITheme implementation -- this is a pure move.
 */
object AppPreferencesStore {

    private val logger = KotlinLogging.logger {}

    private val preferencesFile = File("lsd-preferences.properties")
    private val legacySettingsFile = File("lsd-settings.properties")
    private val settingsFile get() = preferencesFile

    fun loadSettings() = loadPreferences()

    private fun Properties.getBoolean(key: String): Boolean? {
        val raw = getProperty(key)?.trim() ?: return null
        return raw.toBooleanStrictOrNull() ?: raw.toBoolean()
    }

    fun loadPreferences() {
        try {
            val fileToRead = when {
                preferencesFile.exists() -> preferencesFile
                legacySettingsFile.exists() -> legacySettingsFile
                else -> null
            }
            if (fileToRead != null) {
                val props = Properties()
                fileToRead.inputStream().use { props.load(it) }
                val savedPresetScale = props.getProperty("presetNameScalePercent")?.toIntOrNull()
                if (savedPresetScale != null) {
                    UITheme.presetNameScalePercent = (kotlin.math.round(savedPresetScale / 10f) * 10).toInt().coerceIn(80, 120)
                    logger.info { "Loaded presetNameScalePercent from settings file: ${UITheme.presetNameScalePercent}%" }
                }
                val savedAudio = props.getBoolean("audioEngineEnabled")
                if (savedAudio != null) {
                    UITheme.audioEngineEnabled = savedAudio
                    logger.info { "Loaded audioEngineEnabled from settings file: ${UITheme.audioEngineEnabled}" }
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
                val savedRouting = props.getProperty("audioChannelRouting")
                if (savedRouting != null) {
                    try {
                        UITheme.audioChannelRouting = llm.slop.liquidlsd.audio.AudioChannelRouting.fromString(savedRouting)
                        logger.info { "Loaded audioChannelRouting from settings: ${UITheme.audioChannelRouting}" }
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to parse audioChannelRouting '$savedRouting'" }
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

                props.getProperty("clockSource")?.let { savedSource ->
                    try {
                        AudioEngine.clockSource = llm.slop.liquidlsd.audio.ClockSource.fromString(savedSource)
                    } catch (_: Exception) {}
                }
                props.getBoolean("linkEnabled")?.let {
                    llm.slop.liquidlsd.link.AbletonLinkEngine.setEnabled(it)
                }
                props.getProperty("linkQuantum")?.toDoubleOrNull()?.let {
                    llm.slop.liquidlsd.link.AbletonLinkEngine.quantum = it.coerceIn(1.0, 32.0)
                }
                props.getBoolean("linkStartStopSync")?.let {
                    llm.slop.liquidlsd.link.AbletonLinkEngine.setStartStopSyncEnabled(it)
                }
                props.getProperty("linkDampingHysteresisBpm")?.toDoubleOrNull()?.let {
                    llm.slop.liquidlsd.link.LinkSyncManager.signalDamping.hysteresisThresholdBpm = it.coerceIn(0.1, 5.0)
                }
                props.getProperty("linkDampingSustainedBeats")?.toIntOrNull()?.let {
                    llm.slop.liquidlsd.link.LinkSyncManager.signalDamping.sustainedBeatsThreshold = it.coerceIn(1, 16)
                }
                props.getProperty("linkDampingPhaseThresholdBeats")?.toDoubleOrNull()?.let {
                    llm.slop.liquidlsd.link.LinkSyncManager.signalDamping.phaseErrorThresholdBeats = it.coerceIn(0.1, 2.0)
                }
                props.getProperty("carabinerHost")?.let {
                    llm.slop.liquidlsd.link.AbletonLinkEngine.carabinerHost = it
                }
                props.getProperty("carabinerPort")?.toIntOrNull()?.let {
                    llm.slop.liquidlsd.link.AbletonLinkEngine.carabinerPort = it.coerceIn(1024, 65535)
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
                    UITheme.backgroundVideoEnabled = savedBgVideo
                    logger.info { "Loaded backgroundVideoEnabled from settings file: ${UITheme.backgroundVideoEnabled}" }
                }

                val savedCleanMode = props.getBoolean("cleanModeEnabled")
                if (savedCleanMode != null) {
                    UITheme.cleanModeEnabled = savedCleanMode
                    logger.info { "Loaded cleanModeEnabled from settings file: ${UITheme.cleanModeEnabled}" }
                }

                val savedRandomization = props.getBoolean("randomizationEnabled")
                if (savedRandomization != null) {
                    UITheme.randomizationEnabled = savedRandomization
                    logger.info { "Loaded randomizationEnabled from settings file: ${UITheme.randomizationEnabled}" }
                }

                val savedSequencer = props.getBoolean("sequencerEnabled")
                if (savedSequencer != null) {
                    UITheme.sequencerEnabled = savedSequencer
                    logger.info { "Loaded sequencerEnabled from settings file: ${UITheme.sequencerEnabled}" }
                }

                val savedMidi = props.getBoolean("midiEnabled")
                if (savedMidi != null) {
                    UITheme.midiEnabled = savedMidi
                    logger.info { "Loaded midiEnabled from settings file: ${UITheme.midiEnabled}" }
                }


                val savedTooltips = props.getBoolean("tooltipsEnabled")
                if (savedTooltips != null) {
                    UITheme.tooltipsEnabled = savedTooltips
                    logger.info { "Loaded tooltipsEnabled from settings file: $savedTooltips" }
                }
                val savedMaxFps = props.getProperty("maxFps")?.toIntOrNull()
                if (savedMaxFps != null) {
                    UITheme.maxFps = if (savedMaxFps == 60) 60 else 30
                    logger.info { "Loaded maxFps from settings file: ${UITheme.maxFps}" }
                }
                val savedMode = props.getProperty("libraryMode") ?: props.getProperty("assetBrowserMode")
                if (savedMode != null) {
                    UITheme.libraryMode = try { UITheme.LibraryMode.valueOf(savedMode) } catch (e: Exception) { UITheme.LibraryMode.HALF }
                    logger.info { "Loaded libraryMode from settings file: ${UITheme.libraryMode}" }
                } else {
                    val savedHalfHeight = props.getBoolean("assetManagerHalfHeight")
                    if (savedHalfHeight != null) {
                        UITheme.libraryMode = if (savedHalfHeight) UITheme.LibraryMode.HALF else UITheme.LibraryMode.FULL
                        logger.info { "Migrated assetManagerHalfHeight to libraryMode: ${UITheme.libraryMode}" }
                    }
                }
                val savedColumn3Mode = props.getProperty("column3Mode")
                if (savedColumn3Mode != null) {
                    UITheme.column3Mode = try { UITheme.Column3Mode.valueOf(savedColumn3Mode) } catch (e: Exception) { UITheme.Column3Mode.MIXER }
                    logger.info { "Loaded column3Mode from settings file: ${UITheme.column3Mode}" }
                }
                // "workspaceMode" (Classic vs Performance) is no longer read: Classic view was removed.
                props.getProperty("performanceMatrixTab")?.toIntOrNull()?.let {
                    UITheme.performanceMatrixTab = it
                    logger.info { "Loaded performanceMatrixTab from settings file: ${UITheme.performanceMatrixTab}" }
                }
                val savedAutoVj = props.getProperty("autoVjDirtyBehavior")
                if (savedAutoVj != null) {
                    UITheme.autoVjDirtyBehavior = try { UITheme.AutoVjDirtyBehavior.valueOf(savedAutoVj) } catch (e: Exception) { UITheme.AutoVjDirtyBehavior.AUTO_DISCARD }
                    logger.info { "Loaded autoVjDirtyBehavior from settings file: ${UITheme.autoVjDirtyBehavior}" }
                }
                val savedProfile = props.getProperty("activeMidiProfile")
                if (savedProfile != null) {
                    UITheme.activeMidiProfile = savedProfile
                }
                val savedKeyTrigger = props.getProperty("queueKeyTrigger")
                if (savedKeyTrigger != null) {
                    UITheme.queueKeyTrigger = try { UITheme.QueueKeyTrigger.valueOf(savedKeyTrigger) } catch (e: Exception) { UITheme.QueueKeyTrigger.NONE }
                }
                val savedStartup = props.getProperty("startupBehavior")
                if (savedStartup != null) {
                    UITheme.startupBehavior = try { UITheme.StartupBehavior.valueOf(savedStartup) } catch (e: Exception) { UITheme.StartupBehavior.PREVIOUS_SESSION }
                    logger.info { "Loaded startupBehavior from settings file: ${UITheme.startupBehavior}" }
                }
                val savedTheme = props.getProperty("theme")
                if (savedTheme != null) {
                    UITheme.theme = try { UITheme.Theme.valueOf(savedTheme) } catch (e: Exception) { UITheme.Theme.BORING }
                    logger.info { "Loaded theme from settings file: ${UITheme.theme}" }
                }
                props.getBoolean("showMidiCol")?.let { if (savedMidi == null) UITheme.midiEnabled = it }
                props.getBoolean("showLfoCol")?.let { UITheme.showLfoCol = it }
                props.getBoolean("showSeqCol")?.let { if (savedSequencer == null) UITheme.sequencerEnabled = it }
                props.getBoolean("showAudioCol")?.let { if (savedAudio == null) UITheme.audioEngineEnabled = it }
                props.getProperty("col1Ratio")?.toFloatOrNull()?.let { UITheme.col1Ratio = it.coerceIn(0.10f, 0.70f) }
                props.getProperty("col2Ratio")?.toFloatOrNull()?.let { UITheme.col2Ratio = it.coerceIn(0.10f, 0.70f) }
                (props.getProperty("libraryRatio") ?: props.getProperty("assetBrowserRatio"))?.toFloatOrNull()?.let { UITheme.libraryRatio = it.coerceIn(0.10f, 0.90f) }
                (props.getProperty("lastCustomLibraryRatio") ?: props.getProperty("lastCustomAssetBrowserRatio"))?.toFloatOrNull()?.let { UITheme.lastCustomLibraryRatio = it.coerceIn(0.10f, 0.90f) }
                props.getProperty("renderResolutionPreset")?.let { saved ->
                    UITheme.renderResolutionPreset = try { UITheme.ResolutionPreset.valueOf(saved) } catch (e: Exception) { UITheme.ResolutionPreset.RES_1080P }
                }
                props.getProperty("customRenderWidth")?.toIntOrNull()?.let { UITheme.customRenderWidth = it.coerceIn(128, 7680) }
                props.getProperty("customRenderHeight")?.toIntOrNull()?.let { UITheme.customRenderHeight = it.coerceIn(128, 4320) }
                props.getProperty("outputScaleMode")?.let { saved ->
                    UITheme.outputScaleMode = try { UITheme.OutputScaleMode.valueOf(saved) } catch (e: Exception) { UITheme.OutputScaleMode.FIT }
                }
                props.getProperty("recordingDirectory")?.let { UITheme.recordingDirectory = it }
                props.getBoolean("recordingIncludeAudio")?.let { UITheme.recordingIncludeAudio = it }
                props.getProperty("recordingBitrateMbps")?.toIntOrNull()?.let { UITheme.recordingBitrateMbps = it }
                props.getProperty("recordingFps")?.toIntOrNull()?.let { UITheme.recordingFps = it }
                val savedWidth = props.getProperty("preferencesWidth") ?: props.getProperty("settingsWidth")
                savedWidth?.toFloatOrNull()?.let { UITheme.preferencesWidth = it.coerceIn(400f, 3840f) }
                val savedHeight = props.getProperty("preferencesHeight") ?: props.getProperty("settingsHeight")
                savedHeight?.toFloatOrNull()?.let { UITheme.preferencesHeight = it.coerceIn(300f, 2160f) }
                props.getBoolean("framelessWindow")?.let { UITheme.framelessWindow = it }
                props.getBoolean("trackpadConsoleEnabled")?.let { UITheme.trackpadConsoleEnabled = it }
                props.getProperty("checkUpdatesOnStartup")?.let { UITheme.checkUpdatesOnStartup = it.toBoolean() }
                props.getProperty("ignoredUpdateVersion")?.let { UITheme.ignoredUpdateVersion = it }

                props.getBoolean("rackSoloMode")?.let { UITheme.rackSoloMode = it }
                props.getProperty("rackExpandedModules")?.let { encoded ->
                    UITheme.rackExpandedModules = encoded.split(';')
                        .filter { it.isNotBlank() }
                        .mapNotNull { entry ->
                            val idx = entry.indexOf('=')
                            if (idx <= 0) null else entry.substring(0, idx) to entry.substring(idx + 1)
                        }.toMap()
                }

                props.getProperty("videoOutputConfigs")?.let { json ->
                    try {
                        UITheme.videoOutputConfigs = Json.decodeFromString<Map<VideoOutputEndpoint, VideoOutputConfig>>(json)
                        logger.info { "Loaded videoOutputConfigs from preferences file" }
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to parse videoOutputConfigs JSON" }
                    }
                }
            } else {
                logger.info { "No preferences file found, using defaults: fixed UI 95%, presetNameScalePercent: ${UITheme.presetNameScalePercent}%, audioEngineEnabled: ${UITheme.audioEngineEnabled}, backgroundVideoEnabled: ${UITheme.backgroundVideoEnabled}, tooltipsEnabled: ${UITheme.tooltipsEnabled}, maxFps: ${UITheme.maxFps}, framelessWindow: ${UITheme.framelessWindow}" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load preferences, using defaults" }
        }
    }

    fun saveSettings() = savePreferences()

    fun savePreferences() {
        try {
            val props = Properties()
            val fileToRead = if (preferencesFile.exists()) preferencesFile else if (legacySettingsFile.exists()) legacySettingsFile else null
            fileToRead?.inputStream()?.use { props.load(it) }
            props.remove("workspaceMode") // Classic view removed; drop the stale key

            props.setProperty("presetNameScalePercent", UITheme.presetNameScalePercent.toString())
            props.setProperty("audioEngineEnabled", UITheme.audioEngineEnabled.toString())
            props.setProperty("audioBackend", AudioEngine.backendMode.name)
            props.setProperty("audioChannelRouting", AudioEngine.channelRouting.name)
            props.setProperty("audioDeviceName", AudioEngine.selectedDeviceName ?: "")
            props.setProperty("audioInputGain", AudioEngine.inputGain.toString())
            props.setProperty("audioBpmLocked", AudioEngine.isBpmLocked.toString())
            props.setProperty("audioManualBpm", AudioEngine.manualBpm.toString())
            props.setProperty("clockSource", AudioEngine.clockSource.name)
            props.setProperty("linkEnabled", llm.slop.liquidlsd.link.AbletonLinkEngine.isEnabled.toString())
            props.setProperty("linkQuantum", llm.slop.liquidlsd.link.AbletonLinkEngine.quantum.toString())
            props.setProperty("linkStartStopSync", llm.slop.liquidlsd.link.AbletonLinkEngine.isStartStopSyncEnabled().toString())
            props.setProperty("linkDampingHysteresisBpm", llm.slop.liquidlsd.link.LinkSyncManager.signalDamping.hysteresisThresholdBpm.toString())
            props.setProperty("linkDampingSustainedBeats", llm.slop.liquidlsd.link.LinkSyncManager.signalDamping.sustainedBeatsThreshold.toString())
            props.setProperty("linkDampingPhaseThresholdBeats", llm.slop.liquidlsd.link.LinkSyncManager.signalDamping.phaseErrorThresholdBeats.toString())
            props.setProperty("carabinerHost", llm.slop.liquidlsd.link.AbletonLinkEngine.carabinerHost)
            props.setProperty("carabinerPort", llm.slop.liquidlsd.link.AbletonLinkEngine.carabinerPort.toString())
            props.setProperty("audioBeatTarget", AudioEngine.beatDetector.settings.target.name)
            props.setProperty("audioBpmFloor", AudioEngine.beatDetector.settings.bpmSearchFloor.toString())
            props.setProperty("audioBpmCeiling", AudioEngine.beatDetector.settings.bpmSearchCeiling.toString())
            props.setProperty("audioTransitionAlpha", AudioEngine.beatDetector.settings.transitionWeightAlpha.toString())
            props.setProperty("audioTrackingInertia", AudioEngine.beatDetector.settings.trackingInertiaBpmPerBeat.toString())
            props.setProperty("backgroundVideoEnabled", UITheme.backgroundVideoEnabled.toString())
            props.setProperty("cleanModeEnabled", UITheme.cleanModeEnabled.toString())
            props.setProperty("randomizationEnabled", UITheme.randomizationEnabled.toString())
            props.setProperty("sequencerEnabled", UITheme.sequencerEnabled.toString())
            props.setProperty("midiEnabled", UITheme.midiEnabled.toString())
            props.setProperty("tooltipsEnabled", UITheme.tooltipsEnabled.toString())
            props.setProperty("maxFps", UITheme.maxFps.toString())
            props.setProperty("libraryMode", UITheme.libraryMode.name)
            props.setProperty("column3Mode", UITheme.column3Mode.name)
            props.setProperty("performanceMatrixTab", UITheme.performanceMatrixTab.toString())
            props.setProperty("autoVjDirtyBehavior", UITheme.autoVjDirtyBehavior.name)
            props.setProperty("activeMidiProfile", UITheme.activeMidiProfile)
            props.setProperty("queueKeyTrigger", UITheme.queueKeyTrigger.name)
            props.setProperty("startupBehavior", UITheme.startupBehavior.name)
            props.setProperty("theme", UITheme.theme.name)
            props.setProperty("showMidiCol", UITheme.showMidiCol.toString())
            props.setProperty("showLfoCol", UITheme.showLfoCol.toString())
            props.setProperty("showSeqCol", UITheme.showSeqCol.toString())
            props.setProperty("showAudioCol", UITheme.showAudioCol.toString())
            props.setProperty("col1Ratio", UITheme.col1Ratio.toString())
            props.setProperty("col2Ratio", UITheme.col2Ratio.toString())
            props.setProperty("libraryRatio", UITheme.libraryRatio.toString())
            props.setProperty("lastCustomLibraryRatio", UITheme.lastCustomLibraryRatio.toString())
            props.setProperty("renderResolutionPreset", UITheme.renderResolutionPreset.name)
            props.setProperty("customRenderWidth", UITheme.customRenderWidth.toString())
            props.setProperty("customRenderHeight", UITheme.customRenderHeight.toString())
            props.setProperty("outputScaleMode", UITheme.outputScaleMode.name)
            props.setProperty("recordingDirectory", UITheme.recordingDirectory)
            props.setProperty("recordingIncludeAudio", UITheme.recordingIncludeAudio.toString())
            props.setProperty("recordingBitrateMbps", UITheme.recordingBitrateMbps.toString())
            props.setProperty("recordingFps", UITheme.recordingFps.toString())
            props.setProperty("preferencesWidth", UITheme.preferencesWidth.toString())
            props.setProperty("preferencesHeight", UITheme.preferencesHeight.toString())
            props.setProperty("settingsWidth", UITheme.preferencesWidth.toString())
            props.setProperty("settingsHeight", UITheme.preferencesHeight.toString())
            props.setProperty("framelessWindow", UITheme.framelessWindow.toString())
            props.setProperty("trackpadConsoleEnabled", UITheme.trackpadConsoleEnabled.toString())
            props.setProperty("checkUpdatesOnStartup", UITheme.checkUpdatesOnStartup.toString())
            props.setProperty("ignoredUpdateVersion", UITheme.ignoredUpdateVersion)

            props.setProperty("rackSoloMode", UITheme.rackSoloMode.toString())
            props.setProperty("rackExpandedModules", UITheme.rackExpandedModules.entries.joinToString(";") { "${it.key}=${it.value}" })

            try {
                props.setProperty("videoOutputConfigs", Json.encodeToString(UITheme.videoOutputConfigs))
            } catch (e: Exception) {
                logger.error(e) { "Failed to serialize videoOutputConfigs to JSON" }
            }

            val tmpFile = File("${preferencesFile.absolutePath}.tmp")
            tmpFile.outputStream().use { props.store(it, "Liquid LSD Preferences") }
            java.nio.file.Files.move(
                tmpFile.toPath(),
                preferencesFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
            logger.info { "Saved preferences to file" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save preferences" }
        }
    }
}

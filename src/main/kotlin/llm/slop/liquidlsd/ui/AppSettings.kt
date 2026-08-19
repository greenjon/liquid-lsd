package llm.slop.liquidlsd.ui

data class AppSettings(
    val baseSize: Float = 20f,
    val audioEngineEnabled: Boolean = true,
    val backgroundVideoEnabled: Boolean = false,
    val cleanModeEnabled: Boolean = false,
    val randomizationEnabled: Boolean = true,
    val autoVjDirtyBehavior: UITheme.AutoVjDirtyBehavior = UITheme.AutoVjDirtyBehavior.AUTO_DISCARD,
    val activeMidiProfile: String = "default",
    val queueKeyTrigger: UITheme.QueueKeyTrigger = UITheme.QueueKeyTrigger.NONE,
    val tooltipsEnabled: Boolean = true,
    val maxFps: Int = 30,
    val startupBehavior: UITheme.StartupBehavior = UITheme.StartupBehavior.PREVIOUS_SESSION,
    val libraryMode: UITheme.LibraryMode = UITheme.LibraryMode.HALF,
    val theme: UITheme.Theme = UITheme.Theme.BORING,
    val showMidiCol: Boolean = true,
    val showLfoCol: Boolean = true,
    val showAudioCol: Boolean = true,
    val showTriggerCol: Boolean = true,
    val col1Ratio: Float = 0.30f,
    val col2Ratio: Float = 0.40f,
    val libraryRatio: Float = 0.50f,
    val lastCustomLibraryRatio: Float = 0.50f,
    val gridCellRatio: Float = 1.0f
)

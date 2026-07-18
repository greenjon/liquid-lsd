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
    val assetBrowserMode: UITheme.AssetBrowserMode = UITheme.AssetBrowserMode.HALF
)

package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImBoolean

/**
 * Dedicated UI component for the Web Broadcast relay preferences:
 * - Relay server URL / secret token configuration.
 * - Target dispatch rate limit and auto-connect toggle.
 * - Live connection status HUD and connect/disconnect/force-sync controls.
 *
 * Rendered within the "Web Broadcast" category of [PreferencesPanel].
 */
object BroadcastPreferencesPanel {

    private val serverUrlBuf = imgui.type.ImString(llm.slop.liquidlsd.broadcast.BroadcastPreferences.serverUrl, 256)
    private val tokenBuf = imgui.type.ImString(llm.slop.liquidlsd.broadcast.BroadcastPreferences.token, 128)
    private var showToken = false

    fun drawContent(session: llm.slop.liquidlsd.SessionContext, mixer: llm.slop.liquidlsd.rendering.Mixer?) {
        val theme = session.uiTheme
        theme.h2("Web Broadcast Relay")
        ImGui.separator()
        ImGui.spacing()

        ImGui.textWrapped("Broadcasts real-time preset state, mixer balance, and parameter modulations to the Liquid LSD Web TV client.")
        ImGui.spacing()

        // Server URL
        theme.caption("RELAY SERVER URL")
        if (serverUrlBuf.get() != llm.slop.liquidlsd.broadcast.BroadcastPreferences.serverUrl) {
            serverUrlBuf.set(llm.slop.liquidlsd.broadcast.BroadcastPreferences.serverUrl)
        }
        if (ImGui.inputText("##broadcast_url", serverUrlBuf)) {
            llm.slop.liquidlsd.broadcast.BroadcastPreferences.serverUrl = serverUrlBuf.get().trim()
            llm.slop.liquidlsd.broadcast.BroadcastPreferences.savePreferences()
        }
        itemTooltip("WebSocket relay URL (e.g. ws://127.0.0.1:9004 or wss://relay.example.com)")

        ImGui.spacing()

        // Secret Token
        theme.caption("BROADCASTER SECRET TOKEN")
        if (tokenBuf.get() != llm.slop.liquidlsd.broadcast.BroadcastPreferences.token) {
            tokenBuf.set(llm.slop.liquidlsd.broadcast.BroadcastPreferences.token)
        }
        val tokenFlags = if (showToken) 0 else imgui.flag.ImGuiInputTextFlags.Password
        if (ImGui.inputText("##broadcast_token", tokenBuf, tokenFlags)) {
            llm.slop.liquidlsd.broadcast.BroadcastPreferences.token = tokenBuf.get().trim()
            llm.slop.liquidlsd.broadcast.BroadcastPreferences.savePreferences()
        }
        ImGui.sameLine()
        val eyeLabel = if (showToken) "Hide" else "Show"
        if (ImGui.button(eyeLabel)) {
            showToken = !showToken
        }

        ImGui.spacing()

        // Target Update Rate
        val sliderBoxW = 50f
        CustomRangeSlider.drawCompactSlider(
            session = session,
            label = "Rate Limit",
            currentValue = llm.slop.liquidlsd.broadcast.BroadcastPreferences.targetFps.toFloat(),
            minLimit = 5f,
            maxLimit = 60f,
            defaultValue = 30f,
            formatValue = { "${it.toInt()} Hz" },
            idPrefix = "preferences_broadcast_target_fps",
            themeColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.9f, 0.9f),
            showCurrentLabel = false,
            customBoxWidth = sliderBoxW,
            onValueChanged = { newVal ->
                llm.slop.liquidlsd.broadcast.BroadcastPreferences.targetFps = newVal.toInt()
                llm.slop.liquidlsd.broadcast.BroadcastPreferences.savePreferences()
            }
        )
        itemTooltip("Maximum rate to dispatch parameter delta packets to the relay.")

        ImGui.spacing()

        // Auto-connect checkbox
        val autoConn = ImBoolean(llm.slop.liquidlsd.broadcast.BroadcastPreferences.autoConnect)
        if (ImGui.checkbox("Auto-connect on launch", autoConn)) {
            llm.slop.liquidlsd.broadcast.BroadcastPreferences.autoConnect = autoConn.get()
            llm.slop.liquidlsd.broadcast.BroadcastPreferences.savePreferences()
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // Connection Status HUD
        theme.h3("Connection Status")
        val state = llm.slop.liquidlsd.broadcast.BroadcastEngine.connectionState
        val isLive = llm.slop.liquidlsd.broadcast.BroadcastEngine.isLive

        when (state) {
            llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.CONNECTED -> {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.2f, 0.9f, 0.2f, 1f)
                ImGui.text("${Icons.ACTIVITY} CONNECTED (LIVE)")
                ImGui.popStyleColor()
            }
            llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.CONNECTING -> {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.9f, 0.8f, 0.2f, 1f)
                ImGui.text("${Icons.REFRESH} CONNECTING...")
                ImGui.popStyleColor()
            }
            llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.ERROR -> {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.95f, 0.3f, 0.3f, 1f)
                ImGui.text("${Icons.ALERT} ERROR: ${llm.slop.liquidlsd.broadcast.BroadcastEngine.lastError ?: "Connection failed"}")
                ImGui.popStyleColor()
            }
            llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.DISCONNECTED -> {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1f)
                ImGui.text("${Icons.POWER} OFFLINE (DISCONNECTED)")
                ImGui.popStyleColor()
            }
        }

        ImGui.spacing()

        // Control Buttons
        if (isLive) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.8f, 0.2f, 0.2f, 1f)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.9f, 0.3f, 0.3f, 1f)
            if (ImGui.button("${Icons.POWER} Disconnect Broadcast", 200f, 32f)) {
                llm.slop.liquidlsd.broadcast.BroadcastEngine.stopBroadcast()
            }
            ImGui.popStyleColor(2)
        } else {
            val canConnect = llm.slop.liquidlsd.broadcast.BroadcastPreferences.isConfigured
            if (!canConnect) {
                ImGui.beginDisabled()
            }
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.15f, 0.6f, 0.25f, 1f)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.25f, 0.75f, 0.35f, 1f)
            if (ImGui.button("${Icons.ZAP} Go Live (Connect)", 200f, 32f)) {
                if (mixer != null) {
                    llm.slop.liquidlsd.broadcast.BroadcastEngine.startBroadcast(mixer)
                }
            }
            ImGui.popStyleColor(2)
            if (!canConnect) {
                ImGui.endDisabled()
                itemTooltip("Relay Server URL and Broadcaster Secret Token must both be configured in Preferences.")
            }
        }

        if (state == llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.CONNECTED) {
            ImGui.sameLine()
            if (ImGui.button("${Icons.REFRESH} Force Sync State", 160f, 32f)) {
                llm.slop.liquidlsd.broadcast.BroadcastEngine.forceSync()
            }
            itemTooltip("Re-send full state snapshot to relay immediately.")
        }
    }
}

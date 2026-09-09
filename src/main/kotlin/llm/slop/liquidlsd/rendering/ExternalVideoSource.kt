package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * A visual source that pulls live video from external tools (Spout on Windows, Syphon on macOS).
 */
class ExternalVideoSource(
    override val id: String = "spout_input",
    var serverName: String = ""
) : VisualSource {
    
    override val displayName = "External Video"
    
    override val categories: List<String>
        get() = listOf("Input", "Video")

    override val globalAlpha = ModulatableParameter(baseValue = 1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    
    // Additional parameters if needed (e.g. contrast, brightness)
    override val parameters: Map<String, ModulatableParameter> = mapOf()

    override val is3D = false

    override fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        list.add("$prefix/$displayName/Gain" to globalAlpha)
        return list
    }

    var currentTextureId: Int = 0
        private set
        
    var textureWidth: Int = 1920
        private set
        
    var textureHeight: Int = 1080
        private set

    private var receiver: TextureReceiver? = null
    private var isConnected = false
    private var lastServerName = ""

    override fun update() {
        super.update()
        
        if (serverName != lastServerName) {
            disconnect()
            lastServerName = serverName
        }
        
        if (serverName.isNotBlank() && !isConnected) {
            connect(serverName)
        }
        
        if (isConnected) {
            val tex = receiver?.update() ?: 0
            if (tex != 0) {
                currentTextureId = tex
                textureWidth = receiver?.currentWidth ?: 1920
                textureHeight = receiver?.currentHeight ?: 1080
            }
        } else {
            currentTextureId = 0
        }
    }
    
    private fun connect(name: String) {
        val osName = System.getProperty("os.name").lowercase()
        receiver = when {
            osName.contains("win") -> SpoutReceiverImpl()
            osName.contains("mac") -> SyphonReceiverImpl()
            else -> NullTextureReceiver()
        }
        
        if (receiver?.start(name) == true) {
            isConnected = true
        } else {
            isConnected = false
        }
    }
    
    private fun disconnect() {
        receiver?.stop()
        receiver = null
        isConnected = false
        currentTextureId = 0
    }

    override fun clone(): VisualSource {
        return ExternalVideoSource(id, serverName)
    }

    override fun clear() {
        // No-op
    }

    override fun dispose() {
        disconnect()
    }
}

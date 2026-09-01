package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.MeterType
import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlinx.serialization.Serializable

@Serializable
data class ParamMeta(
    val name: String,
    val default: Float,
    val min: Float,
    val max: Float,
    val type: String = "MONOPOLAR",
    val defaultMin: Float? = null,
    val defaultMax: Float? = null,
    val isAngle: Boolean = false,
    val description: String = ""
)

@Serializable
data class SourceMeta(
    val id: String,
    val name: String,
    val parameters: List<ParamMeta>,
    val feedback: Boolean = false,
    val description: String = ""
)

/**
 * DynamicVisualSource represents a visual generator driven by a custom fragment shader.
 *
 * SHADER OWNERSHIP MODEL:
 * Shaders are relatively heavy, stateless OpenGL program objects. Creating and compiling them for every
 * cloned instance (e.g., inside each Deck) is unnecessary and wasteful. Therefore, we use a shared
 * shader ownership model:
 * 1. The "master" instances of DynamicVisualSource loaded and managed by [VisualSourceRegistry] are the exclusive
 *    owners of their [shader] objects. For these master instances, [ownsShader] is set to `true`.
 * 2. When a deck clones a master instance (via [clone]), the clone references the same [shader] instance
 *    but has [ownsShader] set to `false`.
 * 3. Calling [dispose] on a master instance will delete the underlying OpenGL shader program because it owns it.
 *    Calling [dispose] on a cloned instance will release any local resources (like feedback FBOs) but will NOT
 *    dispose of the shared [shader], as it does not own it.
 */
open class DynamicVisualSource(
    val id: String,
    override val displayName: String,
    val shader: Shader,
    override val parameters: LinkedHashMap<String, ModulatableParameter>,
    override val globalAlpha: ModulatableParameter = ModulatableParameter(1.0f),
    val hasFeedback: Boolean = false,
    val ownsShader: Boolean = false // Only the master instances in the registry own the shader
) : VisualSource {
    var fb1: FBO? = null
    var fb2: FBO? = null
    var fbIndex: Int = 0

    init {
        // Registration moved to getParameterPaths
    }

    fun swapFeedbackBuffers() {
        fbIndex = 1 - fbIndex
    }

    fun getCurrentHistoryFBO(): FBO? = if (fbIndex == 0) fb1 else fb2
    fun getNextHistoryFBO(): FBO? = if (fbIndex == 0) fb2 else fb1

    open fun setupUniforms(shader: Shader) {
        parameters.forEach { (name, param) ->
            val uniformName = "u" + name.replace(" ", "")
            shader.setUniform(uniformName, param.value)
        }
    }

    override fun dispose() {
        fb1?.dispose()
        fb2?.dispose()
        fb1 = null
        fb2 = null
        if (ownsShader) {
            shader.dispose()
        }
    }

    override fun clear() {
        fb1?.clear(0f, 0f, 0f, 0f)
        fb2?.clear(0f, 0f, 0f, 0f)
    }

    override fun clone(): DynamicVisualSource {
        val clonedParams = LinkedHashMap<String, ModulatableParameter>()
        this.parameters.forEach { (name, param) ->
            clonedParams[name] = param.clone()
        }
        return DynamicVisualSource(
            id = this.id,
            displayName = this.displayName,
            shader = this.shader,
            parameters = clonedParams,
            globalAlpha = this.globalAlpha.clone(),
            hasFeedback = this.hasFeedback,
            ownsShader = false // Cloned instances do not own the shared shader
        )
    }

    override fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        parameters.forEach { (name, param) ->
            list.add("$prefix/$displayName/$name" to param)
        }
        list.add("$prefix/$displayName/Gain" to globalAlpha)
        return list
    }
}

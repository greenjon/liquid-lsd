package llm.slop.liquidlsd.rendering.isf

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.rendering.FBO
import llm.slop.liquidlsd.rendering.Geometry
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.utils.TimeSource
import kotlinx.serialization.json.*
import org.lwjgl.opengl.GL33.*
import kotlin.math.roundToInt

class ISFVisualSource(
    id: String,
    displayName: String,
    shader: Shader,
    val header: ISFHeader,
    parameters: LinkedHashMap<String, ModulatableParameter>,
    hasFeedback: Boolean = false,
    ownsShader: Boolean = false,
    is3D: Boolean = header.is3D || (parameters.containsKey("Rotate X") && parameters.containsKey("Rotate Y")),
    override val categories: List<String> = header.CATEGORIES ?: emptyList(),
    override val folderPath: String = "",
    val baseDir: java.io.File? = null,
    val importedTextures: Map<String, Int> = emptyMap(),
    val ownsTextures: Boolean = false
) : DynamicVisualSource(id, displayName, shader, parameters, hasFeedback = hasFeedback, ownsShader = ownsShader, is3D = is3D, categories = categories, folderPath = folderPath) {

    /**
     * Pre-parsed representation of an ISF pass dimension expression (e.g. "$WIDTH/2.0", "$HEIGHT", "512").
     * Parsed once at construction time (see [parseDimExpr]) so the render loop only performs plain
     * arithmetic on already-resolved operands — no String.replace/split in the hot path.
     */
    private sealed class DimOperand {
        class Literal(val value: Float) : DimOperand()
        object Width : DimOperand()
        object Height : DimOperand()
    }

    private class DimExpr(private val op: Char, private val left: DimOperand, private val right: DimOperand?) {
        fun eval(deckWidth: Int, deckHeight: Int): Int {
            val l = resolve(left, deckWidth, deckHeight)
            val r = right ?: return l.toInt()
            val rv = resolve(r, deckWidth, deckHeight)
            return when (op) {
                '/' -> (l / rv).toInt()
                '*' -> (l * rv).toInt()
                else -> l.toInt()
            }
        }

        private fun resolve(operand: DimOperand, deckWidth: Int, deckHeight: Int): Float = when (operand) {
            is DimOperand.Literal -> operand.value
            DimOperand.Width -> deckWidth.toFloat()
            DimOperand.Height -> deckHeight.toFloat()
        }
    }

    /**
     * Parses an ISF pass dimension expression once at load/compile time. Mirrors the grammar previously
     * handled by runtime string substitution: a bare literal, a bare $WIDTH/$HEIGHT token, a division
     * "TOKEN/TOKEN", or (when no "/" is present) a multiplication "TOKEN*TOKEN" — matching the prior
     * implementation's "/" takes priority over "*" behavior. Returns null when [expr] is null or
     * unparseable, meaning "use the caller's base dimension".
     */
    private fun parseDimExpr(expr: String?): DimExpr? {
        if (expr == null) return null
        return try {
            val divIdx = expr.indexOf('/')
            val mulIdx = expr.indexOf('*')
            if (divIdx >= 0) {
                val left = parseDimOperand(expr.substring(0, divIdx).trim()) ?: return null
                val right = parseDimOperand(expr.substring(divIdx + 1).trim()) ?: return null
                DimExpr('/', left, right)
            } else if (mulIdx >= 0) {
                val left = parseDimOperand(expr.substring(0, mulIdx).trim()) ?: return null
                val right = parseDimOperand(expr.substring(mulIdx + 1).trim()) ?: return null
                DimExpr('*', left, right)
            } else {
                val operand = parseDimOperand(expr.trim()) ?: return null
                DimExpr('=', operand, null)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDimOperand(token: String): DimOperand? = when (token) {
        "\$WIDTH" -> DimOperand.Width
        "\$HEIGHT" -> DimOperand.Height
        else -> token.toFloatOrNull()?.let { DimOperand.Literal(it) }
    }

    private var frameIndex = 0
    private var lastTime = TimeSource.getTimeSec().toFloat()

    // Multipass infrastructure — compiled once from the fixed header.PASSES list so the render loop
    // never does string parsing, map lookups, or Pair allocation (see ARCHITECTURE.md zero-alloc guarantees).
    private val passesArray: Array<ISFPass> = header.PASSES.toTypedArray()

    // Target name -> integer slot, resolved once at construction (never touched on the hot render path)
    private val nonPersistentTargetSlots: Map<String, Int>
    private val persistentTargetSlots: Map<String, Int>
    init {
        val nonPersistent = LinkedHashMap<String, Int>()
        val persistent = LinkedHashMap<String, Int>()
        for (pass in passesArray) {
            val target = pass.TARGET ?: continue
            if (pass.PERSISTENT) {
                if (!persistent.containsKey(target)) persistent[target] = persistent.size
            } else {
                if (!nonPersistent.containsKey(target)) nonPersistent[target] = nonPersistent.size
            }
        }
        nonPersistentTargetSlots = nonPersistent
        persistentTargetSlots = persistent
    }

    // Pre-parsed dimension expressions (e.g. "$WIDTH/2.0") — parsed once, evaluated with plain
    // arithmetic every frame instead of String.replace/split.
    private val passWidthExprs: Array<DimExpr?> = passesArray.map { parseDimExpr(it.WIDTH) }.toTypedArray()
    private val passHeightExprs: Array<DimExpr?> = passesArray.map { parseDimExpr(it.HEIGHT) }.toTypedArray()

    // Per-pass resolved slot indices (>=0 valid, -1 = not applicable), resolved once at construction
    private val passPersistentSlots: IntArray = IntArray(passesArray.size) { i ->
        val pass = passesArray[i]
        val target = pass.TARGET
        if (pass.PERSISTENT && target != null) persistentTargetSlots[target] ?: -1 else -1
    }
    private val passRegularSlots: IntArray = IntArray(passesArray.size) { i ->
        val pass = passesArray[i]
        val target = pass.TARGET
        if (!pass.PERSISTENT && target != null) nonPersistentTargetSlots[target] ?: -1 else -1
    }

    // Ping-pong slot: front = write target this frame, back = read/history source. swap() flips the
    // two FBO references in place with zero allocation (replaces the old Pair reallocation per frame).
    private class PingPongSlot(var front: FBO, var back: FBO) {
        fun swap() {
            val tmp = front
            front = back
            back = tmp
        }
    }

    private val regularFBOs: Array<FBO?> = arrayOfNulls(nonPersistentTargetSlots.size)
    private val persistentSlots: Array<PingPongSlot?> = arrayOfNulls(persistentTargetSlots.size)

    private var passWidth = 0
    private var passHeight = 0

    private class ImportedTextureBinding(val uniformName: String, val textureId: Int)
    val activeImportedTextures: Map<String, Int>
    private val importedBindings: Array<ImportedTextureBinding>
    private val shouldDisposeTextures: Boolean

    init {
        if (importedTextures.isNotEmpty()) {
            activeImportedTextures = importedTextures
            shouldDisposeTextures = ownsTextures
        } else if (baseDir != null) {
            val loaded = mutableMapOf<String, Int>()
            for (imported in header.getImportedAssets()) {
                val assetFile = java.io.File(baseDir, imported.path)
                val texId = ISFTextureLoader.loadTexture(assetFile)
                if (texId > 0) {
                    loaded[imported.name] = texId
                }
            }
            activeImportedTextures = loaded
            shouldDisposeTextures = ownsShader || loaded.isNotEmpty()
        } else {
            activeImportedTextures = emptyMap()
            shouldDisposeTextures = false
        }
        importedBindings = activeImportedTextures.map { (name, texId) ->
            ImportedTextureBinding(name, texId)
        }.toTypedArray()
    }

    private class PassBinding(val target: String, val isPersistent: Boolean, val slot: Int)
    private val passBindings: Array<PassBinding> = passesArray.mapNotNull { pass ->
        pass.TARGET?.let { target ->
            val slot = if (pass.PERSISTENT) persistentTargetSlots[target] ?: -1 else nonPersistentTargetSlots[target] ?: -1
            PassBinding(target, pass.PERSISTENT, slot)
        }
    }.toTypedArray()

    private sealed class ISFInputBinding {
        abstract fun apply(shader: Shader)

        class FloatInput(val name: String, val param: ModulatableParameter?) : ISFInputBinding() {
            override fun apply(shader: Shader) {
                shader.setUniform(name, param?.value ?: 0f)
            }
        }

        class BoolInput(val name: String, val param: ModulatableParameter?) : ISFInputBinding() {
            override fun apply(shader: Shader) {
                shader.setUniform(name, if ((param?.value ?: 0f) > 0.5f) 1 else 0)
            }
        }

        class LongInput(val name: String, val param: ModulatableParameter?) : ISFInputBinding() {
            override fun apply(shader: Shader) {
                shader.setUniform(name, (param?.value ?: 0f).toInt())
            }
        }

        class ColorInput(
            val name: String,
            val r: ModulatableParameter?,
            val g: ModulatableParameter?,
            val b: ModulatableParameter?,
            val a: ModulatableParameter?
        ) : ISFInputBinding() {
            override fun apply(shader: Shader) {
                shader.setUniform(name, r?.value ?: 0f, g?.value ?: 0f, b?.value ?: 0f, a?.value ?: 1f)
            }
        }

        class Point2DInput(
            val name: String,
            val x: ModulatableParameter?,
            val y: ModulatableParameter?
        ) : ISFInputBinding() {
            override fun apply(shader: Shader) {
                shader.setUniform(name, x?.value ?: 0f, y?.value ?: 0f)
            }
        }
    }

    private val inputBindings: Array<ISFInputBinding> = header.INPUTS.mapNotNull { input ->
        when (input.TYPE.lowercase()) {
            "float" -> ISFInputBinding.FloatInput(input.NAME, parameters[input.NAME])
            "bool" -> ISFInputBinding.BoolInput(input.NAME, parameters[input.NAME])
            "long" -> ISFInputBinding.LongInput(input.NAME, parameters[input.NAME])
            "color" -> ISFInputBinding.ColorInput(
                input.NAME,
                parameters["${input.NAME} R"],
                parameters["${input.NAME} G"],
                parameters["${input.NAME} B"],
                parameters["${input.NAME} A"]
            )
            "point2d" -> ISFInputBinding.Point2DInput(
                input.NAME,
                parameters["${input.NAME} X"],
                parameters["${input.NAME} Y"]
            )
            else -> null
        }
    }.toTypedArray()

    override fun setupUniforms(shader: Shader) {
        // 1. ISF standard uniforms
        val currentTime = TimeSource.getTimeSec().toFloat()
        val deltaTime = currentTime - lastTime
        lastTime = currentTime
        
        shader.setUniform("TIME", currentTime)
        shader.setUniform("TIMEDELTA", deltaTime)
        shader.setUniform("FRAMEINDEX", frameIndex++)
        
        // Derive DATE components via epoch arithmetic — zero allocation (no LocalDateTime/LocalTime objects)
        val epochMs = System.currentTimeMillis()
        val epochSec = epochMs / 1000L
        val secondsSinceMidnight = (epochSec % 86400L).toFloat() + ((epochMs % 1000L) / 1000f)
        // Gregorian calendar from epoch seconds (proleptic, valid well past 2100)
        val daysSinceEpoch = (epochSec / 86400L).toInt()
        val year400 = daysSinceEpoch / 146097; val rem400 = daysSinceEpoch % 146097
        val year100 = minOf(rem400 / 36524, 3); val rem100 = rem400 - year100 * 36524
        val year4   = rem100 / 1461;             val rem4   = rem100 % 1461
        val year1   = minOf(rem4 / 365, 3);      val rem1   = rem4 - year1 * 365
        val yearNum = 1970 + year400 * 400 + year100 * 100 + year4 * 4 + year1
        val isLeap  = (yearNum % 4 == 0 && yearNum % 100 != 0) || yearNum % 400 == 0
        val monthStarts = if (isLeap) MONTH_STARTS_LEAP else MONTH_STARTS_NORMAL
        var monthNum = 11
        for (m in 0..10) { if (rem1 < monthStarts[m + 1]) { monthNum = m; break } }
        shader.setUniform("DATE", yearNum.toFloat(), (monthNum + 1).toFloat(), (rem1 - monthStarts[monthNum] + 1).toFloat(), secondsSinceMidnight)
        
        // 2. ISF Input uniforms — pre-bound direct references, zero allocations per frame
        for (i in 0 until inputBindings.size) {
            inputBindings[i].apply(shader)
        }
    }

    override fun renderTopology(targetFBO: FBO) {
        if (passesArray.isEmpty()) {
            shader.setUniform("PASSINDEX", 0)
            shader.setUniform("RENDERSIZE", targetFBO.width.toFloat(), targetFBO.height.toFloat())
            var texUnit = 0
            for (i in 0 until importedBindings.size) {
                val b = importedBindings[i]
                if (b.textureId != 0) {
                    glActiveTexture(GL_TEXTURE0 + texUnit)
                    glBindTexture(GL_TEXTURE_2D, b.textureId)
                    shader.setUniform(b.uniformName, texUnit)
                    texUnit++
                }
            }
            Geometry.drawFullscreenQuad()
            if (texUnit > 0) glActiveTexture(GL_TEXTURE0)
            return
        }

        val width = targetFBO.width
        val height = targetFBO.height
        if (passWidth != width || passHeight != height) {
            passWidth = width
            passHeight = height
            resizePassFBOs()
        }

        val currentFbo = targetFBO.framebufferId

        for (passIdx in passesArray.indices) {
            val pass = passesArray[passIdx]
            val isFinalPass = (passIdx == passesArray.size - 1)

            // Determine target FBO — array indexing over pre-resolved slot indices, no map lookups
            val persistentSlotIdx = passPersistentSlots[passIdx]
            val regularSlotIdx = passRegularSlots[passIdx]
            val fboToBind = if (isFinalPass) {
                currentFbo
            } else if (persistentSlotIdx >= 0) {
                persistentSlots[persistentSlotIdx]?.front?.framebufferId ?: currentFbo
            } else if (regularSlotIdx >= 0) {
                regularFBOs[regularSlotIdx]?.framebufferId ?: currentFbo
            } else {
                currentFbo
            }

            glBindFramebuffer(GL_FRAMEBUFFER, fboToBind)

            val renderWidth = passWidthExprs[passIdx]?.eval(passWidth, passHeight) ?: width
            val renderHeight = passHeightExprs[passIdx]?.eval(passWidth, passHeight) ?: height
            glViewport(0, 0, renderWidth, renderHeight)

            shader.setUniform("PASSINDEX", passIdx)
            shader.setUniform("RENDERSIZE", renderWidth.toFloat(), renderHeight.toFloat())

            var texUnit = 0
            for (b in 0 until passBindings.size) {
                val binding = passBindings[b]
                val texToBind = if (binding.isPersistent) {
                    if (binding.slot >= 0) persistentSlots[binding.slot]?.back?.texture ?: 0 else 0
                } else {
                    if (binding.slot >= 0) regularFBOs[binding.slot]?.texture ?: 0 else 0
                }
                if (texToBind != 0) {
                    glActiveTexture(GL_TEXTURE0 + texUnit)
                    glBindTexture(GL_TEXTURE_2D, texToBind)
                    shader.setUniform(binding.target, texUnit)
                    texUnit++
                }
            }

            for (i in 0 until importedBindings.size) {
                val b = importedBindings[i]
                if (b.textureId != 0) {
                    glActiveTexture(GL_TEXTURE0 + texUnit)
                    glBindTexture(GL_TEXTURE_2D, b.textureId)
                    shader.setUniform(b.uniformName, texUnit)
                    texUnit++
                }
            }

            Geometry.drawFullscreenQuad()

            // Swap ping-pong persistent buffers — in-place reference swap, zero allocation
            if (persistentSlotIdx >= 0) {
                persistentSlots[persistentSlotIdx]?.swap()
            }
        }

        // Restore original FBO and viewport
        glBindFramebuffer(GL_FRAMEBUFFER, currentFbo)
        glViewport(0, 0, width, height)
        glActiveTexture(GL_TEXTURE0)
    }


    private fun resizePassFBOs() {
        // clear existing — this only runs on an actual resize, not the per-frame hot path
        for (i in regularFBOs.indices) {
            regularFBOs[i]?.dispose()
            regularFBOs[i] = null
        }
        for (i in persistentSlots.indices) {
            persistentSlots[i]?.let { it.front.dispose(); it.back.dispose() }
            persistentSlots[i] = null
        }

        for (i in passesArray.indices) {
            val pass = passesArray[i]
            val target = pass.TARGET ?: continue
            val passW = passWidthExprs[i]?.eval(passWidth, passHeight) ?: passWidth
            val passH = passHeightExprs[i]?.eval(passWidth, passHeight) ?: passHeight
            val format = if (pass.FLOAT) GL_RGBA32F else GL_RGBA8

            if (pass.PERSISTENT) {
                val slot = persistentTargetSlots[target] ?: continue
                val fbo1 = FBO(passW, passH, format)
                val fbo2 = FBO(passW, passH, format)
                fbo1.clear(0f, 0f, 0f, 0f)
                fbo2.clear(0f, 0f, 0f, 0f)
                persistentSlots[slot] = PingPongSlot(fbo1, fbo2)
            } else {
                val slot = nonPersistentTargetSlots[target] ?: continue
                regularFBOs[slot] = FBO(passW, passH, format)
            }
        }
    }

    override fun dispose() {
        super.dispose()
        for (i in regularFBOs.indices) {
            regularFBOs[i]?.dispose()
            regularFBOs[i] = null
        }
        for (i in persistentSlots.indices) {
            persistentSlots[i]?.let { it.front.dispose(); it.back.dispose() }
            persistentSlots[i] = null
        }
        if (shouldDisposeTextures) {
            for (b in importedBindings) {
                ISFTextureLoader.disposeTexture(b.textureId)
            }
        }
    }

    override fun clear() {
        super.clear()
        for (fbo in regularFBOs) {
            fbo?.clear(0f, 0f, 0f, 0f)
        }
        for (slot in persistentSlots) {
            slot?.let {
                it.front.clear(0f, 0f, 0f, 0f)
                it.back.clear(0f, 0f, 0f, 0f)
            }
        }
    }

    companion object {
        // Day-of-year offset for the 1st of each month (index 0=Jan … 11=Dec), plus sentinel at [12]
        // Used in setupUniforms() so the DATE computation is allocation-free on the render thread.
        private val MONTH_STARTS_NORMAL = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334,365)
        private val MONTH_STARTS_LEAP   = intArrayOf(0,31,60,91,121,152,182,213,244,274,305,335,366)

        fun createParameters(header: ISFHeader): LinkedHashMap<String, ModulatableParameter> {
            val params = LinkedHashMap<String, ModulatableParameter>()
            header.INPUTS.forEach { input ->
                when (input.TYPE.lowercase()) {
                    "float", "long" -> {
                        val default = (input.DEFAULT as? JsonPrimitive)?.floatOrNull ?: 0.5f
                        val min = (input.MIN as? JsonPrimitive)?.floatOrNull ?: 0.0f
                        val max = (input.MAX as? JsonPrimitive)?.floatOrNull ?: 1.0f
                        params[input.NAME] = ModulatableParameter(
                            baseValue = default,
                            minClamp = min,
                            maxClamp = max
                        )
                    }
                    "bool" -> {
                        val default = (input.DEFAULT as? JsonPrimitive)?.booleanOrNull ?: false
                        params[input.NAME] = ModulatableParameter(
                            baseValue = if (default) 1.0f else 0.0f,
                            minClamp = 0.0f,
                            maxClamp = 1.0f
                        )
                    }
                    "color" -> {
                        val defArray = input.DEFAULT as? JsonArray
                        val r = (defArray?.getOrNull(0) as? JsonPrimitive)?.floatOrNull ?: 1f
                        val g = (defArray?.getOrNull(1) as? JsonPrimitive)?.floatOrNull ?: 1f
                        val b = (defArray?.getOrNull(2) as? JsonPrimitive)?.floatOrNull ?: 1f
                        val a = (defArray?.getOrNull(3) as? JsonPrimitive)?.floatOrNull ?: 1f
                        
                        params["${input.NAME} R"] = ModulatableParameter(r, minClamp = 0f, maxClamp = 1f)
                        params["${input.NAME} G"] = ModulatableParameter(g, minClamp = 0f, maxClamp = 1f)
                        params["${input.NAME} B"] = ModulatableParameter(b, minClamp = 0f, maxClamp = 1f)
                        params["${input.NAME} A"] = ModulatableParameter(a, minClamp = 0f, maxClamp = 1f)
                    }
                    "point2d" -> {
                        val defArray = input.DEFAULT as? JsonArray
                        val x = (defArray?.getOrNull(0) as? JsonPrimitive)?.floatOrNull ?: 0.5f
                        val y = (defArray?.getOrNull(1) as? JsonPrimitive)?.floatOrNull ?: 0.5f

                        val minArray = input.MIN as? JsonArray
                        val minX = (minArray?.getOrNull(0) as? JsonPrimitive)?.floatOrNull ?: 0f
                        val minY = (minArray?.getOrNull(1) as? JsonPrimitive)?.floatOrNull ?: 0f

                        val maxArray = input.MAX as? JsonArray
                        val maxX = (maxArray?.getOrNull(0) as? JsonPrimitive)?.floatOrNull ?: 1f
                        val maxY = (maxArray?.getOrNull(1) as? JsonPrimitive)?.floatOrNull ?: 1f
                        
                        params["${input.NAME} X"] = ModulatableParameter(x, minClamp = minX, maxClamp = maxX)
                        params["${input.NAME} Y"] = ModulatableParameter(y, minClamp = minY, maxClamp = maxY)
                    }
                }
            }
            return params
        }
    }

    override fun clone(): ISFVisualSource {
        val clonedParams = LinkedHashMap<String, ModulatableParameter>()
        this.parameters.forEach { (name, param) ->
            clonedParams[name] = param.clone()
        }
        return ISFVisualSource(
            id = this.id,
            displayName = this.displayName,
            shader = this.shader,
            header = this.header,
            parameters = clonedParams,
            hasFeedback = this.hasFeedback,
            ownsShader = false,
            is3D = this.is3D,
            categories = this.categories,
            folderPath = this.folderPath,
            baseDir = this.baseDir,
            importedTextures = this.activeImportedTextures,
            ownsTextures = false
        )
    }
}



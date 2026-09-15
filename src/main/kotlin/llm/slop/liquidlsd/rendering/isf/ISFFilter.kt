package llm.slop.liquidlsd.rendering.isf

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.FBO
import llm.slop.liquidlsd.rendering.Geometry
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.VisualEffect
import llm.slop.liquidlsd.utils.TimeSource
import org.lwjgl.opengl.GL33.*

class ISFFilter(
    override val id: String,
    override val displayName: String,
    val header: ISFHeader,
    val shader: Shader,
    val ownsShader: Boolean = true,
    override val categories: List<String> = header.CATEGORIES.takeIf { it != null && it.isNotEmpty() } ?: listOf("Color Adjustment"),
    override val folderPath: String = "",
    val baseDir: java.io.File? = null,
    val importedTextures: Map<String, Int> = emptyMap(),
    val ownsTextures: Boolean = false
) : VisualEffect {

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
     * handled by runtime string substitution: a bare literal, a bare $WIDTH/$HEIGHT token, or a division
     * "TOKEN/TOKEN" (ISFFilter does not support "*", matching the prior implementation's behavior).
     * Returns null when [expr] is null or unparseable, meaning "use the caller's base dimension".
     */
    private fun parseDimExpr(expr: String?): DimExpr? {
        if (expr == null) return null
        return try {
            val divIdx = expr.indexOf('/')
            if (divIdx >= 0) {
                val left = parseDimOperand(expr.substring(0, divIdx).trim()) ?: return null
                val right = parseDimOperand(expr.substring(divIdx + 1).trim()) ?: return null
                DimExpr('/', left, right)
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

    override val parameters = mutableMapOf<String, ModulatableParameter>()
    override val dryWet = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    override var enabled = true

    private val inputImageName: String?
    private val transitionStartName: String
    private val transitionEndName: String

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

    private var deckWidth = 0
    private var deckHeight = 0

    private class ImportedTextureBinding(val uniformName: String, val textureId: Int)
    val activeImportedTextures: Map<String, Int>
    private val importedBindings: Array<ImportedTextureBinding>
    private val shouldDisposeTextures: Boolean

    private class PassBinding(val target: String, val isPersistent: Boolean, val slot: Int)
    private val passBindings: Array<PassBinding> = passesArray.mapNotNull { pass ->
        pass.TARGET?.let { target ->
            val slot = if (pass.PERSISTENT) persistentTargetSlots[target] ?: -1 else nonPersistentTargetSlots[target] ?: -1
            PassBinding(target, pass.PERSISTENT, slot)
        }
    }.toTypedArray()

    private class FilterParamBinding(val name: String, val type: String, val param: ModulatableParameter)
    private val filterParamBindings: Array<FilterParamBinding>
    private val transitionParamBindings: Array<FilterParamBinding>
    private val cachedParams: Array<ModulatableParameter>

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

        // Find the primary image input
        inputImageName = header.INPUTS.find { it.TYPE.lowercase() == "image" }?.NAME


        // Pre-resolve transition image names
        val imageInputs = header.INPUTS.filter { it.TYPE.lowercase() == "image" }
        transitionStartName = imageInputs.find { it.NAME.equals("startImage", true) || it.NAME.equals("inputImage", true) || it.NAME.equals("uTex1", true) }?.NAME
            ?: imageInputs.firstOrNull()?.NAME ?: "startImage"
        transitionEndName = imageInputs.find { it.NAME.equals("endImage", true) || it.NAME.equals("toImage", true) || it.NAME.equals("uTex2", true) }?.NAME
            ?: imageInputs.getOrNull(1)?.NAME ?: "endImage"

        // Create modulatable parameters for non-image inputs
        for (input in header.INPUTS) {
            if (input.TYPE.lowercase() == "image") continue

            val defaultVal = input.DEFAULT?.toString()?.toFloatOrNull() ?: 0.0f
            val valuesFloat = input.VALUES?.mapNotNull { it.toString().toFloatOrNull() }
            val minVal = input.MIN?.toString()?.toFloatOrNull()
                ?: valuesFloat?.minOrNull()
                ?: 0.0f
            val maxVal = input.MAX?.toString()?.toFloatOrNull()
                ?: valuesFloat?.maxOrNull()
                ?: 1.0f
            
            parameters[input.NAME] = ModulatableParameter(
                baseValue = defaultVal,
                minClamp = minVal,
                maxClamp = maxVal
            )
        }

        filterParamBindings = header.INPUTS.mapNotNull { input ->
            val param = parameters[input.NAME]
            if (param != null) FilterParamBinding(input.NAME, input.TYPE.lowercase(), param) else null
        }.toTypedArray()

        transitionParamBindings = filterParamBindings.filter {
            it.type != "image" && !it.name.equals("progress", ignoreCase = true)
        }.toTypedArray()

        cachedParams = parameters.values.toTypedArray()
    }

    override fun update() {
        dryWet.evaluate()
        for (i in 0 until cachedParams.size) {
            cachedParams[i].evaluate()
        }
    }

    /**
     * Renders the filter reading from [inputTexture] into the currently bound FBO.
     */
    fun render(inputTexture: Int, width: Int, height: Int) {
        // Handle resizing passes if needed
        if (deckWidth != width || deckHeight != height) {
            deckWidth = width
            deckHeight = height
            resizePassFBOs()
        }

        shader.bind()

        // Set standard ISF uniforms
        val currentTime = TimeSource.getTimeSec().toFloat()
        val deltaTime = currentTime - lastTime
        lastTime = currentTime

        shader.setUniform("TIME", currentTime)
        shader.setUniform("TIMEDELTA", deltaTime)
        shader.setUniform("FRAMEINDEX", frameIndex++)
        setUniformDate(shader)

        // Set user parameters — indexed loop over pre-bound parameters
        for (i in 0 until filterParamBindings.size) {
            val binding = filterParamBindings[i]
            when (binding.type) {
                "float" -> shader.setUniform(binding.name, binding.param.value)
                "long", "int" -> shader.setUniform(binding.name, binding.param.value.toInt())
                "bool" -> shader.setUniform(binding.name, if (binding.param.value > 0.5f) 1 else 0)
                "point2d" -> shader.setUniform(binding.name, binding.param.value, 0f) 
                "color" -> shader.setUniform(binding.name, binding.param.value, binding.param.value, binding.param.value, 1.0f) 
            }
        }

        if (passesArray.isEmpty()) {
            shader.setUniform("PASSINDEX", 0)
            shader.setUniform("RENDERSIZE", width.toFloat(), height.toFloat())
            var texUnit = 0
            if (inputImageName != null) {
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, inputTexture)
                shader.setUniform(inputImageName, 0)
                texUnit = 1
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
            if (texUnit > 0) glActiveTexture(GL_TEXTURE0)
        } else {
            // Restore FBO ID later since we bind our own pass targets
            val currentFbo = glGetInteger(GL_FRAMEBUFFER_BINDING)
            glDisable(GL_BLEND)

            for (passIdx in passesArray.indices) {
                val pass = passesArray[passIdx]
                val isFinalPass = (passIdx == passesArray.size - 1)

                // Determine target FBO — array indexing over pre-resolved slot indices, no map lookups
                val persistentSlotIdx = passPersistentSlots[passIdx]
                val regularSlotIdx = passRegularSlots[passIdx]
                val fboToBind = if (isFinalPass) {
                    currentFbo
                } else if (persistentSlotIdx >= 0) {
                    // write to the active persistent FBO
                    persistentSlots[persistentSlotIdx]?.front?.framebufferId ?: currentFbo
                } else if (regularSlotIdx >= 0) {
                    regularFBOs[regularSlotIdx]?.framebufferId ?: currentFbo
                } else {
                    currentFbo
                }

                glBindFramebuffer(GL_FRAMEBUFFER, fboToBind)

                val renderWidth = passWidthExprs[passIdx]?.eval(deckWidth, deckHeight) ?: width
                val renderHeight = passHeightExprs[passIdx]?.eval(deckWidth, deckHeight) ?: height
                glViewport(0, 0, renderWidth, renderHeight)

                shader.setUniform("PASSINDEX", passIdx)
                shader.setUniform("RENDERSIZE", renderWidth.toFloat(), renderHeight.toFloat())

                var texUnit = 0

                // Bind input image
                if (inputImageName != null) {
                    glActiveTexture(GL_TEXTURE0 + texUnit)
                    glBindTexture(GL_TEXTURE_2D, inputTexture)
                    shader.setUniform(inputImageName, texUnit)
                    texUnit++
                }

                // Bind all pass targets available — zero-allocation loop over pre-resolved pass bindings
                for (b in 0 until passBindings.size) {
                    val binding = passBindings[b]
                    val texToBind = if (binding.isPersistent) {
                        // read from the history persistent FBO
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

                // Bind imported assets
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


                // Swap ping pong buffers if persistent — in-place reference swap, zero allocation
                if (persistentSlotIdx >= 0) {
                    persistentSlots[persistentSlotIdx]?.swap()
                }
            }
            
            // Restore original FBO and viewport
            glBindFramebuffer(GL_FRAMEBUFFER, currentFbo)
            glViewport(0, 0, width, height)
        }

        shader.unbind()
        glActiveTexture(GL_TEXTURE0)
    }

    /**
     * Renders a 2-image transition reading from [startTexture] (Deck A) and [endTexture] (Deck B)
     * into the currently bound FBO using [progressValue] (0.0 to 1.0).
     */
    fun renderTransition(startTexture: Int, endTexture: Int, progressValue: Float, width: Int, height: Int) {
        if (deckWidth != width || deckHeight != height) {
            deckWidth = width
            deckHeight = height
            resizePassFBOs()
        }

        shader.bind()

        val currentTime = TimeSource.getTimeSec().toFloat()
        val deltaTime = currentTime - lastTime
        lastTime = currentTime

        shader.setUniform("TIME", currentTime)
        shader.setUniform("TIMEDELTA", deltaTime)
        shader.setUniform("FRAMEINDEX", frameIndex++)
        setUniformDate(shader)

        // Set user parameters — indexed loop over pre-bound transition parameters
        shader.setUniform("progress", progressValue)
        for (i in 0 until transitionParamBindings.size) {
            val binding = transitionParamBindings[i]
            when (binding.type) {
                "float" -> shader.setUniform(binding.name, binding.param.value)
                "long", "int" -> shader.setUniform(binding.name, binding.param.value.toInt())
                "bool" -> shader.setUniform(binding.name, if (binding.param.value > 0.5f) 1 else 0)
                "point2d" -> shader.setUniform(binding.name, binding.param.value, 0f) 
                "color" -> shader.setUniform(binding.name, binding.param.value, binding.param.value, binding.param.value, 1.0f) 
            }
        }

        shader.setUniform("PASSINDEX", 0)
        shader.setUniform("RENDERSIZE", width.toFloat(), height.toFloat())

        // Map pre-resolved image inputs (startImage -> unit 0, endImage -> unit 1)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, startTexture)
        shader.setUniform(transitionStartName, 0)

        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, endTexture)
        shader.setUniform(transitionEndName, 1)

        var transTexUnit = 2
        for (i in 0 until importedBindings.size) {
            val b = importedBindings[i]
            if (b.textureId != 0) {
                glActiveTexture(GL_TEXTURE0 + transTexUnit)
                glBindTexture(GL_TEXTURE_2D, b.textureId)
                shader.setUniform(b.uniformName, transTexUnit)
                transTexUnit++
            }
        }

        Geometry.drawFullscreenQuad()

        shader.unbind()
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

        // create new
        for (i in passesArray.indices) {
            val pass = passesArray[i]
            val target = pass.TARGET ?: continue
            val passW = passWidthExprs[i]?.eval(deckWidth, deckHeight) ?: deckWidth
            val passH = passHeightExprs[i]?.eval(deckWidth, deckHeight) ?: deckHeight
            val format = if (pass.FLOAT) GL_RGBA32F else GL_RGBA8

            if (pass.PERSISTENT) {
                val slot = persistentTargetSlots[target] ?: continue
                val fbo1 = FBO(passW, passH, format)
                val fbo2 = FBO(passW, passH, format)
                // Initialize persistent buffers to zero/transparent
                fbo1.clear(0f,0f,0f,0f)
                fbo2.clear(0f,0f,0f,0f)
                persistentSlots[slot] = PingPongSlot(fbo1, fbo2)
            } else {
                val slot = nonPersistentTargetSlots[target] ?: continue
                regularFBOs[slot] = FBO(passW, passH, format)
            }
        }
    }

    override fun clone(): ISFFilter {
        val copy = ISFFilter(
            id = id,
            displayName = displayName,
            header = header,
            shader = shader,
            ownsShader = false,
            categories = this.categories,
            folderPath = this.folderPath,
            baseDir = this.baseDir,
            importedTextures = this.activeImportedTextures,
            ownsTextures = false
        )
        copy.enabled = this.enabled
        copy.dryWet.baseValue = this.dryWet.baseValue
        // Note: modulators are not cloned here as they are managed by the Preset/Deck logic
        this.parameters.forEach { (name, param) ->
            copy.parameters[name]?.baseValue = param.baseValue
        }
        return copy
    }

    override fun reset() {
        enabled = true
        dryWet.reset()
        parameters.values.forEach { it.reset() }
        frameIndex = 0
        for (slot in persistentSlots) {
            slot?.let {
                it.front.clear(0f, 0f, 0f, 0f)
                it.back.clear(0f, 0f, 0f, 0f)
            }
        }
        for (fbo in regularFBOs) {
            fbo?.clear(0f, 0f, 0f, 0f)
        }
    }

    override fun dispose() {
        if (ownsShader) {
            shader.dispose()
        }
        if (shouldDisposeTextures) {
            for (b in importedBindings) {
                ISFTextureLoader.disposeTexture(b.textureId)
            }
        }
        for (i in regularFBOs.indices) {
            regularFBOs[i]?.dispose()
            regularFBOs[i] = null
        }
        for (i in persistentSlots.indices) {
            persistentSlots[i]?.let { it.front.dispose(); it.back.dispose() }
            persistentSlots[i] = null
        }
    }


    override fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        list.add("$prefix/DryWet" to dryWet)
        parameters.forEach { (name, param) ->
            list.add("$prefix/$name" to param)
        }
        return list
    }

    private fun setUniformDate(shader: Shader) {
        val epochMs = System.currentTimeMillis()
        val epochSec = epochMs / 1000L
        val secondsSinceMidnight = (epochSec % 86400L).toFloat() + ((epochMs % 1000L) / 1000f)
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
    }

    companion object {
        private val MONTH_STARTS_NORMAL = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365)
        private val MONTH_STARTS_LEAP   = intArrayOf(0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335, 366)
    }
}

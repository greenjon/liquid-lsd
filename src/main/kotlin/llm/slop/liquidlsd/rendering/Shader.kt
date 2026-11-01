package llm.slop.liquidlsd.rendering

import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*

private val logger = KotlinLogging.logger {}

/**
 * GLSL Shader program wrapper.
 * Handles loading, compiling, linking, and uniform management.
 */
class Shader(vertexSource: String, fragmentSource: String) {
    
    val programId: Int
    private val vertexId: Int
    private val fragmentId: Int
    private var isDisposed = false
    private val uniformLocationCache = mutableMapOf<String, Int>()

    /** Uniforms the source expects the compiler to drop (LSD_PHASES rate inputs); a missing one is not worth a warning. */
    var quietUniforms: Set<String> = emptySet()

    /**
     * Active sampler uniforms. The renderer sets built-in scalars by fixed names (`time`, `mouse`, ...); a shader may
     * use one of those names for its own texture (an ISF pass target called `time`), and a float set on a sampler is a
     * GL error, so the float/vector setters skip these names.
     */
    private val samplerNames = HashSet<String>()

    init {
        var vId = 0
        var fId = 0
        var pId = 0
        try {
            // Compile vertex shader
            vId = compileShader(GL_VERTEX_SHADER, vertexSource)
            
            // Compile fragment shader
            fId = compileShader(GL_FRAGMENT_SHADER, fragmentSource)
            
            // Link program
            pId = glCreateProgram()
            if (pId == 0) {
                throw RuntimeException("Failed to create shader program object.")
            }
            glAttachShader(pId, vId)
            glAttachShader(pId, fId)
            glLinkProgram(pId)
            
            // Check link status
            if (glGetProgrami(pId, GL_LINK_STATUS) == GL_FALSE) {
                val log = glGetProgramInfoLog(pId)
                throw RuntimeException("Shader program linking failed:\n$log")
            }
            
            // Validate program
            // macOS core profile fails validation without a bound VAO, which is not a real defect
            if (!System.getProperty("os.name").lowercase().contains("mac")) glValidateProgram(pId)
            if (!System.getProperty("os.name").lowercase().contains("mac") &&
                glGetProgrami(pId, GL_VALIDATE_STATUS) == GL_FALSE) {
                logger.warn { "Shader program validation warning:\n${glGetProgramInfoLog(pId)}" }
            }
            
            collectSamplerNames(pId)

            vertexId = vId
            fragmentId = fId
            programId = pId
            logger.debug { "Created shader program $programId" }
            GLResourceTracker.register(programId, "Shader created at ${Thread.currentThread().stackTrace[2]}")
        } catch (e: Exception) {
            // Clean up whatever was created to prevent OpenGL leaks on failure
            if (pId != 0) {
                if (vId != 0) glDetachShader(pId, vId)
                if (fId != 0) glDetachShader(pId, fId)
                glDeleteProgram(pId)
            }
            if (vId != 0) {
                glDeleteShader(vId)
            }
            if (fId != 0) {
                glDeleteShader(fId)
            }
            throw e
        }
    }

    private fun collectSamplerNames(program: Int) {
        val count = glGetProgrami(program, GL_ACTIVE_UNIFORMS)
        val size = org.lwjgl.BufferUtils.createIntBuffer(1)
        val type = org.lwjgl.BufferUtils.createIntBuffer(1)
        for (i in 0 until count) {
            val name = glGetActiveUniform(program, i, size, type)
            if (type.get(0) in SAMPLER_TYPES) samplerNames.add(name.removeSuffix("[0]"))
        }
    }

    /** Location for a float/vector value: -1 (a no-op) when [name] is a sampler in this program. */
    private fun valueLocation(name: String): Int = if (name in samplerNames) -1 else getUniformLocation(name)

    /**
     * Compile a shader from source code
     */
    private fun compileShader(type: Int, source: String): Int {
        val adaptedSource = if (source.contains("#version 300 es")) {
            source.replace(Regex("^#version 300 es", RegexOption.MULTILINE), "#version 330 core")
                .replace(Regex("^precision (highp|mediump|lowp) (float|int);", RegexOption.MULTILINE), "// precision qualification")
        } else {
            source
        }
        val shaderId = glCreateShader(type)
        glShaderSource(shaderId, adaptedSource)
        glCompileShader(shaderId)
        
        // Check compilation status
        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            val log = glGetShaderInfoLog(shaderId)
            val typeName = if (type == GL_VERTEX_SHADER) "vertex" else "fragment"
            glDeleteShader(shaderId)
            throw RuntimeException("$typeName shader compilation failed:\n$log")
        }
        
        return shaderId
    }

    /**
     * Bind this shader for use
     */
    fun bind() {
        check(!isDisposed) { "Shader program is already disposed!" }
        glUseProgram(programId)
    }

    /**
     * Unbind this shader
     */
    fun unbind() {
        glUseProgram(0)
    }

    /**
     * Get the location of a uniform variable (cached)
     */
    private fun getUniformLocation(name: String): Int {
        return uniformLocationCache.getOrPut(name) {
            var location = glGetUniformLocation(programId, name)
            if (location == -1 && !name.endsWith("[0]")) {
                location = glGetUniformLocation(programId, "$name[0]")
            }
            if (location == -1) {
                if (name !in SYSTEM_UNIFORMS && name !in quietUniforms) {
                    logger.warn { "Uniform '$name' not found in shader program $programId" }
                }
            }
            location
        }
    }

    // Uniform setters
    fun setUniform(name: String, value: Int) {
        glUniform1i(getUniformLocation(name), value)
    }

    fun setUniform(name: String, value: Float) {
        glUniform1f(valueLocation(name), value)
    }

    fun setUniform(name: String, x: Float, y: Float) {
        glUniform2f(valueLocation(name), x, y)
    }

    fun setUniform(name: String, x: Float, y: Float, z: Float) {
        glUniform3f(valueLocation(name), x, y, z)
    }

    fun setUniform(name: String, x: Float, y: Float, z: Float, w: Float) {
        glUniform4f(valueLocation(name), x, y, z, w)
    }

    fun setUniform4fv(name: String, values: FloatArray) {
        val location = getUniformLocation(name)
        if (location != -1) {
            glUniform4fv(location, values)
        }
    }

    fun setUniform3fv(name: String, values: FloatArray) {
        val location = getUniformLocation(name)
        if (location != -1) {
            glUniform3fv(location, values)
        }
    }

    /**
     * Clean up OpenGL resources
     */
    fun dispose() {
        if (!isDisposed) {
            glDetachShader(programId, vertexId)
            glDetachShader(programId, fragmentId)
            glDeleteShader(vertexId)
            glDeleteShader(fragmentId)
            glDeleteProgram(programId)
            isDisposed = true
            GLResourceTracker.unregister(programId)
            logger.debug { "Disposed shader program $programId" }
        }
    }



    companion object {
        private val SAMPLER_TYPES = setOf(GL_SAMPLER_1D, GL_SAMPLER_2D, GL_SAMPLER_3D, GL_SAMPLER_CUBE, GL_SAMPLER_2D_RECT, GL_SAMPLER_2D_SHADOW)

        val SYSTEM_UNIFORMS: Set<String> = setOf(
            "uTime", "uAlpha", "uResolution", "TIME", "TIMEDELTA", "FRAMEINDEX", "DATE",
            "RENDERSIZE", "uAspectRatio", "uZoom", "uRotateZ", "PASSINDEX", "src",
            "iResolution", "u_resolution", "resolution",
            "iTime", "u_time", "time", "iTimeDelta", "u_delta",
            "iFrame", "u_frame", "iFrameRate", "iDate",
            "iMouse", "u_mouse", "mouse",
            "audioVolume", "audioBass", "audioMid", "audioTreble",
            "audioFFT", "iChannel0", "iChannel1", "iChannel2", "iChannel3",
            "iChannelResolution", "iChannelTime"
        )
        /**
         * Load a shader from resource files
         */
        fun fromResources(vertexPath: String, fragmentPath: String): Shader {
            val vertexSource = loadResource(vertexPath)
            val fragmentSource = loadResource(fragmentPath)
            return Shader(vertexSource, fragmentSource)
        }

        private fun loadResource(path: String): String {
            val stream = Shader::class.java.classLoader.getResourceAsStream(path)
                ?: throw RuntimeException("Shader resource not found: $path")
            return stream.bufferedReader().use { it.readText() }
        }
    }
}

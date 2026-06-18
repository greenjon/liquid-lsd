package llm.slop.spirals.rendering

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

    init {
        // Compile vertex shader
        vertexId = compileShader(GL_VERTEX_SHADER, vertexSource)
        
        // Compile fragment shader
        fragmentId = compileShader(GL_FRAGMENT_SHADER, fragmentSource)
        
        // Link program
        programId = glCreateProgram()
        glAttachShader(programId, vertexId)
        glAttachShader(programId, fragmentId)
        glLinkProgram(programId)
        
        // Check link status
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            val log = glGetProgramInfoLog(programId)
            throw RuntimeException("Shader program linking failed:\n$log")
        }
        
        // Validate program
        glValidateProgram(programId)
        if (glGetProgrami(programId, GL_VALIDATE_STATUS) == GL_FALSE) {
            logger.warn { "Shader program validation warning:\n${glGetProgramInfoLog(programId)}" }
        }
        
        logger.debug { "Created shader program $programId" }
    }

    /**
     * Compile a shader from source code
     */
    private fun compileShader(type: Int, source: String): Int {
        val shaderId = glCreateShader(type)
        glShaderSource(shaderId, source)
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
            val location = glGetUniformLocation(programId, name)
            if (location == -1) {
                logger.warn { "Uniform '$name' not found in shader program $programId" }
            }
            location
        }
    }

    // Uniform setters
    fun setUniform(name: String, value: Int) {
        glUniform1i(getUniformLocation(name), value)
    }

    fun setUniform(name: String, value: Float) {
        glUniform1f(getUniformLocation(name), value)
    }

    fun setUniform(name: String, x: Float, y: Float) {
        glUniform2f(getUniformLocation(name), x, y)
    }

    fun setUniform(name: String, x: Float, y: Float, z: Float) {
        glUniform3f(getUniformLocation(name), x, y, z)
    }

    fun setUniform(name: String, x: Float, y: Float, z: Float, w: Float) {
        glUniform4f(getUniformLocation(name), x, y, z, w)
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
            logger.debug { "Disposed shader program $programId" }
        }
    }

    protected fun finalize() {
        if (!isDisposed) {
            logger.warn { "Shader program $programId was not disposed properly!" }
        }
    }

    companion object {
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

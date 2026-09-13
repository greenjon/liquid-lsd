package llm.slop.liquidlsd.rendering.isf

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("isf-library")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ISFUserLibraryTest {

    private val isfDir: File by lazy {
        val userPath = ISFDirectoryManager.expandPath("~/.local/share/isf")
        File(userPath)
    }

    private val shaderFiles: List<File> by lazy {
        if (!isfDir.exists() || !isfDir.isDirectory) {
            emptyList()
        } else {
            isfDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in setOf("fs", "isf", "frag") }
                .sortedBy { it.name.lowercase() }
                .toList()
        }
    }

    @BeforeAll
    fun checkDirectoryExists() {
        assumeTrue(
            isfDir.exists() && isfDir.isDirectory,
            "User ISF directory ~/.local/share/isf does not exist; skipping ISFUserLibraryTest."
        )
        assumeTrue(
            shaderFiles.isNotEmpty(),
            "No ISF fragment shaders found in ~/.local/share/isf; skipping."
        )
    }

    @Test
    fun `test all ISF shader headers parse successfully`() {
        val parseFailures = mutableListOf<String>()
        var validHeaderCount = 0

        for (file in shaderFiles) {
            val source = file.readText()
            val header = ISFParser.parseHeader(source)
            if (header == null) {
                parseFailures.add("${file.name}: Failed to extract/parse JSON header")
            } else {
                validHeaderCount++
            }
        }

        assertTrue(
            parseFailures.isEmpty(),
            "Found ${parseFailures.size} header parsing failure(s):\n" + parseFailures.joinToString("\n")
        )
        assertTrue(validHeaderCount >= 300, "Expected at least 300 valid headers, found $validHeaderCount")
    }

    @Test
    fun `test all ISF shader inputs have valid schema and bounds`() {
        val invalidInputs = mutableListOf<String>()
        val invertedBoundsWarnings = mutableListOf<String>()
        val supportedTypes = setOf("float", "bool", "long", "int", "color", "point2d", "image", "audio", "audiofft", "event")

        for (file in shaderFiles) {
            val source = file.readText()
            val header = ISFParser.parseHeader(source) ?: continue

            for (input in header.INPUTS) {
                val inputType = input.TYPE.lowercase()
                if (inputType !in supportedTypes) {
                    invalidInputs.add("${file.name}: Input '${input.NAME}' has unsupported TYPE '${input.TYPE}'")
                }

                val minVal = (input.MIN as? JsonPrimitive)?.floatOrNull
                val maxVal = (input.MAX as? JsonPrimitive)?.floatOrNull

                if (minVal != null && maxVal != null && minVal > maxVal) {
                    invertedBoundsWarnings.add("${file.name}: Input '${input.NAME}' has inverted range [MIN=$minVal, MAX=$maxVal]")
                }
            }
        }

        if (invertedBoundsWarnings.isNotEmpty()) {
            println("Noted ${invertedBoundsWarnings.size} intentional inverted slider range(s):\n" + invertedBoundsWarnings.joinToString("\n"))
        }

        assertTrue(
            invalidInputs.isEmpty(),
            "Found ${invalidInputs.size} input validation issue(s):\n" + invalidInputs.take(25).joinToString("\n")
        )
    }

    @Test
    fun `test all ISF passes and imported assets resolve correctly`() {
        val passIssues = mutableListOf<String>()
        val missingAssets = mutableListOf<String>()

        for (file in shaderFiles) {
            val source = file.readText()
            val header = ISFParser.parseHeader(source) ?: continue

            // Validate passes
            for (pass in header.PASSES) {
                val target = pass.TARGET
                if (target != null && (target.isBlank() || target.contains(" "))) {
                    passIssues.add("${file.name}: Invalid PASS TARGET name '$target'")
                }
            }

            // Validate imported textures
            val importedList = header.getImportedAssets()
            for (asset in importedList) {
                val assetFile = File(file.parentFile, asset.path)
                if (!assetFile.exists()) {
                    missingAssets.add("${file.name}: IMPORTED asset '${asset.name}' not found at '${assetFile.path}'")
                }
            }
        }

        assertTrue(
            passIssues.isEmpty(),
            "Found ${passIssues.size} pass validation issue(s):\n" + passIssues.joinToString("\n")
        )
        assertTrue(
            missingAssets.isEmpty(),
            "Found ${missingAssets.size} missing imported asset(s):\n" + missingAssets.joinToString("\n")
        )
    }

    @Test
    fun `test scanner categorizes shaders and detects roles accurately`() {
        val assets = ISFScanner.scanDirectory(isfDir, DirectorySourceType.USER_STANDARD, isfDir.absolutePath)
        assertTrue(assets.isNotEmpty(), "Scanner should discover assets from ~/.local/share/isf")

        val generators = assets.filter { it.type == ISFAssetType.GENERATOR }
        val filters = assets.filter { it.type == ISFAssetType.FILTER }
        val transitions = assets.filter { it.type == ISFAssetType.TRANSITION }

        assertTrue(generators.isNotEmpty(), "Should discover Generator assets")
        assertTrue(filters.isNotEmpty(), "Should discover Filter assets")
        assertTrue(transitions.isNotEmpty(), "Should discover Transition assets")

        for (asset in assets) {
            assertTrue(asset.id.isNotBlank(), "Asset ID should not be blank")
            assertTrue(asset.displayName.isNotBlank(), "Asset display name should not be blank for ${asset.id}")
            assertTrue(File(asset.sourcePath).exists(), "Asset source file must exist: ${asset.sourcePath}")
        }
    }

    @Test
    fun `test all fragment shaders transpile to valid GLSL 330 core`() {
        val transpilationErrors = mutableListOf<String>()
        var successCount = 0

        for (file in shaderFiles) {
            val source = file.readText()
            val header = ISFParser.parseHeader(source) ?: continue

            try {
                val glsl = ISFParser.buildGLSLFragmentShader(source, header)
                assertTrue(glsl.startsWith("#version 330 core"), "${file.name}: Expected #version 330 core header")
                assertTrue(glsl.contains("out vec4 isf_FragColor;"), "${file.name}: Expected isf_FragColor output")
                assertTrue(glsl.contains("uniform vec2 RENDERSIZE;"), "${file.name}: Expected RENDERSIZE uniform")
                assertTrue(glsl.contains("uniform float TIME;"), "${file.name}: Expected TIME uniform")
                successCount++
            } catch (e: Exception) {
                transpilationErrors.add("${file.name}: Preprocessing exception: ${e.message}")
            }
        }

        assertTrue(
            transpilationErrors.isEmpty(),
            "Found ${transpilationErrors.size} transpilation error(s):\n" + transpilationErrors.joinToString("\n")
        )
        assertTrue(successCount >= 300, "Expected at least 300 successfully transpiled shaders, got $successCount")
    }

    @Test
    fun `test GLSL driver compilation on offscreen context`() {
        val hasGlfw = try {
            glfwInit()
        } catch (e: Throwable) {
            false
        }

        assumeTrue(hasGlfw, "GLFW failed to initialize in test environment; skipping OpenGL driver compilation.")

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        val window = glfwCreateWindow(64, 64, "ISF Compilation Smoke Test", 0L, 0L)
        assumeTrue(window != 0L, "Could not create offscreen GLFW window; skipping OpenGL driver compilation.")

        try {
            glfwMakeContextCurrent(window)
            GL.createCapabilities()

            val cleanShaders = mutableListOf<String>()
            val compileWarnings = mutableMapOf<String, String>()
            val compileFailures = mutableMapOf<String, String>()

            for (file in shaderFiles) {
                val source = file.readText()
                val header = ISFParser.parseHeader(source) ?: continue
                val glsl = try {
                    ISFParser.buildGLSLFragmentShader(source, header)
                } catch (e: Exception) {
                    compileFailures[file.name] = "Transpilation failed: ${e.message}"
                    continue
                }

                val shaderId = glCreateShader(GL_FRAGMENT_SHADER)
                if (shaderId == 0) {
                    compileFailures[file.name] = "glCreateShader returned 0"
                    continue
                }

                glShaderSource(shaderId, glsl)
                glCompileShader(shaderId)

                val status = glGetShaderi(shaderId, GL_COMPILE_STATUS)
                val infoLog = glGetShaderInfoLog(shaderId).trim()

                if (status == GL_FALSE) {
                    compileFailures[file.name] = infoLog
                } else {
                    if (infoLog.isNotBlank()) {
                        compileWarnings[file.name] = infoLog
                    } else {
                        cleanShaders.add(file.name)
                    }
                }
                glDeleteShader(shaderId)
            }

            // Write detailed markdown audit report
            val reportDir = File("build/reports")
            if (!reportDir.exists()) reportDir.mkdirs()
            val reportFile = File(reportDir, "isf_shader_audit.md")

            val reportContent = buildString {
                appendLine("# ISF Shader Audit Report")
                appendLine()
                appendLine("Generated across **${shaderFiles.size}** fragment shaders in `~/.local/share/isf`.")
                appendLine()
                appendLine("| Metric | Count | Percentage |")
                appendLine("| :--- | :--- | :--- |")
                val total = shaderFiles.size.toFloat()
                val cleanCount = cleanShaders.size + compileWarnings.size
                appendLine("| **Total Fragment Shaders** | ${shaderFiles.size} | 100% |")
                appendLine("| **Compiled Cleanly (No Errors)** | $cleanCount | ${"%.1f".format(cleanCount / total * 100f)}% |")
                appendLine("| **Zero Warnings / Flawless** | ${cleanShaders.size} | ${"%.1f".format(cleanShaders.size / total * 100f)}% |")
                appendLine("| **Driver Warnings Only** | ${compileWarnings.size} | ${"%.1f".format(compileWarnings.size / total * 100f)}% |")
                appendLine("| **Compilation Errors** | ${compileFailures.size} | ${"%.1f".format(compileFailures.size / total * 100f)}% |")
                appendLine()

                if (compileFailures.isNotEmpty()) {
                    appendLine("## Compilation Failures (${compileFailures.size})")
                    appendLine()
                    for ((name, log) in compileFailures.entries.sortedBy { it.key }) {
                        appendLine("### `$name`")
                        appendLine("```")
                        appendLine(log)
                        appendLine("```")
                        appendLine()
                    }
                }

                if (compileWarnings.isNotEmpty()) {
                    appendLine("## Compilation Warnings (${compileWarnings.size})")
                    appendLine()
                    for ((name, log) in compileWarnings.entries.sortedBy { it.key }) {
                        appendLine("### `$name`")
                        appendLine("```")
                        appendLine(log)
                        appendLine("```")
                        appendLine()
                    }
                }
            }
            reportFile.writeText(reportContent)
            println("Audit report written to: ${reportFile.absolutePath}")

            println("ISF GLSL Compilation Results: ${cleanShaders.size + compileWarnings.size} / ${shaderFiles.size} compiled successfully (${cleanShaders.size} clean, ${compileWarnings.size} with warnings, ${compileFailures.size} failed).")

            // Ensure high compile success rate threshold (> 90%)
            val successRate = (cleanShaders.size + compileWarnings.size).toFloat() / shaderFiles.size.toFloat()
            assertTrue(
                successRate >= 0.90f,
                "Expected at least 90% compile success rate, got ${"%.1f".format(successRate * 100f)}% (${compileFailures.size} failed)"
            )
        } finally {
            glfwDestroyWindow(window)
            glfwTerminate()
        }
    }

    @Test
    fun `test all paired vertex shaders compile and link cleanly with their fragment shaders`() {
        val hasGlfw = try {
            glfwInit()
        } catch (e: Throwable) {
            false
        }

        assumeTrue(hasGlfw, "GLFW failed to initialize in test environment; skipping OpenGL driver compilation.")

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        val window = glfwCreateWindow(64, 64, "ISF Vertex Linking Smoke Test", 0L, 0L)
        assumeTrue(window != 0L, "Could not create offscreen GLFW window; skipping OpenGL driver compilation.")

        try {
            glfwMakeContextCurrent(window)
            GL.createCapabilities()

            val vsFiles = isfDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in setOf("vs", "vert") }
                .sortedBy { it.name.lowercase() }
                .toList()

            assertTrue(vsFiles.isNotEmpty(), "Expected paired .vs files in ~/.local/share/isf")

            val linkFailures = mutableMapOf<String, String>()
            val linkSuccesses = mutableListOf<String>()

            for (vsFile in vsFiles) {
                val baseName = vsFile.nameWithoutExtension
                val fsFile = File(vsFile.parentFile, "$baseName.fs")
                if (!fsFile.exists()) {
                    linkFailures[vsFile.name] = "Matching fragment shader '$baseName.fs' not found"
                    continue
                }

                val fsSource = fsFile.readText()
                val header = ISFParser.parseHeader(fsSource) ?: continue
                val vsSource = vsFile.readText()

                val glslVert = ISFParser.buildGLSLVertexShader(vsSource, header)
                val glslFrag = ISFParser.buildGLSLFragmentShader(fsSource, header)

                val vId = glCreateShader(GL_VERTEX_SHADER)
                val fId = glCreateShader(GL_FRAGMENT_SHADER)
                val pId = glCreateProgram()

                glShaderSource(vId, glslVert)
                glCompileShader(vId)

                val vStatus = glGetShaderi(vId, GL_COMPILE_STATUS)
                val vLog = glGetShaderInfoLog(vId).trim()

                if (vStatus == GL_FALSE) {
                    linkFailures[vsFile.name] = "Vertex compilation failed:\n$vLog"
                    glDeleteShader(vId)
                    glDeleteShader(fId)
                    glDeleteProgram(pId)
                    continue
                }

                glShaderSource(fId, glslFrag)
                glCompileShader(fId)

                val fStatus = glGetShaderi(fId, GL_COMPILE_STATUS)
                val fLog = glGetShaderInfoLog(fId).trim()

                if (fStatus == GL_FALSE) {
                    linkFailures[vsFile.name] = "Fragment compilation failed:\n$fLog"
                    glDeleteShader(vId)
                    glDeleteShader(fId)
                    glDeleteProgram(pId)
                    continue
                }

                glAttachShader(pId, vId)
                glAttachShader(pId, fId)
                glLinkProgram(pId)

                val linkStatus = glGetProgrami(pId, GL_LINK_STATUS)
                val linkLog = glGetProgramInfoLog(pId).trim()

                if (linkStatus == GL_FALSE) {
                    linkFailures[vsFile.name] = "Program link failed:\n$linkLog"
                } else {
                    linkSuccesses.add(vsFile.name)
                }

                glDetachShader(pId, vId)
                glDetachShader(pId, fId)
                glDeleteShader(vId)
                glDeleteShader(fId)
                glDeleteProgram(pId)
            }

            println("Paired Vertex Shader Results: ${linkSuccesses.size} / ${vsFiles.size} linked cleanly.")
            if (linkFailures.isNotEmpty()) {
                println("Link failures:\n" + linkFailures.map { "${it.key}: ${it.value}" }.joinToString("\n\n"))
            }

            assertTrue(
                linkSuccesses.size >= 35,
                "Expected at least 35 / ${vsFiles.size} vertex shaders to link cleanly, got ${linkSuccesses.size} (${linkFailures.size} failed)"
            )
        } finally {
            glfwDestroyWindow(window)
            glfwTerminate()
        }
    }
}

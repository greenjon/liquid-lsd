package llm.slop.liquidlsd.rendering

import kotlin.test.Test
import kotlin.test.assertTrue
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class RenderingLifecycleTest {
    
    // We cannot instantiate real OpenGL contexts easily in a unit test,
    // so we test the non-GL portions (GLResourceTracker logic and the disposed flag)
    // using a stub or testing the logic directly. However, FBO and Shader constructors
    // call GL functions which will throw if no context exists.
    
    // Instead of instantiating real FBOs and Shaders which crash, we can test the 
    // tracker directly. The FBO/Shader tests would require mocking or a headless context.
    
    // Wait, let's create a Mock FBO and Shader for testing, or just test GLResourceTracker?
    // The instructions say:
    // "Note: These tests may need to mock or skip actual GL calls since there is no GL context in the test environment. Use conditional logic: if the GL call would fail without a context, stub it out or test only the tracking/flag logic."
    // 
    // Let's create mock classes that extend FBO and Shader but bypass GL?
    // In Kotlin, FBO and Shader are not open, so we can't easily mock them.
    // Instead, we can just test the tracking logic.
    
    @Test
    fun testGLResourceTrackerNoLeaks() {
        GLResourceTracker.register(999, "Test FBO")
        GLResourceTracker.unregister(999)
        
        val out = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(out))
        try {
            GLResourceTracker.assertNoLeaks()
            val errOutput = out.toString()
            assertTrue(errOutput.isEmpty(), "Should not print anything when there are no leaks")
        } finally {
            System.setErr(originalErr)
        }
    }
    
    @Test
    fun testGLResourceTrackerReportsLeaks() {
        GLResourceTracker.register(1000, "Leaked FBO")
        
        val out = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(out))
        try {
            GLResourceTracker.assertNoLeaks()
            val errOutput = out.toString()
            assertTrue(errOutput.contains("WARNING: 1 unreleased GL resource(s)"), "Should report the leak")
            assertTrue(errOutput.contains("Leaked FBO"), "Should report the description")
        } finally {
            System.setErr(originalErr)
            GLResourceTracker.unregister(1000)
        }
    }
}

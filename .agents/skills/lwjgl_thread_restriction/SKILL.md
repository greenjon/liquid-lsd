---
name: lwjgl_thread_restriction
description: Rule to keep all GLFW window polling and OpenGL context manipulation strictly on the primary thread (Thread 0) on Linux.
---

# LWJGL Thread Restriction on Linux

OpenGL context must live strictly on the main OS thread (Thread 0) on Linux. If background threads (such as audio threads) try to make OpenGL draw calls, it will trigger an immediate driver failure.

## Guidelines

1. **Main Thread Only**: Keep all GLFW window polling and OpenGL context manipulation strictly on the primary thread (Thread 0).
2. **Audio/Background Threads**: If audio processing (e.g. via JACK) or other tasks run on background threads, they must never interact directly with OpenGL or GLFW.
3. **Thread-Safe Data Exchange**: Pass audio spectrum data (or any other background data) from the JACK thread to the main thread via a thread-safe, lock-free ring buffer (e.g., `CvHistoryBuffer`) or volatile fields.

---

## Concrete Kotlin Examples

### ❌ Bad (Unsafe) Code Example
This example violates the Thread 0 rule by compiling shaders in a background thread pool and trying to set uniform variables directly from the JACK audio callback thread.

```kotlin
import org.lwjgl.opengl.GL20
import java.io.File
import java.util.concurrent.CompletableFuture

class UnsafeGlManipulator {
    // ❌ BAD: Background thread executing OpenGL compilation
    fun loadShaderAsync(file: File) {
        CompletableFuture.runAsync {
            val code = file.readText()
            val shaderId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER)
            GL20.glShaderSource(shaderId, code)
            GL20.glCompileShader(shaderId) // 💥 CRASH: No OpenGL context on this thread!
        }
    }

    // ❌ BAD: Audio thread calling OpenGL uniforms
    fun onAudioCallbackProcessed(value: Float) {
        // Assume this method is invoked from the JACK callback thread
        val uniformLoc = 1
        GL20.glUniform1f(uniformLoc, value) // 💥 CRASH: OpenGL calls from audio thread!
    }
}
```

###  Good (Safe) Code Example
This example respects the guidelines by queueing GPU creation tasks to a concurrent collection processed strictly on the main thread, and passing audio values via a volatile float scalar read on the main thread's render cycle.

```kotlin
import org.lwjgl.opengl.GL20
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class SafeGlManager {
    //  GOOD: Concurrent queue to buffer tasks for Thread 0 execution
    private val pendingGlTasks = ConcurrentLinkedQueue<Runnable>()
    
    //  GOOD: Volatile field for transferring parameters from background thread
    @Volatile
    private var lastAudioLevel = 0.0f

    // Called from any background IO thread
    fun requestShaderLoad(file: File) {
        val code = file.readText()
        pendingGlTasks.offer(Runnable {
            // This lambda will run on Thread 0, so GL operations are safe
            val shaderId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER)
            GL20.glShaderSource(shaderId, code)
            GL20.glCompileShader(shaderId)
            // ... store shaderId
        })
    }

    // Called from JACK audio processing thread
    fun receiveAudioSignal(level: Float) {
        lastAudioLevel = level // Safe non-blocking write to volatile field
    }

    // Called strictly on Thread 0 (OS Main Thread) inside the render loop
    fun renderFrame(uniformLoc: Int) {
        // 1. Process deferred OpenGL tasks
        var task = pendingGlTasks.poll()
        while (task != null) {
            task.run() // Safe GL operations
            task = pendingGlTasks.poll()
        }

        // 2. Read the level and apply uniform on Thread 0
        val level = lastAudioLevel // Safe read
        GL20.glUniform1f(uniformLoc, level) // Safe GL call
        
        // 3. Perform render calls
        GL20.glDrawArrays(GL20.GL_TRIANGLES, 0, 6)
    }
}
```

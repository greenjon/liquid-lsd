---
name: jack_callback_safety
description: Safety rules for the JACK audio processing callback running inside a real-time system thread.
---

# JACK Callback Safety

The JACK audio processing callback runs inside a real-time system thread. Since this is a high-performance, time-critical section, any delays or non-deterministic operations can cause audio dropouts (xruns) or crash the audio server.

## Guidelines

Inside the core audio processing callback loop:
1. **Never use blocking calls** (e.g., locks, file I/O, network requests).
2. **Never allocate memory** (e.g., calling `new` / allocating arrays, creating new objects/instances, using Kotlin lambdas that capture state, or instantiating collections). Pre-allocate all buffers and objects during initialization.
3. **Never use print or logging statements** (e.g., `println`, logger invocations, standard output/error stream writes).
4. **Treat it as a high-performance, time-critical section**.

---

## Concrete Kotlin Examples

### ❌ Bad (Unsafe) Code Example
This example violates multiple real-time constraints by allocating objects, executing blocking I/O, logging exceptions, and acquiring standard locks inside the callback.

```kotlin
import mu.KotlinLogging
import java.io.File
import java.nio.FloatBuffer

class UnsafeAudioCallback {
    private val logger = KotlinLogging.logger {}
    private val lock = Any()

    // Violates JACK Callback safety rules
    fun process(buffer: FloatBuffer, numFrames: Int) {
        // ❌ BAD: Object allocation (heap array instanced inside callback)
        val tempBuffer = FloatArray(numFrames) 
        buffer.get(tempBuffer)

        // ❌ BAD: Blocking synchronization (mutex lock blocks the thread)
        synchronized(lock) {
            // ❌ BAD: Writing to disk (I/O is blocking and highly non-deterministic)
            File("audio_log.txt").appendText("Processing block\n")
        }

        try {
            // ❌ BAD: Kotlin lambda allocation (capturing state triggers allocation)
            val processed = tempBuffer.map { it * 2.0f }
        } catch (e: Exception) {
            // ❌ BAD: Logging/printing blocks and allocates JVM memory
            logger.error(e) { "Failed processing audio!" }
        }
    }
}
```

###  Good (Safe) Code Example
This example respects all rules: it uses pre-allocated primitive arrays, lock-free status updates via volatile variables, and completely avoids heap allocations, locks, and logging.

```kotlin
import java.nio.FloatBuffer

class SafeAudioCallback {
    //  GOOD: Pre-allocated buffers at class initialization time
    private val preAllocatedBuffer = FloatArray(16384)
    
    //  GOOD: Volatile variables for lock-free data transfer to other threads
    @Volatile
    var currentRMS: Float = 0.0f
        private set

    fun process(buffer: FloatBuffer, numFrames: Int) {
        // Safety boundary: prevent array out of bounds without allocating
        if (numFrames > preAllocatedBuffer.size) return 
        
        //  GOOD: Zero-allocation primitive reads
        buffer.get(preAllocatedBuffer, 0, numFrames)
        
        var sumSquares = 0.0f
        //  GOOD: Simple primitive loop with zero heap allocation
        for (i in 0 until numFrames) {
            val sample = preAllocatedBuffer[i]
            sumSquares += sample * sample
        }
        
        val rms = Math.sqrt((sumSquares / numFrames).toDouble()).toFloat()
        
        //  GOOD: Safe, non-blocking volatile write to inform the UI/render threads
        currentRMS = rms
    }
}
```

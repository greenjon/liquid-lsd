# Project-Scoped Rules for Spirals Desktop

These rules guide the development of this Linux real-time graphics and audio project.

## 1. Real-Time and Low-Latency Constraints
- **Zero-Allocation Callback Thread**: Ensure no object allocations (`new`, Kotlin lambdas that capture state, standard library collections instantiation) or blocking operations (File I/O, JDBC, locks) occur within the JACK audio processing thread (e.g., inside `AudioEngine` callback).
- **Garbage Collection Optimization**: Always launch with ZGC (`-XX:+UseZGC`) and latency targets (`-XX:MaxGCPauseMillis=2`) to prevent audio dropouts (xruns) and frame stuttering.

## 2. OpenGL Context & Window Safety
- **Single-Threaded Windowing/GL**: All GLFW polling and OpenGL context manipulation must be executed strictly on the primary OS thread (Thread 0). Pass audio analysis results to the rendering thread using a thread-safe, lock-free ring buffer (e.g. `CvHistoryBuffer` or concurrent queues).
- **Debug context**: Keep `GLFW_OPENGL_DEBUG_CONTEXT` enabled in development so that any driver/GPU errors are reported immediately via the `GLDebug` callback.

## 3. External Tool Usage
- When diagnosing audio connections or debugging input ports, prefer using:
  - `jack_lsp` to list available ports
  - `jack_connect` to wire audio ports together
  - `pw-link` to establish connections in PipeWire environments

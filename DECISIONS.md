# Architectural Decisions - Spirals Desktop

This document outlines the key architectural decisions made in the development of Spirals Desktop, detailing the context, options considered, and the rationale behind each choice.

---

## 1. Tech Stack Selection (Kotlin/JVM + LWJGL + JNAJack)
- **Decision**: Build the VJ system using **Kotlin/JVM** on top of **LWJGL 3 (GLFW + OpenGL 3.3)** for graphics and **JNAJack** for Linux audio.
- **Rationale**: 
  - **Kotlin/JVM** provides excellent development velocity, strong typing, and rich ecosystem support (e.g. serialization, logging).
  - **LWJGL 3** gives us thin, high-performance bindings to native windowing (GLFW) and graphics (OpenGL), bypassing heavy Java 2D or JavaFX graphics layers.
  - **JNAJack** provides direct, low-latency access to JACK/PipeWire sound servers, which is crucial for real-time audio analysis.

---

## 2. Zero-Allocation & Non-Blocking Audio Callback
- **Decision**: Enforce a strict zero-allocation, non-blocking rule within the JACK audio processing thread (e.g. `AudioEngine` callback).
- **Rationale**: 
  - The JACK callback runs inside a real-time system thread managed by the host OS audio server. Any blockage (I/O, locks, logging) or non-deterministic CPU pause (JVM Garbage Collection allocation sweeps) can cause buffer underruns, resulting in audible stutters/dropouts (xruns) or server crashes.
  - All buffers, filter banks, and memory arrays used in audio analysis are pre-allocated during initialization.

---

## 3. Z Garbage Collector (ZGC) for Latency Control
- **Decision**: Mandate launching the JVM with ZGC (`-XX:+UseZGC`) and a low pause-time target (`-XX:MaxGCPauseMillis=2`).
- **Rationale**: 
  - The default G1 garbage collector can introduce pause times of 10-100ms. Since a typical low-latency audio buffer of 128 frames at 48kHz must process in **2.6ms**, a GC pause of even 3ms will guarantee an xrun.
  - ZGC performs concurrent GC phases, keeping JVM pauses under 1ms, safely below the real-time audio thread budget and visual frame budgets (16.6ms for 60Hz).

---

## 4. Single-Threaded Windowing and OpenGL (Thread 0)
- **Decision**: Bind the OpenGL context and all GLFW window operations strictly to the primary OS thread (Thread 0).
- **Rationale**: 
  - Operating system window managers (particularly macOS and Linux X11/Wayland) require window events and event polling to run on the thread that created the window.
  - Calling OpenGL functions from multiple threads or off-thread triggers driver faults and immediate native JVM crashes (segfaults).

---

## 5. Lock-Free Audio-to-Render Data Passing
- **Decision**: Avoid mutexes/locks for thread synchronization. Instead, pass data from the audio thread to the rendering thread using `@Volatile` fields, `AtomicReference<BeatAnchor>`, and the custom single-writer `CvHistoryBuffer` ring-buffer.
- **Rationale**: 
  - Locking on the audio thread can cause **priority inversion**, where a lower-priority rendering thread holding the lock blocks the real-time audio thread.
  - Lock-free structures keep the threads decoupled; transient data races in visualization buffers (like the oscilloscope) are acceptable, as they cause at most a single-frame visual glitch rather than an application-wide crash or xrun.

---

## 6. Explicit ImGui Native Memory Management
- **Decision**: Explicitly allocate and free native `imgui-java` structures (e.g. calling `.destroy()` on styles and font configs, and caching `ImString` buffers as class fields).
- **Rationale**: 
  - `imgui-java` is a JNI wrapper around a C++ immediate-mode library. Standard JVM GC does not track or clean up C++ heap allocations.
  - Allocating ImGui objects (like `ImString` or `ImInt`) per-frame on the heap results in severe native memory leaks. Failing to keep a JVM reference to font arrays while native ImGui references them triggers JVM SegFaults.

---

## 7. Platform-Specific Audio-Reactive CV Limits
- **Decision**: Active audio CV extraction is restricted to Linux (JACK/PipeWire). On macOS and Windows, the audio engine runs in a dummy mode, returning `0` for all CV signals, but allowing all rendering and MIDI maps to continue working normally.
- **Rationale**: 
  - Linux with JACK/PipeWire provides the standardized, low-latency API required for real-time visual synchronization.
  - Rather than embedding heavy cross-platform wrapper systems (which introduce overhead and instability), this approach isolates real-time audio requirements to Linux while retaining build/run portability for other platforms.

---

## 8. Decoupled Asynchronous Patch IO
- **Decision**: Execute all patch and preset saving/loading (`.lsd` JSON files) on a dedicated daemon background thread (`PatchManager-IO` executor) and pass the loaded DTOs to the main thread via thread-safe queues.
- **Rationale**: 
  - File I/O is slow and blocking. Saving or loading a patch on the main rendering thread would cause noticeable frame drops during a live VJ performance.
  - Loading on a background thread keeps rendering smooth, and using thread-safe queues ensures that applying loaded patches happens atomically at the start of the next render frame, preventing OpenGL state corruption.

---
name: lwjgl_thread_restriction
description: Rule to keep all GLFW window polling and OpenGL context manipulation strictly on the primary thread (Thread 0) on Linux.
---

# LWJGL Thread Restriction on Linux

OpenGL context must live strictly on the main OS thread (Thread 0) on Linux. If background threads (such as audio threads) try to make OpenGL draw calls, it will trigger an immediate driver failure.

## Guidelines

1. **Main Thread Only**: Keep all GLFW window polling and OpenGL context manipulation strictly on the primary thread (Thread 0).
2. **Audio/Background Threads**: If audio processing (e.g. via JACK) or other tasks run on background threads, they must never interact directly with OpenGL or GLFW.
3. **Thread-Safe Data Exchange**: Pass audio spectrum data (or any other background data) from the JACK thread to the main thread via a thread-safe, lock-free ring buffer.

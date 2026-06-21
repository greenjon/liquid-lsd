---
name: imgui_memory_management
description: Guidelines for managing native memory allocations in ImGui/JVM wrapper to prevent JVM SegFaults.
---

# ImGui Memory Management in Kotlin/JVM

ImGui in Kotlin/JVM relies on native bindings (usually via LWJGL or SharedLib). If the agent creates UI elements inside a loop without manually tracking native memory pointers, the JVM will abruptly crash with a SegFault.

## Guidelines

1. **Explicit Memory Allocation & Freeing**: Every time you write or modify an ImGui UI layer, you must explicitly allocate and free native memory structures using LWJGL's `MemoryStack` or resource scopes.
2. **No Leaks**: Never let native pointers leak outside the rendering loop. Always free allocations properly.
3. **Usage of MemoryStack**:
   ```kotlin
   MemoryStack.stackPush().use { stack ->
       // Allocate native memory using stack
       // ...
       // Auto-released when exiting the block
   }
   ```

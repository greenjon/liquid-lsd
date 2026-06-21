---
name: jack_callback_safety
description: Safety rules for the JACK audio processing callback running inside a real-time system thread.
---

# JACK Callback Safety

The JACK audio processing callback runs inside a real-time system thread. Since this is a high-performance, time-critical section, any delays or non-deterministic operations can cause audio dropouts (xruns) or crash the audio server.

## Guidelines

Inside the core audio processing callback loop:
1. **Never use blocking calls** (e.g., locks, file I/O, network requests).
2. **Never allocate memory** (e.g., calling `new`, creating new objects/instances). Pre-allocate all buffers and objects during initialization.
3. **Never use print or logging statements** (e.g., `println`, `log.info`, standard output/error stream writes).
4. **Treat it as a high-performance, time-critical section**.

# Architecture Overview

This section covers the high-level system design, threading boundaries, and safety models of Liquid LSD.

## High-Level Video Pipeline

Liquid LSD processes real-time audio and generates framebuffers through sequential stages:

```
JACK / Java Sound ──► AudioEngine ──► CVRegistry
                                    │  (every frame: updateAll)
                          ┌─────────┴──────────────┐
                       Deck A                   Deck B
                    (VisualSource)           (VisualSource)
                          │                       │
               ModulatableParams         ModulatableParams
               evaluated via CV          evaluated via CV
                          │                       │
                     cleanFBO                cleanFBO
                          │                       │
                    feedback.frag           feedback.frag
                    (ping-pong FBOs)        (ping-pong FBOs)
                          └─────────┬──────────────┘
                                Mixer.kt
                              mixer.frag
                                  │
                             masterFBO ──► Deck C (compositing) ──► screen
```

---

## Threading Boundaries

Liquid LSD runs on two primary threads: the **OS Main Thread** and the **Audio Thread** (JACK callback thread or Java Sound capture thread). Maintaining strict separation between them is critical for real-time safety.

### Thread 0 (OS Main / Rendering Thread)
- **Duties**: GLFW event polling, OpenGL context creation, OpenGL draw calls (rendering to Decks, Mixer, and Screen), ImGui interface drawing.
- **Rules**: All LWJGL 3 GLFW window and OpenGL context manipulations must run on Thread 0.

### Audio Thread (JACK Callback or Java Sound Loop)
- **Duties**: Periodically receives raw audio buffer chunks (via JACK callback or Java Sound capture thread), runs bandpass filtering, FFT calculations, RMS amplitude extraction, and onset detection.
- **Rules**: Must be *completely non-blocking* and *zero-allocation*.

---

## Concurrency Safety & Lock-Free Data Passing

Since the audio thread operates under strict real-time constraints and the rendering thread runs at screen refresh rates (60Hz+), lock-based synchronization between them could lead to audio glitches (xruns) or frame stuttering.

### Lock-Free Buffers
- **`AtomicReference<BeatAnchor>`**: The primary mechanism for passing beat clock state (beats, BPM,
  timestamp) from the audio thread to the render thread. The audio thread/loop writes a new `BeatAnchor`
  value object atomically; the render thread reads it without any lock.
- **`CvHistoryBuffer`**: A pre-allocated ring buffer used to pass historical CV values from the audio
  engine to the main thread for drawing oscilloscopes and UI monitoring.
- **`@Volatile` scalars**: Used for simple single-value flags like `isBpmLocked`, `inputGain`, and
  `currentState` where atomic read/write is sufficient.
- **Concurrent Collections**: Thread-safe structures like `ConcurrentLinkedQueue` are used to queue
  incoming MIDI CC events from device listeners before they are polled on the render loop.

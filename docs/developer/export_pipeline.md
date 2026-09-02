# Media Export & Recording Pipeline

This document details the architecture and low-level mechanics of Liquid LSD's media export systems: real-time live MP4 recording and offline deterministic 4K/60fps video rendering.

---

## 1. Real-Time Recording Pipeline (`RealtimeRecorder.kt`)

The primary constraint of real-time video capture in a live performance application is **zero rendering stalls**: invoking synchronous `glReadPixels` on the primary render thread forces the CPU to wait for all queued GPU draw calls to complete, causing severe frame drops (stutter) and audio callback buffer underruns (xruns).

To overcome this, `RealtimeRecorder` utilizes a multi-threaded, asynchronous ring-buffered architecture:

```
┌────────────────────────────────────────────────────────────────────────┐
│ PRIMARY RENDER THREAD (Thread 0)                                       │
│                                                                        │
│  Draw Frame ──► Readback Trigger (glReadPixels into PBO Ping)          │
│                      │                                                 │
│                      ▼                                                 │
│  Next Frame ──► Map & Copy (glMapBufferRange from PBO Pong)            │
│                      │ (Native memcpy to direct ByteBuffer)            │
└──────────────────────┼─────────────────────────────────────────────────┘
                       │ Non-blocking ArrayBlockingQueue.offer()
                       ▼
┌────────────────────────────────────────────────────────────────────────┐
│ VIDEO WORKER THREAD ("RealtimeRecorder-VideoWorker")                   │
│                                                                        │
│  ArrayBlockingQueue.poll() ──► Write RGBA bytes to FFmpeg stdin pipe  │
│                                (Recycle ByteBuffer to pool)            │
└────────────────────────────────────────────────────────────────────────┘
```

### Asynchronous PBO Readback (`PboReadbackPipeline.kt`)

1. **Dual PBO Ring Buffer**: Two Pixel Buffer Objects (`GL_PIXEL_PACK_BUFFER`) alternate roles each frame:
   - **Ping PBO**: Bound as the target for `glReadPixels`. The GPU initiates DMA transfer from framebuffer memory into PBO storage asynchronously in the background.
   - **Pong PBO**: Contains the completed pixel transfer from the previous frame. Mapped via `glMapBufferRange(GL_READ_ONLY)` with zero CPU-GPU synchronization stalls.
2. **Pre-Allocated ByteBuffers**: A pool of direct `ByteBuffer` instances allocated via `MemoryUtil.memAlloc` stores raw uncompressed RGBA pixel buffers.
3. **Queue Throttling**: A bounded `ArrayBlockingQueue` (capacity: 10 frames) absorbs short-term disk write spikes. If the disk subsystem falls behind and the queue fills up, frames are intentionally dropped with a telemetry counter increment (`droppedFramesCount`) rather than blocking the render loop.

### Lock-Free Audio Tapping

Audio recording runs concurrently with zero allocations:
- Inside the real-time audio callback (`AudioEngine`), an audio block is offered to a lock-free Single-Producer Single-Consumer (SPSC) ring buffer of pre-allocated `FloatArray` buffers (8192 samples).
- An asynchronous worker thread (`RealtimeRecorder-AudioWorker`) polls the SPSC queue and writes raw 32-bit float PCM data to a temporary audio scratch file.
- When recording stops, FFmpeg finalizes and multiplexes the video stream and raw PCM audio into the destination MP4 container.

---

## 2. Offline Render Studio (`OfflineRenderStudio.kt`)

Offline rendering decouples rendering from physical wall-clock time. It runs a deterministic simulation where every video frame advances the internal timeline and audio DSP state by an exact fractional step ($\Delta t = 1.0 / \text{FPS}$).

```
┌──────────────────────────────────────────────────────────────┐
│ Deterministic Offline Simulation Loop                        │
│                                                              │
│  1. Advance Virtual Time: t += 1.0 / FPS                     │
│  2. Step Audio Engine: Feed exact N samples from DecodedAudio│
│  3. For each Sub-Frame slice (Motion Blur):                  │
│     - Compute intermediate CV modulators                     │
│     - Render Deck A & Deck B into Clean FBOs                 │
│     - Execute Ping-Pong Feedback & Mixer Passes              │
│     - Accumulate slice in AccumulationBuffer (HDR)           │
│  4. Normalize & Tone-Map Accumulation Buffer                 │
│  5. Async PBO Readback ──► Stream to FFmpeg stdin            │
└──────────────────────────────────────────────────────────────┘
```

### Deterministic Virtual Clock & DSP

- **`DecodedAudio`**: Upfront decoding of `.wav`, `.flac`, `.mp3`, or `.ogg` files into mono and stereo floating-point arrays via Java Sound or FFmpeg pipe.
- **Sample-Accurate Stepping**: Exactly `(sampleRate / FPS)` audio samples are fed into the `AudioEngine` DSP pipeline per frame. Biquad filters, energy RMS followers, and beat tracking flywheels update deterministically regardless of CPU speed.

### Sub-Frame Accumulation Motion Blur (`AccumulationBuffer.kt`)

Fast-moving geometry (e.g. ribbon rotations at 120 RPM or high-frequency feedback zoom) can appear strobed or stepped at standard 60 FPS.

- **Temporal Oversampling**: Slices each frame into $S$ sub-steps ($S \in \{2, 4, 8, 16\}$).
- **Weighting Curves**: Sub-frame slices are blended into a floating-point `RGBA16F` / `RGBA32F` accumulation texture using uniform or exposure-weighted Gaussian shutter curves.
- **Shutter Angle**: Simulates physical camera shutter speeds (e.g., $180^\circ = 1/120\text{s}$ shutter at 60 FPS).

---

## 3. Subprocess FFmpeg Pipe (`FFmpegProcessPipe.kt`)

Video frames are encoded on-the-fly by spawning FFmpeg as an asynchronous child process and streaming raw RGBA byte arrays directly to its `stdin` file descriptor:

```bash
ffmpeg -y -f rawvideo -pix_fmt rgba -s:v 3840x2160 -r 60 -i - \
       -i /tmp/audio_scratch.pcm -c:v libx264 -crf 17 -preset veryfast \
       -pix_fmt yuv420p -c:a aac -b:a 320k output.mp4
```

- **Hardware Acceleration Autodetection**: Automatically detects and selects hardware encoders (`h264_nvenc` for NVIDIA, `h264_qsv` for Intel, `h264_vaapi` for Linux/AMD, `h264_videotoolbox` for Apple Silicon) with transparent fallback to `libx264`.
- **Pipe Safety**: Ensures `stdin.flush()` and `Process.destroy()` lifecycle hygiene to avoid zombie processes or locked file handles.

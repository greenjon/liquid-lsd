# Operations & Tuning

This guide covers performance optimization, JVM tuning, diagnostic commands, and audio/graphics troubleshooting for different target operating systems.

## JVM Tuning for Low-Latency Performance

To ensure real-time rendering and low-latency audio processing without drops or frame stutters, the Java Virtual Machine must be tuned carefully.

> [!NOTE]
> **JACK-Only Caveat**: Low-latency JVM/GC tuning (ZGC) and real-time priorities are strictly critical on Linux when using the active JACK/PipeWire audio engine to prevent xruns (audio dropouts). On macOS and Windows, where the audio engine is disabled (dummy mode) and audio CVs stay at 0, these low-latency tuning flags are optional, though ZGC is still recommended to maintain smooth, stutter-free visual rendering.

### Z Garbage Collector (ZGC)
Use the Z Garbage Collector, which is designed for low-latency workloads with pause times under a millisecond.
Enable it using the following JVM flags:
```bash
-XX:+UseZGC -XX:MaxGCPauseMillis=2
```

### Platform-Specific Optimization Flags

#### Linux (x86_64 & arm64)
If you are running with the active JACK audio engine, ensure your user has real-time priority access configured in `/etc/security/limits.d/audio.conf` to prevent thread preemption:
```text
@audio   -   rtprio   95
@audio   -   memlock  unlimited
```
Run with memory locking to prevent JVM heap swapping:
```bash
java -XX:+UseZGC -XX:MaxGCPauseMillis=2 -jar lsd-all.jar
```

#### macOS (Intel & Apple Silicon)
Ensure you use native libraries compiled for the specific architecture (e.g. native Apple Silicon libs vs. Intel x86_64 libs) to avoid Rosetta 2 translation overhead. ZGC can be used to ensure smooth UI/graphics rendering.

#### Windows
Ensure the GPU is set to "High Performance" mode in the Windows Graphics Settings for `java.exe` to ensure smooth visual rendering. (Note: Since real-time audio is disabled on Windows, Windows audio latency and driver mapping settings are not applicable.)

---

## Audio Connectivity Diagnostics (Linux CLI)

> [!WARNING]
> **JACK-Only / Linux-Only**: These diagnostic commands and tools are only applicable to Linux environments running JACK or PipeWire. On other platforms, the audio engine is deactivated and runs in a dummy mode, so no audio ports are created.

When debugging input ports and routing on Linux, use the following tools:

### `jack_lsp`
Lists all active JACK/PipeWire ports:
```bash
jack_lsp
```
Use `jack_lsp -c` to see active connection links between ports.

### `jack_connect`
Manually wire ports together (e.g., routing system capture to spirals):
```bash
jack_connect system:capture_1 lsd:input_1
jack_connect system:capture_2 lsd:input_2
```

### `pw-link`
Use `pw-link` in PipeWire environments to inspect and establish links:
```bash
pw-link -l                       # List links
pw-link system:capture_1 lsd:input_1   # Establish connection
```

---

## Troubleshooting Guide

### 1. JVM Crash on Startup (LWJGL/OpenGL)
- **Symptoms**: The application crashes immediately upon start with a native log file (`hs_err_pid.log`).
- **Causes**:
  - OpenGL 3.3 context could not be created (unsupported driver/integrated GPU).
  - Calling OpenGL functions or polling GLFW windows off Thread 0 (violating LWJGL single-thread safety).
- **Solutions**:
  - Verify your graphics drivers are up to date.
  - Review the OpenGL logs. In development, check the stdout for lines starting with `GLDebug`.

### 2. Audio Dropouts (xruns)
> [!NOTE]
> **JACK-Only / Linux-Only**: Audio dropouts/xruns troubleshooting only applies to Linux environments.

- **Symptoms**: Stuttering audio, click sounds, or error logs showing `xrun` in the terminal.
- **Solutions**:
  - Increase your JACK/PipeWire buffer size (e.g. from 128 to 256 or 512 frames) or lower the sample rate (from 96kHz to 48kHz).
  - Ensure ZGC is active and check the GC logs to confirm pause times are not exceeding the audio buffer period.

### 3. MIDI CC Input Not Registering
- **Symptoms**: Rotating knobs does not trigger MIDI Learn or update CV values.
- **Solutions**:
  - Confirm the MIDI device is plugged in and recognized by the OS (`lsusb` or MIDI Control Panel).
  - Make sure another app does not have exclusive lock access to the MIDI device.
  - Check the startup logs to ensure `MidiEngine` output lists the connected device.

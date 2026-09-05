# Operations & Performance Tuning

This guide covers JVM Garbage Collector tuning, real-time audio configuration across operating systems, CLI diagnostics, and troubleshooting procedures for Liquid LSD.

---

## Low-Latency JVM Tuning (ZGC)

To maintain 60+ FPS visual rendering and sub-millisecond audio analysis without frame stuttering or audio dropouts (xruns), Liquid LSD should be launched with the Z Garbage Collector (ZGC):

```bash
# Linux / Windows:
java -XX:+UseZGC -XX:MaxGCPauseMillis=2 --enable-native-access=ALL-UNNAMED -jar build/libs/liquid-lsd-desktop-1.0-SNAPSHOT-all.jar

# macOS (requires -XstartOnFirstThread):
java -XstartOnFirstThread -XX:+UseZGC -XX:MaxGCPauseMillis=2 --enable-native-access=ALL-UNNAMED -jar build/libs/liquid-lsd-desktop-1.0-SNAPSHOT-all.jar
```

### Key Flags Explained
- `-XX:+UseZGC`: Enables the Z Garbage Collector, designed for concurrent execution with pause times typically below $1\text{ ms}$.
- `-XX:MaxGCPauseMillis=2`: Advises the JVM GC scheduler to keep pause times under 2 milliseconds, well within 60 FPS frame boundaries ($16.6\text{ ms}$).
- `-XstartOnFirstThread` (macOS only): Forces the JVM to spawn the application on the initial OS process thread (Thread 0), which is mandatory for Cocoa event polling and GLFW initialization.
- `--enable-native-access=ALL-UNNAMED`: Authorizes LWJGL/Unsafe native method access without JVM restricted method warnings on JDK 21+.

---

## Operating System Setup & Audio Drivers

### Linux (x86_64 & ARM64)
- **Audio Engines**: Supports native PipeWire-JACK and JACK2 servers via `JackClient.kt`, or Java Sound system capture (`JavaSoundClient.kt`).
- **Real-Time Permissions**: Configure `/etc/security/limits.d/audio.conf` to grant real-time priority:
  ```text
  @audio   -   rtprio   95
  @audio   -   memlock  unlimited
  ```

### macOS (Intel & Apple Silicon)
- **Audio Engine**: Runs out-of-the-box using `JavaSoundClient.kt` capturing from standard input devices (microphone, line-in, or virtual audio cables like BlackHole/Loopback). Also supports optional JACK2 for macOS.
- **Apple Silicon Native**: Always run native ARM64 JVM builds (`zulu17-ca-jdk17.x` or bundled Adoptium ARM64) to prevent Rosetta 2 translation overhead.
- **Main Thread Event Loop**: GLFW and Cocoa windowing require `-XstartOnFirstThread`. Launching via the provided `run-mac-arm.command` or `run-mac-intel.command` script sets this automatically and detects the bundled JRE in `jre/macos-<arch>/Contents/Home/bin/java`.

### Windows (x64)
- **Audio Engine**: Runs out-of-the-box via `JavaSoundClient.kt` system audio capture. Supports optional JACK2 for Windows with ASIO drivers.
- **GPU Performance**: Ensure `java.exe` is assigned to "High Performance GPU" in Windows Graphics Settings.

---

## Audio Connectivity Diagnostics (Linux CLI)

When diagnosing input port connections under PipeWire/JACK on Linux:

### `jack_lsp`
Lists active JACK ports and connections:
```bash
jack_lsp
jack_lsp -c    # Display active link routing
```

### `jack_connect`
Manually routes audio from system capture to Liquid LSD:
```bash
jack_connect system:capture_1 lsd:input_1
jack_connect system:capture_2 lsd:input_2
```

### `pw-link` (PipeWire Native)
```bash
pw-link -l                          # List active PipeWire links
pw-link system:capture_1 lsd:input_1 # Establish link
```

---

## Troubleshooting Guide

### 1. Startup Graphics Crash (LWJGL / OpenGL)
- **Symptoms**: Immediate crash with `hs_err_pid.log`.
- **Causes**:
  - System GPU does not support OpenGL 3.3+.
  - GLFW/OpenGL calls executed off Thread 0.
- **Resolution**:
  - Update graphics card drivers.
  - Launch with standard sandbox rules and inspect stdout for `GLDebug` driver error logs.

### 2. Audio Dropouts (xruns)
- **Symptoms**: Audio stuttering or console logs displaying `xrun`.
- **Resolution**:
  - Increase JACK buffer size (from 128 to 256 or 512 frames).
  - Verify ZGC is active (`-XX:+UseZGC -XX:MaxGCPauseMillis=2`).

### 3. MIDI Controller Input Not Detected
- **Symptoms**: Moving knobs does not register in MIDI Learn mode.
- **Resolution**:
  - Confirm device USB connection using `lsusb` (Linux) or Device Manager (Windows).
  - Verify another DAW application does not have exclusive lock access to the MIDI device.
  - Review startup console logs for `MidiEngine` device initialization messages.

# Getting Started

This guide walks you through system prerequisites, compilation, packaging, and the initial launch steps for Liquid LSD across supported operating systems.

---

## System Prerequisites

Liquid LSD is built on Kotlin/JVM and OpenGL 3.3. It runs natively on Linux (x86_64 & ARM64), macOS (Intel & Apple Silicon), and Windows (x64).

### Java Development Kit (JDK)
- **JDK 17 or higher** is required on all platforms.
  - **Linux**: Install via package manager (Ubuntu/Debian: `sudo apt install openjdk-17-jdk`, Fedora: `sudo dnf install java-17-openjdk-devel`).
  - **macOS**: Recommended Azul Zulu JDK via Homebrew (`brew install --cask zulu17`).
  - **Windows**: Install Azul Zulu or Eclipse Temurin JDK 17+ installer.

### Audio Architecture
- **Linux (Recommended)**: PipeWire with `pipewire-jack` or native JACK2 server for ultra-low latency audio analysis and inter-app routing.
- **Cross-Platform Fallback (macOS, Windows, JACK-less Linux)**: Works out-of-the-box using system audio capture via `JavaSoundClient`. No external audio daemons or complex routing required!

---

## Build & Run Instructions

Liquid LSD uses Gradle as its build system with a bundled wrapper (`gradlew` or `gradlew.bat`).

### Compiling Source Code
To check for syntax and type-check Kotlin sources without launching the GUI:
```bash
./gradlew compileKotlin
```

### Running in Development Mode
Launch the workstation directly from source:
```bash
./gradlew run
```
*(On Windows: `.\gradlew.bat run`). If Gradle daemon socket warnings occur, add `--no-daemon`.*

### Packaging Standalone Fat JAR
To package the executable JAR with all cross-platform native library binaries (LWJGL OpenGL/GLFW, JNAJack, ImGui wrappers):
```bash
./gradlew shadowJar
```
The resulting fat JAR is output to:
```
build/libs/liquid-lsd-desktop-1.0-SNAPSHOT-all.jar
```

Launch the packaged JAR with low-latency ZGC flags:
```bash
# Linux / Windows:
java -XX:+UseZGC -XX:MaxGCPauseMillis=2 -jar build/libs/liquid-lsd-desktop-1.0-SNAPSHOT-all.jar

# macOS (requires -XstartOnFirstThread for GLFW Cocoa main thread event loop):
java -XstartOnFirstThread -XX:+UseZGC -XX:MaxGCPauseMillis=2 -jar build/libs/liquid-lsd-desktop-1.0-SNAPSHOT-all.jar
```

---

## First Launch Walkthrough

1. **Launch Liquid LSD**: Run `./gradlew run`.
2. **Verify Interface**: A window titled **Liquid LSD - Libre Shader Decks** will open showing real-time generative visuals and a three-column interface:
   - **Left Panel**: Preset Grid (CV modulation matrix).
   - **Middle Panel**: Cell Config (parameter editor, LFO controls & oscilloscope).
   - **Right Panel**: Mixer / Master Output Monitor.
3. **Check Audio Input**:
   - On **Linux with PipeWire/JACK**: Route audio from your media player or hardware input to `lsd:input_1` / `lsd:input_2` using Helvum, qjackctl, or `jack_connect`.
   - On **macOS / Windows / Standalone Linux**: Liquid LSD automatically captures from your system default audio input device using Java Sound.
4. **Observe Modulation**: As audio plays, the `AUDIO` (`AMP`, `BASS`, `MID`, `HIGH`) and `TRIGGER` columns in the Preset Grid will animate dynamically.
5. **Explore Documentation & Notes**:
   - Hover over parameter labels to view built-in engine descriptions and live value breakdowns.
   - Press **`F3`** to switch between Performance Mode and Asset Management Mode.

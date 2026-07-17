# Getting Started

This guide walks you through the system prerequisites, compilation, packaging, and the initial launch steps for Liquid LSD on various operating systems.

## Prerequisites

To run Liquid LSD, you will need a Java Development Kit (JDK) version 17 or higher, and a running audio connection server (JACK or PipeWire-JACK).

### Linux (x86_64 & arm64)
- **JDK**: Java 17+ (Ubuntu/Debian: `sudo apt install openjdk-17-jdk`, Fedora: `sudo dnf install java-17-openjdk-devel`).
- **Audio Server**: JACK2 or PipeWire with `pipewire-jack` compatibility layer installed.
- **ALSA & PortAudio**: Usually standard in modern Linux distributions.

### macOS (Intel & Apple Silicon)
- **JDK**: Java 17+ (Recommended: Azul Zulu JDK for Apple Silicon or Intel via Homebrew: `brew install --cask zulu17`).
- **Audio Server**: JACK2 for macOS (installable via Homebrew or official installer) or virtual soundcard systems.

### Windows (x64)
- **JDK**: Java 17+ (Recommended: Azul Zulu JDK).
- **Audio Server**: JACK2 for Windows (requires installing the JACK2 Windows installer and configuring a driver like ASIO4ALL).

---

## Build & Run Instructions

Liquid LSD uses Gradle as its build system. The project directory includes a Gradle wrapper (`gradlew`).

### Compiling the Code
To check for syntax and compile the Kotlin source code without launching the window:
```bash
./gradlew compileKotlin
```

### Running in Development Mode
To build and launch the application directly from the source tree:
```bash
./gradlew run
```
*Note: Make sure your JACK audio server is running before executing this command to enable audio-reactive CVs.*

### Packaging a Standalone Executable (Fat JAR)
To package the application and all platform-specific native library dependencies (LWJGL, JNAJack, ImGui wrappers) into a single executable JAR file:
```bash
./gradlew shadowJar
```
The output artifact will be generated at:
`build/libs/lsd-all.jar`

Run the packaged JAR with:
```bash
java -XX:+UseZGC -XX:MaxGCPauseMillis=2 -jar build/libs/lsd-all.jar
```

---

## First Launch Walkthrough

1. **Start the Audio Server**: Launch PipeWire/JACK (e.g., using `qjackctl` or starting the system audio service).
2. **Launch Liquid LSD**: Run `./gradlew run`.
3. **Verify Window initialization**: A window with the title **Liquid LSD - VJ Software** should open, showing a live generative mandala and a three-column ImGui setup:
   - **Left Panel**: Patch Grid (modulation matrix).
   - **Middle Panel**: Cell Config (parameters editor & oscilloscope).
   - **Right Panel**: Mixer / Output Monitor.
4. **Connect Audio Ports**:
   - The application registers input ports with the JACK server.
   - Route audio from your system hardware inputs or media player to the `spirals` input ports using a connection manager like Patchage, Helvum, or the CLI commands in the tuning guide.
5. **Observe Reactions**: If audio is playing and routed correctly, the `AMP`, `BASS`, `MID`, and `HIGH` columns in the Patch Grid will animate, indicating active CV modulation.

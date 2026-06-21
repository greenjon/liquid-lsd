# Spirals Desktop

Open source audio-reactive visual performance software for VJs.

## Status
🚧 Early development - Project structure established

## Tech Stack
- Kotlin/JVM
- LWJGL 3 (OpenGL)
- ImGui (UI)
- JACK Audio

## Roadmap & Active Tasks
- See `ROADMAP.md` for long-term project planning and completed milestones.
- See `TODO.md` for the list of active tasks and features currently under development.

## Building

```bash
./gradlew build
```

## Running

```bash
./gradlew run
```

## Packaging (Fat JAR)

To package the application and all its native library dependencies into a single distribution executable:

```bash
./gradlew shadowJar
```
The output will be generated under `build/libs/spirals-desktop-1.0-SNAPSHOT-all.jar`.

## Real-Time Performance & JVM Tuning

For low-latency performance without audio glitches (xruns):
- The application uses **ZGC** (`-XX:+UseZGC`) and target pause times of 2ms (`-XX:MaxGCPauseMillis=2`) to keep Garbage Collection stutters minimal.
- An OpenGL debug callback is registered via `GLDebug.setupDebugCallback()` in development, logging driver warnings/errors automatically.

## Audio Connection & Debugging (Linux)

You can manage the JACK audio connections using command line tools:
- `jack_lsp` - Lists active JACK audio/MIDI ports.
- `jack_connect <source> <destination>` - Establishes a patch connection.
- `pw-link` - Connection utility for PipeWire-JACK environments.

## License
MIT

---

## **Your Project Structure Should Look Like:**

```
spirals-desktop/
├── .gitignore
├── LICENSE
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   └── wrapper/
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── llm/slop/spirals/
│       │       ├── Main.kt
│       │       ├── audio/
│       │       ├── cv/
│       │       ├── midi/
│       │       ├── parameters/
│       │       ├── rendering/
│       │       ├── ui/
│       │       └── patches/
│       └── resources/
│           ├── shaders/
│           │   ├── blit.vert
│           │   └── blit.frag
│           ├── patches/
│           │   └── default.json
│           └── logback.xml
└── build/
```

---

## **Now Test Everything!**

```bash
# In VS Code terminal:
./gradlew run

# Project-Scoped Rules for Liquid LSD Desktop

These rules guide the development of this cross-platform (Linux x64/ARM64, macOS x64/ARM64, Windows x64)
real-time graphics and audio project.

## 1. Real-Time and Low-Latency Constraints (Linux / JACK only)
- **Zero-Allocation Callback Thread**: Ensure no object allocations (`new`, Kotlin lambdas that capture state, standard library collections instantiation) or blocking operations (File I/O, JDBC, locks) occur within the JACK audio processing thread (e.g., inside `AudioEngine` callback). JACK audio is only active on Linux; other platforms skip this code path.
- **Garbage Collection Optimization**: Always launch with ZGC (`-XX:+UseZGC`) and latency targets (`-XX:MaxGCPauseMillis=2`) to prevent audio dropouts (xruns) and frame stuttering.

## 2. OpenGL Context & Window Safety
- **Single-Threaded Windowing/GL**: All GLFW polling and OpenGL context manipulation must be executed strictly on the primary OS thread (Thread 0). Pass audio analysis results to the rendering thread using a thread-safe, lock-free ring buffer (e.g. `CvHistoryBuffer` or concurrent queues).
- **Debug context**: Keep `GLFW_OPENGL_DEBUG_CONTEXT` enabled in development so that any driver/GPU errors are reported immediately via the `GLDebug` callback.

## 3. External Tool Usage (Linux / JACK environments only)
- When diagnosing audio connections or debugging input ports, prefer using:
  - `jack_lsp` to list available ports
  - `jack_connect` to wire audio ports together
  - `pw-link` to establish connections in PipeWire environments

## 4. Continuous Documentation Maintenance ("Doc-as-you-Code")
- **Atomic Doc Updates**: Whenever modifying or refactoring code, features, preset schemas, audio engines, UI controls, or build settings, you MUST update the corresponding documentation files in `docs/`, `README.md`, `ARCHITECTURE.md`, or `DECISIONS.md` within the same turn/commit.
- **Documentation Mapping**:
  - Audio engine (`src/.../audio/`) → `docs/developer/` and `ARCHITECTURE.md`
  - Rendering / Shaders / UI (`src/.../render/`, `src/.../ui/`) → `docs/user_guide/` and `docs/developer/`
  - Presets & Configs (`presets/`, parameters) → `docs/user_guide/`
  - Architectural / System decisions → `DECISIONS.md` and `ARCHITECTURE.md`
- **Release Notes & Logs**: Whenever completing feature additions or bug fixes, update `RELEASE_NOTES.md` or `docs/release_notes.md`.

## 5. UI Typography & Character Set Rules
- **Font Stack**: The UI uses **Inter** (regular/medium/bold) for text, **JetBrains Mono** for code, and **Lucide** (`Icons.kt`) for icons.
- **Avoid Multi-Byte Emojis in ImGui Strings**: Do NOT insert raw multi-byte Emojis (e.g. 📁, 🎨, 📋, 📝, 💾, ⚠) into ImGui text strings or UI labels. They do not exist in standard TTF fonts or font atlas textures and will render as missing `?` glyphs.
- **Use `Icons.*` Constants or Standard Symbols**: Always use `Icons.<SYMBOL>` (e.g., `Icons.FOLDER`, `Icons.FILE`, `Icons.SETTINGS`, `Icons.ALERT`) for UI icons, or standard ASCII and basic Unicode symbols (e.g., `±`, `→`, `—`, `•`) which are baked into the `UITheme` glyph atlas.



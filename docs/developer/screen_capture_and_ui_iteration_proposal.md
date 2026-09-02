# Concept Proposal: Automated Screen Capture, UI Testing & Responsive Lab

**Status**: Draft / RFC  
**Target Area**: `Main.kt`, `ui/UIManager.kt`, `export/PboReadbackPipeline.kt`, `build.gradle.kts`  
**Origins**: Adapted from PR #22 concept by Lawrence Norton (`@lnorton89`)  

---

## 1. Motivation & Value Proposition

As Liquid LSD matures with complex modular UI panels (Preset Grid, Modular Cell Config accordions, Library drawer, Live Deck Monitors, and Custom CSD Titlebar), visual regression testing and up-to-date documentation imagery become essential.

### Core Objectives
1. **Automated Documentation & Web Assets**: Generate crisp, deterministic UI screenshots for user guides (`docs/user_guide/`) and web broadcasting pages on demand or during release builds.
2. **Visual Regression & Layout Auditing**: Verify that UI layouts, column widths, text clipping, and responsive breakpoints remain functional across different window resolutions (`1920x1080`, `1440x900`, `1280x720`, `2560x1440`, `maximized`) without manual testing.
3. **Isolated UI Lab Component Gallery**: Provide an isolated sandbox environment to preview themes, custom icons, sliders, meters, and modal popups without requiring active audio hardware or heavyweight GLSL shader compilation.

---

## 2. Proposed CLI Flags & Launch Options

To support automated capture in CI or local developer workflows, `Main.kt` would parse optional startup arguments:

```bash
# Capture full app screenshot at 1080p after 5 frames and exit cleanly
./gradlew run --args="--screenshot-ui=docs/assets/main-1080p.png --window=1920x1080 --screenshot-after-frames=5 --no-audio"

# Launch isolated UI Lab gallery in compact window
./gradlew run --args="--ui-lab --window=1280x720 --no-audio"
```

| Flag | Argument | Description |
|---|---|---|
| `--screenshot-ui` | `<filepath.png>` | Path where the captured PNG framebuffer should be saved. Liquid LSD automatically triggers exit after capturing. |
| `--screenshot-after-frames` | `<int>` *(default: 5)* | Number of frames to render before capturing, ensuring ImGui font atlases, layout metrics, and dynamic splitters have fully settled. |
| `--window` | `<W>x<H>` or `maximized` | Overrides saved window dimensions for deterministic capture resolutions. |
| `--no-audio` | *(flag)* | Bypasses JACK and JavaSound audio initialization to allow execution on headless CI nodes or systems without sound cards. |
| `--ui-lab` | *(flag)* | Renders an isolated UI component gallery (icons, swatches, custom widgets, meters) instead of the main performance mixer. |

---

## 3. Technical Architecture & Implementation Details

```
                    ┌──────────────────────────────┐
                    │     App CLI Launch Flags     │
                    └──────────────┬───────────────┘
                                   │
              ┌────────────────────┴────────────────────┐
              ▼                                         ▼
   Standard Live Workspace                     Isolated UI Lab Mode
   - Main Live Deck Mixer                      - Theme color swatches
   - Audio Engine & Modulation Matrix          - Custom slider / icon tester
   - Dynamic GLSL Shaders                      - Modular accordion gallery
              │                                         │
              └────────────────────┬────────────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │   Frame Settle Counter (N)   │
                    └──────────────┬───────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │    OpenGL Readback Pipeline  │
                    │   - glReadPixels / STB Image │
                    │   - Asynchronous PBO option  │
                    └──────────────┬───────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │     PNG Disk Persistence     │
                    │       GLFW Clean Exit        │
                    └──────────────────────────────┘
```

### 3.1 Frame Capture Mechanism
Liquid LSD already contains high-performance readback infrastructure in `export/PboReadbackPipeline.kt` and STB bindings in LWJGL:

```kotlin
object ScreenshotCapture {
    fun captureFramebufferToPng(file: File, width: Int, height: Int): Boolean {
        file.parentFile?.mkdirs()
        val stride = width * 3 // RGB
        val source = BufferUtils.createByteBuffer(stride * height)
        val flipped = BufferUtils.createByteBuffer(stride * height)

        val previousPackAlignment = glGetInteger(GL_PACK_ALIGNMENT)
        glPixelStorei(GL_PACK_ALIGNMENT, 1)
        glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, source)
        glPixelStorei(GL_PACK_ALIGNMENT, previousPackAlignment)

        // Vertical flip (OpenGL lower-left origin to PNG top-left origin)
        for (y in 0 until height) {
            val sourceRow = (height - 1 - y) * stride
            val targetRow = y * stride
            for (x in 0 until stride) {
                flipped.put(targetRow + x, source.get(sourceRow + x))
            }
        }

        return STBImageWrite.stbi_write_png(file.absolutePath, width, height, 3, flipped, stride)
    }
}
```

### 3.2 Settle Counter & Clean Exit
Because Dear ImGui dynamically calculates widget coordinates and auto-sizes windows across initial frames:
1. Render $N$ frames (e.g. 5 frames) to let ImGui layout passes, splitters, and font atlases converge.
2. On frame $N$, execute `captureFramebufferToPng()`.
3. Signal `glfwSetWindowShouldClose(window, true)` to trigger a graceful shutdown.

### 3.3 Headless CI Integration (`xvfb`)
On Linux CI runners (e.g. GitHub Actions), OpenGL rendering can be run without physical displays via Xvfb:
```bash
xvfb-run -s "-screen 0 1920x1080x24" ./gradlew captureResponsiveApp
```

---

## 4. Gradle Automation Tasks (Future Idea)

```kotlin
// build.gradle.kts
tasks.register<JavaExec>("captureResponsiveApp") {
    group = "verification"
    description = "Captures UI screenshots across standard responsive resolutions."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("llm.slop.liquidlsd.MainKt")
    args = listOf(
        "--screenshot-ui=docs/user_guide/assets/ui-desktop-1080p.png",
        "--window=1920x1080",
        "--screenshot-after-frames=6",
        "--no-audio"
    )
}
```

---

## 5. Summary & Next Steps

This proposal provides a blueprint for when we decide to implement automated visual capture and headless UI verification. When ready to implement:
1. Wire CLI argument parsing into `Main.kt` / `AppSettings.kt`.
2. Add `ScreenshotCapture.kt` utility under `export/` or `utils/`.
3. Add a check at the end of the render loop in `Main.kt` to trigger the capture and exit.

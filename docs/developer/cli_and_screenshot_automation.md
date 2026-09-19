# CLI & Automated Screen Capture Pipeline

This document describes the CLI argument parser, automated framebuffer PNG screenshot capture pipeline, and the isolated UI Lab sandbox environment in Liquid LSD.

## 1. Overview & Architecture

To support automated visual regression testing, documentation asset generation, and headless CI execution, Liquid LSD parses startup arguments via `CliArgs` and supports single-frame PNG capture with automatic graceful exit.

```
                    ┌──────────────────────────────┐
                    │      CliArgs.parse(args)     │
                    └──────────────┬───────────────┘
                                   │
              ┌────────────────────┴────────────────────┐
              ▼                                         ▼
   Live Workspace Mode                        Isolated UI Lab Mode
   - Main 3-Column Deck Mixer                 - Theme color swatches
   - Audio Engine (unless --no-audio)         - Custom widget & slider gallery
   - Performance Mode & Shaders               - Lucide icon catalog
              │                                         │
              └────────────────────┬────────────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │    Frame Settle Counter (N)  │
                    └──────────────┬───────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │   ScreenshotCapture (PNG)    │
                    └──────────────┬───────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │    Clean Exit (glfwClose)    │
                    └──────────────────────────────┘
```

## 2. Startup CLI Flags

| Flag | Argument | Description |
|---|---|---|
| `--screenshot-ui` | `<filepath.png>` | Output PNG path for captured framebuffer. Triggers automatic exit after capture. |
| `--screenshot-after-frames` | `<int>` *(default: 5)* | Number of frames to render before capture to allow ImGui layouts to settle. |
| `--window` | `<W>x<H>` or `maximized` | Overrides initial window dimensions (e.g. `1920x1080` or `1280x720`). |
| `--no-audio` | *(flag)* | Bypasses JACK and JavaSound audio initialization for headless or soundcard-less environments. |
| `--ui-lab` | *(flag)* | Launches the isolated UI component gallery sandbox instead of the live mixer workspace. |
| `--help` / `-h` | *(flag)* | Prints command line options and exits. |
| `--version` / `-v` | *(flag)* | Prints application version and runtime details and exits. |

## 3. Frame Capture Mechanism

Screenshot capture is handled by `ScreenshotCapture`:
1. `glReadPixels` reads RGBA pixel memory directly from the OpenGL framebuffer.
2. `flipVertical` flips image scanlines vertically to convert from OpenGL lower-left origin to standard top-left PNG origin.
3. STB Image (`stbi_write_png`) encodes and writes the PNG file to disk.
4. `glfwSetWindowShouldClose(window, true)` signals clean shutdown immediately after writing.

## 4. Gradle Tasks

The build file `build.gradle.kts` defines two automated verification tasks:
- `./gradlew captureResponsiveApp`: Renders full 1080p workspace screenshot to `docs/user_guide/assets/ui-desktop-1080p.png`.
- `./gradlew captureUiLab`: Renders 720p UI Lab gallery screenshot to `docs/user_guide/assets/ui-lab-1080p.png`.

## 5. Headless CI Integration (`xvfb`)

On Linux CI runners without physical displays (e.g. GitHub Actions), run screenshot tasks using `xvfb-run`:
```bash
xvfb-run -s "-screen 0 1920x1080x24" ./gradlew captureResponsiveApp
```

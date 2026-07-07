# UI Iteration Workflow

Spirals includes a deterministic UI lab mode for visual design work without requiring live audio, MIDI, or saved session state.

## Run The Lab

```bash
./gradlew runUiLab
```

Equivalent direct launch:

```bash
./gradlew run --args="--ui-lab --no-audio --no-watchdog"
```

## Capture A Screenshot

```bash
./gradlew captureUiLab
```

The default output is:

```text
build/ui-lab/ui-lab.png
```

For custom capture sizes or paths:

```bash
./gradlew run --args="--ui-lab --window=1440x900 --screenshot-ui=build/ui-lab/custom.png --screenshot-after-frames=8 --no-audio --no-watchdog"
```

## Capture Responsive Sets

```bash
./gradlew captureResponsiveUi
```

This writes compact, laptop, desktop, and wide screenshots for both the deterministic lab and the live app shell, plus a maximized live-app capture:

```text
build/ui-lab/responsive/ui-lab-compact.png
build/ui-lab/responsive/ui-lab-laptop.png
build/ui-lab/responsive/ui-lab-desktop.png
build/ui-lab/responsive/ui-lab-wide.png
build/ui-lab/responsive/app-compact.png
build/ui-lab/responsive/app-laptop.png
build/ui-lab/responsive/app-desktop.png
build/ui-lab/responsive/app-wide.png
build/ui-lab/responsive/app-maximized.png
```

The compact preset is the minimum practical review size. OS-minimized screenshots are intentionally not captured because minimized GLFW windows may not have a stable framebuffer to read.

For targeted review:

```bash
./gradlew captureResponsiveApp
./gradlew captureResponsiveUiLab
```

## Useful Flags

- `--ui-lab`: render the deterministic UI lab instead of the live app layout.
- `--window=WIDTHxHEIGHT`: set the startup GLFW window size.
- `--screenshot-ui=PATH`: write the current framebuffer to a PNG and exit.
- `--screenshot-after-frames=N`: wait for N frames before capture so fonts and GL resources settle.
- `--no-audio`: skip JACK audio startup.
- `--no-watchdog`: skip MIDI/JACK watchdog startup.

## Testing Strategy

Keep layout math in pure calculators where possible and cover it with normal Kotlin tests. Use UI lab screenshots for visual review of typography, spacing, colors, controls, and panel density.
